/* The GLES 2 calls the client writes by hand (client2_prelude.c), replayed: included by
   gl_server.c inside its replay switch, after the generated cases. */
case OP_glCreateShader: {
	GLenum type = get_u32(&rd_); GLuint name = get_u32(&rd_);
	GLuint real = glCreateShader(type);
	if (real) add_name(&prog_names, name, real);
	break;
}
case OP_glCreateProgram: {
	GLuint name = get_u32(&rd_);
	GLuint real = glCreateProgram();
	if (real) add_name(&prog_names, name, real);
	break;
}
case OP_glShaderSource: {
	GLuint shader = map_name(&prog_names, get_u32(&rd_), 0);
	const char *src = get_blob(&rd_, 1);
	if (src && blob_len) { GLint n = (GLint)strnlen(src, blob_len); glShaderSource(shader, 1, &src, &n); }
	break;
}
case OP_glVertexAttribPointer: {
	GLuint index = get_u32(&rd_); GLint size = (GLint)get_u32(&rd_); GLenum type = get_u32(&rd_);
	GLboolean normalized = (GLboolean)get_u32(&rd_); GLsizei stride = (GLsizei)get_u32(&rd_); uint32_t ptr = get_u32(&rd_);
	/* In a buffer object the pointer is an offset, set now; from the app's memory it comes with the draw. */
	if (array_bound) glVertexAttribPointer(index, size, type, normalized, stride, (const void *)(uintptr_t)ptr);
	else if (index < ARRAYS) { arrays[index].size = size; arrays[index].type = type; arrays[index].normalized = normalized; arrays[index].stride = stride; }
	break;
}
