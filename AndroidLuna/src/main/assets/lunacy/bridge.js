// Lunacy bridge: PalmSystem, PalmServiceBridge, palmGetResource. ES5 only: must run on Chromium 37.
(function () {
	var N = window.LunacyNative;
	if (!N) { return; }
	var pending = {};

	// Frames. On webOS every frame of a window had its own PalmServiceBridge binding, and
	// webOS's own cross-app UI is a frame: enyo.CrossAppUI loads the system's FilePicker in
	// an iframe. Here a reply comes back through evaluateJavascript, which only ever runs in
	// the main frame, so each same-origin frame registers itself with the main frame and
	// replies are handed on to whichever frame is waiting for that token. Tokens come from
	// one counter shared by the frames, so they can't be confused. A cross-origin frame
	// can't join in (and shouldn't: see the ratchet items in docs/architecture.md).
	var shared = (function () {
		try {
			var t = window.top;
			if (!t.__lunacyFrames) { t.__lunacyFrames = []; }
			t.__lunacyFrames.push(window);
			return t;
		} catch (e) { return null; }
	})();
	function nextToken() {
		var holder = shared || window;
		holder.__lunacyToken = (holder.__lunacyToken || 0) + 1;
		return holder.__lunacyToken;
	}
	function frames() {
		try { return (shared && shared.__lunacyFrames) || [window]; } catch (e) { return [window]; }
	}

	// One call at a time per bridge. A call with "subscribe": true keeps answering until
	// cancel() (or the page goes away); calling again ends the earlier call.
	function Bridge() { this.token = 0; }
	Bridge.prototype.call = function (url, params) {
		this.cancel();
		var t = nextToken(), sub = false;
		// Open calls: subscriptions, and db8 watches (Enyo's DbService sends "watch": true alone).
		try { var p = JSON.parse(params); sub = p.subscribe === true || p.watch === true; } catch (e) {}
		if (/\/watch$/.test(String(url))) { sub = true; }
		this.token = t;
		this.subscribed = sub;
		pending[t] = this;
		N.call(t, String(url), String(params));
	};
	Bridge.prototype.cancel = function () {
		if (this.token && pending[this.token]) {
			delete pending[this.token];
			if (this.subscribed) { N.cancel(this.token); }
		}
	};
	//* The frame this reply belongs to, if it is this one.
	Bridge.__replyLocal = function (t, json) {
		var b = pending[t];
		if (!b) { return false; }
		if (!b.subscribed) { delete pending[t]; }
		if (b.onservicecallback) { b.onservicecallback(json); }
		return true;
	};
	//* Native calls this in the main frame; the waiting frame may be a child of it.
	Bridge.__reply = function (t, json) {
		if (Bridge.__replyLocal(t, json)) { return; }
		var all = frames();
		for (var i = 0; i < all.length; i++) {
			try {
				var f = all[i];
				if (f !== window && f.PalmServiceBridge && f.PalmServiceBridge.__replyLocal(t, json)) { return; }
			} catch (e) {}
		}
	};
	window.PalmServiceBridge = Bridge;

	var launchParams = "";
	// Launch parameters arrive in the query string; webOS reports "" when there are none.
	var lp = location.search.match(/[?&]launchParams=([^&]*)/);
	if (lp) { try { launchParams = JSON.stringify(JSON.parse(decodeURIComponent(lp[1]))); } catch (e) {} }

	// The locale fields and the clock format follow the device's own settings, as webOS's did.
	var locale = { locale: "en_us", localeRegion: "us", phoneRegion: "us", timeFormat: "HH12" };
	try { if (N.localeInfo) { locale = JSON.parse(N.localeInfo()); } } catch (e) {}

	var palmSystem = {
		deviceInfo: N.deviceInfo(),
		launchParams: launchParams,
		// "<appid> <pid>", as a device reports it.
		identifier: location.hostname.replace(/\.media\.cryptofs\.apps$/, "") + " " + (N.processId ? N.processId() : 1000),
		version: "Webkit4/V8; device",  // as the TouchPad reports it: not the webOS version
		locale: locale.locale,
		localeRegion: locale.localeRegion,
		phoneRegion: locale.phoneRegion,
		timeFormat: locale.timeFormat,
		// The screen's orientation, kept current by the shell as LunaSysMgr did; Enyo reads it
		// when the page's resize event arrives. "up", "down", "left" or "right".
		screenOrientation: N.screenOrientation ? N.screenOrientation() : "up",
		// The shell sets this as cards gain and lose focus; a device always has it.
		isActivated: false,
		isMinimal: false,
		activityId: 1,
		stageReady: function () { N.stageReady(); },
		// Mojo's StageController.setWindowOrientation assigns to the property, so the two are
		// the same thing; see the windowOrientation property below for what webOS did with it.
		setWindowOrientation: function (o) { this.windowOrientation = o; },
		// What the app last asked for, which is where "free" belongs. Null until it asks, as
		// LunaSysMgr's m_specifiedWindowOrientation is.
		specifiedWindowOrientation: null,
		// The card grows over the status bar's 28 px and the bar slides away while the card is
		// maximized; the page hears about it through its own resize. A video player and most
		// games ask for it.
		enableFullScreenMode: function (on) { N.fullScreen(Boolean(on)); },
		// webOS's window properties. blockScreenTimeout (a video player holds the screen on
		// while it plays) and statusBarColor (the bar's fill while the card is maximized, taken
		// up at the next maximize, as a device does) are answered. fullScreen is the same as
		// enableFullScreenMode, which is how LunaSysMgr read it back.
		//
		// suppressBannerMessages, suppressGestures and rotationLockMaximized are stored by
		// LunaSysMgr and then read by nothing, in LunaCE and in HP's luna-sysmgr alike: a
		// TouchPad shows the banner, takes the swipe and turns the screen regardless (measured,
		// Docs/luna-deltas.md "D"). So they are accepted and do nothing here either. The rest
		// are hardware the tablet hasn't got, and are logged.
		setWindowProperties: function (p) {
			var props = p || {};
			var rest = [];
			for (var k in props) {
				if (!Object.prototype.hasOwnProperty.call(props, k)) { continue; }
				if (k === "blockScreenTimeout") { N.blockScreenTimeout(Boolean(props[k])); }
				else if (k === "statusBarColor") { N.statusBarColor(Number(props[k]) | 0); }
				else if (k === "fullScreen") { N.fullScreen(Boolean(props[k])); }
				else if (k === "suppressBannerMessages" || k === "suppressGestures" || k === "rotationLockMaximized") {}
				else { rest.push(k); }
			}
			if (rest.length) { N.log("PalmSystem.setWindowProperties " + rest.join(",") + " (not implemented)"); }
		},
		// addBannerMessage(message, launchParamsJson, icon, soundClass, soundFile, duration, doNotSuppress)
		// returns the banner's id at once, as webOS did.
		addBannerMessage: function (msg, params, icon) {
			return N.addBanner(String(msg), params ? String(params) : "", icon ? absolute(icon) : "");
		},
		removeBannerMessage: function (id) { N.removeBanner(Number(id)); },
		clearBannerMessages: function () { N.clearBanners(); },
		playSoundNotification: function () {},
		keepAlive: function () {},
		shutdown: function () {},
		receivePageUpDownInLandscape: function () {},
		show: function () {},
		hide: function () {},
		markFirstUseDone: function () {},
		setAlertSound: function () {},
		enableDockMode: function () {},
		// Enyo uses these to focus inputs. On webOS the host injected a real click; the spike
		// focuses the element under the point.
		useSimulatedMouseClicks: function () {},
		simulateMouseClick: function (x, y, down) {
			if (down) { return; }
			var el = document.elementFromPoint(x, y);
			if (el && el.focus) { el.focus(); }
		}
	};
	// On a device PalmSystem is a host object, and none of its members enumerate: a page that
	// walks it with for..in sees nothing (measured, docs/spike-1.md). Same here, so an app that
	// copies or inspects it behaves as it did on a TouchPad. Everything stays writable, because
	// the shell sets isActivated and launchParams from outside.
	function publish(obj) {
		var host = {};
		for (var k in obj) {
			if (Object.prototype.hasOwnProperty.call(obj, k)) {
				try {
					Object.defineProperty(host, k, { value: obj[k], writable: true, configurable: true, enumerable: false });
				} catch (e) { host[k] = obj[k]; }
			}
		}
		return host;
	}
	function define(obj, name, value) {
		try {
			Object.defineProperty(obj, name, { value: value, writable: true, configurable: true, enumerable: false });
		} catch (e) { obj[name] = value; }
	}
	window.PalmSystem = publish(palmSystem);

	// PalmSystem.windowOrientation reads and writes two different things, and webOS meant it to.
	//
	// Reading it gives the *window's* own orientation - the screen's, turned by where the home
	// button is (LunaSysMgr's mapScreenOrientationToWindowOrientation; the shell does the
	// turning, from the device profile). Writing it does not set that: it records what the app
	// is *asking* for and applies it as a policy, "free" meaning "let the card rotate with the
	// screen". LunaSysMgr keeps the request in m_specifiedWindowOrientation, which is a
	// separate property, and the getter never returns it. Both getters answer one of "up",
	// "down", "left" and "right", and never "free".
	//
	// Lunacy used to store the write and hand it back on the read, which meant every app that
	// asks to rotate freely - Mojo does it for the app - then read "free" and branched on it.
	// A Mojo app handles rotation by comparing the value with the four names, so it did
	// nothing at all: drPodder's playback slider stayed the width of its own text, huddled in
	// the corner, because the branch that sizes it never ran.
	(function () {
		var PS = window.PalmSystem;
		try {
			Object.defineProperty(PS, "windowOrientation", {
				configurable: true, enumerable: false,
				get: function () {
					try { return N.windowOrientation ? N.windowOrientation() : PS.screenOrientation; }
					catch (e) { return PS.screenOrientation; }
				},
				// "free" (the default, and what Mojo asks for) lets the card follow the screen,
				// which is what Lunacy's cards already do; a fixed orientation is recorded and
				// logged, because Lunacy has no rotation lock yet.
				set: function (o) {
					PS.specifiedWindowOrientation = String(o);
					if (String(o).toLowerCase() !== "free") {
						N.log("PalmSystem.windowOrientation = " + o + " (rotation lock not implemented)");
					}
				}
			});
		} catch (e) {}
	})();

	// Relaunch, as LunaSysMgr did it: the root window's launchParams become the new
	// parameters, then Mojo.relaunch() runs. True means the app handled it (Enyo's app menu,
	// for one: tapping the title relaunches with {"palm-command":"open-app-menu"}).
	window.__lunacyRelaunch = function (params) {
		PalmSystem.launchParams = params;
		return Boolean(window.Mojo && Mojo.relaunch && Mojo.relaunch());
	};

	// The virtual keyboard. Automatic mode (the default) shows it while a text field has focus;
	// manual mode leaves it to keyboardShow and keyboardHide (Enyo's enyo.keyboard).
	var manualKeyboard = false;
	function editable(el) {
		if (!el || el.nodeType !== 1 || el.readOnly || el.disabled) { return false; }
		if (el.tagName === "TEXTAREA" || el.isContentEditable) { return true; }
		return el.tagName === "INPUT" && !/^(button|checkbox|radio|submit|reset|file|image|range|color|hidden)$/i.test(el.type || "");
	}
	document.addEventListener("focusin", function (e) {
		if (!manualKeyboard && editable(e.target)) { N.keyboard(true); }
	}, true);
	document.addEventListener("focusout", function () {
		setTimeout(function () { if (!manualKeyboard && !editable(document.activeElement)) { N.keyboard(false); } }, 0);
	}, true);
	// A tap on a field that already has focus brings the keyboard back (after Android's back hid it).
	window.__lunacyFieldTapped = function (el) {
		if (!manualKeyboard && editable(el)) { N.keyboard(true); }
	};

	// Synchronous file read. With a "json" hint webOS returned the parsed object.
	function absolute(url) {
		var a = document.createElement("a");
		a.href = url;
		return a.href;
	}

	// Window types. webOS read "attributes={...}" from window.open's features (window: "card",
	// "dashboard", "popupalert"). Android's onCreateWindow never sees the features, so hand
	// them to native just before the window is created.
	var nativeOpen = window.open;
	window.open = function (url, name, features) {
		var m = /attributes=(\{.*\})/.exec(features || "");
		var attrs = {};
		if (m) { try { attrs = JSON.parse(m[1]); } catch (e) {} }
		if (attrs.icon) { attrs.icon = absolute(attrs.icon); }
		var h = /(?:^|[, ])height=(\d+)/.exec(features || "");
		if (h) { attrs.height = Number(h[1]); }
		N.nextWindow(JSON.stringify(attrs));
		return nativeOpen.call(window, url, name, features);
	};

	// A missing or disallowed file gives null, and a path that isn't rooted is refused: on a
	// TouchPad, palmGetResource("appinfo.json") returns null while the same file named from
	// the root reads fine (Workbench/probe, docs/spike-1.md).
	//
	// "Rooted" here means a full URL or a path starting with "/". On a device that path is
	// file:///usr/palm/..., and on Lunacy's origins the same file is /usr/palm/... - Enyo
	// builds one or the other depending on where the page is served from, and both name the
	// file the device would have read. Refusing the second form broke enyo.g11n, which reads
	// its date and number formats that way, so every app lost date formatting.
	window.palmGetResource = function (path, hint) {
		var p = String(path);
		if (!/^[a-z][a-z0-9+.-]*:/i.test(p) && p.charAt(0) !== "/") {
			N.log("palmGetResource needs a rooted path: " + p);
			return null;
		}
		// Ask the card host for the file as it is on disk. On a device this call read the
		// file; no browser was involved, so none of the transforms that belong to loading a
		// *page* apply. Mojo reads every widget template and every scene this way, and a
		// template that arrives with Lunacy's boot scripts in front of it is not the
		// template the framework wrote: Mojo's menu widget looks up an element inside the
		// rendered node, finds the injected <link> instead and throws.
		var x = new XMLHttpRequest();
		x.open("GET", p + (p.indexOf("?") < 0 ? "?" : "&") + "__lunacy_res=1", false);
		try { x.send(null); } catch (e) { N.log("palmGetResource failed " + path); return null; }
		if (x.status !== 200) { N.log("palmGetResource missing " + path); return null; }
		if (hint && /json/.test(hint)) {
			try { return JSON.parse(x.responseText); } catch (e) { N.log("palmGetResource bad json " + path); return null; }
		}
		return x.responseText;
	};
	// Note: no PalmSystem.getResource and no PalmSystem.getIdentifier. Both are undefined on a
	// TouchPad (docs/spike-1.md), and an app that feature-detects one should get the same
	// answer here.
	// enyo.windows.activate: bring this window's card forward.
	define(PalmSystem, "activate", function () { N.activate(); });
	define(PalmSystem, "setManualKeyboardEnabled", function (manual) { manualKeyboard = Boolean(manual); });
	define(PalmSystem, "keyboardShow", function () { N.keyboard(true); });
	define(PalmSystem, "keyboardHide", function () { N.keyboard(false); });
	// false: the keyboard covers the card, which gets Mojo.positiveSpaceChanged instead of a resize.
	define(PalmSystem, "allowResizeOnPositiveSpaceChange", function (resize) { N.keyboardResizes(Boolean(resize)); });

	// webOS's linkifier: the text comes back with web addresses, e-mail addresses and
	// telephone numbers wrapped in anchors, and everything else untouched. Mojo hands every
	// piece of user text through it (Mojo.Format.runTextIndexer), and an app then works on
	// what comes back - SimpleChat reads .length off it - so a no-op returning undefined
	// stops the app dead.
	//
	// Measured on the reference TouchPad (Workbench/probe 0.1.2):
	//   "call 555-1234"            -> 'call <a href="tel:555-1234">555-1234</a>'
	//   "www.example.com"          -> '<a href="http://www.example.com">www.example.com</a>'
	//   "a@b.com"                  -> '<a href="mailto:a@b.com">a@b.com</a>'
	//   "(555) 123-4567"           -> the href keeps the number exactly as written
	//   "2026-09-21", "100-200", "1234", a newline, "<b>bold</b> & 'quoted'" -> unchanged
	// The device's is a single naive pass over the whole string: text already inside an
	// anchor's href gets linkified again there too, and & is not escaped. This does the same
	// in one pass, so it can't re-process what it just inserted either. The telephone shapes
	// are the four the device was seen to match; webOS's own indexer may know more.
	var INDEXER = new RegExp([
		"(?:[a-z][a-z0-9+.\\-]*://|www\\.)[^\\s<>\"']+",
		"[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}",
		"\\(\\d{3}\\)[\\s.\\-]?\\d{3}[\\s.\\-]?\\d{4}",
		"\\b(?:\\d{3}[\\s.\\-]\\d{3}[\\s.\\-]\\d{4}|\\d{10}|\\d{3}[\\s.\\-]\\d{4})\\b"
	].join("|"), "gi");
	define(PalmSystem, "runTextIndexer", function (text) {
		if (typeof text !== "string" || !text) { return text === undefined ? "" : String(text); }
		return text.replace(INDEXER, function (m) {
			var href;
			if (/^[a-z][a-z0-9+.\-]*:\/\//i.test(m)) { href = m; }
			else if (/^www\./i.test(m)) { href = "http://" + m; }
			else if (m.indexOf("@") >= 0) { href = "mailto:" + m; }
			else { href = "tel:" + m; }
			return '<a href="' + href + '">' + m + '</a>';
		});
	});

	// The rest of the PalmSystem surface measured on a TouchPad (docs/spike-1.md). Calls with no
	// Lunacy equivalent yet are logged no-ops, so apps don't throw; the log shows what's used.
	["deactivate", "addNewContentIndicator", "removeNewContentIndicator", "cancelCrossAppScene",
	 "cancelSceneTransition", "crossAppSceneActive", "decrypt", "editorFocused", "encrypt", "hideSpellingWidget",
	 "paste", "prepareSceneTransition", "printFrame", "runAnimationLoop", "runCrossAppTransition",
	 "runSceneTransition", "stagePreparing"].forEach(function (name) {
		if (!PalmSystem[name]) {
			define(PalmSystem, name, function () { N.log("PalmSystem." + name + " (not implemented)"); });
		}
	});
})();
