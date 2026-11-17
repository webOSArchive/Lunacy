// webOS's Text Assist, in an app's page, for Luna DE's on-screen keyboard: what LunaKeyboard's
// KeyboardService does to the field on Android (Text Assist's auto-correction, auto-capitalization,
// Double Space For Period, a correction undone by backspace) and what its AssistOverlay draws over
// the text (the gray dots under a corrected word, and the bubble of alternates a tap on it opens).
// On Android a keyboard reaches the field only through Android's InputConnection; here the field
// is in this page, so the same rules work on it directly. The keyboard (the shell page) says what
// was pressed, through the main process (main.js typeKey); this answers with the key events the
// main process is to send (Backspace, Return, Tab, the arrows, which reach the app as real keys, as
// on Android) and with what the keyboard should show (the capital it would type, Return's label).
// Text goes in as an input method's commit does, with no key events (insertText).
"use strict";
module.exports = function (window, ipc) {
	var document = window.document;
	var DOUBLE_SPACE_MS = 1500, OWN_EDIT_MS = 300, MARK_REACH = 4000, MAX_ITEMS = 5;
	var art = {};                       // the ate-* pictures, as data URLs, from the main process
	var settings = { autoCap: true, autoCorrect: true, doubleSpace: true };

	// ---- the field ----

	/** The field being typed into, or null: a text input, a textarea or something editable. */
	function field() {
		var el = document.activeElement;
		if (!el) { return null; }
		if (el.isContentEditable) { var root = el; while (root.parentElement && root.parentElement.isContentEditable) { root = root.parentElement; } return root; }
		if (el.tagName === "TEXTAREA") { return el; }
		if (el.tagName === "INPUT" && /^(text|search|email|url|password|tel|number|)$/i.test(el.type || "")) { return el; }
		return null;
	}
	function isInput(el) { return el.tagName === "INPUT" || el.tagName === "TEXTAREA"; }
	function attr(el, n) { return (el.getAttribute(n) || "").toLowerCase(); }

	/** Where the caret is, as offsets into the field's text: [start, end]. */
	function selection(el) {
		if (isInput(el)) { try { return [el.selectionStart || 0, el.selectionEnd || 0]; } catch (e) { return [el.value.length, el.value.length]; } }
		var sel = window.getSelection();
		if (!sel.rangeCount) { return [0, 0]; }
		var r = sel.getRangeAt(0);
		return [offsetOf(el, r.startContainer, r.startOffset), offsetOf(el, r.endContainer, r.endOffset)];
	}
	function offsetOf(el, node, off) {
		var pre = document.createRange();
		pre.selectNodeContents(el);
		try { pre.setEnd(node, off); } catch (e) { return 0; }
		return pre.toString().length;
	}
	function textOf(el) { return isInput(el) ? el.value : el.textContent; }
	function before(el, n) { var s = selection(el)[0]; return textOf(el).slice(Math.max(0, s - n), s); }

	/** A range of an editable element's text as a DOM Range. */
	function domRange(el, start, end) {
		var r = document.createRange(), walker = document.createTreeWalker(el, 4 /* SHOW_TEXT */), n, at = 0, set = false;
		while ((n = walker.nextNode())) {
			var len = n.nodeValue.length;
			if (!set && start <= at + len) { r.setStart(n, start - at); set = true; }
			if (set && end <= at + len) { r.setEnd(n, end - at); return r; }
			at += len;
		}
		r.selectNodeContents(el); r.collapse(false);
		return r;
	}
	function select(el, start, end) {
		if (isInput(el)) { el.setSelectionRange(start, end); return; }
		var sel = window.getSelection(); sel.removeAllRanges(); sel.addRange(domRange(el, start, end));
	}

	var lastOwnEdit = 0;
	function own() { lastOwnEdit = Date.now(); }
	/** Text in at the caret, as an input method commits it. */
	function insert(text) { own(); document.execCommand("insertText", false, text); }
	/** [start, end) of the text replaced with [text], the caret after it. */
	function replace(el, start, end, text) { own(); select(el, start, end); document.execCommand("insertText", false, text); }

	/** Whether webOS corrected this field: ordinary text, and not one that asks not to be (KeyboardService.assisted). */
	function assisted(el) {
		if (!el) { return false; }
		// A page's field says "don't correct" with autocorrect="off" (Enyo's autocorrect: false, and
		// Mojo's autoReplace: false through Lunacy's compat layer), as the WebView reads it on Android.
		if (attr(el, "autocorrect") === "off") { return false; }
		if (el.tagName === "INPUT" && !/^(text|search|)$/i.test(el.type || "")) { return false; }
		return true;
	}

	/**
	 * Whether the field wants a capital here (Android's caps mode for sentences, as the WebView
	 * reports it): the start of the text, or after a sentence's end and a space, or a new line.
	 */
	function capsHere(el) {
		if (!el || (el.tagName === "INPUT" && !/^(text|search|)$/i.test(el.type || ""))) { return false; }
		var a = attr(el, "autocapitalize");
		if (a === "off" || a === "none") { return false; }
		// TextUtils.getCapsMode for sentences: opening quotes and brackets, then the spaces before
		// them; the start of the text or a new line is a sentence's start, and so is a sentence's
		// end (its closing quotes skipped) with at least one space after it.
		var b = before(el, 200), i = b.length;
		while (i > 0 && "\"'([".indexOf(b[i - 1]) >= 0) { i--; }
		var j = i;
		while (j > 0 && (b[j - 1] === " " || b[j - 1] === "\t")) { j--; }
		if (j === 0 || b[j - 1] === "\n") { return true; }
		if (j === i) { return false; }
		while (j > 0 && "\"')]".indexOf(b[j - 1]) >= 0) { j--; }
		return j > 0 && ".?!".indexOf(b[j - 1]) >= 0;
	}

	/**
	 * What the field wants its Return key to say (KeyboardService.enterLabel): a label it asks
	 * for (enterkeyhint), "Enter" for a field of many lines, else "Done", as webOS's single-line fields had.
	 */
	function enterLabel(el) {
		if (!el) { return "Enter"; }
		var hint = { enter: "Enter", done: "Done", go: "Go", next: "Next", previous: "Prev", search: "Search", send: "Send" }[attr(el, "enterkeyhint")];
		if (hint) { return hint; }
		if (el.tagName === "TEXTAREA" || el.isContentEditable) { return "Enter"; }
		if ((el.type || "").toLowerCase() === "search") { return "Search"; }
		return "Done";
	}

	// ---- the word being typed, and the corrections ----

	var word = "", wordEnd = -1, ignored = {}, undo = null, lastSpaceAt = 0, fieldNow = null;
	var marks = [];   // { el, start, text, original, alternates }

	function reset() { word = ""; wordEnd = -1; ignored = {}; undo = null; clearMarks(); hideBubble(); }

	function isWordChar(s) { return s.length === 1 && (/\p{L}/u.test(s) || (word.length > 0 && (s === "'" || s === "’"))); }

	/** The host's corrector (LunaKeyboard's Corrector): what [w] should be, and what else it could be. */
	function correction(w) {
		if (!settings.autoCorrect || ignored[w] || /\d/.test(w)) { return null; }
		try {
			var x = new XMLHttpRequest();
			x.open("GET", location.origin + "/__luna-de/assist?w=" + encodeURIComponent(w), false);
			x.send();
			var d = JSON.parse(x.responseText);
			return d && d.fix ? d : null;
		} catch (e) { return null; }
	}
	function learn(w) { try { var x = new XMLHttpRequest(); x.open("GET", location.origin + "/__luna-de/assist?learn=" + encodeURIComponent(w), true); x.send(); } catch (e) {} }

	/** Ends the word being typed with [sep], correcting it first if Text Assist would (KeyboardService.endWord). */
	function endWord(el, sep) {
		if (!word) { if (sep) { insert(sep); } return; }
		var typed = word, start = wordEnd - typed.length;
		word = ""; wordEnd = -1;
		var c = correction(typed);
		// The field must still hold the word just before the caret, as typed.
		var there = before(el, typed.length) === typed && selection(el)[0] === start + typed.length;
		if (!c || c.fix === typed || !there) { if (sep) { insert(sep); } undo = null; return; }
		replace(el, start, start + typed.length, c.fix);
		if (sep) { insert(sep); }
		var m = { el: el, start: start, text: c.fix, original: typed, alternates: (c.alternates || []).filter(function (a) { return a !== c.fix; }) };
		marks.push(m);
		undo = { mark: m, sep: sep };
		drawMarks();
		// The plop: a correction was made (TextAssist.Notifier).
		if (ipc) { ipc.send("luna-de:kb-plop"); }
	}

	/** Marks at or past [pos] can't be trusted once the text before them changes. */
	function dropMarksFrom(el, pos) {
		var n = marks.length;
		marks = marks.filter(function (m) { return m.el !== el || m.start + m.text.length <= pos; });
		if (marks.length !== n) { drawMarks(); }
	}
	/** Whether the field still holds [m]'s word where it was; a mark that isn't is dropped. */
	function stillThere(m) {
		var ok = textOf(m.el).slice(m.start, m.start + m.text.length) === m.text;
		if (!ok) { marks.splice(marks.indexOf(m), 1); drawMarks(); }
		return ok;
	}

	// ---- what the keyboard does (KeyboardService.onText, onEmoji, onBackspace, onEnter, onTab, onCursor) ----

	function onText(el, text) {
		hideBubble();
		var sel = selection(el);
		if (!word) { dropMarksFrom(el, sel[1]); }
		if (assisted(el) && settings.autoCorrect && isWordChar(text)) {
			undo = null;
			if (!word) { wordEnd = sel[1]; }
			word += text; wordEnd += text.length;
			insert(text);
			return;
		}
		// Double Space For Period: a second space straight after a word's makes ". ".
		if (text === " " && assisted(el) && settings.doubleSpace && !word) {
			var b = before(el, 2);
			if (b.length === 2 && b[1] === " " && /[\p{L}\p{N}]/u.test(b[0]) && Date.now() - lastSpaceAt < DOUBLE_SPACE_MS) {
				var s = selection(el)[0];
				replace(el, s - 1, s, ". ");
				lastSpaceAt = 0; undo = null;
				return;
			}
		}
		if (word) { endWord(el, text); } else { insert(text); undo = null; }
		if (text === " ") { lastSpaceAt = Date.now(); }
	}

	function onEmoji(el, text) {
		hideBubble();
		if (!word) { dropMarksFrom(el, selection(el)[1]); }
		word = ""; wordEnd = -1;
		insert(text);
		undo = null;
	}

	/** Returns the key events the main process is to send. */
	function onBackspace(el, wholeWord) {
		hideBubble();
		// Straight after a correction, backspace puts the word back as it was typed.
		var u = undo;
		undo = null;
		if (u && !word) {
			var m = u.mark, b = before(el, m.text.length + u.sep.length);
			if (b === m.text + u.sep) {
				var s = selection(el)[0];
				replace(el, s - b.length, s, m.original + u.sep);
				ignored[m.original] = true;
				learn(m.original);
				lastSpaceAt = 0;
				marks.splice(marks.indexOf(m), 1); drawMarks();
				return [];
			}
		}
		if (word) {
			if (wholeWord) { word = ""; wordEnd = -1; }
			else { word = word.slice(0, -1); wordEnd -= 1; if (!word) { wordEnd = -1; } }
		}
		var sel = selection(el);
		if (sel[1] > 0) { dropMarksFrom(el, sel[1] - 1); }
		if (!wholeWord) { own(); return [{ key: "Backspace" }]; }
		// Held down long enough, webOS started taking whole words: the spaces before the caret and the word before them.
		var t = before(el, 64), i = t.length;
		while (i > 0 && /\s/.test(t[i - 1])) { i--; }
		while (i > 0 && !/\s/.test(t[i - 1])) { i--; }
		own();
		var out = [];
		for (var k = 0; k < t.length - i; k++) { out.push({ key: "Backspace" }); }
		return out;
	}

	function onEnter(el) { if (word) { endWord(el, ""); } undo = null; own(); return [{ key: "Return" }]; }
	function onTab(el) { if (word) { endWord(el, ""); } return [{ key: "Tab" }]; }
	function onHide(el) { if (word) { endWord(el, ""); } return []; }
	function onCursor(el, dx, dy, shift) {
		word = ""; wordEnd = -1;
		own();
		return [{ key: dx > 0 ? "Right" : dx < 0 ? "Left" : dy > 0 ? "Down" : "Up", shift: !!shift }];
	}

	// ---- the marks and the bubble (AssistOverlay) ----
	//
	// Drawn in a layer of their own over the page, outside its styles (a shadow root): the
	// corrected word's gray dots, a 5 px dot every 6 px a pixel under its baseline, and the
	// bubble above it (or below, if there is no room) with the original, in orange dots, and the
	// other possibilities, Prelude 17 px, in the ate-* art measured on the reference TouchPad.

	var layer = null, shadow = null, bubble = null;
	function ensureLayer() {
		if (layer && layer.isConnected) { return; }
		layer = document.createElement("luna-de-text-assist");
		layer.style.cssText = "position:fixed;left:0;top:0;width:0;height:0;z-index:2147483647;pointer-events:none;all:initial;position:fixed;";
		shadow = layer.attachShadow({ mode: "closed" });
		(document.documentElement || document.body).appendChild(layer);
	}

	/** A text range's boxes on the screen: the word's, for its dots and its bubble. Its baseline is a fifth of the line up from the bottom. */
	function rangeBox(el, start, end) {
		var rects = [];
		if (!isInput(el)) {
			var r = domRange(el, start, end), list = r.getClientRects();
			for (var i = 0; i < list.length; i++) { rects.push(list[i]); }
		} else { rects = mirrorRects(el, start, end); }
		if (!rects.length) { return null; }
		var box = { left: Infinity, top: Infinity, right: -Infinity, bottom: -Infinity };
		rects.forEach(function (q) { box.left = Math.min(box.left, q.left); box.top = Math.min(box.top, q.top); box.right = Math.max(box.right, q.right); box.bottom = Math.max(box.bottom, q.bottom); });
		box.baseline = box.bottom - (box.bottom - box.top) * 0.2;
		var fb = el.getBoundingClientRect();
		// Scrolled out of its field: not drawn.
		if (box.bottom < fb.top || box.top > fb.bottom || box.right < fb.left || box.left > fb.right) { return null; }
		return box;
	}

	/** Where text sits in an input or a textarea: a copy of the field, laid out the same, measured. */
	function mirrorRects(el, start, end) {
		var cs = window.getComputedStyle(el), m = document.createElement("div");
		["boxSizing", "width", "height", "paddingTop", "paddingRight", "paddingBottom", "paddingLeft", "borderTopWidth", "borderRightWidth", "borderBottomWidth", "borderLeftWidth",
		 "fontFamily", "fontSize", "fontWeight", "fontStyle", "letterSpacing", "lineHeight", "textTransform", "wordSpacing", "textIndent", "textAlign", "tabSize"].forEach(function (k) { m.style[k] = cs[k]; });
		m.style.position = "fixed"; m.style.visibility = "hidden"; m.style.overflow = "hidden"; m.style.borderStyle = "solid"; m.style.borderColor = "transparent";
		m.style.whiteSpace = el.tagName === "TEXTAREA" ? "pre-wrap" : "pre"; m.style.wordWrap = "break-word";
		var fb = el.getBoundingClientRect();
		m.style.left = fb.left + "px"; m.style.top = fb.top + "px";
		var v = el.value;
		m.appendChild(document.createTextNode(v.slice(0, start)));
		var span = document.createElement("span"); span.textContent = v.slice(start, end) || "​"; m.appendChild(span);
		m.appendChild(document.createTextNode(v.slice(end)));
		document.body.appendChild(m);
		m.scrollTop = el.scrollTop; m.scrollLeft = el.scrollLeft;
		var out = []; var list = span.getClientRects();
		for (var i = 0; i < list.length; i++) { out.push({ left: list[i].left - (el.scrollLeft - m.scrollLeft), top: list[i].top - (el.scrollTop - m.scrollTop), right: list[i].right - (el.scrollLeft - m.scrollLeft), bottom: list[i].bottom - (el.scrollTop - m.scrollTop) }); }
		m.remove();
		return out;
	}

	function dots(parent, box, picture) {
		var y = box.baseline + 1, step = 6;
		for (var x = box.left; x + 5 <= box.right + step / 2; x += step) {
			var d = document.createElement("img");
			d.src = picture;
			d.style.cssText = "position:fixed;width:5px;height:5px;left:" + x + "px;top:" + y + "px;";
			parent.appendChild(d);
		}
	}

	var drawQueued = false;
	function drawMarks() {
		if (drawQueued) { return; }
		drawQueued = true;
		window.requestAnimationFrame(function () {
			drawQueued = false;
			if (!marks.length && !bubble) { if (shadow) { shadow.innerHTML = ""; } return; }
			ensureLayer();
			shadow.innerHTML = "";
			marks.forEach(function (m) {
				if (!m.el.isConnected) { return; }
				var box = rangeBox(m.el, m.start, m.start + m.text.length);
				if (box && art.corrected) { dots(shadow, box, art.corrected); }
			});
			if (bubble) { drawBubble(); }
		});
	}
	function clearMarks() { marks = []; drawMarks(); }
	window.addEventListener("scroll", function () { if (marks.length || bubble) { drawMarks(); } }, true);
	window.addEventListener("resize", function () { if (marks.length || bubble) { drawMarks(); } });

	function showBubble(m) {
		var items = [m.original].concat(m.alternates).filter(function (it, i, all) { return it !== m.text && all.indexOf(it) === i; }).slice(0, MAX_ITEMS);
		if (!items.length) { hideBubble(); return; }
		bubble = { mark: m, items: items };
		drawMarks();
	}
	function hideBubble() { if (bubble) { bubble = null; drawMarks(); } }

	function drawBubble() {
		var m = bubble.mark, box = rangeBox(m.el, m.start, m.start + m.text.length);
		if (!box || !art.left) { return; }
		var b = document.createElement("div");
		b.style.cssText = "position:fixed;pointer-events:auto;height:60px;font:17px Prelude,sans-serif;color:#fff;white-space:nowrap;user-select:none;-webkit-user-select:none;";
		var ctx = document.createElement("canvas").getContext("2d");
		ctx.font = "17px Prelude, sans-serif";
		var widths = bubble.items.map(function (t) { return ctx.measureText(t).width; });
		var gap = 12 * 2 + 4, inner = widths.reduce(function (a, c) { return a + c; }, 0) + gap * (bubble.items.length - 1);
		var w = Math.max(inner + 16 + 15, 17 + 17 + 33), cx = (box.left + box.right) / 2;
		var x0 = Math.max(0, Math.min(window.innerWidth - w, cx - w / 2));
		var above = box.top - (52 + 2) >= 0;
		var y0 = above ? box.top - (52 + 2) : box.bottom + (5 + 2) - 2;
		var bodyY = above ? y0 : y0 + 8;
		function piece(src, x, pw, y) { var i = document.createElement("img"); i.src = src; i.style.cssText = "position:fixed;left:" + x + "px;top:" + y + "px;width:" + pw + "px;height:60px;"; b.appendChild(i); }
		piece(art.left, x0, 17, bodyY);
		piece(art.middle, x0 + 17, w - 34, bodyY);
		piece(art.right, x0 + w - 17, 17, bodyY);
		var ax = Math.max(x0 + 17, Math.min(x0 + w - 17 - 33, cx - 33 / 2));
		piece(above ? art.down : art.up, ax, 33, y0);
		var x = x0 + Math.max(16, (w - inner) / 2), baseline = bodyY + 29;
		bubble.items.forEach(function (it, i) {
			if (i > 0 && art.divider) {
				var d = document.createElement("img"); d.src = art.divider;
				d.style.cssText = "position:fixed;width:4px;height:40px;left:" + (x - gap / 2 - 2) + "px;top:" + (bodyY + 3) + "px;";
				b.appendChild(d);
			}
			var t = document.createElement("div");
			t.textContent = it;
			t.style.cssText = "position:fixed;left:" + (x - gap / 2) + "px;top:" + bodyY + "px;height:43px;padding:0 " + (gap / 2) + "px;line-height:" + (29 * 2 - 17) + "px;cursor:pointer;";
			(function (pick) {
				t.addEventListener("mousedown", function (e) { e.preventDefault(); e.stopPropagation(); });
				t.addEventListener("click", function (e) { e.preventDefault(); e.stopPropagation(); picked(m, pick); });
			})(it);
			b.appendChild(t);
			if (i === 0 && it === m.original && art.misspelled) { dots(b, { left: x, right: x + widths[i], baseline: baseline }, art.misspelled); }
			x += widths[i] + gap;
		});
		shadow.appendChild(b);
	}

	/** A word from the bubble replaces the corrected one; its original comes back unmarked (KeyboardService.picked). */
	function picked(m, pick) {
		hideBubble();
		if (!stillThere(m)) { return; }
		replace(m.el, m.start, m.start + m.text.length, pick);
		marks.splice(marks.indexOf(m), 1);
		if (pick === m.original) { ignored[pick] = true; learn(pick); }
		var delta = pick.length - m.text.length;
		marks.forEach(function (o) { if (o.el === m.el && o.start >= m.start + m.text.length) { o.start += delta; } });
		drawMarks();
		state();
	}

	// ---- the caret moved by itself (KeyboardService.onUpdateSelection) ----

	document.addEventListener("selectionchange", function () {
		var el = field();
		if (!el) { hideBubble(); return; }
		// Another field: the keyboard starts clean for it, as Android's onStartInputView has it.
		if (el !== fieldNow) { fieldNow = el; reset(); state(true); return; }
		var sel = selection(el), ours = Date.now() - lastOwnEdit < OWN_EDIT_MS;
		// The caret went somewhere the keyboard didn't put it: the word is finished as it stands.
		if (word && (sel[0] !== sel[1] || sel[1] > wordEnd || sel[1] < wordEnd - word.length) && !ours) { word = ""; wordEnd = -1; }
		if (!ours && sel[0] === sel[1]) {
			marks.slice().forEach(function (m) { if (m.el === el && Math.abs(sel[0] - m.start) < MARK_REACH) { stillThere(m); } });
		}
		// A tap that puts the caret in a corrected word opens its bubble; anything else closes it.
		var m = null;
		if (!ours && sel[0] === sel[1] && !word) {
			for (var i = marks.length - 1; i >= 0; i--) { if (marks[i].el === el && sel[0] >= marks[i].start && sel[0] <= marks[i].start + marks[i].text.length) { m = marks[i]; break; } }
		}
		if (m && stillThere(m)) { undo = null; showBubble(m); }
		else if (bubble && !ours) { hideBubble(); }
		if (!ours) { undo = null; }
		state();
	});

	/** What the keyboard should show now, told to it (through the main process). */
	function state(fresh) {
		var el = field();
		var s = { autoCap: !!(el && settings.autoCap && !word && capsHere(el)), enterLabel: enterLabel(el), field: !!el, fresh: !!fresh };
		if (ipc) { ipc.send("luna-de:kb-state", s); }
		return s;
	}

	/** What the keyboard pressed: [what] is { text }, { emoji }, { backspace, word }, { enter }, { tab }, { hide }, { cursor: {dx, dy, shift} }. */
	function handle(what) {
		var el = field(), keys = [];
		if (el !== fieldNow) { fieldNow = el; reset(); }
		if (!el) {
			if (what.text || what.emoji) { insert(what.text || what.emoji); }
			else if (what.backspace) { keys = [{ key: "Backspace" }]; }
			else if (what.enter) { keys = [{ key: "Return" }]; }
			else if (what.tab) { keys = [{ key: "Tab" }]; }
			else if (what.cursor) { keys = [{ key: what.cursor.dx > 0 ? "Right" : what.cursor.dx < 0 ? "Left" : what.cursor.dy > 0 ? "Down" : "Up", shift: !!what.cursor.shift }]; }
			return { keys: keys, state: state() };
		}
		if (what.text) { onText(el, what.text); }
		else if (what.emoji) { onEmoji(el, what.emoji); }
		else if (what.backspace) { keys = onBackspace(el, !!what.word); }
		else if (what.enter) { keys = onEnter(el); }
		else if (what.tab) { keys = onTab(el); }
		else if (what.hide) { keys = onHide(el); }
		else if (what.cursor) { keys = onCursor(el, what.cursor.dx, what.cursor.dy, what.cursor.shift); }
		var s = what.backspace || what.enter || what.cursor ? null : state();
		return { keys: keys, state: s };
	}

	window.__lunaDeKeyboard = {
		handle: handle,
		state: state,
		setArt: function (a) { art = a || {}; drawMarks(); },
		setSettings: function (s) { for (var k in s) { settings[k] = !!s[k]; } },
		reset: function () { reset(); fieldNow = field(); return state(); },
	};
};
