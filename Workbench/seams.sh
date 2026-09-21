#!/bin/sh
# Find border-image seams in a running card: seams.sh <appid-substring> <name>
# Screenshots the tablet, reads every border-imaged widget's geometry out of the
# page, and reports the slice boundaries that show a one-pixel discontinuity.
# See seams.py, and "border-image seams" in Docs/fix-log.md.
set -e
cd "$(dirname "$0")"
app=$1
name=${2:-seams}
./lshot.sh "$name" >/dev/null
./cdp.sh eval '(function(){
  var out = [], all = document.querySelectorAll("*");
  for (var i = 0; i < all.length; i++) {
    var s = getComputedStyle(all[i]);
    var bi = s.webkitBorderImage || s.borderImage || "";
    if (bi.indexOf("url") !== 0) { continue; }
    var r = all[i].getBoundingClientRect();
    if (r.width < 8 || r.height < 8) { continue; }
    function n(v) { return parseFloat(v) || 0; }
    out.push({ cls: (all[i].className || all[i].tagName) + "",
               r: [r.left, r.top, r.width, r.height],
               bw: [n(s.borderTopWidth), n(s.borderRightWidth), n(s.borderBottomWidth), n(s.borderLeftWidth)],
               img: bi.substring(bi.lastIndexOf("/") + 1, bi.indexOf("\")")) });
  }
  return JSON.stringify(out);
})()' "$app" | sed -e 's/^"//' -e 's/"$//' -e 's/\\"/"/g' -e 's/\\\\/\\/g' > "results/$name.json"
python3 seams.py "results/$name.png" "results/$name.json" --status-bar 28
