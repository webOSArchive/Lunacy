#!/bin/bash
# Lunacy's build-machine tests: everything that runs without a device. Run it before a commit
# that touches what they cover, and before every release build (Docs/test-plan.md, tier 1).
#
#   ./test.sh            all of them
#   ./test.sh quick      everything but the JVM tests (seconds, not minutes)
#
# Prints [PASS] / [FAIL] / [SKIP] <area> <what> -- detail, then a summary, and exits non-zero
# when anything failed. A release is not built on a FAIL.
cd "$(dirname "$0")" || exit 2
QUICK=$([ "$1" = quick ] && echo 1)
pass=0; fail=0; skip=0
P() { echo "[PASS] $*"; pass=$((pass+1)); }
F() { echo "[FAIL] $*"; fail=$((fail+1)); }
S() { echo "[SKIP] $*"; skip=$((skip+1)); }
WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT

echo "===== Lunacy build-machine tests — $(git log --oneline -1 2>/dev/null) — $(date) ====="

# ---- bundled data: every JSON file Lunacy ships parses (a stray comma stops a whole kind,
# template or app from loading on a device, with nothing on the screen to say why).
bad=$(python3 - <<'EOF'
import json, os, sys
roots = ["CoreApps", "AndroidLuna/src/main/assets/oauth", "AndroidLuna/src/main/assets/luna"]
bad = []
for root in roots:
    for d, dirs, files in os.walk(root):
        dirs[:] = [x for x in dirs if x not in ("node_modules", "build")]
        for f in files:
            if not f.endswith(".json"): continue
            p = os.path.join(d, f)
            try:
                with open(p, encoding="utf-8-sig") as h: json.load(h)
            except Exception as e:
                # db8 kinds and Palm's own files may carry json-c's trailing commas, which the
                # configurator strips (Docs/eas.md); anything else is a real error.
                with open(p, encoding="utf-8-sig") as h: t = h.read()
                import re
                try: json.loads(re.sub(r",(\s*[}\]])", r"\1", t))
                except Exception: bad.append("%s: %s" % (p, e))
print("\n".join(bad))
EOF
)
[ -z "$bad" ] && P "data  every bundled JSON file parses" || F "data  JSON that doesn't parse: $bad"

# ---- the account types: each template's id, folder and file name agree, and every
# Lunacy provider names a template that exists (Docs/oauth.md).
out=$(python3 - <<'EOF'
import json, os
base = "CoreApps/org.webosarchive.lunacy.signin/accounts"
ids = set(); errs = []
for d in sorted(os.listdir(base)):
    p = os.path.join(base, d, d + ".json")
    if not os.path.exists(p): errs.append("no %s" % p); continue
    t = json.load(open(p))
    if t.get("templateId") != d: errs.append("%s: templateId %s" % (p, t.get("templateId")))
    ids.add(d)
prov = json.load(open("AndroidLuna/src/main/assets/oauth/providers.json"))["providers"]
for k, v in prov.items():
    if v.get("templateId") and v["templateId"] not in ids: errs.append("provider %s names %s" % (k, v["templateId"]))
    fb = v.get("fallback")
    if fb and fb not in prov: errs.append("provider %s falls back to unknown %s" % (k, fb))
print("\n".join(errs))
EOF
)
[ -z "$out" ] && P "data  account templates and OAuth providers agree" || F "data  $out"

# ---- an account page's result that makes an account (it carries credentials) carries its
# template: First Launch, Contacts' and Calendar's first screen, makes no account from one
# without (entry-first-launch.js); only Add Account falls back to its own. The Android account's
# page left it out, so a fresh install's account was never made (the Kyocera, 2026-11-15).
out=$(python3 - <<'EOF'
import glob, re
errs = []
for f in sorted(glob.glob("CoreApps/*/source/**/*.js", recursive=True)):
    src = open(f, encoding="utf-8").read()
    for m in re.finditer(r"sendResult\(\{(.*?)\}\);", src, re.S):
        body = m.group(1)
        if re.search(r"returnValue:\s*true", body) and "credentials" in body and not re.search(r"\btemplate:", body):
            errs.append("%s:%d" % (f, src[:m.start()].count("\n") + 1))
print(" ".join(errs))
EOF
)
[ -z "$out" ] && P "data  account pages return their template with an account" || F "data  an account page's result has no template: $out"

# ---- the preload's host tests (C: mail auth, the Exchange adapters, back-off, PDK sends).
if command -v cc >/dev/null; then
  if out=$(sh LunaRuntimes/pdk/tests/run.sh 2>&1); then
    P "pdk   preload host tests ($(echo "$out" | grep -cE "ok$|all passed") suites)"
  else
    F "pdk   preload host tests -- $(echo "$out" | grep -iE "fail|error" | head -3 | tr '\n' ' ')"
  fi
else S "pdk   no C compiler"; fi

# ---- the bundled apps' own tests, run with Node (they load the real app files with Enyo and
# db8 stubbed). Palm's Jasmine specs need a browser and are listed, not run.
if command -v node >/dev/null; then
  for t in $(find CoreApps -path "*/tests/*" -name "*.js" -not -path "*/node_modules/*" | sort); do
    name="${t#CoreApps/}"
    if grep -q "^describe(" "$t"; then S "app   $name -- a Jasmine spec (needs a browser harness)"; continue; fi
    if out=$(timeout 120 node "$t" 2>&1); then P "app   $name"; else F "app   $name -- $(echo "$out" | grep -iE "fail|error|assert" | head -2 | tr '\n' ' ')"; fi
  done
else S "app   no node"; fi

# ---- Lunacy's curl (services shell out to /usr/bin/curl, which is curl.js in Node): the
# options apps send, each seen against a local server. A missing one fails the whole command
# ("option --noproxy: is unknown": WebDAV Client listed nothing, 2026-11-15).
if command -v node >/dev/null; then
  r=$(node - "AndroidLuna/src/main/assets/lunacy/services/curl.js" <<'NODE'
const http = require("http"), { execFile } = require("child_process");
const seen = [];
const srv = http.createServer((q, s) => { let b = ""; q.on("data", d => b += d); q.on("end", () => {
  const a = q.headers.authorization ? Buffer.from(q.headers.authorization.replace(/^Basic /, ""), "base64").toString() : "none";
  seen.push(q.method + " " + (q.headers.depth || "") + " " + a + " " + b.length);
  s.writeHead(207, { "Content-Type": "application/xml" }); s.end("<multistatus/>"); }); });
srv.listen(0, "127.0.0.1", () => {
  const url = "http://127.0.0.1:" + srv.address().port + "/remote.php/webdav/";
  // WebDAV Client 0.7.1's directory listing, as its service builds it, through a shell.
  // Its listing puts the credentials in the URL, encodeURIComponent'd; its uploads use -u.
  const creds = encodeURIComponent("us@r") + ":" + encodeURIComponent("@Pa!ss") + "@";
  const list = 'node "' + process.argv[2] + '" -k -s -S -f --noproxy "*" -X PROPFIND -H "Depth: 1" -H "Content-Type: application/xml" -d \'<propfind/>\' -w "%{http_code}" "' + url.replace("//", "//" + creds) + '"';
  const withU = 'node "' + process.argv[2] + '" -k -s -f -X PROPFIND -H "Depth: 1" -d \'<propfind/>\' -u "user:pass" -w "%{http_code}" "' + url + '"';
  execFile("/bin/sh", ["-c", list], (e, out, err) => execFile("/bin/sh", ["-c", withU], (e2, out2) => {
    console.log(JSON.stringify({ code: (e ? e.code : 0) + (e2 ? e2.code : 0), out: out + out2, err: err, seen: seen })); srv.close(); }));
});
NODE
)
  echo "$r" | python3 -c 'import json,sys; r=json.load(sys.stdin); ok=r["code"]==0 and r["out"]=="<multistatus/>207"*2 and r["seen"]==["PROPFIND 1 us@r:@Pa!ss 11","PROPFIND 1 user:pass 11"]; sys.exit(0 if ok else 1)' \
    && P "curl  curl.js takes WebDAV Client's PROPFIND (--noproxy, credentials in the URL and -u, -d, -w)" || F "curl  WebDAV Client's PROPFIND -- $r"
else S "curl  no node on this machine"; fi

# ---- the JVM tests (Robolectric: db8 on a real SQLite, the transforms, the page hooks, ...).
if [ -z "$QUICK" ]; then
  rm -rf AndroidLuna/build/test-results/testArm64DebugUnitTest
  ./gradlew :AndroidLuna:testArm64DebugUnitTest -q > "$WORK/gradle.log" 2>&1
  rc=$?
  read t f e <<<"$(python3 - <<'EOF'
import glob, re
t = f = e = 0
for p in glob.glob("AndroidLuna/build/test-results/testArm64DebugUnitTest/*.xml"):
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(p).read())
    if m: t += int(m[1]); f += int(m[2]); e += int(m[3])
print(t, f, e)
EOF
)"
  if [ "$rc" = 0 ] && [ "$f" = 0 ] && [ "$e" = 0 ] && [ "$t" -gt 0 ]; then P "jvm   $t unit tests"
  else F "jvm   $t tests, $f failed, $e errors (gradle $rc) -- $(grep -E "FAILED|Exception" "$WORK/gradle.log" | head -3 | tr '\n' ' ')"; fi
else S "jvm   skipped (quick)"; fi

echo "===== $pass passed, $fail failed, $skip skipped ====="
[ "$fail" = 0 ]
