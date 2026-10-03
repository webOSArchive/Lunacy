#!/usr/bin/env python3
"""Generates the GL stream between a PDK app and the shell from the PDK's own GLES 1.1
headers (Docs/pdk.md, "Transformers G1"):

  gen_gles1.py client <gl.h> <glext.h>   -> libGLES_CM.so's source: every glXxx the headers
                                            declare, serialising its call into a batch that
                                            goes down the socket; queries answered locally
  gen_gles1.py server <gl.h> <glext.h>   -> the replay switch for liblunacygl (gl_server.c),
                                            calling the real GLES 1.1 in the shell's process
  gen_gles1.py names  <gl.h> <glext.h>   -> opcode, name

Both sides number the functions identically (their order in the headers), so the opcode
is the index. A command is [u32 opcode][u32 payload bytes][payload]: the arguments as
4-byte units in order (floats as their bits, a copied pointer as a present flag), then
each copied pointer argument as [u32 length][bytes]. Client-side vertex arrays are not
sent when set but when drawn: a draw is preceded by one ARRAY command (opcode 0xFFFF)
per enabled array with the bytes it covers, and the server points the real GL at its copy.
"""
import os
import re
import sys

PROTO = re.compile(r'^GL_API\s+(.+?)\s*GL_APIENTRY\s+(gl\w+)\s*\((.*)\);\s*$')

SCALAR = {'GLenum', 'GLint', 'GLuint', 'GLfloat', 'GLfixed', 'GLsizei', 'GLboolean', 'GLclampf', 'GLclampx',
          'GLintptr', 'GLsizeiptr', 'GLbitfield', 'GLshort', 'GLubyte', 'GLbyte'}

# How many values a *v parameter holds, by its pname (the GL spec's tables); 1 otherwise.
PNAME_COUNTS = {
    'light': {0x1200: 4, 0x1201: 4, 0x1202: 4, 0x1203: 4, 0x1204: 3},
    'lightmodel': {0x0B53: 4},
    'material': {0x1200: 4, 0x1201: 4, 0x1202: 4, 0x1600: 4, 0x1602: 4},
    'fog': {0x0B66: 4},
    'texenv': {0x2201: 4},
    'texparam': {0x8B9D: 4},
    'pointparam': {0x8129: 3},
    'texgen': {0x2503: 4, 0x2502: 4, 0x2501: 4},
}
V_TABLE = [('glLightModel', 'lightmodel'), ('glLight', 'light'), ('glMaterial', 'material'), ('glFog', 'fog'),
           ('glTexEnv', 'texenv'), ('glTexParameter', 'texparam'), ('glPointParameter', 'pointparam'), ('glTexGen', 'texgen')]
FIXED_COUNTS = {'glLoadMatrixf': 16, 'glLoadMatrixx': 16, 'glMultMatrixf': 16, 'glMultMatrixx': 16,
                'glLoadMatrixxOES': 16, 'glMultMatrixxOES': 16,
                'glClipPlanef': 4, 'glClipPlanex': 4, 'glClipPlanefOES': 4, 'glClipPlanexOES': 4, 'glClipPlanefIMG': 4, 'glClipPlanexIMG': 4,
                'glDrawTexsvOES': 5, 'glDrawTexivOES': 5, 'glDrawTexxvOES': 5, 'glDrawTexfvOES': 5,
                'glProgramEnvParameter4fvARB': 4, 'glProgramLocalParameter4fvARB': 4,
                'glProgramLocalParameter4xvIMG': 4, 'glProgramEnvParameter4xvIMG': 4}
# The client-side arrays: function -> (array id, has a size argument)
ARRAY_FUNCS = {'glVertexPointer': (0, True), 'glColorPointer': (1, True), 'glNormalPointer': (2, False),
               'glTexCoordPointer': (3, True), 'glPointSizePointerOES': (4, False),
               'glMatrixIndexPointerOES': (5, True), 'glWeightPointerOES': (6, True)}
DELETES = {'glDeleteTextures', 'glDeleteBuffers', 'glDeleteFramebuffersOES', 'glDeleteRenderbuffersOES',
           'glDeleteFencesNV', 'glDeleteProgramsARB'}
# Answered in the app's process, never sent.
LOCAL = {
    'glGetString', 'glGetError', 'glGetIntegerv', 'glGetFloatv', 'glGetFixedv', 'glGetFixedvOES', 'glGetBooleanv',
    'glGetPointerv', 'glGetBufferPointervOES', 'glGetBufferParameteriv', 'glGetLightfv', 'glGetLightxv', 'glGetLightxvOES',
    'glGetMaterialfv', 'glGetMaterialxv', 'glGetMaterialxvOES', 'glGetTexEnvfv', 'glGetTexEnviv', 'glGetTexEnvxv', 'glGetTexEnvxvOES',
    'glGetTexParameterfv', 'glGetTexParameteriv', 'glGetTexParameterxv', 'glGetTexParameterxvOES', 'glGetClipPlanef', 'glGetClipPlanex',
    'glGetClipPlanefOES', 'glGetClipPlanexOES', 'glGetTexGenfvOES', 'glGetTexGenivOES', 'glGetTexGenxvOES',
    'glGetRenderbufferParameterivOES', 'glGetFramebufferAttachmentParameterivOES', 'glCheckFramebufferStatusOES',
    'glIsTexture', 'glIsBuffer', 'glIsEnabled', 'glIsFramebufferOES', 'glIsRenderbufferOES', 'glIsFenceNV', 'glTestFenceNV',
    'glGenTextures', 'glGenBuffers', 'glGenFramebuffersOES', 'glGenRenderbuffersOES', 'glGenFencesNV', 'glGenProgramsARB',
    'glQueryMatrixxOES', 'glMapBufferOES', 'glUnmapBufferOES', 'glReadPixels',
    'glGetTexStreamDeviceAttributeivIMG', 'glGetTexStreamDeviceNameIMG', 'glGetFenceivNV',
    'glGetDriverControlsQCOM', 'glGetDriverControlStringQCOM', 'glExtGetTexturesQCOM', 'glExtGetBuffersQCOM',
    'glExtGetRenderbuffersQCOM', 'glExtGetFramebuffersQCOM', 'glExtGetTexLevelParameterivQCOM', 'glExtGetTexSubImageQCOM',
    'glExtGetBufferPointervQCOM', 'glExtGetShadersQCOM', 'glExtGetProgramsQCOM', 'glExtIsProgramBinaryQCOM',
    'glExtGetProgramBinarySourceQCOM', 'glEGLImageTargetTexture2DOES', 'glEGLImageTargetRenderbufferStorageOES',
    'glMultiDrawArraysEXT', 'glMultiDrawElementsEXT', 'glGetProgramivARB', 'glGetProgramStringARB',
}
ARRAY_OP = 0xFFFF


def params(text):
    text = text.strip()
    if text in ('', 'void'):
        return []
    out = []
    for i, p in enumerate(text.split(',')):
        p = p.strip()
        m = re.match(r'^(.*?)(\**)\s*(\w+)?\s*(\[\d*\])?$', p)
        typ = (m.group(1).strip() + ' ' + m.group(2)).strip()
        name = m.group(3) or f'a{i}'
        if name in SCALAR or name == 'GLvoid':
            typ = (typ + ' ' + name).strip(); name = f'a{i}'
        if m.group(4):
            typ += ' *'
        out.append((typ, name))
    return out


def is_ptr(typ):
    return '*' in typ


def base(typ):
    return typ.replace('const', '').replace('*', '').strip()


def elem_size(typ):
    return {'GLshort': 2, 'GLubyte': 1, 'GLbyte': 1, 'GLboolean': 1, 'char': 1}.get(base(typ), 4)


def read_protos(paths):
    protos, seen = [], set()
    for index, path in enumerate(paths):
        for line in open(path):
            m = PROTO.match(line.strip())
            if not m:
                continue
            ret, name, args = m.group(1).strip(), m.group(2), m.group(3)
            if name in seen:
                continue
            seen.add(name)
            # Everything outside gl.h, and gl.h's own optional OES functions (matrix palette,
            # draw texture), has to be asked for by name; glPointSizePointerOES is core.
            ext = index > 0 or (name.endswith('OES') and name != 'glPointSizePointerOES')
            protos.append((ret, name, params(args), ext))
    return protos


def v_count_expr(name, ps):
    if name in FIXED_COUNTS:
        return str(FIXED_COUNTS[name])
    for prefix, table in V_TABLE:
        if name.startswith(prefix) and name.endswith('v') and not name.startswith('glGet'):
            if not any(n == 'pname' for _, n in ps):
                return None
            cases = ' '.join(f'case 0x{k:04X}: n = {v}; break;' for k, v in PNAME_COUNTS[table].items())
            return f'pname_count(pname, {len(PNAME_COUNTS[table])}, (const int[]){{{", ".join(f"0x{k:04X}, {v}" for k, v in PNAME_COUNTS[table].items())}}})'
    return None


def classify(name, ps):
    """For each pointer parameter: (name, kind, size expression)."""
    kinds = []
    for typ, n in ps:
        if not is_ptr(typ):
            continue
        if name in ARRAY_FUNCS and n == 'pointer':
            kinds.append((n, 'array', None))
        elif name == 'glDrawElements' and n == 'indices':
            kinds.append((n, 'indices', None))
        elif name == 'glVertexAttribPointerARB' and n == 'pointer':
            kinds.append((n, 'offset', None))
        elif name in ('glTexImage2D', 'glTexSubImage2D') and n == 'pixels':
            kinds.append((n, 'blob', 'image_bytes(width, height, format, type)'))
        elif name in ('glCompressedTexImage2D', 'glCompressedTexSubImage2D') and n == 'data':
            kinds.append((n, 'blob', '(size_t)imageSize'))
        elif name in ('glBufferData', 'glBufferSubData') and n == 'data':
            kinds.append((n, 'blob', '(size_t)size'))
        elif name == 'glProgramStringARB' and n == 'string':
            kinds.append((n, 'blob', '(size_t)len'))
        elif name in DELETES:
            kinds.append((n, 'blob', f'(size_t){ps[0][1]} * 4'))
        elif name == 'glDiscardFramebufferEXT':
            kinds.append((n, 'blob', '(size_t)numAttachments * 4'))
        else:
            c = v_count_expr(name, ps)
            kinds.append((n, 'blob', f'(size_t)({c}) * {elem_size(typ)}') if c is not None else (n, 'unsupported', None))
    return kinds


def gen_client(protos):
    print('/* Generated by gen_gles1.py from the PDK\'s GLES/gl.h and glext.h: do not edit. */')
    print(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'client_prelude.c')).read())
    for op, (ret, name, ps, _) in enumerate(protos):
        sig = ', '.join(f'{t} {n}' for t, n in ps) or 'void'
        print(f'{ret} {name}({sig}) {{')
        kinds = classify(name, ps)
        print(f'    trace("{name}");')
        if name in LOCAL or any(k == 'unsupported' for _, k, _ in kinds):
            print(local_body(name, ret, ps))
            print('}')
            continue
        if name in ARRAY_FUNCS:
            aid, has_size = ARRAY_FUNCS[name]
            unit = 'client_unit' if name == 'glTexCoordPointer' else '0'
            size = 'size' if has_size else ('1' if name == 'glPointSizePointerOES' else '3')
            print(f'    set_array({aid}, {unit}, {size}, type, stride, pointer);')
        if name == 'glEnableClientState':
            print('    enable_array(array, 1);')
        if name == 'glDisableClientState':
            print('    enable_array(array, 0);')
        if name == 'glClientActiveTexture':
            print('    client_unit = (int)texture - 0x84C0; if (client_unit < 0 || client_unit > 3) client_unit = 0;')
        if name == 'glBindBuffer':
            print('    bind_buffer(target, buffer);')
        if name == 'glBufferData':
            print('    remember_buffer(target, (size_t)size, data);')
        if name == 'glBufferSubData':
            print('    remember_buffer_sub(target, (size_t)offset, (size_t)size, data);')
        if name == 'glPixelStorei':
            print('    if (pname == 0x0CF5) unpack_alignment = param; if (pname == 0x0D05) pack_alignment = param;')
        if name == 'glViewport':
            print('    viewport[0] = x; viewport[1] = y; viewport[2] = width; viewport[3] = height;')
        if name == 'glScissor':
            print('    scissor[0] = x; scissor[1] = y; scissor[2] = width; scissor[3] = height;')
        if name == 'glDrawArrays':
            print('    send_arrays((size_t)first + (size_t)count, 0, 0, NULL);')
        if name == 'glDrawElements':
            print('    send_arrays(0, (size_t)count, type, indices);')
        blobs = [(n, sz) for n, k, sz in kinds if k == 'blob']
        blob_len = ' + '.join([f'4 + blob_len({n}, {sz})' for n, sz in blobs] or ['0'])
        print(f'    uint8_t *wr_ = begin({op}, {4 * len(ps)} + ({blob_len}));')
        print('    if (!wr_) return;')
        for t, n in ps:
            if is_ptr(t):
                k = [k for nn, k, _ in kinds if nn == n][0]
                if k in ('array', 'indices', 'offset'):
                    print(f'    wr_ = put_u32(wr_, (uint32_t)(uintptr_t){n});')
                else:
                    print(f'    wr_ = put_u32(wr_, {n} ? 1u : 0u);')
            elif base(t) in ('GLfloat', 'GLclampf'):
                print(f'    wr_ = put_f32(wr_, {n});')
            else:
                print(f'    wr_ = put_u32(wr_, (uint32_t){n});')
        for n, sz in blobs:
            print(f'    wr_ = put_blob(wr_, {n}, {sz});')
        print('    end(wr_);')
        if name in ('glFlush', 'glFinish'):
            print('    lunacy_gl_flush();')
        print('}')


def local_body(name, ret, ps):
    if name == 'glGetError':
        return '    { GLenum e = gl_error; gl_error = GL_NO_ERROR; return e; }'
    if name == 'glGetString':
        return ('    static char vendor[] = "Lunacy", renderer[] = "Lunacy GLES 1.1 stream", version[] = "OpenGL ES-CM 1.1", '
                'extensions[] = "GL_OES_framebuffer_object GL_OES_compressed_ETC1_RGB8_texture GL_OES_draw_texture GL_OES_texture_npot GL_OES_blend_subtract GL_OES_point_sprite", none[] = "";\n'
                '    switch (name) { case GL_VENDOR: return (const GLubyte*)vendor; case GL_RENDERER: return (const GLubyte*)renderer; case GL_VERSION: return (const GLubyte*)version; case GL_EXTENSIONS: return (const GLubyte*)extensions; default: return (const GLubyte*)none; }')
    if name in ('glGenTextures', 'glGenBuffers', 'glGenFramebuffersOES', 'glGenRenderbuffersOES', 'glGenFencesNV', 'glGenProgramsARB'):
        return f'    gen({ps[0][1]}, {ps[1][1]});'
    if name == 'glGetIntegerv':
        return ('    switch (pname) { case GL_MAX_TEXTURE_SIZE: *params = 2048; break; case GL_VIEWPORT: memcpy(params, viewport, sizeof viewport); break; '
                'case GL_SCISSOR_BOX: memcpy(params, scissor, sizeof scissor); break; case GL_MAX_TEXTURE_UNITS: *params = 2; break; case GL_DEPTH_BITS: *params = 16; break; '
                'case GL_STENCIL_BITS: *params = 8; break; case GL_RED_BITS: case GL_GREEN_BITS: case GL_BLUE_BITS: case GL_ALPHA_BITS: *params = 8; break; case GL_MAX_LIGHTS: *params = 8; break; '
                'case GL_NUM_COMPRESSED_TEXTURE_FORMATS: *params = 1; break; case GL_COMPRESSED_TEXTURE_FORMATS: *params = 0x8D64; break; case GL_ARRAY_BUFFER_BINDING: *params = (GLint)array_buffer; break; '
                'case GL_ELEMENT_ARRAY_BUFFER_BINDING: *params = (GLint)element_buffer; break; case GL_UNPACK_ALIGNMENT: *params = unpack_alignment; break; case GL_MAX_MODELVIEW_STACK_DEPTH: *params = 16; break; '
                'case GL_PACK_ALIGNMENT: *params = pack_alignment; break; case 0x8B9A: *params = GL_UNSIGNED_BYTE; break; case 0x8B9B: *params = GL_RGBA; break; case GL_MAX_PROJECTION_STACK_DEPTH: *params = 2; break; case GL_MAX_TEXTURE_STACK_DEPTH: *params = 2; break; case GL_MAX_CLIP_PLANES: *params = 6; break; default: *params = 0; }')
    if name in ('glGetFloatv', 'glGetFixedv', 'glGetFixedvOES'):
        one = '65536' if 'Fixed' in name else '1.0f'
        sixty = '(64 << 16)' if 'Fixed' in name else '64.0f'
        big = '(2048 << 16)' if 'Fixed' in name else '2048.0f'
        return (f'    switch (pname) {{ case GL_ALIASED_POINT_SIZE_RANGE: case GL_ALIASED_LINE_WIDTH_RANGE: case GL_SMOOTH_POINT_SIZE_RANGE: case GL_SMOOTH_LINE_WIDTH_RANGE: params[0] = {one}; params[1] = {sixty}; break; '
                f'case GL_MAX_TEXTURE_SIZE: params[0] = {big}; break; default: *params = 0; }}')
    if name == 'glGetBooleanv':
        return '    *params = GL_FALSE;'
    if name == 'glCheckFramebufferStatusOES':
        return '    return GL_FRAMEBUFFER_COMPLETE_OES;'
    if name in ('glIsTexture', 'glIsBuffer', 'glIsFramebufferOES', 'glIsRenderbufferOES'):
        return '    return GL_TRUE;'
    if name == 'glIsEnabled':
        return '    return array_enabled_cap(cap);'
    if name == 'glMapBufferOES':
        return '    return NULL;'
    if name == 'glUnmapBufferOES':
        return '    return GL_FALSE;'
    if name == 'glReadPixels':
        return '    read_pixels(x, y, width, height, format, type, pixels);'
    if name == 'glQueryMatrixxOES':
        return '    return 0;'
    out = []
    for t, n in ps:
        if is_ptr(t) and 'const' not in t and base(t) not in ('GLvoid', 'void', 'char'):
            out.append(f'    if ({n}) *{n} = 0;')
    if ret != 'void':
        out.append(f'    return ({ret})0;')
    return '\n'.join(out) if out else '    (void)0;'


def gen_server(protos):
    print('/* Generated by gen_gles1.py from the PDK\'s GLES/gl.h and glext.h: do not edit.')
    print('   Included by gl_server.c inside its replay switch over `op`, with reader `rd_`. */')
    print('#ifdef GLES_NAMES')
    print('static const char *const op_names[] = {')
    for _, name, _, _ in protos:
        print(f'    "{name}",')
    print('};')
    print('#else')
    for op, (ret, name, ps, ext) in enumerate(protos):
        kinds = classify(name, ps)
        if name in LOCAL or any(k == 'unsupported' for _, k, _ in kinds):
            continue
        print(f'case {op}: {{ /* {name} */')
        call_name = name
        if ext:
            # An extension: Android's GLES 1.1 library exports few of them by name; asked for
            # through EGL the first time, and skipped when the driver hasn't got it.
            sig = ', '.join(t for t, _ in ps) or 'void'
            print(f'    static {ret} (*fn_)({sig}); static int looked_;')
            print(f'    if (!looked_) {{ fn_ = ({ret} (*)({sig}))eglGetProcAddress("{name}"); looked_ = 1; if (!fn_) missing("{name}"); }}')
            print('    if (!fn_) break;')
            call_name = 'fn_'
        args = []
        for t, n in ps:
            if is_ptr(t):
                k = [k for nn, k, _ in kinds if nn == n][0]
                print(f'    uint32_t {n}_v = get_u32(&rd_);')
                if k == 'indices':
                    args.append(f'(element_bound ? (const GLvoid *)(uintptr_t){n}_v : (const GLvoid *)index_scratch)')
                elif k in ('array', 'offset'):
                    args.append(f'({t})(uintptr_t){n}_v')
                else:
                    args.append(f'({t}){n}_p')
            elif base(t) in ('GLfloat', 'GLclampf'):
                print(f'    {t} {n} = get_f32(&rd_);')
                args.append(n)
            else:
                print(f'    {t} {n} = ({t})get_u32(&rd_);')
                args.append(n)
        for n, k, sz in kinds:
            if k == 'blob':
                print(f'    const void *{n}_p = get_blob(&rd_, {n}_v);')
        if name in ARRAY_FUNCS:
            aid, has_size = ARRAY_FUNCS[name]
            unit = 'client_unit' if name == 'glTexCoordPointer' else '0'
            size = 'size' if has_size else ('1' if name == 'glPointSizePointerOES' else '3')
            print(f'    remember_pointer({aid}, {unit}, {size}, type, stride, pointer_v);')
            print('    break;\n}')
            continue
        if name == 'glClientActiveTexture':
            print('    client_unit = (int)texture - 0x84C0; if (client_unit < 0 || client_unit > 3) client_unit = 0;')
        if name == 'glBindBuffer':
            print('    if (target == 0x8893) element_bound = buffer; if (target == 0x8892) array_bound = buffer;')
        # Framebuffer and renderbuffer names: the client makes them up, drivers refuse a name
        # they didn't generate, so the shell maps each to one of its own; the app's
        # framebuffer 0 is the shell's offscreen one, its own size (gl_server.c).
        if name == 'glBindFramebufferOES':
            print('    framebuffer = map_name(&fb_names, framebuffer, 1);')
        if name in ('glBindRenderbufferOES', 'glFramebufferRenderbufferOES'):
            print('    renderbuffer = map_name(&rb_names, renderbuffer, 1);')
        if name in ('glDeleteFramebuffersOES', 'glDeleteRenderbuffersOES'):
            table = '&fb_names' if 'Frame' in name else '&rb_names'
            arr = [n for t, n in ps][1]
            print(f'    delete_names({table}, n, (const GLuint *){arr}_p, fn_); break;\n}}')
            continue
        if name == 'glEnableClientState':
            print('    server_enable(array, 1);')
        if name == 'glDisableClientState':
            print('    server_enable(array, 0);')
        if name in ('glDrawArrays', 'glDrawElements'):
            print('    apply_arrays();')
        # Uploads: checked against what arrived, BGRA swizzled where the driver lacks it.
        if name == 'glTexImage2D':
            print('    if (pixels_p) { pixels_p = upload("glTexImage2D", width, height, &internalformat, &format, type, pixels_p); if (pixels_p == (const void *)-1) break; }')
        if name == 'glTexSubImage2D':
            print('    if (pixels_p) { pixels_p = upload("glTexSubImage2D", width, height, NULL, &format, type, pixels_p); if (pixels_p == (const void *)-1) break; }')
        # PVRTC decoded where the driver lacks it (pvrtc.h).
        if name == 'glCompressedTexImage2D':
            print('    if (compressed_upload(0, target, level, 0, 0, internalformat, width, height, data_p)) break;')
        if name == 'glCompressedTexSubImage2D':
            print('    if (compressed_upload(1, target, level, xoffset, yoffset, format, width, height, data_p)) break;')
        if name == 'glPixelStorei':
            print('    if (pname == 0x0CF5) unpack_alignment = param;')
        call = f'{call_name}({", ".join(args)})'
        print(f'    {call};' if ret == 'void' else f'    (void){call};')
        print('    break;\n}')
    print('#endif')


def main():
    mode, paths = sys.argv[1], sys.argv[2:]
    protos = read_protos(paths)
    if mode == 'client':
        gen_client(protos)
    elif mode == 'server':
        gen_server(protos)
    elif mode == 'names':
        for op, (_, name, _, ext) in enumerate(protos):
            print(op, name, 'ext' if ext else '')
    else:
        sys.exit('mode: client | server | names')


if __name__ == '__main__':
    main()
