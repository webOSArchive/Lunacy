#!/bin/sh
# A throwaway mail server for checking Email's layout on a device that can't reach a real one
# (the Nexus 5's certificate store is too old for Fastmail). GreenMail in Docker, plain IMAP and
# SMTP with no TLS, on this machine's loopback only; a device reaches it over USB through
# `adb reverse`. See Docs/dev-workflow.md, "A dummy mail account".
#
#   ./dummy-mail.sh start            start the server and seed it (nine messages, three folders)
#   ./dummy-mail.sh seed             seed it again (adds the messages once more)
#   ./dummy-mail.sh connect [serial] forward the device's 127.0.0.1:3143 and :3025 to the server
#   ./dummy-mail.sh stop [serial]    remove the container (and the device's forwards)
#
# The account on the device: Email > Email Account, dummy@lunacy.test / dummy, Sign In; the
# lookup fails and Manual Setup opens: IMAP, incoming 127.0.0.1 port 3143, outgoing 127.0.0.1
# port 3025, no encryption, the same user and password both ways.
set -e
NAME=lunacy-dummy-mail
IMAGE=greenmail/standalone:2.0.1

seed() {
    python3 -I - <<'EOF'
import smtplib, imaplib, email.utils, time
from email.mime.text import MIMEText
long = "\n".join("Paragraph %d. The software has to recover from overloads gracefully, dropping lower "
                 "priority tasks and keeping the ones that matter for the landing." % i for i in range(1, 13))
msgs = [
 ("Ada Lovelace <ada@example.org>", "Notes on the Analytical Engine", "I've attached nothing, but here are my thoughts on the engine's\ncapacity to compose elaborate pieces of music.\n\nAda", "plain"),
 ("Grace Hopper <grace@example.org>", "Found a bug", "There was a moth in relay 70, panel F. Taped it into the log book.\n\n-- Grace", "plain"),
 ("webOS Archive <news@example.org>", "This week in the archive", "<h2>This week</h2><p>Three new apps restored, and the <b>App Museum</b> has a new look.</p><ul><li>Kalemsoft Player</li><li>Glimpse</li><li>Quick Office</li></ul>", "html"),
 ("Alan Turing <alan@example.org>", "Lunch on Thursday?", "Are you free for lunch on Thursday? I'll be near the lab around noon.", "plain"),
 ("Alan Turing <alan@example.org>", "Re: Lunch on Thursday?", "Noon works. See you at the usual place.", "plain"),
 ("Margaret Hamilton <mh@example.org>", "Priority displays", long, "plain"),
 ("Linus <linus@example.org>", "A very long subject line that keeps going well past the width of a phone's message list to see where it gets cut", "Short body.", "plain"),
 ("Calendar <noreply@example.org>", "Reminder: TouchPad meetup at 7 pm", "Don't forget your charger.", "plain"),
 # An email wider than a phone's pane, as most newsletters are: it should be zoomed out to fit.
 ("Wide News <wide@example.org>", "A 600 px newsletter", '<html><body style="margin:0"><table width="600" cellpadding="12" style="background:#eef;border:1px solid #99c"><tr><td><h1 style="font-family:Arial">The Wide Newsletter</h1><p style="font-family:Arial;font-size:15px">This table is 600 px wide, as most newsletters are. It should be zoomed out to fit the pane.</p></td></tr></table></body></html>', "html"),
]
s = smtplib.SMTP("127.0.0.1", 3025)
for i, (sender, subject, body, kind) in enumerate(msgs):
    m = MIMEText(body, kind, "utf-8")
    m["From"] = sender; m["To"] = "Dummy <dummy@lunacy.test>"; m["Subject"] = subject
    m["Date"] = email.utils.formatdate(time.time() - (len(msgs) - i) * 3600 * 5, localtime=True)
    m["Message-ID"] = email.utils.make_msgid(domain="example.org")
    s.sendmail(sender.split("<")[1][:-1], ["dummy@lunacy.test"], m.as_string())
s.quit()
c = imaplib.IMAP4("127.0.0.1", 3143)
c.login("dummy@lunacy.test", "dummy")
for f in ("Sent", "Drafts", "Trash"):
    c.create(f)
c.logout()
print("seeded %d messages; folders INBOX, Sent, Drafts, Trash" % len(msgs))
EOF
}

case "$1" in
start)
    docker run -d --name $NAME -p 127.0.0.1:3143:3143 -p 127.0.0.1:3025:3025 \
        -e GREENMAIL_OPTS="-Dgreenmail.setup.test.imap -Dgreenmail.setup.test.smtp -Dgreenmail.hostname=0.0.0.0 -Dgreenmail.users=dummy:dummy@lunacy.test -Dgreenmail.users.login=email -Dgreenmail.auth.disabled" \
        $IMAGE >/dev/null
    # GreenMail takes a few seconds to open its ports.
    for i in 1 2 3 4 5 6 7 8 9 10; do
        python3 -I -c "import socket; socket.create_connection(('127.0.0.1', 3025), 1)" 2>/dev/null && break
        sleep 1
    done
    seed ;;
seed) seed ;;
connect)
    adb ${2:+-s $2} reverse tcp:3143 tcp:3143
    adb ${2:+-s $2} reverse tcp:3025 tcp:3025
    echo "the device's 127.0.0.1:3143 (IMAP) and :3025 (SMTP) reach the server" ;;
stop)
    adb ${2:+-s $2} reverse --remove tcp:3143 2>/dev/null || true
    adb ${2:+-s $2} reverse --remove tcp:3025 2>/dev/null || true
    docker rm -f $NAME >/dev/null && echo "removed $NAME" ;;
*) echo "usage: $0 start|seed|connect [serial]|stop [serial]" >&2; exit 1 ;;
esac
