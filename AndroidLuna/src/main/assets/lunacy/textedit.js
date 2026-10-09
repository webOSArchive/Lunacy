// Text editing as webOS 3 did it: the caret, holding in a field, selection and its popup.
// ES5 only: must run on Chromium 37.
//
// The compat layer turns every touch into webOS's mouse events and cancels the touch itself,
// so the engine never places a caret, selects a word or shows its own selection UI. On the
// TouchPad the system did those things for every editable field, the same in Enyo and Mojo
// apps (measured on the reference TouchPad with codepoet, 2026-10-04):
//
// - A tap in a field puts the caret where the finger is.
// - Holding about a second in a field puts the caret there and shows a popup under it:
//   "Select | Select All", and "| Paste" when the clipboard has something.
// - Select selects the word at the caret, Select All the whole field, with a handle at each
//   end: a small grey pentagon above the start pointing down, one below the end pointing up.
// - A touch on a selection - a tap, or dragging a handle - shows "Cut | Copy" under it.
// - A double tap selects the word with no handles; a tap on it then gives them.
// - A tap anywhere else clears the selection and its popup, and moves the caret.
// - Text that isn't editable can't be selected in an app.
//
// The highlight is yellow at about 60%: #FEFE66 on white and (253,248,73) on Memos' paper.
// The popup is drawn here to the TouchPad's measurements, in TouchPad px (one CSS px here).
(function () {
	var HOLD_MS = 1000, DOUBLE_TAP_MS = 350, DOUBLE_TAP_PX = 30, HANDLE_REACH = 12;
	var N = window.LunacyNative;
	var ui = null, popup = null, startHandle = null, endHandle = null;
	var field = null;          // the field the UI is for
	var handles = false;       // whether the selection shows its handles
	var holdTimer = null, held = false, lastTap = null, drag = null, touchTarget = null;

	// ---- the look ----

	var CSS =
		"::selection{background-color:rgba(253,253,0,0.6)}" +
		".lunacy-edit{position:fixed;left:0;top:0;width:0;height:0;z-index:2147483647}" +
		".lunacy-edit-popup{position:fixed;display:none;white-space:nowrap;height:39px;" +
			"border:1px solid rgba(0,0,0,0.64);border-radius:8px;" +
			"background:-webkit-linear-gradient(top,#7a7c7e,#585a5d);" +
			"box-shadow:inset 0 1px 0 #aaabac,inset 0 -1px 0 #6e7072,0 2px 6px rgba(0,0,0,0.55);" +
			"font-family:Prelude,sans-serif;font-size:17px;color:#fff;-webkit-user-select:none}" +
		".lunacy-edit-item{display:inline-block;height:39px;line-height:39px;padding:0 21px;" +
			"box-shadow:inset 1px 0 0 #737578,inset 2px 0 0 #535557}" +
		".lunacy-edit-item:first-child{box-shadow:none}" +
		".lunacy-edit-item.down{background:rgba(0,0,0,0.25)}" +
		".lunacy-edit-arrow{position:absolute;top:-9px;width:17px;height:9px;margin-left:-8px}" +
		".lunacy-edit-handle{position:fixed;display:none;width:15px;height:12px;margin-left:-7px}" +
		".lunacy-edit-arrow svg,.lunacy-edit-handle svg{display:block}";
	// The pointer and the handles, as the TouchPad drew them.
	var ARROW = "<svg xmlns='http://www.w3.org/2000/svg' width='17' height='9'>" +
		"<path d='M0.5 9.5 L8.5 1 L16.5 9.5 Z' fill='#8d8f91' stroke='rgba(0,0,0,0.64)' stroke-width='1' stroke-linejoin='round'/>" +
		"<path d='M2 9.5 H15' stroke='#8d8f91' stroke-width='1.5'/></svg>";
	function handleSvg(down) {
		var p = down ? "M1 1.5 H14 V4.5 L7.5 11 L1 4.5 Z" : "M1 10.5 H14 V7.5 L7.5 1 L1 7.5 Z";
		return "<svg xmlns='http://www.w3.org/2000/svg' width='15' height='12'>" +
			"<path d='" + p + "' fill='#dfdfde' stroke='rgba(0,0,0,0.5)' stroke-width='1' stroke-linejoin='round'/></svg>";
	}

	// The popup's styles, as a link rather than a <style>: Mojo reads every sheet's href
	// (compat.js, __lunacyStyle). Added with the page, so they are in long before a popup.
	(function () {
		var s = document.createElement("link");
		s.rel = "stylesheet";
		s.href = "data:text/css," + encodeURIComponent(CSS);
		(document.head || document.documentElement).appendChild(s);
	})();

	function build() {
		if (ui || !document.body) { return; }
		ui = document.createElement("div");
		ui.className = "lunacy-edit";
		popup = document.createElement("div");
		popup.className = "lunacy-edit-popup";
		startHandle = document.createElement("div");
		startHandle.className = "lunacy-edit-handle";
		startHandle.innerHTML = handleSvg(true);
		endHandle = document.createElement("div");
		endHandle.className = "lunacy-edit-handle";
		endHandle.innerHTML = handleSvg(false);
		ui.appendChild(startHandle);
		ui.appendChild(endHandle);
		ui.appendChild(popup);
		document.body.appendChild(ui);
	}

	// ---- fields ----

	var TEXT_TYPES = /^(text|search|email|url|tel|password|number)?$/i;
	function editableOf(el) {
		for (; el && el.nodeType === 1; el = el.parentNode) {
			if (el.tagName === "TEXTAREA") { return el.readOnly || el.disabled ? null : el; }
			if (el.tagName === "INPUT") { return TEXT_TYPES.test(el.type) && !el.readOnly && !el.disabled ? el : null; }
			if (el.isContentEditable) {
				while (el.parentNode && el.parentNode.isContentEditable) { el = el.parentNode; }
				return el;
			}
		}
		return null;
	}
	function plain(f) { return f.tagName === "INPUT" || f.tagName === "TEXTAREA"; }
	// A field's selection, or null where it has none to give. WebView 44 throws on reading it from
	// an email or number input (Apollo's sign-in, 2026-10-09); later engines answer null, and the
	// TouchPad's WebKit gave one.
	function selStart(f) { try { return f.selectionStart; } catch (e) { return null; } }
	function selEnd(f) { try { return f.selectionEnd; } catch (e) { return null; } }

	// Chromium drops the caret at the start of a line in an editable box whose left edge
	// falls just past a device pixel, on a screen with a fractional pixel ratio: Email's
	// compose body (on the Galaxy Tab A7 Lite, 1.33) showed no caret on its empty first line,
	// though typing worked, and showed it again 0.25 px further right. Measured 2026-10-07:
	// line starts at 399.03 and 403.02 device px drew no caret; 400.36, 401.69 and 404.35 did.
	// The TouchPad drew it everywhere. So a focused editable box is moved by under half a
	// device pixel, putting its line starts mid-pixel; nothing on screen moves by a whole one.
	// Not for a box the page itself transforms.
	var gridded = null;
	function alignToGrid(f) {
		var ratio = window.devicePixelRatio || 1;
		if (ratio === Math.round(ratio) || plain(f)) { return; }
		if (gridded !== f) {
			if (gridded) { gridded.style.webkitTransform = ""; }
			gridded = null;
			if (getComputedStyle(f).webkitTransform !== "none") { return; }
		}
		f.style.webkitTransform = "";
		var cs = getComputedStyle(f);
		var left = (f.getBoundingClientRect().left + parseFloat(cs.borderLeftWidth) + parseFloat(cs.paddingLeft)) * ratio;
		var shift = 0.5 - (left - Math.floor(left));
		f.style.webkitTransform = "translateX(" + (shift / ratio) + "px)";
		gridded = f;
	}
	document.addEventListener("focusin", function (ev) {
		var f = editableOf(ev.target);
		if (f) { alignToGrid(f); }
	}, true);

	// An input or a textarea keeps its text where the page can't measure it, so a copy of it
	// is laid over it for a moment - same box, same font, same scroll - and measured instead.
	var PROPS = ["borderTopWidth", "borderRightWidth", "borderBottomWidth", "borderLeftWidth",
		"paddingTop", "paddingRight", "paddingBottom", "paddingLeft", "fontStyle", "fontVariant",
		"fontWeight", "fontSize", "fontFamily", "lineHeight", "textAlign", "textTransform",
		"textIndent", "letterSpacing", "wordSpacing", "direction"];
	function mirror(f) {
		var cs = getComputedStyle(f), r = f.getBoundingClientRect(), m = document.createElement("div"), i;
		for (i = 0; i < PROPS.length; i++) { m.style[PROPS[i]] = cs[PROPS[i]]; }
		var st = m.style;
		st.position = "fixed"; st.left = r.left + "px"; st.top = r.top + "px";
		st.width = r.width + "px"; st.height = r.height + "px"; st.boxSizing = "border-box";
		st.margin = "0"; st.borderStyle = "solid"; st.borderColor = "transparent";
		st.overflow = "hidden"; st.color = "transparent"; st.background = "transparent";
		st.opacity = "0"; st.zIndex = "2147483647"; st.pointerEvents = "auto";
		st.webkitUserSelect = "text";
		if (f.tagName === "INPUT") {
			// One line, centred in the box as an input centres it.
			st.whiteSpace = "pre"; st.wordWrap = "normal";
			st.lineHeight = Math.max(0, r.height - parseFloat(cs.borderTopWidth) - parseFloat(cs.borderBottomWidth) -
				parseFloat(cs.paddingTop) - parseFloat(cs.paddingBottom)) + "px";
		} else {
			st.whiteSpace = "pre-wrap"; st.wordWrap = "break-word";
		}
		var text = f.value;
		if (f.type === "password") { text = text.replace(/[\s\S]/g, "•"); }
		// A zero-width space gives the end of the text a box of its own.
		var t = document.createTextNode(text + "​");
		m.appendChild(t);
		document.body.appendChild(m);
		m.scrollTop = f.scrollTop;
		m.scrollLeft = f.scrollLeft;
		return { el: m, text: t, length: f.value.length };
	}
	function unmirror(m) { if (m.el.parentNode) { m.el.parentNode.removeChild(m.el); } }

	// The text offset under a point.
	function offsetAt(f, x, y) {
		var r;
		if (!plain(f)) {
			r = document.caretRangeFromPoint(x, y);
			return r && f.contains(r.startContainer) ? r : null;
		}
		var m = mirror(f), off = f.value.length;
		try {
			r = document.caretRangeFromPoint(x, y);
			if (r && r.startContainer === m.text) { off = Math.min(r.startOffset, m.length); }
			else if (r && r.startContainer === m.el) { off = r.startOffset ? m.length : 0; }
		} finally { unmirror(m); }
		return off;
	}

	// Where the selection is on the screen: its first and last line's boxes.
	function selectionBoxes(f) {
		var first = null, last = null, all = null, rs, i, rg;
		function take(list) {
			for (i = 0; i < list.length; i++) {
				var b = list[i];
				if (!b.width && !b.height) { continue; }
				if (!first) { first = b; }
				last = b;
			}
		}
		if (plain(f)) {
			var a = selStart(f), z = selEnd(f), m = mirror(f);
			try {
				rg = document.createRange();
				if (a === z) {
					// A caret: the box of the character after it (or the end's zero-width space).
					rg.setStart(m.text, a);
					rg.setEnd(m.text, a + 1);
					rs = rg.getClientRects();
					if (rs[0]) { first = last = { left: rs[0].left, right: rs[0].left, top: rs[0].top, bottom: rs[0].bottom }; }
				} else {
					rg.setStart(m.text, a);
					rg.setEnd(m.text, z);
					take(rg.getClientRects());
					all = rg.getBoundingClientRect();
				}
			} finally { unmirror(m); }
		} else {
			var sel = window.getSelection();
			if (!sel.rangeCount) { return null; }
			rg = sel.getRangeAt(0);
			take(rg.getClientRects());
			if (!first) {
				var b = rg.getBoundingClientRect();
				first = last = { left: b.left, right: b.left, top: b.top, bottom: b.bottom || b.top };
			}
			all = rg.getBoundingClientRect();
		}
		if (!first) { return null; }
		if (f.tagName === "INPUT") {
			// The measuring copy's line is the input's whole height; the caret is the text's.
			var fs = parseFloat(getComputedStyle(f).fontSize) || 16, half = fs * 0.62;
			var tight = function (b) { var c = (b.top + b.bottom) / 2; return { left: b.left, right: b.right, top: c - half, bottom: c + half }; };
			first = tight(first); last = tight(last); all = tight(all || first);
		}
		return { first: first, last: last, all: all || first, field: f.getBoundingClientRect() };
	}

	function selectionRange(f) {
		if (plain(f)) { return [selStart(f), selEnd(f)]; }
		var s = window.getSelection();
		return s.rangeCount ? s.getRangeAt(0) : null;
	}
	function hasSelection(f) {
		if (plain(f)) { return selEnd(f) > selStart(f); }
		var s = window.getSelection();
		return s.rangeCount > 0 && !s.isCollapsed && f.contains(s.anchorNode);
	}
	function placeCaret(f, at) {
		if (plain(f)) { try { f.setSelectionRange(at, at); } catch (e) {} return; }
		if (at) { var s = window.getSelection(); s.removeAllRanges(); s.addRange(at); s.collapseToStart(); }
	}
	// The word around the caret, as WebKit's word selection takes it.
	function selectWord(f) {
		if (plain(f)) {
			var v = f.value, a = selStart(f), z = a, W = /[\wÀ-￿'’]/;
			while (a > 0 && W.test(v.charAt(a - 1))) { a--; }
			while (z < v.length && W.test(v.charAt(z))) { z++; }
			if (a === z && z < v.length) { z++; }
			try { f.setSelectionRange(a, z); } catch (e) {}
			return;
		}
		var s = window.getSelection();
		if (s.modify) { s.collapseToStart(); s.modify("move", "backward", "word"); s.modify("extend", "forward", "word"); }
	}
	function selectAll(f) {
		if (plain(f)) { f.select(); return; }
		var rg = document.createRange(), s = window.getSelection();
		rg.selectNodeContents(f); s.removeAllRanges(); s.addRange(rg);
	}

	// ---- the UI ----

	function hide() {
		if (!ui) { return; }
		popup.style.display = "none";
		startHandle.style.display = "none";
		endHandle.style.display = "none";
		handles = false;
	}
	function visible() { return ui && (popup.style.display === "block" || handles); }

	// The popup, its pointer at x: under the text with its tip at `below`, or over it with its
	// tip at `above` where there is no room underneath (a field at the foot of a card the
	// keyboard has shortened; the TouchPad's browser drew it over the text too).
	function showPopup(items, x, below, above) {
		build();
		popup.innerHTML = "";
		for (var i = 0; i < items.length; i++) {
			var it = document.createElement("div");
			it.className = "lunacy-edit-item";
			it.textContent = items[i].label;
			it.__lunacyAction = items[i].action;
			popup.appendChild(it);
		}
		var arrow = document.createElement("div");
		arrow.className = "lunacy-edit-arrow";
		arrow.innerHTML = ARROW;
		popup.appendChild(arrow);
		popup.style.display = "block";
		popup.style.left = "0px";
		var w = popup.offsetWidth, h = popup.offsetHeight, vw = window.innerWidth;
		var left = Math.max(4, Math.min(vw - w - 4, Math.round(x - w / 2)));
		var flip = below + 9 + h > window.innerHeight - 2 && above - 9 - h >= 0;
		popup.style.left = left + "px";
		popup.style.top = Math.round(flip ? above - 9 - h : below + 9) + "px";
		arrow.style.left = Math.round(Math.max(10, Math.min(w - 10, x - left))) + "px";
		arrow.style.top = flip ? (h - 2) + "px" : "-9px";
		arrow.style.webkitTransform = flip ? "rotate(180deg)" : "";
	}

	function showHandles(f, boxes) {
		build();
		boxes = boxes || selectionBoxes(f);
		if (!boxes) { return; }
		var fb = boxes.field, a = boxes.first, z = boxes.last;
		// A handle shows only where its end of the selection is in the field's visible box.
		var showStart = a.left >= fb.left - 1 && a.left <= fb.right + 1 && a.top >= fb.top - 2 && a.top <= fb.bottom;
		var showEnd = z.right >= fb.left - 1 && z.right <= fb.right + 1 && z.bottom >= fb.top && z.bottom <= fb.bottom + 2;
		startHandle.style.left = Math.round(a.left) + "px";
		startHandle.style.top = Math.round(a.top - 14) + "px";
		startHandle.style.display = showStart ? "block" : "none";
		endHandle.style.left = Math.round(z.right) + "px";
		endHandle.style.top = Math.round(z.bottom + 2) + "px";
		endHandle.style.display = showEnd ? "block" : "none";
		handles = true;
	}

	function clipboardHasText() {
		try { return N && N.clipboardHasText ? N.clipboardHasText() : false; } catch (e) { return false; }
	}

	// Hold: the caret is placed already; Select, Select All, and Paste when there's something.
	function caretPopup(f) {
		var b = selectionBoxes(f);
		if (!b) { return; }
		var items = [
			{ label: "Select", action: function () { selectWord(f); hide(); showHandles(f); } },
			{ label: "Select All", action: function () { selectAll(f); hide(); showHandles(f); } }
		];
		if (clipboardHasText()) {
			items.push({ label: "Paste", action: function () { hide(); if (window.PalmSystem && PalmSystem.paste) { PalmSystem.paste(); } } });
		}
		showPopup(items, b.first.left, b.first.bottom + 4, b.first.top - 4);
	}

	// A touch on a selection: its handles, and Cut | Copy under it.
	function selectionPopup(f) {
		var b = selectionBoxes(f);
		if (!b) { return; }
		showHandles(f, b);
		var items = [
			{ label: "Cut", action: function () { document.execCommand("cut"); hide(); } },
			{ label: "Copy", action: function () { document.execCommand("copy"); hide(); } }
		];
		var x = b.first === b.last ? (b.first.left + b.first.right) / 2 : (b.all.left + b.all.right) / 2;
		showPopup(items, x, b.last.bottom + 18, b.first.top - 18);
	}

	// ---- touches on the UI itself ----

	function inUi(el) { return ui && el && el.nodeType && ui.contains(el); }

	function uiStart(t, el) {
		if (el === startHandle || startHandle.contains(el) || el === endHandle || endHandle.contains(el)) {
			drag = { start: startHandle.contains(el), anchor: selectionRange(field) };
			popup.style.display = "none";
			return;
		}
		for (; el && el !== popup; el = el.parentNode) {
			if (el.__lunacyAction) { el.className += " down"; drag = { item: el }; return; }
		}
		drag = { none: true };
	}
	function uiMove(t) {
		if (!drag || !field || drag.item || drag.none) { return; }
		// The finger is on the handle, below or above the text; the point it means is the line.
		var y = drag.start ? t.clientY + 14 : t.clientY - 14;
		if (plain(field)) {
			var off = offsetAt(field, t.clientX, y), a = selStart(field), z = selEnd(field);
			if (drag.start) { a = Math.min(off, z - 1); } else { z = Math.max(off, a + 1); }
			try { field.setSelectionRange(Math.max(0, a), z); } catch (e) {}
		} else {
			var r = offsetAt(field, t.clientX, y), s = window.getSelection();
			if (r && s.rangeCount) {
				var cur = s.getRangeAt(0), nr = document.createRange();
				if (drag.start) { nr.setStart(r.startContainer, r.startOffset); nr.setEnd(cur.endContainer, cur.endOffset); }
				else { nr.setStart(cur.startContainer, cur.startOffset); nr.setEnd(r.startContainer, r.startOffset); }
				if (!nr.collapsed) { s.removeAllRanges(); s.addRange(nr); }
			}
		}
		showHandles(field);
	}
	function uiEnd(t, el) {
		var d = drag;
		drag = null;
		if (!d || d.none) { return; }
		if (d.item) {
			d.item.className = d.item.className.replace(" down", "");
			if (el && (el === d.item || d.item.contains(el))) { d.item.__lunacyAction(); }
			return;
		}
		if (field && hasSelection(field)) { selectionPopup(field); }
	}

	// ---- the compat layer's touches (compat.js calls these) ----

	window.__lunacyEdit = {
		// A touch that starts on the edit UI is the UI's, and the page never hears of it. Decided
		// by where the finger is, not by the touch's target: the engine gives a touch that starts
		// a few px from a focused field to the field, and a handle is 2 px from its text. A
		// handle is also only 14 px wide, so a finger within HANDLE_REACH of it has it.
		owns: function (t) {
			if (!ui) { return false; }
			var x = t.clientX, y = t.clientY, hs = [startHandle, endHandle], i;
			for (i = 0; i < 2; i++) {
				if (hs[i].style.display !== "block") { continue; }
				var r = hs[i].getBoundingClientRect();
				if (x >= r.left - HANDLE_REACH && x <= r.right + HANDLE_REACH && y >= r.top - HANDLE_REACH && y <= r.bottom + HANDLE_REACH) {
					touchTarget = hs[i];
					return true;
				}
			}
			var el = document.elementFromPoint(x, y);
			if (inUi(el)) { touchTarget = el; return true; }
			return false;
		},
		uiTouch: function (type, t, el) {
			if (type === "start") { uiStart(t, touchTarget || el); touchTarget = null; }
			else if (type === "move") { uiMove(t); }
			else { uiEnd(t, el); }
		},
		down: function (t, target) {
			held = false;
			clearTimeout(holdTimer);
			var f = editableOf(target);
			if (!f) { return; }
			var x = t.clientX, y = t.clientY;
			holdTimer = setTimeout(function () {
				holdTimer = null;
				held = true;
				lastTap = null;
				if (document.activeElement !== f) { f.focus(); }
				field = f;
				hide();
				placeCaret(f, offsetAt(f, x, y));
				caretPopup(f);
			}, HOLD_MS);
		},
		// The finger went somewhere: not a hold, and the UI is out of date.
		moved: function () {
			clearTimeout(holdTimer);
			holdTimer = null;
			if (visible()) { hide(); }
		},
		// A tap has been handled by the page and its framework; now the caret and selection.
		// Returns true when the tap was the end of a hold, which leaves its popup up.
		tapped: function (t, target) {
			clearTimeout(holdTimer);
			holdTimer = null;
			if (held) { held = false; return true; }
			var x = t.clientX, y = t.clientY, now = Date.now();
			var f = editableOf(target);
			var twice = lastTap && now - lastTap.t < DOUBLE_TAP_MS &&
				Math.abs(x - lastTap.x) < DOUBLE_TAP_PX && Math.abs(y - lastTap.y) < DOUBLE_TAP_PX && lastTap.f === f;
			lastTap = { t: now, x: x, y: y, f: f };
			setTimeout(function () {
				if (!f || document.activeElement !== f && !(f.isContentEditable && f.contains(document.activeElement))) {
					hide();
					field = null;
					return;
				}
				var onSelection = false;
				if (hasSelection(f)) {
					var off = offsetAt(f, x, y);
					if (plain(f)) { onSelection = off >= selStart(f) && off <= selEnd(f); }
					else { var rg = window.getSelection().getRangeAt(0); onSelection = !!off && rg.comparePoint && rg.comparePoint(off.startContainer, off.startOffset) === 0; }
				}
				field = f;
				if (twice) {
					hide();
					selectWord(f);
					lastTap = null;
				} else if (onSelection) {
					selectionPopup(f);
				} else {
					hide();
					placeCaret(f, offsetAt(f, x, y));
				}
			}, 0);
			return false;
		}
	};
})();
