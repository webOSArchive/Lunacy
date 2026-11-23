// Lunacy's compat layer: a TV remote in a card (experimental, branch android-tv). ES5 only: must
// run on Chromium 37.
//
// webOS had no remote and no focus: every control was a finger's. Enyo's and Mojo's buttons,
// list rows and menu items are plain elements the engine can't focus, so a remote's D-pad moved
// nothing (measured on a Chromecast with Google TV: the arrows reach the page as keydowns and
// focus stays on the body). Here the shell hands the D-pad's moves to move(), which keeps one
// control "focused" and shows it in the framework's own pressed state, as a finger held on it
// would (codepoet, 2026-11-23): Enyo's down state for its buttons, its held state for the rest,
// and Mojo's selected class for anything that carries its tap highlight. OK is a real tap: the
// shell asks point() where the focused control is and sends a finger's touch there, so the tap
// goes through input.js like any other and the frameworks see nothing new.
(function () {
	if (window.top !== window) { return; }
	var current = null;      // the focused control's element
	var undo = null;         // takes its pressed state off again
	var lastRect = null;     // where it was, for a control that has gone
	var SCROLL_SHARE = 0.6;  // how far a scroller moves when nothing is left in that direction
	var MARGIN = 8;          // a revealed control's distance from its scroller's edge

	/**
	 * The Enyo control [n] belongs to. A list's rows are flyweights: one control draws every row,
	 * and each row's node carries its id, as Enyo's dispatcher finds them (findDispatchTarget).
	 */
	function enyoOf(n) {
		var E = window.enyo;
		return E && E.$ && n.id ? E.$[n.id] || null : null;
	}
	/** The Enyo control whose own node [n] is (Enyo finds a control's node only when asked: hasNode). */
	function enyoControl(n) {
		var c = enyoOf(n);
		return c && c.hasNode && c.hasNode() === n ? c : null;
	}
	function isKind(c, name) {
		var k = window.enyo && enyo[name];
		return !!(k && c instanceof k);
	}
	/** Whether an Enyo control answers a tap: it names a click handler, or is a kind built for one. */
	function enyoTappable(c) {
		if (c.disabled) { return false; }
		if (c.onclick || c.onmousedown || c.onmouseup) { return true; }
		// An Item is a control only when it says so (a handler, or the tap highlight a list's row
		// asks for); a settings row is an Item too, a frame for the control inside it.
		if (isKind(c, "Item")) { return !!c.tapHighlight; }
		return isKind(c, "CustomButton") || isKind(c, "ToggleButton") ||
			isKind(c, "CustomListSelector") || isKind(c, "MenuItem");
	}
	function mojoTappable(n) {
		return n.hasAttribute("x-mojo-tap-highlight") || n.hasAttribute("x-mojo-touch-feedback");
	}
	function field(n) {
		return /^(INPUT|TEXTAREA|SELECT)$/.test(n.tagName) || n.isContentEditable;
	}
	function tappable(n) {
		if (n.disabled) { return false; }
		if (n.tagName === "INPUT" && n.type === "hidden") { return false; }
		if (field(n) || n.tagName === "BUTTON" || (n.tagName === "A" && n.hasAttribute("href"))) { return true; }
		if (n.hasAttribute("onclick") || mojoTappable(n)) { return true; }
		var c = enyoOf(n);
		return !!(c && enyoTappable(c));
	}

	function viewport() { return { left: 0, top: 0, right: window.innerWidth, bottom: window.innerHeight }; }
	function clip(a, b) {
		var r = { left: Math.max(a.left, b.left), top: Math.max(a.top, b.top),
			right: Math.min(a.right, b.right), bottom: Math.min(a.bottom, b.bottom) };
		return r.right > r.left && r.bottom > r.top ? r : null;
	}
	/** Whether [n] can be seen and reached: on the screen, and not under something else (a scrim, a header). */
	function reachable(n) {
		var r = n.getBoundingClientRect();
		if (r.width < 2 || r.height < 2) { return false; }
		var s = getComputedStyle(n);
		if (s.visibility === "hidden" || s.pointerEvents === "none") { return false; }
		var v = clip(r, viewport());
		if (!v) { return false; }
		var hit = document.elementFromPoint((v.left + v.right) / 2, (v.top + v.bottom) / 2);
		return !!hit && (hit === n || n.contains(hit));
	}
	/** The controls a remote can reach now, outermost first where one is the whole of another. */
	function candidates() {
		var sel = "a[href],button,input,textarea,select,[contenteditable],[onclick],[x-mojo-tap-highlight],[x-mojo-touch-feedback]";
		if (window.enyo && enyo.$) { sel += ",[id]"; }
		var all = document.querySelectorAll(sel), out = [];
		for (var i = 0; i < all.length; i++) {
			var n = all[i];
			if (!tappable(n) || !reachable(n)) { continue; }
			// A control filling its tappable parent (a button's caption, a menu item's row) is the same target.
			var same = false;
			for (var j = out.length - 1; j >= 0; j--) {
				if (out[j].contains(n)) {
					var a = out[j].getBoundingClientRect(), b = n.getBoundingClientRect();
					same = b.width * b.height >= 0.8 * a.width * a.height;
					break;
				}
			}
			if (!same) { out.push(n); }
		}
		return out;
	}

	/** The candidate nearest [from] in [dir], or null. */
	function nearest(dir, from, list) {
		var best = null, bestScore = Infinity;
		var fx = (from.left + from.right) / 2, fy = (from.top + from.bottom) / 2;
		for (var i = 0; i < list.length; i++) {
			var n = list[i];
			if (n === current) { continue; }
			var r = n.getBoundingClientRect(), cx = (r.left + r.right) / 2, cy = (r.top + r.bottom) / 2;
			var primary, cross, off;
			// One inside the other (a row and its switch): their edges say nothing, their centres do.
			if (current && (current.contains(n) || n.contains(current))) {
				var dx = cx - fx, dy = cy - fy;
				var along = dir === "down" ? dy : dir === "up" ? -dy : dir === "right" ? dx : -dx;
				var side = dir === "down" || dir === "up" ? Math.abs(dx) : Math.abs(dy);
				if (along <= 1 || side > along * 2 + 40) { continue; }
				var inner = along + 0.5 * side;
				if (inner < bestScore) { bestScore = inner; best = n; }
				continue;
			}
			if (dir === "down") { if (cy <= fy + 1 || r.bottom <= from.bottom) { continue; } primary = r.top - from.bottom; cross = Math.max(0, from.left - r.right, r.left - from.right); off = Math.abs(cx - fx); }
			else if (dir === "up") { if (cy >= fy - 1 || r.top >= from.top) { continue; } primary = from.top - r.bottom; cross = Math.max(0, from.left - r.right, r.left - from.right); off = Math.abs(cx - fx); }
			else if (dir === "right") { if (cx <= fx + 1 || r.right <= from.right) { continue; } primary = r.left - from.right; cross = Math.max(0, from.top - r.bottom, r.top - from.bottom); off = Math.abs(cy - fy); }
			else { if (cx >= fx - 1 || r.left >= from.left) { continue; } primary = from.left - r.right; cross = Math.max(0, from.top - r.bottom, r.top - from.bottom); off = Math.abs(cy - fy); }
			var score = Math.max(0, primary) + 2 * cross + 0.05 * off;
			if (score < bestScore) { bestScore = score; best = n; }
		}
		return best;
	}
	/** The candidate closest to [rect]: where focus goes back to when its control went (a page changed under it). */
	function closestTo(rect, list) {
		var cx = (rect.left + rect.right) / 2, cy = (rect.top + rect.bottom) / 2, best = null, d = Infinity;
		for (var i = 0; i < list.length; i++) {
			var r = list[i].getBoundingClientRect();
			var dd = Math.abs((r.left + r.right) / 2 - cx) + Math.abs((r.top + r.bottom) / 2 - cy);
			if (dd < d) { d = dd; best = list[i]; }
		}
		return best;
	}
	/** Where focus starts: the control nearest the top left. */
	function first(list) {
		var best = null, bestScore = Infinity;
		for (var i = 0; i < list.length; i++) {
			var r = list[i].getBoundingClientRect(), score = r.top * 2 + r.left;
			if (score < bestScore) { bestScore = score; best = list[i]; }
		}
		return best;
	}

	// ---- the pressed state ----

	var LOOK = ["backgroundImage", "backgroundColor", "borderTopColor", "color", "boxShadow", "opacity", "webkitBorderImage", "borderImageSource"];
	function look(n) {
		var s = getComputedStyle(n), out = [];
		for (var i = 0; i < LOOK.length; i++) { out.push(s[LOOK[i]]); }
		var c = n.firstElementChild;
		if (c) { s = getComputedStyle(c); for (var j = 0; j < LOOK.length; j++) { out.push(s[LOOK[j]]); } }
		return out.join("|");
	}
	function withClass(n, cls) {
		if ((" " + n.className + " ").indexOf(" " + cls + " ") >= 0) { return null; }
		n.className += " " + cls;
		return function () { n.className = (" " + n.className + " ").replace(" " + cls + " ", " ").replace(/^ +| +$/g, ""); };
	}
	// Enyo's held gradient (Item.css's .enyo-held): what a control with no pressed look of its own shows.
	var HELD = "-webkit-gradient(linear, left top, left bottom, from(rgba(218,235,251,0.4)), to(rgba(197,224,249,0.4)))";
	function press(n) {
		var before = look(n), off = null;
		var c = enyoControl(n);
		if (c && isKind(c, "CustomButton") && c.setDown) {
			c.setDown(true);
			off = function () { if (!c.destroyed) { c.setDown(false); } };
		} else if (window.enyo && !mojoTappable(n)) {
			off = withClass(n, "enyo-held");
		} else if (window.Mojo && mojoTappable(n)) {
			off = withClass(n, (Mojo.Gesture && Mojo.Gesture.kSelectedClassName) || "selected");
		}
		if (look(n) !== before) { return off; }
		// The framework's state draws nothing on this one: Enyo's held look, laid on the element.
		if (off) { off(); }
		// Over its own image, which stays (a slider's knob is one).
		var was = n.style.backgroundImage, own = getComputedStyle(n).backgroundImage;
		n.style.backgroundImage = own && own !== "none" ? HELD + ", " + own : HELD;
		return function () { n.style.backgroundImage = was; };
	}
	function focus(n) {
		if (undo) { undo(); undo = null; }
		current = n;
		if (n) { undo = press(n); lastRect = n.getBoundingClientRect(); reveal(n); }
	}

	// ---- scrolling ----

	/** The scroller [n] is in: an Enyo scroller, Mojo's, or an element that scrolls itself. */
	function scrollerOf(n) {
		for (var p = n && n.parentNode; p && p.nodeType === 1; p = p.parentNode) {
			var c = enyoControl(p);
			if (c && c.$ && c.$.scroll && isKind(c, "DragScroller")) { return { enyo: c, node: p }; }
			if (p.mojo && p.mojo.getScrollPosition && p.getAttribute("x-mojo-element") === "Scroller") { return { mojo: p.mojo, node: p }; }
			var s = getComputedStyle(p);
			if (/(auto|scroll)/.test(s.overflowY + s.overflowX) && (p.scrollHeight > p.clientHeight + 1 || p.scrollWidth > p.clientWidth + 1)) { return { node: p }; }
		}
		return null;
	}
	/** Moves [sc]'s content by dx, dy (positive: what is further down or right comes into view). */
	function scrollBy(sc, dx, dy) {
		if (sc.enyo) {
			// As enyo.BasicScroller.scrollTo starts its animation: from where it is, by the distance.
			var s = sc.enyo.$.scroll, k = 1 - (s.kFrictionDamping || 0);
			if (dy) { s.y = s.y0 - dy * k; }
			if (dx) { s.x = s.x0 - dx * k; }
			s.start();
		} else if (sc.mojo) {
			var pos = sc.mojo.getScrollPosition();
			sc.mojo.scrollTo(pos.left - dx, pos.top - dy, true);
		} else {
			sc.node.scrollTop += dy; sc.node.scrollLeft += dx;
		}
	}
	/** Brings [n] wholly into its scroller's view, if it is cut off at an edge. */
	function reveal(n) {
		var sc = scrollerOf(n);
		if (!sc) { return; }
		var box = clip(sc.node.getBoundingClientRect(), viewport()), r = n.getBoundingClientRect();
		if (!box) { return; }
		var dy = 0, dx = 0;
		if (r.bottom > box.bottom) { dy = Math.min(r.bottom - box.bottom + MARGIN, r.top - box.top); }
		else if (r.top < box.top) { dy = r.top - box.top - MARGIN; }
		if (r.right > box.right) { dx = Math.min(r.right - box.right + MARGIN, r.left - box.left); }
		else if (r.left < box.left) { dx = r.left - box.left - MARGIN; }
		if (dx || dy) { scrollBy(sc, dx, dy); }
	}
	/** Nothing left in [dir]: the scroller under the focus (or the screen's middle) moves on by part of a screen. */
	function scrollOn(dir) {
		var at = current || document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
		var sc = scrollerOf(at);
		if (!sc) { return false; }
		var box = clip(sc.node.getBoundingClientRect(), viewport()) || viewport();
		var h = (box.bottom - box.top) * SCROLL_SHARE, w = (box.right - box.left) * SCROLL_SHARE;
		scrollBy(sc, dir === "right" ? w : dir === "left" ? -w : 0, dir === "down" ? h : dir === "up" ? -h : 0);
		return true;
	}

	// ---- what the shell calls ----

	function alive(n) { return !!n && document.documentElement.contains(n) && reachable(n); }
	/** A text field keeps left and right for its caret, until the caret is at its end. */
	function caret(dir) {
		var n = current;
		if (!n || document.activeElement !== n || !/^(INPUT|TEXTAREA)$/.test(n.tagName)) { return false; }
		if (dir !== "left" && dir !== "right") { return false; }
		var at;
		try { at = n.selectionStart; } catch (e) { return false; }
		if (typeof at !== "number") { return false; }
		var to = dir === "left" ? at - 1 : at + 1;
		if (to < 0 || to > n.value.length) { return false; }
		n.setSelectionRange(to, to);
		return true;
	}
	window.__lunacyRemote = {
		/** A D-pad press: "up", "down", "left" or "right". */
		move: function (dir) {
			if (caret(dir)) { return; }
			var list = candidates();
			if (!alive(current)) {
				// Nothing focused yet starts at the top left; a control that went hands on to what is
				// now where it was, and this press only shows it.
				focus(lastRect ? closestTo(lastRect, list) : first(list));
				return;
			}
			var next = nearest(dir, current.getBoundingClientRect(), list);
			if (next) {
				if (document.activeElement === current && field(current)) { current.blur(); }
				focus(next);
				return;
			}
			if (scrollOn(dir)) {
				// The scroller animates; once it has, whatever came into view is in reach.
				setTimeout(function () {
					if (!alive(current)) { return; }
					var later = nearest(dir, current.getBoundingClientRect(), candidates());
					if (later) { focus(later); }
				}, 400);
			}
		},
		/**
		 * Where OK taps, as fractions of the page's width and height ("x,y"), or null: with
		 * nothing focused, OK focuses the first control instead, as an arrow would.
		 */
		point: function () {
			if (!alive(current)) { focus(first(candidates())); return null; }
			var v = clip(current.getBoundingClientRect(), viewport());
			if (!v) { return null; }
			// The pressed state comes off first: the tap's own mousedown puts the framework's back.
			if (undo) { undo(); undo = null; }
			return ((v.left + v.right) / 2 / window.innerWidth) + "," + ((v.top + v.bottom) / 2 / window.innerHeight);
		},
		/**
		 * The tap is over: the focus shows again on the control, or, if the tap took it away (a
		 * page or a popup changed), on what is now closest to where it was, once the frameworks'
		 * own transitions have settled (Enyo's panes slide for about half a second).
		 */
		tapped: function () {
			var mine = current, at = lastRect, picked = false;
			function settle(last) {
				if (current !== mine) { return; }   // the D-pad has moved it since
				// A control picked mid-slide is picked again once the page is still.
				if (alive(current) && !(last && picked)) { if (!undo) { undo = press(current); } }
				else if (at) {
					var to = closestTo(at, candidates());
					if (to && to !== current) { focus(to); lastRect = at; mine = current; picked = true; }
					else if (to && !undo) { undo = press(to); }
				}
				if (!last) { setTimeout(function () { settle(true); }, 650); }
			}
			setTimeout(function () { settle(false); }, 350);
		}
	};
})();
