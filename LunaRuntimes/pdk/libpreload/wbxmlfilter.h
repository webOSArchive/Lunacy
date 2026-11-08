/*
 * Exchange ActiveSync replies, cut down to what Palm's mojomail-eas can parse (Docs/oauth.md).
 *
 * EAS sends WBXML: tags in numbered code pages. Palm's service knows pages 0 to 20 (AirSync to
 * ItemOperations, EAS 12.1) and aborts on any other (EasTagCode::BuildId asserts
 * `c <= ItemOperations`). A server talking 16.1 to it - Exchange Online since March 2026 - puts
 * newer pages in its replies: ComposeMail (21), Email2 (22: conversation ids, last verb), Notes
 * (23), RightsManagement (24), Find (25). This drops every element whose tag is in a page above
 * MAX_PAGE, with all it contains, and keeps the page switches right for what follows: a switch is
 * written out only before a tag that is kept, so the service sees the pages it would have seen
 * from a 12.1 server. Strings and opaque data inside dropped elements go too. Everything else
 * passes byte for byte.
 *
 * Two changes for calendar exceptions at 16.x (Docs/outlook-eas.md, "Recurring exceptions"):
 * - Inside a calendar Exception, AirSyncBase's InstanceId (16.0's name for the occurrence) is
 *   written as Calendar's ExceptionStartTime, the same compact date, which is all Palm reads
 *   (without it Palm kept the occurrence as an empty exdate, measured 2026-11-08).
 * - An entry of a Sync's Responses that carries an InstanceId answers a command Palm never sent
 *   (calfilter.h's occurrence Changes and Deletes; a repeated Delete gets status 5): it is held
 *   back until its end and dropped. An entry longer than WBXF_HOLD passes as it is.
 *
 * Streaming: a reply arrives in pieces of any size, and opaque data (a message's MIME) can be
 * large, so nothing is held but the bytes of a token cut in two. Pure logic, tested on the build
 * machine (tests/wbxmlfilter_test.c).
 */
#ifndef LUNACY_WBXMLFILTER_H
#define LUNACY_WBXMLFILTER_H

#include <stddef.h>
#include <string.h>

#define WBXF_MAX_PAGE 20
#define WBXF_HOLD 4096        /* the longest Responses entry held back to be looked at */

struct wbxf {
	int stage;            /* 0 header, 1 body, 2 opaque payload, 3 inline string, 9 not WBXML: pass */
	int header_left;      /* header fields still to pass: version, public id, charset, string table length */
	unsigned long mb;     /* a multi-byte integer being read */
	int mb_for;           /* what it is for: 1 header field, 2 string table length, 3 opaque length, 4 switch page, 5 skip (entity, STR_T) */
	unsigned long left;   /* bytes of the string table or opaque payload still to go */
	int keep_payload;     /* the opaque payload being read is written out */
	int keep_string;      /* the inline string being read is written out */
	int cur_page;         /* the page the reply is in */
	int out_page;         /* the page the service was last told */
	int skip_depth;       /* > 0: inside a dropped element */
	unsigned char held[8];/* an opaque token's start, written once its length is read */
	int held_len;
	int depth;            /* elements open (of those written) */
	int exc_depth;        /* > 0: inside a calendar Exception opened at this depth */
	int resp_depth;       /* > 0: inside a Sync's Responses opened at this depth */
	int holding;          /* > 0: a Responses entry, opened at this depth, is being held back */
	int hold_iid;         /* the held entry carries an InstanceId */
	int hold_page;        /* out_page when holding began */
	size_t hold_len;
	unsigned char hold[WBXF_HOLD];
};

static void wbxf_init(struct wbxf *f) { memset(f, 0, sizeof *f); }

/* One byte out: to the held entry, or to out (an entry too long to hold goes out as it is). */
#define WBXF_PUT(x) do { unsigned char wbxf_c_ = (unsigned char)(x); \
	if (f->holding) { \
		if (f->hold_len < WBXF_HOLD) { f->hold[f->hold_len++] = wbxf_c_; break; } \
		memcpy(out + o, f->hold, f->hold_len); o += f->hold_len; f->hold_len = 0; f->holding = 0; \
	} \
	out[o++] = wbxf_c_; } while (0)

/* Filters n bytes of a reply into out, which holds at least n + 16 + WBXF_HOLD bytes (a page
   switch written late, an opaque token's start held over from the last piece, and a held
   Responses entry can be added); returns the bytes written. */
static size_t wbxf_feed(struct wbxf *f, const unsigned char *in, size_t n, unsigned char *out)
{
	size_t o = 0;
	for (size_t i = 0; i < n; i++) {
		unsigned char c = in[i];
		if (f->stage == 9) { out[o++] = c; continue; }
		if (f->stage == 0) {
			/* version, public id (mb), charset (mb), string table length (mb) and the table */
			if (f->header_left == 0 && f->mb_for == 0 && f->left == 0) {
				if (c != 0x03) { f->stage = 9; out[o++] = c; continue; }   /* not WBXML 1.3: leave it */
				f->header_left = 3; out[o++] = c; continue;
			}
			out[o++] = c;
			if (f->left) { f->left--; if (!f->left && !f->header_left) f->stage = 1; continue; }
			f->mb = (f->mb << 7) | (c & 0x7f);
			if (c & 0x80) continue;
			if (--f->header_left == 0) {      /* that was the string table's length */
				f->left = f->mb;
				if (!f->left) f->stage = 1;
			}
			f->mb = 0;
			continue;
		}
		if (f->stage == 2) {              /* opaque payload */
			if (f->keep_payload) WBXF_PUT(c);
			if (--f->left == 0) f->stage = 1;
			continue;
		}
		if (f->stage == 3) {              /* inline string, to its NUL */
			if (f->keep_string) WBXF_PUT(c);
			if (c == 0) f->stage = 1;
			continue;
		}
		if (f->mb_for) {                  /* the multi-byte integer after a token */
			if (f->mb_for == 3 && f->keep_payload) f->held[f->held_len++] = c;
			f->mb = (f->mb << 7) | (c & 0x7f);
			if (c & 0x80) { if (f->held_len >= (int)sizeof f->held - 1) f->stage = 9; continue; }
			if (f->mb_for == 4) {
				f->cur_page = (int)f->mb;
			} else if (f->mb_for == 3) {
				if (f->keep_payload) for (int k = 0; k < f->held_len; k++) WBXF_PUT(f->held[k]);
				f->left = f->mb;
				if (f->left) f->stage = 2;
			}
			f->mb_for = 0; f->mb = 0; f->held_len = 0;
			continue;
		}
		/* a token */
		switch (c) {
		case 0x00: f->mb_for = 4; f->mb = 0; continue;                 /* SWITCH_PAGE: written lazily */
		case 0x01:                                                    /* END */
			if (f->skip_depth) { f->skip_depth--; continue; }
			WBXF_PUT(c);
			if (f->depth == f->exc_depth) f->exc_depth = 0;
			if (f->depth == f->resp_depth) f->resp_depth = 0;
			if (f->holding && f->depth == f->holding) {          /* the held entry's end */
				if (f->hold_iid) f->out_page = f->hold_page;       /* dropped, and the switches in it */
				else { memcpy(out + o, f->hold, f->hold_len); o += f->hold_len; }
				f->holding = 0; f->hold_len = 0;
			}
			if (f->depth) f->depth--;
			continue;
		case 0x03:                                                    /* STR_I */
			f->keep_string = !f->skip_depth;
			if (f->keep_string) WBXF_PUT(c);
			f->stage = 3; continue;
		case 0xc3:                                                    /* OPAQUE */
			f->keep_payload = !f->skip_depth;
			f->held_len = 0;
			if (f->keep_payload) f->held[f->held_len++] = c;
			f->mb_for = 3; f->mb = 0; continue;
		case 0x02: case 0x83:                                         /* ENTITY, STR_T: not in EAS; dropped */
		case 0x04: case 0x40: case 0x41: case 0x42: case 0x43: case 0x44:
		case 0x80: case 0x81: case 0x82: case 0x84: case 0xc0: case 0xc1: case 0xc2: case 0xc4:
			if (f->holding) { memcpy(out + o, f->hold, f->hold_len); o += f->hold_len; f->holding = 0; f->hold_len = 0; }
			f->stage = 9; out[o++] = c; continue;                     /* something EAS never sends: stop filtering */
		default: {                                                    /* a tag */
			int content = (c & 0x40) != 0;
			if (f->skip_depth) { if (content) f->skip_depth++; continue; }
			if (f->cur_page > WBXF_MAX_PAGE) { if (content) f->skip_depth = 1; continue; }
			int page = f->cur_page, tag = c & 0x3f;
			/* A Responses entry: held back until its end. */
			if (f->resp_depth && f->depth == f->resp_depth && content && !f->holding) {
				f->holding = f->depth + 1; f->hold_iid = 0; f->hold_page = f->out_page; f->hold_len = 0;
			}
			if (page == 17 && tag == 0x2d) {                       /* AirSyncBase InstanceId */
				if (f->holding) f->hold_iid = 1;
				if (f->exc_depth) { page = 4; tag = 0x16; c = (unsigned char)((c & 0xc0) | tag); }   /* ExceptionStartTime */
			}
			if (f->out_page != page) { WBXF_PUT(0x00); WBXF_PUT(page); f->out_page = page; }
			WBXF_PUT(c);
			if (content) {
				f->depth++;
				if (page == 4 && tag == 0x13) f->exc_depth = f->depth;         /* Exception */
				if (page == 0 && tag == 0x06) f->resp_depth = f->depth;        /* Responses */
			}
		}
		}
	}
	return o;
}

#endif
