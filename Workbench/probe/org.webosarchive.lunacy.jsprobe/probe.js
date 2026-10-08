// What the page's JavaScript engine does with "use strict", which apps revived today carry in
// files a 2011 engine ran. webOS 3.x's WebKit (534.6) may predate strict mode; Chromium
// honours it. Read with: palm-log -f org.webosarchive.lunacy.jsprobe (webOS), or logcat.
var out = [];
function say(s) {
	out.push(s);
	try { console.log("JSPROBE " + s); } catch (e) {}
}
function ask(name, fn) {
	var v;
	try { v = String(fn()); } catch (e) { v = "threw: " + e; }
	say(name + "=" + v);
}

// A sloppy function reading its caller, as Enyo's _log does (arguments.callee.caller.nom).
function sloppyCaller() { var c = arguments.callee.caller; return c === null ? "null" : typeof c; }

ask("strictThis", function () { return (function () { "use strict"; return this; })() === undefined; });
ask("strictCaller", function () { return (function () { "use strict"; return sloppyCaller(); })(); });
ask("sloppyCaller", function () { return (function () { return sloppyCaller(); })(); });
ask("strictUndeclared", function () { return (function () { "use strict"; jsprobeUndeclared = 1; return "assigned"; })(); });
ask("strictReadonly", function () { return (function () { "use strict"; var o = {}; Object.defineProperty(o, "x", {value: 1}); o.x = 2; return "no throw"; })(); });
ask("strictDupParams", function () { return typeof eval("(function(){ 'use strict'; return function(a, a){}; })()"); });
ask("strictWith", function () { return eval("(function(){ 'use strict'; with ({}) {} return 'allowed'; })()"); });
ask("strictArgsCallee", function () { return (function () { "use strict"; return typeof arguments.callee; })(); });
ask("strictOctal", function () { return eval("(function(){ 'use strict'; return 010; })()"); });
ask("userAgent", function () { return navigator.userAgent; });

// Collections called as functions, as 2011 WebKit allowed (Quick Office's spreadsheet grid
// calls table.children(i) and row.cells(i)): which collections, what an index, a name or an
// index out of range gives, and whether the collection is still live and the same object.
function collections() {
	var box = document.createElement("div");
	box.innerHTML = '<p id="pa">a</p><p id="pb">b</p><table><tbody><tr><td id="c0">0</td><td>1</td></tr></tbody></table>';
	document.body.appendChild(box);
	var t = box.getElementsByTagName("table")[0], row = t.rows[0];
	ask("typeofChildren", function () { return typeof box.children; });
	ask("childrenToString", function () { return Object.prototype.toString.call(box.children); });
	ask("childrenInstanceof", function () { return box.children instanceof HTMLCollection; });
	ask("childrenSame", function () { return box.children === box.children; });
	ask("childrenCall0", function () { return box.children(0) === box.children[0]; });
	ask("childrenCallName", function () { var r = box.children("pb"); return r && r.id; });
	ask("childrenCallOut", function () { var r = box.children(9); return r === null ? "null" : typeof r; });
	ask("childrenCallString1", function () { var r = box.children("1"); return r && r.id; });
	ask("childrenLive", function () { var c = box.children, n = c.length; box.appendChild(document.createElement("i")); var m = c.length; box.removeChild(box.lastChild); return n + "->" + m; });
	ask("childrenLength", function () { return box.children.length; });
	ask("cellsCall", function () { return row.cells(0) === row.cells[0]; });
	ask("cellsCallName", function () { var r = row.cells("c0"); return r && r.id; });
	ask("rowsCall", function () { return t.rows(0) === row; });
	ask("tBodiesCall", function () { return t.tBodies(0) === t.tBodies[0]; });
	ask("childNodesCall", function () { return box.childNodes(0) === box.childNodes[0]; });
	ask("byTagCall", function () { return box.getElementsByTagName("p")(1).id; });
	ask("formsCall", function () { return typeof document.forms(0); });
	ask("typeofChildNodes", function () { return typeof box.childNodes; });
	document.body.removeChild(box);
}

window.onload = function () {
	collections();
	document.getElementById("out").appendChild(document.createTextNode(out.join("\n")));
};
