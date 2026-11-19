// Luna DE's virtual keyboard: webOS's tablet keyboard as LunaKeyboard draws and works it
// (KeyboardView.kt, Keymap.kt, Emoji.kt), from its art (the device's own pictures;
// LunaKeyboard/src/main/assets, with their NOTICEs). The sizes are the art's: 340 px of
// background, 55 px for the number row and 70 for each of the other four, every plate a
// nine-slice with 13 px corners; the other sizes (XS, S, L) are that drawing at another height.
//
// It has what LunaKeyboard has: the alternates a held key offers in its balloon, backspace's
// repeat (letters, then whole words), the trackball's caret, the emoji layer, shift's lock, the
// capital the field asks for (auto-capitalization) and the Return label it asks for. What it
// types goes to the card through main.js, where the page's Text Assist (textassist.js) does
// what LunaKeyboard's service does to the field, corrections and all.
"use strict";
(function () {
	var ART = "../../LunaKeyboard/src/main/assets/keyboard/", EMOJI = "../../LunaKeyboard/src/main/assets/emoji/";
	var FULL = 340, FULL_ROWS = [55, 70, 70, 70, 70], CORNER = 13;
	var HEIGHT = FULL, ROW_HEIGHTS = FULL_ROWS.slice(), SCALE = 1;
	function setHeight(h) {
		HEIGHT = h; SCALE = h / FULL;
		var keys = Math.round(h * 335 / FULL), used = 0;
		ROW_HEIGHTS = FULL_ROWS.map(function (r, i) {
			var v = i === FULL_ROWS.length - 1 ? keys - used : Math.round(r * SCALE);
			used += v; return v;
		});
	}
	var ACTIVE = "rgb(20,20,20)", DISABLED = "rgb(100,100,100)", FUNCTION = "rgb(210,210,210)";
	// webOS's own timings (LunaCE's TabletKeyboard.cpp), as KeyboardView has them.
	var FIRST_REPEAT = 350, LETTER_REPEAT = 120, WORD_REPEAT = 275, WORD_DELETE_AFTER = 1500, DOUBLE_TAP = 500,
		HOLD_FOR_EXTENDED = 400, TRACK_TICK = 62, TRACK_STEP = 15;
	var POPUP_LEFT = 11, POPUP_RIGHT = 10, POPUP_TOP_TO_KEY = 10, POPUP_POINTER_END = 62, POPUP_KEY_WIDTH = 80, POPUP_SINGLE_LINE_MAX = 5;
	var SUPERSCRIPT = 0.6, SUPERSCRIPT_INSET = 7, SUPERSCRIPT_ROOM = 0.22;

	// LunaCE's US QWERTY tablet keymap (Keymap.kt): [weight, main, alt, extended, altText]; a
	// function key is [weight, name]. A negative weight is a zone that types its character but
	// draws no key (the home row's left edge types "a"). [ext] is the press-and-hold list, whose
	// first character is always the key's own.
	function k(w, main, alt, ext, altText) { return { w: w, main: main, alt: alt === undefined ? main : alt, ext: ext || null, altText: altText || null }; }
	function fn(w, name) { return { w: w, fn: name }; }
	var ROWS = [
		[k(1, "1", "!", "1!¹¼½¡"), k(1, "2", "@", "2@²"), k(1, "3", "#", "3#³¾"), k(1, "4", "$", "4$€£¥¢¤"), k(1, "5", "%", "5%‰"), k(1, "6", "^"), k(1, "7", "&"), k(1, "8", "*"), k(1, "9", "(", "9([{"), k(1, "0", ")", "0)]}"), fn(2, "trackball")],
		[k(1, "q", "`"), k(1, "w", "~"), k(1, "e", "€", "eèéêëęē"), k(1, "r", "£", "r®"), k(1, "t", "\\", "t™þ"), k(1, "y", "|", "yýÿ"), k(1, "u", "{", "uùúûüű"), k(1, "i", "}", "iìíîïİı"), k(1, "o", "[", "oòóôõöøőœºω"), k(1, "p", "]", "p§π"), fn(1, "backspace")],
		[k(-0.5, "a", "<"), k(1, "a", "<", "aàáâãäåæª"), k(1, "s", ">", "sšŞßσ"), k(1, "d", "=", "dð†‡"), k(1, "f", "+"), k(1, "g", "×", "gğ"), k(1, "h", "÷"), k(1, "j", "°"), k(1, "k", ";"), k(1, "l", ":", "lŁ"), fn(1.5, "return")],
		[fn(1, "shift"), k(1, "z", "z", "zžźż", ":-)"), k(1, "x", "x", null, ";-)"), k(1, "c", "c", "cçć©¢", ":-("), k(1, "v", "v", null, ":'("), k(1, "b", "b", null, ":-P"), k(1, "n", "n", "nñń", ":-O"), k(1, "m", "m", "mµ", "<3"), k(1, ",", "/", ",/\\"), k(1, ".", "?", ".?•…¿"), fn(1, "shift")],
		[fn(1, "tab"), fn(1, "symbol"), fn(1, "emoji"), k(5, " "), k(1, "'", "\"", "'\"`‘’“”"), k(1, "-", "_", "-_±¬"), fn(1, "hide")],
	];
	var SYMBOL_LABEL = "+ = [  ]", EMOJI_FACE = "😀";

	var canvas = document.getElementById("keyboard"), ctx = canvas.getContext("2d");
	// The balloon is drawn over the card above the keyboard, on a canvas of its own that takes no pointer.
	var pop = document.createElement("canvas"), pctx = pop.getContext("2d");
	pop.id = "keyboard-popup"; pop.hidden = true;
	canvas.parentNode.insertBefore(pop, canvas.nextSibling);

	var images = {}, zones = [];
	var shift = 0 /* 0 off, 1 once, 2 lock */, symbols = false, autoCap = false, enterLabel = "Enter", lastShiftTap = 0;
	var emojiMode = false, emojiPage = 0, emojiList = [], emojiSlot = new Map(), emojiPerPage = 0;
	var pressedZone = null, extended = null, extendedFrame = null, extendedIndex = 0;
	var trackball = false, trackDX = 0, trackDY = 0, trackVX = 0, trackVY = 0, trackTimer = null, lastX = 0, lastY = 0;
	var longPress = null, repeatTimer = null, repeats = 0;

	["keyboard-bg", "key-white", "key-gray", "key-gray-short", "key-black", "key-shift-on", "key-shift-lock",
	 "icon-delete", "icon-hide-keyboard", "icon-shift", "icon-shift-on", "icon-shift-lock", "trackball", "menu-arrow-down",
	 "popup-bg", "popup-bg-2", "popup-key"].forEach(function (name) {
		var img = new Image();
		img.onload = function () { images[name] = img; draw(); };
		img.src = ART + name + ".png";
	});
	// The emoji in key order: CE's keyboard-emoji.txt.
	fetch(EMOJI + "keyboard-emoji.txt").then(function (r) { return r.text(); }).then(function (t) {
		emojiList = t.split("\n").filter(function (l) { return l.trim() && l.trim()[0] !== "#"; })
			.reduce(function (a, l) { return a.concat(l.trim().split(/\s+/)); }, []);
	}).catch(function () {});
	var emojiImages = {};
	/** OpenMoji's name for an emoji: its code points in upper-case hex, joined by "-", without U+FE0F. */
	function emojiFile(e) {
		var out = [];
		for (var ch of e) { var cp = ch.codePointAt(0); if (cp !== 0xFE0F) { out.push(cp.toString(16).toUpperCase()); } }
		return out.join("-");
	}
	function emojiImage(e, gray) {
		var key = e + (gray ? "g" : "");
		if (emojiImages[key] !== undefined) { return emojiImages[key]; }
		emojiImages[key] = null;
		var img = new Image();
		img.onload = function () {
			if (!gray) { emojiImages[key] = img; draw(); return; }
			// The emoji key's own smiley, among the gray function keys: luminance, rescaled so its
			// brightest part lands on their label gray (Emoji.grayed).
			var c = document.createElement("canvas"); c.width = img.width; c.height = img.height;
			var g = c.getContext("2d"); g.drawImage(img, 0, 0);
			var d = g.getImageData(0, 0, c.width, c.height), px = d.data, bright = 1, i, lum;
			for (i = 0; i < px.length; i += 4) { lum = (px[i] * 11 + px[i + 1] * 16 + px[i + 2] * 5) / 32; if (px[i + 3] > 128 && lum > bright) { bright = lum; } }
			for (i = 0; i < px.length; i += 4) { lum = Math.min(255, ((px[i] * 11 + px[i + 1] * 16 + px[i + 2] * 5) / 32) * 0xd2 / bright); px[i] = px[i + 1] = px[i + 2] = lum; }
			g.putImageData(d, 0, 0);
			emojiImages[key] = c; draw();
		};
		img.src = EMOJI + emojiFile(e) + ".png";
		return null;
	}

	function isLetterOrDigit(ch) { return /^[\p{L}\p{N}]$/u.test(ch); }
	function isLetter(ch) { return /^\p{L}$/u.test(ch); }

	function layout() {
		var w = parseInt(getComputedStyle(document.documentElement).getPropertyValue("--w"), 10) || 1024;
		canvas.width = w; canvas.height = HEIGHT;
		var head = Math.round(((images["popup-bg-2"] ? images["popup-bg-2"].height : 150) - POPUP_TOP_TO_KEY) * SCALE);
		pop.width = w; pop.height = HEIGHT + head; pop.style.height = (HEIGHT + head) + "px";
		zones = [];
		var top = HEIGHT - ROW_HEIGHTS.reduce(function (a, b) { return a + b; }, 0);
		ROWS.forEach(function (row, r) {
			var units = row.reduce(function (a, key) { return a + Math.abs(key.w); }, 0), sofar = 0, left = 0;
			row.forEach(function (key) {
				sofar += Math.abs(key.w);
				// Whole pixels: a key boundary landing mid-pixel puts a seam down its side.
				var right = Math.round(w * sofar / units);
				zones.push({ key: key, row: r, x: left, y: top, w: right - left, h: ROW_HEIGHTS[r] });
				left = right;
			});
			top += ROW_HEIGHTS[r];
		});
		// TabletKeymap::updateEmojiSlots: every character key of the top four rows in reading
		// order, then each edge zone given the slot of the key it types.
		emojiSlot = new Map(); emojiPerPage = 0;
		zones.forEach(function (z) { if (z.row < ROWS.length - 1 && z.key.w > 0 && !z.key.fn) { emojiSlot.set(z, emojiPerPage++); } });
		zones.forEach(function (z, i) {
			if (z.row < ROWS.length - 1 && z.key.w < 0) {
				[zones[i - 1], zones[i + 1]].some(function (n) { if (n && n.row === z.row && n.key.main === z.key.main && emojiSlot.has(n)) { emojiSlot.set(z, emojiSlot.get(n)); return true; } return false; });
			}
		});
		draw();
	}

	function emojiPages() { return emojiPerPage > 0 ? Math.max(1, Math.ceil(emojiList.length / emojiPerPage)) : 1; }
	function emojiFor(z) { if (!emojiMode || !emojiSlot.has(z)) { return null; } return emojiList[emojiPage * emojiPerPage + emojiSlot.get(z)] || null; }
	function leaveEmoji() { emojiMode = false; emojiPage = 0; }

	/** The plate under a key: webOS picked it by what kind of key it is (KeyboardView.plateFor). */
	function plateFor(z) {
		var key = z.key;
		if (key.w < 0 || key.fn === "trackball") { return null; }
		if (z.row === 0) { return "key-gray-short"; }
		if (key.fn === "shift") { return shift === 2 ? "key-shift-lock" : shift === 1 ? "key-shift-on" : "key-black"; }
		if (key.fn) { return "key-black"; }
		return isLetterOrDigit(key.main) || key.main === " " ? "key-white" : "key-gray";
	}

	/** A plate image holds two states, one above the other; each is stretched as a nine-slice (Art.plate, Art.nine). */
	function plate(c, img, z, down) {
		var h = img.height / 2, sy = down ? h : 0;
		var dh = Math.min(CORNER, z.w / 2), dv = Math.min(CORNER, z.h / 2);
		var xs = [0, CORNER, img.width - CORNER, img.width], ys = [sy, sy + CORNER, sy + h - CORNER, sy + h];
		var dx = [z.x, z.x + dh, z.x + z.w - dh, z.x + z.w], dy = [z.y, z.y + dv, z.y + z.h - dv, z.y + z.h];
		for (var i = 0; i < 3; i++) {
			for (var j = 0; j < 3; j++) {
				var sw = xs[i + 1] - xs[i], sh = ys[j + 1] - ys[j], dw = dx[i + 1] - dx[i], dhh = dy[j + 1] - dy[j];
				if (sw > 0 && sh > 0 && dw > 0 && dhh > 0) { c.drawImage(img, xs[i], ys[j], sw, sh, dx[i], dy[j], dw, dhh); }
			}
		}
	}

	function font(size, bold) { return (bold ? "bold " : "") + size + "px Prelude, sans-serif"; }
	/** A label centred in [box], shrunk until it fits 16 px inside, as webOS did rather than letting it run over. */
	function label(c, text, box, color, size, bold) {
		var px = size * SCALE;
		c.font = font(px, bold);
		var room = box.w - 16 * SCALE;
		while (room > 0 && c.measureText(text).width > room && px > 6) { px -= 1; c.font = font(px, bold); }
		c.fillStyle = color; c.textAlign = "center"; c.textBaseline = "middle";
		c.fillText(text, box.x + box.w / 2, box.y + box.h / 2);
	}
	/** webOS shrank the quieter of two characters to three quarters. */
	function sized(base, color) { return color === ACTIVE ? base : base * 75 / 100; }

	/**
	 * Whether a key types its alternate (LunaCE's TabletKeymap::map): a letter through the symbol
	 * key (shift only capitalises it), the number row and the punctuation through shift once. A
	 * shift lock isn't shift for this.
	 */
	function usesAlt(key) {
		if (key.alt === key.main && !key.altText) { return false; }
		var letter = isLetter(key.main) && !/[0-9]/.test(key.alt);
		return letter ? symbols : shift === 1;
	}

	/** Whether a row's pairs are too narrow to stand side by side (a phone's keys): all of it in superscripts then. */
	function superscriptRow(row) {
		ctx.font = font(24 * SCALE, false);
		return zones.some(function (z) {
			if (z.row !== row || z.key.w <= 0 || z.key.alt === z.key.main) { return false; }
			var alt = z.key.altText || z.key.alt;
			return z.w / 2 - 16 * SCALE < Math.max(ctx.measureText(z.key.main).width, ctx.measureText(alt).width);
		});
	}

	function drawCap(z, box) {
		var key = z.key;
		switch (key.fn) {
			case "tab": label(ctx, "Tab", box, FUNCTION, 22, true); return;
			case "symbol": label(ctx, SYMBOL_LABEL, box, FUNCTION, 22, true); return;
			case "return":
				// Return's label sits small in the bottom right corner, as it did on webOS.
				ctx.font = font(22 * SCALE, true); ctx.fillStyle = FUNCTION; ctx.textAlign = "right"; ctx.textBaseline = "alphabetic";
				ctx.fillText(enterLabel, box.x + box.w * 0.85, box.y + box.h * 0.8);
				return;
		}
		if (key.main === " ") { return; }
		var main = key.main, alt = key.altText || (key.alt !== key.main ? key.alt : null);
		var across = z.row === 0 && !!alt, stacked = z.row > 0 && !!alt && !isLetterOrDigit(main), size = 24, nudge = 1;
		if (stacked && Math.floor(box.h / 3) < (size - 2) * SCALE) { stacked = false; across = true; nudge = 2; }
		var capitalised = shift !== 0 || (autoCap && isLetter(main));
		var face = capitalised ? main.toUpperCase() : main, live = usesAlt(key);
		if (!across && !stacked) {
			var t = live && alt ? alt : face;
			label(ctx, t, box, ACTIVE, t.length > 1 ? 22 : 26, t.length > 1);
			return;
		}
		// The character that would be typed takes the dark colour and the full size; the other fades back.
		var mainColor = live ? DISABLED : ACTIVE, altColor = live ? ACTIVE : DISABLED, s = SCALE;
		if (across && superscriptRow(z.row)) {
			label(ctx, face, { x: box.x, y: box.y, w: box.w * (1 - SUPERSCRIPT_ROOM), h: box.h }, mainColor, size, false);
			ctx.font = font(size * SUPERSCRIPT * s, false); ctx.fillStyle = altColor; ctx.textAlign = "right"; ctx.textBaseline = "top";
			ctx.fillText(alt, box.x + box.w - SUPERSCRIPT_INSET * s, box.y + SUPERSCRIPT_INSET * s);
			return;
		}
		if (across) {
			var lead = (live ? 5 : 4) * s, r = { x: box.x + lead, y: box.y + s, w: box.w - lead - (9 * s - lead), h: box.h }, half = r.w / 2;
			label(ctx, face, { x: r.x + half - nudge * s, y: r.y, w: half, h: r.h }, mainColor, sized(size, mainColor), false);
			label(ctx, alt, { x: r.x + nudge * s, y: r.y, w: half, h: r.h }, altColor, sized(size, altColor), false);
		} else {
			var third = box.h / 3;
			label(ctx, face, { x: box.x, y: box.y + box.h - third - 10 * s, w: box.w, h: third }, mainColor, sized(size, mainColor), false);
			label(ctx, alt, { x: box.x, y: box.y + 10 * s, w: box.w, h: third }, altColor, sized(size, altColor), false);
		}
	}

	function centred(img, cx, cy) {
		if (!img) { return; }
		var iw = img.width * SCALE, ih = img.height * SCALE;
		ctx.drawImage(img, Math.round(cx - iw / 2), Math.round(cy - ih / 2), Math.round(iw), Math.round(ih));
	}

	/** An emoji three quarters of the key's shorter side, centred (TabletKeyboard::drawKeyCap). */
	function drawEmoji(e, box, gray) {
		var img = emojiImage(e, gray);
		if (!img) { return false; }
		var size = Math.min(box.w, box.h) * 3 / 4;
		ctx.drawImage(img, Math.round(box.x + box.w / 2 - size / 2), Math.round(box.y + box.h / 2 - size / 2), Math.round(size), Math.round(size));
		return true;
	}

	/** The trackball, its four arrows lit the way it is being pushed and fading back once it is let go. */
	function drawTrackball(z) {
		var cx = z.x + z.w / 2, cy = z.y + z.h / 2, arrow = images["menu-arrow-down"];
		centred(images.trackball, cx, cy);
		if (!arrow) { return; }
		var lit = [trackVY, -trackVX, -trackVY, trackVX];   // down, left, up, right
		ctx.save();
		ctx.translate(Math.round(cx), Math.round(cy));
		for (var i = 0; i < 4; i++) {
			ctx.globalAlpha = Math.min(1, 0.2 + Math.max(0, lit[i]));
			ctx.drawImage(arrow, -arrow.width * SCALE / 2, arrow.height * SCALE / 3, arrow.width * SCALE, arrow.height * SCALE);
			ctx.rotate(Math.PI / 2);
		}
		ctx.restore();
	}

	function drawKey(z) {
		var key = z.key, down = pressedZone === z, name = plateFor(z);
		if (name && images[name]) { plate(ctx, images[name], z, down); }
		if (key.w < 0) { return; }
		if (key.fn === "trackball") { drawTrackball(z); return; }
		// LunaCE trims the bottom of the cap area, and nudges it down while the key is held.
		var box = { x: z.x, y: z.y + (down ? 2 * SCALE : 0), w: z.w, h: z.h - 4 * SCALE }, cx = box.x + box.w / 2, cy = box.y + box.h / 2;
		// The emoji layer: shift's page ("1/4") and the emoji key's "ABC" in the function gray; off
		// the layer, the emoji key wears its own smiley in that gray.
		if (key.fn === "emoji") {
			if (emojiMode) { label(ctx, "ABC", box, FUNCTION, 22, true); }
			else if (!drawEmoji(EMOJI_FACE, box, true)) { label(ctx, EMOJI_FACE, box, FUNCTION, 22, false); }
			return;
		}
		if (emojiMode) {
			if (key.fn === "shift") { label(ctx, (emojiPage + 1) + "/" + emojiPages(), box, FUNCTION, 22, true); return; }
			if (emojiSlot.has(z)) { var e = emojiFor(z); if (e && !drawEmoji(e, box, false)) { label(ctx, e, box, ACTIVE, 26, false); } return; }
		}
		switch (key.fn) {
			case "backspace": centred(images["icon-delete"], cx, cy); return;
			case "hide": centred(images["icon-hide-keyboard"], cx, cy); return;
			case "shift": centred(images[shift === 2 ? "icon-shift-lock" : shift === 1 || autoCap ? "icon-shift-on" : "icon-shift"], cx, cy); return;
		}
		drawCap(z, box);
	}

	function draw() {
		if (canvas.hidden) { return; }
		ctx.clearRect(0, 0, canvas.width, canvas.height);
		if (images["keyboard-bg"]) { ctx.drawImage(images["keyboard-bg"], 0, 0, canvas.width, HEIGHT); }
		zones.forEach(drawKey);
		drawExtended();
	}

	// ---- the press-and-hold balloon ----

	function twoLines(chars) { return chars.length > POPUP_SINGLE_LINE_MAX; }
	function perLine(chars) { return twoLines(chars) ? Math.ceil(chars.length / 2) : chars.length; }
	function chars(s) { return Array.from(s); }

	function showExtended(z) {
		var list = chars(z.key.ext);
		var bg = images[twoLines(list) ? "popup-bg-2" : "popup-bg"];
		if (!bg) { return; }
		var s = SCALE, h = bg.height * s, w = (POPUP_LEFT + POPUP_RIGHT + perLine(list) * POPUP_KEY_WIDTH) * s;
		var head = pop.height - HEIGHT;
		var left = Math.max(0, Math.min(canvas.width - w, z.x + z.w / 2 - (POPUP_KEY_WIDTH / 2 + POPUP_LEFT) * s));
		// Above the key, in the room over the keyboard: over the card, which steps aside for it.
		var top = Math.max(0, head + z.y - h + POPUP_TOP_TO_KEY * s);
		extended = list; extendedIndex = 0;
		extendedFrame = { x: left, y: top, w: w, h: h };
		if (window.__lunaCover) { window.__lunaCover(true, HEIGHT); }
		pop.hidden = false;
		drawExtended();
	}
	function hideExtended() {
		if (!extended) { return; }
		extended = null; extendedFrame = null;
		pctx.clearRect(0, 0, pop.width, pop.height); pop.hidden = true;
		if (window.__lunaCover) { window.__lunaCover(false); }
	}
	function drawExtended() {
		pctx.clearRect(0, 0, pop.width, pop.height);
		if (!extended || !extendedFrame) { return; }
		var f = extendedFrame, s = SCALE, bg = images[twoLines(extended) ? "popup-bg-2" : "popup-bg"], cell = images["popup-key"];
		if (!bg || !cell) { return; }
		// The balloon's pointer is part of the art, so only the side away from it stretches (Art.stretchH).
		var L = POPUP_POINTER_END, R = POPUP_RIGHT;
		pctx.drawImage(bg, 0, 0, L, bg.height, f.x, f.y, L * s, f.h);
		pctx.drawImage(bg, L, 0, bg.width - L - R, bg.height, f.x + L * s, f.y, f.w - (L + R) * s, f.h);
		pctx.drawImage(bg, bg.width - R, 0, R, bg.height, f.x + f.w - R * s, f.y, R * s, f.h);
		var ch = cell.height / 2 * s, line0 = perLine(extended);
		extended.forEach(function (c, i) {
			var r = { x: f.x + (POPUP_LEFT + (i % line0) * POPUP_KEY_WIDTH) * s, y: f.y + POPUP_LEFT * s + Math.floor(i / line0) * ch, w: POPUP_KEY_WIDTH * s, h: ch };
			plate(pctx, cell, r, i === extendedIndex);
			label(pctx, c, r, ACTIVE, 22, false);
		});
	}
	/** The balloon's cell under a point of the keyboard (its first, the key's own, anywhere else). */
	function extendedAt(x, y) {
		if (!extended || !extendedFrame) { return 0; }
		var f = extendedFrame, s = SCALE, head = pop.height - HEIGHT, py = y + head;
		if (x < f.x || x > f.x + f.w || py < f.y || py > f.y + f.h) { return 0; }
		var line0 = perLine(extended), ch = (images["popup-key"] ? images["popup-key"].height / 2 : 60) * s;
		var col = Math.max(0, Math.min(line0 - 1, Math.floor((x - f.x - POPUP_LEFT * s) / (POPUP_KEY_WIDTH * s))));
		var line = Math.max(0, Math.floor((py - f.y - POPUP_LEFT * s) / ch));
		return Math.max(0, Math.min(extended.length - 1, line * line0 + col));
	}

	// ---- the trackball ----

	function trackTick() {
		if (trackVX === 0 && trackVY === 0) { return; }
		trackVX = decay(trackVX); trackVY = decay(trackVY);
		draw();
		trackTimer = setTimeout(trackTick, TRACK_TICK);
	}
	function decay(v) { return v > 0.1 ? v - 0.1 : v < -0.1 ? v + 0.1 : 0; }
	function trackMove(dx, dy) {
		trackDX += dx / SCALE; trackDY += dy / SCALE;
		var sh = shift !== 0;
		function go(cx, cy) { window.luna.key({ cursor: { dx: cx, dy: cy, shift: sh } }); }
		if (trackDX >= TRACK_STEP) { trackDX = 0; trackVX = Math.min(1, trackVX + 0.1); go(1, 0); }
		if (trackDX <= -TRACK_STEP) { trackDX = 0; trackVX = Math.max(-1, trackVX - 0.1); go(-1, 0); }
		if (trackDY <= -TRACK_STEP) { trackDY = 0; trackVY = Math.max(-1, trackVY - 0.1); go(0, -1); }
		if (trackDY >= TRACK_STEP) { trackDY = 0; trackVY = Math.min(1, trackVY + 0.1); go(0, 1); }
		draw();
	}

	// ---- touch ----

	function point(e) { var r = canvas.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; }
	function zoneAt(p) {
		for (var i = 0; i < zones.length; i++) { var z = zones[i]; if (p.x >= z.x && p.x < z.x + z.w && p.y >= z.y && p.y < z.y + z.h) { return z; } }
		return null;
	}
	function stopTimers() { clearTimeout(longPress); clearTimeout(repeatTimer); longPress = null; repeatTimer = null; }

	function repeat() {
		var z = pressedZone;
		if (!z || z.key.fn !== "backspace") { return; }
		repeats++;
		var word = repeats * LETTER_REPEAT > WORD_DELETE_AFTER;
		window.luna.key({ backspace: true, word: word });
		repeatTimer = setTimeout(repeat, word ? WORD_REPEAT : LETTER_REPEAT);
	}

	// The keyboard never takes the page's focus for itself: main.js gives it back to the card.
	canvas.addEventListener("pointerdown", function (e) {
		e.preventDefault();
		var p = point(e); lastX = p.x; lastY = p.y;
		var z = zoneAt(p);
		canvas.setPointerCapture(e.pointerId);
		if (!z) { return; }
		if (z.key.fn === "trackball") { trackball = true; trackDX = 0; trackDY = 0; clearTimeout(trackTimer); return; }
		pressedZone = z;
		if (z.key.fn === "backspace") { repeats = 0; window.luna.key({ backspace: true, word: false }); repeatTimer = setTimeout(repeat, FIRST_REPEAT); }
		// No accents over the emoji layer's keys, nor over its page key (TabletKeymap::getExtendedChars).
		var emojiKey = emojiMode && (emojiSlot.has(z) || z.key.fn === "shift");
		if (z.key.ext && !emojiKey) { longPress = setTimeout(function () { if (pressedZone) { showExtended(pressedZone); } }, HOLD_FOR_EXTENDED); }
		draw();
	});
	canvas.addEventListener("pointermove", function (e) {
		if (!e.buttons) { return; }
		var p = point(e);
		if (trackball) { trackMove(p.x - lastX, p.y - lastY); lastX = p.x; lastY = p.y; return; }
		lastX = p.x; lastY = p.y;
		if (extended) { var i = extendedAt(p.x, p.y); if (i !== extendedIndex) { extendedIndex = i; drawExtended(); } return; }
		// Sliding off a key onto another one moves the press, as webOS allowed.
		var z = zoneAt(p);
		if (z !== pressedZone) { stopTimers(); pressedZone = z; draw(); }
	});
	function release(e, commitIt) {
		stopTimers();
		if (trackball) { trackball = false; trackTimer = setTimeout(trackTick, TRACK_TICK); return; }
		if (commitIt) { commit(); }
		hideExtended();
		pressedZone = null;
		draw();
	}
	canvas.addEventListener("pointerup", function (e) { release(e, true); });
	canvas.addEventListener("pointercancel", function (e) { release(e, false); });

	/** What the key let go of does (KeyboardView.commit). */
	function commit() {
		if (extended) {
			var c = extended[extendedIndex];
			window.luna.key({ text: autoCap && shift === 0 ? c.toUpperCase() : c });
			if (shift === 1) { shift = 0; }
			return;
		}
		var z = pressedZone;
		if (!z) { return; }
		var key = z.key;
		if (emojiMode) {
			if (emojiSlot.has(z)) { var e = emojiFor(z); if (e) { window.luna.key({ emoji: e }); } return; }
			if (key.fn === "shift") { emojiPage = (emojiPage + 1) % emojiPages(); return; }
		}
		switch (key.fn) {
			case "emoji":
				// The layer starts on its first page, with symbols and shift off.
				if (emojiMode) { leaveEmoji(); } else { emojiMode = true; emojiPage = 0; }
				symbols = false; shift = 0;
				return;
			case "shift": {
				var now = Date.now();
				shift = now - lastShiftTap < DOUBLE_TAP ? 2 : shift === 0 ? 1 : 0;
				lastShiftTap = now;
				autoCap = false;
				return;
			}
			// The symbol key leaves the emoji layer for the symbols.
			case "symbol": leaveEmoji(); symbols = !symbols; return;
			case "backspace": return;          // sent on the way down, and repeated since
			case "return": window.luna.key({ enter: true }); return;
			case "tab": window.luna.key({ tab: true }); return;
			case "hide": window.luna.key({ hide: true }); return;
			case "trackball": return;
		}
		// webOS mapped the key first and capitalised whatever came out, which leaves a symbol alone.
		var mapped = usesAlt(key) ? (key.altText || key.alt) : key.main;
		var caps = shift !== 0 || (autoCap && isLetter(mapped[0]));
		window.luna.key({ text: caps ? mapped.toUpperCase() : mapped });
		// One shift, one character; a lock is only ended by the shift key itself.
		if (shift === 1) { shift = 0; }
	}

	// What the field asks for: a capital next (auto-capitalization), and Return's label.
	window.luna.onKeyboardState(function (s) {
		if (!s) { return; }
		if (s.fresh) { shift = 0; symbols = false; leaveEmoji(); hideExtended(); }
		autoCap = !!s.autoCap; enterLabel = s.enterLabel || "Enter";
		draw();
	});
	// A correction was made: the plop (TextAssist.Notifier; the reference TouchPad's systemSound soundOnly).
	var plop = new Audio("../../LunaKeyboard/src/main/assets/assist/text_entry_correction.wav");
	window.luna.onKeyboardPlop(function () { try { plop.currentTime = 0; plop.play(); } catch (e) {} });

	window.luna.onKeyboard(function (d) {
		canvas.hidden = !d.show;
		if (!d.show) { hideExtended(); stopTimers(); }
		document.body.classList.toggle("keyboard", !!d.show);
		if (d.height) { setHeight(d.height); }
		// A field starts the keyboard clean (KeyboardView.reset).
		if (d.show) { shift = 0; symbols = false; leaveEmoji(); trackVX = trackVY = 0; layout(); }
	});
	window.luna.onScreen(function () { setTimeout(layout, 0); });

	// The bezel's keyboard button: a real keyboard attached (lit), or not. Its right button
	// offers the on-screen keyboard's sizes, webOS's four.
	var button = document.getElementById("realkb");
	button.addEventListener("contextmenu", function (e) {
		e.preventDefault();
		window.luna.keyboardSize().then(function (now) {
			var m = document.getElementById("menu");
			m.innerHTML = "";
			[["XS", -2], ["S", -1], ["M (webOS's default)", 0], ["L", 1]].forEach(function (it) {
				var b = document.createElement("button");
				b.textContent = (now.size === it[1] ? "✓ " : " ") + "On-screen keyboard: " + it[0];
				b.addEventListener("click", function () { m.hidden = true; window.luna.keyboardSize(it[1]); });
				m.appendChild(b);
			});
			var r = document.getElementById("screen").getBoundingClientRect();
			m.style.left = "14px";
			m.style.top = (r.height - 170) + "px";
			m.hidden = false;
		});
	});
	button.addEventListener("click", function () {
		window.luna.realKeyboard().then(function (real) {
			button.classList.toggle("on", real);
			button.title = real
				? "A real keyboard is attached: the on-screen keyboard stays away. Click to take it away."
				: "No real keyboard: the on-screen keyboard comes up for a text field. Click to attach one.";
		});
	});
})();
