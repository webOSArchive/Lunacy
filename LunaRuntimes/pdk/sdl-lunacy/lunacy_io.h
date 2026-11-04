/*
 * Reading the shell's socket, for the libraries on the app's side of it (libpdl, the GLES
 * clients): one copy of the loop that reads a whole message or fails.
 */
#ifndef LUNACY_IO_H
#define LUNACY_IO_H

#include <errno.h>
#include <stddef.h>
#include <unistd.h>

/* Exactly n bytes from fd, through interruptions; -1 on an error or the end of the stream. */
static int read_all(int fd, void *p, size_t n)
{
	char *c = p;
	while (n) {
		ssize_t r = read(fd, c, n);
		if (r < 0 && errno == EINTR) continue;
		if (r <= 0) return -1;
		c += r; n -= (size_t)r;
	}
	return 0;
}

#endif
