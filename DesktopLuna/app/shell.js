// Luna DE's shell: one window as a TouchPad's screen, the status bar, the launcher, apps as
// cards and the card view (Docs/luna-de.md, milestone 3b). Its geometry is AndroidLuna's
// (CardLayer.Params, StatusBar, QuickLaunch), and its card view CardLayer's, groups and all.
// Which windows there are is the host's (LunaCore's AppLifecycle); where each card is, this.
"use strict";
(function () {
	var STATUS = 28, RESERVE = 48, ORIGIN = 0.40, GAP = 30; // StatusBar.HEIGHT; CardLayer.Params: PILL_RESERVE, ORIGIN_RATIO, GAP
	var W, H, AREA, ORIGIN_Y;
	var ACTIVE_RATIO = 0.55, NON_ACTIVE_RATIO = 0.50;   // ActiveCardWindowRatio, NonActiveCardWindowRatio; a phone's are the Pre3's
	/** The screen's size, and what follows from it: the card area below the status bar and CardLayer's ratios. */
	function measure(w, h) {
		W = w; H = h; AREA = h - STATUS;
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
	var TAP = 25, AXIS_LOCK = 0.866, HOLD_MS = 700, FLING_MIN = 300;   // TAP_RADIUS, AXIS_LOCK, HOLD_MS; a flick's least speed, px/s
	// Throw away: CardWindowManager's thresholds, in Luna's flick units (about a third of px/s).
	var THROW_VELOCITY = -1100, THROW_DISTANCE = -50, THROW_MIN_VELOCITY = -500;
	var MAXIMIZE_MS = 300, FAN_MS = 200, SLIDE_MS = 300, DELETE_MS = 300, TRACK_GROUP_MS = 50, TRACK_MS = 300,
		SHUFFLE_MS = 350, GROUP_REORDER_MS = 500, DIM_MS = 300, MIN_SCALE = 0.26, GROUP_X_DISTANCE = 0.35, GROUP_ROT = 90,
		SIDE_STAGGER = 10, REORDER_OPACITY = 0.8, DIMMED = 0.8;
	var EASE = { outQuart: "cubic-bezier(0.165, 0.84, 0.44, 1)", outCubic: "cubic-bezier(0.215, 0.61, 0.355, 1)", linear: "linear" };
	var maximized = false;
	var $ = function (id) { return document.getElementById(id); };

	// ---- the cards ----
	//
	// AndroidLuna's CardLayer, LunaSysMgr's CardWindowManager and CardGroup: cards live in
	// groups. A card an app opens while its own card is up joins that card's group, and so does
	// one an app opens for another app that launched it while that app's card was up; anything
	// else starts a group of its own just right of the active one. The active group is fanned;
	// the others collapse into a 10 px stagger at the smaller scale as they leave the middle.
	// Holding a card of the active group picks it up to reorder it, within its group or out of
	// it into the next (or a group of its own); holding the space either side goes to the group
	// that way. A card here is the shell's picture of an app window: main.js keeps its live view,
	// shown over the card area while it is up and hidden in the card view, where this draws its
	// snapshot. Each card's place is its group's x (sliding on one timing) and its own place in
	// the group (on another), so a card is two elements: the outer for the group's x, the face
	// for the rest.

	var groups = [], activeGroup = null, current = null, topCard = null, activating = null, upCard = null;
	/** Every card, group by group, back to front. */
	function allCards() { var out = []; groups.forEach(function (g) { out = out.concat(g.cards); }); return out; }
	/** The card in front: the active group's active card (cards[active] before groups). */
	function cur() { return activeGroup ? activeGroup.active : null; }
	function cardById(id) { return allCards().filter(function (c) { return c.id === id; })[0] || null; }

	function activeScale() { return Math.max(MIN_SCALE, (AREA - RESERVE) * (isPhone() ? 0.659 : ACTIVE_RATIO) / AREA); }
	function nonActiveScale() { return Math.max(MIN_SCALE, (AREA - RESERVE) * (isPhone() ? 0.61 : NON_ACTIVE_RATIO) / AREA); }
	function isPhone() { return document.body.classList.contains("phone"); }

	/** Half a transformed card's width, rotation included, as LunaCE's mapRect measures it. */
	function halfExtent(s, rot) {
		var a = rot * Math.PI / 180;
		return s * (W / 2 * Math.cos(a) + AREA / 2 * Math.abs(Math.sin(a)));
	}

	/**
	 * CardGroup::calculateOpenedPositions for the Stack arranger, relative to the group, with
	 * LunaCE's always-centred position. A group [xOffset] from the centre collapses towards a
	 * 10 px stagger at the non-active scale as it goes. Sets the group's extents.
	 */
	function fan(g, xOffset) {
		var n = g.cards.length, p = (n - 1) / 2, c0 = activeScale(), non = nonActiveScale(), aw = W * c0;
		var poses = g.cards.map(function (card, i) {
			var x = ((i - p) / 3) * aw * GROUP_X_DISTANCE, y = x > 0 ? x / 15 : 0, s = c0, r = x / (c0 * GROUP_ROT);
			if (xOffset !== 0) {
				var amt = Math.max(1, aw - Math.abs(xOffset)) / aw;
				x = x * amt + (1 - amt) * SIDE_STAGGER * i;
				y *= amt; s = non + (c0 - non) * amt; r *= amt;
			}
			return { gx: 0, rx: x, cy: ORIGIN_Y + y, scale: s, rot: r };
		});
		if (n) {
			g.left = -(poses[0].rx - halfExtent(poses[0].scale, poses[0].rot));
			g.right = poses[n - 1].rx + halfExtent(poses[n - 1].scale, poses[n - 1].rot);
		}
		return poses;
	}

	/** The Linear arranger, used while a card is maximized: full size, side by side. */
	function linear(g) {
		var n = g.cards.length, a = Math.max(0, g.cards.indexOf(g.active));
		var poses = g.cards.map(function (card, i) { return { gx: 0, rx: (i - a) * (W + (n > 1 ? GAP : 0)), cy: AREA / 2, scale: 1, rot: 0 }; });
		if (n) { g.left = -(poses[0].rx - W / 2); g.right = poses[n - 1].rx + W / 2; }
		return poses;
	}

	/**
	 * Every card's place, CardWindowManager::slideAllGroups: the active group at [activeX] and
	 * the others either side of it, GAP between their extents. [tracking]: the groups under a
	 * finger, where the active group collapses too. A Map of card to pose.
	 */
	function layout(stack, activeX, tracking) {
		activeX = activeX || 0;
		var out = new Map(), ag = activeGroup;
		if (!ag) { return out; }
		function poses(g, off) { return stack ? fan(g, off) : linear(g); }
		function put(g, gx, ps) { g.cards.forEach(function (c, i) { out.set(c, { gx: gx, rx: ps[i].rx, cy: ps[i].cy, scale: ps[i].scale, rot: ps[i].rot }); }); }
		put(ag, activeX, poses(ag, tracking ? activeX : 0));
		var ai = groups.indexOf(ag), edge = activeX - ag.left - GAP, i, g, x;
		// A side group's extents depend on how far it has collapsed, which depends on where it
		// lands: measured collapsed first, then placed once more with that.
		for (i = ai - 1; i >= 0; i--) { g = groups[i]; poses(g, -1e9); x = edge - g.right; put(g, x, poses(g, x)); edge = x - GAP - g.left; }
		edge = activeX + ag.right + GAP;
		for (i = ai + 1; i < groups.length; i++) { g = groups[i]; poses(g, 1e9); x = edge + g.left; put(g, x, poses(g, x)); edge = x + GAP + g.right; }
		return out;
	}

	/** CardWindowManager::maximizeActiveWindow: the card full size in the middle, the rest of its group off either side at the active scale. */
	function maximizedLayout(card) {
		var g = card.group, out = layout(false);
		if (!g) { return out; }
		var a = g.cards.indexOf(card);
		g.cards.forEach(function (c, i) {
			out.set(c, c === card ? { gx: 0, rx: 0, cy: AREA / 2, scale: 1, rot: 0 }
				: { gx: 0, rx: (i < a ? -1 : 1) * W, cy: AREA / 2, scale: activeScale(), rot: 0 });
		});
		return out;
	}

	/** Puts a card where its numbers say, over the given timings (none: at once). */
	function apply(c, gMs, gEase, cMs, cEase) {
		c.el.style.transition = gMs ? "transform " + gMs + "ms " + gEase : "none";
		c.face.style.transition = cMs ? "transform " + cMs + "ms " + cEase + ", opacity " + cMs + "ms " + cEase : "none";
		c.el.style.transform = "translateX(" + c.gx + "px)";
		c.face.style.transform = "translate(" + c.rx + "px," + (c.cy - AREA / 2 + c.lift) + "px) scale(" + c.scale + ") rotate(" + c.rot + "deg)";
		c.face.style.opacity = c.fade;
	}
	function setPose(c, p) { c.gx = p.gx; c.rx = p.rx; c.cy = p.cy; c.scale = p.scale; c.rot = p.rot; c.lift = 0; }

	var runTimer = null;
	/** Every card in [poses] to its place, each on the timings [timing] gives it; [done] when they have all arrived. */
	function run(poses, timing, done) {
		clearTimeout(runTimer);
		var total = 0;
		poses.forEach(function (p, c) {
			var t = timing(c);
			setPose(c, p);
			apply(c, t[0], t[1], t[2], t[3]);
			total = Math.max(total, t[0], t[2]);
		});
		runTimer = setTimeout(function () { restack(); if (done) { done(); } }, total);
	}

	/** slideAllGroups: every group to its place over 300 ms OutQuart, and the active group's own cards over 200 ms OutCubic. */
	function slide(except, done) {
		var ag = activeGroup, poses = layout(true);
		if (except) { poses.delete(except); }
		run(poses, function (c) { return c.group === ag ? [SLIDE_MS, EASE.outQuart, FAN_MS, EASE.outCubic] : [SLIDE_MS, EASE.outQuart, SLIDE_MS, EASE.outQuart]; }, done);
	}

	/** Within a group later cards are drawn over earlier ones, the active group over the others, and the card on its way up over all. */
	function restack() {
		var order = [];
		groups.forEach(function (g) { if (g !== activeGroup) { order = order.concat(g.cards); } });
		if (activeGroup) { order = order.concat(activeGroup.cards); }
		if (topCard && order.indexOf(topCard) >= 0) { order.splice(order.indexOf(topCard), 1); order.push(topCard); }
		order.forEach(function (c, i) { c.el.style.zIndex = i + 1; });
	}

	/** LunaSysMgr's active card window: changing it dims the one before (CardDimmPercentage, 300 ms OutCubic). */
	function setCurrent(c) {
		if (c === current) { return; }
		if (current && current.group) { current.dim.style.opacity = String(1 - DIMMED); }
		current = c;
		if (c) { c.dim.style.opacity = "0"; }
	}
	function setActiveGroup(g) { activeGroup = g; setCurrent(g ? g.active : null); restack(); setTitle(); }
	/** CardWindowManager::groupClosestToCenterHorizontally. */
	function closestGroup() {
		var best = null, d = Infinity;
		groups.forEach(function (g) { var x = g.cards[0] ? Math.abs(g.cards[0].gx) : Infinity; if (x < d) { d = x; best = g; } });
		return best;
	}

	/** The status bar's title: the app's own name while its card is up, as the TouchPad showed it (not the page's title). */
	function setTitle() {
		var c = cur();
		$("popout").disabled = !(c && c.id);
		zoomState(false);
		if (jt && jt.open) { return; }
		$("title-text").textContent = maximized && c ? (appTitles[c.appId] || c.title) : "Luna DE";
	}
	var appTitles = {}, appEntries = {};
	/** The Phone Zoom button, for the card that is up: how its app's switch stands, or (flip) the other way. */
	function zoomState(flip) {
		var c = cur(), b = $("zoom");
		b.disabled = !(c && c.id);
		if (b.disabled) { b.classList.remove("on"); return; }
		window.luna.phoneZoom(c.id, flip).then(function (on) { b.classList.toggle("on", !!on); });
	}
	$("zoom").addEventListener("click", function () { zoomState(true); });

	/**
	 * LunaSysMgr's m_cardToRestoreToMaximized: a card that joined the focused card's group
	 * remembers that card, and if it is closed while it is up, the shell goes to the card view
	 * and then maximizes the remembered card again (Email's compose card closing after Send
	 * lands back on the inbox). Minimizing, switching cards or another card arriving forgets it.
	 */
	var restoreTo = null, restoreAfter = null;

	/** A card comes up: its picture grows to the screen (300 ms OutQuart), then its live view takes over. */
	function maximize(card, ms) {
		if (!card || !card.group) { return; }
		if (ms === undefined) { ms = MAXIMIZE_MS; }
		if (card !== restoreAfter) { restoreTo = null; restoreAfter = null; }
		closeLauncher();
		card.group.active = card;
		launchingCards.delete(card);
		setActiveGroup(card.group);
		activating = card; topCard = card; restack();
		maximized = true; upCard = null;
		document.body.classList.add("maximized");
		setTitle();
		run(maximizedLayout(card), function () { return [ms, EASE.outQuart, ms, EASE.outQuart]; }, function () {
			if (activating !== card) { return; }
			activating = null; upCard = card;
			showLive(card);
		});
	}

	/**
	 * The card that is up shows its live page, once it has one that is ready: until then the
	 * loading card stays on the screen. The splash fades out over the page (CardSplash.dismiss).
	 */
	function showLive(c) {
		if (!c.id || !c.pageReady || !maximized || cur() !== c || activating) { return; }
		window.luna.maximize(c.id);
		if (c.splash) { var sp = c.splash; c.splash = null; sp.classList.add("gone"); setTimeout(function () { sp.remove(); }, 320); }
	}

	/** Down to the card view: the live card's picture taken first, so it shrinks from what was on the screen. */
	function cardView(then) {
		restoreTo = null; restoreAfter = null;
		var c = maximized ? cur() : null;
		maximized = false; activating = null; upCard = null; topCard = null;
		document.body.classList.remove("maximized");
		setTitle();
		var done = function () { window.luna.cardView(); restack(); slide(null, then); refreshPictures(c); };
		if (!c || !c.id || c.splash) { done(); return; }
		window.luna.snapshot(c.id).then(function (url) { if (url) { c.img.src = url; } done(); }, done);
	}

	/** Every card's picture taken again, but [skip]'s, just taken: a page goes on drawing while hidden. */
	function refreshPictures(skip) {
		allCards().forEach(function (k) {
			if (k === skip || !k.id || k.splash) { return; }
			window.luna.snapshot(k.id).then(function (url) { if (url) { k.img.src = url; } });
		});
	}

	/** Launching cards whose apps aren't ready yet, waiting in the card view. */
	var launchingCards = new Set();

	/**
	 * A new card, in the shell's picture: its loading card with the app's splash icon
	 * (CardSplash), coming up from below the screen into the card view (CardLayer.openLaunching).
	 * [sibling] is the card whose group it joins (CardWindowManager::prepareAddWindowSibling).
	 */
	function newCard(appId, title, sibling) {
		var el = document.createElement("div");
		el.className = "card";
		var face = document.createElement("div");
		face.className = "face";
		var img = document.createElement("img");
		img.className = "picture";
		img.alt = "";
		face.appendChild(img);
		var c = { id: 0, appId: appId, title: title, el: el, face: face, img: img, pageReady: false, splash: null, born: performance.now(),
			group: null, gx: 0, rx: 0, cy: 0, scale: 1, rot: 0, lift: 0, fade: 1 };
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
			face.appendChild(splash);
			c.splash = splash;
		}
		c.dim = document.createElement("div");
		c.dim.className = "dim";
		face.appendChild(c.dim);
		el.appendChild(face);
		$("cards").appendChild(el);
		// The card it joins is the one to come back to when it goes (m_cardToRestoreToMaximized).
		restoreTo = sibling && sibling.group ? sibling : null;
		restoreAfter = restoreTo ? c : null;
		var g = sibling && sibling.group;
		if (!g) { g = { cards: [], active: null, left: 0, right: 0 }; groups.splice(activeGroup ? groups.indexOf(activeGroup) + 1 : groups.length, 0, g); }
		g.cards.push(c); c.group = g; g.active = c;
		setActiveGroup(g);
		// setActiveCardOffScreen: full size, its top at the bottom of the screen; then up into the card view.
		setPose(c, { gx: 0, rx: 0, cy: AREA + AREA / 2, scale: 1, rot: 0 });
		apply(c, 0, "", 0, "");
		topCard = c; restack();
		c.el.getBoundingClientRect();
		if (maximized) { window.luna.cardView(); maximized = false; upCard = null; activating = null; document.body.classList.remove("maximized"); }
		launchingCards.add(c);
		topCard = null;
		run(layout(true), function () { return [SLIDE_MS, EASE.outQuart, SLIDE_MS, EASE.outQuart]; });
		setTitle();
		return c;
	}

	/**
	 * The card a new one joins, by CardHost's rule: the card that is up, if the new card is a
	 * window its own app opened ([fromParent]), or one an app opened for the app that launched it ([launcher]).
	 */
	function siblingFor(appId, fromParent, launcher) {
		var up = maximized ? cur() : null;
		if (!up) { return null; }
		return (fromParent && up.appId === appId) || (launcher && launcher !== appId && up.appId === launcher) ? up : null;
	}

	/**
	 * A tap on an app that has no card: its loading card is on the screen at once, before the
	 * host has been asked, and takes the app's first card when main.js makes it. One that no
	 * card ever claims (the app only raised a dashboard, or couldn't start) goes again.
	 */
	function launching(entry, launcher) {
		if (allCards().some(function (c) { return c.appId === entry.id; })) { return; }
		var c = newCard(entry.id, entry.title, siblingFor(entry.id, false, launcher));
		c.waiting = setTimeout(function () { if (!c.id) { removeCard(c); } }, 10000);
	}

	/** A card main.js has made (a launch, or an app's window.open): it takes the loading card the tap put up, or comes in with its own. */
	function added(d) {
		closeJustType();
		closeLauncher();
		clearGlow();
		var c = allCards().filter(function (k) { return !k.id && k.appId === d.appId; })[0] || newCard(d.appId, d.title, siblingFor(d.appId, d.fromParent, d.launcher));
		clearTimeout(c.waiting);
		c.id = d.id;
		setTitle();
		// Up once its page is in, or after a while anyway.
		c.timer = setTimeout(function () { ready({ id: c.id }); }, 15000);
	}

	/**
	 * The card's page is ready: a launching card maximizes by itself (CardLayer.ready), unless
	 * another card has come up meanwhile or the card view is in hand, and its page takes the
	 * splash's place. Not before it has reached the card view: the launch is seen to arrive there.
	 */
	function ready(d) {
		var c = cardById(d.id);
		if (!c || c.pageReady) { return; }
		c.pageReady = true;
		clearTimeout(c.timer);
		if (maximized && cur() === c) { showLive(c); return; }
		var wait = Math.max(0, SLIDE_MS - (performance.now() - c.born));
		setTimeout(function () {
			if (!launchingCards.has(c) || !c.group) { return; }
			if ((maximized && cur() !== c) || cardDrag) { return; }
			maximize(c);
		}, wait);
	}

	/**
	 * A card gone (closed here, by its page, popped out, or a loading card unclaimed): out of its
	 * group, the card behind it its group's active one; the group nearest the middle the active
	 * group; a card that goes while it is up takes the shell back to the card view, and back up
	 * to the card it was opened from (restoreTo).
	 */
	function removeCard(c) {
		var back = restoreTo && c === restoreAfter ? restoreTo : null;
		if (c === restoreAfter || c === restoreTo) { restoreTo = null; restoreAfter = null; }
		var g = c.group;
		if (!g) { return; }
		var i = g.cards.indexOf(c), ai = g.cards.indexOf(g.active);
		g.cards.splice(i, 1); c.group = null;
		if (!g.cards.length) { g.active = null; groups.splice(groups.indexOf(g), 1); }
		else if (i === ai) { g.active = i > 0 ? g.cards[i - 1] : g.cards[0]; }
		c.el.remove();
		launchingCards.delete(c);
		if (reorderCard === c) { endReorder(false); }
		if (topCard === c) { topCard = null; }
		if (current === c) { current = null; }
		var wasUp = (maximized && (upCard === c || activating === c));
		if (activating === c) { activating = null; }
		if (upCard === c) { upCard = null; }
		setActiveGroup(g.cards.length && g === activeGroup ? g : closestGroup());
		if (wasUp) {
			maximized = false; document.body.classList.remove("maximized"); window.luna.cardView(); setTitle();
			slide(null, function () { if (back && back.group && !maximized) { maximize(back); } });
		} else if (!maximized) { slide(); }
	}

	function removed(d) { var c = cardById(d.id); if (c) { removeCard(c); } }

	/** Closes a card: off the top of the card area whichever way it was thrown, over 300 ms OutCubic, then gone. */
	function throwAway(c) {
		clearTimeout(runTimer);
		c.lift = -(AREA * c.scale / 2) - c.cy;
		apply(c, 0, "", DELETE_MS, EASE.outCubic);
		setTimeout(function () { closeCard(c); }, DELETE_MS);
	}
	function closeCard(c) { if (!c || !c.group) { return; } if (c.id) { window.luna.close(c.id); } else { removeCard(c); } }

	// ---- reorder (CardWindowManager's reorder state) ----

	var reorderCard = null, zone = 0;
	/** CardWindowManager::getReorderZone: either side of the middle W·activeScale. */
	function zoneAt(x) { var half = W * activeScale() / 2, rel = x - W / 2; return rel < -half ? -1 : rel > half ? 1 : 0; }
	function cx(c) { return W / 2 + c.gx + c.rx; }

	/** The held card goes 80 % opaque and leaves its group's layout. */
	function startReorder(c, x) {
		reorderCard = c; c.fade = REORDER_OPACITY; topCard = c; restack();
		zone = zoneAt(x);
		document.body.classList.add("reordering");
		apply(c, 0, "", 0, "");
	}
	function reorderMove(dx, dy, x) {
		var c = reorderCard;
		if (!c) { return; }
		c.rx += dx; c.cy += dy; c.scale = activeScale(); c.rot = 0;
		apply(c, 0, "", 0, "");
		var z = zoneAt(x);
		if (z === zone && z === 0) { moveReorderCentre(); }
		else if (z !== zone) { zone = z; if (z === 1) { moveReorderRight(); } else if (z === -1) { moveReorderLeft(); } }
	}
	/** The others make room: the active group to the middle, everyone to their places. */
	function arrangeAfterReorder(ms) {
		var ag = activeGroup, poses = layout(true);
		poses.delete(reorderCard);
		run(poses, function (c) { return c.group === ag ? [SHUFFLE_MS, EASE.outCubic, ms, EASE.outCubic] : [ms, EASE.outCubic, ms, EASE.outCubic]; }, function () {
			// A pointer still held to one side keeps going.
			if (reorderCard) { if (zone === 1) { moveReorderRight(); } else if (zone === -1) { moveReorderLeft(); } }
		});
	}
	/** CardGroup::moveActiveCard: past a sibling's centre, take its place. */
	function moveReorderCentre() {
		var c = reorderCard, g = c && c.group;
		if (!g) { return; }
		var a = g.cards.indexOf(c), x = cx(c), to = -1, i;
		for (i = 0; i < a; i++) { if (x < cx(g.cards[i])) { to = i; break; } }
		if (to < 0) { for (i = g.cards.length - 1; i > a; i--) { if (x > cx(g.cards[i])) { to = i; break; } } }
		if (to < 0) { return; }
		g.cards.splice(a, 1); g.cards.splice(to, 0, c);
		setActiveGroup(g); arrangeAfterReorder(SHUFFLE_MS);
	}
	function detach(c, g) { var i = g.cards.indexOf(c); g.cards.splice(i, 1); g.active = g.cards.length ? g.cards[Math.max(0, i - 1)] : null; }
	/** moveReorderSlotRight: one place right in the group; past its front, out of it. A card that was alone joins the next group at the back; one leaving a group starts a new one. */
	function moveReorderRight() {
		var c = reorderCard, g = c && c.group;
		if (!g) { return; }
		var a = g.cards.indexOf(c);
		if (a < g.cards.length - 1) { g.cards[a] = g.cards[a + 1]; g.cards[a + 1] = c; setActiveGroup(g); arrangeAfterReorder(SHUFFLE_MS); }
		else if (g !== groups[groups.length - 1] || g.cards.length > 1) {
			var gi = groups.indexOf(g), ng;
			detach(c, g);
			if (!g.cards.length) { groups.splice(gi, 1); ng = groups[gi]; } else { ng = { cards: [], active: null, left: 0, right: 0 }; groups.splice(gi + 1, 0, ng); }
			ng.cards.unshift(c); c.group = ng; ng.active = c;
			setActiveGroup(ng); arrangeAfterReorder(GROUP_REORDER_MS);
		}
	}
	/** moveReorderSlotLeft, the mirror: a card that was alone joins the previous group at the front. */
	function moveReorderLeft() {
		var c = reorderCard, g = c && c.group;
		if (!g) { return; }
		var a = g.cards.indexOf(c);
		if (a > 0) { g.cards[a] = g.cards[a - 1]; g.cards[a - 1] = c; setActiveGroup(g); arrangeAfterReorder(SHUFFLE_MS); }
		else if (g !== groups[0] || g.cards.length > 1) {
			var gi = groups.indexOf(g), ng;
			detach(c, g);
			if (!g.cards.length) { groups.splice(gi, 1); ng = groups[Math.max(0, gi - 1)]; } else { ng = { cards: [], active: null, left: 0, right: 0 }; groups.splice(gi, 0, ng); }
			ng.cards.push(c); c.group = ng; ng.active = c;
			setActiveGroup(ng); arrangeAfterReorder(GROUP_REORDER_MS);
		}
	}
	/** Let go: opaque again, back in its group, and everything to its place. */
	function endReorder(animate) {
		var c = reorderCard;
		if (!c) { return; }
		reorderCard = null; c.fade = 1; topCard = null;
		document.body.classList.remove("reordering");
		restack();
		if (animate !== false) { slide(); }
	}

	// ---- touch in the card view (CardLayer.onTouchEvent) ----

	/** The topmost card under a point, of [g] only when given. */
	function cardAt(x, y, g) {
		var order = allCards().slice().sort(function (a, b) { return (+a.el.style.zIndex || 0) - (+b.el.style.zIndex || 0); });
		for (var i = order.length - 1; i >= 0; i--) {
			var c = order[i];
			if (g && c.group !== g) { continue; }
			var w = W * c.scale, h = AREA * c.scale, x0 = cx(c), y0 = c.cy + c.lift;
			if (x >= x0 - w / 2 && x <= x0 + w / 2 && y >= y0 - h / 2 && y <= y0 + h / 2) { return c; }
		}
		return null;
	}
	/** CardGroup::withinColumn. */
	function withinColumn(g, x) { var gx = W / 2 + (g.cards[0] ? g.cards[0].gx : 0); return x >= gx - g.left && x <= gx + g.right; }

	var cardDrag = null;   // the pointer's gesture in the card view, while it is down
	(function () {
		var box = $("cards");
		box.addEventListener("pointerdown", function (e) {
			if (e.button !== 0 || maximized || activating) { return; }
			var r = box.getBoundingClientRect(), x = e.clientX - r.left, y = e.clientY - r.top;
			var ag = activeGroup;
			var d = cardDrag = { kind: "undecided", x0: x, y0: y, lastX: x, lastY: y, card: ag ? cardAt(x, y, ag) : null, samples: [] };
			if (d.card) { ag.active = d.card; }
			d.pivot = ag && ag.cards[0] ? ag.cards[0].gx : 0;
			box.setPointerCapture(e.pointerId);
			// Tap-and-hold: on a card of the active group, pick it up; on the space either side, the group that way.
			d.hold = setTimeout(function () {
				if (d.kind !== "undecided") { return; }
				if (d.card) { d.kind = "reorder"; startReorder(d.card, d.lastX); }
				else {
					d.kind = "none";
					var i = groups.indexOf(activeGroup) + (d.x0 < W / 2 ? -1 : 1);
					if (groups[i]) { setActiveGroup(groups[i]); slide(); }
				}
			}, HOLD_MS);
			function move(ev) {
				var mx = ev.clientX - r.left, my = ev.clientY - r.top, dx = mx - d.x0, dy = my - d.y0;
				d.samples.push([ev.timeStamp, mx, my]); if (d.samples.length > 6) { d.samples.shift(); }
				if (d.kind === "undecided" && Math.hypot(dx, dy) > TAP) {
					clearTimeout(d.hold);
					d.kind = Math.abs(dx) > AXIS_LOCK * Math.abs(dy) ? "scroll" : d.card ? "throw" : "none";
				}
				if (d.kind === "scroll") {
					// slideAllGroupsTo: the groups follow the pointer, collapsing as they leave the middle.
					d.pivot += mx - d.lastX;
					run(layout(true, d.pivot, true), function () { return [TRACK_GROUP_MS, EASE.linear, TRACK_MS, EASE.linear]; });
				} else if (d.kind === "throw") { d.card.lift = dy; apply(d.card, 0, "", 0, ""); }
				else if (d.kind === "reorder") { reorderMove(mx - d.lastX, my - d.lastY, mx); }
				d.lastX = mx; d.lastY = my;
			}
			function up(ev) {
				box.removeEventListener("pointermove", move); box.removeEventListener("pointerup", up); box.removeEventListener("pointercancel", up);
				clearTimeout(d.hold);
				cardDrag = null;
				var vx = 0, vy = 0, s = d.samples;
				if (s.length > 1 && ev.timeStamp - s[s.length - 1][0] < 80 && s[s.length - 1][0] > s[0][0]) {
					var dt = (s[s.length - 1][0] - s[0][0]) / 1000; vx = (s[s.length - 1][1] - s[0][1]) / dt; vy = (s[s.length - 1][2] - s[0][2]) / dt;
				}
				var c = d.card;
				if (d.kind === "scroll") {
					// The group nearest the middle becomes active; a flick goes one further.
					var g = closestGroup();
					if (Math.abs(vx) > FLING_MIN && g) { g = groups[Math.max(0, Math.min(groups.length - 1, groups.indexOf(g) + (vx < 0 ? 1 : -1)))]; }
					setActiveGroup(g); slide();
				} else if (d.kind === "throw" && c) {
					// Luna's rule: far enough, fast enough, and faster the shorter the drag.
					var dist = c.lift, v = vy / 3;
					var flung = dist < THROW_DISTANCE && v < THROW_MIN_VELOCITY && v < (THROW_VELOCITY * THROW_DISTANCE) / dist;
					if (flung || c.cy + c.lift < 0 || c.cy + c.lift > AREA) { throwAway(c); } else { slide(); }
				} else if (d.kind === "reorder") { endReorder(); }
				else if (d.kind === "undecided" && ev.type === "pointerup") {
					var ag2 = activeGroup;
					if (c) { maximize(c); }
					else if (ag2 && !withinColumn(ag2, d.x0)) {
						// A tap on another group makes it the active one.
						var other = cardAt(d.x0, d.y0);
						if (other && other.group) { setActiveGroup(other.group); slide(); }
					}
				}
			}
			box.addEventListener("pointermove", move); box.addEventListener("pointerup", up); box.addEventListener("pointercancel", up);
		});
		// Luna DE's tools for a card, by the right button.
		box.addEventListener("contextmenu", function (e) {
			e.preventDefault();
			if (maximized) { return; }
			var r = box.getBoundingClientRect(), c = cardAt(e.clientX - r.left, e.clientY - r.top);
			if (c) { showMenu(e.clientX, e.clientY, c); }
		});
	})();

	/** The card view relaid at once, for a new screen size: maximized or not. */
	function relayout() {
		var poses = maximized && cur() ? maximizedLayout(cur()) : layout(true);
		poses.forEach(function (p, c) { setPose(c, p); apply(c, 0, "", 0, ""); });
		restack();
	}

	/** The SDK emulator's keys and the wheel in the card view: the next group either way. */
	function stepGroup(d) {
		if (!activeGroup) { return; }
		var i = Math.max(0, Math.min(groups.length - 1, groups.indexOf(activeGroup) + d));
		setActiveGroup(groups[i]); slide();
	}

	// ---- Luna DE's tools for a card ----

	function showMenu(x, y, c) {
		showItems(x, y, [["Open", function () { maximize(c); }],
		 ["Pop out to its own window", function () { window.luna.popOut(c.id); }],
		 ["DevTools", function () { window.luna.devTools(c.id); }],
		 ["Reload", function () { window.luna.reload(c.id); }],
		 ["Close", function () { closeCard(c); }]]);
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

	// ---- the pages side by side (Launcher's pagePos) ----
	//
	// A sideways drag on a page that started on no icon (or on one, outside edit mode) pans the
	// pages with the pointer, the neighbours alongside, up to a fifth of a page past either end.
	// Let go, it snaps to the nearest page over 250 ms InQuad, or, flicked faster than 500 px/s,
	// goes on to the next that way, as long as the flick's speed would take it (200 to 1200 ms,
	// OutCubic). A tab tapped slides there the same 250 ms. Up and down, a drag scrolls the page.
	var SNAP_MS = 250, FLICK_V = 500, FLICK_MIN_MS = 200, FLICK_MAX_MS = 1200;
	var IN_QUAD = "cubic-bezier(0.55, 0.085, 0.68, 0.53)", OUT_CUBIC = "cubic-bezier(0.215, 0.61, 0.355, 1)";
	var strip = null;   // the neighbours while the pages move: { ghosts: { -1: el, 1: el }, offset }

	/** A picture of page [n], laid out as the grid is, for beside it while the pages move. */
	function ghostOf(n) {
		var grid = $("grid"), g = document.createElement("div"), cs = getComputedStyle(grid);
		g.className = "page-ghost";
		["gridTemplateColumns", "gridAutoRows", "rowGap", "paddingTop", "paddingLeft", "paddingRight", "paddingBottom", "alignContent"].forEach(function (k) { g.style[k] = cs[k]; });
		g.style.top = grid.offsetTop + "px"; g.style.height = grid.offsetHeight + "px";
		(pages[n] ? pages[n].apps : []).forEach(function (c) { var t = tileFor(c); t.style.pointerEvents = "none"; g.appendChild(t); });
		$("launcher").appendChild(g);
		return g;
	}

	function stripOpen() {
		if (strip) { return strip; }
		strip = { ghosts: {}, offset: 0 };
		[-1, 1].forEach(function (d) { if (pages[page + d]) { strip.ghosts[d] = ghostOf(page + d); } });
		return strip;
	}

	/** The pages [px] along from where the current one rests (negative: towards the next). */
	function stripTo(px, ms, easing) {
		var s = stripOpen();
		s.offset = px;
		var t = ms ? "transform " + ms + "ms " + easing : "none";
		$("grid").style.transition = t;
		$("grid").style.transform = "translateX(" + px + "px)";
		Object.keys(s.ghosts).forEach(function (d) {
			var g = s.ghosts[d];
			g.style.transition = t;
			g.style.transform = "translateX(" + (px + d * W) + "px)";
		});
	}

	function stripClose() {
		if (!strip) { return; }
		Object.keys(strip.ghosts).forEach(function (d) { strip.ghosts[d].remove(); });
		strip = null;
		$("grid").style.transition = ""; $("grid").style.transform = "";
	}

	/** To page [target]: from where the strip is, over [ms] with [easing]; the tab shows it at once. */
	var settling = null;
	function slideTo(target, ms, easing) {
		target = Math.max(0, Math.min(pages.length - 1, target));
		clearTimeout(settling);
		if (target === page && !strip) { showPage(target); return; }
		if (Math.abs(target - page) > 1) { stripClose(); showPage(target); return; }
		[].forEach.call($("tabs").children, function (b, i) { b.classList.toggle("selected", i === target); });
		stripOpen();
		$("grid").getBoundingClientRect();
		stripTo((page - target) * W, ms, easing);
		settling = setTimeout(function () { stripClose(); showPage(target); }, ms);
	}

	(function () {
		var grid = $("grid");
		grid.addEventListener("pointerdown", function (e) {
			if (e.button !== 0 || drag || settling && strip) { return; }
			var onTile = e.target.closest && e.target.closest(".tile");
			if (editing && onTile) { return; }   // in edit mode a drag on an icon picks it up
			var x0 = e.clientX, y0 = e.clientY, axis = null, scroll0 = grid.scrollTop, samples = [];
			function move(ev) {
				var dx = ev.clientX - x0, dy = ev.clientY - y0;
				if (!axis && Math.max(Math.abs(dx), Math.abs(dy)) > TAP) { axis = Math.abs(dx) >= Math.abs(dy) ? "x" : "y"; }
				if (drag) { axis = null; return; }   // an icon was picked up meanwhile
				samples.push([ev.timeStamp, ev.clientX]); if (samples.length > 6) { samples.shift(); }
				if (axis === "x") {
					// Up to a fifth of a page past either end.
					var pos = Math.max(-0.2, Math.min(pages.length - 0.8, page - dx / W));
					stripTo((page - pos) * W, 0);
				} else if (axis === "y") { grid.scrollTop = scroll0 - dy; }
			}
			function up(ev) {
				window.removeEventListener("pointermove", move); window.removeEventListener("pointerup", up); window.removeEventListener("pointercancel", up);
				if (axis !== "x" || !strip) { return; }
				// The pointer's speed over its last moves; held still before it was let go, none.
				var vx = 0;
				if (samples.length > 1 && ev.timeStamp - samples[samples.length - 1][0] < 80) {
					var a = samples[0], b = samples[samples.length - 1]; if (b[0] > a[0]) { vx = (b[1] - a[1]) / (b[0] - a[0]) * 1000; }
				}
				var pos = page - strip.offset / W;
				if (Math.abs(vx) > FLICK_V) {
					var target = Math.max(0, Math.min(pages.length - 1, page + (vx < 0 ? 1 : -1)));
					var ms = Math.max(FLICK_MIN_MS, Math.min(FLICK_MAX_MS, Math.abs(target - pos) * W / (Math.abs(vx) / 1000)));
					slideTo(target, Math.round(ms), OUT_CUBIC);
				} else { slideTo(Math.round(Math.max(0, Math.min(pages.length - 1, pos))), SNAP_MS, IN_QUAD); }
			}
			window.addEventListener("pointermove", move); window.addEventListener("pointerup", up); window.addEventListener("pointercancel", up);
		});
		// A trackpad's sideways swipe turns the page too.
		var wheelX = 0, wheelTimer = null;
		grid.addEventListener("wheel", function (e) {
			if (Math.abs(e.deltaX) <= Math.abs(e.deltaY) || strip) { return; }
			e.preventDefault();
			wheelX += e.deltaX;
			clearTimeout(wheelTimer); wheelTimer = setTimeout(function () { wheelX = 0; }, 200);
			if (Math.abs(wheelX) > 80) { var d = wheelX > 0 ? 1 : -1; wheelX = 0; slideTo(page + d, SNAP_MS, IN_QUAD); }
		}, { passive: false });
	})();

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
					onTap: function () { if (!tabDragged) { slideTo(i, SNAP_MS, IN_QUAD); } },
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
	function closeScrim() { $("scrim").hidden = true; $("scrim").innerHTML = ""; $("scrim").classList.remove("picture"); scrimAway = null; }
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
	/**
	 * LunaCE's AppInfoDialog, as AndroidLuna's RemoveDialog draws it: over scrim.png, a 342 px
	 * panel in popup-bg.png, "Remove Application?" in bold 18 and the app and its version in bold
	 * 14, centred, and Cancel over Remove, each 52 px in pin/button-black.png, stacked across the
	 * panel. A tap outside is Cancel.
	 */
	function removeDialog(a) {
		var panel = document.createElement("div"); panel.className = "remove-dialog";
		var h = document.createElement("div"); h.className = "title"; h.textContent = "Remove Application?"; panel.appendChild(h);
		var what = document.createElement("div"); what.className = "what"; what.textContent = a.title + " - v." + (a.version || ""); panel.appendChild(what);
		[["Cancel", function () {}], ["Remove", function () { window.luna.remove(a.id); }]].forEach(function (it) {
			var b = document.createElement("button"); b.textContent = it[0];
			b.addEventListener("click", function () { closeScrim(); it[1](); });
			panel.appendChild(b);
		});
		openScrim(panel, closeScrim);
		$("scrim").classList.add("picture");
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
		var running = allCards().some(function (c) { return c.appId === entry.id; }) || entry.switch;
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
		closeJustType();
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
		if (sys.open) { sysClose(); }
		else if (!$("scrim").hidden) { if (scrimAway) { scrimAway(); } else { closeScrim(); } }
		else if (jt.open) { closeJustType(); }
		else if (editing) { exitEdit(); }
		else if (!$("launcher").hidden) { closeLauncher(); }
		else if (maximized) { cardView(); }
	}
	$("home").addEventListener("click", home);
	// The log window; and by right-click, the developer tools of a card.
	$("devtools").addEventListener("click", function () { window.luna.log(); });
	$("devtools").addEventListener("contextmenu", function (e) { e.preventDefault(); window.luna.toolsMenu(); });
	// The bezel's other corner: the card that is up, or in front in the card view, popped out.
	$("popout").addEventListener("click", function () { var c = cur(); if (c && c.id) { window.luna.popOut(c.id); } });
	// And the device itself: a TouchPad or a Pre3, by its picture.
	$("device").addEventListener("click", function () { window.luna.device(); });
	// The bezel's right button, anywhere on it but the buttons that have their own: full screen and the device.
	$("bezel").addEventListener("contextmenu", function (e) {
		if (e.target.closest && e.target.closest("#devtools, #realkb")) { return; }
		e.preventDefault();
		window.luna.bezelMenu();
	});
	/** The screen as main.js gives it (in this page's px), the device's picture, and what the device hasn't got. */
	function applyScreen(d) {
		var wasPhone = document.body.classList.contains("phone");
		document.body.classList.toggle("phone", d.device === "pre3");
		measure(d.width, d.height);
		document.body.classList.toggle("portrait", d.height > d.width);
		if (wasPhone !== (d.device === "pre3") && pages.length) { loadApps(false); }
		if (wasPhone !== (d.device === "pre3")) { closeJustType(); sysClose(); }
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
		else if (e.key === "Escape" && (editing || !$("scrim").hidden || sys.open)) { e.preventDefault(); home(); }
		// Left and Right switch cards in the card view (the SDK emulator's keys).
		else if ((e.key === "ArrowLeft" || e.key === "ArrowRight") && !maximized && groups.length && $("launcher").hidden) {
			stepGroup(e.key === "ArrowRight" ? 1 : -1);
		} else if (e.key === "Enter" && !maximized && cur() && $("launcher").hidden) { maximize(cur()); }
	});
	// Home (the emulator's key): the card on the screen down to the card view, and up again.
	function toggleCard() {
		if (!$("launcher").hidden) { closeLauncher(); }
		else if (maximized) { cardView(); }
		else if (cur()) { maximize(cur()); }
	}
	// The title is the app menu's, while a card is up (the TouchPad's status bar).
	$("title").addEventListener("click", function () { if (maximized) { window.luna.appMenu(); } });

	// ---- Just Type ----
	//
	// Palm's Just Type app, drawn to its measurements and with its art as AndroidLuna's
	// JustTypePanel has them. What a text finds, and what each choice does, is the host's to say
	// (LunaCore's JustTypeSearch over com.palm.universalsearch, which every installed app's
	// appinfo.json feeds): the apps to LAUNCH; "Go to website" for something that reads as an
	// address; each app's content search with results and its count; "Search <default engine>";
	// SEARCH USING… and QUICK ACTIONS, four and then More…. The results follow the typing by
	// half a second, and the content searches by half a second more. An email address shows the
	// actions alone. Return takes the first thing offered.
	var JT_ART = "../../AndroidLuna/src/main/assets/luna/images/justtype/";
	var JT_SEARCH_MS = 500, JT_DB_MS = 500;
	var JT_FILTERS = ["All", "Contacts", "Content", "Actions"];
	var JT_EMAIL = /^([A-Za-z0-9_\-.])+@([A-Za-z0-9_\-.@])+\.([A-Za-z]{2,4}) *$/;
	var jt = { open: false, term: "", searched: false, view: 0, actionsOnly: false, detail: null, moreSearch: false, moreActions: false,
		data: null, content: [], timer: null, dbTimer: null, gen: 0 };

	function jtIcon(item) {
		var img = document.createElement("img");
		img.alt = "";
		var generic = JT_ART + "search-icon-generic.png";
		img.onerror = function () { img.onerror = null; img.src = generic; };
		if (item.type === "web") { img.src = JT_ART + "search-icon-" + item.id + ".png"; }
		else if (item.type === "opensearch") { img.src = generic; }
		else if (appEntries[item.id]) { img.src = appEntries[item.id].icon; }
		else { window.luna.iconFor(item.id).then(function (u) { img.src = u; }); }
		return img;
	}

	function openJustType(initial) {
		if (maximized || jt.open) { return; }
		closeLauncher();
		jt.open = true;
		var el = $("justtype");
		el.hidden = false; el.getBoundingClientRect();
		document.body.classList.add("justtype");
		$("title-text").textContent = "Just Type";
		var input = $("jt-input");
		input.value = initial || "";
		input.focus();
		jtTyped(input.value);
	}

	function closeJustType() {
		if (!jt.open) { return; }
		jt.open = false;
		clearTimeout(jt.timer); clearTimeout(jt.dbTimer); jt.gen++;
		document.body.classList.remove("justtype");
		$("jt-input").blur();
		setTimeout(function () { if (!jt.open) { $("justtype").hidden = true; $("jt-input").value = ""; jtTyped(""); } }, 150);   // universalSearchCrossFadeDuration
		setTitle();
	}

	function jtTyped(text) {
		clearTimeout(jt.timer);
		$("jt-clear").hidden = !text;
		if (!text) {
			// JustType.clearSearchText: back to the start, tabs and all.
			clearTimeout(jt.dbTimer); jt.gen++;
			jt.term = ""; jt.searched = false; jt.view = 0; jt.actionsOnly = false; jt.detail = null;
			jt.moreSearch = false; jt.moreActions = false; jt.data = null; jt.content = [];
			jtDraw(); return;
		}
		// updateEmailOrWebDiv runs on every keystroke from three characters.
		if (text.length >= 3) {
			if (JT_EMAIL.test(text)) { jt.actionsOnly = true; jt.view = 3; jt.detail = null; }
			else if (jt.actionsOnly) { jt.actionsOnly = false; jt.view = 0; }
		}
		jt.timer = setTimeout(function () { jtSearch(text); }, JT_SEARCH_MS);
		// "Start typing…" is for an empty field only: it goes with the first character.
		if (!jt.searched) { jtDraw(); }
	}

	function jtSearch(text) {
		if (!text || !jt.open) { return Promise.resolve(); }
		var gen = ++jt.gen;
		clearTimeout(jt.dbTimer);
		return window.luna.justType(text, false).then(function (d) {
			if (gen !== jt.gen || !d) { return; }
			var first = !jt.searched;
			jt.searched = true; jt.term = text; jt.data = d; jt.moreSearch = false; jt.moreActions = false;
			if (text.length < 2) { jt.content = []; if (jt.detail) { jt.detail = null; jt.view = 2; } }
			else {
				jt.dbTimer = setTimeout(function () {
					window.luna.justType(text, true).then(function (d2) {
						if (gen !== jt.gen || !d2) { return; }
						jt.content = d2.content || [];
						// The results being looked at went away: back to the content groups.
						if (jt.detail && !jt.content.some(function (c) { return c.id === jt.detail; })) { jt.detail = null; jt.view = 2; }
						jtDraw();
					});
				}, JT_DB_MS);
			}
			if (first) { $("jt-list").scrollTop = 0; }
			jtDraw();
		});
	}

	function jtGo(kind, id, index) {
		var term = jt.term;
		closeJustType();
		if (kind === "app") { launch(appEntries[id] || { id: id, title: id }, null); return; }
		window.luna.justTypeGo(kind, id, term, index);
	}

	/** Return: the address, else the first app found, else the default search. */
	function jtEnter() {
		clearTimeout(jt.timer);
		var text = $("jt-input").value;
		if (!text) { return; }
		var ready = text !== jt.term || !jt.searched ? jtSearch(text) : Promise.resolve();
		ready.then(function () {
			var d = jt.data;
			if (!d || !jt.open) { return; }
			if (d.goToWeb && !jt.actionsOnly) { jtGo("web", "", 0); return; }
			if (jt.view !== 2 && jt.view !== 1 && d.launch.length) { jtGo("app", d.launch[0].id, 0); return; }
			if (d.default) { jtGo("default", d.default.id, 0); }
		});
	}

	function jtGroup(caption, focused) {
		var g = document.createElement("div");
		g.className = "jt-group" + (caption ? " labeled" : "") + (focused ? " focused" : "");
		if (caption) { var c = document.createElement("div"); c.className = "caption"; c.textContent = caption; g.appendChild(c); }
		return g;
	}
	function jtRow(iconEl, label, tap, cls) {
		var r = document.createElement("div");
		r.className = "jt-row" + (cls ? " " + cls : "");
		if (iconEl) { r.appendChild(iconEl); }
		var s = document.createElement("span"); s.className = "label"; s.textContent = label; r.appendChild(s);
		if (tap) { r.addEventListener("click", tap); }
		return r;
	}
	function jtArt(name) { var i = document.createElement("img"); i.alt = ""; i.src = JT_ART + name; return i; }
	/** Util.highlightString: every word that starts with the typed text, underlined. */
	function jtHighlight(el, text, words) {
		el.textContent = "";
		var w = words.trim(), re = null;
		try { re = w ? new RegExp("\\b(" + w.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + ")", "ig") : null; } catch (e) {}
		var at = 0, m;
		while (re && (m = re.exec(text))) {
			if (m.index > at) { el.appendChild(document.createTextNode(text.slice(at, m.index))); }
			var u = document.createElement("u"); u.textContent = m[0]; el.appendChild(u);
			at = m.index + m[0].length;
			if (!m[0].length) { break; }
		}
		el.appendChild(document.createTextNode(text.slice(at)));
	}
	/** Four, then More… when there is more than one left (populateLaunchAndSearchList). */
	function jtLimited(g, items, all, more, row) {
		var limit = (jt.data && jt.data.limit) || 4;
		if (all || items.length <= limit + 1) { items.forEach(function (i) { g.appendChild(row(i)); }); return; }
		items.slice(0, limit).forEach(function (i) { g.appendChild(row(i)); });
		g.appendChild(jtRow(jtArt("search-icon-new.png"), "More...", function () { more(); jtDraw(); }, "more"));
	}
	function jtResultRow(item, r, index, cls) {
		var row = document.createElement("div");
		row.className = "jt-row result " + cls;
		var lines = document.createElement("div"); lines.className = "lines";
		(r.lines || []).forEach(function (l, i) { var d = document.createElement("div"); d.className = i ? "l2" : "l1"; jtHighlight(d, l, jt.term); lines.appendChild(d); });
		row.appendChild(lines);
		var kind = { "com.palm.browserbookmarks:1": "bookmark-icon-48.png", "com.palm.browserhistory:1": "history-icon-48.png" }[r.kind];
		if (kind && cls === "detail") { var k = jtArt(kind); k.className = "kind"; row.appendChild(k); }
		row.addEventListener("click", function () { jtGo("result", item.id, index); });
		return row;
	}

	function jtActions(d) {
		if (!d.actions.length) { return null; }
		var g = jtGroup("Quick Actions");
		jtLimited(g, d.actions, jt.moreActions, function () { jt.moreActions = true; }, function (a) { return jtRow(jtIcon(a), a.displayName, function () { jtGo("action", a.id, 0); }); });
		return g;
	}

	function jtDraw() {
		var list = $("jt-list"), tabs = $("jt-tabs"), d = jt.data;
		list.innerHTML = "";
		tabs.hidden = !jt.searched;
		if (!jt.searched || !d) {
			if ($("jt-input").value) { return; }
			// Just Type with nothing typed: HomeView's emptyMain.
			var e = document.createElement("div"); e.className = "jt-empty"; e.appendChild(jtArt("empty-search.png"));
			var t = document.createElement("div"); t.textContent = "Start typing to search or create"; e.appendChild(t);
			list.appendChild(e); return;
		}
		// The filter tabs: six cells on a tablet, the outer two empty; the four alone on a phone,
		// where, too narrow for 15 px, every label shrinks alike until CONTACTS fits 8 px inside its cell.
		tabs.innerHTML = "";
		var phone = document.body.classList.contains("phone");
		if (phone) {
			var ctx = (jtDraw.canvas = jtDraw.canvas || document.createElement("canvas")).getContext("2d");
			ctx.font = "bold 15px Prelude, sans-serif";
			var widest = Math.max.apply(null, JT_FILTERS.map(function (f) { return ctx.measureText(f.toUpperCase()).width; }));
			var room = W / 4 - 8;
			tabs.style.setProperty("--jt-tab-px", (widest <= room ? 15 : 15 * room / widest).toFixed(2) + "px");
		}
		for (var i = 0; i < 6; i++) {
			var f = JT_FILTERS[i - 1];
			if (phone && !f) { continue; }
			var cell = document.createElement("button");
			cell.className = (f ? "filter" : "blank") + (i - 1 === jt.view ? " chosen" : "");
			// The label centred on the whole cell, over the art's 34 px ends too.
			var lab = document.createElement("span"); lab.textContent = f || ""; cell.appendChild(lab);
			if (f) { (function (n) { cell.addEventListener("click", function () {
				// JustType.tabClickHandler → MainView.showView.
				jt.actionsOnly = false; jt.detail = null; jt.moreSearch = false; jt.moreActions = false; jt.view = n; list.scrollTop = 0; jtDraw();
			}); })(i - 1); }
			tabs.appendChild(cell);
		}
		var groups = [];
		if (jt.detail) {
			var item = jt.content.filter(function (c) { return c.id === jt.detail; })[0];
			if (item) {
				var dg = jtGroup(item.displayName + " (" + item.results.length + ")");
				item.results.forEach(function (r, n) { dg.appendChild(jtResultRow(item, r, n, "detail")); });
				groups.push(dg);
			}
		} else if (jt.view === 1) {
			// Contacts aren't searched yet (as on Android).
		} else if (jt.actionsOnly) {
			var only = jtActions(d); if (only) { groups.push(only); }
		} else {
			var all = jt.view === 0, goToWeb = all && d.goToWeb;
			if (goToWeb) {
				var wg = jtGroup(null, true);
				wg.appendChild(jtRow(jtArt("goto-icon-32.png"), "Go to website", function () { jtGo("web", "", 0); }, "web"));
				groups.push(wg);
			}
			if ((all || jt.view === 3) && d.launch.length) {
				var lg = jtGroup("Launch"); lg.classList.add("launch");
				d.launch.forEach(function (a, n) {
					var tile = document.createElement("button");
					tile.className = "jt-tile" + (n === 0 ? " first" : "");
					tile.appendChild(icon(a));
					var name = document.createElement("span");
					// The matched start of the title underlined.
					if (a.match >= 0) {
						name.appendChild(document.createTextNode(a.title.slice(0, a.match)));
						var u = document.createElement("u"); u.textContent = a.title.slice(a.match, a.match + a.matchLength); name.appendChild(u);
						name.appendChild(document.createTextNode(a.title.slice(a.match + a.matchLength)));
					} else { name.textContent = a.title; }
					tile.appendChild(name);
					tile.addEventListener("click", function () { jtGo("app", a.id, 0); });
					lg.appendChild(tile);
				});
				groups.push(lg);
			}
			if (all || jt.view === 2) {
				jt.content.forEach(function (c) {
					var cg = jtGroup(null);
					var hi = c.id === "com.palm.app.email" || c.id === "com.palm.app.enyo-email" ? jtArt("icon-email.png") : c.id === "com.palm.app.browser" ? jtArt("go-url.png") : null;
					if (!hi) { hi = document.createElement("span"); hi.className = "dbicon"; var ii = jtIcon(c); ii.onerror = function () { ii.onerror = null; ii.src = JT_ART + "calendar.png"; }; hi.appendChild(ii); }
					var head = jtRow(hi, c.displayName, function () { jt.detail = c.id; jt.view = 2; list.scrollTop = 0; jtDraw(); }, "content" + (c.inline ? " open" : ""));
					var pill = document.createElement("span"); pill.className = "count"; pill.textContent = c.results.length; head.appendChild(pill);
					cg.appendChild(head);
					if (c.inline) { c.results.forEach(function (r, n) { cg.appendChild(jtResultRow(c, r, n, "drawer")); }); }
					groups.push(cg);
				});
			}
			if (all || jt.view === 3) {
				if (d.default) {
					var sg = jtGroup(null, !goToWeb && !d.launch.length);
					sg.appendChild(jtRow(jtIcon(d.default), "Search " + d.default.displayName, function () { jtGo("default", d.default.id, 0); }));
					groups.push(sg);
				}
				if (d.using.length) {
					var ug = jtGroup("Search Using...");
					jtLimited(ug, d.using, jt.moreSearch, function () { jt.moreSearch = true; }, function (e2) { return jtRow(jtIcon(e2), e2.displayName, function () { jtGo("using", e2.id, 0); }); });
					groups.push(ug);
				}
				var ag = jtActions(d); if (ag) { groups.push(ag); }
			}
		}
		if (!groups.length && (jt.view === 1 || jt.view === 2)) {
			// "No Search Results Found", as DbSearch and ContactSearch show it.
			var none = document.createElement("div"); none.className = "jt-empty none"; none.appendChild(jtArt("empty-search.png"));
			var nt = document.createElement("div"); nt.textContent = "No Search Results Found"; none.appendChild(nt);
			list.appendChild(none); return;
		}
		list.classList.toggle("detail", !!jt.detail);
		groups.forEach(function (g) { list.appendChild(g); });
	}

	$("pill").addEventListener("click", function () { openJustType(""); });
	$("jt-input").addEventListener("input", function () { jtTyped($("jt-input").value); });
	$("jt-input").addEventListener("keydown", function (e) {
		e.stopPropagation();
		if (e.key === "Enter") { e.preventDefault(); jtEnter(); }
		else if (e.key === "Escape") { e.preventDefault(); closeJustType(); }
	});
	$("jt-clear").addEventListener("click", function () { $("jt-input").value = ""; jtTyped(""); $("jt-input").focus(); });
	// Just type: a printable key in the card view or the launcher opens Just Type with that
	// character, as on a TouchPad with a keyboard paired.
	document.addEventListener("keydown", function (e) {
		if (jt.open || maximized || editing || !$("scrim").hidden || e.ctrlKey || e.metaKey || e.altKey) { return; }
		if (e.key.length !== 1 || e.key === " " || (e.target && /^(INPUT|TEXTAREA)$/.test(e.target.tagName))) { return; }
		e.preventDefault();
		openJustType(e.key);
	});

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
				// A row is swept away to the right (main.js, app-preload.js), as on a device.
				var row = document.createElement("div"); row.className = "row";
				box.appendChild(row);
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
		if (sys.open) { sysClose(); }
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
		if (maximized || !groups.length || cardDrag) { return; }
		var d = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
		if (Math.abs(d) < 20) { return; }
		stepGroup(d > 0 ? 1 : -1);
	}, { passive: true });

	// ---- the system menu ----
	//
	// The status bar's right-hand group opens it: LunaSysMgr's SystemMenu.qml as the reference
	// TouchPad has it and AndroidLuna's SystemMenu draws it. 300 px wide in menu-dropdown-bg.png,
	// its right edge 11 px past the screen's; the content 7 px in at the sides and 14 px up from
	// the bottom; rows 42 px high with menu-divider.png between them; headers 14 px in and a
	// drawer's rows 16 more. A drawer opens over 350 ms OutCubic; the menu fades in and out over
	// 200 ms. For now it holds the date and the Wi-Fi drawer, whose Turn off / Turn on WiFi
	// switches Luna DE's simulated network (the Wi-Fi tile's switch). Turned off, the menu goes
	// 300 ms later; turned on, a second later (WiFiElement's wait for the connection).
	var sys = { open: false, wifiOpen: false, closer: null, online: true, covered: false };

	function sysDraw() {
		var box = $("sysmenu").querySelector(".content");
		box.innerHTML = "";
		function row(cls, text, tap, last) {
			var r = document.createElement("div");
			r.className = "row " + cls + (last ? " last" : "");
			var t = document.createElement("span"); t.className = "text"; t.textContent = text; r.appendChild(t);
			if (tap) { r.classList.add("selectable"); r.addEventListener("click", tap); }
			return r;
		}
		function divider() { var d = document.createElement("div"); d.className = "divider"; return d; }
		// The date: DateFormat's full date, in grey.
		box.appendChild(row("date", new Date().toLocaleDateString(undefined, { weekday: "long", year: "numeric", month: "long", day: "numeric" })));
		box.appendChild(divider());
		// Wi-Fi's header: the title, and its state on the right.
		var head = row("header", "Wi-Fi", function () { sys.wifiOpen = !sys.wifiOpen; drawer.classList.toggle("open", sys.wifiOpen); drawer.style.height = (sys.wifiOpen ? drawer.scrollHeight : 0) + "px"; head.classList.toggle("last", !sys.wifiOpen); }, !sys.wifiOpen);
		var state = document.createElement("span"); state.className = "state"; state.textContent = sys.online ? "ON" : "OFF"; head.appendChild(state);
		box.appendChild(head);
		var drawer = document.createElement("div"); drawer.className = "drawer" + (sys.wifiOpen ? " open" : "");
		drawer.appendChild(divider());
		// Turn off WiFi / Turn on WiFi, as WiFiElement had it, and under it, where a device listed
		// its networks, a grey "Simulated connectivity" that can't be tapped: the switch is Luna
		// DE's simulated network, and the machine's own is left alone (codepoet).
		drawer.appendChild(row("sub", sys.online ? "Turn off WiFi" : "Turn on WiFi", function () {
			var on = !sys.online;
			window.luna.setNetwork(on);
			sysCloseIn(on ? 1000 : 300);
		}));
		drawer.appendChild(divider());
		drawer.appendChild(row("sub info", "Simulated connectivity", null, true));
		box.appendChild(drawer);
		drawer.style.height = sys.wifiOpen ? "auto" : "0px";
		if (sys.wifiOpen) { drawer.style.height = drawer.scrollHeight + "px"; }
	}

	function sysOpen() {
		if (sys.open) { sysClose(); return; }
		$("menu").hidden = true;
		if (dashOpen) { dashOpen = false; placePanels(); }
		sys.open = true; sys.wifiOpen = false;
		clearTimeout(sys.closer);
		sysDraw();
		var el = $("sysmenu");
		el.hidden = false; $("sysmenu-scrim").hidden = false;
		el.getBoundingClientRect();
		document.body.classList.add("sysmenu");
		// Over a card that is up, its live view steps aside for the menu, its picture in its place.
		var c = maximized ? cur() : null;
		if (c && c.id && !c.splash) {
			window.luna.snapshot(c.id).then(function (url) {
				if (!sys.open) { return; }
				if (url) { c.img.src = url; }
				sys.covered = true; window.luna.cover(true);
			});
		}
	}

	function sysClose() {
		if (!sys.open) { return; }
		sys.open = false;
		clearTimeout(sys.closer);
		document.body.classList.remove("sysmenu");
		$("sysmenu-scrim").hidden = true;
		if (sys.covered) { sys.covered = false; window.luna.cover(false); }
		setTimeout(function () { if (!sys.open) { $("sysmenu").hidden = true; } }, 200);
	}

	/**
	 * The on-screen keyboard's balloon reaches above the keyboard, over the card that is up: its
	 * live view steps aside while the balloon is there, its picture (the page above a keyboard
	 * [kbHeight] tall) in its place, as for the system menu.
	 */
	var kbCover = null;
	window.__lunaCover = function (on, kbHeight) {
		var c = maximized ? cur() : null;
		if (!on) { if (kbCover) { var k = kbCover; kbCover = null; k.img.style.height = ""; window.luna.cover(false); } return; }
		if (!c || !c.id || kbCover) { return; }
		kbCover = c;
		window.luna.snapshot(c.id).then(function (url) {
			if (kbCover !== c) { return; }
			if (url) { c.img.src = url; c.img.style.height = (AREA - (kbHeight || 0)) + "px"; }
			window.luna.cover(true);
		});
	};

	/** SystemMenu.qml's closeMenuTimer. */
	function sysCloseIn(ms) { clearTimeout(sys.closer); sys.closer = setTimeout(sysClose, ms); }

	$("system").addEventListener("click", sysOpen);
	$("sysmenu-scrim").addEventListener("pointerdown", function (e) { e.preventDefault(); sysClose(); });

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
	window.luna.onCardLoading(function (d) { launching({ id: d.appId, title: d.title }, d.launcher); });
	window.luna.onCardWithdrawn(function (d) {
		allCards().filter(function (c) { return !c.id && c.appId === d.appId; }).forEach(function (c) { clearTimeout(c.waiting); removeCard(c); });
	});
	window.luna.onCardReady(ready);
	window.luna.onCardRemoved(removed);
	window.luna.onShow(function (d) { var c = cardById(d.id); if (c) { maximize(c); } });
	window.luna.onToggleCard(toggleCard);
	window.luna.onLauncher(function () { if ($("launcher").hidden) { openLauncher(); } else { closeLauncher(); } });
	window.luna.onScreen(function (d) {
		applyScreen(d);
		relayout();
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
	window.luna.onNetwork(function (d) {
		sys.online = !!d.online;
		document.querySelector("#status-right .wifi").classList.toggle("off", !d.online);
		// The menu's Wi-Fi row says what it does now, unless the menu is on its way out.
		if (sys.open && !sys.closer) { sysDraw(); }
		else if (sys.open) {
			var st = document.querySelector("#sysmenu .header .state"); if (st) { st.textContent = sys.online ? "ON" : "OFF"; }
		}
	});

	/** The launcher's pages and the quick-launch bar, as the host has them: what is installed, arranged as its owner left it. */
	function loadApps(first) {
		return window.luna.shell().then(function (s) {
			pages = s.pages;
			dock = s.dock;
			if (s.online !== undefined) { sys.online = !!s.online; document.querySelector("#status-right .wifi").classList.toggle("off", !s.online); }
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
