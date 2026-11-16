#!/usr/bin/env python3
"""What an APK holds, against what its build was given: every file Gradle should have packaged
is in it, with the content it has here, and nothing that would stop Lunacy reading it.

    Workbench/apk-inventory.py <apk> [<apk> ...]

Run by build.sh after every build, from the tree the APKs were built from. For each APK:

- every file of AndroidLuna/src/main/assets and AndroidLuna/local-assets is under assets/, and
  every file git tracks in CoreApps/<id>/ is under assets/apps/<id>/ (bundleCoreApps), each at
  its content here (dot files apart, which aapt leaves out by design). aapt silently leaves out
  other names too (folders starting with "_",
  "*~", CVS, thumbs.db ...), so a file of an app, the frameworks or the webOS root that one of
  those rules drops is caught here, not on a device;
- the APK's own list of framework patches (assets/patches.applied, written by fetch-assets.sh
  as it applies each) names every patch under LunaRuntimes/*/patches at the hash it has now.
  An APK is held against local-assets/, so stale assets passed every other check: Mojo patch
  0005 was committed after a fetch, reported fixed, and in no build (2026-11-15/16);
- lib/ holds every library of local-jni/ for the APK's ABI, and none of another ABI;
- the webOS root's configuration (etc/palm: activities, kinds, permissions; services' and apps'
  configuration/, account templates) parses, with Palm's trailing commas allowed, as the
  configurator reads it.

Prints one line per problem, then a summary; exits 1 on any problem. (Added 2026-11-15, after
1.0: codepoet asked for a post-build inventory. 1.0's own bug was a file that was packaged but
never configured on a fresh install; Workbench/fresh-install.sh is that one's check.)
"""
import glob, hashlib, json, os, re, subprocess, sys, zipfile, zlib

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


_crc = {}


def crc_of(path):
    """A file's CRC-32, as a zip records it; kept, since every APK is held against the same files."""
    if path not in _crc:
        c = 0
        with open(path, "rb") as f:
            for block in iter(lambda: f.read(1 << 20), b""):
                c = zlib.crc32(block, c)
        _crc[path] = c & 0xFFFFFFFF
    return _crc[path]


def patches(z, have, name):
    """The APK's own list of the framework patches its assets were made with (fetch-assets.sh
    writes assets/patches.applied as it applies each one) against LunaRuntimes/ here: every
    patch in the tree is listed with the hash it has now, and nothing else is."""
    problems = []
    if "assets/patches.applied" not in have:
        return ["%s: no assets/patches.applied: its assets come from a fetch-assets.sh run that recorded no patches" % name], 0
    listed = {}
    for line in z.read("assets/patches.applied").decode().splitlines():
        parts = line.split()
        if len(parts) >= 2:
            listed.setdefault(parts[1], set()).add(parts[0])
    here = sorted(os.path.relpath(f, ROOT) for f in glob.glob(os.path.join(ROOT, "LunaRuntimes", "*", "patches", "*.patch")))
    for f in here:
        with open(os.path.join(ROOT, f), "rb") as fh:
            digest = hashlib.sha256(fh.read()).hexdigest()
        if f not in listed:
            problems.append("%s: patch not applied to its assets: %s" % (name, f))
        elif listed[f] != {digest}:
            problems.append("%s: patch applied in another version than the tree's: %s" % (name, f))
    for f in sorted(set(listed) - set(here)):
        problems.append("%s: its assets carry a patch the tree doesn't have: %s" % (name, f))
    return problems, len(here)


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
        crcs = {i.filename: i.CRC for i in z.infolist() if not i.is_dir()}
        expected = {}
        source = {}
        for base in ("AndroidLuna/src/main/assets", "AndroidLuna/local-assets"):
            for rel, size in files_under(os.path.join(ROOT, base)).items():
                expected["assets/" + rel] = size
                source["assets/" + rel] = os.path.join(ROOT, base, rel)
        for f in tracked("CoreApps"):
            parts = f.split("/")
            if len(parts) < 3:  # CoreApps/README.md and the like: for people, not an app
                continue
            p = os.path.join(ROOT, f)
            if os.path.isfile(p) and not os.path.islink(p):
                expected["assets/apps/" + "/".join(parts[1:])] = os.path.getsize(p)
                source["assets/apps/" + "/".join(parts[1:])] = p
        if abi:
            for rel, size in files_under(os.path.join(ROOT, "AndroidLuna/local-jni", abi)).items():
                expected["lib/%s/%s" % (abi, rel)] = size
                source["lib/%s/%s" % (abi, rel)] = os.path.join(ROOT, "AndroidLuna/local-jni", abi, rel)
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
            elif crcs[f] != crc_of(source[f]):
                problems.append("%s: %s has other content than the file here (same size)" % (name, f))
        patch_problems, npatches = patches(z, have, name)
        problems += patch_problems
        for f in sorted(have):
            if CONFIG.match(f) and (f.endswith(".json") or "/activities/" in f or "/kinds/" in f or "/permissions/" in f):
                try:
                    json.loads(json_c(z.read(f).decode("utf-8")))
                except Exception as e:
                    problems.append("%s: %s doesn't parse: %s" % (name, f, e))
    return problems, len(expected), len(dots), npatches


def main():
    if len(sys.argv) < 2:
        print(__doc__.strip().split("\n\n")[1])
        sys.exit(2)
    bad = 0
    for apk in sys.argv[1:]:
        problems, n, dots, npatches = check(apk)
        for p in problems:
            print("[FAIL] inventory  " + p)
        print("[%s] inventory  %s: %d files checked by content (%d dot files left out by aapt), %d patches applied, %d problems" % ("PASS" if not problems else "FAIL", os.path.basename(apk), n, dots, npatches, len(problems)))
        bad += len(problems)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
