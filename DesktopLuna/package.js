#!/usr/bin/env node
// Builds Luna DE's distributable for the machine this runs on, in one go, with nothing copied
// by hand (Docs/luna-de.md, "Packaging"):
//
//   cd DesktopLuna && npm install && npm run dist
//
// What a package carries, and where each comes from:
//   - the Electron app (app/), built by electron-builder;
//   - the JVM host and its libraries (./gradlew :DesktopLuna:host:installDist), with SQLite's
//     native library for this machine taken out of its jar and put beside it, and the others
//     dropped (a Mac's notarisation reads inside jars, and refuses unsigned libraries there);
//   - a Java runtime of its own, cut with the JDK's jlink to the modules the host uses, so a
//     user needs no Java;
//   - the Node 12 that runs webOS JS services (nodejs.org's build, checked against its checksum);
//   - Lunacy's assets, at the places they have in a checkout: AndroidLuna/src/main/assets,
//     AndroidLuna/local-assets (what fetch-assets.sh fetched and patched; not its PDK part,
//     which Luna DE doesn't run), CoreApps and the keyboard's art.
//
// A package is for the system and processor it was built on: a Mac's on a Mac (signed and
// notarised there when the usual CSC_* and APPLE_* variables are set), Linux's on Linux, and
// Windows' on Windows. Windows' can also be made on Linux or a Mac:
//
//   npm run dist -- --win
//
// which needs no Windows machine and no fetch-assets.sh run there: nothing in the package is
// compiled for Windows here, its three native parts are fetched (node.exe from nodejs.org; the
// Windows modules of the same JDK version this machine's jlink is, from Adoptium, which jlink
// cuts a Windows runtime from) or taken from a jar (SQLite's .dll). It is unsigned; sign the
// installer on Windows afterwards, or build there with CSC_LINK set. Output: DesktopLuna/dist/.
"use strict";
const fs = require("fs"), path = require("path"), os = require("os"), https = require("https"), crypto = require("crypto");
const { execFileSync } = require("child_process");

const HERE = __dirname, REPO = path.resolve(HERE, "..");
const HOST_WIN = process.platform === "win32";
// What the package is for: this machine, or Windows on x64 with --win.
const CROSS_WIN = process.argv.includes("--win") && !HOST_WIN;
const WIN = HOST_WIN || CROSS_WIN, MAC = process.platform === "darwin" && !CROSS_WIN;
const ARCH = CROSS_WIN ? "x64" : process.arch;
const CACHE = path.join(HERE, "build", "cache");
const STAGE = path.join(HERE, "build", "package");
const NODE_VERSION = "v12.22.12";
// What the host and its libraries use of the JDK (java.desktop for ImageIO, jdk.crypto.ec for
// TLS, jdk.httpserver for the host's own server, jdk.unsupported for SQLite's driver).
const JAVA_MODULES = ["java.base", "java.sql", "java.desktop", "java.logging", "java.naming", "java.net.http", "java.xml",
	"java.management", "java.security.jgss", "jdk.httpserver", "jdk.crypto.ec", "jdk.unsupported", "jdk.charsets", "jdk.zipfs"];

const say = (m) => console.log("package: " + m);
const fail = (m) => { console.error("package: " + m); process.exit(1); };
const run = (cmd, args, opts) => execFileSync(cmd, args, Object.assign({ stdio: "inherit" }, opts));

function fetchTo(url, file) {
	return new Promise((resolve, reject) => {
		https.get(url, (res) => {
			if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) { res.resume(); return fetchTo(res.headers.location, file).then(resolve, reject); }
			if (res.statusCode !== 200) { res.resume(); return reject(new Error(url + ": " + res.statusCode)); }
			const out = fs.createWriteStream(file);
			res.pipe(out);
			out.on("finish", () => out.close(resolve));
			out.on("error", reject);
		}).on("error", reject);
	});
}
const sha256 = (file) => crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");

/**
 * The JDK this build uses, found for itself (app/find-jdk.js): a 17 (up to 22 will do) with jlink
 * and jar, wherever it is installed. The machine's default Java isn't consulted or changed.
 */
const JDK = require("./app/find-jdk.js").findJdk(["jlink", "jar"]);
if (!JDK.home) {
	fail("needs a JDK 17 (17 to 22 will do) and found none. Looked at: " + (JDK.seen.join("; ") || "nothing") +
		". Install one (it needn't be the default" + (MAC ? "; brew install --cask temurin@17" : "") + "), or set LUNA_DE_JDK to its folder.");
}
/** One of that JDK's programs, by name. */
function jdkTool(name) { return path.join(JDK.home, "bin", name + (HOST_WIN ? ".exe" : "")); }

/** Node 12 for this machine into DesktopLuna/node/bin, as fetch-node.sh does it (and Windows' too). */
async function node12() {
	// Another system's Node is kept apart from this machine's own (DesktopLuna/node, which `npm start` uses).
	const home = CROSS_WIN ? path.join(STAGE, "node") : path.join(HERE, "node");
	const bin = path.join(home, "bin", WIN ? "node.exe" : "node");
	if (!CROSS_WIN) { try { if (execFileSync(bin, ["--version"]).toString().trim() === NODE_VERSION) { say("node/ already holds Node " + NODE_VERSION); return home; } } catch (e) {} }
	// nodejs.org built Node 12 for x64 on all three (an Apple Silicon Mac runs it under Rosetta), and for arm64 on Linux.
	const arch = !WIN && !MAC && ARCH === "arm64" ? "arm64" : "x64";
	if (ARCH !== "x64" && ARCH !== "arm64") { fail("no Node 12 build for " + ARCH); }
	const base = "https://nodejs.org/dist/" + NODE_VERSION + "/";
	const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "luna-de-node-"));
	const name = WIN ? "win-" + arch + "/node.exe" : "node-" + NODE_VERSION + "-" + (MAC ? "darwin" : "linux") + "-" + arch + ".tar.gz";
	const got = path.join(tmp, path.basename(name)), sums = path.join(tmp, "SHASUMS256.txt");
	say(base + name);
	await fetchTo(base + name, got);
	await fetchTo(base + "SHASUMS256.txt", sums);
	const line = fs.readFileSync(sums, "utf8").split("\n").find((l) => l.endsWith("  " + name));
	if (!line || line.split(" ")[0] !== sha256(got)) { fail("the Node download doesn't match nodejs.org's checksum"); }
	fs.rmSync(home, { recursive: true, force: true });
	fs.mkdirSync(path.join(home, "bin"), { recursive: true });
	if (WIN) {
		fs.copyFileSync(got, bin);
		await fetchTo("https://raw.githubusercontent.com/nodejs/node/" + NODE_VERSION + "/LICENSE", path.join(home, "LICENSE"));
	} else {
		run("tar", ["-xzf", got, "-C", tmp]);
		const dir = path.join(tmp, path.basename(name, ".tar.gz"));
		fs.copyFileSync(path.join(dir, "bin", "node"), bin);
		fs.chmodSync(bin, 0o755);
		fs.copyFileSync(path.join(dir, "LICENSE"), path.join(home, "LICENSE"));
	}
	fs.rmSync(tmp, { recursive: true, force: true });
	return home;
}

/** The host's jars into the stage, SQLite's driver without its native libraries, this machine's beside them. */
function stageHost() {
	const from = path.join(HERE, "host", "build", "install", "luna-de-host", "lib"), to = path.join(STAGE, "host");
	fs.mkdirSync(path.join(to, "lib"), { recursive: true });
	fs.mkdirSync(path.join(to, "native"), { recursive: true });
	const osName = WIN ? "Windows" : MAC ? "Mac" : "Linux", arch = ARCH === "arm64" ? "aarch64" : "x86_64";
	for (const jar of fs.readdirSync(from)) {
		if (!/^sqlite-jdbc-.*\.jar$/.test(jar)) { fs.copyFileSync(path.join(from, jar), path.join(to, "lib", jar)); continue; }
		const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "luna-de-sqlite-"));
		run(jdkTool("jar"), ["xf", path.join(from, jar)], { cwd: tmp });
		const native = path.join(tmp, "org", "sqlite", "native", osName, arch);
		const libs = fs.existsSync(native) ? fs.readdirSync(native) : [];
		if (libs.length !== 1) { fail("SQLite's driver has no native library for " + osName + " " + arch); }
		fs.copyFileSync(path.join(native, libs[0]), path.join(to, "native", libs[0]));
		fs.rmSync(path.join(tmp, "org", "sqlite", "native"), { recursive: true, force: true });
		run(jdkTool("jar"), ["--create", "--file", path.join(to, "lib", jar), "--manifest", path.join("META-INF", "MANIFEST.MF"), "-C", tmp, "."], { cwd: tmp, stdio: ["ignore", "ignore", "inherit"] });
		fs.rmSync(tmp, { recursive: true, force: true });
		say("host: " + jar + " without its native libraries; " + libs[0] + " beside it");
	}
}

/**
 * The Windows modules of the JDK version this machine's jlink is, for jlink to cut a Windows
 * runtime from (it insists the two match). Eclipse Temurin's build of that version, from
 * Adoptium, checked against the checksum published beside it; kept in build/cache.
 */
async function windowsJmods() {
	// java prints its settings on standard error.
	const shown = require("child_process").spawnSync(jdkTool("java"), ["-XshowSettings:properties", "-version"], { encoding: "utf8" });
	const runtime = (/java\.runtime\.version = (\S+)/.exec(String(shown.stderr) + String(shown.stdout)) || [])[1];
	const m = /^(\d+(?:\.\d+)*)\+(\d+)/.exec(runtime || "");
	if (!m) { fail("can't tell this JDK's version (java.runtime.version: " + runtime + ")"); }
	const release = "jdk-" + m[1] + "+" + m[2], dir = path.join(CACHE, "windows-" + release);
	if (fs.existsSync(path.join(dir, "jmods", "java.base.jmod"))) { say("Windows JDK modules: " + release + " (cached)"); return path.join(dir, "jmods"); }
	const api = "https://api.adoptium.net/v3/assets/release_name/eclipse/" + encodeURIComponent(release) + "?architecture=x64&os=windows&image_type=jdk";
	fs.mkdirSync(CACHE, { recursive: true });
	const meta = path.join(CACHE, "windows-jdk.json");
	await fetchTo(api, meta).catch((e) => fail("Adoptium has no Windows build of " + release + " (this machine's JDK version): " + e.message + ". Use a JDK whose version Temurin also released, or build on Windows."));
	const pkg = JSON.parse(fs.readFileSync(meta, "utf8")).binaries[0].package;
	const zip = path.join(CACHE, pkg.name);
	say(pkg.link);
	await fetchTo(pkg.link, zip);
	if (sha256(zip) !== pkg.checksum) { fail("the Windows JDK download doesn't match Adoptium's checksum"); }
	const tmp = fs.mkdtempSync(path.join(CACHE, "unzip-"));
	run("unzip", ["-q", zip, "*/jmods/*", "*/legal/*", "-d", tmp]);
	fs.rmSync(dir, { recursive: true, force: true });
	fs.renameSync(path.join(tmp, fs.readdirSync(tmp)[0]), dir);
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.rmSync(zip, { force: true });
	return path.join(dir, "jmods");
}

/** A copy of a tree with its symbolic links followed: a Windows package can't hold a link. */
function withoutLinks(from, to) {
	// By hand: fs.cpSync's "dereference" leaves the links inside a tree as links.
	fs.mkdirSync(to, { recursive: true });
	for (const name of fs.readdirSync(from)) {
		const a = path.join(from, name), b = path.join(to, name);
		if (fs.statSync(a).isDirectory()) { withoutLinks(a, b); } else { fs.copyFileSync(a, b); }
	}
	return to;
}

/**
 * AndroidLuna/local-assets: the webOS frameworks and system files, patched. They aren't Lunacy's
 * to keep in its repository, so each builder makes them from a TouchPad of their own:
 *
 *   AndroidLuna/get-assets-from-touchpad.sh    takes the files from a TouchPad on the cable
 *   AndroidLuna/fetch-assets.sh --desktop      applies Lunacy's patches: Luna DE's part of local-assets
 *
 * (The development machine's own fetch-assets.sh, without --desktop, makes the Android build's
 * too.) What is there must have been made with the framework patches this checkout has: its
 * patches.applied is compared with LunaRuntimes/, and a difference stops the build, since a
 * package made from older frameworks than its source says would be a quiet lie.
 */
const ASSETS = path.join(REPO, "AndroidLuna", "local-assets");
/** Every patch file in LunaRuntimes/ must be in the manifest with its own checksum; returns what isn't. */
function patchesMissing(manifest) {
	const have = {};
	for (const line of manifest.split("\n")) { const m = /^([0-9a-f]{64})  (\S+)/.exec(line); if (m) { have[m[2]] = m[1]; } }
	const missing = [], runtimes = path.join(REPO, "LunaRuntimes");
	for (const set of fs.readdirSync(runtimes)) {
		const dir = path.join(runtimes, set, "patches");
		if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) { continue; }
		// The manifest names only what fetch-assets applies: a set it has no line for at all (the PDK's) isn't its business.
		if (!Object.keys(have).some((k) => k.startsWith("LunaRuntimes/" + set + "/"))) { continue; }
		for (const name of fs.readdirSync(dir).filter((n) => n.endsWith(".patch"))) {
			const rel = "LunaRuntimes/" + set + "/patches/" + name;
			if (have[rel] !== sha256(path.join(dir, name))) { missing.push(rel); }
		}
	}
	return missing;
}

(async () => {
	if (!fs.existsSync(path.join(ASSETS, "rootfs.index"))) {
		fail("AndroidLuna/local-assets isn't there. With a TouchPad (webOS CE 3.1.0) on the cable: AndroidLuna/get-assets-from-touchpad.sh, then AndroidLuna/fetch-assets.sh --desktop.");
	}
	const missing = fs.existsSync(path.join(ASSETS, "patches.applied")) ? patchesMissing(fs.readFileSync(path.join(ASSETS, "patches.applied"), "utf8")) : [];
	if (missing.length) {
		fail("AndroidLuna/local-assets is older than this checkout's framework patches (" + missing[0] + (missing.length > 1 ? " and " + (missing.length - 1) + " more" : "") +
			"): run AndroidLuna/fetch-assets.sh again (--desktop for Luna DE's part alone).");
	}
	say("JDK " + JDK.major + ": " + JDK.home);
	// --configure-on-demand: only the host and LunaCore are set up, not the Android projects, so a
	// machine building Luna DE needs no Android SDK.
	say("building the host");
	run(path.join(REPO, HOST_WIN ? "gradlew.bat" : "gradlew"), ["--console=plain", "-q", "--configure-on-demand", ":DesktopLuna:host:installDist"], { cwd: REPO, shell: HOST_WIN, env: Object.assign({}, process.env, { JAVA_HOME: JDK.home }) });
	fs.rmSync(STAGE, { recursive: true, force: true });
	fs.mkdirSync(STAGE, { recursive: true });
	const nodeHome = await node12();
	stageHost();
	say("a Java runtime of " + JAVA_MODULES.length + " modules (jlink)" + (CROSS_WIN ? ", for Windows" : ""));
	const modulePath = CROSS_WIN ? ["--module-path", await windowsJmods()] : [];
	run(jdkTool("jlink"), [...modulePath, "--add-modules", JAVA_MODULES.join(","), "--strip-debug", "--no-man-pages", "--no-header-files", "--compress=2", "--output", path.join(STAGE, "jre")]);
	// Windows keeps no symbolic links in a package: CoreApps' few are followed.
	const coreApps = WIN ? withoutLinks(path.join(REPO, "CoreApps"), path.join(STAGE, "CoreApps")) : path.join(REPO, "CoreApps");

	const builder = require("electron-builder");
	const version = require("./package.json").version;
	const repo = (p) => path.join(REPO, p);
	// Off Windows, the installer's own maker needs Wine (it runs a Windows program to write the
	// uninstaller); without Wine the package is the zip alone, which is the whole app in a folder.
	let crossTargets = ["zip"];
	if (CROSS_WIN) {
		try { execFileSync("wine", ["--version"], { stdio: "ignore" }); crossTargets = ["nsis", "zip"]; }
		catch (e) { say("no Wine here, so no installer: the zip only (build on Windows, or install Wine, for the installer)"); }
	}
	await builder.build({
		targets: CROSS_WIN ? builder.Platform.WINDOWS.createTarget(crossTargets, builder.Arch.x64) : undefined,
		config: {
			appId: "org.webosarchive.luna-de",
			productName: "Luna DE",
			copyright: "webOS Archive; GPL-3.0-or-later. Bundled webOS material ships as noted in its NOTICE files.",
			directories: { output: "dist", buildResources: "build-resources" },
			files: ["app/**", "package.json"],
			// The same folders at the same places as in a checkout (main.js's REPO).
			extraResources: [
				{ from: repo("AndroidLuna/src/main/assets"), to: "AndroidLuna/src/main/assets" },
				{ from: repo("AndroidLuna/local-assets"), to: "AndroidLuna/local-assets", filter: ["**/*", "!pdk/**"] },
				{ from: coreApps, to: "CoreApps" },
				{ from: repo("LunaKeyboard/src/main/assets/keyboard"), to: "LunaKeyboard/src/main/assets/keyboard" },
				{ from: nodeHome, to: "DesktopLuna/node" },
				{ from: path.join(STAGE, "host"), to: "host" },
				{ from: path.join(STAGE, "jre"), to: "jre" },
				{ from: repo("LICENSE"), to: "LICENSE.Lunacy" },
				{ from: repo("NOTICE"), to: "NOTICE.Lunacy" },
			].filter((r) => fs.existsSync(r.from)),
			artifactName: "luna-de-${version}-${os}-${arch}.${ext}",
			// The icon: Lunacy's moon (build-resources/make-icon.py).
			linux: { target: ["AppImage", "tar.gz"], category: "Development", executableName: "luna-de", icon: "build-resources/icon.png" },
			// Signed with the Developer ID in the keychain (or CSC_LINK), and notarised when
			// APPLE_ID, APPLE_APP_SPECIFIC_PASSWORD and APPLE_TEAM_ID (or an APPLE_API_KEY set) are
			// in the environment. The hardened runtime needs the JVM's and Node's entitlements.
			mac: { icon: "build-resources/icon-mac.png", target: ["dmg", "zip"], category: "public.app-category.developer-tools", hardenedRuntime: true, gatekeeperAssess: false,
				entitlements: "build-resources/entitlements.mac.plist", entitlementsInherit: "build-resources/entitlements.mac.plist" },
			win: { target: ["nsis", "zip"], icon: "build-resources/icon.png" },
			nsis: { oneClick: false, allowToChangeInstallationDirectory: true },
			// Linux: a package can't count on Chromium's sandbox helper (an AppImage or a tarball
			// can't carry a setuid-root file, and newer kernels' settings refuse the other kind;
			// Chromium then stops before any of the app's own code runs), so the program people
			// start is a script that starts the real one as `npm start` does, with --no-sandbox.
			afterPack: async (ctx) => {
				if (ctx.electronPlatformName !== "linux") { return; }
				const exe = path.join(ctx.appOutDir, ctx.packager.executableName), real = exe + ".bin";
				fs.renameSync(exe, real);
				fs.writeFileSync(exe, '#!/bin/sh\n# Luna DE (DesktopLuna/package.js): Chromium without its sandbox helper, which a package cannot install.\nexec "$(dirname "$(readlink -f "$0")")/' + path.basename(real) + '" --no-sandbox "$@"\n');
				fs.chmodSync(exe, 0o755);
			},
		},
	});
	say("done: DesktopLuna/dist/ (Luna DE " + version + ")");
})().catch((e) => fail(e && e.stack || String(e)));
