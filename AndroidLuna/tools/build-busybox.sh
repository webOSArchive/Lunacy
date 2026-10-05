#!/bin/sh
# Builds Lunacy's busybox for both ABIs, against Android's own libc (bionic), into
# local-build/busybox/out/<abi>/libbusybox.so (gitignored). fetch-assets.sh runs this when
# there is no build yet and copies the result into local-jni, which it empties on every run.
#
# Why not the prebuilt ones, and why bionic: Android's seccomp policy for apps allows the
# system calls bionic makes and kills a process with SIGSYS (exit 159) on any other. The
# prebuilt busyboxes (busybox.net's armv7, Alpine's arm64) are built with FEATURE_SUID, which
# calls setuid() and setgid() as every applet starts, to drop a setuid root it never has;
# setuid is on the blocklist, so a package's postinst died before its first line (found on
# the Pixel Tablet, 2026-10-02, with qemu-user's -strace against the published blocklist).
# A static glibc build without FEATURE_SUID died the same way at startup on Android 17
# (2026-10-03): glibc's own startup makes a call the policy has no entry for. Linking against
# bionic, as a position-independent executable, means every call is one Android makes
# itself. The cost is the applets bionic has no headers or calls for, listed in
# configure(): none of them is anything a webOS package script uses (mount is Lunacy's own
# stand-in, WebosRoot.installTools), and ash is the shell, not hush.
#
# Needs: the NDK's clang (r28c), curl. The 32-bit build targets API 21 (Lunacy's minSdk),
# the 64-bit API 28 (the arm64 flavour's target).
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
NDK=${NDK:-$HOME/Android/Sdk/ndk/android-ndk-r28c}
BIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin
WORK=$HERE/local-build/busybox
BB_VER=1.37.0
mkdir -p "$WORK/stub"
cd "$WORK"

[ -f busybox-$BB_VER.tar.bz2 ] || curl -sL https://busybox.net/downloads/busybox-$BB_VER.tar.bz2 -o busybox-$BB_VER.tar.bz2
# busybox links -lresolv for res_init; bionic keeps that in libc, so an empty archive stands in.
[ -f stub/libresolv.a ] || "$BIN/llvm-ar" rcs stub/libresolv.a

# Off: FEATURE_SUID (above); STATIC (bionic's libc.a duplicates busybox's fallbacks, and
# libc.so is in every process anyway); and the applets bionic can't build: tc (kernel headers
# gone), the x86-only hash acceleration, loadfont/setfont (sys/kd.h), hostid, utmp/wtmp,
# ether-wake, adjtimex, su, conspy, ifconfig/arp/ifenslave (interface.c redefines in6_ifreq),
# hush, the minix tools, ipcs/ipcrm (semun), mount/umount (addmntent), swapon/swapoff.
OFF="FEATURE_SUID FEATURE_SUID_CONFIG STATIC TC SHA1_HWACCEL SHA256_HWACCEL LOADFONT SETFONT HOSTID
FEATURE_WTMP FEATURE_UTMP ETHER_WAKE ADJTIMEX SU FEATURE_SU_SYSLOG FEATURE_SU_CHECKS_SHELL
FEATURE_SU_BLANK_PW_NEEDS_SECURE_TTY CONSPY IFCONFIG ARP IFENSLAVE HUSH SHELL_HUSH FSCK_MINIX MKFS_MINIX
IPCS IPCRM MOUNT UMOUNT SWAPON SWAPOFF"
configure() {  # $1 = build dir, $2 = more CONFIG_ names to turn off
  rm -rf "$1"; mkdir -p "$1"; tar xjf busybox-$BB_VER.tar.bz2 -C "$1" --strip-components=1
  ( cd "$1" && make defconfig > /dev/null 2>&1 &&
    for c in $OFF $2; do sed -i "s/^CONFIG_$c=y/# CONFIG_$c is not set/" .config; done &&
    sed -i 's/^\(CONFIG_FEATURE_IFCONFIG_[A-Z_]*\)=y/# \1 is not set/; s/^\(CONFIG_HUSH_[A-Z_]*\)=y/# \1 is not set/; s/^\(CONFIG_FEATURE_MOUNT_[A-Z_]*\)=y/# \1 is not set/; s/^\(CONFIG_FEATURE_UMOUNT_[A-Z_]*\)=y/# \1 is not set/; s/^\(CONFIG_FEATURE_SWAPON[A-Z_]*\)=y/# \1 is not set/; s/^# CONFIG_PIE is not set/CONFIG_PIE=y/' .config )
}

# busybox declares ptr_to_globals `*const` and assigns it through a cast behind a compiler
# barrier, to keep it in a register; the NDK's clang (19) still hoists the read above the
# store, and awk wrote through NULL (SIGSEGV at fault address 4 on the Pixel Tablet,
# 2026-10-03). BB_GLOBAL_CONST empty makes it a plain global that every applet reloads.
CFLAGS_COMMON="-DBB_GLOBAL_CONST="
build() {  # $1 = build dir, $2 = clang target, $3 = output, $4 = more CFLAGS
  ( cd "$1" && make -j8 CC="$BIN/clang --target=$2 -Wno-error -L$WORK/stub $CFLAGS_COMMON $4" HOSTCC=cc CROSS_COMPILE= busybox_unstripped > make.log 2>&1 ) || { echo "busybox failed in $1; see $1/make.log"; exit 1; }
  "$BIN/llvm-strip" -o "$3" "$1/busybox_unstripped"
  clear_flags_1 "$3"
  echo "built $3"
}

# lld marks a PIE with DF_1_PIE and DF_1_NOW in DT_FLAGS_1. Android 5 and 6's linker knows
# neither and prints "WARNING: linker: <applet>: unsupported flags DT_FLAGS_1=0x8000001" on
# every exec - into every package script's output. Both flags are advice (the kernel and
# linker see the PIE from its ELF type; NOW only makes binding eager), so they are cleared.
clear_flags_1() {
  python3 - "$1" <<'PY'
import struct, sys
p = sys.argv[1]; d = bytearray(open(p, 'rb').read())
cls = d[4]  # 1 = ELF32, 2 = ELF64
if cls == 1:
    shoff, shentsize, shnum = struct.unpack_from('<I', d, 0x20)[0], struct.unpack_from('<H', d, 0x2e)[0], struct.unpack_from('<H', d, 0x30)[0]
else:
    shoff, shentsize, shnum = struct.unpack_from('<Q', d, 0x28)[0], struct.unpack_from('<H', d, 0x3a)[0], struct.unpack_from('<H', d, 0x3c)[0]
for i in range(shnum):
    sh = shoff + i * shentsize
    typ = struct.unpack_from('<I', d, sh + 4)[0]
    if typ != 6: continue  # SHT_DYNAMIC
    if cls == 1:
        off, size, ent = struct.unpack_from('<I', d, sh + 0x10)[0], struct.unpack_from('<I', d, sh + 0x14)[0], 8
    else:
        off, size, ent = struct.unpack_from('<Q', d, sh + 0x18)[0], struct.unpack_from('<Q', d, sh + 0x20)[0], 16
    for j in range(size // ent):
        e = off + j * ent
        tag = struct.unpack_from('<i' if cls == 1 else '<q', d, e)[0]
        if tag == 0x6ffffffb:  # DT_FLAGS_1
            struct.pack_into('<I' if cls == 1 else '<Q', d, e + (4 if cls == 1 else 8), 0)
open(p, 'wb').write(d)
PY
}

configure build64
mkdir -p "$WORK/out/arm64-v8a"
build build64 aarch64-linux-android28 "$WORK/out/arm64-v8a/libbusybox.so"

# API 21's bionic has no fseeko (fseek does; nothing here seeks past 2 GB), sigtimedwait
# (init and mdev), getlogin_r (logname only), syncfs (sync -f only), sethostname, getrandom
# (seedrng), the ns_* resolver calls (nslookup), adjtimex (ntpd), DHCPv6's headers or
# System V IPC (logread, syslogd's shared-memory log).
configure build32 "INIT LINUXRC MDEV FEATURE_MDEV_CONF FEATURE_MDEV_RENAME FEATURE_MDEV_RENAME_REGEXP FEATURE_MDEV_EXEC FEATURE_MDEV_LOAD_FIRMWARE FEATURE_MDEV_DAEMON LOGREAD FEATURE_LOGREAD_REDUCED_LOCKING FEATURE_IPC_SYSLOG HOSTNAME DNSDOMAINNAME SEEDRNG NSLOOKUP FEATURE_NSLOOKUP_BIG FEATURE_NSLOOKUP_LONG_OPTIONS NTPD FEATURE_NTPD_SERVER FEATURE_NTPD_CONF FEATURE_NTP_AUTH UDHCPC6 FEATURE_UDHCPC6_RFC3646 FEATURE_UDHCPC6_RFC4704 FEATURE_UDHCPC6_RFC4833 FEATURE_UDHCPC6_RFC5970 FEATURE_SYNC_FANCY FEATURE_USE_INITTAB FEATURE_KILL_REMOVED FEATURE_INIT_SCTTY FEATURE_INIT_SYSLOG FEATURE_INIT_QUIET FEATURE_INIT_COREDUMPS LOGNAME"
mkdir -p "$WORK/out/armeabi-v7a"
build build32 armv7a-linux-androideabi21 "$WORK/out/armeabi-v7a/libbusybox.so" "-Dfseeko=fseek -Dftello=ftell"

# What fetch-assets.sh looks for, last, so that a failed build is built again.
echo "busybox $BB_VER, against bionic, PIE, FEATURE_SUID off; built $(date -u +%Y-%m-%d)" > "$WORK/out/BUILT"
