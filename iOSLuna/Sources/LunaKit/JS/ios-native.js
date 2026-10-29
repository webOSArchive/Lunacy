// iOSLuna: window.LunacyNative on WKWebView. ES5, like the rest of Lunacy's page scripts.
//
// Lunacy's page scripts (bridge.js, net.js, websql.js, compat.js, textedit.js) talk to the host
// through one object, which Android provides with addJavascriptInterface: every method is a
// synchronous call into Kotlin. WKWebView has no synchronous channel, so this object is the
// same contract built from what it has:
//   - methods that only tell the host something post a message (webkit.messageHandlers);
//   - results a request is waiting for are pushed by the host *before* it calls Lunacy's own
//     "done" hook, so netResult and sqlResult answer from memory, as Android's answered from
//     its result map - this keeps working with the app in the background;
//   - values that don't change while the page lives are injected with this script
//     (__LUNACY_STATIC__);
//   - the few calls that must answer at once (opening a database, a synchronous XHR, the
//     screen's size) go through prompt(), which WKWebView hands to the host synchronously.
// Anything an iOS host has no counterpart for answers as "not here", never a fake success.
(function () {
	if (window.LunacyNative) { return; }
	var H = window.webkit && webkit.messageHandlers && webkit.messageHandlers.lunacy;
	if (!H) { return; }
	var S = __LUNACY_STATIC__;
	var PROMPT = "\u0000lunacy";

	function post(m, a) { H.postMessage({ m: m, a: a }); }
	function sync(m, a) {
		var r = window.prompt(PROMPT, JSON.stringify({ m: m, a: a }));
		if (r === null || r === undefined) { return null; }
		try { return JSON.parse(r).v; } catch (e) { return null; }
	}

	// Results waiting to be read, kept on the top window so a same-origin frame's request is
	// found whichever frame reads it (net.js and websql.js hand results between frames).
	var store = (function () {
		try {
			var t = window.top;
			return t.__lunacyIOSStore || (t.__lunacyIOSStore = { net: {}, sql: {} });
		} catch (e) { return { net: {}, sql: {} }; }
	})();
	function take(kind, id, missing) {
		var r = store[kind][id];
		if (r === undefined) { return missing; }
		delete store[kind][id];
		return r;
	}
	// The host's way in: it stores the result, then calls Lunacy's own hook, as Android does.
	window.__lunacyIOS = {
		net: function (id, json) { store.net[id] = json; if (window.__lunacyNetDone) { __lunacyNetDone(id); } },
		sql: function (id, json) { store.sql[id] = json; if (window.__lunacySqlDone) { __lunacySqlDone(id); } }
	};

	var banners = 0;
	var N = {
		// The bus (bridge.js). Replies come back through PalmServiceBridge.__reply.
		call: function (token, url, params) { post("call", [token, url, params]); },
		cancel: function (token) { post("cancel", [token]); },
		log: function (msg) { post("log", [String(msg)]); },

		// The network shim (net.js).
		netSend: function (id, request) { post("netSend", [id, request]); },
		netResult: function (id) { return take("net", id, '{"error":"error","errorText":"no result"}'); },
		netSendSync: function (request) { return sync("netSendSync", [request]); },
		netAbort: function (id) { post("netAbort", [id]); },

		// WebSQL (websql.js).
		sqlOpen: function (name, version, host, creation) { return sync("sqlOpen", [name, version, host, creation]); },
		sqlRequest: function (id, request) { post("sqlRequest", [id, request]); },
		sqlResult: function (id) { return take("sql", id, '{"error":{"code":0,"message":"no result"}}'); },

		// What the page is told about where it runs.
		deviceInfo: function () { return S.deviceInfo; },
		localeInfo: function () { return S.localeInfo; },
		engineMajor: function () { return -1; },  // not Chromium: none of compat.js's Chromium-version fixes apply
		processId: function () { return S.processId; },
		activityId: function () { return -1; },
		mediaBase: function () { return ""; },
		// "Send http:// images and media through /__lunacy/net": on iOS so they are declared as
		// what they are, since AVFoundation believes a server's type (NetRoute.swift). The name is
		// Android's reason for the same route.
		localNetworkBlocked: function () { return true; },
		screenSize: function () { return sync("screenSize", []); },
		cardSize: function () { return sync("cardSize", []); },
		screenOrientation: function () { return sync("screenOrientation", []); },
		windowOrientation: function () { return sync("screenOrientation", []); },

		// The window and its stage.
		stageReady: function () { post("stageReady", []); },
		pageDrawn: function () { post("pageDrawn", []); },
		activate: function () { post("activate", []); },
		fullScreen: function (on) { post("fullScreen", [!!on]); },
		requestOrientation: function (o) { post("requestOrientation", [String(o)]); },
		statusBarColor: function (rgb) { post("statusBarColor", [rgb]); },
		blockScreenTimeout: function (block) { post("blockScreenTimeout", [!!block]); },
		nextWindow: function (attributes) { post("nextWindow", [attributes]); },

		// Text input and the clipboard.
		keyboard: function (show) { post("keyboard", [!!show]); },
		keyboardResizes: function (resize) { post("keyboardResizes", [!!resize]); },
		setClipboard: function (text) { post("setClipboard", [String(text)]); },
		copiedToClipboard: function () {},
		clipboardHasText: function () { return !!sync("clipboardHasText", []); },
		paste: function () { post("paste", []); },
		cookieWritten: function () {},

		// Banners and sounds.
		addBanner: function (message, params, icon, soundClass, soundFile, duration) {
			var id = ++banners;
			post("addBanner", [id, message, params, icon, soundClass, soundFile, duration]);
			return id;
		},
		removeBanner: function (id) { post("removeBanner", [id]); },
		clearBanners: function () { post("clearBanners", []); },
		playSound: function (soundClass, soundFile, duration) { post("playSound", [soundClass, soundFile, duration]); },

		// No PDK plug-ins and no BrowserAdapter views on iOS: bridge.js and Enyo report these.
		pluginStart: function () { return 0; },
		pluginCall: function () { return ""; },
		pluginStop: function () {}
	};
	window.LunacyNative = N;

	// The page's console in the host's log, as AndroidLuna's WebChromeClient.onConsoleMessage
	// puts it in Android's; WKWebView has no such hook. The console itself is unchanged.
	["log", "info", "warn", "error"].forEach(function (level) {
		var orig = console[level];
		console[level] = function () {
			try {
				var parts = [];
				for (var i = 0; i < arguments.length; i++) {
					var x = arguments[i];
					parts.push(typeof x === "string" ? x : (x && x.stack) || (function () { try { return JSON.stringify(x); } catch (e) { return String(x); } })());
				}
				post("console", [level, parts.join(" ")]);
			} catch (e) {}
			return orig.apply(console, arguments);
		};
	});
	// Uncaught errors reach only the console (compat.js), so it reports them here too.

	// The lock screen's, Control Center's and a car's or headset's media buttons. While a page
	// plays, WebKit owns them and offers them through the Media Session API; without handlers it
	// only plays and pauses the element itself (behind the app's back) and the skip buttons stay
	// off. On webOS those buttons were keys, which apps heard from com.palm.keys/media/status,
	// so each action is handed to the host as the key a TouchPad's Bluetooth stack sent.
	var session = navigator.mediaSession;
	// Whether the last media key asked for silence: an app the person paused may sleep.
	var personPaused = false;
	if (session && session.setActionHandler) {
		// No previoustrack: the button would show and do nothing for an app that can't go back.
		var keys = { play: "play", pause: "pause", stop: "stop", nexttrack: "next" };
		for (var action in keys) {
			try {
				session.setActionHandler(action, (function (key) {
					return function () { personPaused = key === "pause" || key === "stop"; post("mediaKey", [key]); };
				})(keys[action]));
			} catch (e) {}
		}
	}
	post("mediaSession", [!!(session && session.setActionHandler)]);
	// What's playing, as a page tells it: its album art is an image like any other, so an
	// http:// one goes the way compat.js sends http:// images (__lunacyLocalUrl, NetRoute.swift).
	var Metadata = window.MediaMetadata;
	if (Metadata) {
		var Wrapped = function (init) {
			if (init && init.artwork && window.__lunacyLocalUrl) {
				init.artwork = init.artwork.map(function (a) {
					var copy = {};
					for (var k in a) { copy[k] = a[k]; }
					copy.src = __lunacyLocalUrl(a.src);
					return copy;
				});
			}
			return new Metadata(init);
		};
		Wrapped.prototype = Metadata.prototype;
		window.MediaMetadata = Wrapped;
	}

	// Playing on in the background. A webOS app kept running when its card wasn't in front;
	// iOS keeps a web page running in the background only while it is playing sound, and
	// suspends it seconds after the sound stops. A music app goes quiet between tracks, while a
	// skip loads the next one, and while it waits out a dropped connection; if the page is
	// suspended then, the app's own timers - which would start the next track or retry - never
	// fire until it is opened again (seen on the road, 2026-10-29: a skip, then a track that
	// loaded but was never started, and the page suspended ten seconds later). So in the
	// background, whenever the app's media stops for any reason but the person's own pause or
	// stop key, the page plays silence until real playback starts again, for three minutes at
	// most - long enough for an app's retries, short of keeping a broken app awake for good.
	var KEEP_MS = 180000, CHECK_MS = 15000, keep = null, keepTimer = 0, checkTimer = 0, watched = [];
	function inBackground() { return document.visibilityState === "hidden"; }
	function sounding() {
		for (var i = 0; i < watched.length; i++) {
			var m = watched[i];
			if (!m.paused && !m.ended && m.readyState > 2) { return true; }
		}
		return false;
	}
	function check(e) {
		if (!inBackground()) { return; }
		if (personPaused) { keepOff("paused by a media key"); return; }
		if (sounding()) { keepOff("playback started"); return; }
		keepOn(e ? e.type : "check");
	}
	function keepOn(why) {
		if (keep && !keep.paused) { return; }
		if (!keep) {
			keep = document.createElement("audio");
			keep.__lunacyKeepAlive = true;
			keep.loop = true;
			keep.src = "data:audio/wav;base64,UklGRmQfAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YUAfAACAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgA==";
		}
		try {
			var p = keep.play();
			if (p && p.catch) { p.catch(function (err) { post("log", ["keep-alive refused: " + err]); }); }
		} catch (err) { post("log", ["keep-alive refused: " + err]); }
		post("log", ["keep-alive on: app media " + why + " in the background"]);
		clearTimeout(keepTimer);
		keepTimer = setTimeout(function () { keepOff("three minutes passed"); }, KEEP_MS);
		clearInterval(checkTimer);
		checkTimer = setInterval(function () { if (sounding()) { keepOff("playback started"); } }, CHECK_MS);
	}
	function keepOff(why) {
		clearTimeout(keepTimer);
		clearInterval(checkTimer);
		if (keep && !keep.paused) { keep.pause(); post("log", ["keep-alive off: " + why]); }
	}
	// Apollo's players are new Audio() elements that never join the document, so their events
	// can't be caught there; each media element is watched from its first play() or src.
	function watch(m) {
		if (m.__lunacyKeepAlive || m.__lunacyWatched) { return; }
		m.__lunacyWatched = true;
		watched.push(m);
		["pause", "ended", "error", "emptied", "abort", "stalled", "waiting"].forEach(function (t) { m.addEventListener(t, check); });
		m.addEventListener("playing", function () { personPaused = false; keepOff("playback started"); });
	}
	// Telling what's playing again, when a car or headset connects (the host calls this). Many
	// head units read the elapsed time once when they connect and then wait for a track change,
	// so a song already playing showed a stuck clock until the next one. A fresh copy of the
	// page's metadata makes WebKit announce it again, with a snapshot of where the playing
	// element is; the snapshot is cleared a few seconds later, so WebKit goes back to following
	// the element itself and a stale position can't outlive the song.
	var positionTimer = 0;
	window.__lunacyIOS.reannounce = function () {
		if (!session) { return; }
		var md = session.metadata;
		if (md && window.MediaMetadata) {
			try {
				session.metadata = new MediaMetadata({ title: md.title, artist: md.artist, album: md.album, artwork: md.artwork });
			} catch (e) {}
		}
		var playing = null;
		for (var i = 0; i < watched.length; i++) {
			var m = watched[i];
			if (!m.paused && !m.ended && isFinite(m.duration) && m.duration > 0) { playing = m; }
		}
		if (playing && session.setPositionState) {
			try {
				session.setPositionState({ duration: playing.duration, playbackRate: playing.playbackRate || 1,
					position: Math.min(playing.currentTime, playing.duration) });
				clearTimeout(positionTimer);
				positionTimer = setTimeout(function () { try { session.setPositionState(); } catch (e) {} }, 5000);
			} catch (e) {}
		}
		post("log", ["now playing announced again" + (playing ? " at " + Math.round(playing.currentTime) + "s" : "")]);
	};

	var M = window.HTMLMediaElement && HTMLMediaElement.prototype, play = M && M.play, load = M && M.load;
	if (play) {
		M.play = function () { watch(this); return play.apply(this, arguments); };
		M.load = function () { watch(this); return load.apply(this, arguments); };
		document.addEventListener("visibilitychange", function () { if (!inBackground()) { keepOff("app in front"); } });
	}
})();
