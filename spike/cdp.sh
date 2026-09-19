#!/bin/sh
# DevTools on a Lunacy page from the command line: forwards the running Lunacy process's
# WebView DevTools socket, then runs cdp.mjs. See docs/dev-workflow.md.
#   ./cdp.sh eval 'document.title' com.jmtk.apollo
#   ./cdp.sh send Page.reload '{}' com.jmtk.apollo
#   ./cdp.sh inject 'console.log("before any script")' com.jmtk.apollo
#   ./cdp.sh list
cd "$(dirname "$0")"
PORT=${CDP_PORT:-9223}
PID=$(adb shell ps | awk '$NF ~ /^org\.webosarchive\.lunacy\r?$/ {print $2}' | head -1)
[ -n "$PID" ] || { echo "Lunacy isn't running" >&2; exit 1; }
adb forward tcp:$PORT localabstract:webview_devtools_remote_$PID >/dev/null
CDP_PORT=$PORT exec node cdp.mjs "$@"
