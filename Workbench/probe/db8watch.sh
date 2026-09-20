#!/bin/sh
# db8 watches on a TouchPad: find with watch, and the watch method, each then a put.
A=org.webosarchive.lunacy.probe
K=org.webosarchive.lunacy.probe.w:1
luna-send -n 1 -a $A palm://com.palm.db/delKind "{\"id\":\"$K\"}" >/dev/null 2>&1
luna-send -n 1 -a $A palm://com.palm.db/putKind "{\"id\":\"$K\",\"owner\":\"$A\",\"indexes\":[{\"name\":\"byName\",\"props\":[{\"name\":\"name\"}]}]}"
luna-send -n 1 -a $A palm://com.palm.db/put "{\"objects\":[{\"_kind\":\"$K\",\"name\":\"a\"}]}"
echo "== find watch"; luna-send -n 2 -a $A palm://com.palm.db/find "{\"query\":{\"from\":\"$K\"},\"watch\":true}" > /tmp/w1.txt 2>&1 &
echo "== watch"; luna-send -n 2 -a $A palm://com.palm.db/watch "{\"query\":{\"from\":\"$K\",\"where\":[{\"prop\":\"name\",\"op\":\"=\",\"val\":\"b\"}]}}" > /tmp/w2.txt 2>&1 &
sleep 2
echo "== put b"; luna-send -n 1 -a $A palm://com.palm.db/put "{\"objects\":[{\"_kind\":\"$K\",\"name\":\"b\"}]}"
sleep 2
echo "== find watch replies"; cat /tmp/w1.txt
echo "== watch replies"; cat /tmp/w2.txt
echo "== get deleted"; ID=$(luna-send -n 1 -a $A palm://com.palm.db/put "{\"objects\":[{\"_kind\":\"$K\",\"name\":\"c\"}]}" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'); luna-send -n 1 -a $A palm://com.palm.db/del "{\"ids\":[\"$ID\"]}"; luna-send -n 1 -a $A palm://com.palm.db/get "{\"ids\":[\"$ID\"]}"
echo "== find incDel no order"; luna-send -n 1 -a $A palm://com.palm.db/find "{\"query\":{\"from\":\"$K\",\"incDel\":true}}"
echo "== put with _rev"; R=$(luna-send -n 1 -a $A palm://com.palm.db/put "{\"objects\":[{\"_kind\":\"$K\",\"name\":\"d\"}]}"); I2=$(echo "$R" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'); V2=$(echo "$R" | sed -n 's/.*"rev":\([0-9]*\).*/\1/p'); luna-send -n 1 -a $A palm://com.palm.db/put "{\"objects\":[{\"_id\":\"$I2\",\"_rev\":$V2,\"_kind\":\"$K\",\"name\":\"d2\"}]}"; luna-send -n 1 -a $A palm://com.palm.db/get "{\"ids\":[\"$I2\"]}"
echo "== put stale _rev"; luna-send -n 1 -a $A palm://com.palm.db/put "{\"objects\":[{\"_id\":\"$I2\",\"_rev\":1,\"_kind\":\"$K\",\"name\":\"d3\"}]}"
echo "== page"; N=$(luna-send -n 1 -a $A palm://com.palm.db/find "{\"query\":{\"from\":\"$K\",\"orderBy\":\"name\",\"limit\":1}}" | sed -n 's/.*"next":"\([^"]*\)".*/\1/p'); luna-send -n 1 -a $A palm://com.palm.db/find "{\"query\":{\"from\":\"$K\",\"orderBy\":\"name\",\"limit\":1,\"page\":\"$N\"}}"
luna-send -n 1 -a $A palm://com.palm.db/delKind "{\"id\":\"$K\"}"
