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
	/**
	 * What a tap that didn't land on a field itself means for the keyboard, decided once the
	 * tap has been handled rather than during it.
	 *
	 * A framework focuses a field from the tap *it* is given, and this code runs first: Mojo's
	 * text field listens for a tap on its widget and calls focus(), so blurring here took the
	 * focus straight back off it and SimpleChat's compose box could not be typed into.
	 *
	 * So: if something took focus, the keyboard follows it (the bridge's focusin listener
	 * would normally do that, but Mojo focuses through a reference to the field's own focus
	 * method taken while the widget was built, and no focusin arrives from it). If nothing
	 * did, a tap away from the focused field puts the keyboard down, as a device did - and a
	 * tap *inside* that field's own widget is not a tap away from it. Mojo draws a field's
	 * hint text as a sibling of the input, so tapping the hint of an already-focused field
	 * has to raise the keyboard, not dismiss it.
	 */
	function afterTap(target, before) {
		setTimeout(function () {
			var now = document.activeElement;
			var tell = window.__lunacyFieldTapped;
			if (now !== before) {
				if (focusable(now) && tell) { tell(now); }
				if (window.__lunacyKeepFieldVisible) { window.__lunacyKeepFieldVisible(); }
				return;
			}
			if (!now || now === document.body || !focusable(now)) { return; }
			var widget = now.parentNode || now;
			if (widget.contains && widget.contains(target)) {
				if (tell) { tell(now); }
				return;
			}
			now.blur();
		}, 0);
	}

	function end(ev) {
		if (!st) { return; }
		var t = ev.changedTouches[0], target = under(t);
		// Read before the mouse events go out: that is when a framework may focus a field.
		var before = document.activeElement;
		fire("mouseup", t, target);
		if (!st.moved && ev.type === "touchend") {
			var f = focusable(st.target);
			if (f) { f.focus(); if (window.__lunacyFieldTapped) { window.__lunacyFieldTapped(f); } }
			fire("click", t, st.target);
			// Tapping away from a field puts the keyboard down, as a device did. It can't be
			// decided here, though: a framework focuses a field from the tap *it* is being
			// given, and this runs first. Mojo's text field does exactly that - its widget
			// listens for a tap and calls focus() - and SimpleChat's compose box could not be
			// typed into, because the field was focused and then blurred a line later.
			// So the decision waits until the tap has been handled, and stands only if
			// nothing took focus in the meantime.
			if (!f) { afterTap(st.target, before); }
		}
		fire("mouseout", t, target);
		if (st.moved && ev.type === "touchend") { flick(t); }
		st = null;
		ev.preventDefault();
	}
	document.addEventListener("touchend", end, opts);
	document.addEventListener("touchcancel", end, opts);
})();

// The other half of the webOS input model: the keyboard. LunaSysMgr's virtual keyboard put
// real key events into the page, so a keystroke arrived as keydown, keypress and keyup with
// the character's own code. Android's keyboards are input methods, and a soft keyboard
// commits text through the IME instead. Measured on the HP 10 G2, typing "a":
//
//   soft keyboard   keydown keyCode=229 -> textInput data="a" -> input -> keyup keyCode=229
//   key injection   keydown keyCode=82  -> keypress keyCode=charCode=114 -> textInput -> input -> keyup
//
// 229 is the standard "ask the IME" sentinel, and **no keypress is dispatched at all**. Code
// written for webOS reads keypress, so with a soft keyboard it simply never runs: Mojo hides
// a text field's hint text from its keypress handler, so an app's placeholder stayed behind
// what was being typed, and the same handler's charsAllow filter never rejected anything.
//
// So the character is delivered as the keypress a device sent. textInput fires before the
// text is inserted and is cancelable, which is where a device's keypress sat too, so a
// listener that stops the event still keeps the character out. A real keypress means a real
// key: the synthetic one is only sent when none came.
(function () {
	var sawKeypress = false;
	document.addEventListener("keydown", function () { sawKeypress = false; }, true);
	document.addEventListener("keypress", function () { sawKeypress = true; }, true);
	document.addEventListener("textInput", function (e) {
		if (sawKeypress) { return; }
		var data = e.data;
		if (typeof data !== "string" || !data.length) { return; }
		var target = e.target || document.activeElement;
		if (!target || !target.dispatchEvent) { return; }
		// An IME can commit several characters at once (a predicted word, an autocorrection).
		// Each gets its own keypress, as typing them would have; one refusal stops the lot,
		// because half a commit can't be inserted.
		for (var i = 0; i < data.length; i++) {
			var code = data.charCodeAt(i);
			var ev = document.createEvent("Events");
			ev.initEvent("keypress", true, true);
			// A generic Event has no key fields of its own, so these are plain properties -
			// which is what a listener reads.
			ev.keyCode = code;
			ev.charCode = code;
			ev.which = code;
			if (!target.dispatchEvent(ev)) { e.preventDefault(); return; }
		}
	}, true);
})();

// The focused field stays in view while the keyboard is up.
//
// The card shrinks to the space above the keyboard and the page is re-laid out, which is what
// a device did; an app whose own layout doesn't give all of that space back then leaves its
// field under the keyboard. SimpleChat's compose box does: its scene stays 463 px tall in a
// 385 px card, so the box a person is typing into sits below the fold. Typing into something
// you can't see is the one thing that must not happen, so the field is brought back into view
// once the resize has settled. Where an app does reflow properly this finds nothing to do.
(function () {
	function typing(el) {
		if (!el || el === document.body) { return false; }
		return /^(INPUT|TEXTAREA)$/.test(el.tagName) || el.isContentEditable;
	}
	/** The nearest ancestor that has somewhere to scroll to. */
	function scroller(el) {
		for (var n = el.parentNode; n && n.nodeType === 1; n = n.parentNode) {
			if (n.scrollHeight - n.clientHeight > 1) { return n; }
		}
		return null;
	}
	function keepVisible() {
		var el = document.activeElement;
		if (!typing(el)) { return; }
		var box = scroller(el);
		if (!box) { return; }
		var e = el.getBoundingClientRect(), b = box.getBoundingClientRect();
		// Clear of the bottom edge by the field's own height: an app's own bar sits down
		// there (SimpleChat's Photo button does), and a field tucked right against the edge
		// is under it. Above that, leave the scroll alone.
		var margin = 48;
		var over = e.bottom + margin - b.bottom;
		if (over > 0) { box.scrollTop = Math.min(box.scrollTop + over, box.scrollHeight - box.clientHeight); }
		else if (e.top < b.top) { box.scrollTop = Math.max(0, box.scrollTop - (b.top - e.top)); }
	}
	// Twice: the card's resize arrives before the page has finished re-laying itself out, so
	// the first pass often has nothing to measure yet.
	window.__lunacyKeepFieldVisible = function () { setTimeout(keepVisible, 0); setTimeout(keepVisible, 150); };
	window.addEventListener("resize", window.__lunacyKeepFieldVisible);
	// And when a field is focused while the keyboard is already up, which fires no resize.
	document.addEventListener("focusin", window.__lunacyKeepFieldVisible, true);
})();

// Uncaught script errors reach only the console. The reference TouchPad (WebKit 534.6)
// has window.onerror but never calls it, and dispatches no "error" event to window listeners
// (Workbench/probe 0.0.5). Some apps install onerror overlays that were never seen on a device.
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
			d.set.call(this, window.__lunacyFileUrl ? __lunacyFileUrl(v, true) : v);
		}
	});
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
