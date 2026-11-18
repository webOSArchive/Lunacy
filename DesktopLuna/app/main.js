// Luna DE's Electron main process (Docs/luna-de.md). It starts the JVM host (LunaCore on the
// desktop), routes the app origins' requests to it, and runs the device: one window, a TouchPad's
// screen, with the shell page drawn in it and each app window a card.
//
// A card is a native view (WebContentsView) over the shell page, so that a page's window.open
// makes a real window with its opener, as Android's onCreateWindow does: Enyo's window manager
// talks to its cards through those objects, and apps like Email and Calendar start headless and
// open every card that way. The shell draws everything else, and in the card view shows each
// card as a snapshot (a native view can't be scaled as Lunacy's live cards are; recorded in
// Docs/luna-de.md). Which windows there are, and what each is (a card, a dashboard, a page nobody
// sees), is the host's to say: LunaCore's AppLifecycle, the class Android's shell runs. This
// makes the views it names, tells it of each window a page opens and each view that goes, and
// places them as its events say (followHost).
"use strict";
const { app, BaseWindow, BrowserWindow, Menu, WebContentsView, dialog, ipcMain, net, session, shell: electronShell } = require("electron");
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
//
// Or this screen ("screen"): a TouchPad at whatever size the window is, as Lunacy is on an Android
// tablet, where it fills the screen it finds. The window is then resizable (and F11 fills the
// screen with it); the screen is the window's, less the bezel; and a TouchPad px is one of the
// desktop's own (a device-independent pixel), so the desktop's display scaling is what makes a
// dense monitor's TouchPad the size it should be, as Android's density does on a phone. Android's
// tablet rule (the short side about 768 px) was tried first and made a desktop monitor's
// TouchPad enormous (codepoet, 2026-11-17): a monitor is far bigger, and further away, than a
// tablet. Wide or tall is how the window is shaped.
const BEZEL = 44;
const FILL = "screen";
const MIN_FILL = { width: 480, height: 400 };
let device = { id: "touchpad", name: "HP TouchPad", width: 1024, height: 768, scale: 1, virtualKeyboard: true };
let orientation = "right";
/** As this screen: the window's screen area, in its own px. */
let fill = { width: 1024, height: 768 };
const filling = () => device.id === FILL;
const fillScale = () => 1;
const portrait = () => orientation === "up" || orientation === "down";
const STATUS_CSS = 28;
const statusPx = () => Math.round(STATUS_CSS * device.scale);
const screenSize = () => {
	if (filling()) { return { width: fill.width, height: fill.height }; }
	const long = Math.max(device.width, device.height), short = Math.min(device.width, device.height);
	return portrait() ? { width: short, height: long } : { width: long, height: short };
};
const cardRect = () => { const s = screenSize(); return { x: 0, y: statusPx(), width: s.width, height: s.height - statusPx() }; };
/** What the shell page is told of the screen: in its own px, which are the device's over its scale. */
const screenForShell = () => {
	const s = screenSize();
	return { width: Math.round(s.width / device.scale), height: Math.round(s.height / device.scale), orientation, device: device.id, mode,
		bezel: BEZEL / device.scale, virtualKeyboard: device.virtualKeyboard };
};

let host = null;          // "http://127.0.0.1:<port>"
let hostProcess = null;
let shell = null;         // the device's window
let nextCard = 1;
/**
 * Every app window's view, by the id the host knows it by: { id, appId, view, attrs, card, panel }.
 * "h..." ids are the host's (an app's first window, which it names before the view exists);
 * "e..." ids are given here, to a window a page opened. A view with no card and no panel is on
 * no screen: a headless app's page, or a window the host hasn't placed yet.
 */
const views = new Map();
/** A view's id by its webContents' id, and by each page it has loaded (app-preload.js's page id). */
const viewOfContents = new Map();
const viewOfPage = new Map();
let nextOpened = 1;
/** Card id to { id, appId, title, view, popped, v }: the views shown as cards. */
const cards = new Map();
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
	// Luna DE's version (package.json's: the SDK version it ships with), for its build info.
	let bin, args = ["--repo", REPO, "--data", dataDir(), "--with-parent", "1", "--version", app.getVersion()];
	// A packaged Luna DE's assets are one zip beside the app (package.js); LUNA_DE_ASSETS names one
	// for a checkout, to try a package's assets without packaging.
	const zip = app.isPackaged ? path.join(process.resourcesPath, "assets.zip") : process.env.LUNA_DE_ASSETS;
	if (zip) { args.push("--assets", zip); }
	// The desktop's own folders that stand for a device's under /media/internal, as Android's
	// shared storage does there (codepoet, 2026-11-18): the person's documents, downloads, music,
	// pictures and videos, wherever this system keeps them. One that can't be found isn't mapped.
	const folders = {};
	for (const [name, key] of [["documents", "documents"], ["downloads", "downloads"], ["music", "music"], ["photos", "pictures"], ["video", "videos"]]) {
		try { folders[name] = app.getPath(key); } catch (e) {}
	}
	args.push("--folders", JSON.stringify(folders));
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
	} else {
		// From a checkout: a Java 17 found for itself (find-jdk.js), whatever the machine's default
		// is, on the built jars. Not Gradle's launcher, which takes the default Java (a Mac's may
		// be 8, which can't run the host) and on Windows is a command file.
		const jdk = require("./find-jdk.js").findJdk();
		if (!jdk.home) { throw new Error("Luna DE's host needs Java 17 (to 22) and none was found; looked at: " + (jdk.seen.join("; ") || "nothing") + ". Set LUNA_DE_JDK to one."); }
		const lib = path.join(__dirname, "..", "host", "build", "install", "luna-de-host", "lib");
		if (!fs.existsSync(lib)) { throw new Error("No host at " + lib + ": run ./gradlew :DesktopLuna:host:installDist first"); }
		bin = path.join(jdk.home, "bin", IS_WIN ? "java.exe" : "java");
		args = ["-cp", path.join(lib, "*"), "org.webosarchive.lunacy.desktop.MainKt", ...args];
	}
	if (!fs.existsSync(bin)) {
		throw new Error("No host at " + bin + (app.isPackaged ? ": this copy of Luna DE is incomplete" : ": run ./gradlew :DesktopLuna:host:installDist first"));
	}
	return new Promise((resolve, reject) => {
		hostProcess = spawn(bin, args, { stdio: ["pipe", "pipe", "inherit"] });
		hostProcess.on("exit", (code) => {
			if (!host) { const e = new Error("host exited " + code); e.busy = code === 3; reject(e); } else { app.quit(); }
		});
		readline.createInterface({ input: hostProcess.stdout }).on("line", (line) => {
			const m = /^LUNA-DE-HOST (\d+)$/.exec(line);
			if (m && !host) { host = "http://127.0.0.1:" + m[1]; resolve(host); }
			else if (line === "LUNA-DE-HOST-BUSY") { const e = new Error("another Luna DE is running with " + dataDir()); e.busy = true; reject(e); }
			else { console.log("[host] " + line); }
		});
	});
}

/**
 * A request the host holds until it has something to say (a page's /poll, the shell's /events),
 * on a connection of its own (Node's, which has no limit) instead of Chromium's: it keeps six
 * to a host, every page's poll held one for up to 25 s, and with six pages open (the three
 * boot-time apps and three cards) the next page's files waited behind them: an app took 25 s
 * to start.
 */
const longAgent = new (require("http").Agent)({ keepAlive: true, maxSockets: Infinity });
function hostLong(pathAndQuery) {
	return new Promise((resolve) => {
		const req = require("http").get(host + pathAndQuery, { agent: longAgent }, (res) => {
			const chunks = [];
			res.on("data", (c) => chunks.push(c));
			res.on("end", () => resolve(new Response(Buffer.concat(chunks), { status: res.statusCode || 502,
				headers: { "content-type": res.headers["content-type"] || "application/json" } })));
			res.on("error", () => resolve(new Response("[]", { status: 502 })));
		});
		req.on("error", () => resolve(new Response("[]", { status: 502 })));
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
			if (what === "poll") { return hostLong("/poll?" + q.toString()); }
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
	// The preload runs in a page's frames too (nodeIntegrationInSubFrames; the name is Electron's, and
	// no frame gets Node): a frame shares its page's interface, as on Android (app-preload.js).
	return { preload: path.join(__dirname, "app-preload.js"), contextIsolation: false, sandbox: false, nodeIntegration: false,
		nodeIntegrationInSubFrames: true, allowRunningInsecureContent: true };
}

function appIdOf(url) {
	try { const h = new URL(url).hostname; return h.endsWith(HOST_SUFFIX) ? h.slice(0, -HOST_SUFFIX.length) : null; } catch (e) { return null; }
}

/** Something the host is told, with nothing to wait for. */
function hostPost(what, params) {
	if (host) { net.fetch(host + what + "?" + new URLSearchParams(params).toString(), { method: "POST" }).catch(() => {}); }
}

/**
 * A view for the window the host calls [id]: around the webContents Chromium made for a page's
 * window.open, or a new one. It is on no screen until the host says where it goes.
 */
function makeView(id, appId, webContents, attrs) {
	const view = webContents ? new WebContentsView({ webContents, webPreferences: pagePrefs() }) : new WebContentsView({ webPreferences: pagePrefs() });
	const v = { id, appId, view, attrs: attrs || {}, card: null, panel: null };
	views.set(id, v);
	const contents = view.webContents.id;
	viewOfContents.set(contents, id);
	adoptOpens(view.webContents);
	// window.close() from the page, or the page gone: the host hears the window has closed.
	view.webContents.on("destroyed", () => {
		viewOfContents.delete(contents);
		for (const [page, at] of viewOfPage) { if (at === id) { viewOfPage.delete(page); } }
		if (views.get(id) !== v) { return; }   // closed at the host's word: it knows
		views.delete(id);
		unplace(v);
		hostPost("/view-gone", { view: id });
	});
	return v;
}

/** The view the host names in an event, made now if this is the first it is heard of (an app's first window). */
function viewFor(ev) { return views.get(ev.view) || (/^h/.test(ev.view) ? makeView(ev.view, ev.appId) : null); }

/**
 * Every page's window.open makes a view for the new page, which keeps its opener (Electron's
 * createWindow: the view is made around the webContents Chromium already made for the open).
 * What the window is (a card, a dashboard, an alert) the host decides, from the kind the page
 * named (window.open's "window" attribute, WindowType in LunaCore): it is told here, and places
 * the view with an event.
 */
function adoptOpens(wc) {
	wc.setWindowOpenHandler((d) => {
		if (!appIdOf(d.url) && d.url !== "about:blank") { return { action: "deny" }; }
		return {
			action: "allow",
			// A window lives until the lifecycle closes it, as on webOS, where a dashboard outlived
			// the card that opened it. Electron's own rule closes a window with its opener.
			outlivesOpener: true,
			createWindow: (options) => {
				const appId = appIdOf(wc.getURL()) || appIdOf(d.url) || "";
				const attrs = (pendingAttributes.get(wc.id) || []).shift() || {};
				const v = makeView("e" + nextOpened++, appId, options.webContents, attrs);
				v.title = d.frameName;
				hostPost("/opened", { view: v.id, parent: viewOfContents.get(wc.id) || "", app: appId, kind: String(attrs.window || "card") });
				return v.view.webContents;
			},
			overrideBrowserWindowOptions: { webPreferences: pagePrefs() },
		};
	});
}

/** A view the host says is a card: in the device's window, hidden until the shell brings it up. */
function addCard(v, title, hints) {
	const id = nextCard++;
	const view = v.view, appId = v.appId;
	const c = { id, appId, title: v.title || title || appId, view, popped: null, v };
	cards.set(id, c);
	v.card = c;
	view.setBackgroundColor("#000000");
	view.setBounds(cardRect());
	view.setVisible(false);
	shell.contentView.addChildView(view);
	tell(view.webContents, "type", "card");
	view.webContents.on("page-title-updated", (e, t) => { c.title = t; send("luna-de:card-title", { id, title: t }); });
	// The card waits in the card view with its splash while its page loads, then comes up
	// (CLAUDE.md, rule 0). Ready is the page's first paint with something in it; a page that never
	// says so is taken as ready when it has loaded.
	c.ready = () => { if (!c.saidReady) { c.saidReady = true; send("luna-de:card-ready", { id }); } };
	if (view.webContents.isLoading() || !view.webContents.getURL()) {
		view.webContents.once("did-finish-load", c.ready);
		view.webContents.once("did-fail-load", c.ready);
	}
	send("luna-de:card-added", Object.assign({ id, appId, title: c.title }, hints || {}));
	// A window a page opened may have loaded before the host said it was a card.
	if (!view.webContents.isLoading() && view.webContents.getURL()) { c.ready(); }
	return c;
}

/**
 * A view the host says is a dashboard, a popup alert or a dock-mode window: a live view the
 * shell places, hidden until it does. A dashboard hears it has no focus as soon as it is there,
 * as on a TouchPad.
 */
function addPanel(v, kind) {
	const id = nextCard++;
	const view = v.view, appId = v.appId;
	const p = { id, appId, kind, view, height: Number(v.attrs.height) || (kind === "dashboard" ? 52 : 100), v };
	panels.set(id, p);
	v.panel = p;
	view.setBackgroundColor("#00000000");
	view.setBounds({ x: 0, y: statusPx(), width: 320, height: p.height });
	view.setVisible(false);
	shell.contentView.addChildView(view);
	tell(view.webContents, "type", kind === "dockmode" ? "dockMode" : kind);
	if (kind === "dashboard") {
		tell(view.webContents, "focus", "false");
		// The row's sweep, in the page (app-preload.js): every touch, or for an Enyo dashboard
		// (webosDragMode "manual") only its 50 px badge at the left; the rest are the app's.
		const manual = String(v.attrs.webosDragMode || "") === "manual";
		const arm = () => { if (!view.webContents.isDestroyed()) { view.webContents.executeJavaScript("window.__lunaDeDashboard&&__lunaDeDashboard(" + manual + ")", false).catch(() => {}); } };
		view.webContents.on("dom-ready", arm);
		arm();
	}
	send("luna-de:panel-added", { id, appId, kind, height: p.height, icon: host + "/icon?id=" + encodeURIComponent(appId) });
}

/** A panel where the shell put it, moved [p.dx] along (a dashboard being swept away). */
function placePanel(p) {
	if (!p.rect) { return; }
	p.view.setBounds({ x: p.rect.x + Math.round(p.dx || 0), y: p.rect.y, width: p.rect.width, height: p.rect.height });
}

/**
 * A dashboard swept sideways (DashboardRow): it follows the pointer, to the right only in the
 * drop-down; let go past a quarter of its width it goes on off by one and a half widths over
 * dashboardDeleteDuration (200 ms, linear) and is closed; short of that it goes back the same 200 ms.
 * The page's px are the device's over its scale.
 */
const DISMISS_MS = 200;
function panelOf(wc) { for (const p of panels.values()) { if (p.view.webContents === wc) { return p; } } return null; }
function animatePanel(p, to, done) {
	clearInterval(p.anim);
	const from = p.dx || 0, t0 = Date.now();
	p.anim = setInterval(() => {
		const f = Math.min(1, (Date.now() - t0) / DISMISS_MS);
		p.dx = from + (to - from) * f;
		if (!p.view.webContents.isDestroyed()) { placePanel(p); }
		if (f >= 1) { clearInterval(p.anim); p.anim = null; if (done) { done(); } }
	}, 16);
}
ipcMain.on("luna-de:dash-drag", (e, dx) => {
	const p = panelOf(e.sender);
	if (!p || p.leaving) { return; }
	clearInterval(p.anim);
	p.dx = Math.max(0, dx * device.scale);
	placePanel(p);
});
ipcMain.on("luna-de:dash-release", (e) => {
	const p = panelOf(e.sender);
	if (!p || p.leaving || !p.rect) { return; }
	if (Math.abs(p.dx || 0) > p.rect.width / 4) {
		p.leaving = true;
		animatePanel(p, Math.sign(p.dx) * p.rect.width * 1.5, () => askClose(p.v));
	} else { animatePanel(p, 0); }
});

function removePanel(id) {
	const p = panels.get(id);
	if (!p) { return; }
	panels.delete(id);
	p.v.panel = null;
	try { if (shell) { shell.contentView.removeChildView(p.view); } } catch (e) {}
	send("luna-de:panel-removed", { id });
}

/** [v] off the screen: its card or its panel goes; the view itself stays until it is closed. */
function unplace(v) {
	if (v.card) { removeCard(v.card.id); }
	if (v.panel) { removePanel(v.panel.id); }
}

/** The host closes a window: off the screen, and its page closed (which runs its unload handlers). */
function closeView(v) {
	views.delete(v.id);
	unplace(v);
	if (!v.view.webContents.isDestroyed()) { v.view.webContents.close(); }
}

/** The person closed a card or a dashboard: the host is asked, and its events take it away. */
function askClose(v) { if (v && views.get(v.id) === v) { hostPost("/close", { view: v.id }); } }

/** Tells the host something of a page: now if its id is known, and again for each page the view loads. */
function tell(wc, what, value) {
	const t = told.get(wc.id) || {};
	t[what] = value;
	told.set(wc.id, t);
	const page = pageIds.get(wc.id);
	if (page) { postTell(page, what, value); }
}
function pageGone(page) {
	viewOfPage.delete(page);
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
	// The page's Text Assist starts clean for this field, with its pictures, and says what to show.
	wc.executeJavaScript("window.__lunaDeKeyboard?(__lunaDeKeyboard.setArt(" + JSON.stringify(textAssistArt()) + "),__lunaDeKeyboard.reset()):null", true)
		.then((s) => { if (s) { send("luna-de:kb-state", s); } }).catch(() => {});
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
/**
 * What the on-screen keyboard pressed, for the card it is up for: [key] is { text }, { emoji },
 * { backspace, word }, { enter }, { tab }, { hide }, { cursor }. The page's Text Assist
 * (textassist.js) does what LunaKeyboard's service does to the field, and answers with the key
 * events to send (Backspace, Return, Tab, the arrows: real keys, as on Android) and what the
 * keyboard is to show. In order, one at a time, as an input method's edits are.
 */
let keyChain = Promise.resolve();
function typeKey(key) {
	const c = keyboardCard;
	if (!c || c.view.webContents.isDestroyed()) { return; }
	const wc = c.view.webContents;
	keyChain = keyChain.then(async () => {
		if (wc.isDestroyed()) { return; }
		wc.focus();
		let r = null;
		try { r = await wc.executeJavaScript("window.__lunaDeKeyboard?__lunaDeKeyboard.handle(" + JSON.stringify(key) + "):null", true); } catch (e) {}
		if (!r) {
			// No Text Assist in the page (it hasn't loaded): the plain thing.
			if (key.text || key.emoji) { wc.insertText(key.text || key.emoji); }
			r = { keys: key.backspace ? [{ key: "Backspace" }] : key.enter ? [{ key: "Return" }] : key.tab ? [{ key: "Tab" }] : [] };
		}
		for (const k of r.keys || []) { sendKey(wc, k.key, k.shift); }
		if (r.state) { send("luna-de:kb-state", r.state); }
		if (key.hide) { lowerKeyboard(c, true); keyboardCard = null; send("luna-de:keyboard", { show: false, height: KEYBOARD }); }
	});
}
function sendKey(wc, code, shift) {
	const modifiers = shift ? ["shift"] : [];
	wc.sendInputEvent({ type: "keyDown", keyCode: code, modifiers });
	if (code === "Return") { wc.sendInputEvent({ type: "char", keyCode: "\r", modifiers }); }
	wc.sendInputEvent({ type: "keyUp", keyCode: code, modifiers });
}

/** Text Assist's pictures (the ate-* art the reference TouchPad's WebKit drew), as data URLs for the pages. */
let assistArt = null;
function textAssistArt() {
	if (assistArt) { return assistArt; }
	const dir = path.join(REPO, "LunaKeyboard", "src", "main", "assets", "assist");
	const names = { left: "ate-left", middle: "ate-middle", right: "ate-right", down: "ate-arrow-down", up: "ate-arrow-up", divider: "ate-divider",
		corrected: "ate-underline-corrected", misspelled: "ate-underline-mispelled" };
	assistArt = {};
	for (const k of Object.keys(names)) {
		try { assistArt[k] = "data:image/png;base64," + fs.readFileSync(path.join(dir, names[k] + ".png")).toString("base64"); } catch (e) {}
	}
	return assistArt;
}
// The page's Text Assist says what the keyboard should show (a capital next, Return's label),
// and when it corrected a word (the plop, which the shell plays).
ipcMain.on("luna-de:kb-state", (e, s) => { if (keyboardCard && keyboardCard.view.webContents === e.sender) { send("luna-de:kb-state", s); } });
ipcMain.on("luna-de:kb-plop", () => send("luna-de:kb-plop"));

function removeCard(id) {
	const c = cards.get(id);
	if (!c) { return; }
	cards.delete(id);
	c.v.card = null;
	if (shown === id) { shown = null; }
	if (keyboardCard === c) { keyboardCard = null; send("luna-de:keyboard", { show: false, height: KEYBOARD }); }
	try { if (!c.popped && shell) { shell.contentView.removeChildView(c.view); } } catch (e) {}
	if (c.popped && !c.popped.isDestroyed()) { const w = c.popped; c.popped = null; w.close(); }
	send("luna-de:card-removed", { id });
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
	// As this screen, the window's size is the screen's: every change of it is the screen changing.
	shell.on("resize", () => { if (filling()) { fillChanged(); } });
	for (const ev of ["enter-full-screen", "leave-full-screen", "maximize", "unmaximize"]) { shell.on(ev, () => { if (filling()) { fillChanged(); } }); }
	// Out of full screen by the desktop's own means: the window is Resizable now.
	shell.on("leave-full-screen", () => { if (mode === "full" && !modeChanging) { mode = "resizable"; saveDevice(mode); send("luna-de:screen", screenForShell()); } });
}

/** The window's screen area as it is now: its content, less the bezel. */
function windowFill() {
	const [w, h] = shell.getContentSize();
	return { width: Math.max(MIN_FILL.width, w), height: Math.max(MIN_FILL.height, h - BEZEL) };
}

/**
 * As this screen, the window changed size: the screen is that size now. The scale follows it
 * (and the shell page's zoom and every app page's with it), a change between wide and tall is
 * the device turning, the cards take the new shape, and the host hears the size apps are told.
 */
let fillTimer = null;
function fillChanged() {
	clearTimeout(fillTimer);
	fillTimer = setTimeout(applyFill, 30);
}
async function applyFill() {
	if (!shell || !filling()) { return; }
	const size = windowFill();
	const scale = fillScale(size), was = orientation, to = size.height > size.width ? "up" : "right";
	if (size.width === fill.width && size.height === fill.height && scale === device.scale && to === was) { return; }
	fill = size;
	device.scale = scale;
	const here = [...cards.values()].filter((c) => !c.popped);
	if (to !== was) {
		orientation = to;
		await Promise.all(here.map((c) => orient(c.view.webContents, to)));
	}
	try { shell.webContents.setZoomFactor(device.scale); } catch (e) {}
	for (const v of views.values()) { try { if (!(v.card && v.card.popped)) { v.view.webContents.setZoomFactor(pageScale(v.view.webContents)); } } catch (e) {} }
	const r = cardRect();
	for (const c of here) { c.view.setBounds(c === keyboardCard && c.shrunk ? { x: r.x, y: r.y, width: r.width, height: r.height - KEYBOARD } : r); }
	send("luna-de:screen", screenForShell());
	hostPost("/screen", fillForHost());
	if (to !== was) { hostPost("/orientation", { v: to }); }
}
/** The screen as the host tells apps of it: in a page's px (the window's over the scale). */
function fillForHost() { return { w: Math.round(fill.width / device.scale), h: Math.round(fill.height / device.scale) }; }

/** The window as the device has it: a fixed size for a TouchPad or a Pre3, free as this screen (where it starts filling the display). */
function shapeWindow() {
	if (!shell) { return; }
	if (filling()) {
		shell.setResizable(true);
		shell.setMinimumSize(MIN_FILL.width, MIN_FILL.height + BEZEL);
		// Full Screen: the whole display, a moment after the window became resizable (asked at once,
		// GNOME on Wayland took it for a window that couldn't change size). Resizable: a window as
		// it is, to be sized by hand.
		const full = mode === "full";
		if (!full && shell.isFullScreen()) { shell.setFullScreen(false); }
		if (full) { setTimeout(() => { if (shell && mode === "full" && !shell.isFullScreen()) { shell.setFullScreen(true); } }, 100); }
		return;
	}
	if (shell.isFullScreen()) { shell.setFullScreen(false); }
	if (shell.isMaximized()) { shell.unmaximize(); }
	const size = screenSize();
	shell.setResizable(true);
	shell.setMinimumSize(1, 1);
	shell.setContentSize(size.width, size.height + BEZEL);
	shell.setResizable(false);
}

/**
 * The screen modes, one at a time (codepoet): Full Screen (a TouchPad the size of the display),
 * HP TouchPad and HP Pre3 (their own screens, in a window of that size), and Resizable (a
 * TouchPad the size of a window sized by hand). Full Screen and Resizable are the same device
 * to the host ("screen"); only the window differs.
 */
const MODES = [["full", "Full Screen"], ["touchpad", "HP TouchPad"], ["pre3", "HP Pre3"], ["resizable", "Resizable"]];
let mode = "touchpad", beforeFull = "resizable", modeChanging = false;
async function setMode(m) {
	if (!shell || !MODES.some((x) => x[0] === m)) { return mode; }
	if (m === "full" && mode !== "full") { beforeFull = mode; }
	modeChanging = true;
	try {
		mode = m;
		if (m === "full" || m === "resizable") { if (!filling()) { await setDevice(FILL); } else { shapeWindow(); } }
		else { await setDevice(m); }
	} finally { setTimeout(() => { modeChanging = false; }, 500); }
	saveDevice(mode);
	send("luna-de:screen", screenForShell());
	return mode;
}

/** F11: into Full Screen, and back out to the mode it came from. */
async function toggleFullScreen() { await setMode(mode === "full" ? beforeFull : "full"); }

/** The device Luna DE was last, kept beside its data: it starts as that again. */
const savedDeviceFile = () => path.join(dataDir(), "luna-de-device.json");
function savedDevice() {
	let d = "";
	try { d = JSON.parse(fs.readFileSync(savedDeviceFile(), "utf8")).device || ""; } catch (e) {}
	return d === "screen" ? "resizable" : d;   // saved before the modes had their names
}
function saveDevice(id) { try { fs.mkdirSync(dataDir(), { recursive: true }); fs.writeFileSync(savedDeviceFile(), JSON.stringify({ device: id })); } catch (e) {} }

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
	// Its window closed: the card is closed, as a flick up closes one in the device.
	win.on("closed", () => { if (c.popped === win) { c.popped = null; askClose(c.v); } });
	send("luna-de:card-removed", { id });
}

ipcMain.handle("luna-de:shell", async () => {
	const s = await (await net.fetch(host + "/shell")).json();
	// A cell is an app, or a group of them.
	s.pages.forEach((p) => p.apps.forEach((c) => (c.apps ? c.apps.forEach(withIcon) : withIcon(c))));
	s.dock.forEach(withIcon);
	return s;
});
// Just Type: what a text finds (the host's answer, by LunaCore's rules), and a choice made.
ipcMain.handle("luna-de:justtype", async (e, text, content) => {
	try {
		const d = await (await net.fetch(host + "/justtype?" + new URLSearchParams({ q: text, content: content ? "1" : "0" }).toString())).json();
		(d.launch || []).forEach(withIcon);
		return d;
	} catch (err) { return null; }
});
// The system menu's Wi-Fi: the simulated network on or off.
ipcMain.handle("luna-de:network", (e, on) => { hostPost("/network", { v: on ? "1" : "0" }); return true; });
// Something of the shell's own drawn over the card that is up (the system menu): its live view
// steps aside while it is there, the shell showing the card's picture in its place.
ipcMain.handle("luna-de:cover", (e, on) => {
	const c = shown && cards.get(shown);
	if (c && !c.popped) { c.view.setVisible(!on); if (!on) { c.view.webContents.focus(); } }
	return true;
});
ipcMain.handle("luna-de:icon-url", (e, id) => host + "/icon?id=" + encodeURIComponent(id));
ipcMain.handle("luna-de:justtype-go", (e, kind, id, text, index) => {
	hostPost("/justtype-go", { kind, id: id || "", q: text || "", i: index === undefined ? -1 : index });
	return true;
});
// The launcher as its owner has just arranged it (its tabs, or the quick-launch bar): the host keeps it.
ipcMain.handle("luna-de:launcher-save", (e, what) => {
	net.fetch(host + "/launcher", { method: "POST", body: JSON.stringify(what || {}) }).catch(() => {});
	return true;
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
		p.rect = r ? { x: Math.round(r.x * k), y: Math.round(r.y * k), width: Math.round(r.width * k), height: Math.round(r.height * k) } : null;
		if (r) { placePanel(p); }
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
ipcMain.handle("luna-de:close", (e, id) => { const k = panels.get(id) || cards.get(id); if (k) { askClose(k.v); } return true; });
ipcMain.handle("luna-de:popout", (e, id) => { popOut(id); return true; });
ipcMain.handle("luna-de:devtools", (e, id) => { const c = cards.get(id); if (c) { c.view.webContents.openDevTools({ mode: "detach" }); } return true; });

/**
 * The bezel's </> button opens the log; its right-click menu inspects a card: Electron's own
 * developer tools for that one page. (A menu of the system's own: one drawn in the shell page
 * would lie under a card that is up.)
 */
/**
 * The bezel's own menu, by the right button anywhere on it: full screen, and the device. (The
 * window's title bar is the desktop's, which takes no items of an app's on Linux.)
 */
/** The wallpaper as the host has it now (the one chosen in Screen & Lock, else the TouchPad's own); a new address each time, so it is read again. */
function wallpaperUrl() { return host + "/wallpaper?t=" + Date.now(); }
ipcMain.handle("luna-de:wallpaper", () => wallpaperUrl());

ipcMain.handle("luna-de:bezel-menu", () => {
	if (!shell) { return false; }
	const items = [];
	for (const [id, label] of MODES) {
		items.push({ label, type: "radio", checked: mode === id, accelerator: id === "full" ? "F11" : undefined, click: () => setMode(id) });
	}
	// Luna DE's own part of the device's user storage, /media/internal, in the desktop's file
	// manager: ringtones, wallpapers and whatever apps and services have written there. The
	// documents, downloads, music, photos and video folders are the desktop's own (startHost).
	items.push({ type: "separator" });
	items.push({ label: "Open the Device's Files (/media/internal)", click: () => {
		const dir = path.join(dataDir(), "webos", "media", "internal");
		electronShell.openPath(dir).then((err) => { if (err) { console.error("Luna DE: " + dir + ": " + err); } });
	} });
	Menu.buildFromTemplate(items).popup({ window: shell });
	return true;
});
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
	// And to /var/log/messages in the webOS root, where palm-log reads it, as webOS's syslogd had it.
	if (host) {
		net.fetch(host + "/console", { method: "POST", headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ app: appId, level: entry.level, message: entry.message, source: where ? "file://" + where : "", line: line || 0 }) }).catch(() => {});
	}
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

// window.close() in an app's page (app-preload.js): that page's view closes, and the host hears
// the window has gone. Left to Electron, it closed the window the view is in: the whole device.
ipcMain.on("luna-de:close-self", (e) => { if (!e.sender.isDestroyed()) { e.sender.close(); } });

/** Each page's id, as its preload told the host (app-preload.js), by its webContents' id. */
const pageIds = new Map();
ipcMain.on("luna-de:page", (e, page) => {
	// The view's last page has gone (a reload, a navigation): the host forgets it.
	const before = pageIds.get(e.sender.id);
	if (before && before !== page) { pageGone(before); }
	pageIds.set(e.sender.id, page);
	// Which window the page is in: this process's to say, as the app is. The host holds the
	// page's first native call until it has heard.
	const view = viewOfContents.get(e.sender.id) || "";
	if (view) { viewOfPage.set(page, view); }
	hostPost("/page", { w: page, view });
	// What this view's page is (its type, its focus) goes to each page it loads.
	const t = told.get(e.sender.id) || {};
	for (const what of ["type", "active", "focus", "orientation"]) { if (t[what] !== undefined) { postTell(page, what, t[what]); } }
});

/** What the host's shell asks of the screen (DesktopShell.take), for as long as Luna DE runs. */
async function followHost() {
	for (;;) {
		let events = [];
		try { const r = await hostLong("/events"); if (r.status !== 200) { throw new Error("no host"); } events = await r.json(); }
		catch (err) { if (!hostProcess || hostProcess.exitCode !== null) { return; } await new Promise((r) => setTimeout(r, 1000)); }
		for (const ev of events) {
			try {
				if (ev.type === "card") {
					// A window that is a card: an app's first (its view made now), or one a page opened.
					const v = viewFor(ev);
					if (v && !v.card) { addCard(v, ev.title, { fromParent: !!ev.fromParent, launcher: ev.launcher || "" }); }
				} else if (ev.type === "loading") {
					// A headless app's loading card: the shell page draws one (unless a tap already
					// did), which the app's first card takes. Its page runs unseen.
					viewFor(ev);
					send("luna-de:card-loading", { appId: ev.appId, title: ev.title, launcher: ev.launcher || "" });
				} else if (ev.type === "withdrawn") { send("luna-de:card-withdrawn", { appId: ev.appId }); }
				else if (ev.type === "hide") { const v = viewFor(ev); if (v) { unplace(v); } }
				else if (ev.type === "load") { const v = viewFor(ev); if (v) { v.view.webContents.loadURL(ev.url); } }
				else if (ev.type === "panel") { const v = views.get(ev.view); if (v && !v.panel) { unplace(v); addPanel(v, ev.kind); } }
				else if (ev.type === "remove") { const v = views.get(ev.view); if (v) { unplace(v); } }
				else if (ev.type === "show") {
					// The app's first card comes forward (a relaunch it didn't handle).
					const c = [...cards.values()].find((k) => k.appId === ev.id && !k.popped);
					if (c) { send("luna-de:show", { id: c.id }); }
				} else if (ev.type === "close") {
					const v = views.get(ev.view);
					if (v) { closeView(v); }
				} else if (ev.type === "apps") { send("luna-de:apps", {}); }
				else if (ev.type === "banner") {
					send("luna-de:banner", { id: ev.id, appId: ev.appId, message: ev.message, params: ev.params,
						icon: ev.icon || (ev.appId ? host + "/icon?id=" + encodeURIComponent(ev.appId) : "") });
				} else if (ev.type === "wallpaper") { send("luna-de:wallpaper", wallpaperUrl()); }
				else if (ev.type === "sound") {
					// A banner's sound or playSoundNotification's (DesktopShell.sound): the app's own
					// file from its origin, else the class's default from the host.
					send("luna-de:sound", { url: ev.url || host + "/sound?name=" + encodeURIComponent(ev.name), duration: ev.duration });
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
	if (filling()) {
		// This screen is held the way its window is shaped: turning it is the window's width and
		// height changing places, where the window is free to (not filling the display).
		const tall = to === "up" || to === "down";
		if (tall === portrait() || shell.isFullScreen() || shell.isMaximized()) { return; }
		shell.setContentSize(fill.height, fill.width + BEZEL);
		return;
	}
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
 * Luna DE becomes another device: the host closes every app first (a page is told its device
 * once, as it starts) and changes, and the window takes the new screen's size and scale.
 */
async function setDevice(id) {
	if (id === device.id || !shell) { return device.id; }
	// As this screen it starts at the size the window has, and takes the display's as the window does.
	const q = new URLSearchParams({ v: id });
	if (id === FILL) {
		const size = windowFill(), k = fillScale(size);
		q.set("w", Math.round(size.width / k)); q.set("h", Math.round(size.height / k));
	}
	// The host ends every app first (its events close their views), then is the other device.
	await net.fetch(host + "/device?" + q.toString(), { method: "POST" }).catch(() => {});
	await readDevice();
	if (filling()) { fill = windowFill(); device.scale = fillScale(fill); orientation = fill.height > fill.width ? "up" : "right"; }
	if (!filling()) { mode = device.id; }
	shapeWindow();
	try { shell.webContents.setZoomFactor(device.scale); } catch (e) {}
	send("luna-de:keyboard", { show: false, height: KEYBOARD });
	send("luna-de:screen", screenForShell());
	return device.id;
}
ipcMain.handle("luna-de:fullscreen", async () => { await toggleFullScreen(); return !!(shell && shell.isFullScreen()); });
/** The bezel's device button goes round the modes: TouchPad, Pre3, Resizable, Full Screen. */
const CYCLE = ["touchpad", "pre3", "resizable", "full"];
ipcMain.handle("luna-de:device", (e, id) => setMode(id === FILL ? "resizable" : id || CYCLE[(CYCLE.indexOf(mode) + 1) % CYCLE.length]));
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
		else if (input.key === "F11") { ev.preventDefault(); toggleFullScreen(); }
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
		// A second copy on a data folder in use: told plainly, as nothing else would show.
		if (e.busy) { dialog.showErrorBox("Luna DE is already running", "Another Luna DE is using its data folder:\n" + dataDir() + "\n\nUse that one, or close it first. (LUNA_DE_DATA names another folder, for a second copy.)"); app.exit(0); return; }
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
	// As the device it was last time.
	const last = savedDevice();
	if (CYCLE.includes(last) && last !== mode) { shell.webContents.once("did-finish-load", () => setMode(last)); }
	// LUNA_DE_OPEN=<app id>[,<app id>...] launches those apps once the shell is ready.
	ipcMain.once("luna-de:ready", () => {
		// LUNA_DE_INSTALL=<package or app folder>[<path separator><another>...] installs those first.
		(process.env.LUNA_DE_INSTALL || "").split(path.delimiter).filter(Boolean).forEach((f) => install(path.resolve(f)));
		if (!process.env.LUNA_DE_OPEN) { return; }
		for (const id of process.env.LUNA_DE_OPEN.split(",")) { askLaunch(id); }
	});
});

// A sound plays when an app or the system asks, as on webOS, whether or not a person has clicked in
// that page first (a notification's sound in the shell page, an alarm's in an app's).
app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");
app.on("window-all-closed", () => app.quit());
app.on("quit", () => { if (hostProcess) { hostProcess.kill(); } });
