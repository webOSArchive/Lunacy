#!/usr/bin/env python3
"""What an APK holds, against what its build was given: every file Gradle should have packaged
is in it, at the size it has here, and nothing that would stop Lunacy reading it.

    Workbench/apk-inventory.py <apk> [<apk> ...]

Run by build.sh after every build, from the tree the APKs were built from. For each APK:

- every file of AndroidLuna/src/main/assets and AndroidLuna/local-assets is under assets/, and
  every file git tracks in CoreApps/<id>/ is under assets/apps/<id>/ (bundleCoreApps), each at
  its size here (dot files apart, which aapt leaves out by design). aapt silently leaves out
  other names too (folders starting with "_",
  "*~", CVS, thumbs.db ...), so a file of an app, the frameworks or the webOS root that one of
  those rules drops is caught here, not on a device;
- lib/ holds every library of local-jni/ for the APK's ABI, and none of another ABI;
- the webOS root's configuration (etc/palm: activities, kinds, permissions; services' and apps'
  configuration/, account templates) parses, with Palm's trailing commas allowed, as the
  configurator reads it.

Prints one line per problem, then a summary; exits 1 on any problem. (Added 2026-11-15, after
1.0: codepoet asked for a post-build inventory. 1.0's own bug was a file that was packaged but
never configured on a fresh install; Workbench/fresh-install.sh is that one's check.)
"""
import json, os, re, subprocess, sys, zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
ABIS = {"arm32": "armeabi-v7a", "arm64": "arm64-v8a"}


def files_under(base):
    out = {}
    for d, dirs, names in os.walk(base):
        for n in names:
            p = os.path.join(d, n)
            if os.path.islink(p) or not os.path.isfile(p):
                continue
            out[os.path.relpath(p, base)] = os.path.getsize(p)
    return out


def tracked(path):
    r = subprocess.run(["git", "-C", ROOT, "ls-files", "-z", "--", path], capture_output=True)
    return [f for f in r.stdout.decode().split("\0") if f]


def json_c(text):
    return re.sub(r",(\s*[}\]])", r"\1", text)


CONFIG = re.compile(r"^assets/(rootfs/etc/palm/|rootfs/usr/palm/(services|public/accounts)/[^/]+/(configuration/|[^/]+\.json$)|apps/[^/]+/(configuration/|account/))")


def check(apk):
    problems = []
    name = os.path.basename(apk)
    abi = next((v for k, v in ABIS.items() if k in name), None)
    with zipfile.ZipFile(apk) as z:
        have = {i.filename: i.file_size for i in z.infolist() if not i.is_dir()}
        expected = {}
        for base in ("AndroidLuna/src/main/assets", "AndroidLuna/local-assets"):
            for rel, size in files_under(os.path.join(ROOT, base)).items():
                expected["assets/" + rel] = size
        for f in tracked("CoreApps"):
            parts = f.split("/")
            if len(parts) < 3:  # CoreApps/README.md and the like: for people, not an app
                continue
            p = os.path.join(ROOT, f)
            if os.path.isfile(p) and not os.path.islink(p):
                expected["assets/apps/" + "/".join(parts[1:])] = os.path.getsize(p)
        if abi:
            for rel, size in files_under(os.path.join(ROOT, "AndroidLuna/local-jni", abi)).items():
                expected["lib/%s/%s" % (abi, rel)] = size
            for f in have:
                if f.startswith("lib/") and not f.startswith("lib/%s/" % abi):
                    problems.append("%s: another ABI's library: %s" % (name, f))
        else:
            problems.append("%s: no ABI in the name; its libraries aren't checked" % name)
        # Dot files (.rvmrc, .gitignore: a developer's tooling in an app's folder) are what aapt
        # leaves out by design, and nothing webOS reads; counted, not failed.
        dots = [f for f in expected if os.path.basename(f).startswith(".")]
        for f in dots:
            del expected[f]
        for f, size in sorted(expected.items()):
            if f not in have:
                problems.append("%s: missing %s" % (name, f))
            elif have[f] != size:
                problems.append("%s: %s is %d bytes, %d here" % (name, f, have[f], size))
        for f in sorted(have):
            if CONFIG.match(f) and (f.endswith(".json") or "/activities/" in f or "/kinds/" in f or "/permissions/" in f):
                try:
                    json.loads(json_c(z.read(f).decode("utf-8")))
                except Exception as e:
                    problems.append("%s: %s doesn't parse: %s" % (name, f, e))
    return problems, len(expected), len(dots)


def main():
    if len(sys.argv) < 2:
        print(__doc__.strip().split("\n\n")[1])
        sys.exit(2)
    bad = 0
    for apk in sys.argv[1:]:
        problems, n, dots = check(apk)
        for p in problems:
            print("[FAIL] inventory  " + p)
        print("[%s] inventory  %s: %d files checked (%d dot files left out by aapt), %d problems" % ("PASS" if not problems else "FAIL", os.path.basename(apk), n, dots, len(problems)))
        bad += len(problems)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
