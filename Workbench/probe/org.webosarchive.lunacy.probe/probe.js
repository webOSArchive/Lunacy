// Records the webOS app contract as a TouchPad sees it. Each result goes to the page and to
// console.log as "LUNACYPROBE <key> <json>".
(function () {
	var out = document.getElementById("out");
	function rec(k, v) {
		var s;
		try { s = JSON.stringify(v); } catch (e) { s = String(v); }
		out.appendChild(document.createTextNode(k + " = " + s + "\n"));
		console.log("LUNACYPROBE " + k + " " + s);
	}
	function safe(k, f) { try { rec(k, f()); } catch (e) { rec(k, "THROWS " + e); } }

	safe("location.href", function () { return location.href; });
	safe("location.protocol", function () { return location.protocol; });
	safe("location.hostname", function () { return location.hostname; });
	safe("location.host", function () { return location.host; });
	safe("location.origin", function () { return location.origin; });
	safe("document.baseURI", function () { return document.baseURI; });
	safe("document.domain", function () { return document.domain; });
	safe("userAgent", function () { return navigator.userAgent; });
	safe("screen", function () { return [screen.width, screen.height, window.innerWidth, window.innerHeight, window.devicePixelRatio]; });
	safe("ontouchstart", function () { return "ontouchstart" in window; });
	safe("openDatabase", function () { return typeof window.openDatabase; });
	safe("localStorage", function () { localStorage.setItem("lunacyprobe", "1"); return localStorage.getItem("lunacyprobe"); });

	var ps = window.PalmSystem;
	safe("PalmSystem.type", function () { return typeof ps; });
	if (ps) {
		var props = {};
		for (var k in ps) {
			try { var v = ps[k]; props[k] = typeof v === "function" ? "function" : v; } catch (e) { props[k] = "THROWS"; }
		}
		rec("PalmSystem.members", props);
		var known = ["activate", "activityId", "addBannerMessage", "addNewContentIndicator", "cancelCrossAppScene", "cancelSceneTransition", "clearBannerMessages", "crossAppSceneActive", "deactivate", "decrypt", "deviceInfo", "editorFocused", "enableFullScreenMode", "encrypt", "hideSpellingWidget", "identifier", "isActivated", "isMinimal", "launchParams", "locale", "localeRegion", "paste", "phoneRegion", "playSoundNotification", "prepareSceneTransition", "receivePageUpDownInLandscape", "removeBannerMessage", "removeNewContentIndicator", "runAnimationLoop", "runCrossAppTransition", "runSceneTransition", "runTextIndexer", "screenOrientation", "setAlertSound", "setManualKeyboardEnabled", "setWindowOrientation", "setWindowProperties", "simulated", "simulateMouseClick", "stagePreparing", "stageReady", "timeFormat", "useSimulatedMouseClicks", "version", "windowIdentifier", "windowOrientation", "keyboardShow", "keyboardHide", "allowResizeOnPositiveSpaceChange", "printFrame", "TZ", "getResource", "shutdown", "hide", "show", "keepAlive", "markFirstUseDone", "enableDockMode", "getIdentifier"], types = {};
		known.forEach(function (n) {
			try { var v = ps[n]; types[n] = typeof v === "function" ? "fn" : (typeof v === "string" || typeof v === "number" || typeof v === "boolean" ? v : typeof v); } catch (e) { types[n] = "THROWS"; }
		});
		known.forEach(function (n) { if (n !== "deviceInfo") { rec("ps." + n, types[n]); } });
		safe("PalmSystem.identifier", function () { return ps.identifier; });
		safe("PalmSystem.launchParams", function () { return ps.launchParams; });
		safe("PalmSystem.deviceInfo", function () { return ps.deviceInfo; });
		safe("PalmSystem.getIdentifier", function () { return ps.getIdentifier && ps.getIdentifier(); });
	}
	safe("PalmServiceBridge.type", function () { return typeof window.PalmServiceBridge; });
	safe("palmGetResource.type", function () { return typeof window.palmGetResource; });
	safe("palmGetResource(appinfo, const json)", function () { var r = palmGetResource("appinfo.json", "const json"); return [typeof r, r && r.id]; });
	safe("palmGetResource(appinfo)", function () { var r = palmGetResource("appinfo.json"); return [typeof r, String(r).slice(0, 40)]; });
	safe("palmGetResource(missing, const json)", function () { var r = palmGetResource("nope.json", "const json"); return [typeof r, r]; });
	safe("palmGetResource(abs framework_config)", function () { var r = palmGetResource("/usr/palm/frameworks/enyo/1.0/framework/framework_config.json", "const json"); return [typeof r]; });
	var root = enyo.fetchAppRootPath();
	safe("palmGetResource(abs appinfo, const json)", function () { var r = palmGetResource(root + "appinfo.json", "const json"); return [typeof r, r === null, r && r.id]; });
	safe("palmGetResource(abs appinfo, no hint)", function () { var r = palmGetResource(root + "appinfo.json"); return [typeof r, r === null, String(r).slice(0, 30)]; });
	safe("palmGetResource(abs missing, const json)", function () { var r = palmGetResource(root + "nope.json", "const json"); return [typeof r, r === null]; });
	safe("palmGetResource(fw framework_config)", function () { var r = palmGetResource("file:///usr/palm/frameworks/enyo/1.0/framework/framework_config.json", "const json"); return [typeof r, r === null]; });
	safe("palmGetResource(system file)", function () { var r = palmGetResource("file:///usr/palm/command-resource-handlers.json", "const json"); return [typeof r, r === null]; });
	safe("enyo.fetchAppRootPath", function () { return enyo.fetchAppRootPath(); });
	safe("enyo.fetchAppId", function () { return enyo.fetchAppId(); });
	safe("enyo.fetchAppInfo.id", function () { return enyo.fetchAppInfo().id; });
	safe("enyo.args", function () { return enyo.windowParams; });

	// System files apps read.
	["/usr/palm/command-resource-handlers.json", "/usr/palm/frameworks/tellurium/tellurium_config.json",
	 "/usr/palm/applications/com.palm.app.browser/appinfo.json", "/etc/palm-build-info"].forEach(function (p) {
		safe("sync-xhr " + p, function () { var x = new XMLHttpRequest(); x.open("GET", p, false); x.send(null); return [x.status, x.responseText.length]; });
	});

	// Cross-origin: servers with and without CORS headers.
	["http://example.com/", "https://example.com/", "http://appcatalog.webosarchive.org/WebService/getConfig.php", "file:///usr/palm/command-resource-handlers.json"].forEach(function (u) {
		try {
			var x = new XMLHttpRequest();
			x.open("GET", u, true);
			x.onreadystatechange = function () { if (x.readyState === 4) { rec("xhr " + u, [x.status, (x.responseText || "").length]); } };
			x.send(null);
		} catch (e) { rec("xhr " + u, "THROWS " + e); }
	});

	// Uncaught errors: does window.onerror exist, and which errors reach it? Enyo's WebView
	// parses the failed command-resource-handlers.json read inside the XHR handler.
	safe("onerror.inWindow", function () { return ["onerror" in window, typeof window.onerror]; });
	safe("JSON.parse('')", function () { return JSON.parse(""); });
	window.addEventListener("error", function (e) { rec("errorEvent.called", [String(e && e.message), e && e.target === window]); }, false);
	window.onerror = function () { rec("onerror.called", [arguments.length, String(arguments[0]), String(arguments[2])]); return true; };
	safe("onerror.xhrHandlerThrow", function () {
		var x = new XMLHttpRequest();
		x.open("GET", "/usr/palm/command-resource-handlers.json", true);
		x.onreadystatechange = function () { if (x.readyState === 4) { rec("onerror.xhrHandler.state4", x.status); JSON.parse(x.responseText); } };
		try { x.send(null); } catch (e) { return "send THROWS " + e; }
		return "sent";
	});
	setTimeout(function () { rec("onerror.timeoutThrow", "throwing"); throw new Error("probe timeout throw"); }, 500);

	// Media: what an Audio object does with an empty src, as enyo.Sound sets it on creation.
	(function () {
		var a = new Audio(), ev = [];
		["error", "emptied", "loadstart", "abort", "stalled", "suspend"].forEach(function (t) { a.addEventListener(t, function () { ev.push(t + (a.error ? ":" + a.error.code : "")); }, false); });
		a.src = "";
		var b = new Audio(), evb = [];
		["error", "loadstart"].forEach(function (t) { b.addEventListener(t, function () { evb.push(t + (b.error ? ":" + b.error.code : "")); }, false); });
		b.setAttribute("preload", "none");
		b.src = "";
		setTimeout(function () {
			rec("media.emptySrc", { events: ev, src: a.src, attr: a.getAttribute("src"), net: a.networkState, ready: a.readyState, error: a.error && a.error.code });
			rec("media.emptySrcPreloadNone", { events: evb, net: b.networkState, error: b.error && b.error.code });
		}, 1500);
	})();

	// Bus round trip.
	safe("bus", function () {
		var b = new PalmServiceBridge();
		b.onservicecallback = function (r) { rec("bus reply systemTime", r); };
		b.call("palm://com.palm.systemservice/time/getSystemTime", "{}");
		var c = new PalmServiceBridge();
		c.onservicecallback = function (r) { rec("bus reply unknown", r); };
		c.call("palm://com.lunacy.nosuchservice/foo", "{}");
		window._keep = [b, c];
		return "sent";
	});

	// WebSQL, which Mojo apps keep their data in. Two things about it differ between hosts
	// and both decide whether an app runs: how many arguments openDatabase insists on, and
	// what an SQLError's message says. drPodder creates its tables on the strength of
	// error.message === "no such table: feed".
	safe("openDatabase arity", function () { return window.openDatabase.length; });
	safe("openDatabase(name, version)", function () {
		var db = openDatabase("lunacyprobe2", "1.0");
		return db ? "opened, version " + db.version : "null";
	});
	safe("sql error", function () {
		var db = openDatabase("lunacyprobe4", "1.0", "Lunacy probe", 65536);
		db.transaction(function (t) {
			t.executeSql("SELECT * FROM nosuchtable", [], function () { rec("sql error", "unexpectedly succeeded"); },
				function (t2, e) { rec("sql error", { code: e.code, message: e.message }); });
		});
		return "sent";
	});

	// PalmSystem.runTextIndexer: webOS's linkifier. Mojo hands it every chat message, and
	// an app then reads .length off what comes back, so what it returns matters.
	safe("runTextIndexer plain", function () {
		return ps && ps.runTextIndexer ? ps.runTextIndexer("hello there") : "(absent)";
	});
	safe("runTextIndexer rich", function () {
		return ps && ps.runTextIndexer ?
			ps.runTextIndexer("call 555-1234 or see http://example.com or mail a@b.com") : "(absent)";
	});
	safe("runTextIndexer html", function () {
		return ps && ps.runTextIndexer ? ps.runTextIndexer("a <b>bold</b> & 'quoted'") : "(absent)";
	});
	safe("runTextIndexer empty", function () {
		return ps && ps.runTextIndexer ? ps.runTextIndexer("") : "(absent)";
	});

	[
		"2026-09-21 was a Monday",
		"see www.example.com and https://a.b/c?d=1&e=2",
		"(555) 123-4567 and 5551234567 and 1234",
		"line one\nline two",
		"already <a href=\"http://x\">linked</a> and http://y",
		"100-200 range"
	].forEach(function (t, i) {
		safe("runTextIndexer case " + i + " in", function () { return t; });
		safe("runTextIndexer case " + i + " out", function () {
			return ps && ps.runTextIndexer ? ps.runTextIndexer(t) : "(absent)";
		});
	});

	// Input model: which events does a touch produce? Drag on the blue square.
	var seq = [], pad = document.getElementById("pad");
	["touchstart", "touchmove", "touchend", "mousedown", "mousemove", "mouseup", "click", "mouseover", "mouseout"].forEach(function (t) {
		pad.addEventListener(t, function (e) {
			if (seq.length && seq[seq.length - 1] === t) { return; }
			seq.push(t);
			if (t === "click" || t === "mouseup") { rec("input", seq.slice()); }
		}, true);
	});
	window.addEventListener("resize", function () { rec("resize", [window.innerWidth, window.innerHeight]); });
	document.addEventListener("keydown", function (e) { rec("keydown", e.keyCode); });
	document.addEventListener("keyup", function (e) { rec("keyup", e.keyCode); });
	window.Mojo = window.Mojo || {};
	Mojo.stageActivated = function () { rec("event", "stageActivated"); };
	Mojo.stageDeactivated = function () { rec("event", "stageDeactivated"); };
	if (ps && ps.stageReady) { ps.stageReady(); }
	rec("done", true);
})();
