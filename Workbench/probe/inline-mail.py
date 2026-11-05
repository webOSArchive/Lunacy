#!/usr/bin/env python3
"""A test email with inline images, into the dummy account (Workbench/dummy-mail.sh).

    inline-mail.py [smtp host] [web host]

Sends one message to dummy@lunacy.test through the dummy server's SMTP (127.0.0.1:3025 by
default): HTML with two images carried in the message (multipart/related, cid: references -
Email shows them from the file cache, file:///var/file-cache), one remote image from [web host]
(Workbench/probe/web served there, port 8000), a mailto: link and a target="_blank" link.
"""
import io, smtplib, sys
from email.mime.image import MIMEImage
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from PIL import Image, ImageDraw

smtp = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
web = sys.argv[2] if len(sys.argv) > 2 else "192.168.10.45"

def png(w, h, colour, label):
    im = Image.new("RGB", (w, h), colour)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], outline=(0, 0, 0), width=3)
    d.text((12, h // 2 - 6), label, fill=(0, 0, 0))
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()

html = f"""<html><body style="font-family:sans-serif">
<p>Inline images test. Two images are carried in this message:</p>
<p><img src="cid:inline-one" width="300" height="120" alt="inline one"></p>
<p>Some text between them, then the second, smaller:</p>
<p><img src="cid:inline-two" alt="inline two"></p>
<p>One remote image, loaded from {web}:8000 (Email may ask before loading remote images):</p>
<p><img src="http://{web}:8000/remote.png" alt="remote"></p>
<p><a href="mailto:dummy@lunacy.test?subject=Reply%20from%20a%20mailto%20link">A mailto: link</a> -
it should open a new message in Email.</p>
<p><a href="http://{web}:8000/links.html" target="_blank">A target="_blank" link</a> -
it should open in the Web app.</p>
</body></html>"""

msg = MIMEMultipart("related")
msg["From"] = "Image Tester <images@example.org>"
msg["To"] = "Dummy <dummy@lunacy.test>"
msg["Subject"] = "Inline images, a mailto: and a _blank link"
alt = MIMEMultipart("alternative")
alt.attach(MIMEText("Inline images test. Open in an HTML view to see the images.", "plain"))
alt.attach(MIMEText(html, "html"))
msg.attach(alt)
for cid, data in (("inline-one", png(300, 120, (120, 200, 255), "INLINE ONE (cid:inline-one)")),
                  ("inline-two", png(160, 80, (255, 200, 120), "INLINE TWO"))):
    part = MIMEImage(data, "png")
    part.add_header("Content-ID", f"<{cid}>")
    part.add_header("Content-Disposition", "inline", filename=f"{cid}.png")
    msg.attach(part)

with smtplib.SMTP(smtp, 3025) as s:
    s.send_message(msg)
print("sent:", msg["Subject"])
