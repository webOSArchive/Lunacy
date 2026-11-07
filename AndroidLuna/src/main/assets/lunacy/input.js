// Lunacy's compat layer, part 1 of 4: the input model. compat.js was split by position into
// input.js, engine.js, media.js and layout.js, loaded in that order (AppServer's page head), so
// every section still runs after the same ones it did. ES5 only: must run on Chromium 37.
// Here: touches as webOS's mouse events and flicks, the timer pacing (which sits among them),
// key presses from a soft keyboard, removeInputFocus, and keeping the focused field in view.
// webOS WebKit delivered touches to web content as mouse events (mousedown/mousemove/mouseup
// + click). Enyo 1 and Mojo only listen for mouse events. Chromium only synthesizes them for
// taps, never drags, so recreate the webOS input model for every page.
(function () {
	var st = null;
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
		return target.dispatchEvent(e);
	}
	function under(t) { return document.elementFromPoint(t.clientX, t.clientY) || (st && st.target) || document.body; }
	function focusable(el) {
		for (; el && el.nodeType === 1; el = el.parentNode) {
			if (/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) || el.isContentEditable) { return el; }
		}
		return null;
	}
	var opts = { capture: true, passive: false };  // Chromium 37 reads this as capture=true
	// Text editing - the caret, holding in a field, selection and its popup - is textedit.js's.
	// A touch that starts on its popup or handles is its own and the page never hears of it.
	var edit = null, editTouch = false;
	function E() { return edit || (edit = window.__lunacyEdit || null); }
	document.addEventListener("touchstart", function (ev) {
		if (ev.touches.length > 1) { st = null; if (E()) { E().moved(); } return; }  // leave multi-touch (pinch) alone
		var t = ev.changedTouches[0];
		if (E() && E().owns(t)) {
			st = null; editTouch = true;
			E().uiTouch("start", t, t.target);
			ev.preventDefault();
			return;
		}
		editTouch = false;
		st = { x: t.clientX, y: t.clientY, target: t.target, moved: false, t: Date.now(), px: t.clientX, py: t.clientY };
		if (E()) { E().down(t, t.target); }
		samples = []; sample(t);
		fire("mouseover", t, t.target);
		// Enyo cancels every mousedown and moves the focus itself on the mouseup
		// (enyo.gesture.tryFocus), keeping it where a control asks - its Edit menu does, so
		// that Cut, Copy and Paste reach the field they are for. On such a page the tap-away
		// rule below would second-guess it.
		st.enyoFocus = !fire("mousedown", t, t.target) && !!(window.enyo && enyo.gesture);
		ev.preventDefault();
	}, opts);
	// A move reaches the page only once the finger has really gone somewhere, as LunaSysMgr's
	// EventThrottler::shouldDropMove decided for every web app (WebAppMgrProxy): until the
	// finger leaves a radius around where it went down, moves are dropped, and after that a
	// move to the same spot is. The radius is the reference TouchPad's own luna.conf -
	// TapRadiusMax 25, shrinking 10% every 200 ms held down to TapRadiusMin 5 - in screen
	// pixels, which are TouchPad px here too. Mojo counts on it: off the emulator it treats any
	// mousemove after a mousedown as the start of a drag and drops the tap, so a finger's
	// first touchmove - the Galaxy Tab A7 Lite sends one at the very spot it went down - left
	// drPodder's menu items and buttons dead to a real finger while adb's taps worked.
	var TAP_RADIUS = 25, TAP_RADIUS_MIN = 5, SHRINK_PERCENT = 10, SHRINK_MS = 200;
	function stillATap(t) {
		var held = Date.now() - st.t, radius = TAP_RADIUS;
		if (held > SHRINK_MS) {
			radius -= Math.floor(radius * SHRINK_PERCENT * held / (SHRINK_MS * 100));
			if (radius < TAP_RADIUS_MIN) { radius = TAP_RADIUS_MIN; }
		}
		var dx = t.clientX - st.x, dy = t.clientY - st.y;
		return dx * dx + dy * dy < radius * radius;
	}
	document.addEventListener("touchmove", function (ev) {
		if (editTouch) { E().uiTouch("move", ev.changedTouches[0]); ev.preventDefault(); return; }
		if (!st) { return; }
		var t = ev.changedTouches[0];
		sample(t);
		ev.preventDefault();
		if (!st.moved) {
			if (stillATap(t)) { return; }
			st.moved = true;
			if (E()) { E().moved(); }
		} else if (t.clientX === st.px && t.clientY === st.py) {
			return;
		}
		st.px = t.clientX; st.py = t.clientY;
		fire("mousemove", t, under(t));
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
	// Mojo's popups - the app menu and its Edit submenu, pickers - are drawn over the scene and
	// act on the field that has the focus: Edit's Cut, Copy and Paste are greyed out the moment
	// no text field is focused. A tap in one is not a tap away.
	function inMojoPopup(el) {
		for (; el && el.nodeType === 1; el = el.parentNode) {
			if ((" " + el.className + " ").indexOf(" palm-popup-container ") >= 0) { return true; }
		}
		return false;
	}
	function afterTap(target, before) {
		if (inMojoPopup(target)) { return; }
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
		if (editTouch) {
			var u = ev.changedTouches[0];
			editTouch = false;
			E().uiTouch("end", u, ev.type === "touchend" ? document.elementFromPoint(u.clientX, u.clientY) : null);
			ev.preventDefault();
			return;
		}
		if (!st) { return; }
		var t = ev.changedTouches[0], target = under(t);
		// The flick goes first, before the mouseup, as LunaSysMgr delivered it: Mojo's
		// gesture tracker only notes a flick (Mojo.Gesture.dispatchGesture sets sendFlick and
		// the velocity) and sends its own flick event from the mouseup that follows. A flick
		// after the mouseup was never seen, and no Mojo scroller coasted (codepoet,
		// 2026-10-03; Enyo's scroller measures its own, so it was unaffected).
		if (st.moved && ev.type === "touchend") { flick(t); }
		// Read before the mouse events go out: that is when a framework may focus a field.
		var before = document.activeElement;
		// A touch taken away from the page - the shell or a dashboard row claiming the gesture -
		// is no release where the finger was: WindowedWebApp turned LunaSysMgr's PenCancel into
		// a mouseup at (-10000, -10000), on nothing. Released where the finger was, it read as a
		// tap (Mojo's tap needs no more than a mouseup) or crossed into another element (Enyo
		// clicks their common ancestor). Off the page, Mojo sees y < 0 and sends no tap, and
		// Enyo finds no control on the document to click.
		if (ev.type === "touchcancel") {
			fire("mouseup", { screenX: -10000, screenY: -10000, clientX: -10000, clientY: -10000 }, document);
		} else {
			fire("mouseup", t, target);
		}
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
			if (!f && !st.enyoFocus) { afterTap(st.target, before); }
			if (E()) { E().tapped(t, st.target); }
		}
		fire("mouseout", t, target);
		st = null;
		ev.preventDefault();
	}
	document.addEventListener("touchend", end, opts);
	document.addEventListener("touchcancel", end, opts);
})();

// Timers at the TouchPad's pace. Apps animated with a chain of short timers and fixed steps,
// never reading the clock: Photos' slideshow slides each photo in by 90 px per
// setTimeout(..., 1) (SwipeTransition.doScene), so its speed was the device's. Two limits set
// it there, both measured (Workbench/probe's slideprobe, and Photos filmed in Exhibition):
// - No timer ran sooner than 10 ms after it was set; Chromium runs the first few at 1 ms and
//   the rest at 4. So a delay under 10 ms waits 10.
// - A step that changed the page had to be drawn before the next could run, and drawing a
//   full-screen photo took the TouchPad about 70 ms: Photos' slide took about 770 ms on its
//   screen for 11 steps, where Chromium drew it in a frame or two. So a short timer set by a
//   short timer whose callback changed the page - the next step of an animation - waits
//   70 ms. A chain that only computes (Mojo's and Enyo's "do this next") keeps 10 ms.
// Longer delays are untouched.
(function () {
	var MIN_MS = 10, STEP_MS = 70, slice = Array.prototype.slice;
	var nativeTimeout = window.setTimeout, nativeInterval = window.setInterval;
	// While a short timer's callback runs: whether it has changed the page so far.
	var watch = null, inShort = false, changed = false;
	function changedPage() {
		if (watch && !changed) { changed = watch.takeRecords().length > 0; }
		return changed;
	}
	function run(fn, short) {
		if (typeof fn !== "function") { return fn; }
		return function () {
			if (!short || !window.MutationObserver) { return fn.apply(this, arguments); }
			var wasShort = inShort, wasWatch = watch, wasChanged = changed;
			inShort = true; changed = false;
			watch = new MutationObserver(function () {});
			watch.observe(document, { attributes: true, childList: true, subtree: true });
			try { return fn.apply(this, arguments); }
			finally { watch.disconnect(); inShort = wasShort; watch = wasWatch; changed = wasChanged; }
		};
	}
	window.setTimeout = function (fn, ms) {
		var args = slice.call(arguments), short = !(ms >= MIN_MS);
		if (short) { args[1] = inShort && changedPage() ? STEP_MS : MIN_MS; }
		args[0] = run(fn, short);
		return nativeTimeout.apply(window, args);
	};
	window.setInterval = function (fn, ms) {
		var args = slice.call(arguments);
		if (!(ms >= MIN_MS)) { args[1] = MIN_MS; }
		return nativeInterval.apply(window, args);
	};
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

// webOS's text-entry modes, as the keyboard hears them.
//
// A field said what the webOS keyboard's smart text engine should do with webOS's own
// attributes, which Chromium knows nothing of: Mojo's TextField writes x-palm-ste-mode
// ("replace-off" for autoReplace: false; "cap-lowercase", "cap-title", "cap-sentence" for
// textCase) or, on older controls, x-palm-disable-ste-all="true" (textReplacement: false) and
// x-palm-title-cap="true"; Enyo's Input writes x-palm-disable-auto-cap="true" for
// autoCapitalize "lowercase" (its autocorrect: false is already the standard autocorrect="off").
// So every such field was autocorrected and capitalised. As a field takes focus, before the
// keyboard is raised, each is given the standard attribute Chromium does pass on to Android's
// keyboard: autocorrect="off" (no AUTO_CORRECT flag) and autocapitalize (the CAP flags, on
// WebViews from Chromium 43; Chromium 37 ignores it). An attribute the page set itself stays.
(function () {
	function set(el, name, value) { if (!el.hasAttribute(name)) { el.setAttribute(name, value); } }
	function modes(el) {
		if (!el || el.nodeType !== 1 || !(/^(INPUT|TEXTAREA)$/.test(el.tagName) || el.isContentEditable)) { return; }
		var ste = " " + (el.getAttribute("x-palm-ste-mode") || "") + " ";
		var all = el.getAttribute("x-palm-disable-ste-all") === "true";
		if (all || ste.indexOf(" replace-off ") >= 0) { set(el, "autocorrect", "off"); }
		// The smart text engine off altogether takes its capitals too, unless the field asks for them.
		if (ste.indexOf(" cap-title ") >= 0 || el.getAttribute("x-palm-title-cap") === "true") { set(el, "autocapitalize", "words"); }
		else if (ste.indexOf(" cap-sentence ") >= 0) { set(el, "autocapitalize", "sentences"); }
		else if (all || ste.indexOf(" cap-lowercase ") >= 0 || el.getAttribute("x-palm-disable-auto-cap") === "true") { set(el, "autocapitalize", "none"); }
	}
	document.addEventListener("focusin", function (ev) { modes(ev.target); }, true);
	document.addEventListener("mousedown", function (ev) { modes(ev.target); }, true);
})();
