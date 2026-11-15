// Luna DE's Electron main process (Docs/luna-de.md). It starts the JVM host (LunaCore on the
// desktop), routes the app origins' requests to it, and opens the launcher and the app windows.
"use strict";
const { app, BrowserWindow, ipcMain, net, session } = require("electron");
const { spawn } = require("child_process");
const path = require("path");
const fs = require("fs");
const readline = require("readline");

const REPO = path.resolve(__dirname, "..", "..");
const HOST_SUFFIX = ".media.cryptofs.apps";
const IS_WIN = process.platform === "win32";
// The TouchPad's screen, the only device for now (codepoet, 2026-11-15).
const SIZES = { landscape: [1024, 768], portrait: [768, 1024] };

let host = null;          // "http://127.0.0.1:<port>"
let hostProcess = null;
let launcher = null;

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

function openLauncher() {
	launcher = new BrowserWindow({
		width: 1024, height: 768, useContentSize: true, title: "Luna DE",
		backgroundColor: "#202020",
		webPreferences: { preload: path.join(__dirname, "launcher-preload.js"), contextIsolation: true, sandbox: true },
	});
	launcher.removeMenu();
	launcher.loadFile(path.join(__dirname, "launcher.html"));
	launcher.on("closed", () => { launcher = null; });
}

/** An app's card: a TouchPad-sized window on the app's own origin, with its DevTools a key away. */
function openApp(entry) {
	const [w, h] = SIZES.landscape;
	const win = new BrowserWindow({
		width: w, height: h, useContentSize: true, resizable: false, title: entry.title,
		backgroundColor: "#000000",
		webPreferences: {
			preload: path.join(__dirname, "app-preload.js"),
			// The page and Lunacy's scripts share one world, as they do in Android's WebView,
			// where window.LunacyNative is a binding in the page's own world.
			contextIsolation: false,
			sandbox: false,
			nodeIntegration: false,
		},
	});
	win.removeMenu();
	win.webContents.on("before-input-event", (e, input) => {
		if (input.type === "keyDown" && (input.key === "F12" || (input.control && input.shift && input.key.toLowerCase() === "i"))) {
			win.webContents.toggleDevTools();
		}
		if (input.type === "keyDown" && input.key === "F5") { win.webContents.reload(); }
	});
	win.loadURL(entry.url);
}

ipcMain.handle("lunade:apps", async () => {
	const r = await net.fetch(host + "/apps");
	const apps = await r.json();
	return apps.map((a) => Object.assign(a, { icon: host + a.icon }));
});
ipcMain.handle("lunade:launch", async (e, entry) => { openApp(entry); return true; });

app.whenReady().then(async () => {
	try {
		await startHost();
	} catch (e) {
		console.error("Luna DE: " + e.message);
		app.exit(1);
		return;
	}
	routeAppOrigins();
	openLauncher();
	// LUNADE_OPEN=<app id> opens that app at once (for scripts and tests).
	if (process.env.LUNADE_OPEN) {
		const apps = await (await net.fetch(host + "/apps")).json();
		const entry = apps.find((a) => a.id === process.env.LUNADE_OPEN);
		if (entry) { openApp(entry); }
	}
});

app.on("window-all-closed", () => app.quit());
app.on("quit", () => { if (hostProcess) { hostProcess.kill(); } });
