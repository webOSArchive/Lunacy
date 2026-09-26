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

window.onload = function () {
	document.getElementById("out").appendChild(document.createTextNode(out.join("\n")));
};
