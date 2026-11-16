// Luna DE's virtual keyboard: webOS's tablet keyboard, drawn from LunaKeyboard's art (the
// device's own pictures; LunaKeyboard/src/main/assets/keyboard, with its NOTICE) and LunaCE's US
// QWERTY tablet keymap as LunaKeyboard ports it (Keymap.kt). The sizes are the art's: 340 px of
// background, 55 px for the number row and 70 for each of the other four, every plate a
// nine-slice with 13 px corners (KeyboardView.kt, Art.kt).
//
// It is up when main.js says so (a field has the focus and no real keyboard is attached), and a
// key typed here goes to the card through main.js. Not here yet, and recorded in
// Docs/luna-de.md: the press-and-hold alternates, the trackball's caret dragging, the emoji
// layer and auto-capitalisation.
"use strict";
(function () {
	var ART = "../../LunaKeyboard/src/main/assets/keyboard/";
	var HEIGHT = 340, ROW_HEIGHTS = [55, 70, 70, 70, 70], CORNER = 13;
	var ACTIVE = "rgb(20,20,20)", FADED = "rgb(100,100,100)", FUNCTION = "rgb(210,210,210)";

	// [weight, main, alt, altText]; a function key is [weight, "name"]. A negative weight is a
	// zone that types its character but draws no key (the home row's left edge types "a").
	function k(w, main, alt, altText) { return { w: w, main: main, alt: alt === undefined ? main : alt, altText: altText }; }
	function fn(w, name) { return { w: w, fn: name }; }
	var ROWS = [
		[k(1, "1", "!"), k(1, "2", "@"), k(1, "3", "#"), k(1, "4", "$"), k(1, "5", "%"), k(1, "6", "^"), k(1, "7", "&"), k(1, "8", "*"), k(1, "9", "("), k(1, "0", ")"), fn(2, "trackball")],
		[k(1, "q", "`"), k(1, "w", "~"), k(1, "e", "€"), k(1, "r", "£"), k(1, "t", "\\"), k(1, "y", "|"), k(1, "u", "{"), k(1, "i", "}"), k(1, "o", "["), k(1, "p", "]"), fn(1, "backspace")],
		[k(-0.5, "a", "<"), k(1, "a", "<"), k(1, "s", ">"), k(1, "d", "="), k(1, "f", "+"), k(1, "g", "×"), k(1, "h", "÷"), k(1, "j", "°"), k(1, "k", ";"), k(1, "l", ":"), fn(1.5, "return")],
		[fn(1, "shift"), k(1, "z", "z", ":-)"), k(1, "x", "x", ";-)"), k(1, "c", "c", ":-("), k(1, "v", "v", ":'("), k(1, "b", "b", ":-P"), k(1, "n", "n", ":-O"), k(1, "m", "m", "<3"), k(1, ",", "/"), k(1, ".", "?"), fn(1, "shift")],
		[fn(1, "tab"), fn(1, "symbol"), fn(1, "emoji"), k(5, " "), k(1, "'", "\""), k(1, "-", "_"), fn(1, "hide")],
	];
	var SYMBOL_LABEL = "+ = [  ]";

	var canvas = document.getElementById("keyboard"), ctx = canvas.getContext("2d");
	var images = {}, zones = [], shift = 0 /* 0 off, 1 once, 2 lock */, symbol = false, pressed = null, lastShift = 0;

	["keyboard-bg", "key-white", "key-gray", "key-gray-short", "key-black", "key-shift-on", "key-shift-lock",
	 "icon-delete", "icon-hide-keyboard", "icon-shift", "icon-shift-on", "icon-shift-lock", "trackball"].forEach(function (name) {
		var img = new Image();
		img.onload = function () { images[name] = img; draw(); };
		img.src = ART + name + ".png";
	});

	function isLetterOrDigit(ch) { return /[0-9a-z]/i.test(ch); }

	function layout() {
		var w = parseInt(getComputedStyle(document.documentElement).getPropertyValue("--w"), 10) || 1024;
		canvas.width = w; canvas.height = HEIGHT;
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
		draw();
	}

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
	function plate(img, z, down) {
		var h = img.height / 2, sy = down ? h : 0;
		var dh = Math.min(CORNER, z.w / 2), dv = Math.min(CORNER, z.h / 2);
		var xs = [0, CORNER, img.width - CORNER, img.width], ys = [sy, sy + CORNER, sy + h - CORNER, sy + h];
		var dx = [z.x, z.x + dh, z.x + z.w - dh, z.x + z.w], dy = [z.y, z.y + dv, z.y + z.h - dv, z.y + z.h];
		for (var i = 0; i < 3; i++) {
			for (var j = 0; j < 3; j++) {
				var sw = xs[i + 1] - xs[i], sh = ys[j + 1] - ys[j], dw = dx[i + 1] - dx[i], dhh = dy[j + 1] - dy[j];
				if (sw > 0 && sh > 0 && dw > 0 && dhh > 0) { ctx.drawImage(img, xs[i], ys[j], sw, sh, dx[i], dy[j], dw, dhh); }
			}
		}
	}

	function label(text, cx, cy, color, size, bold) {
		ctx.font = (bold ? "bold " : "") + size + "px Prelude, sans-serif";
		ctx.fillStyle = color; ctx.textAlign = "center"; ctx.textBaseline = "middle";
		ctx.fillText(text, cx, cy);
	}

	/** Which of a key's two characters it would type now: shift for the number row and the punctuation, the symbol key for the letters. */
	function usesAlt(key) { return isLetterOrDigit(key.main) && !/[0-9]/.test(key.main) ? symbol : shift !== 0; }
	function typed(key) {
		if (key.main === " ") { return " "; }
		if (usesAlt(key)) { return key.altText || key.alt; }
		return shift !== 0 && /[a-z]/.test(key.main) ? key.main.toUpperCase() : key.main;
	}

	function drawKey(z) {
		var key = z.key, down = pressed === z, name = plateFor(z);
		if (name && images[name]) { plate(images[name], z, down); }
		if (key.w < 0) { return; }
		// LunaCE trims the bottom of the cap area, and nudges it down while the key is held.
		var box = { x: z.x, y: z.y + (down ? 2 : 0), w: z.w, h: z.h - 4 }, cx = box.x + box.w / 2, cy = box.y + box.h / 2;
		function icon(n) { var img = images[n]; if (img) { ctx.drawImage(img, Math.round(cx - img.width / 2), Math.round(cy - img.height / 2)); } }
		switch (key.fn) {
			case "trackball": icon("trackball"); return;
			case "backspace": icon("icon-delete"); return;
			case "hide": icon("icon-hide-keyboard"); return;
			case "shift": icon(shift === 2 ? "icon-shift-lock" : shift === 1 ? "icon-shift-on" : "icon-shift"); return;
			case "tab": label("Tab", cx, cy, FUNCTION, 22, true); return;
			case "symbol": label(SYMBOL_LABEL, cx, cy, FUNCTION, 22, true); return;
			case "emoji": label("☺", cx, cy, FUNCTION, 22, false); return;
			case "return":
				// Return's label sits small in the bottom right corner, as it did on webOS.
				ctx.font = "bold 22px Prelude, sans-serif"; ctx.fillStyle = FUNCTION; ctx.textAlign = "right"; ctx.textBaseline = "alphabetic";
				ctx.fillText("Enter", box.x + box.w * 0.85, box.y + box.h * 0.8);
				return;
		}
		if (key.main === " ") { return; }
		var alt = key.altText || (key.alt !== key.main ? key.alt : null), live = usesAlt(key);
		var face = shift !== 0 && /[a-z]/.test(key.main) ? key.main.toUpperCase() : key.main;
		var across = z.row === 0 && alt, stacked = z.row > 0 && alt && !isLetterOrDigit(key.main);
		if (!across && !stacked) {
			var t = live && alt ? alt : face;
			label(t, cx, cy, ACTIVE, t.length > 1 ? 22 : 26, t.length > 1);
			return;
		}
		// The character that would be typed takes the dark colour; the other fades back.
		var mainColor = live ? FADED : ACTIVE, altColor = live ? ACTIVE : FADED;
		if (across) {
			label(face, box.x + box.w * 0.34, cy + 1, mainColor, 24, false);
			label(alt, box.x + box.w * 0.68, cy + 1, altColor, 24, false);
		} else {
			label(alt, cx, box.y + box.h * 0.32, altColor, 24, false);
			label(face, cx, box.y + box.h * 0.68, mainColor, 24, false);
		}
	}

	function draw() {
		if (canvas.hidden) { return; }
		ctx.clearRect(0, 0, canvas.width, canvas.height);
		if (images["keyboard-bg"]) { ctx.drawImage(images["keyboard-bg"], 0, 0, canvas.width, HEIGHT); }
		zones.forEach(drawKey);
	}

	function zoneAt(e) {
		var r = canvas.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
		for (var i = 0; i < zones.length; i++) { var z = zones[i]; if (x >= z.x && x < z.x + z.w && y >= z.y && y < z.y + z.h) { return z; } }
		return null;
	}

	function press(z) {
		var key = z.key;
		switch (key.fn) {
			case "shift": {
				// A second tap soon after the first locks it (KeyboardView's DOUBLE_TAP, 500 ms).
				var now = Date.now();
				shift = shift === 2 ? 0 : (shift === 1 && now - lastShift < 500 ? 2 : shift === 1 ? 0 : 1);
				lastShift = now;
				return;
			}
			case "symbol": symbol = !symbol; return;
			case "backspace": window.lunade.key({ code: "Backspace" }); return;
			case "return": window.lunade.key({ code: "Return" }); return;
			case "tab": window.lunade.key({ code: "Tab" }); return;
			case "hide": window.lunade.key({ hide: true }); return;
			case "trackball": case "emoji": return;
		}
		window.lunade.key({ text: typed(key) });
		if (shift === 1) { shift = 0; }
	}

	// The keyboard never takes the page's focus for itself: main.js gives it back to the card.
	canvas.addEventListener("pointerdown", function (e) {
		e.preventDefault();
		pressed = zoneAt(e);
		canvas.setPointerCapture(e.pointerId);
		draw();
	});
	canvas.addEventListener("pointerup", function (e) {
		var z = zoneAt(e), was = pressed;
		pressed = null;
		if (was && z === was) { press(was); }
		draw();
	});

	window.lunade.onKeyboard(function (d) {
		canvas.hidden = !d.show;
		document.body.classList.toggle("keyboard", !!d.show);
		if (d.show) { shift = 0; symbol = false; layout(); }
	});
	window.lunade.onScreen(function () { setTimeout(layout, 0); });

	// The bezel's keyboard button: a real keyboard attached (lit), or not.
	var button = document.getElementById("realkb");
	button.addEventListener("click", function () {
		window.lunade.realKeyboard().then(function (real) {
			button.classList.toggle("on", real);
			button.title = real
				? "A real keyboard is attached: the on-screen keyboard stays away. Click to take it away."
				: "No real keyboard: the on-screen keyboard comes up for a text field. Click to attach one.";
		});
	});
})();
