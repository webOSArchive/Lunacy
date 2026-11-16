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
// notarised there when the usual CSC_* and APPLE_* variables are set), Windows' on Windows,
// Linux's on Linux. Output: DesktopLuna/dist/.
"use strict";
const fs = require("fs"), path = require("path"), os = require("os"), https = require("https"), crypto = require("crypto");
const { execFileSync } = require("child_process");

const HERE = __dirname, REPO = path.resolve(HERE, "..");
const WIN = process.platform === "win32", MAC = process.platform === "darwin";
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

/** The JDK tool by name: JAVA_HOME's, else the PATH's. */
function jdkTool(name) {
	const exe = name + (WIN ? ".exe" : "");
	const home = process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, "bin", exe);
	return home && fs.existsSync(home) ? home : exe;
}

/** Node 12 for this machine into DesktopLuna/node/bin, as fetch-node.sh does it (and Windows' too). */
async function node12() {
	const bin = path.join(HERE, "node", "bin", WIN ? "node.exe" : "node");
	try { if (execFileSync(bin, ["--version"]).toString().trim() === NODE_VERSION) { say("node/ already holds Node " + NODE_VERSION); return; } } catch (e) {}
	// nodejs.org built Node 12 for x64 on all three (an Apple Silicon Mac runs it under Rosetta), and for arm64 on Linux.
	const arch = process.platform === "linux" && process.arch === "arm64" ? "arm64" : "x64";
	if (process.arch !== "x64" && process.arch !== "arm64") { fail("no Node 12 build for " + process.arch); }
	const base = "https://nodejs.org/dist/" + NODE_VERSION + "/";
	const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "lunade-node-"));
	const name = WIN ? "win-" + arch + "/node.exe" : "node-" + NODE_VERSION + "-" + (MAC ? "darwin" : "linux") + "-" + arch + ".tar.gz";
	const got = path.join(tmp, path.basename(name)), sums = path.join(tmp, "SHASUMS256.txt");
	say(base + name);
	await fetchTo(base + name, got);
	await fetchTo(base + "SHASUMS256.txt", sums);
	const line = fs.readFileSync(sums, "utf8").split("\n").find((l) => l.endsWith("  " + name));
	if (!line || line.split(" ")[0] !== sha256(got)) { fail("the Node download doesn't match nodejs.org's checksum"); }
	fs.rmSync(path.join(HERE, "node"), { recursive: true, force: true });
	fs.mkdirSync(path.join(HERE, "node", "bin"), { recursive: true });
	if (WIN) {
		fs.copyFileSync(got, bin);
		await fetchTo("https://raw.githubusercontent.com/nodejs/node/" + NODE_VERSION + "/LICENSE", path.join(HERE, "node", "LICENSE"));
	} else {
		run("tar", ["-xzf", got, "-C", tmp]);
		const dir = path.join(tmp, path.basename(name, ".tar.gz"));
		fs.copyFileSync(path.join(dir, "bin", "node"), bin);
		fs.chmodSync(bin, 0o755);
		fs.copyFileSync(path.join(dir, "LICENSE"), path.join(HERE, "node", "LICENSE"));
	}
	fs.rmSync(tmp, { recursive: true, force: true });
}

/** The host's jars into the stage, SQLite's driver without its native libraries, this machine's beside them. */
function stageHost() {
	const from = path.join(HERE, "host", "build", "install", "lunade-host", "lib"), to = path.join(STAGE, "host");
	fs.mkdirSync(path.join(to, "lib"), { recursive: true });
	fs.mkdirSync(path.join(to, "native"), { recursive: true });
	const osName = WIN ? "Windows" : MAC ? "Mac" : "Linux", arch = process.arch === "arm64" ? "aarch64" : "x86_64";
	for (const jar of fs.readdirSync(from)) {
		if (!/^sqlite-jdbc-.*\.jar$/.test(jar)) { fs.copyFileSync(path.join(from, jar), path.join(to, "lib", jar)); continue; }
		const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "lunade-sqlite-"));
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

(async () => {
	if (!fs.existsSync(path.join(REPO, "AndroidLuna", "local-assets", "rootfs.index"))) {
		fail("AndroidLuna/local-assets isn't there: run AndroidLuna/fetch-assets.sh first (BUILDING.md)");
	}
	say("building the host");
	run(path.join(REPO, WIN ? "gradlew.bat" : "gradlew"), ["--console=plain", "-q", ":DesktopLuna:host:installDist"], { cwd: REPO, shell: WIN });
	await node12();
	fs.rmSync(STAGE, { recursive: true, force: true });
	stageHost();
	say("a Java runtime of " + JAVA_MODULES.length + " modules (jlink)");
	run(jdkTool("jlink"), ["--add-modules", JAVA_MODULES.join(","), "--strip-debug", "--no-man-pages", "--no-header-files", "--compress=2", "--output", path.join(STAGE, "jre")]);

	const builder = require("electron-builder");
	const version = require("./package.json").version;
	const repo = (p) => path.join(REPO, p);
	await builder.build({
		config: {
			appId: "org.webosarchive.lunade",
			productName: "Luna DE",
			copyright: "webOS Archive; GPL-3.0-or-later. Bundled webOS material ships as noted in its NOTICE files.",
			directories: { output: "dist", buildResources: "build-resources" },
			files: ["app/**", "package.json"],
			// The same folders at the same places as in a checkout (main.js's REPO).
			extraResources: [
				{ from: repo("AndroidLuna/src/main/assets"), to: "AndroidLuna/src/main/assets" },
				{ from: repo("AndroidLuna/local-assets"), to: "AndroidLuna/local-assets", filter: ["**/*", "!pdk/**"] },
				{ from: repo("CoreApps"), to: "CoreApps" },
				{ from: repo("LunaKeyboard/src/main/assets/keyboard"), to: "LunaKeyboard/src/main/assets/keyboard" },
				{ from: path.join(HERE, "node"), to: "DesktopLuna/node" },
				{ from: path.join(STAGE, "host"), to: "host" },
				{ from: path.join(STAGE, "jre"), to: "jre" },
				{ from: repo("LICENSE"), to: "LICENSE.Lunacy" },
				{ from: repo("NOTICE"), to: "NOTICE.Lunacy" },
			].filter((r) => fs.existsSync(r.from)),
			artifactName: "LunaDE-${version}-${os}-${arch}.${ext}",
			// The icon: Lunacy's moon (build-resources/make-icon.py).
			linux: { target: ["AppImage", "tar.gz"], category: "Development", executableName: "lunade", icon: "build-resources/icon.png" },
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
