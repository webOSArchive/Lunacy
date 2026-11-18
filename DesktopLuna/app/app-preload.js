// window.LunacyNative in a Luna DE window: the desktop's side of the interface AppWindow.Native
// gives Android's WebView (Docs/luna-de.md). Every method is a synchronous request to the page's
// own origin, which Electron's main process hands to the host, naming the app by that origin.
// Bus replies come back as scripts, which a long poll fetches and runs in the page.
"use strict";
(function () {
	// Each load of a page is a page of its own: a reply meant for the last one never reaches this.
	var page = Date.now().toString(36) + Math.random().toString(36).slice(2);
	var base = location.origin + "/__luna-de/";
	// The methods AppWindow.Native has (AndroidLuna/.../card/AppWindow.kt).
	var METHODS = ["deviceInfo", "call", "cancel", "stageReady", "pageDrawn", "screenOrientation",
		"cardSize", "windowOrientation", "requestOrientation", "screenSize", "localeInfo", "processId",
		"activityId", "mediaBase", "localNetworkBlocked", "activate", "keyboard", "keyboardResizes",
		"blockScreenTimeout", "fullScreen", "statusBarColor", "cookieWritten", "webViewCreate", "webViewCall",
		"webViewPlace", "webViewCovered", "webViewDestroy", "pluginStart", "pluginCall", "pluginStop", "log",
		"nextWindow", "addBanner", "paste", "clipboardHasText", "removeBanner", "clearBanners", "playSound",
		"setClipboard", "copiedToClipboard", "netSend", "netResult", "netSendSync", "netAbort",
		"sqlOpen", "sqlRequest", "sqlResult",
		// Luna DE's own: the page has loaded, and a script's answer to the host (a relaunch's).
		"pageLoaded", "scriptResult"];

	function invoke(method, args) {
		var x = new XMLHttpRequest();
		x.open("POST", base + "native?w=" + page + "&m=" + method, false);
		x.send(JSON.stringify(args));
		if (x.status !== 200 || !x.responseText) { return null; }
		try { return JSON.parse(x.responseText); } catch (e) { return null; }
	}

	var N = {};
	METHODS.forEach(function (m) {
		N[m] = function () { return invoke(m, Array.prototype.slice.call(arguments)); };
	});
	// This engine's own version, for the compat layer's engine-bound fixes.
	N.engineMajor = function () { return parseInt(process.versions.chrome, 10) || -1; };
	window.LunacyNative = N;
	// The main process knows the page by this id, and tells the host which window it is in (the
	// host holds the page's first native call until it has heard). Not a synchronous message: one
	// sent from a window as window.open makes it was never answered, and hung the app.
	var ipc = null;
	try { ipc = require("electron").ipcRenderer; ipc.send("luna-de:page", page); } catch (e) {}
	// webOS drew no scrollbars on a window: apps scroll in their frameworks' own scrollers. Chromium
	// on a desktop draws them on a page larger than its window, and each takes 15 px from the page,
	// which makes it larger than its window the other way too (a Mojo scene laid out at the card's
	// size). The window's bars only, as Android's AppWindow turns its WebView's off; a scroller
	// inside the page keeps what it draws.
	try {
		require("electron").webFrame.insertCSS(
			"html::-webkit-scrollbar, body::-webkit-scrollbar { display: none !important; }", { cssOrigin: "user" });
	} catch (e) { console.error("Luna DE: window scrollbars: " + e); }
	// Text Assist, for the on-screen keyboard's typing into this page's fields (textassist.js).
	try { require("./textassist.js")(window, ipc); } catch (e) { console.error("Luna DE: text assist: " + e); }
	// window.close() closes this page's own window (its card, its dashboard), as on webOS. Electron's
	// own would close the window the view sits in, which is the whole device, and Luna DE with it.
	if (ipc) { try { window.close = function () { ipc.send("luna-de:close-self"); }; } catch (e) {} }
	/**
	 * A dashboard row's sweep (DashboardRow on Android): the row owns the pointer (all of it, or
	 * with [manual] only the 50 px badge at the left) and the page never sees it go down. Moved
	 * sideways past the slop, the row follows it (the main process moves the view); let go, the
	 * main process decides whether it goes. A press that never moved is the page's after all: a
	 * press and a release where it went down, and a click.
	 */
	window.__lunaDeDashboard = function (manual) {
		if (window.__lunaDeDashboardOn || !ipc) { return; }
		window.__lunaDeDashboardOn = true;
		var BADGE = 50, SLOP = 8, down = null, replaying = false;
		function block(e) { if (!replaying) { e.stopImmediatePropagation(); e.preventDefault(); } }
		window.addEventListener("pointerdown", function (e) {
			if (replaying || e.button !== 0 || (manual && e.clientX > BADGE)) { down = null; return; }
			down = { x: e.clientX, y: e.clientY, moved: false, dragging: false, id: e.pointerId };
			try { document.documentElement.setPointerCapture(e.pointerId); } catch (err) {}
			e.stopImmediatePropagation(); e.preventDefault();
		}, true);
		window.addEventListener("pointermove", function (e) {
			if (!down) { return; }
			e.stopImmediatePropagation();
			var dx = e.clientX - down.x, dy = e.clientY - down.y;
			if (!down.moved && (Math.abs(dx) > SLOP || Math.abs(dy) > SLOP)) { down.moved = true; down.dragging = Math.abs(dx) > Math.abs(dy); }
			if (down.dragging) { ipc.send("luna-de:dash-drag", dx); }
		}, true);
		window.addEventListener("pointerup", function (e) {
			var d = down;
			if (!d) { return; }
			e.stopImmediatePropagation(); e.preventDefault();
			down = null;
			// The browser's own click after this release is the row's too: the page gets its own, below, or none.
			window.__lunaDeSwallowClick = true;
			setTimeout(function () { window.__lunaDeSwallowClick = false; }, 0);
			if (d.moved) { if (d.dragging) { ipc.send("luna-de:dash-release"); } return; }
			var el = document.elementFromPoint(d.x, d.y);
			if (!el) { return; }
			replaying = true;
			try {
				["mousedown", "mouseup", "click"].forEach(function (t) {
					el.dispatchEvent(new MouseEvent(t, { bubbles: true, cancelable: true, view: window, clientX: d.x, clientY: d.y, button: 0, buttons: t === "mousedown" ? 1 : 0 }));
				});
			} finally { replaying = false; }
		}, true);
		window.addEventListener("pointercancel", function () { if (down && down.dragging) { ipc.send("luna-de:dash-release"); } down = null; }, true);
		["mousedown", "mouseup", "click", "mousemove"].forEach(function (t) { window.addEventListener(t, function (e) { if (!replaying && (down || t === "click" && window.__lunaDeSwallowClick)) { block(e); } }, true); });
	};
	// What the page is about to open (window.open's attributes: a card, a dashboard, a popup
	// alert) is Electron's to place, so it hears of it before the open reaches it.
	N.nextWindow = function (attributesJson) { if (ipc) { try { ipc.sendSync("luna-de:next-window", String(attributesJson)); } catch (e) {} } return null; };
	window.addEventListener("load", function () { N.pageLoaded(); });

	function poll() {
		var x = new XMLHttpRequest();
		x.open("GET", base + "poll?w=" + page, true);
		x.onload = function () {
			var scripts = [];
			try { scripts = JSON.parse(x.responseText); } catch (e) {}
			scripts.forEach(function (js) {
				try { (0, eval)(js); } catch (e) { console.error("Luna DE: " + e); }
			});
			setTimeout(poll, 0);
		};
		x.onerror = function () { setTimeout(poll, 1000); };
		x.send();
	}
	poll();

	// Mojo logs everything here. On a device Mojo.Log starts at errors only and an app raises it
	// in its framework_config.json (logLevel 99); Luna DE is a developer's tool, so info and
	// warnings show whatever the app set (codepoet, 2026-11-16; a delta, in Docs/luna-de.md).
	// The level is held as soon as the framework's script has run, before the app's own.
	function loudMojo() {
		var log = window.Mojo && window.Mojo.Log;
		if (!log || log.__loud) { return; }
		try {
			Object.defineProperty(log, "currentLogLevel", { get: function () { return 99; }, set: function () {}, configurable: true });
			log.__loud = true;
		} catch (e) {}
	}
	document.addEventListener("load", loudMojo, true);
	document.addEventListener("DOMContentLoaded", loudMojo);

	window.addEventListener("pagehide", function () {
		try { navigator.sendBeacon(base + "gone?w=" + page); } catch (e) {}
	});
})();
