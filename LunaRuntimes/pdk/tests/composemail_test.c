/*
 * Palm's sends in ComposeMail form (libpreload/composemail.h), on the build machine.
 *
 *   cc -O2 -Wall -I../libpreload composemail_test.c -o composemail_test && ./composemail_test
 *
 * The SendMail bytes are the ones Exchange Online accepted from the build machine on 2026-11-06
 * (a message it delivered); the status reply is what it sent back to Palm's raw-MIME form.
 */
#include <stdio.h>
#include <string.h>

#include "composemail.h"

static int failures;
static void check(int ok, const char *what) { if (!ok) { fprintf(stderr, "composemail_test: FAIL: %s\n", what); failures++; } }

int main(void)
{
	struct cm_send s;

	check(cm_parse_url("https://outlook.office365.com/Microsoft-Server-ActiveSync?Cmd=SendMail&SaveInSent=T&User=a%40b&DeviceId=X&DeviceType=Palm", &s) == CM_SENDMAIL && s.save, "SendMail with SaveInSent");
	check(cm_parse_url("https://h/Microsoft-Server-ActiveSync?Cmd=SendMail&DeviceId=X", &s) == CM_SENDMAIL && !s.save, "SendMail without SaveInSent");
	check(cm_parse_url("https://h/Microsoft-Server-ActiveSync?Cmd=SmartReply&ItemId=8%3A12&CollectionId=8&SaveInSent=T&DeviceId=X", &s) == CM_SMARTREPLY
		&& strcmp(s.item, "8:12") == 0 && strcmp(s.folder, "8") == 0 && s.save, "SmartReply, its source decoded");
	check(cm_parse_url("https://h/Microsoft-Server-ActiveSync?Cmd=SmartForward&ItemId=3&CollectionId=8", &s) == CM_SMARTFORWARD, "SmartForward");
	check(cm_parse_url("https://h/Microsoft-Server-ActiveSync?Cmd=SmartReply&ItemId=3", &s) == 0, "a reply without its folder is left alone");
	check(cm_parse_url("https://h/Microsoft-Server-ActiveSync?Cmd=Sync&DeviceId=X", &s) == 0, "Sync isn't a send");
	check(cm_parse_url("https://h/Microsoft-Server-ActiveSync?DeviceId=X&Cmd=SendMail", &s) == CM_SENDMAIL, "Cmd anywhere in the query");

	unsigned char out[1024];
	cm_parse_url("https://h/x?Cmd=SendMail&SaveInSent=T", &s);
	size_t n = cm_prefix(&s, "probe1", 677, out);
	static const unsigned char want[] = { 0x03, 0x01, 0x6a, 0x00, 0x00, 0x15, 0x45,
		0x51, 0x03, 'p', 'r', 'o', 'b', 'e', '1', 0x00, 0x01,
		0x08, 0x50, 0xc3, 0x85, 0x25 };   /* 677 = 5 * 128 + 37 */
	check(n == sizeof want && memcmp(out, want, n) == 0, "SendMail prefix, as Exchange Online took it");

	n = cm_prefix(&s, "c", 5, out);
	check(n == 16 && out[n - 1] == 5, "a one-byte length");
	n = cm_prefix(&s, "c", 128 * 128 * 3 + 1, out);
	check(out[n - 3] == 0x83 && out[n - 2] == 0x80 && out[n - 1] == 0x01, "a three-byte length");

	cm_parse_url("https://h/x?Cmd=SmartReply&ItemId=8%3A1&CollectionId=8", &s);
	n = cm_prefix(&s, "c", 5, out);
	static const unsigned char reply[] = { 0x03, 0x01, 0x6a, 0x00, 0x00, 0x15, 0x47,
		0x51, 0x03, 'c', 0x00, 0x01,
		0x4b, 0x4c, 0x03, '8', 0x00, 0x01, 0x4d, 0x03, '8', ':', '1', 0x00, 0x01, 0x01,
		0x50, 0xc3, 0x05 };
	check(n == sizeof reply && memcmp(out, reply, n) == 0, "SmartReply prefix with its source, no SaveInSentItems");

	static const unsigned char refused[] = { 0x03, 0x01, 0x6a, 0x00, 0x00, 0x15, 0x45, 0x52, 0x03, '1', '0', '1', 0x00, 0x01, 0x01 };
	check(cm_status(refused, sizeof refused) == 101, "status 101 read");
	check(cm_status((const unsigned char *)"", 0) == 0, "an empty reply has no status");
	static const unsigned char sync[] = { 0x03, 0x01, 0x6a, 0x00, 0x45, 0x4e, 0x03, '1', 0x00, 0x01, 0x01 };
	check(cm_status(sync, sizeof sync) == 0, "another command's reply isn't a send status");

	if (failures) { fprintf(stderr, "composemail_test: %d failed\n", failures); return 1; }
	printf("composemail_test: ok\n");
	return 0;
}
