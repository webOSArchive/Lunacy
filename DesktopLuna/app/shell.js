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
		// A phone: as many 84 px cells as fit between 6 px margins (Launcher.Phone).
		document.documentElement.style.setProperty("--columns", document.body.classList.contains("phone") ? Math.max(1, Math.floor((w - 12) / 84)) : w >= 1024 ? 6 : w >= 700 ? 4 : 3);
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
		zoomState(false);
		$("title-text").textContent = maximized && cards[active] ? (appTitles[cards[active].appId] || cards[active].title) : "Luna DE";
	}
	var appTitles = {}, appEntries = {};
	/** The Phone Zoom button, for the card that is up: how its app's switch stands, or (flip) the other way. */
	function zoomState(flip) {
		var c = cards[active], b = $("zoom");
		b.disabled = !(c && c.id);
		if (b.disabled) { b.classList.remove("on"); return; }
		window.luna.phoneZoom(c.id, flip).then(function (on) { b.classList.toggle("on", !!on); });
	}
	$("zoom").addEventListener("click", function () { zoomState(true); });

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
		window.luna.maximize(c.id);
		if (c.splash) { var sp = c.splash; c.splash = null; sp.classList.add("gone"); setTimeout(function () { sp.remove(); }, 320); }
	}

	/** Down to the card view: the live card's picture taken first, so it shrinks from what was on the screen. */
	function cardView() {
		var c = maximized ? cards[active] : null;
		maximized = false;
		document.body.classList.remove("maximized");
		setTitle();
		var done = function () { window.luna.cardView(); layout(true); refreshPictures(c); };
		if (!c || !c.id || c.splash) { done(); return; }
		window.luna.snapshot(c.id).then(function (url) { if (url) { c.img.src = url; } done(); }, done);
	}

	/** Every card's picture taken again, but [skip]'s, just taken: a page goes on drawing while hidden. */
	function refreshPictures(skip) {
		cards.forEach(function (k) {
			if (k === skip || !k.id || k.splash) { return; }
			window.luna.snapshot(k.id).then(function (url) { if (url) { k.img.src = url; } });
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
		if (maximized) { window.luna.cardView(); maximized = false; document.body.classList.remove("maximized"); }
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
		if (wasUp) { maximized = false; document.body.classList.remove("maximized"); window.luna.cardView(); }
		layout(true);
		setTitle();
	}

	/** A card gone (closed here, by its page, or popped out): its picture leaves upwards. */
	function removed(d) {
		var i = cardIndex(d.id);
		if (i >= 0) { dropCard(cards[i], maximized && i === active); }
	}

	function closeCard(i) { if (!cards[i]) { return; } if (cards[i].id) { window.luna.close(cards[i].id); } else { dropCard(cards[i], false); } }

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
		 ["Pop out to its own window", function () { window.luna.popOut(c.id); }],
		 ["DevTools", function () { window.luna.devTools(c.id); }],
		 ["Reload", function () { window.luna.reload(c.id); }],
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

	// A phone's tab bar scrolls sideways (Docs/phone.md): by a drag along it, as a finger did, or
	// by the mouse's wheel. A drag that moved isn't a tap on the tab it ended over.
	var tabDragged = false;
	(function () {
		var tabs = $("tabs"), down = false, startX = 0, startScroll = 0;
		tabs.addEventListener("pointerdown", function (e) { down = true; tabDragged = false; startX = e.clientX; startScroll = tabs.scrollLeft; });
		window.addEventListener("pointermove", function (e) {
			if (!down) { return; }
			if (Math.abs(e.clientX - startX) > 6) { tabDragged = true; }
			if (tabDragged) { tabs.scrollLeft = startScroll - (e.clientX - startX); }
		});
		window.addEventListener("pointerup", function () { down = false; setTimeout(function () { tabDragged = false; }, 0); });
		tabs.addEventListener("wheel", function (e) { tabs.scrollLeft += e.deltaX || e.deltaY; e.preventDefault(); }, { passive: false });
	})();

	// ---- the launcher as its owner arranges it (LunaCE's, as AndroidLuna's Launcher has it) ----
	//
	// A cell is an app or a group of apps. Holding an icon begins edit mode and picks the icon up;
	// in edit mode a drag picks one up. A picked-up icon follows the pointer and the layout is
	// looked at only when the pointer has all but stopped (so passing over icons doesn't shuffle
	// them): over another tab it goes to that page; over the middle of another icon, and held
	// there 300 ms, it will drop onto it (into its group, or the two make one); anywhere else it
	// takes the cell under it and the others move aside over 300 ms. Held at a side edge 1.5 s it
	// goes to the next page; dropped on the quick-launch bar, the bar takes the app. Holding a
	// tab renames it (one the owner added can be deleted, its icons going to the first page);
	// holding the bar's empty space brings up "+" for a new tab, up to six. The host keeps it all
	// (LunaCore's LauncherLayout).
	var editing = false, dock = [], limits = { tabs: 6, name: 24, groupName: "Group" };
	var HOLD_MS = 500, TAB_HOLD_MS = 700, ADD_HOLD_MS = 650, MOVE_MS = 300, EDGE = 50, EDGE_MS = 1500, GROUP_DWELL_MS = 300, STILL_MS = 60, CELL = 128, GROUP_CORE = 0.6;
	var drag = null;   // what follows the pointer now: { to(x, y), drop(x, y) }

	function isGroup(c) { return !!(c && c.apps); }
	function keyOf(c) { return isGroup(c) ? c.uid : c.id; }
	function newId() { return "group_" + Date.now().toString(36) + Math.random().toString(36).slice(2, 10); }

	function saveTabs() {
		window.luna.saveLauncher({ tabs: pages.map(function (p) {
			return { designator: p.designator, name: p.title, apps: p.apps.map(function (c) {
				return isGroup(c) ? { group: c.group, uid: c.uid, apps: c.apps.map(function (a) { return a.id; }) } : c.id;
			}) };
		}) });
	}
	function saveDock() { window.luna.saveLauncher({ dock: dock.map(function (a) { return a.id; }) }); }

	/**
	 * A press on something that can be tapped, held or dragged: a tap if it ends within the tap
	 * radius and wasn't held; [o.hold] ms without leaving the radius is a hold; leaving it first is
	 * a drag. onHold and onDrag may pick something up (set [drag]), which then follows the pointer
	 * until it is let go.
	 */
	function press(e, o) {
		if (e.button !== 0) { return; }
		var x0 = e.clientX, y0 = e.clientY, held = false, moved = false;
		var timer = o.hold ? setTimeout(function () { held = true; if (o.onHold) { o.onHold(x0, y0); } }, o.hold) : null;
		function move(ev) {
			if (!moved && Math.max(Math.abs(ev.clientX - x0), Math.abs(ev.clientY - y0)) > TAP) {
				moved = true; clearTimeout(timer);
				if (!held && o.onDrag) { o.onDrag(x0, y0); }
			}
			if (drag) { drag.to(ev.clientX, ev.clientY); }
		}
		function up(ev) {
			window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", up); window.removeEventListener("pointercancel", up);
			clearTimeout(timer);
			if (drag) { var d = drag; drag = null; d.drop(ev.clientX, ev.clientY); }
			else if (!moved && !held && o.onTap) { o.onTap(ev); }
		}
		window.addEventListener("pointermove", move); window.addEventListener("pointerup", up); window.addEventListener("pointercancel", up);
	}

	/** GroupIcon's composite: the first members as thumbnails, 2 by 2, "+N" in the fourth place past four. */
	function groupArt(g) {
		var art = document.createElement("div");
		art.className = "group-art";
		g.apps.slice(0, 4).forEach(function (a, i) {
			if (g.apps.length > 4 && i === 3) { var more = document.createElement("b"); more.textContent = "+" + (g.apps.length - 3); art.appendChild(more); return; }
			art.appendChild(icon(a));
		});
		return art;
	}

	function tileFor(c) {
		var t = document.createElement("button");
		t.className = "tile";
		t.cell = c;
		if (isGroup(c)) {
			t.classList.add("group");
			t.title = c.apps.map(function (a) { return a.title; }).join(", ");
			t.appendChild(groupArt(c));
		} else {
			t.title = c.id;
			t.appendChild(icon(c));
			if (c.removable) { var del = document.createElement("i"); del.className = "del"; t.appendChild(del); }
		}
		var s = document.createElement("span"); s.textContent = isGroup(c) ? c.group : c.title; t.appendChild(s);
		t.addEventListener("pointerdown", function (e) {
			var onDelete = editing && e.target.classList && e.target.classList.contains("del");
			press(e, {
				hold: editing || onDelete ? 0 : HOLD_MS,
				onHold: function (x, y) { enterEdit(); pickUp(c, x, y); },
				onDrag: function (x, y) { if (editing && !onDelete) { pickUp(c, x, y); } },
				onTap: function () {
					if (onDelete) { removeDialog(c); return; }
					if (editing) { return; }   // icons don't launch while being arranged
					if (isGroup(c)) { glow(t); openGroup(c, t); } else { launch(c, t); }
				},
			});
		});
		// An installed app's tile: Luna DE's Remove, by the right button.
		t.addEventListener("contextmenu", function (e) {
			e.preventDefault();
			if (!isGroup(c) && c.removable) { showItems(e.clientX, e.clientY, [["Remove " + c.title, function () { window.luna.remove(c.id); }]]); }
		});
		return t;
	}

	function showPage(n) {
		page = Math.max(0, Math.min(pages.length - 1, n));
		[].forEach.call($("tabs").children, function (b, i) { b.classList.toggle("selected", i === page); });
		// The chosen tab comes fully into view (Launcher.Phone's TAB_SCROLL_MS, 250 ms).
		var chosen = $("tabs").children[page];
		if (chosen && chosen.scrollIntoView) { chosen.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" }); }
		var grid = $("grid"), cells = pages[page] ? pages[page].apps : [];
		grid.innerHTML = "";
		cells.forEach(function (c) {
			var t = tileFor(c);
			if (lifted && lifted === c) { t.classList.add("lifted"); }
			grid.appendChild(t);
		});
		$("empty-page").hidden = cells.length > 0;
	}

	// ---- edit mode ----

	function enterEdit() {
		if (editing) { return; }
		editing = true; clearGlow();
		document.body.classList.add("editing");
		$("tab-done").hidden = false; $("tab-add").hidden = true;
	}
	/** Done, the home button, or the launcher closing. */
	function exitEdit() {
		if (!editing) { return; }
		editing = false;
		document.body.classList.remove("editing");
		$("tab-done").hidden = true;
	}
	$("tab-done").addEventListener("click", exitEdit);

	/** Changes the order, every icon sliding from where it is to its new cell (IconReorderIconMoveAnimTime). */
	function reorder(change) {
		var before = {};
		[].forEach.call($("grid").children, function (t) { before[keyOf(t.cell)] = t.getBoundingClientRect(); });
		change();
		showPage(page);
		[].forEach.call($("grid").children, function (t) {
			var was = before[keyOf(t.cell)];
			if (!was) { return; }
			var now = t.getBoundingClientRect(), dx = was.left - now.left, dy = was.top - now.top;
			if (!dx && !dy) { return; }
			t.style.transition = "none"; t.style.transform = "translate(" + dx + "px," + dy + "px)";
			t.getBoundingClientRect();
			t.style.transition = "transform " + MOVE_MS + "ms cubic-bezier(0.55, 0.085, 0.68, 0.53)"; t.style.transform = "";
		});
	}

	var lifted = null;   // the cell that is picked up: its own tile keeps its place, unseen

	function tabAt(x, y) {
		var tabs = $("tabs").children;
		for (var i = 0; i < tabs.length; i++) { var r = tabs[i].getBoundingClientRect(); if (x >= r.left && x < r.right && y >= r.top && y < r.bottom) { return i; } }
		return -1;
	}
	function dockTop() { return $("quicklaunch").getBoundingClientRect().top; }

	/** The cell under a point of the page, by the grid's own measure; the last one for a point past them. */
	function cellAt(x, y) {
		var grid = $("grid"), tiles = grid.children;
		if (!tiles.length) { return -1; }
		var first = tiles[0].getBoundingClientRect(), cols = 1;
		while (cols < tiles.length && tiles[cols].getBoundingClientRect().top === first.top) { cols++; }
		var pitchY = tiles.length > cols ? tiles[cols].getBoundingClientRect().top - first.top : first.height;
		var col = Math.max(0, Math.min(cols - 1, Math.floor((x - first.left) / first.width)));
		var row = Math.max(0, Math.floor((y - first.top) / pitchY));
		return Math.max(0, Math.min(tiles.length - 1, row * cols + col));
	}

	function pickUp(c, x, y) {
		var el = [].filter.call($("grid").children, function (t) { return t.cell === c; })[0];
		if (!el) { return; }
		var r = el.getBoundingClientRect(), ghost = el.cloneNode(true);
		ghost.className = "tile ghost" + (isGroup(c) ? " group" : "");
		ghost.style.width = r.width + "px"; ghost.style.height = r.height + "px";
		$("screen").appendChild(ghost);
		lifted = c; el.classList.add("lifted");
		var d = { x: x, y: y, dx: x - r.left, dy: y - r.top, still: null, hover: null, target: null, arm: null, side: 0, edge: null };
		function place() { ghost.style.transform = "translate(" + (d.x - d.dx) + "px," + (d.y - d.dy) + "px)"; }
		function clearTarget() {
			clearTimeout(d.arm); d.hover = null; d.target = null;
			[].forEach.call($("grid").children, function (t) { t.classList.remove("group-target"); });
		}
		function lightTab(i) { [].forEach.call($("tabs").children, function (b, j) { b.classList.toggle("over", j === i); }); }
		/** The dragged icon goes to the end of another page, which is shown. */
		function moveTo(to) {
			if (to < 0 || to >= pages.length || to === page) { return; }
			var apps = pages[page].apps;
			apps.splice(apps.indexOf(c), 1);
			pages[to].apps.push(c);
			clearTarget();
			showPage(to);
		}
		/** The pointer has all but stopped: what the icon is over decides. */
		function sample() {
			if (d.y < $("grid").getBoundingClientRect().top) { var i = tabAt(d.x, d.y); if (i >= 0 && i !== page) { moveTo(i); } return; }
			if (d.y > dockTop()) { return; }
			var apps = pages[page].apps, to = cellAt(d.x, d.y), from = apps.indexOf(c);
			var over = to >= 0 ? $("grid").children[to] : null;
			if (over && over.cell !== c && !isGroup(c)) {
				var r2 = over.getBoundingClientRect(), core = CELL * GROUP_CORE / 2;
				if (Math.abs(d.x - (r2.left + r2.width / 2)) < core && Math.abs(d.y - (r2.top + r2.height / 2)) < core) {
					var k = keyOf(over.cell);
					if (d.hover !== k) {
						clearTarget(); d.hover = k;
						d.arm = setTimeout(function () { d.target = k; [].forEach.call($("grid").children, function (t) { t.classList.toggle("group-target", keyOf(t.cell) === k); }); }, GROUP_DWELL_MS);
					}
					return;
				}
			}
			clearTarget();
			if (to >= 0 && from >= 0 && to !== from) { reorder(function () { apps.splice(from, 1); apps.splice(to, 0, c); }); }
		}
		function edgeFlip() {
			if (!d.side) { return; }
			moveTo(page + d.side);
			d.edge = setTimeout(edgeFlip, EDGE_MS);
		}
		place();
		drag = {
			to: function (nx, ny) {
				d.x = nx; d.y = ny; place();
				var onPage = ny >= $("grid").getBoundingClientRect().top && ny <= dockTop();
				lightTab(onPage ? -1 : tabAt(nx, ny));
				ghost.classList.toggle("over-dock", ny > dockTop());
				var side = !onPage ? 0 : nx < EDGE ? -1 : nx > W - EDGE ? 1 : 0;
				if (side !== d.side) { d.side = side; clearTimeout(d.edge); if (side) { d.edge = setTimeout(edgeFlip, EDGE_MS); } }
				clearTimeout(d.still); d.still = setTimeout(sample, STILL_MS);
			},
			drop: function (nx, ny) {
				clearTimeout(d.still); clearTimeout(d.edge); clearTimeout(d.arm);
				lightTab(-1);
				var target = d.target;
				[].forEach.call($("grid").children, function (t) { t.classList.remove("group-target"); });
				lifted = null;
				var apps = pages[page].apps;
				if (ny > dockTop()) {
					// Onto the bar: it takes the app; the icon stays where it was in the launcher.
					if (!isGroup(c)) { dropOnDock(c, nx); }
				} else if (target && !isGroup(c)) {
					// Onto another icon: into its group, or the two make a new one where it stood.
					var onto = apps.filter(function (k) { return keyOf(k) === target; })[0];
					if (onto) {
						apps.splice(apps.indexOf(c), 1);
						if (isGroup(onto)) { onto.apps.push(c); }
						else { apps[apps.indexOf(onto)] = { group: limits.groupName, uid: newId(), apps: [onto, c] }; }
					}
				}
				saveTabs();
				// The icon settles from under the pointer into its cell.
				showPage(page);
				var home = [].filter.call($("grid").children, function (t) { return t.cell === c; })[0];
				if (!home) { ghost.remove(); return; }
				var hr = home.getBoundingClientRect();
				home.classList.add("lifted");
				ghost.style.transition = "transform " + MOVE_MS + "ms cubic-bezier(0.55, 0.085, 0.68, 0.53)";
				ghost.style.transform = "translate(" + hr.left + "px," + hr.top + "px)";
				setTimeout(function () { ghost.remove(); home.classList.remove("lifted"); }, MOVE_MS);
			},
		};
	}

	// ---- the tabs ----

	function drawTabs() {
		var tabs = $("tabs");
		tabs.innerHTML = "";
		pages.forEach(function (p, i) {
			var b = document.createElement("button");
			b.textContent = p.title;
			b.addEventListener("pointerdown", function (e) {
				press(e, {
					hold: editing ? 0 : TAB_HOLD_MS,
					onHold: function () { renameTab(i); },
					onTap: function () { if (!tabDragged) { showPage(i); } },
				});
			});
			b.addEventListener("contextmenu", function (e) { e.preventDefault(); if (!editing) { renameTab(i); } });
			tabs.appendChild(b);
		});
	}

	/** A tab was held: rename it, and for one the owner added, offer to delete it (its icons go to the first page). */
	function renameTab(i) {
		var p = pages[i];
		if (!p) { return; }
		textDialog("Rename Tab", p.title, !p.permanent, true, function (name, del) {
			if (name) { p.title = name; }
			if (del && !p.permanent) {
				pages.splice(i, 1);
				pages[0].apps = pages[0].apps.concat(p.apps);
				if (page >= pages.length) { page = pages.length - 1; }
			}
			saveTabs(); drawTabs(); showPage(page);
		});
	}

	// The bar's empty space, held (or right-clicked): "+" for a new tab, while there is room for
	// one. A tap on it asks for the tab's name; a tap anywhere else puts it away.
	$("tabbar").addEventListener("pointerdown", function (e) {
		if (e.target !== $("tabbar") && e.target !== $("tabs")) { return; }
		press(e, { hold: ADD_HOLD_MS, onHold: revealAdd });
	});
	$("tabbar").addEventListener("contextmenu", function (e) {
		if (e.target === $("tabbar") || e.target === $("tabs")) { e.preventDefault(); revealAdd(); }
	});
	function revealAdd() { if (!editing && pages.length < limits.tabs) { $("tab-add").hidden = false; } }
	document.addEventListener("pointerup", function (e) { if (e.target !== $("tab-add") && e.target !== $("tabbar") && e.target !== $("tabs")) { $("tab-add").hidden = true; } }, true);
	$("tab-add").addEventListener("click", function () {
		$("tab-add").hidden = true;
		textDialog("New Tab", "New Tab", false, false, function (name) {
			if (!name || pages.length >= limits.tabs) { return; }
			pages.push({ designator: "usertab_" + newId().slice(6), title: name, permanent: false, apps: [] });
			saveTabs(); drawTabs(); showPage(pages.length - 1);
		});
	});

	// ---- LunaCE's dialogs over the launcher ----

	var scrimAway = null;
	function openScrim(el, away) {
		var scrim = $("scrim");
		scrim.innerHTML = ""; scrim.appendChild(el); scrim.hidden = false;
		scrimAway = away;
	}
	function closeScrim() { $("scrim").hidden = true; $("scrim").innerHTML = ""; scrimAway = null; }
	$("scrim").addEventListener("pointerdown", function (e) { if (e.target === $("scrim") && scrimAway) { scrimAway(); } });

	/**
	 * Rename Tab / New Tab (renamedialog.cpp): the prompt, and the name in a box, up to 24
	 * characters. Return commits; tapping away commits a rename ([commitAway]) but throws a new
	 * tab away. [deletable] puts the trash can beside the box, which deletes at once.
	 * [done] gets the trimmed name (null to leave things as they were) and whether to delete.
	 */
	function textDialog(prompt, value, deletable, commitAway, done) {
		var panel = document.createElement("div"); panel.className = "tab-dialog";
		var p = document.createElement("div"); p.className = "prompt"; p.textContent = prompt; panel.appendChild(p);
		var row = document.createElement("div"); row.className = "row"; panel.appendChild(row);
		var input = document.createElement("input"); input.type = "text"; input.maxLength = limits.name; input.value = value; row.appendChild(input);
		var finished = false;
		function finish(name, del) { if (finished) { return; } finished = true; closeScrim(); done(name, !!del); }
		function typed() { return input.value.trim() || null; }
		if (deletable) {
			var trash = document.createElement("button"); trash.className = "trash"; trash.title = "Delete this tab";
			trash.addEventListener("click", function () { finish(null, true); });
			row.appendChild(trash);
		}
		input.addEventListener("keydown", function (e) {
			e.stopPropagation();
			if (e.key === "Enter") { finish(typed(), false); } else if (e.key === "Escape") { finish(null, false); }
		});
		openScrim(panel, function () { finish(commitAway ? typed() : null, false); });
		input.focus(); input.select();
	}

	/** LunaCE's AppInfoDialog: "Remove Application?", the app and its version, Cancel and Remove. */
	function removeDialog(a) {
		var panel = document.createElement("div"); panel.className = "remove-dialog";
		var h = document.createElement("div"); h.className = "prompt"; h.textContent = "Remove Application?"; panel.appendChild(h);
		var what = document.createElement("div"); what.className = "what"; what.textContent = a.title + (a.version ? " - v." + a.version : ""); panel.appendChild(what);
		[["Cancel", function () {}], ["Remove", function () { window.luna.remove(a.id); }]].forEach(function (it) {
			var b = document.createElement("button"); b.textContent = it[0]; b.className = it[0].toLowerCase();
			b.addEventListener("click", function () { closeScrim(); it[1](); });
			panel.appendChild(b);
		});
		openScrim(panel, closeScrim);
	}

	/**
	 * A group opened (groupoverlay.cpp): the launcher dimmed, and a panel of the group's members,
	 * up to four across and sixteen shown, under the group's name. It grows out of the group's
	 * icon. A tap on a member launches it; holding one puts it back on the page, just after the
	 * group; a tap on the name renames the group; a tap outside closes it.
	 */
	function openGroup(g, from) {
		var panel = document.createElement("div"); panel.className = "group-panel";
		var name = document.createElement("div"); name.className = "name"; name.textContent = g.group; panel.appendChild(name);
		var members = document.createElement("div"); members.className = "members"; panel.appendChild(members);
		members.style.gridTemplateColumns = "repeat(" + Math.min(4, Math.max(1, g.apps.length)) + ", 136px)";
		function close() { clearGlow(); panel.classList.add("closing"); setTimeout(closeScrim, 130); }
		/** The group as it is now, on its page: one member left is that app again, none and it is gone. */
		function changed() {
			pages.forEach(function (p) {
				var i = p.apps.indexOf(g);
				if (i < 0) { return; }
				if (g.apps.length === 0) { p.apps.splice(i, 1); } else if (g.apps.length === 1) { p.apps[i] = g.apps[0]; }
			});
			saveTabs(); showPage(page);
		}
		g.apps.slice(0, 16).forEach(function (a) {
			var t = document.createElement("button"); t.className = "tile";
			t.appendChild(icon(a));
			var s = document.createElement("span"); s.textContent = a.title; t.appendChild(s);
			t.addEventListener("pointerdown", function (e) {
				press(e, {
					hold: 700,
					onHold: function () {
						// Out of the group, onto the page just after it.
						g.apps.splice(g.apps.indexOf(a), 1);
						pages.forEach(function (p) { var i = p.apps.indexOf(g); if (i >= 0) { p.apps.splice(i + 1, 0, a); } });
						changed(); closeScrim(); clearGlow();
					},
					onTap: function () { t.classList.add("glow"); close(); launch(a, null); },
				});
			});
			members.appendChild(t);
		});
		name.addEventListener("click", function () {
			if (name.querySelector("input")) { return; }
			var input = document.createElement("input"); input.type = "text"; input.maxLength = limits.name; input.value = g.group;
			name.textContent = ""; name.appendChild(input); input.focus(); input.select();
			function commit() {
				var v = input.value.trim();
				if (v) { g.group = v; saveTabs(); showPage(page); }
				name.textContent = g.group;
			}
			input.addEventListener("keydown", function (e) { e.stopPropagation(); if (e.key === "Enter") { commit(); } else if (e.key === "Escape") { name.textContent = g.group; } });
			input.addEventListener("blur", function () { if (input.parentNode === name) { commit(); } });
		});
		openScrim(panel, close);
		// Out of the group's icon: 0.85 to full size, faded in, over 160 ms.
		if (from) {
			var r = from.getBoundingClientRect(), pr = panel.getBoundingClientRect();
			panel.style.transformOrigin = (r.left + r.width / 2 - pr.left) + "px " + (r.top + 40 - pr.top) + "px";
		}
		panel.classList.add("opening");
		panel.getBoundingClientRect();
		panel.classList.remove("opening");
	}

	// ---- the quick-launch bar's own arranging (QuickLaunch) ----
	//
	// Holding a bar icon picks it up, for that drag only; in the launcher's edit mode a drag picks
	// one up too. It sits 15 px above the pointer and the others make room for it; above the bar
	// it turns half transparent. Let go on the bar and it takes that slot, let go above the bar
	// and it leaves the bar. A launcher icon dropped on the bar joins it.

	function dockMax() { return document.body.classList.contains("phone") ? 4 : 5; }
	function slotAt(x, count) {
		var r = $("dock").getBoundingClientRect();
		return Math.max(0, Math.min(Math.max(0, count - 1), Math.floor((x - r.left) / (r.width / Math.max(1, count)))));
	}
	function dropOnDock(a, x) {
		var at = dock.map(function (k) { return k.id; }).indexOf(a.id);
		if (at >= 0) { dock.splice(at, 1); dock.splice(slotAt(x, dock.length + 1), 0, a); }
		else if (dock.length < dockMax()) { dock.splice(slotAt(x, dock.length + 1), 0, a); }
		else { dock[slotAt(x, dock.length)] = a; }
		saveDock(); drawDock();
	}

	var dockLifted = null;
	function drawDock() {
		var el = $("dock");
		el.innerHTML = "";
		dock.slice(0, dockMax()).forEach(function (a) {
			var b = document.createElement("button");
			b.title = a.title;
			b.appendChild(icon(a));
			if (dockLifted === a) { b.classList.add("lifted"); }
			b.addEventListener("pointerdown", function (e) {
				press(e, {
					hold: editing ? 0 : HOLD_MS,
					onHold: function (x, y) { dockPick(a, x, y); },
					onDrag: function (x, y) { if (editing) { dockPick(a, x, y); } },
					onTap: function () { if (!editing) { launch(a, b); } },
				});
			});
			el.appendChild(b);
		});
		setTimeout(layoutDock, 0);
	}

	function dockPick(a, x, y) {
		var ghost = icon(a);
		ghost.className = "dock-ghost";
		$("screen").appendChild(ghost);
		dockLifted = a; drawDock();
		function place(nx, ny) { ghost.style.transform = "translate(" + (nx - 32) + "px," + (ny - 32 - 15) + "px)"; }
		place(x, y);
		drag = {
			to: function (nx, ny) {
				place(nx, ny);
				var on = ny >= dockTop();
				ghost.classList.toggle("leaving", !on);
				if (on) {
					var from = dock.indexOf(a), to = slotAt(nx, dock.length);
					if (from >= 0 && to !== from) { dock.splice(from, 1); dock.splice(to, 0, a); drawDock(); }
				}
			},
			drop: function (nx, ny) {
				ghost.remove(); dockLifted = null;
				if (ny < dockTop()) { var i = dock.indexOf(a); if (i >= 0) { dock.splice(i, 1); } }
				saveDock(); drawDock();
			},
		};
	}

	/**
	 * An app launched from the launcher or the bar. What the person sees comes first, before
	 * anything is asked of the host: the tapped icon glows, and an app with no card gets its loading
	 * card. Then the host starts it, or relaunches it and has its card brought up.
	 */
	function launch(entry, tapped) {
		glow(tapped);
		window.luna.launch(entry);
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
		exitEdit(); closeScrim();
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
		if (!$("scrim").hidden) { if (scrimAway) { scrimAway(); } else { closeScrim(); } }
		else if (editing) { exitEdit(); }
		else if (!$("launcher").hidden) { closeLauncher(); }
		else if (maximized) { cardView(); }
	}
	$("home").addEventListener("click", home);
	// The log window; and by right-click, the developer tools of a card.
	$("devtools").addEventListener("click", function () { window.luna.log(); });
	$("devtools").addEventListener("contextmenu", function (e) { e.preventDefault(); window.luna.toolsMenu(); });
	// The bezel's other corner: the card that is up, or in front in the card view, popped out.
	$("popout").addEventListener("click", function () { var c = cards[active]; if (c && c.id) { window.luna.popOut(c.id); } });
	// And the device itself: a TouchPad or a Pre3, by its picture.
	$("device").addEventListener("click", function () { window.luna.device(); });
	/** The screen as main.js gives it (in this page's px), the device's picture, and what the device hasn't got. */
	function applyScreen(d) {
		var wasPhone = document.body.classList.contains("phone");
		document.body.classList.toggle("phone", d.device === "pre3");
		measure(d.width, d.height);
		document.body.classList.toggle("portrait", d.height > d.width);
		if (wasPhone !== (d.device === "pre3") && pages.length) { loadApps(false); }
		// The bezel is Luna DE's own, not the device's screen: it keeps its size at any scale.
		if (d.bezel) { document.documentElement.style.setProperty("--unzoom", String(d.bezel / 44)); }
		$("device").querySelector("img").src = "images/device-" + (d.device || "touchpad") + (d.device === "screen" ? ".svg" : ".png");
		$("device").title = "The device: " + ({ pre3: "an HP Pre3", screen: "this screen (a TouchPad at the window's size; F11 fills the display)" }[d.device] || "an HP TouchPad") + ". Click for the next: TouchPad, Pre3, this screen (changing it closes the open apps)";
		$("realkb").style.display = d.virtualKeyboard === false ? "none" : "";
	}
	window.luna.screenNow().then(applyScreen);
	// The bezel's corner: the device turned between wide and tall.
	$("rotate").addEventListener("click", function () { window.luna.rotate(); });
	document.addEventListener("keydown", function (e) {
		if (e.key === "F1") { e.preventDefault(); home(); }
		else if (e.key === "Escape" && (editing || !$("scrim").hidden)) { e.preventDefault(); home(); }
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
	$("title").addEventListener("click", function () { if (maximized) { window.luna.appMenu(); } });

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
				x.addEventListener("click", function () { window.luna.close(p.id); });
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
		window.luna.place(rects, dashOpen && d.length > 0);
	}

	$("notes").addEventListener("click", function () {
		if (bannerOn && bannerOn.appId) { var b = bannerOn; endBanner(); window.luna.launch({ id: b.appId, params: parse(b.params) }); return; }
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

	// Nothing in the shell is dragged the browser's way: a press on an icon's picture that moved
	// began a drag of the picture, and the icon being arranged was let go at once.
	document.addEventListener("dragstart", function (e) { e.preventDefault(); });
	document.addEventListener("dragover", function (e) { e.preventDefault(); e.dataTransfer.dropEffect = "copy"; });
	document.addEventListener("drop", function (e) {
		e.preventDefault();
		for (var i = 0; i < e.dataTransfer.files.length; i++) { window.luna.install(e.dataTransfer.files[i]); }
	});

	// ---- the status bar's clock ----

	function tick() {
		var d = new Date(), h = d.getHours() % 12 || 12, m = d.getMinutes();
		$("clock").textContent = h + ":" + (m < 10 ? "0" : "") + m;
	}
	tick(); setInterval(tick, 5000);

	// ---- start ----

	window.luna.onHome(home);
	window.luna.onCardAdded(added);
	// A headless app the host started with a loading card (a launch that was no tap here: another
	// app's, LUNA_DE_OPEN), and the host's word that the app is up and opened no card for it.
	window.luna.onCardLoading(function (d) { launching({ id: d.appId, title: d.title }); });
	window.luna.onCardWithdrawn(function (d) {
		cards.filter(function (c) { return !c.id && c.appId === d.appId; }).forEach(function (c) { clearTimeout(c.waiting); dropCard(c, false); });
	});
	window.luna.onCardReady(ready);
	window.luna.onCardRemoved(removed);
	window.luna.onShow(function (d) { var i = cardIndex(d.id); if (i >= 0) { maximize(i); } });
	window.luna.onToggleCard(toggleCard);
	window.luna.onLauncher(function () { if ($("launcher").hidden) { openLauncher(); } else { closeLauncher(); } });
	window.luna.onScreen(function (d) {
		applyScreen(d);
		if (maximized) { cards.forEach(function (k, j) { k.el.classList.remove("animate"); transform(k, j === active ? 0 : (j < active ? -1 : 1) * (W + GAP), 0, 1); }); }
		else { layout(false); }
		placePanels();
	});
	window.luna.onPanelAdded(function (d) { panels.push(d); drawNotes(); });
	window.luna.onPanelRemoved(function (d) { panels = panels.filter(function (p) { return p.id !== d.id; }); drawNotes(); });
	window.luna.onBanner(function (d) { banners.push(d); nextBanner(); });
	window.luna.onBannerRemove(function (d) {
		banners = banners.filter(function (b) { return d.id ? b.id !== d.id : b.appId !== d.appId; });
		if (bannerOn && (d.id ? bannerOn.id === d.id : bannerOn.appId === d.appId)) { endBanner(); }
	});

	// The simulated network (the Wi-Fi tile): the status bar's Wi-Fi icon shows it gone.
	window.luna.onNetwork(function (d) { document.querySelector("#status-right .wifi").classList.toggle("off", !d.online); });

	/** The launcher's pages and the quick-launch bar, as the host has them: what is installed, arranged as its owner left it. */
	function loadApps(first) {
		return window.luna.shell().then(function (s) {
			pages = s.pages;
			dock = s.dock;
			if (s.limits) { limits = s.limits; }
			function note(a) { appTitles[a.id] = a.title; appEntries[a.id] = a; }
			pages.forEach(function (p) { p.apps.forEach(function (c) { if (isGroup(c)) { c.apps.forEach(note); } else { note(c); } }); });
			dock.forEach(note);
			drawTabs();
			showPage(first ? 0 : Math.min(page, pages.length - 1));
			drawDock();
		});
	}
	// An app installed or removed: the launcher draws what there is now.
	window.luna.onApps(function () { loadApps(false); });
	loadApps(true).then(function () { window.luna.ready(); });
})();
