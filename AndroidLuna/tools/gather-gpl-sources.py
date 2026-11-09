#!/usr/bin/env python3
"""Gathers the source of the GPL programs a Lunacy release ships, into
out/Lunacy-<version>-gpl-sources.tar, to be attached to the release beside the APKs.

GPL 2 asks whoever hands out a binary to hand out its complete corresponding source with it:
the source itself, and the scripts that controlled its building. Lunacy ships three GPL
programs it did not write (NOTICE at the top of the repository):

- QEMU's user-mode emulator, libqemu-arm.so in the 64-bit builds, and the LGPL libraries it
  loads (assets pdk/qemu-lib). These are Termux's packages, which tools/build-pdk.sh fetches.
  Termux publishes no source packages; its source is each upstream tarball, built by a recipe
  in termux-packages. This takes termux-packages at the commit that was current when
  build-pdk.sh fetched Termux's index, checks that every recipe there builds the version
  Lunacy ships, and bundles the recipes, Termux's build scripts and every upstream tarball.
- libpurple 2.14.13 and its helper plugins, as webOS CE built them: Pidgin's release.
- libaim.so, the AIM plugin: Pidgin 2.14.2's OSCAR code, built by the webOS Chat project's
  client/libaim at the commit pinned below (rebuilt there and compared with the shipped
  binary on 2026-11-09: identical but for the build folder in its __FILE__ strings).
- BusyBox, unmodified, with tools/build-busybox.sh, which holds its configuration.

Run it after the release and debug builds: it adds the tarball to out/SHA256SUMS.
Downloads are kept in AndroidLuna/local-build/sources (gitignored) and checked by SHA-256.
Needs: git, curl, dpkg-deb, and build-pdk.sh's Termux folder (local-build/pdk/termux).
"""
import fnmatch, hashlib, os, re, shutil, subprocess, sys, tarfile, time

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
REPO = os.path.dirname(ANDROID)
TERMUX = os.path.join(ANDROID, 'local-build/pdk/termux')
QEMU_LIB = os.path.join(ANDROID, 'local-assets/pdk/qemu-lib')
CACHE = os.path.join(ANDROID, 'local-build/sources')
TP_REPO = 'https://github.com/termux/termux-packages.git'

# libpurple, its plugins and libaim: Pidgin's releases (pidgin.im), and the webOS Chat build.
PIDGIN = {
    '2.14.13': '120049dc8e17e09a2a7d256aff2191ff8491abb840c8c7eb319a161e2df16ba8',
    '2.14.2': '19654ad276b149646371fbdac21bc7620742f2975f7399fed0ffc1a18fbaf603',
}
PIDGIN_URL = 'https://downloads.sourceforge.net/project/pidgin/Pidgin/{v}/pidgin-{v}.tar.bz2'
OSCAR_REPO = 'https://github.com/webOSArchive/Oscar-AIM-for-webOS.git'
OSCAR_LIBAIM_COMMIT = '5e414b1'   # the commit libaim.so was built from (see above)
BUSYBOX = ('1.37.0', '3311dff32e746499f4df0d5df04d7eb396382d7e108bb9250e7b519b837043a4')


def die(msg):
    sys.exit('gather-gpl-sources: ' + msg)


def run(*cmd, cwd=None):
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if r.returncode:
        die(' '.join(cmd) + ' failed:\n' + r.stderr)
    return r.stdout


def git(repo, *a):
    return run('git', '-C', repo, *a)


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(1 << 20), b''):
            h.update(b)
    return h.hexdigest()


def fetch(url, want, name=None):
    """A download, kept in the cache and checked against its SHA-256."""
    os.makedirs(os.path.join(CACHE, 'dl'), exist_ok=True)
    dst = os.path.join(CACHE, 'dl', name or os.path.basename(url))
    if not os.path.exists(dst) or sha256(dst) != want:
        print('fetching', url)
        run('curl', '-sfL', '--retry', '3', url, '-o', dst + '.part')
        got = sha256(dst + '.part')
        if got != want:
            os.remove(dst + '.part')
            die('%s: SHA-256 %s, expected %s' % (url, got, want))
        os.rename(dst + '.part', dst)
    return dst


def version_name():
    for line in open(os.path.join(REPO, 'gradle.properties')):
        if line.startswith('versionName='):
            return line.split('=', 1)[1].strip()
    die('no versionName in gradle.properties')


# ---- Termux: which packages Lunacy ships, and the recipes that built them ----

def packages_index():
    pk, cur = {}, None
    for line in open(os.path.join(TERMUX, 'Packages')):
        line = line.rstrip('\n')
        if line.startswith('Package: '):
            cur = line[9:].strip(); pk[cur] = {}
        elif cur and ': ' in line and not line.startswith(' '):
            k, v = line.split(': ', 1); pk[cur][k] = v
    return pk


def shipped_packages(pk):
    """The Termux packages whose files are in the APK: qemu-arm and qemu-lib's libraries."""
    owner = {}
    for p, f in pk.items():
        deb = os.path.join(TERMUX, 'deb', os.path.basename(f.get('Filename', '')))
        if not os.path.isfile(deb):
            continue
        for line in run('dpkg-deb', '-c', deb).splitlines():
            path = line.split(None, 5)[5].split(' -> ')[0]
            owner.setdefault(os.path.basename(path), set()).add(p)
    names = {'qemu-arm'} | {n for n in os.listdir(QEMU_LIB) if '.so' in n}
    ship = set()
    for n in sorted(names):
        if n not in owner:
            die('no Termux package holds %s (is build-pdk.sh\'s termux folder current?)' % n)
        ship |= owner[n]
    return sorted(ship)


def expand(s, version):
    """The few bash expansions Termux's recipes use in a source URL, done as text: recipes
    are never run here. Unknown forms stop the script rather than guess."""
    def one(m):
        expr = m.group(1) or m.group(2)
        v = version
        mm = re.fullmatch(r'TERMUX_PKG_VERSION(\[(\d)\])?(.*)', expr)
        if not mm:
            die('cannot expand ${%s} in %s' % (expr, s))
        if mm.group(2):
            v = version[int(mm.group(2))]
        elif isinstance(v, list):
            v = v[0]
        op = mm.group(3)
        if op == '':
            return v
        if re.fullmatch(r':\d+', op):
            return v[int(op[1:]):]
        if op.startswith('%') and not op.startswith('%%'):    # shortest matching suffix
            return next((v[:i] for i in range(len(v), -1, -1) if fnmatch.fnmatchcase(v[i:], op[1:])), v)
        if op.startswith('#') and not op.startswith('##'):    # shortest matching prefix
            return next((v[i:] for i in range(len(v) + 1) if fnmatch.fnmatchcase(v[:i], op[1:])), v)
        die('cannot expand ${%s} in %s' % (expr, s))
    return re.sub(r'\$\{([^}]+)\}|\$(TERMUX_PKG_VERSION)\b', one, s)


def recipe_values(sh, key):
    m = re.search(r'^' + key + r'=(\((.*?)\)|"([^"]*)"|(\S+))', sh, re.M | re.S)
    if not m:
        return []
    s = next(g for g in m.groups()[1:] if g is not None)
    return [a or b for a, b in re.findall(r'"([^"]+)"|(\S+)', s)]


def termux_sources(stage):
    pk = packages_index()
    ship = shipped_packages(pk)
    tp = os.path.join(CACHE, 'termux-packages')
    if not os.path.isdir(os.path.join(tp, '.git')):
        print('cloning termux-packages (history only, no files)')
        run('git', 'clone', '-q', '--filter=blob:none', '--no-checkout', TP_REPO, tp)
    # The index's own date is when build-pdk.sh fetched it; the recipes are the ones current then.
    cutoff = time.strftime('%Y-%m-%dT%H:%M:%S%z', time.localtime(os.path.getmtime(os.path.join(TERMUX, 'Packages'))))
    commit = git(tp, 'rev-list', '-1', '--before=' + cutoff, 'origin/HEAD').strip() or \
        die('no termux-packages commit before ' + cutoff + '; fetch the clone')
    files = git(tp, 'ls-tree', '-r', '--name-only', commit).split('\n')
    dirs, rows = set(), []
    for p in ship:
        ver = pk[p]['Version']
        d = next((os.path.dirname(f) for f in files if re.fullmatch(r'[^/]+/' + re.escape(p) + '/build.sh', f)), None) or \
            next((os.path.dirname(f) for f in files if f.endswith('/' + p + '.subpackage.sh')), None)
        if not d:
            die('no recipe for %s in termux-packages %s' % (p, commit[:12]))
        sh = git(tp, 'show', commit + ':' + d + '/build.sh')
        v = recipe_values(sh, 'TERMUX_PKG_VERSION')
        r = (recipe_values(sh, 'TERMUX_PKG_REVISION') or ['0'])[0]
        built = v[0] + ('-' + r if r != '0' else '')
        if built != ver:
            die('%s: Lunacy ships %s, termux-packages %s builds %s' % (p, ver, commit[:12], built))
        rows.append((p, ver, d))
        if d in dirs:
            continue
        dirs.add(d)
        urls = recipe_values(sh, 'TERMUX_PKG_SRCURL')
        sums = recipe_values(sh, 'TERMUX_PKG_SHA256')
        if not urls or len(urls) != len(sums):
            die('%s: source URLs and checksums don\'t pair up' % d)
        for u, s in zip(urls, sums):
            url = expand(u, v if len(v) > 1 else v[0])
            name = os.path.basename(d) + '--' + os.path.basename(url)
            shutil.copy(fetch(url, s, name), os.path.join(stage, 'qemu', 'upstream', name))
    # The recipes (build.sh and Termux's patches) and the build system that runs them.
    arch = os.path.join(stage, 'qemu', 'termux-packages-%s.tar' % commit[:12])
    with open(arch, 'wb') as out:
        subprocess.run(['git', '-C', tp, 'archive', '--prefix=termux-packages/', commit,
                        'build-package.sh', 'scripts', 'LICENSE.md', *sorted(dirs)], stdout=out, check=True)
    return commit, cutoff, rows


def git_archive(repo, url, commit, path, out):
    if not os.path.isdir(os.path.join(repo, '.git')):
        run('git', 'clone', '-q', url, repo)
    if subprocess.run(['git', '-C', repo, 'cat-file', '-e', commit + '^{commit}']).returncode:
        git(repo, 'fetch', '-q', 'origin')
    with open(out, 'wb') as f:
        subprocess.run(['git', '-C', repo, 'archive', '--prefix=oscar/', commit, path], stdout=f, check=True)


def main():
    ver = sys.argv[1] if len(sys.argv) > 1 else version_name()
    if not os.path.isfile(os.path.join(TERMUX, 'Packages')) or not os.path.isdir(QEMU_LIB):
        die('run tools/build-pdk.sh and fetch-assets.sh first')
    name = 'Lunacy-%s-gpl-sources' % ver
    stage = os.path.join(CACHE, name)
    shutil.rmtree(stage, ignore_errors=True)
    for d in ('qemu/upstream', 'libpurple', 'libaim', 'busybox'):
        os.makedirs(os.path.join(stage, d))

    commit, cutoff, rows = termux_sources(stage)

    for v in ('2.14.13', '2.14.2'):
        src = fetch(PIDGIN_URL.format(v=v), PIDGIN[v])
        shutil.copy(src, os.path.join(stage, 'libpurple' if v == '2.14.13' else 'libaim'))
    git_archive(os.path.join(CACHE, 'oscar'), OSCAR_REPO, OSCAR_LIBAIM_COMMIT, 'client/libaim',
                os.path.join(stage, 'libaim', 'oscar-libaim-%s.tar' % OSCAR_LIBAIM_COMMIT))

    bb = fetch('https://busybox.net/downloads/busybox-%s.tar.bz2' % BUSYBOX[0], BUSYBOX[1])
    shutil.copy(bb, os.path.join(stage, 'busybox'))
    shutil.copy(os.path.join(HERE, 'build-busybox.sh'), os.path.join(stage, 'busybox'))

    with open(os.path.join(stage, 'README.txt'), 'w') as f:
        f.write(README.format(ver=ver, commit=commit, cutoff=cutoff, oscar=OSCAR_LIBAIM_COMMIT,
                              bb=BUSYBOX[0], rows='\n'.join('  %-20s %-22s %s' % r for r in rows)))

    out = os.path.join(REPO, 'out')
    os.makedirs(out, exist_ok=True)
    tar = os.path.join(out, name + '.tar')
    with tarfile.open(tar, 'w', format=tarfile.PAX_FORMAT) as t:
        t.add(stage, arcname=name, filter=lambda i: (setattr(i, 'uid', 0), setattr(i, 'gid', 0),
              setattr(i, 'uname', ''), setattr(i, 'gname', ''), i)[-1])
    sums = sorted(n for n in os.listdir(out) if n.endswith('.apk') or n.endswith('-gpl-sources.tar'))
    with open(os.path.join(out, 'SHA256SUMS'), 'w') as f:
        for n in sums:
            f.write('%s  %s\n' % (sha256(os.path.join(out, n)), n))
    print('%s (%d MB), and out/SHA256SUMS' % (os.path.relpath(tar, REPO), os.path.getsize(tar) >> 20))


README = """Lunacy {ver}: the source of the GPL programs it ships

Lunacy's own source is at https://github.com/webOSArchive/Lunacy, at the commit each APK
names. This archive holds the source of the GPL (and LGPL) programs and libraries Lunacy's APKs
carry that are not Lunacy's, as GPL 2 section 3(a) asks: each one's source, and the scripts
that built it. See NOTICE in the repository for every component and its licence.

qemu/: QEMU's user-mode emulator for 32-bit ARM (libqemu-arm.so, 64-bit APKs only; GPL 2)
and the libraries it loads (the assets' pdk/qemu-lib; LGPL, BSD, MIT). These are Termux's
builds for Android, from https://packages.termux.dev, fetched by AndroidLuna/tools/build-pdk.sh,
unchanged by Lunacy. Termux builds each from its upstream release with a recipe (build.sh and
patches) in termux-packages. termux-packages-*.tar holds that repository at {commit},
the commit current when Lunacy fetched Termux's index ({cutoff}): Termux's build system
(build-package.sh, scripts/) and the recipe of every package below. upstream/ holds each
recipe's source tarball, named <recipe>--<file>, as the recipe names it and checked against
the recipe's SHA-256. Each package's version was checked against its recipe:

  package              version                recipe
{rows}

libpurple/: libpurple 2.14.13 (libpurple.so.0) and its helper plugins idle.so, newline.so,
offlinemsg.so, autoaccept.so and statenotify.so (GPL 2 or later), as webOS Community Edition
builds them for the TouchPad and Lunacy copies them from one: Pidgin 2.14.13's release, built
with a crosstool-NG GCC 12.5 toolchain for 32-bit ARM glibc, its compiled-in plugin, config
and data folders then set to /usr/lib/purple-2, /etc and /usr/share.

libaim/: libaim.so, libpurple's AIM protocol plugin (GPL 2 or later). Pidgin 2.14.2's OSCAR
code (Pidgin dropped AIM in 2.14.3) with webOS Chat's defaults, built for libpurple 2.14.13
by the webOS Chat project: oscar-libaim-{oscar}.tar is its client/libaim at commit {oscar}
(https://github.com/webOSArchive/Oscar-AIM-for-webOS); build-touchpad.sh there is the whole
build, from pidgin-2.14.2.tar.bz2 here and libpurple/'s pidgin-2.14.13.tar.bz2 for the
headers.

busybox/: BusyBox {bb} (libbusybox.so; GPL 2), unmodified, and build-busybox.sh, Lunacy's
build script, which holds the configuration.
"""

if __name__ == '__main__':
    main()
