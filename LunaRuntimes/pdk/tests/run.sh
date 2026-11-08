#!/bin/sh
# The PDK runtime's host tests: built with the build machine's cc and run there. Any failure
# ends with a non-zero exit, which stops AndroidLuna/tools/build-pdk.sh (and so a release).
#   LunaRuntimes/pdk/tests/run.sh [work dir]
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${1:-${TMPDIR:-/tmp}/lunacy-pdk-tests}
mkdir -p "$WORK"
CC=${HOST_CC:-cc}
command -v "$CC" >/dev/null || { echo "pdk tests: no host C compiler ($CC); set HOST_CC" >&2; exit 1; }
"$CC" -O2 -Wall -pthread -I"$HERE/../sdl-lunacy" "$HERE/send_test.c" -o "$WORK/send_test"
timeout 60 "$WORK/send_test"
"$CC" -O2 -Wall -I"$HERE/../libpreload" "$HERE/mailauth_test.c" -o "$WORK/mailauth_test"
timeout 60 "$WORK/mailauth_test"
"$CC" -O2 -Wall -I"$HERE/../libpreload" "$HERE/wbxmlfilter_test.c" -o "$WORK/wbxmlfilter_test"
timeout 60 "$WORK/wbxmlfilter_test"
"$CC" -O2 -Wall -I"$HERE/../libpreload" "$HERE/composemail_test.c" -o "$WORK/composemail_test"
timeout 60 "$WORK/composemail_test"
"$CC" -O2 -Wall -I"$HERE/../libpreload" "$HERE/backoff_test.c" -o "$WORK/backoff_test"
timeout 60 "$WORK/backoff_test"
"$CC" -O2 -Wall -I"$HERE/../libpreload" "$HERE/calfilter_test.c" -o "$WORK/calfilter_test"
timeout 60 "$WORK/calfilter_test"
