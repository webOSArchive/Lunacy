#!/usr/bin/env node
// Builds Luna DE's distributable, in one go, with nothing copied by hand (Docs/luna-de.md,
// "Packaging"):
//
//   cd DesktopLuna && npm install && npm run dist
//
// What a package carries, and where each comes from:
//   - the Electron app (app/), built by electron-builder;
//   - the JVM host and its libraries (./gradlew :DesktopLuna:host:installDist), with SQLite's
//     native library for the package's system taken out of its jar and put beside it, and the
//     others dropped (a Mac's notarisation reads inside jars, and refuses unsigned libraries there);
//   - a Java runtime of its own, cut with the JDK's jlink to the modules the host uses, so a
//     user needs no Java;
//   - the Node 12 that runs webOS JS services (nodejs.org's build, checked against its checksum);
//   - Lunacy's assets, at the places they have in a checkout: AndroidLuna/src/main/assets,
//     AndroidLuna/local-assets (what fetch-assets.sh fetched and patched; not its PDK part,
//     which Luna DE doesn't run), CoreApps and the keyboard's art.
//
// What it makes, by where it runs:
//   - on a Mac: a dmg and a zip for Apple Silicon (arm64) and for Intel (x64), signed with the
//     Developer ID in the keychain and notarised when Apple's credentials are set
//     (signing/mac.js; signing/set-apple-vars.example);
//   - on Linux: an AppImage and a tar.gz for this machine's processor;
//   - with --win, on Linux or a Mac: Windows x64, a zip (and the installer where Wine is
//     installed), signed when a Windows key is set (signing/windows.js;
//     signing/set-windows-vars.example). Nothing in it is compiled for Windows here: its native
//     parts are fetched (node.exe; the Windows modules of this JDK's version, from Adoptium,
//     which jlink cuts a Windows runtime from) or taken from a jar (SQLite's .dll).
//   --arch x64,arm64 names the processors outright. Another processor's or system's Java runtime is
//   cut from Adoptium's modules for it, as Windows' is.
//   --release refuses to finish a package that isn't signed (and, on a Mac, notarised): what is
//   handed to people. Without it an unsigned build is allowed, and says what it lacks.
//
// After building, every signature is read back (signing/*.js verify) and a gap fails the build.
// Output: out/ at the top of the repository (git-ignored).
"use strict";
const fs = require("fs"), path = require("path"), os = require("os"), https = require("https"), crypto = require("crypto");
const { execFileSync } = require("child_process");

const HERE = __dirname, REPO = path.resolve(HERE, "..");
const ARGS = process.argv.slice(2);
const opt = (name) => { const i = ARGS.indexOf(name); return i >= 0 ? ARGS[i + 1] : undefined; };
const HOST_OS = process.platform === "win32" ? "win" : process.platform === "darwin" ? "mac" : "linux";
// What the package is for: this machine's system, or Windows with --win.
const OS = ARGS.includes("--win") ? "win" : HOST_OS;
const WIN = OS === "win", MAC = OS === "mac";
const ARCHES = opt("--arch") ? opt("--arch").split(",") : MAC ? ["arm64", "x64"] : WIN ? ["x64"] : [process.arch];
const RELEASE = ARGS.includes("--release");
const CACHE = path.join(HERE, "build", "cache");
const STAGE = path.join(HERE, "build", "package");
// Where the packages go: out/ at the top of the repository.
const OUT = path.join(REPO, "out");
const NODE_VERSION = "v12.22.12";
// What the host and its libraries use of the JDK (java.desktop for ImageIO, jdk.crypto.ec for
// TLS, jdk.httpserver for the host's own server, jdk.unsupported for SQLite's driver).
const JAVA_MODULES = ["java.base", "java.sql", "java.desktop", "java.logging", "java.naming", "java.net.http", "java.xml",
	"java.management", "java.security.jgss", "jdk.httpserver", "jdk.crypto.ec", "jdk.unsupported", "jdk.charsets", "jdk.zipfs"];

const say = (m) => console.log("package: " + m);
const fail = (m) => { console.error("package: " + m); process.exit(1); };
const run = (cmd, args, opts) => execFileSync(cmd, args, Object.assign({ stdio: "inherit" }, opts));

for (const a of ARCHES) { if (a !== "x64" && a !== "arm64") { fail("--arch: x64 or arm64, not " + a); } }
if (WIN && ARCHES.some((a) => a !== "x64")) { fail("Windows packages are x64 only: Node 12 has no Windows build for arm64"); }

/**
 * The signing credentials kept beside the scripts that use them (git-ignored): `export NAME="value"`
 * lines, as a shell would source them. What the environment already has wins.
 */
function loadVars(file) {
	if (!fs.existsSync(file)) { return; }
	for (const line of fs.readFileSync(file, "utf8").split("\n")) {
		const m = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*?)\s*$/.exec(line);
		if (!m || line.trim().startsWith("#")) { continue; }
		let v = m[2];
		if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) { v = v.slice(1, -1); }
		if (process.env[m[1]] === undefined) { process.env[m[1]] = v; }
	}
	say("credentials from " + path.relative(HERE, file));
}
loadVars(path.join(HERE, "signing", MAC ? "set-apple-vars.sh" : "set-windows-vars.sh"));
const macSigning = require("./signing/mac.js"), winSigning = require("./signing/windows.js");

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
		". Install one (it needn't be the default" + (HOST_OS === "mac" ? "; brew install --cask temurin@17" : "") + "), or set LUNA_DE_JDK to its folder.");
}
/** One of that JDK's programs, by name. */
function jdkTool(name) { return path.join(JDK.home, "bin", name + (HOST_OS === "win" ? ".exe" : "")); }

/** Whether a package for arch is for this very machine (its own Node, its own JDK's modules). */
const native = (arch) => OS === HOST_OS && arch === process.arch;

/**
 * Node 12 for the package. This machine's own goes in DesktopLuna/node (which `npm start` uses);
 * another system's or processor's is kept apart, in the stage. nodejs.org built Node 12 for x64 on
 * all three systems and for arm64 on Linux only, so a Mac's arm64 package carries the x64 Node
 * (Apple Silicon runs it under Rosetta; Docs/luna-de.md, open question 6).
 */
async function node12(arch, stage) {
	const nodeArch = OS === "linux" ? arch : "x64";
	const own = OS === HOST_OS && nodeArch === process.arch;
	const home = own ? path.join(HERE, "node") : path.join(stage, "node");
	const bin = path.join(home, "bin", WIN ? "node.exe" : "node");
	if (own) { try { if (execFileSync(bin, ["--version"]).toString().trim() === NODE_VERSION) { say("node/ already holds Node " + NODE_VERSION); return home; } } catch (e) {} }
	const base = "https://nodejs.org/dist/" + NODE_VERSION + "/";
	const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "luna-de-node-"));
	const name = WIN ? "win-" + nodeArch + "/node.exe" : "node-" + NODE_VERSION + "-" + (MAC ? "darwin" : "linux") + "-" + nodeArch + ".tar.gz";
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

/** The host's jars into the stage, SQLite's driver without its native libraries, the package's beside them. */
function stageHost(arch, stage) {
	const from = path.join(HERE, "host", "build", "install", "luna-de-host", "lib"), to = path.join(stage, "host");
	fs.mkdirSync(path.join(to, "lib"), { recursive: true });
	fs.mkdirSync(path.join(to, "native"), { recursive: true });
	const osName = WIN ? "Windows" : MAC ? "Mac" : "Linux", jarArch = arch === "arm64" ? "aarch64" : "x86_64";
	for (const jar of fs.readdirSync(from)) {
		if (!/^sqlite-jdbc-.*\.jar$/.test(jar)) { fs.copyFileSync(path.join(from, jar), path.join(to, "lib", jar)); continue; }
		const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "luna-de-sqlite-"));
		run(jdkTool("jar"), ["xf", path.join(from, jar)], { cwd: tmp });
		const lib = path.join(tmp, "org", "sqlite", "native", osName, jarArch);
		const libs = fs.existsSync(lib) ? fs.readdirSync(lib) : [];
		if (libs.length !== 1) { fail("SQLite's driver has no native library for " + osName + " " + jarArch); }
		fs.copyFileSync(path.join(lib, libs[0]), path.join(to, "native", libs[0]));
		fs.rmSync(path.join(tmp, "org", "sqlite", "native"), { recursive: true, force: true });
		run(jdkTool("jar"), ["--create", "--file", path.join(to, "lib", jar), "--manifest", path.join("META-INF", "MANIFEST.MF"), "-C", tmp, "."], { cwd: tmp, stdio: ["ignore", "ignore", "inherit"] });
		fs.rmSync(tmp, { recursive: true, force: true });
		say("host: " + jar + " without its native libraries; " + libs[0] + " (" + osName + " " + jarArch + ") beside it");
	}
}

/**
 * Another system's or processor's JDK modules, for jlink to cut that runtime from: Eclipse
 * Temurin's build from Adoptium, checked against the checksum published beside it, kept in
 * build/cache. jlink insists the modules' feature version match its own (17 with 17), so the
 * same release as this JDK is asked for first, then Temurin's latest of that feature version
 * (a Homebrew JDK's release, "17.0.21+0", is no Temurin release).
 */
async function foreignJmods(arch) {
	const shown = require("child_process").spawnSync(jdkTool("java"), ["-XshowSettings:properties", "-version"], { encoding: "utf8" });
	const runtime = (/java\.runtime\.version = (\S+)/.exec(String(shown.stderr) + String(shown.stdout)) || [])[1];
	const m = /^((\d+)(?:\.\d+)*)\+(\d+)/.exec(runtime || "");
	if (!m) { fail("can't tell this JDK's version (java.runtime.version: " + runtime + ")"); }
	const adoptOs = WIN ? "windows" : MAC ? "mac" : "linux", adoptArch = arch === "arm64" ? "aarch64" : "x64";
	const query = "?architecture=" + adoptArch + "&os=" + adoptOs + "&image_type=jdk";
	fs.mkdirSync(CACHE, { recursive: true });
	const meta = path.join(CACHE, "jdk-" + adoptOs + "-" + adoptArch + ".json");
	let pkg, release;
	try {
		release = "jdk-" + m[1] + "+" + m[3];
		await fetchTo("https://api.adoptium.net/v3/assets/release_name/eclipse/" + encodeURIComponent(release) + query, meta);
		pkg = JSON.parse(fs.readFileSync(meta, "utf8")).binaries[0].package;
	} catch (e) {
		await fetchTo("https://api.adoptium.net/v3/assets/latest/" + m[2] + "/hotspot" + query + "&vendor=eclipse", meta)
			.catch((e2) => fail("Adoptium has no " + adoptOs + " " + adoptArch + " JDK " + m[2] + ": " + e2.message));
		const latest = JSON.parse(fs.readFileSync(meta, "utf8"))[0];
		if (!latest) { fail("Adoptium has no " + adoptOs + " " + adoptArch + " JDK " + m[2]); }
		pkg = latest.binary.package;
		release = latest.release_name;
	}
	const dir = path.join(CACHE, adoptOs + "-" + adoptArch + "-" + release);
	if (fs.existsSync(path.join(dir, "jmods", "java.base.jmod"))) { say(adoptOs + " " + adoptArch + " JDK modules: " + release + " (cached)"); return path.join(dir, "jmods"); }
	const archive = path.join(CACHE, pkg.name);
	say(pkg.link);
	await fetchTo(pkg.link, archive);
	if (sha256(archive) !== pkg.checksum) { fail("the " + adoptOs + " JDK download doesn't match Adoptium's checksum"); }
	const tmp = fs.mkdtempSync(path.join(CACHE, "unpack-"));
	if (pkg.name.endsWith(".zip")) { run("unzip", ["-q", archive, "*/jmods/*", "-d", tmp]); } else { run("tar", ["-xzf", archive, "-C", tmp]); }
	// Where java.base.jmod is: <jdk>/jmods, or a Mac's <jdk>/Contents/Home/jmods.
	const found = (function find(d) {
		if (fs.existsSync(path.join(d, "jmods", "java.base.jmod"))) { return d; }
		for (const e of fs.readdirSync(d, { withFileTypes: true })) { if (e.isDirectory()) { const f = find(path.join(d, e.name)); if (f) { return f; } } }
		return null;
	})(tmp);
	if (!found) { fail("no jmods in " + pkg.name); }
	fs.rmSync(dir, { recursive: true, force: true });
	fs.mkdirSync(dir, { recursive: true });
	fs.renameSync(path.join(found, "jmods"), path.join(dir, "jmods"));
	fs.rmSync(tmp, { recursive: true, force: true });
	fs.rmSync(archive, { force: true });
	say(adoptOs + " " + adoptArch + " JDK modules: " + release);
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

/** What signing this package will get, said before the long build starts; with --release, a gap stops it here. */
function signingPlan() {
	if (MAC) {
		const r = macSigning.readiness();
		if (r.identity && r.notarize) { say("macOS: signed with the Developer ID, and notarised"); }
		else if (r.identity) { say("macOS: signed, NOT notarised: a downloaded copy will be blocked by Gatekeeper. Set APPLE_ID, APPLE_APP_SPECIFIC_PASSWORD and APPLE_TEAM_ID (signing/set-apple-vars.sh) to notarise"); }
		else {
			say("macOS: UNSIGNED: no \"Developer ID Application\" certificate in the keychain; Apple Silicon users will be told the app is damaged");
			// electron-builder would otherwise look for an identity and stop on what it finds instead.
			process.env.CSC_IDENTITY_AUTO_DISCOVERY = "false";
		}
		if (RELEASE && !(r.identity && r.notarize)) { fail("--release: a Mac package must be signed and notarised"); }
		return { signed: r.identity, notarized: r.identity && r.notarize };
	}
	if (WIN) {
		let k;
		try { k = winSigning.key(); } catch (e) { fail(e.message); }
		say("Windows: " + (k ? "signed with " + k.describe + ", every program and library in it" : "UNSIGNED: no Windows key (signing/set-windows-vars.example says how to give one); SmartScreen and Smart App Control will warn or block"));
		if (RELEASE && !k) { fail("--release: a Windows package must be signed"); }
		return { signed: !!k };
	}
	say("Linux: packages aren't signed");
	return {};
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
	const plan = signingPlan();
	say("JDK " + JDK.major + ": " + JDK.home);
	if (WIN) { await winSigning.prepare(jdkTool("java"), CACHE); }
	// --configure-on-demand: only the host and LunaCore are set up, not the Android projects, so a
	// machine building Luna DE needs no Android SDK.
	say("building the host");
	run(path.join(REPO, HOST_OS === "win" ? "gradlew.bat" : "gradlew"), ["--console=plain", "-q", "--configure-on-demand", ":DesktopLuna:host:installDist"], { cwd: REPO, shell: HOST_OS === "win", env: Object.assign({}, process.env, { JAVA_HOME: JDK.home }) });
	fs.rmSync(STAGE, { recursive: true, force: true });
	// Windows keeps no symbolic links in a package: CoreApps' few are followed.
	const coreApps = WIN ? withoutLinks(path.join(REPO, "CoreApps"), path.join(STAGE, "CoreApps")) : path.join(REPO, "CoreApps");

	const builder = require("electron-builder");
	const version = require("./package.json").version;
	const repo = (p) => path.join(REPO, p);
	// Off Windows, the installer's own maker needs Wine (it runs a Windows program to write the
	// uninstaller); without Wine the package is the zip alone, which is the whole app in a folder.
	let winTargets = ["nsis", "zip"];
	if (WIN && HOST_OS !== "win") {
		try { execFileSync("wine", ["--version"], { stdio: "ignore" }); }
		catch (e) { winTargets = ["zip"]; say("no Wine here, so no installer: the zip only (build on Windows, or install Wine, for the installer)"); }
	}
	const built = [];
	for (const arch of ARCHES) {
		const stage = path.join(STAGE, OS + "-" + arch);
		fs.mkdirSync(stage, { recursive: true });
		say("— " + OS + " " + arch + " —");
		const nodeHome = await node12(arch, stage);
		stageHost(arch, stage);
		say("a Java runtime of " + JAVA_MODULES.length + " modules (jlink)" + (native(arch) ? "" : ", for " + OS + " " + arch));
		const modulePath = native(arch) ? [] : ["--module-path", await foreignJmods(arch)];
		run(jdkTool("jlink"), [...modulePath, "--add-modules", JAVA_MODULES.join(","), "--strip-debug", "--no-man-pages", "--no-header-files", "--compress=2", "--output", path.join(stage, "jre")]);

		const platform = WIN ? builder.Platform.WINDOWS : MAC ? builder.Platform.MAC : builder.Platform.LINUX;
		const targetNames = WIN ? winTargets : MAC ? ["dmg", "zip"] : ["AppImage", "tar.gz"];
		await builder.build({
			targets: platform.createTarget(targetNames, builder.Arch[arch]),
			publish: "never",
			config: {
				appId: "org.webosarchive.luna-de",
				productName: "Luna DE",
				copyright: "webOS Archive; GPL-3.0-or-later. Bundled webOS material ships as noted in its NOTICE files.",
				directories: { output: OUT, buildResources: "build-resources" },
				files: ["app/**", "package.json"],
				// The same folders at the same places as in a checkout (main.js's REPO).
				extraResources: [
					{ from: repo("AndroidLuna/src/main/assets"), to: "AndroidLuna/src/main/assets" },
					{ from: repo("AndroidLuna/local-assets"), to: "AndroidLuna/local-assets", filter: ["**/*", "!pdk/**"] },
					{ from: coreApps, to: "CoreApps" },
					{ from: repo("LunaKeyboard/src/main/assets/keyboard"), to: "LunaKeyboard/src/main/assets/keyboard" },
					{ from: nodeHome, to: "DesktopLuna/node" },
					{ from: path.join(stage, "host"), to: "host" },
					{ from: path.join(stage, "jre"), to: "jre" },
					{ from: repo("LICENSE"), to: "LICENSE.Lunacy" },
					{ from: repo("NOTICE"), to: "NOTICE.Lunacy" },
				].filter((r) => fs.existsSync(r.from)),
				artifactName: "luna-de-${version}-${os}-${arch}.${ext}",
				// No auto-update: no update files beside the packages.
				publish: null,
				// The icon: Lunacy's moon (build-resources/make-icon.py).
				linux: { target: ["AppImage", "tar.gz"], category: "Development", executableName: "luna-de", icon: "build-resources/icon.png" },
				// Signed with the Developer ID in the keychain (or CSC_LINK), under the hardened
				// runtime, which needs the JVM's and Node's entitlements. Notarised by
				// signing/mac.js's afterSign, not electron-builder's own (notarize: false), so that
				// it is skipped, and said to be, where Apple's credentials aren't set.
				// signIgnore: the webOS assets (about 11,800 images, scripts and pages) hold no
				// programs, and codesign would otherwise sign each one, with a timestamp from Apple
				// apiece: a quarter of an hour an app. They are sealed by the app's own signature
				// all the same, and signing/mac.js's verify fails the build if a Mach-O there is
				// ever left unsigned.
				mac: { icon: "build-resources/icon-mac.png", category: "public.app-category.developer-tools", hardenedRuntime: true, gatekeeperAssess: false,
					entitlements: "build-resources/entitlements.mac.plist", entitlementsInherit: "build-resources/entitlements.mac.plist", notarize: false,
					signIgnore: ["/Contents/Resources/(AndroidLuna|CoreApps|LunaKeyboard)/"] },
				dmg: { writeUpdateInfo: false },
				afterSign: macSigning.notarize,
				// Signed by signing/windows.js with jsign, from any build machine: the app's exe,
				// the uninstaller and installer through this hook, and every other program and
				// library in the app by afterPack below.
				win: { icon: "build-resources/icon.png", signtoolOptions: { sign: winSigning.sign, signingHashAlgorithms: ["sha256"] } },
				nsis: { oneClick: false, allowToChangeInstallationDirectory: true, differentialPackage: false },
				afterPack: async (ctx) => {
					// Windows: every .exe and .dll signed before anything is packed (signing/windows.js).
					if (ctx.electronPlatformName === "win32") {
						await winSigning.deepSign(ctx.appOutDir, path.join(ctx.appOutDir, ctx.packager.appInfo.productFilename + ".exe"));
						return;
					}
					// Linux: a package can't count on Chromium's sandbox helper (an AppImage or a
					// tarball can't carry a setuid-root file, and newer kernels' settings refuse the
					// other kind; Chromium then stops before any of the app's own code runs), so the
					// program people start is a script that starts the real one as `npm start` does,
					// with --no-sandbox.
					if (ctx.electronPlatformName !== "linux") { return; }
					const exe = path.join(ctx.appOutDir, ctx.packager.executableName), real = exe + ".bin";
					fs.renameSync(exe, real);
					fs.writeFileSync(exe, '#!/bin/sh\n# Luna DE (DesktopLuna/package.js): Chromium without its sandbox helper, which a package cannot install.\nexec "$(dirname "$(readlink -f "$0")")/' + path.basename(real) + '" --no-sandbox "$@"\n');
					fs.chmodSync(exe, 0o755);
				},
			},
		});
		built.push(arch);
	}

	// Every signature read back, as a downloader would meet the package.
	const dist = OUT;
	if (MAC) {
		for (const arch of built) {
			macSigning.verify(path.join(dist, arch === "x64" ? "mac" : "mac-" + arch, "Luna DE.app"), plan);
		}
	}
	if (WIN) {
		const installers = fs.readdirSync(dist).filter((n) => n.startsWith("luna-de-" + version + "-win") && n.endsWith(".exe")).map((n) => path.join(dist, n));
		const unpacked = path.join(dist, "win-unpacked");
		winSigning.verify(unpacked, path.join(unpacked, "Luna DE.exe"), installers);
	}
	// No auto-update, so no update files: electron-builder writes a Mac zip's blockmap whatever it is told.
	for (const n of fs.readdirSync(dist).filter((n) => n.endsWith(".blockmap"))) { fs.rmSync(path.join(dist, n)); }
	say("done: out/ (Luna DE " + version + ")");
	for (const n of fs.readdirSync(dist).filter((n) => n.startsWith("luna-de-" + version + "-"))) {
		say("  " + n + "  " + (fs.statSync(path.join(dist, n)).size / 1048576).toFixed(0) + " MB");
	}
})().catch((e) => fail(e && e.stack || String(e)));
