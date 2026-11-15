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
const { app, BaseWindow, BrowserWindow, WebContentsView, ipcMain, net, session } = require("electron");
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const readline = require("readline");

const REPO = path.resolve(__dirname, "..", "..");
const HOST_SUFFIX = ".media.cryptofs.apps";
const IS_WIN = process.platform === "win32";
// The TouchPad's screen, the only device for now (codepoet, 2026-11-15); the card area is below
// its 28 px status bar (StatusBar.HEIGHT).
const SCREEN = { width: 1024, height: 768 }, STATUS = 28, BEZEL = 44;
const CARD = { x: 0, y: STATUS, width: SCREEN.width, height: SCREEN.height - STATUS };

let host = null;          // "http://127.0.0.1:<port>"
let hostProcess = null;
let shell = null;         // the device's window
let nextCard = 1;
/** Card id to { id, appId, title, view, popped }. */
const cards = new Map();
/** App id to its headless page (a noWindow app's), which isn't a card. */
const headless = new Map();
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
				addCard(view, appIdOf(wc.getURL()) || appIdOf(d.url) || "", d.frameName);
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
	view.setBounds(CARD);
	view.setVisible(false);
	shell.contentView.addChildView(view);
	adoptOpens(view.webContents);
	view.webContents.on("page-title-updated", (e, t) => { c.title = t; send("lunade:card-title", { id, title: t }); });
	// The card waits in the card view while its page loads, then comes up (CLAUDE.md, rule 0).
	view.webContents.once("dom-ready", () => send("lunade:card-ready", { id }));
	// window.close() from the page, or the page gone: the card goes.
	view.webContents.on("destroyed", () => removeCard(id, false));
	send("lunade:card-added", { id, appId, title: c.title });
	return c;
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
	// An app whose last card has gone ends, its headless page with it (AppLifecycle.endApp).
	if (![...cards.values()].some((k) => k.appId === c.appId)) {
		const h = headless.get(c.appId);
		if (h) { headless.delete(c.appId); if (!h.webContents.isDestroyed()) { h.webContents.close(); } }
	}
}

/** An app launched from the shell: its first card if it is running, else its page, as a card or headless. */
function launch(entry) {
	const running = [...cards.values()].find((c) => c.appId === entry.id && !c.popped);
	if (running) { send("lunade:show", { id: running.id }); return; }
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
	shell = new BrowserWindow({
		width: SCREEN.width, height: SCREEN.height + BEZEL, useContentSize: true, resizable: false, title: "Luna DE",
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
	const win = new BaseWindow({ width: CARD.width, height: CARD.height, useContentSize: true, title: c.title, backgroundColor: "#000000" });
	win.contentView.addChildView(c.view);
	c.view.setBounds({ x: 0, y: 0, width: CARD.width, height: CARD.height });
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
ipcMain.handle("lunade:launch", (e, entry) => { launch(entry); return true; });
// The shell brings a card up: its live view on the screen, every other hidden.
ipcMain.handle("lunade:maximize", (e, id) => {
	for (const c of cards.values()) { if (!c.popped) { c.view.setVisible(c.id === id); } }
	shown = cards.has(id) ? id : null;
	if (shown) { cards.get(id).view.webContents.focus(); }
	return true;
});
// The card view: the live views hidden, the shell showing their snapshots.
ipcMain.handle("lunade:cardview", () => {
	for (const c of cards.values()) { if (!c.popped) { c.view.setVisible(false); } }
	shown = null;
	shell.webContents.focus();
	return true;
});
ipcMain.handle("lunade:snapshot", async (e, id) => {
	const c = cards.get(id);
	if (!c || c.view.webContents.isDestroyed()) { return null; }
	try { return (await c.view.webContents.capturePage()).toDataURL(); } catch (err) { return null; }
});
ipcMain.handle("lunade:close", (e, id) => { removeCard(id, true); return true; });
ipcMain.handle("lunade:popout", (e, id) => { popOut(id); return true; });
ipcMain.handle("lunade:devtools", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.openDevTools({ mode: "detach" }); } return true; });
ipcMain.handle("lunade:reload", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.reload(); } return true; });

/** An entry's icon as a URL the shell page can load. */
function withIcon(a) { return Object.assign(a, { icon: host + a.icon }); }

// Keys from any page, a card's included: F1 is the home button, F12 the page's own DevTools,
// F5 reloads it.
app.on("web-contents-created", (e, wc) => {
	wc.on("before-input-event", (ev, input) => {
		if (input.type !== "keyDown") { return; }
		if (input.key === "F1" && shell) { ev.preventDefault(); send("lunade:home"); }
		else if (input.key === "F12" || (input.control && input.shift && input.key.toLowerCase() === "i")) { ev.preventDefault(); wc.toggleDevTools(); }
		else if (input.key === "F5" && wc !== (shell && shell.webContents)) { ev.preventDefault(); wc.reload(); }
	});
});

app.whenReady().then(async () => {
	try {
		await startHost();
	} catch (e) {
		console.error("Luna DE: " + e.message);
		app.exit(1);
		return;
	}
	routeAppOrigins();
	openShell();
	// LUNADE_OPEN=<app id>[,<app id>...] launches those apps once the shell is ready.
	ipcMain.once("lunade:ready", async () => {
		if (!process.env.LUNADE_OPEN) { return; }
		const apps = await (await net.fetch(host + "/apps")).json();
		for (const id of process.env.LUNADE_OPEN.split(",")) {
			const entry = apps.find((a) => a.id === id);
			if (entry) { launch(entry); }
		}
	});
});

app.on("window-all-closed", () => app.quit());
app.on("quit", () => { if (hostProcess) { hostProcess.kill(); } });
