// Lunacy bridge: PalmSystem, PalmServiceBridge, palmGetResource. ES5 only: must run on Chromium 37.
(function () {
	var N = window.LunacyNative;
	if (!N) { return; }
	var pending = {}, nextToken = 1;

	// One call at a time per bridge. A call with "subscribe": true keeps answering until
	// cancel() (or the page goes away); calling again ends the earlier call.
	function Bridge() { this.token = 0; }
	Bridge.prototype.call = function (url, params) {
		this.cancel();
		var t = nextToken++, sub = false;
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
	Bridge.__reply = function (t, json) {
		var b = pending[t];
		if (!b) { return; }
		if (!b.subscribed) { delete pending[t]; }
		if (b.onservicecallback) { b.onservicecallback(json); }
	};
	window.PalmServiceBridge = Bridge;

	var launchParams = "";
	// Launch parameters arrive in the query string; webOS reports "" when there are none.
	var lp = location.search.match(/[?&]launchParams=([^&]*)/);
	if (lp) { try { launchParams = JSON.stringify(JSON.parse(decodeURIComponent(lp[1]))); } catch (e) {} }

	window.PalmSystem = {
		deviceInfo: N.deviceInfo(),
		launchParams: launchParams,
		identifier: location.hostname.replace(/\.media\.cryptofs\.apps$/, "") + " 1000",
		version: "Webkit4/V8; device",  // as the TouchPad reports it: not the webOS version
		locale: "en_us",
		localeRegion: "us",
		phoneRegion: "us",
		timeFormat: "HH12",
		screenOrientation: "up",
		windowOrientation: "up",
		isMinimal: false,
		activityId: 1,
		stageReady: function () { N.stageReady(); },
		setWindowOrientation: function (o) { this.windowOrientation = o; },
		enableFullScreenMode: function () {},
		setWindowProperties: function () {},
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
		getIdentifier: function () { return this.identifier; },
		// Enyo uses these to focus inputs. On webOS the host injected a real click; the spike
		// focuses the element under the point.
		useSimulatedMouseClicks: function () {},
		simulateMouseClick: function (x, y, down) {
			if (down) { return; }
			var el = document.elementFromPoint(x, y);
			if (el && el.focus) { el.focus(); }
		}
	};
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

	window.palmGetResource = function (path, hint) {
		var x = new XMLHttpRequest();
		x.open("GET", path, false);
		x.send(null);
		if (x.status !== 200) { N.log("palmGetResource missing " + path); return undefined; }
		if (hint && /json/.test(hint)) {
			try { return JSON.parse(x.responseText); } catch (e) { N.log("palmGetResource bad json " + path); return undefined; }
		}
		return x.responseText;
	};
	PalmSystem.getResource = window.palmGetResource;
	// enyo.windows.activate: bring this window's card forward.
	PalmSystem.activate = function () { N.activate(); };
	PalmSystem.setManualKeyboardEnabled = function (manual) { manualKeyboard = Boolean(manual); };
	PalmSystem.keyboardShow = function () { N.keyboard(true); };
	PalmSystem.keyboardHide = function () { N.keyboard(false); };
	// false: the keyboard covers the card, which gets Mojo.positiveSpaceChanged instead of a resize.
	PalmSystem.allowResizeOnPositiveSpaceChange = function (resize) { N.keyboardResizes(Boolean(resize)); };

	// The rest of the PalmSystem surface measured on a TouchPad (docs/spike-1.md). Calls with no
	// Lunacy equivalent yet are logged no-ops, so apps don't throw; the log shows what's used.
	["deactivate", "addNewContentIndicator", "removeNewContentIndicator", "cancelCrossAppScene",
	 "cancelSceneTransition", "crossAppSceneActive", "decrypt", "editorFocused", "encrypt", "hideSpellingWidget",
	 "paste", "prepareSceneTransition", "printFrame", "runAnimationLoop", "runCrossAppTransition",
	 "runSceneTransition", "runTextIndexer", "stagePreparing"].forEach(function (name) {
		if (!PalmSystem[name]) {
			PalmSystem[name] = function () { N.log("PalmSystem." + name + " (not implemented)"); };
		}
	});
})();
