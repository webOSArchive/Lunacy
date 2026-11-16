// window.LunacyNative in a Luna DE window: the desktop's side of the interface AppWindow.Native
// gives Android's WebView (Docs/luna-de.md). Every method is a synchronous request to the page's
// own origin, which Electron's main process hands to the host, naming the app by that origin.
// Bus replies come back as scripts, which a long poll fetches and runs in the page.
"use strict";
(function () {
	// Each load of a page is a page of its own: a reply meant for the last one never reaches this.
	var page = Date.now().toString(36) + Math.random().toString(36).slice(2);
	var base = location.origin + "/__lunade/";
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
		"lunadeLoaded", "lunadeResult"];

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
	// The main process names a page by this id when the host asks for it to be closed.
	var ipc = null;
	try { ipc = require("electron").ipcRenderer; ipc.send("lunade:page", page); } catch (e) {}
	// What the page is about to open (window.open's attributes: a card, a dashboard, a popup
	// alert) is Electron's to place, so it hears of it before the open reaches it.
	N.nextWindow = function (attributesJson) { if (ipc) { try { ipc.sendSync("lunade:next-window", String(attributesJson)); } catch (e) {} } return null; };
	window.addEventListener("load", function () { N.lunadeLoaded(); });

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
		if (!log || log.__lunadeLoud) { return; }
		try {
			Object.defineProperty(log, "currentLogLevel", { get: function () { return 99; }, set: function () {}, configurable: true });
			log.__lunadeLoud = true;
		} catch (e) {}
	}
	document.addEventListener("load", loudMojo, true);
	document.addEventListener("DOMContentLoaded", loudMojo);

	window.addEventListener("pagehide", function () {
		try { navigator.sendBeacon(base + "gone?w=" + page); } catch (e) {}
	});
})();
