// Lunacy's compat layer, part 2 of 4 (see input.js): what the TouchPad's 2011 engine did that
// Chromium doesn't - border images without a border style, uncaught errors kept off the page,
// callable collections, no service workers, the old animation-frame name, webosEvent, and
// -webkit-box's static positions and edge margins. ES5 only.
// A border-image is painted only where a border is. The reference TouchPad's WebKit (534.6)
// drew the nine-piece image from paintBorder(), which it called only when
// RenderStyle::hasBorder() held - a border width above zero on some side. Chromium paints a
// border-image whenever there is one, fill and all. So an element that turns its border off
// with `border-width: 0` while a stylesheet still gives it a border-image shows the image's
// middle as a box the TouchPad never drew: First Use's and webOS Account's Start Over button,
// Enyo's `.enyo-button` art under the app's `.restart { border-width: 0; background: none }`
// (codepoet, 2026-10-03, on the Pixel Tablet). Done here for every page: elements matching
// a rule that declares a border-image are checked, and one with every border width at zero
// gets `border-image-source: none` inline until its widths change. Chromium 37 has no
// Element.matches; MutationObserver it has.
(function () {
	var MARK = "data-lunacy-border-image";
	function matches(el, sel) {
		var f = el.matches || el.webkitMatchesSelector;
		try { return f.call(el, sel); } catch (e) { return false; }
	}
	function selectors() {
		var out = [], sheets = document.styleSheets, i, j, rules, r, st;
		for (i = 0; i < sheets.length; i++) {
			try { rules = sheets[i].cssRules; } catch (e) { rules = null; }
			if (!rules) { continue; }
			for (j = 0; j < rules.length; j++) {
				r = rules[j]; st = r.style;
				if (!st || !r.selectorText) { continue; }
				if (st.getPropertyValue("border-image-source") || st.getPropertyValue("-webkit-border-image") || st.getPropertyValue("border-image")) {
					out.push(r.selectorText);
				}
			}
		}
		return out;
	}
	function check(el) {
		var cs = getComputedStyle(el);
		if (!cs) { return; }
		var zero = cs.borderTopWidth === "0px" && cs.borderRightWidth === "0px" && cs.borderBottomWidth === "0px" && cs.borderLeftWidth === "0px";
		var marked = el.hasAttribute(MARK);
		if (zero && !marked) {
			if (cs.borderImageSource === "none" || !cs.borderImageSource) { return; }
			el.style.setProperty("border-image-source", "none");
			el.setAttribute(MARK, "");
		} else if (!zero && marked) {
			el.style.removeProperty("border-image-source");
			el.removeAttribute(MARK);
		}
	}
	function pass(roots) {
		var sels = selectors();
		if (!sels.length) { return; }
		var sel = sels.join(","), i, k, found, seen = [];
		for (i = 0; i < roots.length; i++) {
			var root = roots[i];
			if (root.nodeType !== 1) { continue; }
			if (matches(root, sel) && seen.indexOf(root) < 0) { seen.push(root); check(root); }
			try { found = root.querySelectorAll(sel); } catch (e) { found = []; }
			for (k = 0; k < found.length; k++) { if (seen.indexOf(found[k]) < 0) { seen.push(found[k]); check(found[k]); } }
		}
	}
	var pending = null;
	function schedule(roots) {
		if (!pending) { pending = []; }
		// The whole document replaces the list, but not the pass already queued for it.
		if (roots === document) { var queued = pending.scheduled; pending = [document.documentElement]; pending.scheduled = queued; }
		else if (pending.length !== 1 || pending[0] !== document.documentElement) {
			for (var i = 0; i < roots.length; i++) { if (pending.indexOf(roots[i]) < 0) { pending.push(roots[i]); } }
		}
		if (pending.length === 1 && pending[0] === document.documentElement && pending.scheduled) { return; }
		if (!pending.scheduled) {
			pending.scheduled = true;
			setTimeout(function () { var p = pending; pending = null; if (p) { pass(p); } }, 0);
		}
	}
	function start() {
		schedule(document);
		new MutationObserver(function (records) {
			var roots = [], i, j, rec;
			for (i = 0; i < records.length; i++) {
				rec = records[i];
				if (rec.type === "attributes") {
					if (rec.attributeName === MARK) { continue; }
					roots.push(rec.target);
				} else if (rec.type === "childList") {
					for (j = 0; j < rec.addedNodes.length; j++) { roots.push(rec.addedNodes[j]); }
					// A stylesheet arriving (Enyo loads its CSS by script) can give existing elements a border-image.
					for (j = 0; j < rec.addedNodes.length; j++) {
						var n = rec.addedNodes[j];
						if (n.nodeType === 1 && (n.tagName === "LINK" || n.tagName === "STYLE")) { schedule(document); return; }
					}
				}
			}
			if (roots.length) { schedule(roots); }
		}).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "style"] });
		// Stylesheets load after the DOM they style; look again when they have.
		window.addEventListener("load", function () { schedule(document); });
	}
	if (document.readyState === "loading") { document.addEventListener("DOMContentLoaded", start); } else { start(); }
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

// Collections are callable, as they were in the reference TouchPad's WebKit (534.6): there
// coll(i) was coll[i], a numeric string counted as an index, an index out of range gave null,
// and a name found the element by id (Workbench/probe jsprobe 0.0.2: children, cells, rows,
// tBodies, childNodes, getElementsByTagName's results and document.forms all answered).
// Quick Office's spreadsheet grid calls table.children(i) and row.cells(i), and Chromium threw
// "is not a function" before a cell was drawn. children, cells, rows and tBodies come back as
// a Proxy over the live collection that can also be called; everything else it answers from
// the collection itself, its prototype and its toString tag included, so a framework's type
// check still sees an HTMLCollection (typeof says "function" where the TouchPad said "object":
// Docs/luna-deltas.md). A new one at each read, as the TouchPad gave a new collection each
// time. childNodes and the getElementsBy* results, which frameworks walk in hot loops, are left
// as they are. An engine without Proxy (Chromium 37) keeps its own collections.
(function () {
	if (typeof Proxy !== "function" || typeof Reflect !== "object") { return; }
	function callable(coll) {
		var target = (function () {}).bind(null);
		return new Proxy(target, {
			apply: function (t, self, args) {
				var k = args.length ? args[0] : 0, v;
				if (typeof k === "number" || /^\d+$/.test(String(k))) { v = coll[Number(k)]; return v === undefined ? null : v; }
				return coll.namedItem ? coll.namedItem(String(k)) : null;
			},
			get: function (t, name) { var v = coll[name]; return typeof v === "function" ? v.bind(coll) : v; },
			set: function (t, name, v) { coll[name] = v; return true; },
			has: function (t, name) { return name in coll; },
			ownKeys: function () { return Reflect.ownKeys(coll); },
			getOwnPropertyDescriptor: function (t, name) {
				var d = Reflect.getOwnPropertyDescriptor(coll, name);
				if (d) { d.configurable = true; }
				return d;
			},
			getPrototypeOf: function () { return Object.getPrototypeOf(coll); }
		});
	}
	[[window.Element, "children"], [window.HTMLTableRowElement, "cells"], [window.HTMLTableElement, "rows"],
	 [window.HTMLTableElement, "tBodies"], [window.HTMLTableSectionElement, "rows"]].forEach(function (p) {
		var proto = p[0] && p[0].prototype, d = proto && Object.getOwnPropertyDescriptor(proto, p[1]);
		if (!d || !d.get || !d.configurable) { return; }
		Object.defineProperty(proto, p[1], {
			configurable: true, enumerable: d.enumerable,
			get: function () { var c = d.get.call(this); return c ? callable(c) : c; }
		});
	});
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

// webosEvent: the TouchPad's browser gave every page this profiling hook, and apps mark their
// timings with it (Photos & Videos, opening a picture). Enyo stubs it only where there is no
// PalmSystem. Measured on the reference TouchPad: an object with nothing enumerable, whose
// start, stop and event take anything and return undefined.
(function () {
	if (window.webosEvent) { return; }
	var hook = {};
	["start", "stop", "event"].forEach(function (n) {
		Object.defineProperty(hook, n, { value: function () {}, writable: true, configurable: true });
	});
	window.webosEvent = hook;
})();

// An absolutely positioned child of a -webkit-box that names no offsets sits where it would
// have started in flow. 2011 WebKit's box layout (RenderDeprecatedFlexibleBox) set that static
// position as it walked the children, before packing moved the others: the start of the box's
// content, past the in-flow children before it, at the top (or, in a vertical box, the left).
// Current Chromium lays -webkit-box out as a flexbox, where the static position follows the
// box's packing and alignment, so Photos & Videos' grab handle, an image in a centred toolbar,
// drifted to the middle of it (2026-10-08, measured against the TouchPad). Such an element
// gets that position as offsets, kept in step as the layout changes; one whose offsets a
// stylesheet sets, or its app sets afterwards, is left alone. Candidates are found by their
// inline style, which is how apps and Enyo give a component its position.
(function () {
	if (!window.MutationObserver || !window.requestAnimationFrame) { return; }
	var placed = [], queued = false;
	function px(v) { return parseFloat(v) || 0; }
	function unpositioned(e) {
		var s = e.style;
		return s && (s.position === "absolute" || s.position === "fixed") && !s.left && !s.right && !s.top && !s.bottom;
	}
	// Whether a stylesheet gives it offsets: it moves when they are forced back to auto.
	function styledElsewhere(e) {
		var before = e.getBoundingClientRect(), s = e.style, sides = ["left", "right", "top", "bottom"];
		sides.forEach(function (n) { s.setProperty(n, "auto", "important"); });
		var after = e.getBoundingClientRect();
		sides.forEach(function (n) { s.removeProperty(n); });
		return before.left !== after.left || before.top !== after.top;
	}
	function place(e) {
		var p = e.parentElement;
		if (!p) { return false; }
		var ps = getComputedStyle(p);
		if (ps.display !== "-webkit-box" && ps.display !== "-webkit-inline-box") { return false; }
		var vertical = ps.webkitBoxOrient === "vertical" || ps.webkitBoxOrient === "block-axis";
		var pr = p.getBoundingClientRect(), es = getComputedStyle(e), er = e.getBoundingClientRect();
		var x = pr.left + px(ps.borderLeftWidth) + px(ps.paddingLeft), y = pr.top + px(ps.borderTopWidth) + px(ps.paddingTop);
		for (var c = p.firstElementChild; c && c !== e; c = c.nextElementSibling) {
			var cs = getComputedStyle(c);
			if (cs.position === "absolute" || cs.position === "fixed" || cs.display === "none") { continue; }
			var r = c.getBoundingClientRect();
			if (vertical) { y += r.height + px(cs.marginTop) + px(cs.marginBottom); }
			else { x += r.width + px(cs.marginLeft) + px(cs.marginRight); }
		}
		var left = Math.round(px(es.left) + x + px(es.marginLeft) - er.left) + "px";
		var top = Math.round(px(es.top) + y + px(es.marginTop) - er.top) + "px";
		if (e.style.left !== left) { e.style.left = left; }
		if (e.style.top !== top) { e.style.top = top; }
		e.__lunacyStatic = { left: left, top: top };
		return true;
	}
	function run() {
		queued = false;
		placed = placed.filter(function (e) {
			var mine = e.__lunacyStatic;
			// Gone, or its app has since positioned it.
			if (!document.contains(e) || e.style.left !== mine.left || e.style.top !== mine.top || e.style.right || e.style.bottom) { return false; }
			// Only a change is written, so a layout at rest stays quiet.
			return place(e);
		});
		var found = document.querySelectorAll('[style*="absolute"],[style*="fixed"]');
		for (var i = 0; i < found.length; i++) {
			var e = found[i];
			if (e.__lunacyStatic || !unpositioned(e)) { continue; }
			var box = e.parentElement && getComputedStyle(e.parentElement).display;
			if (box !== "-webkit-box" && box !== "-webkit-inline-box") { continue; }
			if (styledElsewhere(e)) { e.__lunacyStatic = { left: null, top: null }; continue; }
			if (place(e)) { placed.push(e); }
		}
	}
	function queue() { if (!queued) { queued = true; requestAnimationFrame(run); } }
	function start() {
		new MutationObserver(queue).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["style", "class"] });
		window.addEventListener("resize", queue);
		window.addEventListener("load", queue);
		queue();
	}
	if (document.documentElement) { start(); } else { document.addEventListener("DOMContentLoaded", start); }
})();

// A margin at the edge of a -webkit-box's child is dropped. 2011 WebKit (534.6, the reference
// TouchPad) discarded a margin that would collapse out through the top or bottom of a box's
// child with no padding or border there: the child is as tall as its content, the content at
// its edge. Chromium makes each child its own formatting context, so the margin stays inside
// and the child grows by it. Measured with Workbench/probe's enyoprobe 0.0.2 (2026-10-09):
// TouchPad and Lunacy, horizontal and vertical boxes, a top margin alone, a margin two blocks
// down, a child of fixed height - the margin is gone on the device in every case and kept here;
// a child with padding, and an inline-block, keep theirs on both. The Web app's action bar is
// the case people see: its buttons' clients have 3 px margins, so in Chromium the bar's row grew
// 6 px and its load progress sat under it with a gap (luna-deltas A12). Such a margin is set
// to 0 inline here, and given back if the child gains padding or the element moves.
// Each zeroed margin remembers the box child it was zeroed for, and only a pass that looks at
// that child again can give it back: a pass reaching only part of the way up the tree once
// gave back a margin that only a box further up could take away again, and the next frame's
// pass took it away - on every frame a scroller's content moved, so World Today's rows, each
// a scroller with a 2 px top margin, shook up and down by 2 px as they scrolled (2026-10-14).
(function () {
	if (!window.MutationObserver || !window.requestAnimationFrame) { return; }
	var zeroed = [], dirty = [], all = true, queued = false;
	var SIDE = { top: ["Top", "marginTop", "margin-top"], bottom: ["Bottom", "marginBottom", "margin-bottom"] };
	function box(e) {
		if (!e || e.nodeType !== 1) { return false; }
		var d = getComputedStyle(e).display;
		return d === "-webkit-box" || d === "-webkit-inline-box";
	}
	// The block at a child's top (or bottom) edge whose margin would collapse through it.
	function edgeChild(e, last) {
		for (var n = last ? e.lastChild : e.firstChild; n; n = last ? n.previousSibling : n.nextSibling) {
			if (n.nodeType === 3) { if (/\S/.test(n.nodeValue)) { return null; } continue; }
			if (n.nodeType !== 1) { continue; }
			var s = getComputedStyle(n);
			if (s.display === "none" || s.position === "absolute" || s.position === "fixed" || s.float !== "none") { continue; }
			return s.display === "block" || s.display === "list-item" ? n : null;
		}
		return null;
	}
	function closedEdge(s, side) {
		return (parseFloat(s["padding" + SIDE[side][0]]) || 0) > 0 || (parseFloat(s["border" + SIDE[side][0] + "Width"]) || 0) > 0;
	}
	// The blocks whose margins collapse out through a box child's edge, each with that child.
	function chain(kid, side, out) {
		// A child that is a box itself lays its own children out, margins and all: nothing
		// collapses out through it (World Today's first row kept its 2 px gap only by luck).
		if (box(kid)) { return; }
		var s = getComputedStyle(kid);
		// Nor through one that clips: overflow holds a margin in on the TouchPad too (Topaz
		// Explorer's tree arrows, centred in their rows by a top margin, 2026-11-01).
		if (closedEdge(s, side) || s.overflow !== "visible") { return; }
		for (var c = edgeChild(kid, side === "bottom"); c; c = edgeChild(c, side === "bottom")) {
			var cs = getComputedStyle(c);
			out.push([c, side, kid]);
			if (closedEdge(cs, side) || cs.overflow !== "visible") { return; }
		}
	}
	function wanted(kids) {
		var out = [];
		kids.forEach(function (k) { chain(k, "top", out); chain(k, "bottom", out); });
		return out;
	}
	function has(list, e, side) {
		for (var i = 0; i < list.length; i++) { if (list[i][0] === e && list[i][1] === side) { return i; } }
		return -1;
	}
	function zero(e, side, kid) {
		var prop = SIDE[side][2];
		zeroed.push([e, side, e.style.getPropertyValue(prop), e.style.getPropertyPriority(prop), kid]);
		e.style.setProperty(prop, "0px", "important");
	}
	function restore(z) {
		var prop = SIDE[z[1]][2];
		if (z[2]) { z[0].style.setProperty(prop, z[2], z[3]); } else { z[0].style.removeProperty(prop); }
	}
	function kidsOf(parents) {
		var kids = [];
		parents.forEach(function (p) {
			if (!box(p)) { return; }
			for (var c = p.firstElementChild; c; c = c.nextElementSibling) { if (kids.indexOf(c) < 0) { kids.push(c); } }
		});
		return kids;
	}
	function run() {
		queued = false;
		var parents;
		if (all) {
			all = false;
			parents = [].slice.call(document.getElementsByTagName("*"));
		} else {
			parents = [];
			dirty.forEach(function (e) {
				if (!document.documentElement.contains(e)) { return; }
				for (var p = e, n = 0; p && p.nodeType === 1 && n < 4; p = p.parentNode, n++) { if (parents.indexOf(p) < 0) { parents.push(p); } }
				if (e.getElementsByTagName) { [].forEach.call(e.getElementsByTagName("*"), function (d) { parents.push(d); }); }
			});
		}
		dirty = [];
		var kids = kidsOf(parents);
		var want = wanted(kids);
		// Given back: a margin whose element has gone, or whose child was looked at again and
		// no longer drops it. One zeroed for a child this pass didn't reach stays as it is.
		var stale = zeroed.filter(function (z) {
			return !document.documentElement.contains(z[0]) || (kids.indexOf(z[4]) >= 0 && has(want, z[0], z[1]) < 0);
		});
		stale.forEach(function (z) { restore(z); zeroed.splice(zeroed.indexOf(z), 1); });
		want.forEach(function (w) {
			if (has(zeroed, w[0], w[1]) >= 0) { return; }
			if ((parseFloat(getComputedStyle(w[0])[SIDE[w[1]][1]]) || 0) !== 0) { zero(w[0], w[1], w[2]); }
		});
	}
	function queue() { if (!queued) { queued = true; requestAnimationFrame(run); } }
	function start() {
		new MutationObserver(function (records) {
			for (var i = 0; i < records.length; i++) {
				var r = records[i];
				// Its own margins changing are this pass's doing.
				if (r.type === "attributes" && r.attributeName === "style" && has(zeroed, r.target, "top") + has(zeroed, r.target, "bottom") > -2) { continue; }
				dirty.push(r.target);
				for (var j = 0; r.addedNodes && j < r.addedNodes.length; j++) { if (r.addedNodes[j].nodeType === 1) { dirty.push(r.addedNodes[j]); } }
			}
			if (dirty.length) { queue(); }
		}).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["style", "class"] });
		window.addEventListener("load", function () { all = true; queue(); });
		queue();
	}
	if (document.documentElement) { start(); } else { document.addEventListener("DOMContentLoaded", start); }
})();
