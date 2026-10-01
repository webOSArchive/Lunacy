// Lunacy: served in front of mojo.js, for a page that loads Mojo from a script rather than
// from its HTML. ES5 only: must run on Chromium 37.
//
// A page whose HTML carries the mojo.js tag gets Mojo's builtins in front of that tag by the
// serve-time transform (AppServer.mojoBuiltins), and this does nothing there. An Ares app
// writes the tag from ares.js with document.write, where no HTML transform can see it, so
// mojo.js would run without its framework and fall back to a loader.js that webOS never
// shipped ("The load of framework submission 506 failed"). Here, in the same file and before
// mojo.js's own code, the builtins are fetched synchronously - the page is still being parsed
// and nothing else can run - and evaluated in the global scope, in the order the transform
// would have written them; then mojo.js finds its palmInitFramework global as on a device.
(function () {
	var tag = document.querySelector("script[x-mojo-version],script[x-mojo-submission]") || document.currentScript;
	var src = (tag && tag.getAttribute("src")) || "";
	var two = /\/mojo2\/mojo\.js/.test(src);
	var version = (tag && tag.getAttribute("x-mojo-version")) || "";
	var submission = (tag && tag.getAttribute("x-mojo-submission")) || "";
	var name, libraries;
	if (two) {
		// Mojo 2's loader has no version map: it takes only x-mojo-submission, 205 by default,
		// and asks for mojo.core while the framework is still loading.
		name = "palmInitFramework2" + (submission || "205");
		libraries = ["palmunderscoreVersion1_0", "palmfoundationsVersion1_0", "palmglobalizationVersion1_0", "palmmojo_coreVersion1_0"];
	} else {
		// Mojo 1 maps x-mojo-version through its own table: 1 is submission 506.
		var versions = { "1": "506", "2": "344" };
		name = "palmInitFramework" + (submission || versions[version] || version || "506");
		libraries = ["InstallPrototypeBuiltIn"];
	}
	if (typeof window[name] === "function") { return; }
	function load(url) {
		var x = new XMLHttpRequest();
		x.open("GET", url, false);
		x.send(null);
		if (x.status !== 200 && x.status !== 0) { throw new Error("Lunacy: " + url + " answered " + x.status); }
		(0, eval)(x.responseText + "\n//# sourceURL=" + url);
	}
	for (var i = 0; i < libraries.length; i++) { load("/usr/palm/frameworks/mojo/builtins/" + libraries[i] + ".js"); }
	load("/usr/palm/frameworks/mojo/builtins/" + name + ".js");
	load("/__lunacy/mojo-boot.js");
})();
