/*
 * LUNACY_Send, on the build machine (lunacy_send.h is the code the SDL driver compiles).
 *
 *   cc -O2 -pthread -I../sdl-lunacy send_test.c -o send_test && ./send_test
 *
 * Two promises, each one a regression we have had or guarded against:
 *
 * 1. One socket never holds up another. 0.9.2's test round: one lock for every socket meant a
 *    GL batch waiting on a full socket (the shell still replaying the last) held up the audio
 *    and everything else; EMU7800 lost half its frame rate and Fruit Ninja's first frame never
 *    showed on the Galaxy Tab A7 Lite. Here a sender blocks on a socket nobody reads, and a
 *    message on a second socket must still go through, at once.
 *
 * 2. One socket's messages never interleave. A message is a header and a payload written
 *    separately; several threads sending on one socket (as the video, audio and PDL threads
 *    once shared one) must each get theirs out whole. Here four threads send at once and the
 *    reader checks every header against its payload.
 *
 * Exits 0 when both hold; prints what broke and exits 1 otherwise.
 */
#define _GNU_SOURCE
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#include "lunacy_send.h"

static int fail(const char *what) { fprintf(stderr, "send_test: FAIL: %s\n", what); return 1; }

static double now(void)
{
	struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t);
	return t.tv_sec + t.tv_nsec / 1e9;
}

static int read_all_fd(int fd, void *p, size_t n)
{
	char *c = p;
	while (n) { ssize_t r = read(fd, c, n); if (r <= 0) return -1; c += r; n -= (size_t)r; }
	return 0;
}

/* ---- 1: a full socket doesn't hold up another ---- */

struct blocker { int fd; int done; };

/* Sends far more than a socket buffer holds to a socket nobody reads: it blocks in LUNACY_Send. */
static void *block_on_full(void *arg)
{
	struct blocker *b = arg;
	size_t big = 8u << 20;
	char *payload = calloc(1, big);
	LUNACY_Send(b->fd, 6, 0, payload, (uint32_t)big);
	free(payload);
	__atomic_store_n(&b->done, 1, __ATOMIC_SEQ_CST);
	return NULL;
}

struct sender { int fd; int result; double took; };

static void *send_small(void *arg)
{
	struct sender *s = arg;
	double t0 = now();
	s->result = LUNACY_Send(s->fd, 5, 1, "audio", 5);
	s->took = now() - t0;
	return NULL;
}

static int test_independent_sockets(void)
{
	int full[2], other[2];
	if (socketpair(AF_UNIX, SOCK_STREAM, 0, full) || socketpair(AF_UNIX, SOCK_STREAM, 0, other)) return fail("socketpair");
	/* The app's sockets are non-blocking: a full one answers EAGAIN, and the sender polls. */
	fcntl(full[0], F_SETFL, O_NONBLOCK);
	fcntl(other[0], F_SETFL, O_NONBLOCK);

	struct blocker b = { full[0], 0 };
	pthread_t tb; pthread_create(&tb, NULL, block_on_full, &b);
	usleep(200 * 1000);   /* long enough to fill the socket and block in it */
	if (__atomic_load_n(&b.done, __ATOMIC_SEQ_CST)) return fail("the big send didn't block: the test can't tell anything");

	struct sender s = { other[0], -2, 0 };
	pthread_t ts; pthread_create(&ts, NULL, send_small, &s);
	double t0 = now();
	while (s.result == -2 && now() - t0 < 2.0) usleep(1000);
	if (s.result == -2) {
		fprintf(stderr, "send_test: FAIL: a send on one socket waited for another socket's full buffer "
			"(one lock for every socket?)\n");
		return 1;   /* both threads stay blocked; the process exits */
	}
	if (s.result != 0) return fail("the second socket's send failed");
	uint32_t h[3]; char body[5];
	if (read_all_fd(other[1], h, sizeof h) || h[0] != 5 || h[1] != 1 || h[2] != 5 || read_all_fd(other[1], body, 5) || memcmp(body, "audio", 5))
		return fail("the second socket's message came out wrong");

	/* Let the blocked sender finish, so nothing is left behind. */
	char sink[65536];
	fcntl(full[1], F_SETFL, O_NONBLOCK);
	t0 = now();
	while (!__atomic_load_n(&b.done, __ATOMIC_SEQ_CST) && now() - t0 < 10.0) {
		if (read(full[1], sink, sizeof sink) <= 0) usleep(1000);
	}
	if (!__atomic_load_n(&b.done, __ATOMIC_SEQ_CST)) return fail("the big send never finished once read");
	pthread_join(tb, NULL); pthread_join(ts, NULL);
	printf("send_test: a full socket doesn't hold up another (the other send took %.1f ms)\n", s.took * 1000);
	close(full[0]); close(full[1]); close(other[0]); close(other[1]);
	return 0;
}

/* ---- 2: one socket's messages stay whole ---- */

#define THREADS 4
#define MESSAGES 2000

struct writer { int fd; uint32_t id; };

/* Each message's payload is its own header's numbers, repeated, so a mixed-up one shows. */
static void *write_many(void *arg)
{
	struct writer *w = arg;
	uint32_t payload[64];
	for (uint32_t i = 0; i < MESSAGES; i++) {
		uint32_t words = 1 + (i * 7 + w->id) % 64;
		for (uint32_t k = 0; k < words; k++) payload[k] = (w->id << 24) | i;
		if (LUNACY_Send(w->fd, w->id, i, payload, words * 4) < 0) return (void *)1;
	}
	return NULL;
}

static int test_whole_messages(void)
{
	int sv[2];
	if (socketpair(AF_UNIX, SOCK_STREAM, 0, sv)) return fail("socketpair");
	/* A small buffer, so the writes are cut short and the threads really meet. */
	int small = 4096;
	setsockopt(sv[0], SOL_SOCKET, SO_SNDBUF, &small, sizeof small);
	setsockopt(sv[1], SOL_SOCKET, SO_RCVBUF, &small, sizeof small);
	fcntl(sv[0], F_SETFL, O_NONBLOCK);

	pthread_t t[THREADS]; struct writer w[THREADS];
	for (int i = 0; i < THREADS; i++) { w[i].fd = sv[0]; w[i].id = (uint32_t)(i + 1); pthread_create(&t[i], NULL, write_many, &w[i]); }

	uint32_t next[THREADS + 1] = { 0 };
	for (int got = 0; got < THREADS * MESSAGES; got++) {
		uint32_t h[3], payload[64];
		if (read_all_fd(sv[1], h, sizeof h)) return fail("the stream ended early");
		uint32_t id = h[0], i = h[1], len = h[2];
		if (id < 1 || id > THREADS || len == 0 || len > sizeof payload || len % 4) {
			fprintf(stderr, "send_test: FAIL: message %d has a broken header (%u %u %u): messages interleaved\n", got, id, i, len);
			return 1;
		}
		if (read_all_fd(sv[1], payload, len)) return fail("the stream ended in a payload");
		for (uint32_t k = 0; k < len / 4; k++) if (payload[k] != ((id << 24) | i)) {
			fprintf(stderr, "send_test: FAIL: thread %u's message %u carries another's bytes: messages interleaved\n", id, i);
			return 1;
		}
		if (i != next[id]) { fprintf(stderr, "send_test: FAIL: thread %u's messages out of order (%u, wanted %u)\n", id, i, next[id]); return 1; }
		next[id]++;
	}
	for (int i = 0; i < THREADS; i++) { void *r; pthread_join(t[i], &r); if (r) return fail("a send failed"); }
	printf("send_test: %d threads, %d messages each, on one socket: every one whole and in order\n", THREADS, MESSAGES);
	close(sv[0]); close(sv[1]);
	return 0;
}

int main(void)
{
	if (test_independent_sockets()) return 1;
	if (test_whole_messages()) return 1;
	printf("send_test: ok\n");
	return 0;
}
