/*
 * LUNACY_Send: one message (a header, then its payload) to the shell on one of the app's
 * sockets. Part of the Lunacy SDL video driver (SDL_lunacyvideo.c), LGPL 2.1 as it is; in a
 * header of its own so that LunaRuntimes/pdk/tests/send_test.c compiles and checks exactly
 * this code on the build machine. Include it in one translation unit only.
 */
#ifndef LUNACY_SEND_H
#define LUNACY_SEND_H

#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <stddef.h>
#include <stdint.h>
#include <unistd.h>

static int write_all(int fd, const void *p, size_t n)
{
	const char *c = p;
	while (n) {
		ssize_t w = write(fd, c, n);
		if (w < 0) {
			if (errno == EINTR) continue;
			/* A full socket: wait until the shell has read some, rather than spin. */
			if (errno == EAGAIN) { struct pollfd pf = { fd, POLLOUT, 0 }; poll(&pf, 1, -1); continue; }
			return -1;
		}
		c += w; n -= w;
	}
	return 0;
}

/* A message is a header and a payload written separately, so two threads sending on one socket
   could interleave them: each socket's sends go one at a time. Only each socket's: the video,
   audio, GL and PDL streams have sockets of their own, and a GL batch waiting for the shell to
   read (a full socket) mustn't hold up the audio, which the game's own loop waits on. One lock
   for every socket cost EMU7800 half its frame rate and kept Fruit Ninja's first frame off
   the screen on the Galaxy Tab A7 Lite (0.9.2's test round). */
#define SEND_LOCKS 64
static pthread_mutex_t send_locks[SEND_LOCKS] = { [0 ... SEND_LOCKS - 1] = PTHREAD_MUTEX_INITIALIZER };

int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len)
{
	uint32_t h[3] = { type, a, len };
	int r = 0;
	if (sock < 0) return -1;
	/* Two sockets can share a lock (their numbers 64 apart): that only makes them wait for each other. */
	pthread_mutex_t *lock = &send_locks[sock % SEND_LOCKS];
	pthread_mutex_lock(lock);
	if (write_all(sock, h, sizeof h) < 0 || (len && write_all(sock, payload, len) < 0)) r = -1;
	pthread_mutex_unlock(lock);
	return r;
}

#endif
