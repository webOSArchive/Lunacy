/*
 * Calendar items in a Sync request, made acceptable to an Exchange ActiveSync 16.x server
 * (Docs/outlook-eas.md).
 *
 * Palm's mojomail-eas writes a calendar item as EAS 12.1 had it. From 16.0 a server sets some
 * of its fields itself and refuses an item that carries them: Exchange Online answers such an
 * Add with status 6 (the item couldn't be converted), and nothing is stored (measured on the
 * Fold3, 2026-11-08: Palm's Add with UID, DtStamp and MeetingStatus refused). Where the version
 * adapter sends a request at 16.x, the request's calendar elements are rewritten:
 *
 * - DtStamp goes: the server stamps the item.
 * - UID becomes ClientUid (16.1) in an Add: the server makes the UID, and is told the
 *   client's. In a Change it goes: the item is the server's by then, named by its ServerId.
 * - OrganizerName and OrganizerEmail go: the organizer is the account.
 * - MeetingStatus goes: the server decides it from the attendees.
 * - Location, a string in 12.1, becomes AirSyncBase's Location with the string as its
 *   DisplayName (16.0 moved it there; the old element is refused).
 * - A Body's EstimatedDataSize goes: it is the server's to say, and a client's is refused.
 * - A Recurrence that doesn't say its first day of the week gets FirstDayOfWeek 0 (Sunday).
 *
 * Every other byte stays as it was. Requests are whole in memory (CURLOPT_POSTFIELDS), so this
 * rewrites a buffer rather than a stream. Measured against Exchange Online on 2026-11-08: Palm's
 * Add refused with status 6; with UID, DtStamp and MeetingStatus rewritten, an event without a
 * location stored; one with a location still refused until Location moved. Pure logic, tested on the build machine
 * (tests/calfilter_test.c).
 */
#ifndef LUNACY_CALFILTER_H
#define LUNACY_CALFILTER_H

#include <stddef.h>
#include <string.h>

#define CALF_PAGE 4                 /* the Calendar code page */
#define CALF_UID 0x28
#define CALF_CLIENT_UID 0x3c
#define CALF_DTSTAMP 0x11
#define CALF_ORGANIZER_EMAIL 0x19
#define CALF_ORGANIZER_NAME 0x1a
#define CALF_MEETING_STATUS 0x18
#define CALF_LOCATION 0x17
#define CALF_BASE_PAGE 17           /* AirSyncBase */
#define CALF_BASE_LOCATION 0x20
#define CALF_BASE_DISPLAY_NAME 0x10
#define CALF_BASE_ESTIMATED_SIZE 0x0c
#define CALF_RECURRENCE 0x1b
#define CALF_FIRST_DAY_OF_WEEK 0x39
#define CALF_MAX_DEPTH 32
#define CALF_AIRSYNC_CHANGE 0x08    /* AirSync (page 0) Change: a UID in it goes rather than become ClientUid */
/* The room out needs: a 12.1 Location (5 bytes at least) grows by 8, and a Recurrence (at
   least 2 bytes with its END) by 7, so four times the input does. */
#define CALF_OUT_CAP(n) (4 * (n) + 16)

static int calf_mb(const unsigned char *in, size_t n, size_t *i, unsigned long *v)
{
	*v = 0;
	for (int k = 0; k < 5; k++) {
		if (*i >= n) return -1;
		unsigned char c = in[(*i)++];
		*v = (*v << 7) | (c & 0x7f);
		if (!(c & 0x80)) return 0;
	}
	return -1;
}

static int calf_dropped(int page, int tag)
{
	return (page == CALF_PAGE && (tag == CALF_DTSTAMP || tag == CALF_ORGANIZER_EMAIL || tag == CALF_ORGANIZER_NAME || tag == CALF_MEETING_STATUS))
		|| (page == CALF_BASE_PAGE && tag == CALF_BASE_ESTIMATED_SIZE);
}

/*
 * Rewrites a WBXML request of n bytes into out, which has room for CALF_OUT_CAP(n) bytes.
 * Returns the bytes written, 0 when the request has no calendar element to change (out is then
 * not to be used), or (size_t)-1 when it isn't WBXML this understands (send it as it was).
 */
static size_t calf_rewrite(const unsigned char *in, size_t n, unsigned char *out)
{
	size_t i = 0, o = 0;
	unsigned long v;
	int page = 0, out_page = 0, skip = 0, changed = 0, depth = 0, fdow = 0;
	unsigned char recurrence[CALF_MAX_DEPTH] = { 0 };   /* the element open at each depth is a Recurrence */
	int change_depth = 0;                               /* > 0: inside a Change, opened at that depth */
	if (n < 4 || in[0] != 0x03) return (size_t)-1;
	i = 1;
	if (calf_mb(in, n, &i, &v) || calf_mb(in, n, &i, &v)) return (size_t)-1;   /* public id, charset */
	size_t table_at = i;
	if (calf_mb(in, n, &i, &v) || v > n - i) return (size_t)-1;              /* string table */
	i += v;
	memcpy(out, in, i); o = i;
	(void)table_at;
	while (i < n) {
		unsigned char c = in[i++];
		switch (c) {
		case 0x00:                                         /* SWITCH_PAGE: written before the next kept tag */
			if (i >= n) return (size_t)-1;
			page = in[i++];
			continue;
		case 0x01:                                         /* END */
			if (skip) { skip--; continue; }
			if (depth > 0 && depth <= CALF_MAX_DEPTH && recurrence[depth - 1]) {
				/* The end of a Recurrence: the first day of the week, if it didn't say. */
				recurrence[depth - 1] = 0;
				if (!fdow) {
					if (out_page != CALF_PAGE) { out[o++] = 0x00; out[o++] = CALF_PAGE; out_page = CALF_PAGE; }
					out[o++] = 0x40 | CALF_FIRST_DAY_OF_WEEK; out[o++] = 0x03; out[o++] = '0'; out[o++] = 0x00; out[o++] = 0x01;
					changed = 1;
				}
			}
			if (depth > 0) depth--;
			if (change_depth && depth < change_depth) change_depth = 0;
			out[o++] = c; continue;
		case 0x03: {                                       /* STR_I */
			const unsigned char *z = memchr(in + i, 0, n - i);
			if (!z) return (size_t)-1;
			size_t len = (size_t)(z - (in + i)) + 1;
			if (!skip) { out[o++] = c; memcpy(out + o, in + i, len); o += len; }
			i += len;
			continue;
		}
		case 0xc3: {                                       /* OPAQUE */
			size_t at = i;
			if (calf_mb(in, n, &i, &v) || v > n - i) return (size_t)-1;
			if (!skip) { out[o++] = c; memcpy(out + o, in + at, i - at + v); o += i - at + v; }
			i += v;
			continue;
		}
		case 0x02: case 0x04: case 0x40: case 0x41: case 0x42: case 0x43: case 0x44:
		case 0x80: case 0x81: case 0x82: case 0x83: case 0x84: case 0xc0: case 0xc1: case 0xc2: case 0xc4:
			return (size_t)-1;                             /* nothing EAS sends */
		default: {
			int tag = c & 0x3f, content = (c & 0x40) != 0;
			if (skip) { if (content) skip++; continue; }
			if (calf_dropped(page, tag) || (change_depth && page == CALF_PAGE && tag == CALF_UID)) { changed = 1; if (content) skip = 1; continue; }
			if (page == CALF_PAGE && tag == CALF_LOCATION) {
				changed = 1;
				/* <Location>STR_I "..."</Location> becomes base:Location/base:DisplayName; anything
				   else (empty, or not a plain string) goes, as the server would refuse it. */
				const unsigned char *z = content && i < n && in[i] == 0x03 ? memchr(in + i + 1, 0, n - i - 1) : NULL;
				if (z && (size_t)(z - in) + 1 < n && z[1] == 0x01) {
					size_t len = (size_t)(z - (in + i + 1)) + 1;   /* the string and its NUL */
					out[o++] = 0x00; out[o++] = CALF_BASE_PAGE; out_page = CALF_BASE_PAGE;
					out[o++] = 0x40 | CALF_BASE_LOCATION; out[o++] = 0x40 | CALF_BASE_DISPLAY_NAME;
					out[o++] = 0x03; memcpy(out + o, in + i + 1, len); o += len;
					out[o++] = 0x01; out[o++] = 0x01;
					i += 1 + len + 1;                               /* STR_I, the string, END */
				} else if (content) skip = 1;
				continue;
			}
			if (out_page != page) { out[o++] = 0x00; out[o++] = (unsigned char)page; out_page = page; }
			if (page == CALF_PAGE && tag == CALF_UID) { c = (unsigned char)((c & 0xc0) | CALF_CLIENT_UID); changed = 1; }
			if (page == CALF_PAGE && tag == CALF_FIRST_DAY_OF_WEEK) fdow = 1;
			if (content) {
				if (depth < CALF_MAX_DEPTH) recurrence[depth] = page == CALF_PAGE && tag == CALF_RECURRENCE;
				if (page == CALF_PAGE && tag == CALF_RECURRENCE) fdow = 0;
				depth++;
				if (page == 0 && tag == CALF_AIRSYNC_CHANGE) change_depth = depth;
			}
			out[o++] = c;
		}
		}
	}
	return changed ? o : 0;
}

#endif
