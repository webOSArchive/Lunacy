#!/usr/bin/env bash
# Builds Lunacy's APKs into out/, the same way every time.
#
#   ./build.sh debug     the shell (arm32 + arm64) and the keyboard, debug-signed
#   ./build.sh release   the same, signed with codepoet's key, from a clean committed tree
#
# Options:
#   --fetch        run AndroidLuna/fetch-assets.sh first (release does by default)
#   --no-fetch     use AndroidLuna/local-assets/ as it is (release only)
#   --test-apps    include the test apps (debug only; a release never carries them)
#   --lint         run lint too (release always does: NewApi is fatal, minSdk 21 must hold)
#   --allow-dirty  build a release from a tree with uncommitted changes
#
# What makes it repeatable: one JDK (17) and the Gradle wrapper's pinned Gradle, no daemon
# and no build cache, a clean build for a release, and every expected APK checked for and
# hashed into out/SHA256SUMS. See BUILDING.md.
set -euo pipefail
cd "$(dirname "$0")"

die() { echo "build.sh: $*" >&2; exit 1; }
usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

type=""; fetch=""; test_apps=0; lint=0; allow_dirty=0
for a in "$@"; do
    case "$a" in
        debug|release) [ -z "$type" ] || die "one build type at a time"; type=$a ;;
        --fetch) fetch=1 ;;
        --no-fetch) fetch=0 ;;
        --test-apps) test_apps=1 ;;
        --lint) lint=1 ;;
        --allow-dirty) allow_dirty=1 ;;
        -h|--help) usage ;;
        *) echo "build.sh: unknown argument: $a" >&2; usage 1 ;;
    esac
done
[ -n "$type" ] || usage 1
Type=$(tr '[:lower:]' '[:upper:]' <<<"${type:0:1}")${type:1}

if [ "$type" = release ]; then
    [ "$test_apps" = 0 ] || die "a release never carries the test apps"
    lint=1
    fetch=${fetch:-1}
    [ -f keystore.properties ] || die "a release needs keystore.properties (its format is in build.gradle.kts); without it the APKs would be unsigned"
    if [ "$allow_dirty" = 0 ] && [ -n "$(git status --porcelain 2>/dev/null)" ]; then
        die "the tree has uncommitted changes, and a release carries the last commit's build number; commit first, or pass --allow-dirty"
    fi
fi
fetch=${fetch:-0}

# The toolchain.
java -version 2>&1 | grep -q 'version "17\.' || die "JDK 17 is needed (java -version says: $(java -version 2>&1 | head -1))"
sdk=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)
sdk=${sdk:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}
[ -n "$sdk" ] && [ -d "$sdk" ] || die "no Android SDK: put sdk.dir=/path/to/Android/Sdk in local.properties"

# The assets that aren't committed.
if [ "$fetch" = 1 ]; then
    echo "== fetch-assets"
    AndroidLuna/fetch-assets.sh
fi
[ -d AndroidLuna/local-assets/fw ] || die "AndroidLuna/local-assets/ is empty: run with --fetch (it needs Workbench/vendor/ and the NDK)"

gradle=(./gradlew --no-daemon --no-build-cache --console=plain)
[ "$test_apps" = 1 ] && gradle+=(-PtestApps)

# Last build's APKs of this type go first, so nothing stale is left in out/ to be mistaken
# for this build's.
expected=(AndroidLuna-arm32-$type.apk AndroidLuna-arm64-$type.apk LunaKeyboard-$type.apk)
for f in "${expected[@]}"; do rm -f "out/$f"; done

tasks=()
[ "$type" = release ] && tasks+=(clean)
tasks+=("assemble$Type")
[ "$lint" = 1 ] && tasks+=("lint$Type")

echo "== gradle ${tasks[*]}"
"${gradle[@]}" "${tasks[@]}"

for f in "${expected[@]}"; do [ -f "out/$f" ] || die "out/$f was not built"; done

if [ "$type" = release ]; then
    apksigner=$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
    [ -n "$apksigner" ] || die "no apksigner under $sdk/build-tools to check the signatures"
    for f in "${expected[@]}"; do
        # apksigner warns about every META-INF file a v1 signature doesn't cover; that is
        # Gradle's own metadata, so only a failure is shown.
        v=$("$apksigner" verify "out/$f" 2>&1) || { echo "$v" >&2; die "out/$f is not properly signed"; }
    done
    echo "signed by: $("$apksigner" verify --print-certs "out/${expected[0]}" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)"
fi

# Checksums of every APK in out/, so a debug and a release build can sit side by side.
(cd out && sha256sum -- *.apk > SHA256SUMS)

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' AndroidLuna/build.gradle.kts)
build=$(git rev-list --count HEAD 2>/dev/null || echo 1)
dirty=""; [ -n "$(git status --porcelain 2>/dev/null)" ] && dirty=" (uncommitted changes)"
echo
echo "Lunacy $version, build $build, $type, commit $(git rev-parse --short HEAD 2>/dev/null || echo none)$dirty"
for f in "${expected[@]}"; do
    printf '  out/%-32s %s\n' "$f" "$(du -h "out/$f" | cut -f1)"
done
echo "  out/SHA256SUMS"
