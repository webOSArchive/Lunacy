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
const { app, BaseWindow, BrowserWindow, Menu, WebContentsView, dialog, ipcMain, net, session } = require("electron");
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const readline = require("readline");

// The Lunacy tree the host reads its assets from: the checkout this runs in, or, packaged
// (DesktopLuna/package.js), the app's resources folder, which holds the same folders at the same
// places, so the host's --repo and the shell's ../../AndroidLuna pictures hold in both.
// LUNA_DE_REPO names another tree for the host's assets (a test of assets made elsewhere).
const REPO = app.isPackaged ? process.resourcesPath : (process.env.LUNA_DE_REPO || path.resolve(__dirname, "..", ".."));

const HOST_SUFFIX = ".media.cryptofs.apps";
const IS_WIN = process.platform === "win32";
// The TouchPad's screen, the only device for now (codepoet, 2026-11-15): 1024 x 768 held in
// landscape ("right", "left"), 768 x 1024 in portrait ("up", "down"). The card area is below its
// 28 px status bar (StatusBar.HEIGHT).
//
// Or a Pre3's (HP webOS 2.2.4): 480 x 800, named tall, and drawn at its ScaleFactor of 1.5 (its
// luna-platform.conf, read off the device 2026-11-16), so a page lays out 320 wide and the
// status bar's 28 px are the screen's 42. The device is the host's to say (its /shell); the
// window, its zoom and every measure here follow it. Sizes here are the screen's own px.
const BEZEL = 44;
let device = { id: "touchpad", name: "HP TouchPad", width: 1024, height: 768, scale: 1, virtualKeyboard: true };
let orientation = "right";
const portrait = () => orientation === "up" || orientation === "down";
const STATUS_CSS = 28;
const statusPx = () => Math.round(STATUS_CSS * device.scale);
const screenSize = () => {
	const long = Math.max(device.width, device.height), short = Math.min(device.width, device.height);
	return portrait() ? { width: short, height: long } : { width: long, height: short };
};
const cardRect = () => { const s = screenSize(); return { x: 0, y: statusPx(), width: s.width, height: s.height - statusPx() }; };
/** What the shell page is told of the screen: in its own px, which are the device's over its scale. */
const screenForShell = () => {
	const s = screenSize();
	return { width: Math.round(s.width / device.scale), height: Math.round(s.height / device.scale), orientation, device: device.id,
		bezel: BEZEL / device.scale, virtualKeyboard: device.virtualKeyboard };
};

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
 * (luna-de:place); any other kind the shell doesn't draw (a dock-mode window) stays unseen.
 */
const panels = new Map();
/** What a page said of the window it is about to open (PalmSystem's nextWindow), by its webContents' id. */
const pendingAttributes = new Map();
/** What the host is to be told of each page once its id is known: its type and its focus. */
const told = new Map();
let shown = null;         // the card on the screen, or null in the card view

function dataDir() {
	return process.env.LUNA_DE_DATA || path.join(app.getPath("userData"), "data");
}

/** Starts the JVM host and resolves with its address once it listens. */
function startHost() {
	let bin, args = ["--repo", REPO, "--data", dataDir(), "--with-parent", "1"];
	if (app.isPackaged) {
		// Packaged: the Java runtime and the host's jars the package carries, started directly (no
		// launcher script, so nothing on the machine's PATH or in its JAVA_HOME is needed). SQLite's
		// native library sits beside the jars, out of its jar, where a Mac's signing can reach it.
		const hostDir = path.join(process.resourcesPath, "host");
		bin = path.join(process.resourcesPath, "jre", "bin", IS_WIN ? "java.exe" : "java");
		let native = [];
		try {
			const lib = fs.readdirSync(path.join(hostDir, "native"))[0];
			if (lib) { native = ["-Dorg.sqlite.lib.path=" + path.join(hostDir, "native"), "-Dorg.sqlite.lib.name=" + lib]; }
		} catch (e) {}
		args = [...native, "-cp", path.join(hostDir, "lib", "*"), "org.webosarchive.lunacy.desktop.MainKt", ...args];
	} else if (IS_WIN) {
		// From a checkout on Windows: the machine's Java on the built jars, not Gradle's .bat (a
		// command file takes a shell to start, which mangles a path with a space in it).
		const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java.exe") : "java.exe";
		const lib = path.join(__dirname, "..", "host", "build", "install", "luna-de-host", "lib");
		if (!fs.existsSync(lib)) { throw new Error("No host at " + lib + ": run gradlew :DesktopLuna:host:installDist first"); }
		bin = java;
		args = ["-cp", path.join(lib, "*"), "org.webosarchive.lunacy.desktop.MainKt", ...args];
	} else {
		bin = path.join(__dirname, "..", "host", "build", "install", "luna-de-host", "bin", "luna-de-host");
	}
	if (!fs.existsSync(bin) && path.isAbsolute(bin)) {
		throw new Error("No host at " + bin + (app.isPackaged ? ": this copy of Luna DE is incomplete" : ": run ./gradlew :DesktopLuna:host:installDist first"));
	}
	return new Promise((resolve, reject) => {
		hostProcess = spawn(bin, args, { stdio: ["pipe", "pipe", "inherit"] });
		hostProcess.on("exit", (code) => { if (!host) { reject(new Error("host exited " + code)); } else { app.quit(); } });
		readline.createInterface({ input: hostProcess.stdout }).on("line", (line) => {
			const m = /^LUNA-DE-HOST (\d+)$/.exec(line);
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
		if (u.pathname.startsWith("/__luna-de/")) {
			const what = u.pathname.slice("/__luna-de/".length);
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
	view.webContents.on("page-title-updated", (e, t) => { c.title = t; send("luna-de:card-title", { id, title: t }); });
	// The card waits in the card view with its splash while its page loads, then comes up
	// (CLAUDE.md, rule 0). Ready is the page's first paint with something in it; a page that never
	// says so is taken as ready when it has loaded.
	c.ready = () => { if (!c.saidReady) { c.saidReady = true; send("luna-de:card-ready", { id }); } };
	view.webContents.once("did-finish-load", c.ready);
	view.webContents.once("did-fail-load", c.ready);
	// window.close() from the page, or the page gone: the card goes.
	view.webContents.on("destroyed", () => removeCard(id, false));
	send("luna-de:card-added", { id, appId, title: c.title });
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
	view.setBounds({ x: 0, y: statusPx(), width: 320, height: p.height });
	view.setVisible(false);
	shell.contentView.addChildView(view);
	adoptOpens(view.webContents);
	tell(view.webContents, "type", kind === "dockmode" ? "dockMode" : kind);
	if (kind === "dashboard") { tell(view.webContents, "focus", "false"); }
	view.webContents.on("destroyed", () => removePanel(id, false));
	send("luna-de:panel-added", { id, appId, kind, height: p.height, icon: host + "/icon?id=" + encodeURIComponent(appId) });
}

function removePanel(id, close) {
	const p = panels.get(id);
	if (!p) { return; }
	panels.delete(id);
	try { if (shell) { shell.contentView.removeChildView(p.view); } } catch (e) {}
	if (close && !p.view.webContents.isDestroyed()) { p.view.webContents.close(); }
	send("luna-de:panel-removed", { id });
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
	applyKeyboard();
}

// ---- the virtual keyboard ----
//
// webOS's tablet keyboard, 340 px of the screen's foot (LunaKeyboard's art and LunaCE's keymap,
// drawn by the shell page). It is up while the card on the screen wants it (a field has the
// focus, or the app asked) and no real keyboard is there: a real one, as a Bluetooth keyboard
// did on a TouchPad, keeps it away. Luna DE starts with one, since a desktop has one; the
// bezel's keyboard button takes it away and brings it back, so an app can be seen both ways.
// What the app hears is what LunaSysMgr told it (ShellChrome.watchKeyboard on Android):
// Mojo.keyboardShown(true), then its window shrinks to the space above the keyboard, or, if it
// asked not to be resized, Mojo.positiveSpaceChanged with that space; and the reverse.
// Its four sizes are LunaCE's presets (TabletKeyboard: XS 243, S 291, M the art's 340, L 393),
// chosen as on webOS and kept in the same preference (the host's /keyboard-size).
const KEYBOARD_HEIGHTS = { "-2": 243, "-1": 291, "0": 340, "1": 393 };
let keyboardSize = 0;
let KEYBOARD = KEYBOARD_HEIGHTS[0];
let realKeyboard = true;
let keyboardCard = null;   // the card the keyboard is up for

function applyKeyboard() {
	const c = shown ? cards.get(shown) : null;
	// A Pre3 has no on-screen keyboard at all (VirtualKeyboardEnabled=false in its luna-platform.conf): its keys are its own.
	const want = !!(c && !c.popped && c.wantsKeyboard && !realKeyboard && device.virtualKeyboard);
	if ((want ? c : null) === keyboardCard) { return; }
	if (keyboardCard) { lowerKeyboard(keyboardCard, !want); }
	keyboardCard = want ? c : null;
	if (want) { raiseKeyboard(c); }
	send("luna-de:keyboard", { show: !!keyboardCard, height: KEYBOARD });
}

/** A lifecycle hook called the way LunaSysMgr did, a function on the page's Mojo object, here and now: before whatever follows it. */
function callMojo(wc, fn, args) {
	if (wc.isDestroyed()) { return Promise.resolve(); }
	return wc.executeJavaScript("(function(){try{if(window.Mojo&&Mojo." + fn + "){Mojo." + fn + "(" + (args || "") + ");}}catch(e){console.error('Mojo." + fn + ": '+e);}})()", false).catch(() => {});
}

async function raiseKeyboard(c) {
	const r = cardRect(), wc = c.view.webContents;
	c.shrunk = c.keyboardResizes !== false;
	await callMojo(wc, "keyboardShown", "true");
	if (keyboardCard !== c) { return; }
	if (c.shrunk) { c.view.setBounds({ x: r.x, y: r.y, width: r.width, height: r.height - KEYBOARD }); }
	else { callMojo(wc, "positiveSpaceChanged", Math.round(r.width / pageScale(wc)) + "," + Math.round((r.height - KEYBOARD) / pageScale(wc))); }
}

async function lowerKeyboard(c, blur) {
	if (cards.get(c.id) !== c || c.view.webContents.isDestroyed()) { return; }
	const r = cardRect(), wc = c.view.webContents;
	const wasShrunk = c.shrunk;
	c.shrunk = false;
	// Put away by the person (the hide key, a real keyboard arriving): the field's focus goes with it.
	const unfocus = blur && c.wantsKeyboard;
	if (unfocus) { c.wantsKeyboard = false; }
	if (wasShrunk) { if (!c.popped) { c.view.setBounds(r); } } else { await callMojo(wc, "positiveSpaceChanged", Math.round(r.width / pageScale(wc)) + "," + Math.round(r.height / pageScale(wc))); }
	await callMojo(wc, "keyboardShown", "false");
	if (unfocus && !wc.isDestroyed()) { wc.executeJavaScript("window.__lunacyRemoveInputFocus&&__lunacyRemoveInputFocus()", false).catch(() => {}); }
}

/** A key of the virtual keyboard, typed into the card it is up for. */
function typeKey(key) {
	const c = keyboardCard;
	if (!c || c.view.webContents.isDestroyed()) { return; }
	const wc = c.view.webContents;
	wc.focus();
	if (key.hide) { lowerKeyboard(c, true); keyboardCard = null; send("luna-de:keyboard", { show: false, height: KEYBOARD }); return; }
	const code = key.code || key.text;
	wc.sendInputEvent({ type: "keyDown", keyCode: code });
	if (key.text) { for (const ch of key.text) { wc.sendInputEvent({ type: "char", keyCode: ch }); } }
	else if (key.code === "Return") { wc.sendInputEvent({ type: "char", keyCode: "\r" }); }
	wc.sendInputEvent({ type: "keyUp", keyCode: code });
}

function removeCard(id, close) {
	const c = cards.get(id);
	if (!c) { return; }
	cards.delete(id);
	if (shown === id) { shown = null; }
	try { if (!c.popped && shell) { shell.contentView.removeChildView(c.view); } } catch (e) {}
	if (close && !c.view.webContents.isDestroyed()) { c.view.webContents.close(); }
	if (c.popped && !c.popped.isDestroyed()) { c.popped.close(); }
	send("luna-de:card-removed", { id });
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
		icon: path.join(__dirname, "images", "icon.png"),   // the taskbar's, on Linux and Windows; a Mac takes its bundle's
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
	send("luna-de:card-removed", { id });
}

ipcMain.handle("luna-de:shell", async () => {
	const s = await (await net.fetch(host + "/shell")).json();
	s.pages.forEach((p) => p.apps.forEach(withIcon));
	s.dock.forEach(withIcon);
	return s;
});
// The shell asks for an app: the host decides (started, or relaunched and brought forward), as
// it does for applicationManager/launch from an app.
ipcMain.handle("luna-de:launch", (e, entry) => { askLaunch(entry.id, entry.params); return true; });
// The shell brings a card up: its live view on the screen, every other hidden.
ipcMain.handle("luna-de:maximize", (e, id) => {
	for (const c of cards.values()) { if (!c.popped) { c.view.setVisible(c.id === id); } }
	shown = cards.has(id) ? id : null;
	if (shown) { cards.get(id).view.webContents.focus(); }
	focusCards();
	return true;
});
// The card view: the live views hidden, the shell showing their snapshots.
ipcMain.handle("luna-de:cardview", () => {
	for (const c of cards.values()) { if (!c.popped) { c.view.setVisible(false); } }
	shown = null;
	shell.webContents.focus();
	focusCards();
	return true;
});
// The shell places the dashboards and alerts it is showing: [{ id, x, y, width, height }], every
// other one hidden. A dashboard hears its focus each time, as the drop-down opens and closes.
ipcMain.handle("luna-de:place", (e, rects, dashboardsOpen) => {
	const byId = new Map(rects.map((r) => [r.id, r]));
	for (const p of panels.values()) {
		const r = byId.get(p.id);
		// The shell's px are the page's; the screen's are those times the device's scale.
		const k = device.scale;
		if (r) { p.view.setBounds({ x: Math.round(r.x * k), y: Math.round(r.y * k), width: Math.round(r.width * k), height: Math.round(r.height * k) }); }
		p.view.setVisible(!!r);
		if (p.kind === "dashboard") { tell(p.view.webContents, "focus", String(!!dashboardsOpen)); }
	}
	// The drop-down and an alert draw over the card: they are added last, so they are on top.
	return true;
});
// The status bar's title: the app menu of the card on the screen, opened as LunaSysMgr did, by
// relaunching its app with {"palm-command":"open-app-menu"} (ShellChrome.toggleAppMenu).
ipcMain.handle("luna-de:appmenu", () => {
	const c = shown && cards.get(shown);
	if (c) { askLaunch(c.appId, { "palm-command": "open-app-menu" }); }
	return true;
});
// The bezel's rotate button: wide to tall and back (F6 to F9 pick each of the four ways up).
ipcMain.handle("luna-de:rotate", () => { turn(portrait() ? "right" : "up"); return true; });
ipcMain.handle("luna-de:key", (e, key) => { typeKey(key || {}); return true; });
ipcMain.handle("luna-de:keyboard-size", async (e, size) => {
	if (size !== undefined && KEYBOARD_HEIGHTS[String(size)] && size !== keyboardSize) {
		// Down at the old size and up at the new, so the app hears the change as it would on a device.
		const c = keyboardCard;
		if (c) { await lowerKeyboard(c, false); keyboardCard = null; }
		keyboardSize = size; KEYBOARD = KEYBOARD_HEIGHTS[String(size)];
		net.fetch(host + "/keyboard-size?v=" + size, { method: "POST" }).catch(() => {});
		applyKeyboard();
	}
	return { size: keyboardSize, height: KEYBOARD };
});
// The bezel's keyboard button: a real keyboard there, or not.
ipcMain.handle("luna-de:real-keyboard", () => {
	realKeyboard = !realKeyboard;
	applyKeyboard();
	return realKeyboard;
});
ipcMain.handle("luna-de:back", () => { const c = shown && cards.get(shown); if (c) { tell(c.view.webContents, "back", String(Date.now())); } return true; });
ipcMain.on("luna-de:next-window", (e, json) => {
	let attrs = {};
	try { attrs = JSON.parse(json) || {}; } catch (err) {}
	const list = pendingAttributes.get(e.sender.id) || [];
	list.push(attrs);
	pendingAttributes.set(e.sender.id, list);
	e.returnValue = true;
});
ipcMain.handle("luna-de:snapshot", async (e, id) => {
	const c = cards.get(id);
	if (!c || c.view.webContents.isDestroyed()) { return null; }
	try { return (await c.view.webContents.capturePage()).toDataURL(); } catch (err) { return null; }
});
ipcMain.handle("luna-de:close", (e, id) => { if (panels.has(id)) { removePanel(id, true); } else { removeCard(id, true); } return true; });
ipcMain.handle("luna-de:popout", (e, id) => { popOut(id); return true; });
ipcMain.handle("luna-de:devtools", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.openDevTools({ mode: "detach" }); } return true; });

/**
 * The bezel's </> button opens the log; its right-click menu inspects a card: Electron's own
 * developer tools for that one page. (A menu of the system's own: one drawn in the shell page
 * would lie under a card that is up.)
 */
ipcMain.handle("luna-de:tools-menu", () => {
	const items = [...cards.values()].filter((c) => !c.view.webContents.isDestroyed()).map((c) => ({
		label: "Inspect " + (c.title || c.appId), click: () => c.view.webContents.openDevTools({ mode: "detach" }) }));
	if (!items.length) { items.push({ label: "No card is open to inspect", enabled: false }); }
	Menu.buildFromTemplate(items).popup({ window: shell });
	return true;
});

/**
 * The log: every app page's console (cards, dashboards, alerts and the pages with no window),
 * kept from the moment Luna DE starts, so the window can be opened after the fact and still
 * show an app's start. It stands in for palm-log -f until the SDK's tools reach Luna DE, and
 * holds back nothing: there is no system log level here to set.
 */
const LOG_MAX = 5000;
const logLines = [];
let logWindow = null;
const LEVELS = ["debug", "info", "warning", "error"];
function logLine(wc, level, message, line, source) {
	if ((shell && wc === shell.webContents) || (logWindow && !logWindow.isDestroyed() && wc === logWindow.webContents)) { return; }
	let appId = "";
	try { appId = appIdOf(wc.getURL()) || ""; } catch (e) {}
	if (!appId) { return; }   // a developer tools window, or anything else that isn't an app's
	if (String(source || "").startsWith("node:electron")) { return; }   // Electron's own advice to its developers, not the app's
	const now = new Date(), two = (n) => String(n).padStart(2, "0");
	let where = String(source || "");
	const cut = where.indexOf(HOST_SUFFIX);
	if (cut >= 0) { where = where.slice(where.indexOf("/", cut)); }
	const entry = { time: two(now.getHours()) + ":" + two(now.getMinutes()) + ":" + two(now.getSeconds()) + "." + String(now.getMilliseconds()).padStart(3, "0"),
		level: typeof level === "number" ? LEVELS[level] || "info" : (LEVELS.includes(level) ? level : "info"),
		app: appId, message: String(message), source: where ? where + ":" + line : "" };
	logLines.push(entry);
	if (logLines.length > LOG_MAX) { logLines.shift(); }
	if (logWindow && !logWindow.isDestroyed()) { logWindow.webContents.send("luna-de:log-line", entry); }
}
function showLog() {
	if (logWindow && !logWindow.isDestroyed()) { logWindow.show(); logWindow.focus(); return true; }
	logWindow = new BrowserWindow({ width: 900, height: 420, icon: path.join(__dirname, "images", "icon.png"), title: "Luna DE: logs", backgroundColor: "#16171a", autoHideMenuBar: true,
		webPreferences: { preload: path.join(__dirname, "log-preload.js"), contextIsolation: true, sandbox: true } });
	logWindow.loadFile(path.join(__dirname, "log.html"));
	logWindow.on("closed", () => { logWindow = null; });
	return true;
}
ipcMain.handle("luna-de:log", () => showLog());
ipcMain.handle("luna-de:log-history", () => logLines);
ipcMain.handle("luna-de:log-clear", () => { logLines.length = 0; return true; });
ipcMain.handle("luna-de:reload", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.reload(); } return true; });

/** A package (.ipk) or an app's folder handed to Luna DE: the host installs it and says how it went in a banner. */
function install(file) {
	if (!file) { return; }
	net.fetch(host + "/install?" + new URLSearchParams({ path: file }).toString(), { method: "POST" })
		.catch((err) => console.error("Luna DE: install " + file + ": " + err));
}
ipcMain.handle("luna-de:install", (e, files) => { (files || []).forEach(install); return true; });
ipcMain.handle("luna-de:remove", (e, id) => {
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
ipcMain.on("luna-de:page", (e, page) => {
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
					if (c) { send("luna-de:show", { id: c.id }); }
				} else if (ev.type === "close") {
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c) { removeCard(c.id, true); }
					else {
						const h = [...headless.values()].find((v) => pageIds.get(v.webContents.id) === ev.page);
						if (h && !h.webContents.isDestroyed()) { h.webContents.close(); }
					}
				} else if (ev.type === "apps") { send("luna-de:apps", {}); }
				else if (ev.type === "banner") {
					send("luna-de:banner", { id: ev.id, appId: ev.appId, message: ev.message, params: ev.params,
						icon: ev.icon || (ev.appId ? host + "/icon?id=" + encodeURIComponent(ev.appId) : "") });
				} else if (ev.type === "banner-remove") { send("luna-de:banner-remove", { id: ev.id }); }
				else if (ev.type === "banner-clear") { send("luna-de:banner-remove", { appId: ev.appId }); }
				else if (ev.type === "network") { send("luna-de:network", { online: ev.online }); }
								else if (ev.type === "keyboard" || ev.type === "keyboard-resizes") {
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c) {
						if (ev.type === "keyboard") { c.wantsKeyboard = !!ev.show; } else { c.keyboardResizes = !!ev.resize; }
						applyKeyboard();
					}
				}
				else if (ev.type === "ready") {
					// The app's own word that its stage is ready (PalmSystem.stageReady), as on Android.
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c && c.ready) { c.ready(); }
				} else if (ev.type === "activate") {
					const c = [...cards.values()].find((k) => pageIds.get(k.view.webContents.id) === ev.page);
					if (c) { send("luna-de:show", { id: c.id }); }
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
	for (const c of here) {
		const r = cardRect();
		// A zoomed page's scale goes by the card's shorter side, which a turn changes.
		try { c.view.webContents.setZoomFactor(pageScale(c.view.webContents)); } catch (e) {}
		c.view.setBounds(c === keyboardCard && c.shrunk ? { x: r.x, y: r.y, width: r.width, height: r.height - KEYBOARD } : r);
	}
	send("luna-de:screen", screenForShell());
	net.fetch(host + "/orientation?v=" + to, { method: "POST" }).catch(() => {});
}

// Keys from any page, a card's included. The old SDK emulator's wherever they fit (codepoet,
// 2026-11-15; Docs/luna-de.md): End opens and closes the launcher, Home minimises the card and
// maximises it again, Esc is the back gesture, F6 to F9 turn the device up, down, right and left.
// Luna DE's own: F12 for the DevTools of whatever has the keyboard, Ctrl/Cmd+R to reload it, and
// F1 as a spare home button.
const TURN = { F6: "up", F7: "down", F8: "right", F9: "left" };
/** Every page is drawn at the device's scale: 1 on a TouchPad, 1.5 on a Pre3, whose pages are 320 px to its screen's 480. */
function scalePage(wc) {
	const set = () => { if (!wc.isDestroyed()) { try { wc.setZoomFactor(pageScale(wc)); } catch (e) {} } };
	set();
	wc.on("dom-ready", set);
	wc.on("did-finish-load", set);
}

/**
 * Phone Zoom, as Lunacy has it on a phone (Docs/phone.md, "Fixed viewport, per app"): an app with
 * its switch on has its card's page laid out so the card's shorter side is 640 px and shown scaled
 * down to fit, so an app written for a TouchPad's screen fits a Pre3's whole. The host keeps the
 * switches (its DesktopShell.pageScale is this same sum, for cardSize); only as a Pre3, and only
 * for cards in the main window.
 */
let phoneZoom = { width: 640, defaults: [], set: {} };
const zoomOn = (appId) => !!appId && (appId in phoneZoom.set ? !!phoneZoom.set[appId] : phoneZoom.defaults.includes(appId));
/** Screen px per page px in wc's page. */
function pageScale(wc) {
	if (device.id !== "pre3" || !wc || wc.isDestroyed()) { return device.scale; }
	for (const p of panels.values()) { if (p.view.webContents === wc) { return device.scale; } }
	for (const c of cards.values()) { if (c.view.webContents === wc && c.popped) { return device.scale; } }
	let appId = null;
	try { appId = appIdOf(wc.getURL()); } catch (e) {}
	if (!zoomOn(appId)) { return device.scale; }
	const r = cardRect();
	return Math.min(device.scale, Math.min(r.width, r.height) / phoneZoom.width);
}
/** The bezel's Phone Zoom button, for the card that is up: its state, or (flip) the other one, the card loaded again at it. */
ipcMain.handle("luna-de:phone-zoom", async (e, id, flip) => {
	const c = cards.get(id);
	if (!c || device.id !== "pre3") { return null; }
	if (flip) {
		try { phoneZoom = await (await net.fetch(host + "/phone-zoom?app=" + encodeURIComponent(c.appId) + "&v=" + (zoomOn(c.appId) ? 0 : 1), { method: "POST" })).json(); } catch (err) {}
		if (!c.view.webContents.isDestroyed()) { c.view.webContents.reload(); }
	}
	return zoomOn(c.appId);
});

/** What the host says of the device it is being, and what follows from it here. */
async function readDevice() {
	try {
		const s = await (await net.fetch(host + "/shell")).json();
		deviceUserAgent = s.userAgent || "";
		if (s.device) { device = s.device; orientation = s.device.orientation || orientation; }
		if (s.phoneZoom) { phoneZoom = s.phoneZoom; }
	} catch (e) {}
}

/**
 * Luna DE becomes another device: every app is closed first (a page is told its device once, as
 * it starts), then the host changes, and the window takes the new screen's size and scale.
 */
async function setDevice(id) {
	if (id === device.id || !shell) { return device.id; }
	for (const c of [...cards.values()]) { removeCard(c.id, true); }
	for (const p of [...panels.values()]) { removePanel(p.id, true); }
	for (const h of [...headless.values()]) { if (!h.webContents.isDestroyed()) { h.webContents.close(); } }
	headless.clear();
	keyboardCard = null;
	await net.fetch(host + "/device?v=" + id, { method: "POST" }).catch(() => {});
	await readDevice();
	const size = screenSize();
	shell.setResizable(true);
	shell.setContentSize(size.width, size.height + BEZEL);
	shell.setResizable(false);
	try { shell.webContents.setZoomFactor(device.scale); } catch (e) {}
	send("luna-de:keyboard", { show: false, height: KEYBOARD });
	send("luna-de:screen", screenForShell());
	return device.id;
}
ipcMain.handle("luna-de:device", (e, id) => setDevice(id || (device.id === "touchpad" ? "pre3" : "touchpad")));
ipcMain.handle("luna-de:screen-now", () => screenForShell());

/** The user agent every app page gives: the device's own, which webOS apps look for (DeviceProfile.userAgent, from the host). */
let deviceUserAgent = "";
app.on("web-contents-created", (e, wc) => {
	// Before anything loads in it; the shell's own page keeps Electron's.
	if (deviceUserAgent && !(shell && wc === shell.webContents)) { wc.setUserAgent(deviceUserAgent); }
	scalePage(wc);
	// Its console goes to the log.
	wc.on("console-message", (ev) => { logLine(wc, ev.level, ev.message, ev.lineNumber, ev.sourceId); });
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
		if (input.key === "F1" && shell) { ev.preventDefault(); send("luna-de:home"); }
		else if (input.key === "Home" && shell && !input.shift && !input.control && !isEditing(input)) { ev.preventDefault(); send("luna-de:toggle-card"); }
		else if (input.key === "End" && shell && !input.shift && !input.control && !isEditing(input)) { ev.preventDefault(); send("luna-de:launcher"); }
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
	await readDevice();
	try {
		const k = await (await net.fetch(host + "/keyboard-size")).json();
		if (KEYBOARD_HEIGHTS[String(k.size)]) { keyboardSize = k.size; KEYBOARD = KEYBOARD_HEIGHTS[String(k.size)]; }
	} catch (e) {}
	openShell();
	followHost();
	// LUNA_DE_OPEN=<app id>[,<app id>...] launches those apps once the shell is ready.
	ipcMain.once("luna-de:ready", () => {
		// LUNA_DE_INSTALL=<package or app folder>[<path separator><another>...] installs those first.
		(process.env.LUNA_DE_INSTALL || "").split(path.delimiter).filter(Boolean).forEach((f) => install(path.resolve(f)));
		if (!process.env.LUNA_DE_OPEN) { return; }
		for (const id of process.env.LUNA_DE_OPEN.split(",")) { askLaunch(id); }
	});
});

app.on("window-all-closed", () => app.quit());
app.on("quit", () => { if (hostProcess) { hostProcess.kill(); } });
