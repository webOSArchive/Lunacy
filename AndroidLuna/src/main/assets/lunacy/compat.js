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
	 * So: if something took focus, the keyboard follows it - the bridge's focusin listener
	 * does that on its own. If nothing did, a tap away from the focused field puts the
	 * keyboard down, as a device did - and a tap *inside* that field's own widget is not a
	 * tap away from it. Mojo draws a field's hint text as a sibling of the input, so tapping
	 * the hint of an already-focused field has to raise the keyboard, not dismiss it; that
	 * tap changes nothing about focus, so no focusin comes and the keyboard has to be asked
	 * for here.
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

// Putting the keyboard away takes the field's focus with it, as webOS did.
//
// On webOS the keyboard was never simply hidden. Dismissing it - its hide key, a swipe down,
// an app asking - reached IMEController::hideIME, which didn't hide anything: it asked the
// web app to removeInputFocus, WebKit blurred the focused node, and the keyboard went away
// because nothing was focused any more. Android's IME is the other way round: it hides, and
// the field keeps focus and its caret.
//
// That difference is visible, because a Mojo app has no other way to hear about the keyboard.
// Mojo has no keyboardShown of its own, so an app watches its field's focus instead:
// SimpleChat sizes its chat log from document.activeElement (bottomBuffer 600 with the field
// focused, 260 without), so with the field still focused the space stayed reserved for a
// keyboard that wasn't there. The shell calls this when the keyboard goes down.
(function () {
	window.__lunacyRemoveInputFocus = function () {
		var el = document.activeElement;
		if (!el || el === document.body || !el.blur) { return; }
		if (!/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) && !el.isContentEditable) { return; }
		el.blur();
	};
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

// What the window says it is.
//
// On a webOS device every way of asking how big the window is gave the same answer, and an
// app could use any of them: window.innerWidth and document.documentElement.clientWidth were
// both the card's own pixels, and screen.width was the display's, in the same unit. Here all
// three disagree, for two separate reasons.
//
// **innerWidth is a pixel wider than the page is laid out.** A card is 1280 device px wide,
// but Chromium works in density-independent pixels: on the HP 10 G2's 213 dpi screen it
// divides by 1.33125, rounds 961.5 up to 962 and multiplies back, so the page lays out 1281
// CSS px into 1280 (see "border-image seams" in Docs/fix-log.md, which is the same rounding
// seen from the other end). documentElement.clientWidth is the layout viewport, 1280;
// innerWidth is the visual one, 1281. One pixel is enough to break a layout outright: webOS
// IAmA reddit sizes its left pane to innerWidth * 0.4 and leaves the right one at 60% in CSS,
// which comes to 1280.4 in a 1280 px line, so the right pane - the whole article - dropped
// below the fold and the card showed an empty page beside the list.
//
// **screen is in the wrong unit.** screen.width/height come back in Android's
// density-independent pixels (962 x 601 here), where a device reported the panel's own
// (1024 x 768 on a TouchPad, measured with Workbench/probe). An app laying out to screen.width
// gets three quarters of the card.
//
// Both are reported the way a device reported them: the layout viewport for the window, and
// the screen the shell says this device has, turned with the screen.
(function () {
	var N = window.LunacyNative;
	function def(obj, name, get) {
		try { Object.defineProperty(obj, name, { configurable: true, enumerable: true, get: get }); } catch (e) {}
	}
	// The layout viewport, which is what the page is laid out into. In standards mode this is
	// the viewport whatever the document's own height is; the fallback is for a page read
	// before its documentElement exists.
	var rawW = window.innerWidth, rawH = window.innerHeight;
	function viewW() { var e = document.documentElement; return (e && e.clientWidth) || rawW || 0; }
	function viewH() { var e = document.documentElement; return (e && e.clientHeight) || rawH || 0; }
	def(window, "innerWidth", viewW);
	def(window, "innerHeight", viewH);

	// The display, in the pixels this device tells apps it has. It has to follow the screen
	// round rather than be read off the window: deviceInfo names the screen in its natural
	// orientation and never changes (a TouchPad says 1024 x 768 whichever way up it is),
	// while the reference device's screen.width in portrait reported 768 x 1024. The window
	// can't stand in for it either, because the keyboard takes half the card's height and the
	// screen doesn't change when it does.
	function displaySize() {
		try { return JSON.parse(N.screenSize()); } catch (e) { return null; }
	}
	var size = displaySize();
	if (size && size.width && size.height) {
		def(screen, "width", function () { return (displaySize() || size).width; });
		def(screen, "height", function () { return (displaySize() || size).height; });
		def(screen, "availWidth", function () { return (displaySize() || size).width; });
		def(screen, "availHeight", function () { return (displaySize() || size).height; });
		// A card is the whole window on webOS, so outer and inner differed only by the status
		// bar; Chromium reports these in dip too.
		def(window, "outerWidth", function () { return (displaySize() || size).width; });
		def(window, "outerHeight", function () { return (displaySize() || size).height; });
	}
})();

// The card's own background covers the card.
//
// An app paints the card's background on its body, almost always as one image stretched to
// fill it: background-size: 100% 100%, usually with background-attachment: fixed so it
// doesn't scroll with the content. That background is propagated to the canvas, and the area
// it is sized against should be the whole card.
//
// This WebView sizes it against the body's own box, and Mojo gives every body
// `min-height: 480px; height: 100%` (global-base.css), which comes to exactly 480 whenever a
// page's own content doesn't fill the card - a scene whose panes are floats, say. So the
// image is squeezed into 480 px and then tiled down the rest of the card, with a seam at
// every repeat. webOS IAmA reddit is the case codepoet reported as a "sliced up blob"; the
// reference TouchPad draws one smooth ramp over the whole card.
//
// **Only the card's background.** An ordinary element with a fixed background is left exactly
// alone, because there the device agrees with this WebView: both size it against the
// element's own box. Measured on reddit's own `#bar`, a 1030 x 15 strip carrying a 400 x 60
// shadow gradient at 100% 100% - the reference device draws a soft line a few pixels deep,
// which is the gradient squeezed into 15 px, and so does Lunacy. Sizing that one against the
// viewport instead put a flat dark block under the search bar, which is what this rule did
// when it was written to apply to everything.
(function () {
	var PCT = /%/;
	function apply() {
		if (!document.body) { return; }
		// Whichever of the two paints the canvas: the root's background if it has one, else
		// the body's, which is propagated to the root. Nothing else is touched.
		var els = [document.documentElement, document.body];
		var w = document.documentElement.clientWidth, h = document.documentElement.clientHeight;
		for (var i = 0; i < els.length; i++) {
			var el = els[i];
			// Read the size the app declared, not the one this wrote last time: with the
			// pixels still on the element the computed value has no percentage in it any
			// more, and a card that had been rotated would keep the size of the orientation
			// it was in - which is what codepoet saw as the page not re-flowing, the
			// background sitting in a band across the middle of a portrait card.
			el.style.backgroundSize = "";
			el.style.backgroundRepeat = "";
			var cs = window.getComputedStyle(el);
			if (cs.backgroundImage === "none") { continue; }
			var size = PCT.test(cs.backgroundSize) ? cs.backgroundSize : null;
			if (!size) { continue; }
			var parts = size.split(/\s*,\s*/), out = [], covers = true;
			for (var p = 0; p < parts.length; p++) {
				var xy = parts[p].trim().split(/\s+/);
				var sx = px(xy[0], w), sy = px(xy.length > 1 ? xy[1] : xy[0], h);
				if (parseFloat(sx) < w || parseFloat(sy) < h) { covers = false; }
				out.push(sx + " " + sy);
			}
			el.style.backgroundSize = out.join(", ");
			// A background sized to cover the card has nothing to repeat inside it, so saying
			// so costs nothing - and it is the only thing that stops this WebView tiling one
			// that is fixed. Measured on the HP 10 G2 with reddit's card: with the exact size
			// alone the gradient still came back in blocks repeating every 264 px; with
			// no-repeat it is one smooth ramp, 104 down to 23 against the reference
			// TouchPad's 102 down to 23.
			if (covers) { el.style.backgroundRepeat = "no-repeat"; }
		}
	}
	function px(v, basis) {
		var m = /^([\d.]+)%$/.exec(v);
		return m ? Math.round(parseFloat(m[1]) / 100 * basis) + "px" : v;
	}
	window.__lunacyCardBackground = apply;
	// A framework gives the body its classes when it builds its first scene, which is after
	// load: Mojo's palm-default - the class reddit hangs its background on - isn't there when
	// the page finishes loading. So this is repeated over the first few seconds rather than
	// run once. Each pass is two computed-style reads.
	apply();
	document.addEventListener("DOMContentLoaded", apply);
	window.addEventListener("load", function () {
		apply();
		[100, 400, 1200, 3000].forEach(function (ms) { setTimeout(apply, ms); });
	});
	window.addEventListener("resize", apply);
})();

// The viewport, declared rather than left to the engine.
//
// webOS apps carry a viewport meta, and on a device it means something particular: the SDK
// told developers to put `height=device-height` in index.html so a Pre/Pre2-shaped app isn't
// letterboxed on a Pre3, and `"uiRevision": 2` in appinfo.json so it isn't put in the phone
// simulator frame on a TouchPad. Both of the apps this was worked out on carry both. It is a
// declaration about the *card*, not a browser hint, so nothing here overwrites it.
//
// What it doesn't carry is a width, because on webOS there was nothing to say: a card was the
// viewport. This engine has to be told. Left to itself it takes the page's own layout width,
// and when that comes out wider than the card - webOS IAmA reddit has a decorative strip
// hard-coded to 1030 px for a TouchPad - it draws the whole page smaller so that it fits.
// Measured on the HP 10 G2: after turning the tablet into portrait a 400 px element came out
// 313 px wide in an 800 px card, and the bottom of the card was left unpainted.
//
// So the card's own width goes into the meta, with the scale pinned, and the app's own
// directives are kept exactly as they are:
//
//   - the app's meta is read and every directive in it is carried over;
//   - `width`, `minimum-scale` and `maximum-scale` are added only if the app hasn't set them;
//   - an app that sets its own `width` or `initial-scale` is telling the engine something
//     specific, and is left alone entirely;
//   - the app's own element is not touched: the merged copy is appended after it, which is
//     the one the engine reads, so an app that inspects its own markup still finds what it
//     wrote.
//
// The width follows the card, so it is written again whenever the card is resized.
(function () {
	var N = window.LunacyNative, mine = null, appMeta;

	/**
	 * "width=device-width, height=device-height" -> [["width","device-width"], …].
	 *
	 * Commas *and* whitespace separate, which is what the engine itself does and what Palm's
	 * own Clock needs: its meta reads `width=device-width initial-scale=1.0, maximum-scale=1.0`
	 * with the comma missing after the first one.
	 */
	function parse(content) {
		var out = [];
		String(content || "").split(/[,\s]+/).forEach(function (part) {
			var at = part.indexOf("=");
			if (at < 0) { return; }
			var k = part.slice(0, at).trim().toLowerCase(), v = part.slice(at + 1).trim();
			if (k) { out.push([k, v]); }
		});
		return out;
	}
	function has(list, key) {
		for (var i = 0; i < list.length; i++) { if (list[i][0] === key) { return true; } }
		return false;
	}
	function serialize(list) {
		return list.map(function (p) { return p[0] + "=" + p[1]; }).join(", ");
	}
	/**
	 * The card, in device pixels - or nothing, if this window hasn't got one. An app's root
	 * window is a pixel square and never shown (a `noWindow` app's is), and a card that
	 * hasn't been laid out yet reads as nothing at all; neither is a viewport worth writing.
	 */
	function card() {
		var size;
		try { size = JSON.parse(N.cardSize()); } catch (e) { return null; }
		if (!size || !(size.width > 64) || !(size.height > 64)) { return null; }
		return size;
	}
	/** The app's own declaration: the last viewport meta that isn't the one written here. */
	function declared() {
		var metas = document.getElementsByTagName("meta"), found = null;
		for (var i = 0; i < metas.length; i++) {
			if (metas[i] !== mine && String(metas[i].name || "").toLowerCase() === "viewport") { found = metas[i]; }
		}
		return found;
	}
	function apply() {
		var size = card();
		if (!size || !size.width) { return; }
		if (appMeta === undefined) { appMeta = declared(); }
		var list = parse(appMeta && appMeta.content), i, named = false;
		// `device-width` and `device-height` are the SDK's own idiom - "as big as the device,
		// don't letterbox me" - and on webOS they were the card. This engine reads them as
		// the display in density-independent pixels, which is a different unit from the card:
		// carrying `height=device-height` over as it stands laid reddit's card out 1280 x 580
		// where it is 1280 x 772. Written as the card's own numbers they say the same thing
		// in the same unit. A size the app named in pixels is left exactly as it is, and the
		// page is then left alone altogether: that app is being specific.
		for (i = 0; i < list.length; i++) {
			var k = list[i][0], v = list[i][1].toLowerCase();
			if (k === "width" && v === "device-width" && size.width) { list[i][1] = String(size.width); }
			else if (k === "height" && v === "device-height" && size.height) { list[i][1] = String(size.height); }
			else if (k === "initial-scale" || k === "minimum-scale" || k === "maximum-scale" ||
				k === "user-scalable") { named = true; }
		}
		// Nothing the app said is overwritten; what it left unsaid is filled in. The size is
		// the card's, and both halves of it matter: with only the width given, the engine
		// works the height out for itself and comes back a pixel short - 771 where the card
		// is 772 - which leaves the bottom row of the card showing through underneath the
		// page. That is the white line along the bottom of Palm's Clock.
		if (!has(list, "width")) { list.push(["width", String(size.width)]); }
		if (!has(list, "height") && size.height) { list.push(["height", String(size.height)]); }
		// The scale is only pinned for an app that says nothing about it. One that does is
		// controlling its own, and a minimum of 1 could contradict it.
		if (!named) {
			if (!has(list, "minimum-scale")) { list.push(["minimum-scale", "1"]); }
			if (!has(list, "maximum-scale")) { list.push(["maximum-scale", "1"]); }
		}
		if (!list.length) { return; }
		var content = serialize(list);
		if (mine && mine.content === content) { return; }
		if (!mine) {
			mine = document.createElement("meta");
			mine.name = "viewport";
		}
		mine.content = content;
		var head = document.head || document.getElementsByTagName("head")[0];
		if (head && mine.parentNode !== head) { head.appendChild(mine); }
	}
	// After the document's own metas have been parsed, because the engine takes the last one
	// it sees rather than merging them, and again whenever the card changes size.
	// Repeated over the first few seconds as well as on the events, because a card is often
	// not laid out yet when its page finishes loading, and until it is there is no width to
	// write. Each pass is one call across the bridge and a string compare.
	if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", apply); }
	else { apply(); }
	window.addEventListener("load", function () {
		apply();
		[100, 400, 1200, 3000].forEach(function (ms) { setTimeout(apply, ms); });
	});
	window.addEventListener("resize", apply);
	window.__lunacyViewport = apply;
})();
