// Lunacy's compat layer, part 4 of 4 (see input.js): the window and the card - what the window
// says it is, the card's background, the viewport, the page drawn, cookies to disk, the
// mutation events, the phone's columns and hints, dates as the TouchPad read them, and unload
// handlers on close. ES5 only.
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
				// Not trim(): pages replace it, and Plex's returns undefined.
				var xy = parts[p].replace(/^\s+|\s+$/g, "").split(/\s+/);
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
			// The split leaves no whitespace to trim, and pages replace trim(): Plex's returns undefined.
			var k = part.slice(0, at).toLowerCase(), v = part.slice(at + 1);
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
	 * The card, in TouchPad px, with `scale` the device pixels in each - or nothing, if this
	 * window hasn't got one. An app's root window is a pixel square and never shown (a
	 * `noWindow` app's is), and a card that hasn't been laid out yet reads as nothing at all;
	 * neither is a viewport worth writing. Anything bigger is: a dashboard is 52 px tall, and
	 * a floor of 64 here once left drPodder's with no viewport at all, laid out at the
	 * engine's default 980 px with its play controls off the right-hand edge.
	 */
	function card() {
		var size;
		try { size = JSON.parse(N.cardSize()); } catch (e) { return null; }
		if (!size || !(size.width > 1) || !(size.height > 1)) { return null; }
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
		//
		// A scale the app names is the same story: on webOS it counted TouchPad px per CSS px,
		// and this engine counts its density-independent pixels. Where the window's scale is
		// the display's density the two agree, and the number stands. Where they differ - an
		// Exhibition window, held at the TouchPad's dock size and scaled to fit the screen -
		// Photos' `maximum-scale=1.0` held its slideshow at the display's density, laid it out
		// 1068 x 772 instead of 1024 x 740 on a Pixel Tablet, and its hidden controls showed
		// along the bottom (2026-10-19). So the number is carried over in the engine's unit.
		var ratio = (size.scale || 1) / (window.devicePixelRatio || 1);
		for (i = 0; i < list.length; i++) {
			var k = list[i][0], v = list[i][1].toLowerCase();
			if (k === "width" && v === "device-width" && size.width) { list[i][1] = String(size.width); }
			else if (k === "height" && v === "device-height" && size.height) { list[i][1] = String(size.height); }
			else if (k === "initial-scale" || k === "minimum-scale" || k === "maximum-scale" ||
				k === "user-scalable") {
				named = true;
				if (k !== "user-scalable" && parseFloat(v) > 0 && Math.abs(ratio - 1) > 0.001) {
					list[i][1] = String(Math.round(parseFloat(v) * ratio * 100000) / 100000);
				}
			}
		}
		// Nothing the app said is overwritten; what it left unsaid is filled in. The size is
		// the card's, and both halves of it matter: with only the width given, the engine
		// works the height out for itself and comes back a pixel short - 771 where the card
		// is 772 - which leaves the bottom row of the card showing through underneath the
		// page. That is the white line along the bottom of Palm's Clock.
		if (!has(list, "width")) { list.push(["width", String(size.width)]); }
		if (!has(list, "height") && size.height) { list.push(["height", String(size.height)]); }
		// The scale is only pinned for an app that says nothing about it. One that does is
		// controlling its own, and a pinned scale could contradict it.
		//
		// Pinned at one TouchPad px per CSS px, which is what the shell asked the engine for
		// (setInitialScale). A viewport scale counts the engine's density-independent pixels,
		// so it is about the shell's scale over devicePixelRatio - not 1. Pinned at 1 it held
		// only while the shell's scale was 1: on a 1920 x 1200 tablet at 240 dpi (scale 2,
		// ratio 1.5) the page came out a third too large, laid out 1920 wide in a 960 px card,
		// its centred content drawn three quarters of the way across and its bottom off the
		// screen.
		//
		// "About", because the engines round, each its own way, and a pixel matters (see
		// "innerWidth" above). Chromium 37 sizes the card in whole dips, rounded up - a 320 px
		// dashboard's 240.375 dips are 241 - and WebView 131 snaps the scale to steps of about
		// 0.0007. So the first guess is the card's whole dips over its width in TouchPad px,
		// and then the page is measured: the engine lays it out again as soon as the meta
		// changes, so a layout width a pixel off is walked towards the card in small steps,
		// and the first scale that fits is kept. The smallest that fits, because on WebView 131
		// the scale named still moves the height within one of its steps: an 800 x 1312 card
		// laid out 800 x 1312 at 0.7515 and 800 x 1311 at 0.7520.
		// Measured: WebView 131, 1340 px card, the guess is exact; 800 px card, the guess gave
		// 801. Chromium 37, 320 px dashboard, the guess is exact.
		// The scale is part of the key: a foldable folded or opened can keep the card's size in
		// TouchPad px and change the device px in each.
		var pin = null, key = size.width + "x" + size.height + "@" + size.scale;
		if (!named) {
			pin = fitted[key] || Math.ceil(size.width * (size.scale || 1) / (window.devicePixelRatio || 1) - 0.001) / size.width;
			if (!has(list, "minimum-scale")) { list.push(["minimum-scale", "@"]); }
			if (!has(list, "maximum-scale")) { list.push(["maximum-scale", "@"]); }
		}
		if (!list.length) { return; }
		var head = document.head || document.getElementsByTagName("head")[0];
		function write(scale) {
			var content = serialize(list).replace(/@/g, String(Math.round(scale * 100000) / 100000));
			if (mine && mine.content === content && mine.parentNode === head) { return false; }
			if (!mine) {
				mine = document.createElement("meta");
				mine.name = "viewport";
			}
			mine.content = content;
			if (head && mine.parentNode !== head) { head.appendChild(mine); }
			return true;
		}
		if (!write(pin || 1) || pin === null || fitted[key]) { return; }
		// The layout width as laid out, fraction and all: clientWidth rounds it. A device px
		// is a whole one, so where the card's width isn't a whole number of them the layout
		// can't be exactly the card - 884 px at a ratio of 2.625 is 2320.5 device px, and
		// Chromium laid the page out 883.81 px wide, which clientWidth called 884. Two floats
		// of 40% and 60% then came to a hair more than the page, and IAmA reddit's right pane
		// fell below the screen (a Galaxy Z Fold3, 2026-10-16). So what fits is at least the
		// card's width and under a pixel more: the extra is a fraction of a device px.
		var W = size.width;
		function laidWidth() { return document.documentElement.getBoundingClientRect().width || document.documentElement.clientWidth; }
		function fits(w) { return w > W - 0.001 && w < W + 1; }
		var first = laidWidth(), step = first >= W + 1 ? 0.0002 : -0.0002;
		for (var tries = 0, laid = first; laid && !fits(laid) && tries < 20; tries++) {
			pin += step;
			write(pin);
			laid = laidWidth();
			// Walked past it without landing in it: the step before was as close as it gets
			// from that side, and from above the step back is the one that isn't narrower.
			if ((step > 0) !== (laid >= W + 1)) {
				if (step > 0 && laid <= W - 0.001) { pin -= step; write(pin); }
				break;
			}
		}
		fitted[key] = pin;
	}
	/** The scale each card size came out right at, once measured. */
	var fitted = {};
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

// Tell the shell when the page has actually drawn.
//
// webOS held a card's space with a loading card - the app's own icon on a dark background -
// and took it away when the app had *drawn*, not when the framework announced itself. Mojo
// calls PalmSystem.stageReady() while it is still building the first scene, so a card that
// goes then is a blank one. Two animation frames after load is a frame that has been through
// the compositor, which is as close as a page can get to saying "I am on the screen".
(function () {
	var N = window.LunacyNative;
	if (!N || !N.pageDrawn) { return; }
	var raf = window.requestAnimationFrame || window.webkitRequestAnimationFrame;
	function sent() { try { N.pageDrawn(); } catch (e) {} }
	/** Has the page put anything on the screen yet - any laid-out element in the body? */
	function painted() {
		var kids = document.body && document.body.children;
		for (var i = 0; kids && i < kids.length; i++) {
			var r = kids[i].getBoundingClientRect();
			if (r.width > 0 && r.height > 0) { return true; }
		}
		return false;
	}
	// `load` is not it on its own: a framework builds its first scene in its own load
	// handlers and afterwards, so a card let go then is a blank one. Waited for instead,
	// frame by frame, until the page has something laid out - and given up on after a few
	// seconds, because a card kept behind a placeholder for ever would be worse than a blank
	// one.
	function tell(tries) {
		if (!raf) { sent(); return; }
		if (painted() || tries <= 0) { raf(function () { raf(sent); }); return; }
		raf(function () { tell(tries - 1); });
	}
	function start() { tell(180); }
	if (document.readyState === "complete") { start(); } else { window.addEventListener("load", start); }
})();

// A cookie an app writes is on disk at once, as it was on webOS. Chromium keeps cookie writes
// in memory and flushes them some seconds later, so an app killed in between - Android stops
// Lunacy whenever it likes - lost them: Mojo's Mojo.Model.Cookie keeps apps' settings and
// logins in cookies, and Check Mate's log-out came back after a restart. The host flushes
// shortly after each write (AppWindow.cookieWritten).
(function () {
	var d = Object.getOwnPropertyDescriptor(Document.prototype, "cookie");
	if (!d || !d.set || !d.configurable || !window.LunacyNative || !LunacyNative.cookieWritten) { return; }
	Object.defineProperty(Document.prototype, "cookie", {
		configurable: true, enumerable: d.enumerable, get: d.get,
		set: function (v) {
			d.set.call(this, v);
			try { LunacyNative.cookieWritten(); } catch (e) {}
		}
	});
})();

// DOMNodeRemovedFromDocument and DOMNodeInsertedIntoDocument, where the engine no longer fires
// them. Mojo cleans a widget up when its element gets DOMNodeRemovedFromDocument (Mojo 1 and 2's
// WidgetController, Mojo Core's scroller, Mojo's node references): a dialog's cleanup is what
// takes away the focus guard it put on its scene, which blurs anything outside the dialog that
// takes focus. Chromium dropped DOM mutation events altogether (none fire in WebView 153; all
// do in 64), so there no widget was ever cleaned up and every closed dialog left its guard
// behind: Keyring SD's fields took focus and lost it again at once (codepoet, 2026-10-05).
// Where the events are missing, a MutationObserver sends them as the TouchPad's WebKit did:
// not bubbling, to each removed or added node and every element in it, in the order of the
// changes, so a node that moves gets the removal and then the insertion. They arrive once the
// script that made the change returns, not inside the removeChild call; Mojo's handlers only
// clean up and record. The observer starts only when a page listens for one of them.
// The SDK's desktop Mojo carries the same code (LunaRuntimes/mojo/sdk-patches,
// compat-mutation-events.patch); a change here goes there too.
(function () {
	var REMOVED = "DOMNodeRemovedFromDocument", INSERTED = "DOMNodeInsertedIntoDocument";
	if (!window.MutationObserver || !window.EventTarget) { return; }
	var fired = false, probe = document.createElement("div");
	probe.addEventListener(INSERTED, function () { fired = true; }, false);
	document.documentElement.appendChild(probe);
	document.documentElement.removeChild(probe);
	if (fired) { return; }

	function send(node, type) {
		var list = [node], i, e;
		if (node.getElementsByTagName) {
			var all = node.getElementsByTagName("*");
			for (i = 0; i < all.length; i++) { list.push(all[i]); }
		}
		for (i = 0; i < list.length; i++) {
			e = document.createEvent("Event");
			e.initEvent(type, false, false);
			list[i].dispatchEvent(e);
		}
	}
	var observer = null;
	function start() {
		if (observer) { return; }
		observer = new MutationObserver(function (records) {
			for (var r = 0; r < records.length; r++) {
				var rec = records[r], j;
				for (j = 0; j < rec.removedNodes.length; j++) { send(rec.removedNodes[j], REMOVED); }
				for (j = 0; j < rec.addedNodes.length; j++) {
					if (document.documentElement.contains(rec.addedNodes[j])) { send(rec.addedNodes[j], INSERTED); }
				}
			}
		});
		observer.observe(document, { childList: true, subtree: true });
	}
	var add = EventTarget.prototype.addEventListener;
	EventTarget.prototype.addEventListener = function (type) {
		if (type === REMOVED || type === INSERTED) { start(); }
		return add.apply(this, arguments);
	};
})();

// Palm's centred column fits a phone's card.
//
// Palm's Enyo apps put a settings or welcome screen in one centred column, `.box-center`,
// 500 px wide: the accounts library's first-launch view (Calendar, Contacts, Email and
// Photos' Welcome screens), the Accounts app, and Exhibition and Email's own copies of the
// same rule. On a TouchPad that is a column in the middle of a 1024 px card; on a 360 px
// phone card it runs off the side and the page scrolls sideways. Under 540 px the column takes
// the card's width less 12 px each side, as Lunacy's own Screen & Lock and Sounds & Alerts do
// (codepoet, 2026-10-03). Nothing changes on a tablet, where no card is that narrow. `html`
// lifts the rule above every app's own `.box-center`, whichever loads last; an inline style
// still wins, as it would have on the device.
//
// As a stylesheet link, not a <style>: Mojo's locale setup reads the href of every sheet in
// the document (Mojo.Locale.unloadLocaleSpecificStylesheets), a <style>'s is null, and every
// Mojo app stopped while it started - Palm's Video Player among them, so MeTube's videos
// never opened (2026-10-09). No webOS page of Palm's had a <style> for it to meet.
window.__lunacyStyle = function (css) {
	var l = document.createElement("link");
	l.rel = "stylesheet";
	l.href = "data:text/css," + encodeURIComponent(css);
	(document.head || document.documentElement).appendChild(l);
	return l;
};
__lunacyStyle("@media (max-width: 540px) { html .box-center { width: auto; margin-left: 12px; margin-right: 12px; } }");
// On a wider card, a column that names only a max-width (max-width: 500px; margin: 0 auto)
// is as wide as the card allows, up to that, and centred, as on the TouchPad: its old WebKit
// stretched a vertical -webkit-box's child to the box's width whatever its margins. Newer
// Chromium lays -webkit-box out as flexbox, where auto margins cancel the stretch, and the
// column shrank to its content: an Enyo Scroller has none, so it was 0 px wide and the card
// stood empty under its header (the BlueBubbles connector's settings, on the Fold3 unfolded,
// codepoet, 2026-11-04). :where gives the rule no weight, so a width the app sets (Palm's
// width: 500px) still wins; Chromium 37, which keeps the old layout, doesn't read :where.
__lunacyStyle(":where(.enyo-vflexbox) > :where(.box-center) { width: -webkit-fill-available; }");

// An Enyo RichText's hint stays on one line on a phone's card.
//
// Enyo 1's RichText shows its hint as the field's own text (class enyo-richtext-hint) until it
// is focused, so a hint wider than the field wraps and the field grows to hold it: Messaging's
// "Enter message here..." stood three lines tall beside its buttons on a phone, and dropped to
// one the moment the field was focused (codepoet, 2026-11-04). Under 540 px the hint keeps to
// one line and ends in "..." where it doesn't fit. On a tablet, as on the TouchPad, it is as
// Enyo drew it.
__lunacyStyle("@media (max-width: 540px) { html .enyo-richtext-hint { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; } }");

// Dates written year-month-day, read as the TouchPad read them.
//
// The TouchPad's WebKit (534.6) read "2026-11-4T08:36:32" (an unpadded day), "2026-1-4T8:5:7"
// and "2026-11-4 08:36:32" as local times, and a bare "2026-11-04" as local midnight
// (Workbench/probe/org.webosarchive.lunacy.jsprobe 0.0.3, 2026-11-04). Chromium answers Invalid
// Date for the unpadded ones and takes a bare date as UTC midnight, the evening before west of
// Greenwich: the BlueBubbles connector's "Last sync attempt" read "NaN-NaN-NaN NaN:NaN:NaN"
// (codepoet, 2026-11-04). A string in that shape, with no zone, is made a local time again,
// in new Date(s) and Date.parse(s); everything else goes to the engine's own Date. The
// constructor shares the engine's prototype, so instanceof Date and Date.now are as before.
(function () {
	var Native = Date, parse0 = Native.parse;
	var YMD = /^\s*(\d{4})-(\d{1,2})-(\d{1,2})(?:[T ](\d{1,2}):(\d{1,2})(?::(\d{1,2})(?:\.(\d+))?)?)?\s*$/;
	function local(s) {
		var m = typeof s === "string" && YMD.exec(s);
		if (!m) { return null; }
		var ms = m[7] ? +((m[7] + "00").slice(0, 3)) : 0;
		return new Native(+m[1], m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0), ms).getTime();
	}
	function LunacyDate(a, b, c, d, e, f, g) {
		if (!(this instanceof LunacyDate)) { return Native(); }
		switch (arguments.length) {
			case 0: return new Native();
			case 1: var t = local(a); return new Native(t === null ? a : t);
			case 2: return new Native(a, b);
			case 3: return new Native(a, b, c);
			case 4: return new Native(a, b, c, d);
			case 5: return new Native(a, b, c, d, e);
			case 6: return new Native(a, b, c, d, e, f);
			default: return new Native(a, b, c, d, e, f, g);
		}
	}
	LunacyDate.prototype = Native.prototype;
	LunacyDate.now = Native.now;
	LunacyDate.UTC = Native.UTC;
	LunacyDate.parse = function (s) { var t = local(s); return t === null ? parse0.apply(Native, arguments) : t; };
	window.Date = LunacyDate;
})();

// A page's unload handlers run when Lunacy closes its window, on every engine.
//
// webOS ran a window's unload handlers as it closed, and apps count on them: a dashboard
// swiped away tells its app from its own unload handler (Enyo's DashboardContent posts
// doUserClose to its opener, which fires the app's onUserClose), and Enyo apps' onUnload
// saves and tidies up. Lunacy closes a window by destroying its WebView, and newer Chromium
// won't register unload handlers at all ("Permissions policy violation: unload is not allowed
// in this document"), so on those engines none of it happened: Android Notifications never
// heard that a dashboard was swiped away and left the notification in Android, where it came
// back each time Lunacy started (codepoet, 2026-11-04). So the page keeps its unload handlers
// itself, and the shell runs them (window.__lunacyUnload) before it destroys the window
// (AppWindow.close); a navigation away runs them too, from pagehide. They run once.
(function () {
	var handlers = [], ran = false;
	var add = EventTarget.prototype.addEventListener, remove = EventTarget.prototype.removeEventListener;
	EventTarget.prototype.addEventListener = function (type, listener) {
		if (this === window && type === "unload") {
			if (listener && handlers.indexOf(listener) < 0) { handlers.push(listener); }
			return;
		}
		return add.apply(this, arguments);
	};
	EventTarget.prototype.removeEventListener = function (type, listener) {
		if (this === window && type === "unload") {
			var i = handlers.indexOf(listener);
			if (i >= 0) { handlers.splice(i, 1); }
			return;
		}
		return remove.apply(this, arguments);
	};
	window.__lunacyUnload = function () {
		if (ran) { return; }
		ran = true;
		var e = document.createEvent("Event");
		e.initEvent("unload", false, false);
		var list = handlers.slice(0);
		for (var i = 0; i < list.length; i++) {
			try {
				var h = list[i];
				if (typeof h === "function") { h.call(window, e); } else if (h && typeof h.handleEvent === "function") { h.handleEvent(e); }
			} catch (err) { console.error("unload handler: " + (err && err.stack || err)); }
		}
		// window.onunload too, once: an engine that still fires it natively finds it gone.
		var on = window.onunload;
		window.onunload = null;
		if (typeof on === "function") { try { on.call(window, e); } catch (err) { console.error("onunload: " + (err && err.stack || err)); } }
	};
	add.call(window, "pagehide", function () { window.__lunacyUnload(); });
})();
