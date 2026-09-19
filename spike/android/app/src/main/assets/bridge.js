// Lunacy spike bridge. ES5 only: must run on Chromium 37.
(function () {
	var N = window.LunacyNative;
	if (!N) { return; }
	var pending = {}, nextToken = 1;

	function Bridge() { this.token = 0; }
	Bridge.prototype.call = function (url, params) {
		var t = nextToken++;
		this.token = t;
		pending[t] = this;
		N.call(t, String(url), String(params));
	};
	Bridge.prototype.cancel = function () { delete pending[this.token]; };
	Bridge.__reply = function (t, json) {
		var b = pending[t];
		if (!b) { return; }
		delete pending[t];
		if (b.onservicecallback) { b.onservicecallback(json); }
	};
	window.PalmServiceBridge = Bridge;

	var launchParams = "{}";
	try { launchParams = JSON.stringify(JSON.parse(decodeURIComponent((location.search.match(/[?&]launchParams=([^&]*)/) || [0, "%7B%7D"])[1]))); } catch (e) {}

	window.PalmSystem = {
		deviceInfo: N.deviceInfo(),
		launchParams: launchParams,
		identifier: location.hostname.replace(/\.media\.cryptofs\.apps$/, "") + " 0",
		version: "3.0.5",
		locale: "en_us",
		localeRegion: "us",
		phoneRegion: "us",
		timeFormat: "HH12",
		screenOrientation: "up",
		windowOrientation: "up",
		isMinimal: false,
		activityId: 1,
		stageReady: function () { N.log("stageReady"); },
		setWindowOrientation: function (o) { this.windowOrientation = o; },
		enableFullScreenMode: function () {},
		setWindowProperties: function () {},
		addBannerMessage: function () { N.log("banner " + JSON.stringify([].slice.call(arguments))); return 1; },
		removeBannerMessage: function () {},
		clearBannerMessages: function () {},
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
	// Synchronous file read. With a "json" hint webOS returned the parsed object.
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
})();
