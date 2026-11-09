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
