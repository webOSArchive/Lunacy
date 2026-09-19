// Lunacy spike compat layer. ES5 only: must run on Chromium 37.
// webOS WebKit delivered touches to web content as mouse events (mousedown/mousemove/mouseup
// + click). Enyo 1 and Mojo only listen for mouse events. Chromium only synthesizes them for
// taps, never drags, so recreate the webOS input model for every page.
(function () {
	var THRESHOLD = 10, st = null;
	// Flicks, as LunaSysMgr sent them: its FlickGestureRecognizer keeps the last 3 touch
	// samples; above 500 px/s (Manhattan) it reports velocity = displacement / (elapsed ·
	// samples), and WindowedWebApp passes it to the page as Mojo.handleGesture('flick', …).
	// Enyo's and Mojo's scrollers take their inertia from that call alone.
	var samples = [], MAX_SAMPLES = 3, FLICK_SPEED = 500;
	function sample(t) {
		samples.push({ x: t.clientX, y: t.clientY, t: Date.now() });
		if (samples.length > MAX_SAMPLES) { samples.shift(); }
	}
	function flick(t) {
		if (samples.length < 2) { return; }
		var a = samples[0], b = samples[samples.length - 1];
		var dx = b.x - a.x, dy = b.y - a.y, elapsed = (b.t - a.t) / 1000;
		if (elapsed <= 0 || (Math.abs(dx) + Math.abs(dy)) / elapsed <= FLICK_SPEED) { return; }
		elapsed *= samples.length;
		var M = window.Mojo;
		if (M && M.handleGesture) {
			M.handleGesture("flick", { x: Math.round(t.clientX), y: Math.round(t.clientY), timeStamp: b.t,
				xVel: Math.round(dx / elapsed), yVel: Math.round(dy / elapsed) });
		}
	}
	function fire(type, t, target) {
		var e = document.createEvent("MouseEvents");
		e.initMouseEvent(type, true, true, window, 1, t.screenX, t.screenY, t.clientX, t.clientY,
			false, false, false, false, 0, null);
		target.dispatchEvent(e);
	}
	function under(t) { return document.elementFromPoint(t.clientX, t.clientY) || (st && st.target) || document.body; }
	function focusable(el) {
		for (; el && el.nodeType === 1; el = el.parentNode) {
			if (/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) || el.isContentEditable) { return el; }
		}
		return null;
	}
	var opts = { capture: true, passive: false };  // Chromium 37 reads this as capture=true
	document.addEventListener("touchstart", function (ev) {
		if (ev.touches.length > 1) { st = null; return; }  // leave multi-touch (pinch) alone
		var t = ev.changedTouches[0];
		st = { x: t.clientX, y: t.clientY, target: t.target, moved: false };
		samples = []; sample(t);
		fire("mouseover", t, t.target);
		fire("mousedown", t, t.target);
		ev.preventDefault();
	}, opts);
	document.addEventListener("touchmove", function (ev) {
		if (!st) { return; }
		var t = ev.changedTouches[0];
		if (Math.abs(t.clientX - st.x) > THRESHOLD || Math.abs(t.clientY - st.y) > THRESHOLD) { st.moved = true; }
		sample(t);
		fire("mousemove", t, under(t));
		ev.preventDefault();
	}, opts);
	function end(ev) {
		if (!st) { return; }
		var t = ev.changedTouches[0], target = under(t);
		fire("mouseup", t, target);
		if (!st.moved && ev.type === "touchend") {
			var f = focusable(st.target);
			if (f) { f.focus(); if (window.__lunacyFieldTapped) { window.__lunacyFieldTapped(f); } } else if (document.activeElement && document.activeElement !== document.body && focusable(document.activeElement)) { document.activeElement.blur(); }
			fire("click", t, st.target);
		}
		fire("mouseout", t, target);
		if (st.moved && ev.type === "touchend") { flick(t); }
		st = null;
		ev.preventDefault();
	}
	document.addEventListener("touchend", end, opts);
	document.addEventListener("touchcancel", end, opts);
})();

// Uncaught script errors reach only the console. The reference TouchPad (WebKit 534.6)
// has window.onerror but never calls it, and dispatches no "error" event to window listeners
// (spike/probe 0.0.5). Some apps install onerror overlays that were never seen on a device.
// Registered before any app script, this listener stops the event before the page's own
// listeners and its onerror attribute; errors on elements (img, script loads) aren't touched.
(function () {
	window.addEventListener("error", function (e) {
		if (e.target === window) { e.stopImmediatePropagation(); }
	}, true);
})();

// No service workers: the TouchPad's WebKit had none, and apps written to run on the web too
// register one when navigator.serviceWorker exists. It can't work on Lunacy's served origins.
(function () {
	try { if (window.Navigator && "serviceWorker" in Navigator.prototype) { delete Navigator.prototype.serviceWorker; } } catch (e) {}
})();

// Enyo 1 cancels animation frames with webkitCancelRequestAnimationFrame, 2011 WebKit's name.
// Chromium kept webkitRequestAnimationFrame but dropped that name, so Enyo falls back to
// clearTimeout(frameId). Frame and timer ids are separate counters, so that cancels an
// unrelated timer with the same number and leaves the frame running: in Apollo, a Pane's
// fade froze half-way with its click-catching scrim up. The old name gets the current API.
(function () {
	var cancel = window.cancelAnimationFrame || window.webkitCancelAnimationFrame;
	if (!window.webkitCancelRequestAnimationFrame && cancel) { window.webkitCancelRequestAnimationFrame = cancel; }
})();

// file:///media/internal/... (webOS's user storage, where JS services leave files for apps)
// is served on every app origin at /media/internal/...; pages ran from file:// on webOS and
// used those URLs directly.
// Media elements get the loopback MediaServer instead: Chromium hands HLS to Android's
// MediaPlayer, which can't reach app origins.
window.__lunacyFileUrl = function (u, media) {
	if (typeof u !== "string" || !/^file:\/\/\/media\/internal\//i.test(u)) { return u; }
	var N = window.LunacyNative;
	return media && N && N.mediaBase ? N.mediaBase() + u.slice(7) : u.slice(7);
};

// The same for URLs given to new Audio(url) and to setAttribute("src") on media elements, as
// SoundManager2 does.
(function () {
	var map = function (u) { return window.__lunacyFileUrl(u, true); };
	var M = window.HTMLMediaElement && HTMLMediaElement.prototype, S = window.HTMLSourceElement && HTMLSourceElement.prototype;
	[M, S].forEach(function (P) {
		if (!P) { return; }
		var set = P.setAttribute;
		P.setAttribute = function (name, value) {
			return set.call(this, name, String(name).toLowerCase() === "src" ? map(value) : value);
		};
	});
	var A = window.Audio;
	if (A) {
		var Audio = function (src) { return arguments.length && src !== undefined ? new A(map(src)) : new A(); };
		Audio.prototype = A.prototype;
		window.Audio = Audio;
	}
})();

// An empty src leaves a media element empty and silent. On the TouchPad, audio.src = "" fired
// no event and left networkState at 0 (spike/probe 0.0.8); Chromium starts a load and fires
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
			d.set.call(this, window.__lunacyFileUrl ? __lunacyFileUrl(v, true) : v);
		}
	});
})();
