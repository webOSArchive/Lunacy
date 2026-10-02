#!/usr/bin/env python3
"""Generates the GLES 2 stream between a PDK app and the shell from the PDK's own GLES 2
headers (Docs/pdk.md, "GLES 2"), as gen_gles1.py does for GLES 1.1:

  gen_gles2.py client <gl2.h> <gl2ext.h>   -> libGLESv2.so's functions (after client2_prelude.c)
  gen_gles2.py server <gl2.h> <gl2ext.h>   -> the replay switch for liblunacygl2 (gl_server.c)

The wire is gen_gles1.py's: [u32 opcode][u32 payload bytes][payload], arguments as 4-byte
units, an input buffer as [u32 length][bytes padded to 4]. What GLES 2 adds:

- Queries (any function with an output) are round trips: the client sends the call and
  waits; the shell calls the real function and answers with [u32 return value] and, for each
  output argument in order, [u32 length][bytes]; the client copies exactly that. Output sizes
  are the GL spec's: by pname for glGet*v (gl_server.c, get_count), the buffer size for a
  string.
- Shaders and programs are named by the client (a counter, as glGen* names are) and the
  shell maps each to the one the driver created; so are framebuffers and renderbuffers,
  which drivers refuse to bind by a name they didn't make. Every argument named program,
  shader, framebuffer or renderbuffer goes through the map.
- Client-side vertex attribute arrays travel with each draw, one ATTRIB command per enabled
  array that isn't in a buffer object (client2_prelude.c).
- Extensions answer in the app's process: the only apps that import them are Android ports
  that link the whole header (apkenv), and none of those calls has been seen made.
"""
import os
import re
import sys

PROTO = re.compile(r'^GL_APICALL\s+(.+?)\s*GL_APIENTRY\s+(gl\w+)\s*\((.*)\);')
SCALAR = {'GLenum', 'GLint', 'GLuint', 'GLfloat', 'GLfixed', 'GLsizei', 'GLboolean', 'GLclampf', 'GLclampx',
          'GLintptr', 'GLsizeiptr', 'GLbitfield', 'GLshort', 'GLubyte', 'GLbyte', 'GLeglImageOES'}

# Written by hand in client2_prelude.c (client) and gl_server.c (server).
CUSTOM = {'glGetError', 'glGetString', 'glGenTextures', 'glGenBuffers', 'glGenFramebuffers', 'glGenRenderbuffers',
          'glCreateShader', 'glCreateProgram', 'glShaderSource', 'glShaderBinary', 'glVertexAttribPointer',
          'glGetVertexAttribPointerv', 'glReadPixels', 'glGetAttachedShaders'}

# Input buffers: function -> {param: size expression}
INPUT = {
    'glBindAttribLocation': {'name': 'strlen(name) + 1'},
    'glBufferData': {'data': '(size_t)size'},
    'glBufferSubData': {'data': '(size_t)size'},
    'glCompressedTexImage2D': {'data': '(size_t)imageSize'},
    'glCompressedTexSubImage2D': {'data': '(size_t)imageSize'},
    'glDeleteBuffers': {'buffers': '(size_t)n * 4'},
    'glDeleteFramebuffers': {'framebuffers': '(size_t)n * 4'},
    'glDeleteRenderbuffers': {'renderbuffers': '(size_t)n * 4'},
    'glDeleteTextures': {'textures': '(size_t)n * 4'},
    'glGetAttribLocation': {'name': 'strlen(name) + 1'},
    'glGetUniformLocation': {'name': 'strlen(name) + 1'},
    'glTexImage2D': {'pixels': 'image_bytes(width, height, format, type)'},
    'glTexSubImage2D': {'pixels': 'image_bytes(width, height, format, type)'},
    'glTexParameterfv': {'params': '4'},
    'glTexParameteriv': {'params': '4'},
}
for n in (1, 2, 3, 4):
    INPUT[f'glUniform{n}fv'] = {'v': f'(size_t)count * {4 * n}'}
    INPUT[f'glUniform{n}iv'] = {'v': f'(size_t)count * {4 * n}'}
    INPUT[f'glVertexAttrib{n}fv'] = {'values': f'{4 * n}'}
for n in (2, 3, 4):
    INPUT[f'glUniformMatrix{n}fv'] = {'value': f'(size_t)count * {4 * n * n}'}

# Outputs of the round trips: function -> [(param, kind)]; kind 'vals' (a glGet*v array,
# sized by the shell), 'one' (a single value), 'str' (a string of the buffer size given).
OUTPUT = {
    'glGetBooleanv': [('params', 'vals')], 'glGetFloatv': [('params', 'vals')], 'glGetIntegerv': [('params', 'vals')],
    'glGetBufferParameteriv': [('params', 'vals')], 'glGetFramebufferAttachmentParameteriv': [('params', 'vals')],
    'glGetProgramiv': [('params', 'vals')], 'glGetRenderbufferParameteriv': [('params', 'vals')],
    'glGetShaderiv': [('params', 'vals')], 'glGetTexParameterfv': [('params', 'vals')], 'glGetTexParameteriv': [('params', 'vals')],
    'glGetUniformfv': [('params', 'vals')], 'glGetUniformiv': [('params', 'vals')],
    'glGetVertexAttribfv': [('params', 'vals')], 'glGetVertexAttribiv': [('params', 'vals')],
    'glGetShaderPrecisionFormat': [('range', 'vals'), ('precision', 'vals')],
    'glGetProgramInfoLog': [('length', 'one'), ('infolog', 'str')],
    'glGetShaderInfoLog': [('length', 'one'), ('infolog', 'str')],
    'glGetShaderSource': [('length', 'one'), ('source', 'str')],
    'glGetActiveAttrib': [('length', 'one'), ('size', 'one'), ('type', 'one'), ('name', 'str')],
    'glGetActiveUniform': [('length', 'one'), ('size', 'one'), ('type', 'one'), ('name', 'str')],
}
# Round trips with a return value and no outputs.
RETURNS = {'glGetAttribLocation', 'glGetUniformLocation', 'glCheckFramebufferStatus', 'glIsBuffer', 'glIsEnabled',
           'glIsFramebuffer', 'glIsProgram', 'glIsRenderbuffer', 'glIsShader', 'glIsTexture'}
MAPPED = {'program': 'prog_names', 'shader': 'prog_names', 'framebuffer': 'fb_names', 'renderbuffer': 'rb_names'}
ATTRIB_OP = 0xFFFC


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
        if name in SCALAR or name in ('void', 'char', 'GLchar'):
            typ = (typ + ' ' + name).strip(); name = f'a{i}'
        if m.group(4):
            typ += ' *'
        out.append((typ.replace('  ', ' '), name))
    return out


def is_ptr(t):
    return '*' in t


def base(t):
    return t.replace('const', '').replace('*', '').strip()


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
            protos.append((ret, name, params(args), index > 0))
    return protos


def kind(name, ext):
    if name in CUSTOM:
        return 'custom'
    if ext:
        return 'local'
    if name in OUTPUT or name in RETURNS:
        return 'query'
    return 'send'


def gen_client(protos):
    print('/* Generated by gen_gles2.py from the PDK\'s GLES2/gl2.h and gl2ext.h: do not edit. */')
    for op, (_, name, _, _) in enumerate(protos):
        print(f'#define OP_{name} {op}')
    print(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'client2_prelude.c')).read())
    for op, (ret, name, ps, ext) in enumerate(protos):
        k = kind(name, ext)
        if k == 'custom':
            continue
        sig = ', '.join(f'{t} {n}' for t, n in ps) or 'void'
        print(f'GL_APICALL {ret} GL_APIENTRY {name}({sig}) {{')
        print(f'    trace("{name}");')
        if k == 'local':
            for t, n in ps:
                if is_ptr(t) and 'const' not in t and base(t) not in ('void', 'GLvoid'):
                    print(f'    if ({n}) *{n} = 0;')
            print('    return 0;' if ret != 'void' else '    return;')
            print('}')
            continue
        # Bookkeeping the stream depends on.
        if name == 'glBindBuffer':
            print('    bind_buffer(target, buffer);')
        if name == 'glBufferData':
            print('    remember_buffer(target, (size_t)size, data);')
        if name == 'glBufferSubData':
            print('    remember_buffer_sub(target, (size_t)offset, (size_t)size, data);')
        if name == 'glEnableVertexAttribArray':
            print('    if (index < ATTRIBS) attribs[index].enabled = 1;')
        if name == 'glDisableVertexAttribArray':
            print('    if (index < ATTRIBS) attribs[index].enabled = 0;')
        if name == 'glPixelStorei':
            print('    if (pname == 0x0CF5) unpack_alignment = param; if (pname == 0x0D05) pack_alignment = param;')
        if name == 'glDrawArrays':
            print('    send_attribs((size_t)first + (size_t)count, 0, 0, NULL);')
        if name == 'glDrawElements':
            print('    send_attribs(0, (size_t)count, type, indices);')
        inputs = INPUT.get(name, {})
        outs = OUTPUT.get(name, [])
        blobs = [(n, inputs[n]) for t, n in ps if is_ptr(t) and n in inputs]
        blob_len = ' + '.join([f'4 + blob_len({n}, {sz})' for n, sz in blobs] or ['0'])
        print(f'    uint8_t *wr_ = begin({op}, {4 * len(ps)} + ({blob_len}));')
        print('    if (!wr_) ' + ('return 0;' if ret != 'void' else 'return;'))
        for t, n in ps:
            if is_ptr(t):
                if name == 'glDrawElements' and n == 'indices':
                    print(f'    wr_ = put_u32(wr_, (uint32_t)(uintptr_t){n});')
                elif n in inputs:
                    print(f'    wr_ = put_u32(wr_, {n} ? 1u : 0u);')
                else:
                    print(f'    wr_ = put_u32(wr_, {n} ? 1u : 0u);')   # an output: whether the app wants it
            elif base(t) in ('GLfloat', 'GLclampf'):
                print(f'    wr_ = put_f32(wr_, {n});')
            else:
                print(f'    wr_ = put_u32(wr_, (uint32_t){n});')
        for n, sz in blobs:
            print(f'    wr_ = put_blob(wr_, {n}, {sz});')
        print('    end(wr_);')
        if k == 'query':
            print('    struct answer an_; if (ask(&an_) < 0) ' + ('return 0;' if ret != 'void' else 'return;'))
            for n, kd in outs:
                cap = {'str': ([b for t, b in ps if b in ('bufsize', 'bufSize')] or ['0'])[0]}.get(kd, '-1')
                print(f'    take(&an_, {n}, {cap});')
            print('    done(&an_);')
            if ret != 'void':
                print(f'    return ({ret})an_.ret;')
        if name in ('glFlush', 'glFinish'):
            print('    lunacy_gl_flush();')
        print('}')


def gen_server(protos):
    print('/* Generated by gen_gles2.py from the PDK\'s GLES2/gl2.h and gl2ext.h: do not edit.')
    print('   Included by gl_server.c (GLES 2 build) inside its replay switch over `op`, with reader `rd_`. */')
    print('#ifdef GLES_NAMES')
    print('static const char *const op_names[] = {')
    for _, name, _, _ in protos:
        print(f'    "{name}",')
    print('};')
    for op, (_, name, _, _) in enumerate(protos):
        print(f'#define OP_{name} {op}')
    print('#else')
    for op, (ret, name, ps, ext) in enumerate(protos):
        k = kind(name, ext)
        if k in ('custom', 'local'):
            continue
        print(f'case {op}: {{ /* {name} */')
        inputs = INPUT.get(name, {})
        outs = dict(OUTPUT.get(name, []))
        args = []
        for t, n in ps:
            if is_ptr(t):
                print(f'    uint32_t {n}_v = get_u32(&rd_);')
                if name == 'glDrawElements' and n == 'indices':
                    args.append(f'(element_bound ? (const void *)(uintptr_t){n}_v : (const void *)index_scratch)')
                elif n in inputs:
                    args.append(f'({t}){n}_p')
                else:
                    args.append(f'({t})out_{n}')
            elif base(t) in ('GLfloat', 'GLclampf'):
                print(f'    {t} {n} = get_f32(&rd_);')
                args.append(n)
            else:
                print(f'    {t} {n} = ({t})get_u32(&rd_);')
                if n in MAPPED:
                    make = '1' if name.startswith('glBind') else '0'
                    print(f'    {n} = map_name(&{MAPPED[n]}, {n}, {make});')
                args.append(n)
        for t, n in ps:
            if is_ptr(t) and n in inputs:
                print(f'    const void *{n}_p = get_blob(&rd_, {n}_v);')
        # Outputs: buffers the real call writes into, sent back after it.
        for t, n in ps:
            if is_ptr(t) and n in outs:
                kd = outs[n]
                if kd == 'str':
                    bs = [b for tt, b in ps if b in ('bufsize', 'bufSize')][0]
                    print(f'    char *out_{n} = out_buffer({n}_v ? {bs} : 0);')
                else:
                    print(f'    uint8_t out_{n}[64]; memset(out_{n}, 0, sizeof out_{n});')
        if name in ('glDeleteFramebuffers', 'glDeleteRenderbuffers'):
            table = 'fb_names' if 'Frame' in name else 'rb_names'
            arr = ps[1][1]
            print(f'    delete_names(&{table}, n, (const GLuint *){arr}_p, {name}); break;\n}}')
            continue
        if name in ('glDeleteProgram', 'glDeleteShader'):
            arg = ps[0][1]
            print(f'    if ({arg}) {name}({arg}); forget_name(&prog_names, {arg}); break;\n}}')
            continue
        if name in ('glDrawArrays', 'glDrawElements'):
            print('    apply_attribs();')
        if name == 'glBindBuffer':
            print('    if (target == GL_ELEMENT_ARRAY_BUFFER) element_bound = buffer; if (target == GL_ARRAY_BUFFER) array_bound = buffer;')
        if name == 'glTexImage2D':
            print('    if (pixels_p) { pixels_p = upload("glTexImage2D", width, height, &internalformat, &format, type, pixels_p); if (pixels_p == (const void *)-1) break; }')
        if name == 'glTexSubImage2D':
            print('    if (pixels_p) { pixels_p = upload("glTexSubImage2D", width, height, NULL, &format, type, pixels_p); if (pixels_p == (const void *)-1) break; }')
        if name == 'glPixelStorei':
            print('    if (pname == 0x0CF5) unpack_alignment = param;')
        call = f'{name}({", ".join(args)})'
        if k == 'query':
            print(f'    uint32_t ret_ = 0;')
            print(f'    {"ret_ = (uint32_t)" if ret != "void" else ""}{call};')
            print('    answer_begin(ret_);')
            for t, n in ps:
                if is_ptr(t) and n in outs:
                    kd = outs[n]
                    elem = 1 if base(t) == 'GLboolean' else 4
                    if kd == 'vals':
                        cnt = 'get_count(pname)' if any(b == 'pname' for _, b in ps) else ('2' if n == 'range' else '1')
                        if name in ('glGetVertexAttribfv', 'glGetVertexAttribiv'):
                            cnt = '(pname == 0x8626 ? 4 : 1)'   # GL_CURRENT_VERTEX_ATTRIB
                        if name in ('glGetUniformfv', 'glGetUniformiv'):
                            cnt = 'uniform_count(program, location)'
                        print(f'    answer_add({n}_v ? out_{n} : NULL, (uint32_t)({cnt}) * {elem});')
                    elif kd == 'one':
                        print(f'    answer_add({n}_v ? out_{n} : NULL, 4);')
                    else:
                        print(f'    answer_add(out_{n}, out_{n} ? (uint32_t)strnlen(out_{n}, out_cap) + 1 : 0);')
            print('    break;\n}')
            continue
        print(f'    {call};' if ret == 'void' else f'    (void){call};')
        if name == 'glCompileShader':
            print('    check_compile(shader);')
        if name == 'glLinkProgram':
            print('    check_link(program);')
        print('    break;\n}')
    print('#endif')


def main():
    mode, paths = sys.argv[1], sys.argv[2:]
    protos = read_protos(paths)
    {'client': gen_client, 'server': gen_server}[mode](protos)


if __name__ == '__main__':
    main()
