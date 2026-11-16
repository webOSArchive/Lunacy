// Luna DE's Electron main process (Docs/luna-de.md). It starts the JVM host (LunaCore on the
// desktop), routes the app origins' requests to it, and runs the device: one window, a TouchPad's
// screen, with the shell page drawn in it and each app window a card.
//
// A card is a native view (WebContentsView) over the shell page, so that a page's window.open
// makes a real window with its opener, as Android's onCreateWindow does: Enyo's window manager
// talks to its cards through those objects, and apps like Email and Calendar start headless and
// open every card that way. The shell draws everything else, and in the card view shows each
// card as a snapshot (a native view can't be scaled as Lunacy's live cards are; recorded in
// Docs/luna-de.md). Which cards exist is decided here for now; AppLifecycle's move into LunaCore
// takes that over.
"use strict";
const { app, BaseWindow, BrowserWindow, WebContentsView, dialog, ipcMain, net, session } = require("electron");
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const readline = require("readline");

const REPO = path.resolve(__dirname, "..", "..");
const HOST_SUFFIX = ".media.cryptofs.apps";
const IS_WIN = process.platform === "win32";
// The TouchPad's screen, the only device for now (codepoet, 2026-11-15): 1024 x 768 held in
// landscape ("right", "left"), 768 x 1024 in portrait ("up", "down"). The card area is below its
// 28 px status bar (StatusBar.HEIGHT).
const STATUS = 28, BEZEL = 44;
let orientation = "right";
const portrait = () => orientation === "up" || orientation === "down";
const screenSize = () => (portrait() ? { width: 768, height: 1024 } : { width: 1024, height: 768 });
const cardRect = () => { const s = screenSize(); return { x: 0, y: STATUS, width: s.width, height: s.height - STATUS }; };

let host = null;          // "http://127.0.0.1:<port>"
let hostProcess = null;
let shell = null;         // the device's window
let nextCard = 1;
/** Card id to { id, appId, title, view, popped }. */
const cards = new Map();
/** App id to its headless page (a noWindow app's), which isn't a card. */
const headless = new Map();
/**
 * The windows that aren't cards, by id: { id, appId, kind, view, height }. A "dashboard" lives in
 * the status bar's drop-down and a "popupalert" under it, each a live view the shell places
 * (lunade:place); any other kind the shell doesn't draw (a dock-mode window) stays unseen.
 */
const panels = new Map();
/** What a page said of the window it is about to open (PalmSystem's nextWindow), by its webContents' id. */
const pendingAttributes = new Map();
/** What the host is to be told of each page once its id is known: its type and its focus. */
const told = new Map();
let shown = null;         // the card on the screen, or null in the card view

function dataDir() {
	return process.env.LUNADE_DATA || path.join(app.getPath("userData"), "data");
}

/** Starts the JVM host and resolves with its address once it listens. */
function startHost() {
	const bin = path.join(__dirname, "..", "host", "build", "install", "lunade-host", "bin", IS_WIN ? "lunade-host.bat" : "lunade-host");
	if (!fs.existsSync(bin)) {
		throw new Error("No host at " + bin + ": run ./gradlew :DesktopLuna:host:installDist first");
	}
	return new Promise((resolve, reject) => {
		hostProcess = spawn(bin, ["--repo", REPO, "--data", dataDir()], { stdio: ["ignore", "pipe", "inherit"] });
		hostProcess.on("exit", (code) => { if (!host) { reject(new Error("host exited " + code)); } else { app.quit(); } });
		readline.createInterface({ input: hostProcess.stdout }).on("line", (line) => {
			const m = /^LUNADE-HOST (\d+)$/.exec(line);
			if (m && !host) { host = "http://127.0.0.1:" + m[1]; resolve(host); }
			else { console.log("[host] " + line); }
		});
	});
}

/**
 * Every https request in the session comes here. An app origin's goes to the host; anything
 * else goes out to the network untouched (the review: only Lunacy's origins are intercepted).
 * The app is named by the request's own origin, never by what the page says (rule 10).
 */
function routeAppOrigins() {
	session.defaultSession.protocol.handle("https", async (req) => {
		const u = new URL(req.url);
		if (!u.hostname.endsWith(HOST_SUFFIX)) {
			return net.fetch(req, { bypassCustomProtocolHandlers: true });
		}
		const appId = u.hostname.slice(0, -HOST_SUFFIX.length);
		if (u.pathname.startsWith("/__lunade/")) {
			const what = u.pathname.slice("/__lunade/".length);
			const q = new URLSearchParams(u.search);
			q.set("app", appId);
			const init = { method: req.method };
			if (req.method === "POST") { init.body = await req.arrayBuffer(); }
			return net.fetch(host + "/" + what + "?" + q.toString(), init);
		}
		return net.fetch(host + "/serve?u=" + encodeURIComponent(req.url));
	});
}

/** What every app page runs with: Lunacy's page interface (app-preload.js) in the page's own world. */
function pagePrefs() {
	// http:// images and media in an app's https page load, as on the TouchPad, where a page's
	// file:// origin never blocked them (Android: LocalNet and the WebView's mixed-content mode).
	return { preload: path.join(__dirname, "app-preload.js"), contextIsolation: false, sandbox: false, nodeIntegration: false,
		allowRunningInsecureContent: true };
}

function appIdOf(url) {
	try { const h = new URL(url).hostname; return h.endsWith(HOST_SUFFIX) ? h.slice(0, -HOST_SUFFIX.length) : null; } catch (e) { return null; }
}

/**
 * Every page's window.open becomes a card of its app, the new page keeping its opener (Electron's
 * createWindow: the view is made around the webContents Chromium already made for the open).
 */
function adoptOpens(wc) {
	wc.setWindowOpenHandler((d) => {
		if (!appIdOf(d.url) && d.url !== "about:blank") { return { action: "deny" }; }
		return {
			action: "allow",
			createWindow: (options) => {
				const view = new WebContentsView({ webContents: options.webContents, webPreferences: pagePrefs() });
				const appId = appIdOf(wc.getURL()) || appIdOf(d.url) || "";
				// The window's kind is what the page said it was opening (window.open's "window"
				// attribute, WindowType on Android); a kind the shell doesn't know is a card.
				const attrs = (pendingAttributes.get(wc.id) || []).shift() || {};
				const kind = String(attrs.window || "card").toLowerCase();
				if (kind === "dashboard" || kind === "popupalert" || kind === "dockmode") { addPanel(view, appId, kind, attrs); }
				else { addCard(view, appId, d.frameName); }
				return view.webContents;
			},
			overrideBrowserWindowOptions: { webPreferences: pagePrefs() },
		};
	});
}

/** A new card: its view in the device's window, hidden until the shell brings it up. */
function addCard(view, appId, title) {
	const id = nextCard++;
	const c = { id, appId, title: title || appId, view, popped: null };
	cards.set(id, c);
	view.setBackgroundColor("#000000");
	view.setBounds(cardRect());
	view.setVisible(false);
	shell.contentView.addChildView(view);
	adoptOpens(view.webContents);
	tell(view.webContents, "type", "card");
	view.webContents.on("page-title-updated", (e, t) => { c.title = t; send("lunade:card-title", { id, title: t }); });
	// The card waits in the card view with its splash while its page loads, then comes up
	// (CLAUDE.md, rule 0). Ready is the page's first paint with something in it; a page that never
	// says so is taken as ready when it has loaded.
	c.ready = () => { if (!c.saidReady) { c.saidReady = true; send("lunade:card-ready", { id }); } };
	view.webContents.once("did-finish-load", c.ready);
	view.webContents.once("did-fail-load", c.ready);
	// window.close() from the page, or the page gone: the card goes.
	view.webContents.on("destroyed", () => removeCard(id, false));
	send("lunade:card-added", { id, appId, title: c.title });
	return c;
}

/**
 * A dashboard, a popup alert or a dock-mode window: a live view the shell places, hidden until
 * it does. A dashboard hears it has no focus as soon as it is there, as on a TouchPad.
 */
function addPanel(view, appId, kind, attrs) {
	const id = nextCard++;
	const p = { id, appId, kind, view, height: Number(attrs.height) || (kind === "dashboard" ? 52 : 100) };
	panels.set(id, p);
	view.setBackgroundColor("#00000000");
	view.setBounds({ x: 0, y: STATUS, width: 320, height: p.height });
	view.setVisible(false);
	shell.contentView.addChildView(view);
	adoptOpens(view.webContents);
	tell(view.webContents, "type", kind === "dockmode" ? "dockMode" : kind);
	if (kind === "dashboard") { tell(view.webContents, "focus", "false"); }
	view.webContents.on("destroyed", () => removePanel(id, false));
	send("lunade:panel-added", { id, appId, kind, height: p.height, icon: host + "/icon?id=" + encodeURIComponent(appId) });
}

function removePanel(id, close) {
	const p = panels.get(id);
	if (!p) { return; }
	panels.delete(id);
	try { if (shell) { shell.contentView.removeChildView(p.view); } } catch (e) {}
	if (close && !p.view.webContents.isDestroyed()) { p.view.webContents.close(); }
	send("lunade:panel-removed", { id });
	endIfLast(p.appId);
}

/** An app whose last window has gone ends, its headless page with it (AppLifecycle.endApp). */
function endIfLast(appId) {
	if ([...cards.values()].some((k) => k.appId === appId) || [...panels.values()].some((k) => k.appId === appId)) { return; }
	const h = headless.get(appId);
	if (h) { headless.delete(appId); if (!h.webContents.isDestroyed()) { h.webContents.close(); } }
}

/** Tells the host something of a page: now if its id is known, and again for each page the view loads. */
function tell(wc, what, value) {
	const t = told.get(wc.id) || {};
	t[what] = value;
	told.set(wc.id, t);
	const page = pageIds.get(wc.id);
	if (page) { postTell(page, what, value); }
}
function pageGone(page) {
	if (host) { net.fetch(host + "/gone?w=" + encodeURIComponent(page), { method: "POST" }).catch(() => {}); }
}
function postTell(page, what, value) {
	const q = new URLSearchParams({ w: page, do: what, v: value });
	net.fetch(host + "/window?" + q.toString(), { method: "POST" }).catch(() => {});
}

/** Which card has the focus: the one on the screen, or none in the card view. */
function focusCards() {
	for (const c of cards.values()) { tell(c.view.webContents, "active", String(c.id === shown)); }
}

function removeCard(id, close) {
	const c = cards.get(id);
	if (!c) { return; }
	cards.delete(id);
	if (shown === id) { shown = null; }
	try { if (!c.popped && shell) { shell.contentView.removeChildView(c.view); } } catch (e) {}
	if (close && !c.view.webContents.isDestroyed()) { c.view.webContents.close(); }
	if (c.popped && !c.popped.isDestroyed()) { c.popped.close(); }
	send("lunade:card-removed", { id });
	endIfLast(c.appId);
}

/**
 * An app the host says to start (DesktopShell's "launch" event): its page, as a card or headless.
 * Whether it was running already is the host's to say; it relaunches a running app itself.
 */
function launch(entry) {
	if (entry.noWindow) {
		if (headless.has(entry.id)) { return; }
		// The headless page opens its own cards (window.open); it is never on the screen.
		const view = new WebContentsView({ webPreferences: pagePrefs() });
		headless.set(entry.id, view);
		adoptOpens(view.webContents);
		view.webContents.on("destroyed", () => headless.delete(entry.id));
		view.webContents.loadURL(entry.url);
		return;
	}
	const view = new WebContentsView({ webPreferences: pagePrefs() });
	addCard(view, entry.id, entry.title);
	view.webContents.loadURL(entry.url);
}

function send(channel, data) { if (shell && !shell.isDestroyed()) { shell.webContents.send(channel, data); } }

/** The device: the shell page, with the cards' views over its card area. */
function openShell() {
	const size = screenSize();
	shell = new BrowserWindow({
		width: size.width, height: size.height + BEZEL, useContentSize: true, resizable: false, title: "Luna DE",
		backgroundColor: "#000000",
		webPreferences: { preload: path.join(__dirname, "shell-preload.js"), contextIsolation: true, sandbox: true },
	});
	shell.removeMenu();
	shell.loadFile(path.join(__dirname, "shell.html"));
	shell.on("closed", () => { shell = null; app.quit(); });
}

/**
 * A card popped out of the device: its live view moved into a window of its own, at the
 * TouchPad's size (codepoet, 2026-11-15: a developer's tool; first light's way of running an app).
 */
function popOut(id) {
	const c = cards.get(id);
	if (!c || c.popped) { return; }
	shell.contentView.removeChildView(c.view);
	if (shown === id) { shown = null; }
	// It starts as the card was, at the device's size and the way it is held, and from there is a
	// window like any other: resized freely, its page following (the page hears each change as a
	// resize, and a change between wide and tall as the device turning, for that window alone).
	const r = cardRect();
	const win = new BaseWindow({ width: r.width, height: r.height, useContentSize: true, title: c.title, backgroundColor: "#000000",
		resizable: true, minWidth: 320, minHeight: 240 });
	win.contentView.addChildView(c.view);
	c.view.setBounds({ x: 0, y: 0, width: r.width, height: r.height });
	let tall = r.height > r.width;
	win.on("resize", async () => {
		const [w, h] = win.getContentSize();
		if ((h > w) !== tall) {
			tall = h > w;
			// The way it is held first, then the size (orient), then the host's word of it.
			await orient(c.view.webContents, tall ? "up" : "right");
			tell(c.view.webContents, "orientation", tall ? "up" : "right");
		}
		const [w2, h2] = win.getContentSize();
		c.view.setBounds({ x: 0, y: 0, width: w2, height: h2 });
	});
	c.view.setVisible(true);
	c.popped = win;
	win.on("closed", () => removeCard(id, true));
	send("lunade:card-removed", { id });
}

ipcMain.handle("lunade:shell", async () => {
	const s = await (await net.fetch(host + "/shell")).json();
	s.pages.forEach((p) => p.apps.forEach(withIcon));
	s.dock.forEach(withIcon);
	return s;
});
// The shell asks for an app: the host decides (started, or relaunched and brought forward), as
// it does for applicationManager/launch from an app.
ipcMain.handle("lunade:launch", (e, entry) => { askLaunch(entry.id, entry.params); return true; });
// The shell brings a card up: its live view on the screen, every other hidden.
ipcMain.handle("lunade:maximize", (e, id) => {
	for (const c of cards.values()) { if (!c.popped) { c.view.setVisible(c.id === id); } }
	shown = cards.has(id) ? id : null;
	if (shown) { cards.get(id).view.webContents.focus(); }
	focusCards();
	return true;
});
// The card view: the live views hidden, the shell showing their snapshots.
ipcMain.handle("lunade:cardview", () => {
	for (const c of cards.values()) { if (!c.popped) { c.view.setVisible(false); } }
	shown = null;
	shell.webContents.focus();
	focusCards();
	return true;
});
// The shell places the dashboards and alerts it is showing: [{ id, x, y, width, height }], every
// other one hidden. A dashboard hears its focus each time, as the drop-down opens and closes.
ipcMain.handle("lunade:place", (e, rects, dashboardsOpen) => {
	const byId = new Map(rects.map((r) => [r.id, r]));
	for (const p of panels.values()) {
		const r = byId.get(p.id);
		if (r) { p.view.setBounds({ x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }); }
		p.view.setVisible(!!r);
		if (p.kind === "dashboard") { tell(p.view.webContents, "focus", String(!!dashboardsOpen)); }
	}
	// The drop-down and an alert draw over the card: they are added last, so they are on top.
	return true;
});
// The status bar's title: the app menu of the card on the screen, opened as LunaSysMgr did, by
// relaunching its app with {"palm-command":"open-app-menu"} (ShellChrome.toggleAppMenu).
ipcMain.handle("lunade:appmenu", () => {
	const c = shown && cards.get(shown);
	if (c) { askLaunch(c.appId, { "palm-command": "open-app-menu" }); }
	return true;
});
// The bezel's rotate button: wide to tall and back (F6 to F9 pick each of the four ways up).
ipcMain.handle("lunade:rotate", () => { turn(portrait() ? "right" : "up"); return true; });
ipcMain.handle("lunade:back", () => { const c = shown && cards.get(shown); if (c) { tell(c.view.webContents, "back", String(Date.now())); } return true; });
ipcMain.on("lunade:next-window", (e, json) => {
	let attrs = {};
	try { attrs = JSON.parse(json) || {}; } catch (err) {}
	const list = pendingAttributes.get(e.sender.id) || [];
	list.push(attrs);
	pendingAttributes.set(e.sender.id, list);
	e.returnValue = true;
});
ipcMain.handle("lunade:snapshot", async (e, id) => {
	const c = cards.get(id);
	if (!c || c.view.webContents.isDestroyed()) { return null; }
	try { return (await c.view.webContents.capturePage()).toDataURL(); } catch (err) { return null; }
});
ipcMain.handle("lunade:close", (e, id) => { if (panels.has(id)) { removePanel(id, true); } else { removeCard(id, true); } return true; });
ipcMain.handle("lunade:popout", (e, id) => { popOut(id); return true; });
ipcMain.handle("lunade:devtools", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.openDevTools({ mode: "detach" }); } return true; });
ipcMain.handle("lunade:reload", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.reload(); } return true; });

/** A package (.ipk) or an app's folder handed to Luna DE: the host installs it and says how it went in a banner. */
function install(file) {
	if (!file) { return; }
	net.fetch(host + "/install?" + new URLSearchParams({ path: file }).toString(), { method: "POST" })
		.catch((err) => console.error("Luna DE: install " + file + ": " + err));
}
ipcMain.handle("lunade:install", (e, files) => { (files || []).forEach(install); return true; });
ipcMain.handle("lunade:remove", (e, id) => {
	net.fetch(host + "/remove?" + new URLSearchParams({ id }).toString(), { method: "POST" }).catch(() => {});
	return true;
});

/** The file chooser, for a package (Ctrl/Cmd+O) or an app's folder (with Shift). */
async function chooseInstall(folder) {
	const r = await dialog.showOpenDialog(shell, folder
		? { title: "Install an app from its folder", properties: ["openDirectory"] }
		: { title: "Install a webOS package", properties: ["openFile", "multiSelections"], filters: [{ name: "webOS packages", extensions: ["ipk"] }] });
	if (!r.canceled) { r.filePaths.forEach(install); }
}

function askLaunch(id, params) {
	const q = new URLSearchParams({ id });
	if (params) { q.set("params", JSON.stringify(params)); }
	net.fetch(host + "/launch?" + q.toString(), { method: "POST" }).catch((err) => console.error("Luna DE: launch " + id + ": " + err));
}

/** Each page's id, as its preload told the host (app-preload.js), by its webContents' id. */
const pageIds = new Map();
ipcMain.on("lunade:page", (e, page) => {
	// The view's last page has gone (a reload, a navigation): the host forgets it.
	const before = pageIds.get(e.sender.id);
	if (before && before !== page) { pageGone(before); }
	pageIds.set(e.sender.id, page);
	// What this view's page is (its type, its focus) goes to each page it loads.
	const t = told.get(e.sender.id) || {};
	for (const what of ["type", "active", "focus", "orientation"]) { if (t[what] !== undefined) { postTell(page, what, t[what]); } }
});

/** What the host's shell asks of the screen (DesktopShell.take), for as long as Luna DE runs. */
async function followHost() {
	for (;;) {
		let events = [];
		try { events = await (await net.fetch(host + "/events")).json(); }
		catch (err) { if (!hostProcess || hostProcess.exitCode !== null) { return; } await new Promise((r) => setTimeout(r, 1000)); }
		for (const ev of events) {
			try {
				if (ev.type === "launch") { launch(ev); }
				else if (ev.type === "show") {
					// The app's first card comes forward (a relaunch it didn't handle).
					const c = [...cards.values()].find((k) => k.appId === ev.id && !k.popped);
					if (c) { send("lunade:show", { id: c.id }); }
				} else if (ev.type === "close") {
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c) { removeCard(c.id, true); }
					else {
						const h = [...headless.values()].find((v) => pageIds.get(v.webContents.id) === ev.page);
						if (h && !h.webContents.isDestroyed()) { h.webContents.close(); }
					}
				} else if (ev.type === "apps") { send("lunade:apps", {}); }
				else if (ev.type === "banner") {
					send("lunade:banner", { id: ev.id, appId: ev.appId, message: ev.message, params: ev.params,
						icon: ev.icon || (ev.appId ? host + "/icon?id=" + encodeURIComponent(ev.appId) : "") });
				} else if (ev.type === "banner-remove") { send("lunade:banner-remove", { id: ev.id }); }
				else if (ev.type === "banner-clear") { send("lunade:banner-remove", { appId: ev.appId }); }
				else if (ev.type === "network") { send("lunade:network", { online: ev.online }); }
								else if (ev.type === "ready") {
					// The app's own word that its stage is ready (PalmSystem.stageReady), as on Android.
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c && c.ready) { c.ready(); }
				} else if (ev.type === "activate") {
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c) { send("lunade:show", { id: c.id }); }
				}
			} catch (err) { console.error("Luna DE: " + ev.type + ": " + err); }
		}
	}
}

/** An entry's icon as a URL the shell page can load. */
function withIcon(a) { return Object.assign(a, { icon: host + a.icon, splash: host + a.splash }); }

/**
 * PalmSystem.screenOrientation set in a page, before its view takes its new shape: Enyo 1 reads
 * the property when the page's own resize event arrives (enyo.sendOrientationChange), so it has
 * to be there first, as on Android (AppWindow.setScreenOrientation). The host then calls
 * Mojo.screenOrientationChanged, which can come after.
 */
function orient(wc, to) {
	if (wc.isDestroyed()) { return Promise.resolve(); }
	return wc.executeJavaScript("if(window.PalmSystem){PalmSystem.screenOrientation=" + JSON.stringify(to) + "}", false).catch(() => {});
}

/** The device turned: every page is told how it is held, then the window and the cards take the screen's new shape. */
async function turn(to) {
	if (to === orientation || !shell) { return; }
	orientation = to;
	const here = [...cards.values()].filter((c) => !c.popped);
	await Promise.all(here.map((c) => orient(c.view.webContents, to)));
	const size = screenSize();
	shell.setResizable(true);
	shell.setContentSize(size.width, size.height + BEZEL);
	shell.setResizable(false);
	for (const c of here) { c.view.setBounds(cardRect()); }
	send("lunade:screen", { width: size.width, height: size.height, orientation });
	net.fetch(host + "/orientation?v=" + to, { method: "POST" }).catch(() => {});
}

// Keys from any page, a card's included. The old SDK emulator's wherever they fit (codepoet,
// 2026-11-15; Docs/luna-de.md): End opens and closes the launcher, Home minimises the card and
// maximises it again, Esc is the back gesture, F6 to F9 turn the device up, down, right and left.
// Luna DE's own: F12 for the DevTools of whatever has the keyboard, Ctrl/Cmd+R to reload it, and
// F1 as a spare home button.
const TURN = { F6: "up", F7: "down", F8: "right", F9: "left" };
/** The user agent every app page gives: the device's own, which webOS apps look for (DeviceProfile.userAgent, from the host). */
let deviceUserAgent = "";
app.on("web-contents-created", (e, wc) => {
	// Before anything loads in it; the shell's own page keeps Electron's.
	if (deviceUserAgent && !(shell && wc === shell.webContents)) { wc.setUserAgent(deviceUserAgent); }
	// A page that has gone is no longer one of its app's windows: the host is told here, by the
	// main process, which always knows. (The page's own word, a beacon as it unloads, doesn't
	// get out of a view that is being destroyed; the host then took the next launch of the app
	// for a relaunch of a window that wasn't there, and no card came.)
	const id = wc.id;
	wc.on("destroyed", () => {
		const page = pageIds.get(id);
		if (page) { pageGone(page); }
		pageIds.delete(id); told.delete(id); pendingAttributes.delete(id);
	});
	// A file dropped on an app's card would have the card load it; it is Luna DE's to install.
	wc.on("will-navigate", (ev, url) => {
		if (!url.startsWith("file:") || (shell && wc === shell.webContents)) { return; }
		ev.preventDefault();
		try { install(require("url").fileURLToPath(url)); } catch (err) {}
	});
	wc.on("before-input-event", (ev, input) => {
		if (input.type !== "keyDown") { return; }
		const isShell = shell && wc === shell.webContents;
		if ((input.control || input.meta) && input.key.toLowerCase() === "o") { ev.preventDefault(); chooseInstall(input.shift); return; }
		if (input.key === "F1" && shell) { ev.preventDefault(); send("lunade:home"); }
		else if (input.key === "Home" && shell && !input.shift && !input.control && !isEditing(input)) { ev.preventDefault(); send("lunade:toggle-card"); }
		else if (input.key === "End" && shell && !input.shift && !input.control && !isEditing(input)) { ev.preventDefault(); send("lunade:launcher"); }
		else if (TURN[input.key]) { ev.preventDefault(); turn(TURN[input.key]); }
		else if (input.key === "Escape" && isShell && shown) { ev.preventDefault(); tell(cards.get(shown).view.webContents, "back", String(Date.now())); }
		else if (input.key === "F12" || (input.control && input.shift && input.key.toLowerCase() === "i")) { ev.preventDefault(); wc.toggleDevTools(); }
		else if ((input.control || input.meta) && input.key.toLowerCase() === "r" && !isShell) { ev.preventDefault(); wc.reload(); }
	});
});
/** Home and End move the caret in a text field; with Alt held they stay the field's (the keys are the emulator's otherwise). */
function isEditing(input) { return input.alt; }

app.whenReady().then(async () => {
	try {
		await startHost();
	} catch (e) {
		console.error("Luna DE: " + e.message);
		app.exit(1);
		return;
	}
	routeAppOrigins();
	try { deviceUserAgent = (await (await net.fetch(host + "/shell")).json()).userAgent || ""; } catch (e) {}
	openShell();
	followHost();
	// LUNADE_OPEN=<app id>[,<app id>...] launches those apps once the shell is ready.
	ipcMain.once("lunade:ready", () => {
		// LUNADE_INSTALL=<package or app folder>[<path separator><another>...] installs those first.
		(process.env.LUNADE_INSTALL || "").split(path.delimiter).filter(Boolean).forEach((f) => install(path.resolve(f)));
		if (!process.env.LUNADE_OPEN) { return; }
		for (const id of process.env.LUNADE_OPEN.split(",")) { askLaunch(id); }
	});
});

app.on("window-all-closed", () => app.quit());
app.on("quit", () => { if (hostProcess) { hostProcess.kill(); } });
