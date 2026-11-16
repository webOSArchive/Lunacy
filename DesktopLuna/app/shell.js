// Luna DE's shell: one window as a TouchPad's screen, the status bar, the launcher, apps as
// cards and the card view (Docs/luna-de.md, milestone 3b). Its geometry is AndroidLuna's
// (CardLayer.Params, StatusBar, QuickLaunch). For now it keeps its own list of the cards; the
// shell-logic moves (AppLifecycle, CardHost's decisions) replace that with LunaCore's.
"use strict";
(function () {
	var STATUS = 28, RESERVE = 48, ORIGIN = 0.40, GAP = 30; // StatusBar.HEIGHT; CardLayer.Params: PILL_RESERVE, ORIGIN_RATIO, GAP
	var W, H, AREA, ACTIVE, OTHER, ORIGIN_Y;
	/** The screen's size, and what follows from it: the card area below the status bar and CardLayer's ratios. */
	function measure(w, h) {
		W = w; H = h; AREA = h - STATUS;
		ACTIVE = (AREA - RESERVE) * 0.55 / AREA;           // ACTIVE_RATIO, as activeScale() works it out
		OTHER = (AREA - RESERVE) * 0.50 / AREA;            // NON_ACTIVE_RATIO
		ORIGIN_Y = RESERVE + (AREA - RESERVE) * ORIGIN;    // originY() within the card area
		document.documentElement.style.setProperty("--w", w + "px");
		document.documentElement.style.setProperty("--h", h + "px");
		document.documentElement.style.setProperty("--columns", w >= 1024 ? 6 : w >= 700 ? 4 : 3);
		layoutDock();
	}
	/**
	 * Just Type's pill and the quick-launch bar at this screen's size, by AndroidLuna's rules
	 * (JustType, QuickLaunch): a phone's bar is 95 high and holds four apps and the launcher button
	 * in five equal slots, its icons 59 px or 78 % of a slot; a tablet's is 100 high with 64 px
	 * icons in the space left of the launcher button.
	 */
	function layoutDock() {
		var root = document.documentElement.style, phone = document.body.classList.contains("phone");
		var count = Math.max(1, document.querySelectorAll("#dock button").length);
		root.setProperty("--pill-w", Math.round(Math.min(W, H) * 0.75) + "px");
		if (phone) {
			var slot = W / (count + 1), size = Math.min(59, slot * 0.78);
			root.setProperty("--ql", "95px"); root.setProperty("--dock-y", "51px");
			root.setProperty("--dock-w", (slot * count) + "px"); root.setProperty("--dock-icon", size + "px");
			root.setProperty("--dock-button-x", (slot * count + (slot - size) / 2) + "px");
		} else {
			root.setProperty("--ql", "100px"); root.setProperty("--dock-y", "54px"); root.setProperty("--dock-icon", "64px");
			root.setProperty("--dock-w", (W - 64 - 31) + "px"); root.setProperty("--dock-button-x", (W - 64 - 32) + "px");
		}
	}
	measure(1024, 768);
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

	/** The status bar's title: the app's own name while its card is up, as the TouchPad showed it (not the page's title). */
	function setTitle() {
		$("popout").disabled = !(cards[active] && cards[active].id);
		$("devtools").disabled = $("popout").disabled;
		$("title-text").textContent = maximized && cards[active] ? (appTitles[cards[active].appId] || cards[active].title) : "Luna DE";
	}
	var appTitles = {}, appEntries = {};

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
		setTimeout(function () { if (maximized && cards[active] === c) { showLive(c); } }, MAXIMIZE_MS);
	}

	/**
	 * The card that is up shows its live page, once it has one that is ready: until then the
	 * loading card stays on the screen. The splash fades out over the page (CardSplash.dismiss).
	 */
	function showLive(c) {
		if (!c.id || !c.pageReady || !maximized || cards[active] !== c) { return; }
		window.lunade.maximize(c.id);
		if (c.splash) { var sp = c.splash; c.splash = null; sp.classList.add("gone"); setTimeout(function () { sp.remove(); }, 320); }
	}

	/** Down to the card view: the live card's picture taken first, so it shrinks from what was on the screen. */
	function cardView() {
		var c = maximized ? cards[active] : null;
		maximized = false;
		document.body.classList.remove("maximized");
		setTitle();
		var done = function () { window.lunade.cardView(); layout(true); refreshPictures(c); };
		if (!c || !c.id || c.splash) { done(); return; }
		window.lunade.snapshot(c.id).then(function (url) { if (url) { c.img.src = url; } done(); }, done);
	}

	/** Every card's picture taken again, but [skip]'s, just taken: a page goes on drawing while hidden. */
	function refreshPictures(skip) {
		cards.forEach(function (k) {
			if (k === skip || !k.id || k.splash) { return; }
			window.lunade.snapshot(k.id).then(function (url) { if (url) { k.img.src = url; } });
		});
	}

	var MAXIMIZE_MS = 300, SLIDE_MS = 300;   // cardMaximizeDuration, cardSlideDuration (CardLayer.Params)

	/**
	 * A new card, in the shell's picture: its loading card with the app's splash icon (CardSplash),
	 * coming up from below the screen into the card view (CardLayer.openLaunching). [entry] is the
	 * app's launcher entry when the shell has it.
	 */
	function newCard(appId, title) {
		var el = document.createElement("div");
		el.className = "card";
		var img = document.createElement("img");
		img.className = "picture";
		img.alt = "";
		var cover = document.createElement("div");
		cover.className = "cover";
		el.appendChild(img);
		var c = { id: 0, appId: appId, title: title, el: el, img: img, pageReady: false, splash: null, born: performance.now() };
		var entry = appEntries[appId];
		if (entry) {
			var splash = document.createElement("div");
			splash.className = "splash";
			var icon = document.createElement("img");
			icon.alt = "";
			// Its own splashicon at SplashIconSize (192); else the launcher icon at half again, capped there.
			icon.onload = function () {
				var longest = Math.max(icon.naturalWidth, icon.naturalHeight) || 1;
				var want = entry.splashOwn ? 192 : Math.min(192, longest * 1.5);
				icon.style.width = Math.round(icon.naturalWidth * want / longest) + "px";
				icon.style.height = Math.round(icon.naturalHeight * want / longest) + "px";
			};
			icon.src = entry.splash;
			splash.appendChild(icon);
			el.appendChild(splash);
			c.splash = splash;
		}
		el.appendChild(cover);
		if (maximized) { window.lunade.cardView(); maximized = false; document.body.classList.remove("maximized"); }
		cards.splice(active + 1, 0, c);
		$("cards").appendChild(el);
		active = cards.indexOf(c);
		// setActiveCardOffScreen: full size, its top at the bottom of the screen; then up into the card view.
		el.classList.remove("animate");
		transform(c, 0, AREA, 1);
		el.getBoundingClientRect();
		layout(true);
		setTitle();
		wire(c);
		return c;
	}

	/**
	 * A tap on an app that has no card: its loading card is on the screen at once, before the
	 * host has been asked, and takes the app's first card when main.js makes it. One that no
	 * card ever claims (the app only raised a dashboard, or couldn't start) goes again.
	 */
	function launching(entry) {
		if (cards.some(function (c) { return c.appId === entry.id; })) { return; }
		var c = newCard(entry.id, entry.title);
		c.waiting = setTimeout(function () { if (!c.id) { dropCard(c, false); } }, 10000);
	}

	/** A card main.js has made (a launch, or an app's window.open): it takes the loading card the tap put up, or comes in with its own. */
	function added(d) {
		closeLauncher();
		clearGlow();
		var c = cards.filter(function (k) { return !k.id && k.appId === d.appId; })[0] || newCard(d.appId, d.title);
		clearTimeout(c.waiting);
		c.id = d.id;
		setTitle();
		// Up once its page is in, or after a while anyway.
		c.timer = setTimeout(function () { ready({ id: c.id }); }, 15000);
	}

	/** The card's page has loaded: the card maximizes by itself, as a launching card does (CardLayer.ready), and its page takes the splash's place. */
	function ready(d) {
		var i = cardIndex(d.id);
		if (i < 0 || cards[i].pageReady) { return; }
		var c = cards[i];
		c.pageReady = true;
		clearTimeout(c.timer);
		if (maximized && cards[active] === c) { showLive(c); return; }
		// Not before it has reached the card view: the launch is seen to arrive there first.
		var wait = Math.max(0, SLIDE_MS - (performance.now() - c.born));
		setTimeout(function () { var k = cards.indexOf(c); if (k >= 0 && !maximized) { maximize(k); } }, wait);
	}

	function dropCard(c, wasUp) {
		var i = cards.indexOf(c);
		if (i < 0) { return; }
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

	/** A card gone (closed here, by its page, or popped out): its picture leaves upwards. */
	function removed(d) {
		var i = cardIndex(d.id);
		if (i >= 0) { dropCard(cards[i], maximized && i === active); }
	}

	function closeCard(i) { if (!cards[i]) { return; } if (cards[i].id) { window.lunade.close(cards[i].id); } else { dropCard(cards[i], false); } }

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
		showItems(x, y, [["Open", function () { maximize(cards.indexOf(c)); }],
		 ["Pop out to its own window", function () { window.lunade.popOut(c.id); }],
		 ["DevTools", function () { window.lunade.devTools(c.id); }],
		 ["Reload", function () { window.lunade.reload(c.id); }],
		 ["Close", function () { closeCard(cards.indexOf(c)); }]]);
	}

	function showItems(x, y, items) {
		var m = $("menu");
		m.innerHTML = "";
		items.forEach(function (it) {
			var b = document.createElement("button");
			b.textContent = it[0];
			b.addEventListener("click", function () { m.hidden = true; it[1](); });
			m.appendChild(b);
		});
		m.style.left = Math.min(x, W - 220) + "px";
		m.style.top = Math.min(y, H - 220) + "px";
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
			t.addEventListener("click", function () { launch(a, t); });
			// An installed app's tile: Luna DE's Remove, by the right button.
			t.addEventListener("contextmenu", function (e) {
				e.preventDefault();
				if (a.removable) { showItems(e.clientX, e.clientY, [["Remove " + a.title, function () { window.lunade.remove(a.id); }]]); }
			});
			grid.appendChild(t);
		});
	}

	/**
	 * An app launched from the launcher or the bar. What the person sees comes first, before
	 * anything is asked of the host: the tapped icon glows, and an app with no card gets its loading
	 * card. Then the host starts it, or relaunches it and has its card brought up.
	 */
	function launch(entry, tapped) {
		glow(tapped);
		window.lunade.launch(entry);
		// The Wi-Fi tile is Luna DE's network switch, not an app: nothing loads, so no loading card.
		var running = cards.some(function (c) { return c.appId === entry.id; }) || entry.switch;
		// The launcher stays, glowing, for the moment the icon needs to be seen (then the card).
		setTimeout(function () { closeLauncher(); if (!running) { launching(entry); } }, tapped && !$("launcher").hidden ? 120 : 0);
	}

	var glowing = null, glowTimer = null;
	function glow(el) {
		clearGlow();
		if (!el) { return; }
		glowing = el; el.classList.add("glow");
		glowTimer = setTimeout(clearGlow, 3000);   // IconFeedbackTimeout
	}
	function clearGlow() {
		clearTimeout(glowTimer);
		if (glowing) { glowing.classList.remove("glow"); glowing = null; }
	}

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
	// The developer tools of that same card, where its console's logs are.
	$("devtools").addEventListener("click", function () { var c = cards[active]; if (c && c.id) { window.lunade.devTools(c.id); } });
	// The bezel's other corner: the card that is up, or in front in the card view, popped out.
	$("popout").addEventListener("click", function () { var c = cards[active]; if (c && c.id) { window.lunade.popOut(c.id); } });
	// And the device itself: a TouchPad or a Pre3, by its picture.
	$("device").addEventListener("click", function () { window.lunade.device(); });
	/** The screen as main.js gives it (in this page's px), the device's picture, and what the device hasn't got. */
	function applyScreen(d) {
		var wasPhone = document.body.classList.contains("phone");
		document.body.classList.toggle("phone", d.device === "pre3");
		measure(d.width, d.height);
		document.body.classList.toggle("portrait", d.height > d.width);
		if (wasPhone !== (d.device === "pre3") && pages.length) { loadApps(false); }
		// The bezel is Luna DE's own, not the device's screen: it keeps its size at any scale.
		if (d.bezel) { document.documentElement.style.setProperty("--unzoom", String(d.bezel / 44)); }
		$("device").querySelector("img").src = "images/device-" + (d.device || "touchpad") + ".png";
		$("realkb").style.display = d.virtualKeyboard === false ? "none" : "";
	}
	window.lunade.screenNow().then(applyScreen);
	// The bezel's corner: the device turned between wide and tall.
	$("rotate").addEventListener("click", function () { window.lunade.rotate(); });
	document.addEventListener("keydown", function (e) {
		if (e.key === "F1") { e.preventDefault(); home(); }
		// Left and Right switch cards in the card view (the SDK emulator's keys).
		else if ((e.key === "ArrowLeft" || e.key === "ArrowRight") && !maximized && cards.length && $("launcher").hidden) {
			active = Math.max(0, Math.min(cards.length - 1, active + (e.key === "ArrowRight" ? 1 : -1)));
			layout(true);
		} else if (e.key === "Enter" && !maximized && cards[active] && $("launcher").hidden) { maximize(active); }
	});
	// Home (the emulator's key): the card on the screen down to the card view, and up again.
	function toggleCard() {
		if (!$("launcher").hidden) { closeLauncher(); }
		else if (maximized) { cardView(); }
		else if (cards[active]) { maximize(active); }
	}
	// The title is the app menu's, while a card is up (the TouchPad's status bar).
	$("title").addEventListener("click", function () { if (maximized) { window.lunade.appMenu(); } });

	// ---- notifications: banners, the dashboards' drop-down, popup alerts ----

	var panels = [], dashOpen = false, banners = [], bannerOn = null, bannerTimer = null;
	var HOLD_ALONE = 5000, HOLD_QUEUED = 2000, SHOW_MS = 1000, HIDE_MS = 1000;   // Notifications.kt

	function dashboards() { return panels.filter(function (p) { return p.kind === "dashboard"; }); }

	/** The status bar's notification group: there while a banner shows or a dashboard is open. */
	function drawNotes() {
		var d = dashboards(), icons = $("note-icons");
		icons.innerHTML = "";
		d.forEach(function (p) { var i = document.createElement("img"); i.alt = ""; i.src = p.icon; icons.appendChild(i); });
		$("notes").hidden = !d.length && !bannerOn;
		$("notes").querySelector(".arrow").style.display = d.length ? "" : "none";
		if (!d.length) { dashOpen = false; }
		placePanels();
	}

	/** Where each dashboard and alert is on the screen: the frames drawn here, the live views laid over them by main.js. */
	function placePanels() {
		var rects = [], d = dashboards(), box = $("dashboards");
		box.hidden = !(dashOpen && d.length);
		if (!box.hidden) {
			box.innerHTML = "";
			d.forEach(function (p) {
				var row = document.createElement("div"); row.className = "row";
				var x = document.createElement("button"); x.className = "dismiss"; x.textContent = "\u00d7"; x.title = "Dismiss";
				x.addEventListener("click", function () { window.lunade.close(p.id); });
				row.appendChild(x); box.appendChild(row);
			});
			var right = W - $("notes").getBoundingClientRect().right;
			box.style.right = Math.max(0, right - 11) + "px";
			box.style.top = STATUS + "px";
			[].forEach.call(box.children, function (row, i) {
				var r = row.getBoundingClientRect();
				rects.push({ id: d[i].id, x: r.left, y: r.top + (i ? 2 : 0), width: 320, height: 52 });
			});
		}
		var alerts = panels.filter(function (p) { return p.kind === "popupalert"; }), a = alerts[alerts.length - 1], frame = $("alert");
		frame.hidden = !a;
		if (a) {
			frame.style.height = (a.height + 40) + "px";
			frame.style.right = "8px";
			frame.style.top = (STATUS + (box.hidden ? 4 : box.getBoundingClientRect().height + 4)) + "px";
			var f = frame.getBoundingClientRect();
			rects.push({ id: a.id, x: f.left + 20, y: f.top + 20, width: 320, height: a.height });
		}
		window.lunade.place(rects, dashOpen && d.length > 0);
	}

	$("notes").addEventListener("click", function () {
		if (bannerOn && bannerOn.appId) { var b = bannerOn; endBanner(); window.lunade.launch({ id: b.appId, params: parse(b.params) }); return; }
		if (dashboards().length) { dashOpen = !dashOpen; placePanels(); }
	});
	document.addEventListener("pointerdown", function (e) {
		if (dashOpen && !$("dashboards").contains(e.target) && !$("notes").contains(e.target)) { dashOpen = false; placePanels(); }
	}, true);

	function parse(text) { try { return text ? JSON.parse(text) : undefined; } catch (e) { return undefined; } }

	/** Banners one at a time: in over a second, held (longer when nothing waits), then faded and gone. */
	function nextBanner() {
		if (bannerOn || !banners.length) { return; }
		bannerOn = banners.shift();
		var el = $("banner");
		$("banner-text").textContent = bannerOn.message;
		$("banner-icon").src = bannerOn.icon || "";
		$("banner-icon").style.display = bannerOn.icon ? "" : "none";
		el.hidden = false; el.className = "in";
		drawNotes();
		bannerTimer = setTimeout(function () {
			el.className = "out";
			bannerTimer = setTimeout(endBanner, HIDE_MS);
		}, SHOW_MS + (banners.length ? HOLD_QUEUED : HOLD_ALONE));
	}
	function endBanner() {
		clearTimeout(bannerTimer);
		bannerOn = null;
		$("banner").hidden = true; $("banner").className = "";
		drawNotes();
		nextBanner();
	}

	// The card view slides with the wheel, as with a finger.
	$("cards").addEventListener("wheel", function (e) {
		if (maximized || !cards.length) { return; }
		var d = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
		if (Math.abs(d) < 20) { return; }
		active = Math.max(0, Math.min(cards.length - 1, active + (d > 0 ? 1 : -1)));
		layout(true);
	}, { passive: true });

	// ---- installing: a package or an app's folder dropped on the device ----

	document.addEventListener("dragover", function (e) { e.preventDefault(); e.dataTransfer.dropEffect = "copy"; });
	document.addEventListener("drop", function (e) {
		e.preventDefault();
		for (var i = 0; i < e.dataTransfer.files.length; i++) { window.lunade.install(e.dataTransfer.files[i]); }
	});

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
	window.lunade.onShow(function (d) { var i = cardIndex(d.id); if (i >= 0) { maximize(i); } });
	window.lunade.onToggleCard(toggleCard);
	window.lunade.onLauncher(function () { if ($("launcher").hidden) { openLauncher(); } else { closeLauncher(); } });
	window.lunade.onScreen(function (d) {
		applyScreen(d);
		if (maximized) { cards.forEach(function (k, j) { k.el.classList.remove("animate"); transform(k, j === active ? 0 : (j < active ? -1 : 1) * (W + GAP), 0, 1); }); }
		else { layout(false); }
		placePanels();
	});
	window.lunade.onPanelAdded(function (d) { panels.push(d); drawNotes(); });
	window.lunade.onPanelRemoved(function (d) { panels = panels.filter(function (p) { return p.id !== d.id; }); drawNotes(); });
	window.lunade.onBanner(function (d) { banners.push(d); nextBanner(); });
	window.lunade.onBannerRemove(function (d) {
		banners = banners.filter(function (b) { return d.id ? b.id !== d.id : b.appId !== d.appId; });
		if (bannerOn && (d.id ? bannerOn.id === d.id : bannerOn.appId === d.appId)) { endBanner(); }
	});

	// The simulated network (the Wi-Fi tile): the status bar's Wi-Fi icon shows it gone.
	window.lunade.onNetwork(function (d) { document.querySelector("#status-right .wifi").classList.toggle("off", !d.online); });

	/** The launcher's pages and the quick-launch bar, drawn from what the host has installed. */
	function loadApps(first) {
		return window.lunade.shell().then(function (s) {
			pages = s.pages;
			pages.forEach(function (p) { p.apps.forEach(function (a) { appTitles[a.id] = a.title; appEntries[a.id] = a; }); });
			s.dock.forEach(function (a) { appTitles[a.id] = a.title; appEntries[a.id] = a; });
			var tabs = $("tabs");
			tabs.innerHTML = "";
			pages.forEach(function (p, i) {
				var b = document.createElement("button");
				b.textContent = p.title;
				b.addEventListener("click", function () { showPage(i); });
				tabs.appendChild(b);
			});
			showPage(first ? 0 : Math.min(page, pages.length - 1));
			var dock = $("dock");
			dock.innerHTML = "";
			setTimeout(layoutDock, 0);
			// A phone's bar holds four (QuickLaunch.PHONE_MAX_ITEMS), a tablet's five.
			s.dock.slice(0, document.body.classList.contains("phone") ? 4 : 5).forEach(function (a) {
				var b = document.createElement("button");
				b.title = a.title;
				b.appendChild(icon(a));
				b.addEventListener("click", function () { launch(a, b); });
				dock.appendChild(b);
			});
		});
	}
	// An app installed or removed: the launcher draws what there is now.
	window.lunade.onApps(function () { loadApps(false); });
	loadApps(true).then(function () { window.lunade.ready(); });
})();
