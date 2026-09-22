#!/bin/sh
# tpclose.sh <appid>...: close running apps on the TouchPad by process id
cd "$(dirname "$0")"
R=$(./tp.sh 'luna-send -n 1 palm://com.palm.applicationManager/running "{}"' 3)
for id in "$@"; do
  for p in $(echo "$R" | grep -o "\"id\": \"$id\", \"processid\": \"[0-9]*\"" | grep -o '[0-9]*"$' | tr -d '"'); do
    ./tp.sh "luna-send -n 1 palm://com.palm.applicationManager/close '{\"processId\":\"$p\"}'" 2 >/dev/null
  done
done
