// Luna DE's shell: one window as a TouchPad's screen, the status bar, the launcher, apps as
// cards and the card view (Docs/luna-de.md, milestone 3b). Its geometry is AndroidLuna's
// (CardLayer.Params, StatusBar, QuickLaunch). For now it keeps its own list of the cards; the
// shell-logic moves (AppLifecycle, CardHost's decisions) replace that with LunaCore's.
"use strict";
(function () {
	var W = 1024, AREA = 740, STATUS = 28;                 // the card area, below the status bar
	var RESERVE = 48, ORIGIN = 0.40, GAP = 30;             // CardLayer.Params: PILL_RESERVE, ORIGIN_RATIO, GAP
	var ACTIVE = (AREA - RESERVE) * 0.55 / AREA;           // ACTIVE_RATIO, as activeScale() works it out
	var OTHER = (AREA - RESERVE) * 0.50 / AREA;            // NON_ACTIVE_RATIO
	var ORIGIN_Y = RESERVE + (AREA - RESERVE) * ORIGIN;    // originY() within the card area
	var TAP = 25, THROW_DISTANCE = -50, THROW_VELOCITY = -1.1;   // TAP_RADIUS; THROW_*, px and px/ms
	var cards = [], active = -1, maximized = false;
	var $ = function (id) { return document.getElementById(id); };

	// ---- the cards ----
	// A card here is the shell's picture of an app window: main.js keeps its live view, shown
	// over the card area while it is up and hidden in the card view, where this draws its
	// snapshot at CardLayer's place and scale.

	function cardIndex(id) { for (var i = 0; i < cards.length; i++) { if (cards[i].id === id) { return i; } } return -1; }

	function transform(c, x, y, s) { c.el.style.transform = "translate(" + x + "px," + y + "px) scale(" + s + ")"; }

	/** CardWindowManager's slideAllGroups: the active card in the middle, the rest either side. */
	function layout(animate, slide) {
		slide = slide || 0;
		var cy = ORIGIN_Y - AREA / 2;
		cards.forEach(function (c, i) {
			c.el.classList.toggle("animate", !!animate);
			var s = i === active ? ACTIVE : OTHER, x;
			if (i === active) { x = 0; }
			else if (i < active) { x = -(W * ACTIVE / 2 + GAP + W * OTHER / 2) - (active - 1 - i) * (W * OTHER + GAP); }
			else { x = (W * ACTIVE / 2 + GAP + W * OTHER / 2) + (i - active - 1) * (W * OTHER + GAP); }
			transform(c, x + slide, cy, s);
			c.el.style.opacity = "1";
			c.el.style.zIndex = i === active ? 2 : 1;
		});
	}

	function setTitle() {
		$("title-text").textContent = maximized && cards[active] ? cards[active].title : "Luna DE";
	}

	/** A card comes up: its picture grows to the screen, then its live view takes over. */
	function maximize(i) {
		var c = cards[i];
		if (!c) { return; }
		active = i; maximized = true;
		closeLauncher();
		document.body.classList.add("maximized");
		cards.forEach(function (k, j) {
			k.el.classList.add("animate");
			if (j === i) { transform(k, 0, 0, 1); k.el.style.zIndex = 3; }
			else { transform(k, (j < i ? -1 : 1) * (W + GAP), 0, 1); }
		});
		setTitle();
		setTimeout(function () { if (maximized && cards[active] === c) { window.lunade.maximize(c.id); } }, 300);
	}

	/** Down to the card view: the live card's picture taken first, so it shrinks from what was on the screen. */
	function cardView() {
		var c = maximized ? cards[active] : null;
		maximized = false;
		document.body.classList.remove("maximized");
		setTitle();
		var done = function () { window.lunade.cardView(); layout(true); refreshPictures(c); };
		if (!c) { done(); return; }
		window.lunade.snapshot(c.id).then(function (url) { if (url) { c.img.src = url; } done(); }, done);
	}

	/** Every card's picture taken again, but [skip]'s, just taken: a page goes on drawing while hidden. */
	function refreshPictures(skip) {
		cards.forEach(function (k) {
			if (k === skip) { return; }
			window.lunade.snapshot(k.id).then(function (url) { if (url) { k.img.src = url; } });
		});
	}

	/** A card main.js has made (a launch, or an app's window.open): it comes in at the active place and waits for its page. */
	function added(d) {
		closeLauncher();
		var el = document.createElement("div");
		el.className = "card";
		var img = document.createElement("img");
		img.className = "picture";
		img.alt = "";
		var cover = document.createElement("div");
		cover.className = "cover";
		el.appendChild(img); el.appendChild(cover);
		var c = { id: d.id, appId: d.appId, title: d.title, el: el, img: img };
		if (maximized) { window.lunade.cardView(); maximized = false; document.body.classList.remove("maximized"); }
		cards.splice(active + 1, 0, c);
		$("cards").appendChild(el);
		active = cards.indexOf(c);
		el.classList.remove("animate");
		transform(c, 0, AREA, ACTIVE);
		el.getBoundingClientRect();
		layout(true);
		setTitle();
		wire(c);
		// Up once its page is in, or after a moment anyway.
		c.timer = setTimeout(function () { ready({ id: c.id }); }, 2500);
	}

	function ready(d) {
		var i = cardIndex(d.id);
		if (i < 0 || cards[i].up) { return; }
		cards[i].up = true;
		clearTimeout(cards[i].timer);
		setTimeout(function () { var k = cardIndex(d.id); if (k >= 0) { maximize(k); } }, 150);
	}

	/** A card gone (closed here, by its page, or popped out): its picture leaves upwards. */
	function removed(d) {
		var i = cardIndex(d.id);
		if (i < 0) { return; }
		var c = cards[i], wasUp = maximized && i === active;
		c.el.classList.add("animate");
		transform(c, 0, -AREA, wasUp ? 1 : ACTIVE);
		c.el.style.opacity = "0";
		cards.splice(i, 1);
		setTimeout(function () { c.el.remove(); }, 320);
		if (active >= cards.length) { active = cards.length - 1; }
		if (wasUp) { maximized = false; document.body.classList.remove("maximized"); window.lunade.cardView(); }
		layout(true);
		setTitle();
	}

	function closeCard(i) { if (cards[i]) { window.lunade.close(cards[i].id); } }

	/** A card's cover in the card view: tap to bring it up, flick up to close, drag sideways to slide, right-click for Luna DE's tools. */
	function wire(c) {
		var cover = c.el.querySelector(".cover");
		cover.addEventListener("pointerdown", function (e) {
			if (e.button !== 0 || maximized) { return; }
			var x0 = e.clientX, y0 = e.clientY, axis = null, i = cards.indexOf(c);
			var lastY = y0, lastT = e.timeStamp, vy = 0;
			cover.setPointerCapture(e.pointerId);
			function move(ev) {
				var dx = ev.clientX - x0, dy = ev.clientY - y0;
				if (!axis && Math.hypot(dx, dy) > 15) { axis = Math.abs(dx) > 0.866 * Math.abs(dy) ? "x" : "y"; }
				if (ev.timeStamp > lastT) { vy = (ev.clientY - lastY) / (ev.timeStamp - lastT); lastY = ev.clientY; lastT = ev.timeStamp; }
				if (axis === "y" && i === active) {
					c.el.classList.remove("animate");
					transform(c, 0, ORIGIN_Y - AREA / 2 + Math.min(0, dy), ACTIVE);
				} else if (axis === "x") {
					layout(false, dx);
				}
			}
			function up(ev) {
				cover.removeEventListener("pointermove", move);
				cover.removeEventListener("pointerup", up);
				var dx = ev.clientX - x0, dy = ev.clientY - y0;
				if (!axis && Math.hypot(dx, dy) <= TAP) {
					if (i === active) { maximize(i); } else { active = i; layout(true); }
				} else if (axis === "y" && i === active) {
					if (dy < THROW_DISTANCE * 3 || vy < THROW_VELOCITY) { closeCard(i); } else { layout(true); }
				} else if (axis === "x") {
					var step = Math.round(-dx / (W * OTHER + GAP));
					active = Math.max(0, Math.min(cards.length - 1, active + step));
					layout(true);
				}
			}
			cover.addEventListener("pointermove", move);
			cover.addEventListener("pointerup", up);
		});
		cover.addEventListener("contextmenu", function (e) {
			e.preventDefault();
			showMenu(e.clientX, e.clientY, c);
		});
	}

	// ---- Luna DE's tools for a card ----

	function showMenu(x, y, c) {
		var m = $("menu");
		m.innerHTML = "";
		[["Open", function () { maximize(cards.indexOf(c)); }],
		 ["Pop out to its own window", function () { window.lunade.popOut(c.id); }],
		 ["DevTools", function () { window.lunade.devTools(c.id); }],
		 ["Reload", function () { window.lunade.reload(c.id); }],
		 ["Close", function () { closeCard(cards.indexOf(c)); }]].forEach(function (it) {
			var b = document.createElement("button");
			b.textContent = it[0];
			b.addEventListener("click", function () { m.hidden = true; it[1](); });
			m.appendChild(b);
		});
		m.style.left = Math.min(x, W - 220) + "px";
		m.style.top = Math.min(y, 768 - 220) + "px";
		m.hidden = false;
	}
	document.addEventListener("pointerdown", function (e) { if (!$("menu").contains(e.target)) { $("menu").hidden = true; } }, true);

	// ---- the launcher and the quick-launch bar ----

	var pages = [], page = 0;

	function icon(entry) {
		var img = document.createElement("img");
		img.alt = "";
		img.src = entry.icon;
		img.onerror = function () { img.onerror = null; img.src = "../../AndroidLuna/src/main/assets/luna/images/default-app-icon.png"; };
		return img;
	}

	function showPage(n) {
		page = n;
		[].forEach.call($("tabs").children, function (b, i) { b.classList.toggle("selected", i === n); });
		var grid = $("grid");
		grid.innerHTML = "";
		(pages[n] ? pages[n].apps : []).forEach(function (a) {
			var t = document.createElement("button");
			t.className = "tile";
			t.title = a.id;
			t.appendChild(icon(a));
			var s = document.createElement("span"); s.textContent = a.title; t.appendChild(s);
			t.addEventListener("click", function () { launch(a); });
			grid.appendChild(t);
		});
	}

	/** An app launched from the launcher or the bar: main.js starts it, or tells us its card to bring up. */
	function launch(entry) { closeLauncher(); window.lunade.launch(entry); }

	function openLauncher() {
		if (maximized) { cardView(); }
		$("launcher").hidden = false;
		document.body.classList.add("launcher");
		$("title-text").textContent = "Launcher";
	}
	function closeLauncher() {
		$("launcher").hidden = true;
		document.body.classList.remove("launcher");
		setTitle();
	}
	$("launcher-button").addEventListener("click", function () {
		if ($("launcher").hidden) { openLauncher(); } else { closeLauncher(); }
	});

	/** The home button (the bezel's, or F1): up from an app to the card view, or away from the launcher. */
	function home() {
		$("menu").hidden = true;
		if (!$("launcher").hidden) { closeLauncher(); }
		else if (maximized) { cardView(); }
	}
	$("home").addEventListener("click", home);
	document.addEventListener("keydown", function (e) { if (e.key === "F1") { e.preventDefault(); home(); } });

	// The card view slides with the wheel, as with a finger.
	$("cards").addEventListener("wheel", function (e) {
		if (maximized || !cards.length) { return; }
		var d = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
		if (Math.abs(d) < 20) { return; }
		active = Math.max(0, Math.min(cards.length - 1, active + (d > 0 ? 1 : -1)));
		layout(true);
	}, { passive: true });

	// ---- the status bar's clock ----

	function tick() {
		var d = new Date(), h = d.getHours() % 12 || 12, m = d.getMinutes();
		$("clock").textContent = h + ":" + (m < 10 ? "0" : "") + m;
	}
	tick(); setInterval(tick, 5000);

	// ---- start ----

	window.lunade.onHome(home);
	window.lunade.onCardAdded(added);
	window.lunade.onCardReady(ready);
	window.lunade.onCardRemoved(removed);
	window.lunade.onCardTitle(function (d) { var i = cardIndex(d.id); if (i >= 0) { cards[i].title = d.title; setTitle(); } });
	window.lunade.onShow(function (d) { var i = cardIndex(d.id); if (i >= 0) { maximize(i); } });
	window.lunade.shell().then(function (s) {
		pages = s.pages;
		var tabs = $("tabs");
		pages.forEach(function (p, i) {
			var b = document.createElement("button");
			b.textContent = p.title;
			b.addEventListener("click", function () { showPage(i); });
			tabs.appendChild(b);
		});
		showPage(0);
		var dock = $("dock");
		s.dock.forEach(function (a) {
			var b = document.createElement("button");
			b.title = a.title;
			b.appendChild(icon(a));
			b.addEventListener("click", function () { launch(a); });
			dock.appendChild(b);
		});
		window.lunade.ready();
	});
})();
