#!/usr/bin/env python3
"""
A stand-in OAuth provider with XOAUTH2 mail, for testing Lunacy's OAuth sign-in end to end
without Google's or Microsoft's servers (Docs/oauth.md, "Testing").

  python3 -I fake-provider.py [--host 192.168.10.45] [--expires 120]

Serves, on the host given (a LAN address the test device reaches):
  :8091  OAuth 2: /authorize (a page with Allow and Deny), /token (authorization_code with
         PKCE S256, refresh_token). Access tokens last --expires seconds, so a refresh shows
         up within a test. The id_token names dummy@lunacy.test.
  :3145  IMAP in front of the dummy GreenMail (Workbench/dummy-mail.sh, 127.0.0.1:3143):
         takes `AUTHENTICATE XOAUTH2` with a live token, as Gmail does (a refused token gets
         the + challenge first), and refuses LOGIN, so a login that works went through
         Lunacy's translation. Everything after the login is passed through.
  :3027  SMTP in front of GreenMail's 127.0.0.1:3025, offering `AUTH LOGIN XOAUTH2` as
         Microsoft's does (no PLAIN), taking XOAUTH2 only.
Plain connections, no TLS: the device's account uses encryption "none".

`/admin/revoke` refuses every refresh token issued so far (the account must sign in again);
`/admin/expire` makes every access token stale at once.
"""
import argparse, asyncio, base64, hashlib, html, json, secrets, threading, time, urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

EMAIL = "dummy@lunacy.test"
codes = {}      # code -> {challenge, redirect_uri, client_id}
access = {}     # token -> expiry
refresh = set()
args = None


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def b64url(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def id_token():
    head = b64url(json.dumps({"alg": "none", "typ": "JWT"}).encode())
    body = b64url(json.dumps({"iss": "fake-provider", "email": EMAIL, "aud": "lunacy-test", "iat": int(time.time())}).encode())
    return head + "." + body + "."


def issue(with_refresh=True):
    t = "fake-at-" + secrets.token_urlsafe(24)
    access[t] = time.time() + args.expires
    out = {"access_token": t, "token_type": "Bearer", "expires_in": args.expires, "id_token": id_token(),
           "scope": "mail openid email"}
    if with_refresh:
        r = "fake-rt-" + secrets.token_urlsafe(24)
        refresh.add(r)
        out["refresh_token"] = r
    return out


class OAuthHandler(BaseHTTPRequestHandler):
    def send(self, code, body, ctype="application/json", headers=()):
        data = body.encode() if isinstance(body, str) else body
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        for k, v in headers:
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *a):
        log("http", self.address_string(), fmt % a)

    def do_GET(self):
        u = urllib.parse.urlparse(self.path)
        q = dict(urllib.parse.parse_qsl(u.query))
        if u.path == "/authorize":
            # PKCE for a public client (Lunacy on the device); a confidential one (the broker) has its secret.
            missing = [k for k in ("client_id", "redirect_uri", "state") if k not in q]
            if missing or q.get("response_type") != "code":
                return self.send(400, "bad authorize request: missing %s" % missing, "text/plain")
            hidden = "".join('<input type=hidden name="%s" value="%s">' % (html.escape(k), html.escape(v)) for k, v in q.items())
            page = ("<!DOCTYPE html><html><head><meta name=viewport content='width=device-width'><title>Fake Provider</title></head>"
                    "<body style='font-family:sans-serif;margin:2em'><h2>Fake Provider</h2>"
                    "<p>Let <b>%s</b> read and send mail as <b>%s</b>?</p>"
                    "<form method=post action=/approve>%s<button name=allow value=1 style='font-size:1.4em'>Allow</button> "
                    "<button name=allow value=0 style='font-size:1.4em'>Deny</button></form></body></html>"
                    % (html.escape(q["client_id"]), EMAIL, hidden))
            return self.send(200, page, "text/html; charset=utf-8")
        if u.path == "/admin/revoke":
            refresh.clear()
            return self.send(200, '{"revoked":true}')
        if u.path == "/admin/expire":
            for t in access:
                access[t] = 0
            return self.send(200, '{"expired":%d}' % len(access))
        self.send(404, "not found", "text/plain")

    def form(self):
        n = int(self.headers.get("Content-Length") or 0)
        return dict(urllib.parse.parse_qsl(self.rfile.read(n).decode()))

    def do_POST(self):
        u = urllib.parse.urlparse(self.path)
        f = self.form()
        if u.path == "/approve":
            sep = "&" if "?" in f["redirect_uri"] else "?"
            if f.get("allow") != "1":
                loc = f["redirect_uri"] + sep + urllib.parse.urlencode({"error": "access_denied", "state": f["state"]})
            else:
                code = secrets.token_urlsafe(16)
                codes[code] = {"challenge": f.get("code_challenge", ""), "redirect_uri": f["redirect_uri"], "client_id": f["client_id"]}
                loc = f["redirect_uri"] + sep + urllib.parse.urlencode({"code": code, "state": f["state"]})
            log("authorize: redirect to", loc.split("?")[0])
            return self.send(302, "", "text/plain", [("Location", loc)])
        if u.path == "/token":
            g = f.get("grant_type")
            if g == "authorization_code":
                c = codes.pop(f.get("code", ""), None)
                if not c:
                    return self.send(400, '{"error":"invalid_grant","error_description":"unknown code"}')
                if c["redirect_uri"] != f.get("redirect_uri"):
                    return self.send(400, '{"error":"invalid_grant","error_description":"redirect_uri differs"}')
                verifier = f.get("code_verifier", "")
                if c["challenge"] and b64url(hashlib.sha256(verifier.encode()).digest()) != c["challenge"]:
                    return self.send(400, '{"error":"invalid_grant","error_description":"PKCE verifier wrong"}')
                log("token: code exchanged")
                return self.send(200, json.dumps(issue()))
            if g == "refresh_token":
                r = f.get("refresh_token", "")
                if r not in refresh:
                    log("token: refresh refused")
                    return self.send(400, '{"error":"invalid_grant","error_description":"refresh token revoked"}')
                log("token: refreshed")
                return self.send(200, json.dumps(issue(with_refresh=False)))
            return self.send(400, '{"error":"unsupported_grant_type"}')
        self.send(404, "not found", "text/plain")


def token_ok(b64payload):
    try:
        raw = base64.b64decode(b64payload).decode()
    except Exception:
        return None, "not base64"
    parts = dict(p.split("=", 1) for p in raw.split("\x01") if "=" in p)
    tok = parts.get("auth", "")[len("Bearer "):]
    if not parts.get("auth", "").startswith("Bearer "):
        return None, "no bearer"
    if access.get(tok, 0) < time.time():
        return None, "token unknown or expired"
    return parts.get("user"), None


async def pipe(reader, writer):
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
    except Exception:
        pass
    finally:
        writer.close()


async def imap_client(cr, cw):
    sr, sw = await asyncio.open_connection("127.0.0.1", 3143)
    peer = cw.get_extra_info("peername")
    greeting = await sr.readline()
    cw.write(greeting.replace(b"ready", b"ready (fake XOAUTH2 front)"))
    await cw.drain()
    while True:
        line = await cr.readline()
        if not line:
            sw.close(); return
        words = line.decode(errors="replace").rstrip("\r\n").split(" ")
        tag, cmd = words[0], (words[1].upper() if len(words) > 1 else "")
        if cmd == "CAPABILITY":
            cw.write(b"* CAPABILITY IMAP4rev1 LITERAL+ UIDPLUS SORT IDLE MOVE QUOTA AUTH=XOAUTH2 SASL-IR\r\n" + tag.encode() + b" OK CAPABILITY completed.\r\n")
        elif cmd == "LOGIN":
            log("imap", peer, "LOGIN refused (XOAUTH2 only)")
            cw.write(tag.encode() + b" NO [AUTHENTICATIONFAILED] LOGIN is disabled here; use XOAUTH2\r\n")
        elif cmd == "AUTHENTICATE" and len(words) >= 3 and words[2].upper() == "XOAUTH2":
            payload = words[3] if len(words) > 3 else None
            if payload is None:
                cw.write(b"+ \r\n"); await cw.drain()
                payload = (await cr.readline()).decode().strip()
            user, why = token_ok(payload)
            if not user:
                log("imap", peer, "XOAUTH2 refused:", why)
                cw.write(b"+ " + base64.b64encode(b'{"status":"401","schemes":"Bearer","scope":"mail"}') + b"\r\n")
                await cw.drain()
                await cr.readline()   # the client's empty answer
                cw.write(tag.encode() + b" NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)\r\n")
            else:
                sw.write(tag.encode() + b' LOGIN "' + EMAIL.encode() + b'" "dummy"\r\n')
                await sw.drain()
                ok = await sr.readline()
                log("imap", peer, "XOAUTH2 accepted for", user, "->", ok.decode().strip())
                cw.write(ok)
                await cw.drain()
                await asyncio.gather(pipe(cr, sw), pipe(sr, cw))
                return
        else:
            sw.write(line); await sw.drain()
            while True:   # this command's reply, up to its tagged line
                r = await sr.readline()
                if not r:
                    cw.close(); return
                cw.write(r)
                if r.startswith(tag.encode() + b" "):
                    break
        await cw.drain()


async def smtp_client(cr, cw):
    sr, sw = await asyncio.open_connection("127.0.0.1", 3025)
    peer = cw.get_extra_info("peername")
    cw.write(await sr.readline())
    await cw.drain()
    while True:
        line = await cr.readline()
        if not line:
            sw.close(); return
        up = line.upper()
        if up.startswith(b"EHLO"):
            cw.write(b"250-fake.provider Hello\r\n250-8BITMIME\r\n250-AUTH LOGIN XOAUTH2\r\n250 SMTPUTF8\r\n")
        elif up.startswith(b"AUTH XOAUTH2"):
            parts = line.split()
            user, why = token_ok(parts[2].decode()) if len(parts) > 2 else (None, "no payload")
            if not user:
                log("smtp", peer, "XOAUTH2 refused:", why)
                cw.write(b"334 " + base64.b64encode(b'{"status":"401","schemes":"bearer"}') + b"\r\n")
                await cw.drain()
                await cr.readline()
                cw.write(b"535 5.7.8 Username and Password not accepted\r\n")
            else:
                sw.write(b"EHLO fake\r\n"); await sw.drain()
                while not (await sr.readline()).startswith(b"250 "):
                    pass
                log("smtp", peer, "XOAUTH2 accepted for", user)
                cw.write(b"235 2.7.0 Accepted\r\n")
                await cw.drain()
                await asyncio.gather(pipe(cr, sw), pipe(sr, cw))
                return
        elif up.startswith(b"AUTH"):
            log("smtp", peer, "refused", line.split()[1] if len(line.split()) > 1 else line)
            cw.write(b"504 5.7.4 Unrecognized authentication type\r\n")
        else:
            cw.write(b"530 5.7.0 Authentication required\r\n")
        await cw.drain()


async def mail_servers():
    a = await asyncio.start_server(imap_client, args.host, 3145)
    b = await asyncio.start_server(smtp_client, args.host, 3027)
    log("imap on %s:3145, smtp on %s:3027" % (args.host, args.host))
    await asyncio.gather(a.serve_forever(), b.serve_forever())


def main():
    global args
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="192.168.10.45")
    p.add_argument("--expires", type=int, default=120)
    args = p.parse_args()
    http = ThreadingHTTPServer((args.host, 8091), OAuthHandler)
    threading.Thread(target=http.serve_forever, daemon=True).start()
    log("oauth on http://%s:8091 (tokens last %ds)" % (args.host, args.expires))
    asyncio.run(mail_servers())


if __name__ == "__main__":
    main()
