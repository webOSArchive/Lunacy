// Lunacy's compat layer, part 3 of 4 (see input.js): URLs and media - webOS paths and the local
// network in src attributes, Audio and markup, ordered scripts, a player's progress, an empty
// src - and WebSQL's message and arity. ES5 only.
// file:///media/internal/... (webOS's user storage, where JS services leave files for apps)
// is served on every app origin at /media/internal/...; pages ran from file:// on webOS and
// used those URLs directly.
// Media elements get the loopback MediaServer instead: Chromium hands HLS to Android's
// MediaPlayer, which can't reach app origins.
window.__lunacyFileUrl = function (u, media) {
	if (typeof u !== "string" || !/^file:\/\/\/media\/internal\//i.test(u)) { return u; }
	var N = window.LunacyNative;
	return media && N && N.mediaBase && !__lunacyLocalBlocked ? N.mediaBase() + u.slice(7) : u.slice(7);
};

// Local network. webOS pages, on file://, could show any http:// image and play any http://
// stream - a Plex client's thumbnails and tracks on its server at http://192.168.x.x. A current
// WebView blocks an https page's plain-http requests to a private address and holds one to the
// loopback interface for good, mixed content allowed or not (LocalNet.kt). Where it does, the
// page's http:// images and media go to /__lunacy/net on its own origin, and Lunacy fetches
// them. Elsewhere nothing changes.
var __lunacyLocalBlocked = !!(window.LunacyNative && LunacyNative.localNetworkBlocked && LunacyNative.localNetworkBlocked());
window.__lunacyLocalUrl = function (u) {
	if (!__lunacyLocalBlocked || typeof u !== "string" || !/^http:\/\//i.test(u)) { return u; }
	return "/__lunacy/net?u=" + encodeURIComponent(u);
};

// Pages that put a file:// URL in a frame or an image, as webOS pages could: an app's own or
// another app's files (Enyo's CrossAppUI loads an app's UI from the file:// base path the
// application manager gives, as Email does its account wizard), the system's files under
// /usr/palm, and the file cache, where the mail services keep message bodies and attachments.
// An app's files are on that app's origin; the rest are on every origin.
window.__lunacyPageUrl = function (u) {
	if (typeof u !== "string" || u.slice(0, 8).toLowerCase() !== "file:///") { return u; }
	var app = /^file:\/\/\/media\/cryptofs\/apps\/usr\/palm\/applications\/([^\/?#]+)(.*)$/i.exec(u);
	if (app) { return "https://" + app[1] + ".media.cryptofs.apps" + u.slice(7); }
	if (/^file:\/\/\/(usr\/palm|var\/file-cache|media\/internal)\//i.test(u)) { return u.slice(7); }
	return u;
};
(function () {
	[window.HTMLIFrameElement, window.HTMLImageElement].forEach(function (E) {
		var P = E && E.prototype, d = P && Object.getOwnPropertyDescriptor(P, "src");
		if (!d || !d.set) { return; }
		var map = E === window.HTMLImageElement ? function (v) { return __lunacyLocalUrl(__lunacyPageUrl(v)); } : __lunacyPageUrl;
		Object.defineProperty(P, "src", {
			configurable: true, enumerable: d.enumerable, get: d.get,
			set: function (v) { d.set.call(this, map(v)); }
		});
		var set = P.setAttribute;
		P.setAttribute = function (name, value) {
			return set.call(this, name, String(name).toLowerCase() === "src" ? map(value) : value);
		};
	});
})();

// The same for URLs given to new Audio(url) and to setAttribute("src") on media elements, as
// SoundManager2 does.
(function () {
	var map = function (u) { return window.__lunacyLocalUrl(window.__lunacyFileUrl(u, true)); };
	var M = window.HTMLMediaElement && HTMLMediaElement.prototype, S = window.HTMLSourceElement && HTMLSourceElement.prototype;
	[M, S].forEach(function (P) {
		if (!P) { return; }
		var set = P.setAttribute;
		P.setAttribute = function (name, value) {
			var src = String(name).toLowerCase() === "src";
			if (src && P === M && window.__lunacyWatchProgress) { __lunacyWatchProgress(this); }
			return set.call(this, name, src ? map(value) : value);
		};
	});
	var A = window.Audio;
	if (A) {
		var Audio = function (src) {
			var a = arguments.length && src !== undefined ? new A(map(src)) : new A();
			if (window.__lunacyWatchProgress) { __lunacyWatchProgress(a); }
			return a;
		};
		Audio.prototype = A.prototype;
		window.Audio = Audio;
	}
})();

// An ordered script that fails held the page's load event for good on old engines. A page may
// add scripts to run in order (script.async = false, as LAB.js does); on WebView 44 (Nexus 5,
// measured 2026-10-09) one that fails to load - a 404, a dead host - before the page's load
// event leaves the document waiting for it: load never fires, readyState stays "interactive",
// and Enyo never calls stageReady, so the card stays on its splash. Apollo for phones loads
// Villo's patch.js from a host that is gone, and never opened. The TouchPad's WebKit fired
// load, as WebView 153 does (Pixel Tablet, measured the same day). Removing the failed
// element doesn't release it; a failed async script doesn't hold it. So until load, such
// scripts are added as async ones, one at a time, each once the one before has run or
// failed: the same order, and their error events as before. Engines below 60 only: the fix
// is in Chromium's 2015-16 ScriptRunner changes, so the cut-off is an assumption, not a
// measurement.
(function () {
	var N = window.LunacyNative, major = N && N.engineMajor ? N.engineMajor() : -1;
	if (major < 0 || major >= 60 || !window.HTMLScriptElement) { return; }
	var loaded = false, busy = false, queue = [];
	window.addEventListener("load", function () { loaded = true; });
	function run() {
		if (busy || !queue.length) { return; }
		busy = true;
		queue.shift()();
	}
	function chain(name) {
		var P = Node.prototype, orig = P[name];
		P[name] = function (el) {
			if (loaded || !(el instanceof HTMLScriptElement) || el.async !== false || !el.src || el.__lunacyOrdered) {
				return orig.apply(this, arguments);
			}
			el.__lunacyOrdered = true;
			el.async = true;
			var parent = this, args = arguments;
			queue.push(function () {
				var done = function () {
					el.removeEventListener("load", done);
					el.removeEventListener("error", done);
					busy = false;
					run();
				};
				el.addEventListener("load", done);
				el.addEventListener("error", done);
				try { orig.apply(parent, args); } catch (e) { done(); throw e; }
			});
			run();
			return el;
		};
	}
	chain("appendChild");
	chain("insertBefore");
})();

// A player that has its data says so. webOS's media server fired progress while a track
// loaded; WebView 44 hands audio to Android's MediaPlayer, which fires none before playback:
// loadstart, suspend, then loadedmetadata and canplay with nothing buffered (Nexus 5,
// measured 2026-10-09). Apollo starts a track only once it has seen progress, and sat
// silent at canplay. So on those engines an element that reaches loadeddata without a
// progress since its loadstart gets one then. Below Chromium 60, as above: an assumption
// about when Chromium stopped using MediaPlayer for audio, measured only at 44 (needed) and
// 153 (not).
window.__lunacyWatchProgress = (function () {
	var N = window.LunacyNative, major = N && N.engineMajor ? N.engineMajor() : -1;
	if (major < 0 || major >= 60) { return function () {}; }
	return function (el) {
		if (!el || el.__lunacyProgress) { return; }
		var seen = el.__lunacyProgress = { seen: false };
		el.addEventListener("loadstart", function () { seen.seen = false; }, true);
		el.addEventListener("progress", function () { seen.seen = true; }, true);
		el.addEventListener("loadeddata", function () {
			if (seen.seen) { return; }
			seen.seen = true;
			var e = document.createEvent("Event");
			e.initEvent("progress", false, false);
			el.dispatchEvent(e);
		}, true);
	};
})();
(function () {
	var P = window.HTMLMediaElement && HTMLMediaElement.prototype, load = P && P.load;
	if (!load) { return; }
	P.load = function () { __lunacyWatchProgress(this); return load.apply(this, arguments); };
})();

// An empty src leaves a media element empty and silent. On the TouchPad, audio.src = "" fired
// no event and left networkState at 0 (Workbench/probe 0.0.8); Chromium starts a load and fires
// error. enyo.Sound sets src = "" on every new sound, and apps that treat that error as a
// failed track (Apollo) never play. Setting "" removes the attribute instead.
(function () {
	var P = window.HTMLMediaElement && HTMLMediaElement.prototype;
	var d = P && Object.getOwnPropertyDescriptor(P, "src");
	if (!d || !d.set) { return; }
	Object.defineProperty(P, "src", {
		configurable: true, enumerable: d.enumerable, get: d.get,
		set: function (v) {
			if (v === "") { this.removeAttribute("src"); return; }
			if (window.__lunacyWatchProgress) { __lunacyWatchProgress(this); }
			d.set.call(this, window.__lunacyFileUrl ? __lunacyLocalUrl(__lunacyFileUrl(v, true)) : v);
		}
	});
})();

// Markup gets the same: an http:// image or player that a framework writes as HTML (Enyo
// renders its controls as markup) is pointed at /__lunacy/net as it arrives. The engine has
// already refused the first request by then; this asks again where it will be answered.
(function () {
	if (!__lunacyLocalBlocked || !window.MutationObserver) { return; }
	var SEL = "img[src^='http:'],img[src^='HTTP:'],audio[src^='http:'],video[src^='http:'],source[src^='http:']";
	var fix = function (el) { el.setAttribute("src", el.getAttribute("src")); };
	new MutationObserver(function (records) {
		for (var i = 0; i < records.length; i++) {
			var added = records[i].addedNodes;
			for (var j = 0; j < added.length; j++) {
				var n = added[j];
				if (n.nodeType !== 1) { continue; }
				if (n.matches && n.matches(SEL)) { fix(n); }
				var inner = n.querySelectorAll ? n.querySelectorAll(SEL) : [];
				for (var k = 0; k < inner.length; k++) { fix(inner[k]); }
			}
		}
	}).observe(document, { childList: true, subtree: true });
})();

// WebSQL, which is where Mojo apps keep their data. Two things about the reference TouchPad's
// (WebKit 534.6, Workbench/probe 0.1.0) differ from Chromium's, and an app meets both while
// starting.
//
// 1. openDatabase took a name and a version. Its arity there is 0 - nothing is required -
//    and `openDatabase("x", "1.0")` opens a database at version 1.0. The standard made the
//    display name and the size hint required, and Chromium throws "4 arguments required, but
//    only 2 present". Neither missing argument is observable here: both only ever appeared in
//    the quota prompts a desktop browser showed and webOS did not, so they are filled in with
//    the database's own name and webOS's 5 MB allowance.
//
// 2. An SQLError's message was SQLite's own: the probe's failing SELECT reported
//    `{"code":5,"message":"no such table: nosuchtable"}`, where Chromium reports "could not
//    prepare statement (1 no such table: nosuchtable)". Apps read that message. drPodder
//    creates its tables on the strength of `error.message === "no such table: feed"`, so
//    under Chromium's wording it opened an empty database and sat at "Loading Feeds" for
//    ever. The wrapper is stripped back off, leaving the message the app was written against.
(function () {
	var open = window.openDatabase;
	if (!open) { return; }
	var WEBOS_QUOTA = 5 * 1024 * 1024;
	var WRAPPED = /^could not [a-z ]+ \(\d+ ([\s\S]*)\)$/;

	// SQLError has no constructor on the window, so its class is reached through an instance.
	function unwrapMessage(e) {
		var proto = e && typeof e === "object" && typeof e.code === "number" && e.SYNTAX_ERR !== undefined ?
			Object.getPrototypeOf(e) : null;
		var d = proto && Object.getOwnPropertyDescriptor(proto, "message");
		if (!d || !d.get || d.get.__lunacy) { return; }
		var get = function () {
			var m = d.get.call(this), w = WRAPPED.exec(m);
			return w ? w[1] : m;
		};
		get.__lunacy = true;
		try {
			Object.defineProperty(proto, "message", { configurable: true, enumerable: d.enumerable, get: get });
		} catch (err) {}
	}

	// Every callback the app hands to WebSQL may be given an SQLError; the first one to
	// arrive fixes the class for all of them.
	function guard(fn) {
		if (typeof fn !== "function") { return fn; }
		return function () {
			for (var i = 0; i < arguments.length; i++) { unwrapMessage(arguments[i]); }
			return fn.apply(this, arguments);
		};
	}

	function patchTransaction(tx) {
		var proto = tx && Object.getPrototypeOf(tx);
		if (!proto || proto.__lunacySql || typeof proto.executeSql !== "function") { return; }
		proto.__lunacySql = true;
		var exec = proto.executeSql;
		proto.executeSql = function (sql, args, ok, fail) {
			return exec.call(this, sql, args, ok, guard(fail));
		};
	}

	function patchDatabase(db) {
		var proto = db && Object.getPrototypeOf(db);
		if (!proto || proto.__lunacySql) { return db; }
		proto.__lunacySql = true;
		["transaction", "readTransaction", "changeVersion"].forEach(function (name) {
			var orig = proto[name];
			if (typeof orig !== "function") { return; }
			proto[name] = function () {
				var args = Array.prototype.slice.call(arguments), seen = false;
				for (var i = 0; i < args.length; i++) {
					if (typeof args[i] !== "function") { continue; }
					// The first function is the one the transaction itself runs; the rest
					// report on it, and are where an SQLError arrives.
					args[i] = seen ? guard(args[i]) : (function (cb) {
						return function (tx) { patchTransaction(tx); return cb.apply(this, arguments); };
					})(args[i]);
					seen = true;
				}
				return orig.apply(this, args);
			};
		});
		return db;
	}

	window.openDatabase = function (name, version, displayName, size, creation) {
		if (arguments.length >= 4) { return patchDatabase(open.apply(window, arguments)); }
		return patchDatabase(open.call(window, name, version,
			displayName === undefined ? name : displayName, WEBOS_QUOTA, creation));
	};
})();
