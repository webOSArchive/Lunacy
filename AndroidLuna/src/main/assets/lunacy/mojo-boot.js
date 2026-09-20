// Lunacy: what webOS's browser did for a Mojo page, before mojo.js runs.
// ES5 only: must run on Chromium 37.
//
// On a device the framework was compiled into the browser. mojo.js looks for a global
// palmInitFramework<submission> and calls it; if it isn't there it falls back to loading
// javascripts/loader.js from the submission, which webOS doesn't ship - the submission on
// disk carries only assets. So Lunacy serves the builtins that carry the code (the serve-time
// transform in AppServer puts them in front of the app's own tag) and this file makes them
// look the way mojo.js expects.
(function () {
	// mojo.js calls the framework as (window, navigator, document), while the builtin is
	// declared as (window, document, navigator). On a device the browser's own copy took them
	// in the order mojo.js passes; this one is the file Palm shipped for other hosts, and its
	// parameters shadow the globals inside a `with (window)`, so the wrong order would leave
	// the framework calling document methods on navigator. Pass what it actually wants.
	var names = [];
	for (var k in window) {
		if (/^palmInitFramework/.test(k) && typeof window[k] === "function") { names.push(k); }
	}
	for (var i = 0; i < names.length; i++) {
		(function (name) {
			var real = window[name];
			if (real.__lunacyWrapped) { return; }
			var wrapper = function () { return real(window, document, navigator); };
			wrapper.__lunacyWrapped = true;
			window[name] = wrapper;
		})(names[i]);
	}
})();
