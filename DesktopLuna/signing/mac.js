// macOS signing and notarisation for Luna DE's package (package.js).
//
// Signing: electron-builder signs every program and library in the app (the Java runtime's, Node's
// and SQLite's among them) with the "Developer ID Application" identity in the keychain, under
// the hardened runtime with build-resources/entitlements.mac.plist.
//
// Notarisation: Apple's notary service checks the signed app, and its ticket is stapled to it, so
// that a copy downloaded from anywhere opens without Gatekeeper's warning (or, on Apple Silicon,
// "is damaged"). It needs, in the environment or in signing/set-apple-vars.sh (git-ignored;
// signing/set-apple-vars.example is the template):
//
//   APPLE_ID                     the Apple ID's email
//   APPLE_APP_SPECIFIC_PASSWORD  appleid.apple.com → Sign-In and Security → App-Specific Passwords
//   APPLE_TEAM_ID                developer.apple.com/account, top right: the Developer ID's team
//
// Without them the app is signed and not notarised, and the build says so.
"use strict";
const fs = require("fs"), path = require("path");
const { execFileSync, spawnSync } = require("child_process");

const NOTARY = ["APPLE_ID", "APPLE_APP_SPECIFIC_PASSWORD", "APPLE_TEAM_ID"];
const say = (m) => console.log("package: mac signing: " + m);

/** What this machine can do: { identity: a Developer ID to sign with (or a CSC_LINK), notarize: Apple's three set }. */
function readiness(env = process.env) {
	const found = spawnSync("security", ["find-identity", "-v", "-p", "codesigning"], { encoding: "utf8" });
	const identity = !!env.CSC_LINK || /"Developer ID Application: /.test(String(found.stdout));
	return { identity, notarize: NOTARY.every((v) => env[v]) };
}

/** electron-builder's afterSign: notarise the signed app and staple its ticket, before the dmg and zip are made of it. */
async function notarize(context) {
	if (context.electronPlatformName !== "darwin") { return; }
	if (!NOTARY.every((v) => process.env[v])) {
		say("notarisation skipped: set " + NOTARY.join(", ") + " (signing/set-apple-vars.sh) to notarise");
		return;
	}
	const { notarize: submit } = require("@electron/notarize");
	const appPath = path.join(context.appOutDir, context.packager.appInfo.productFilename + ".app");
	say("notarising " + appPath + " (Apple's service: usually a few minutes)");
	await submit({ tool: "notarytool", appPath, appleId: process.env.APPLE_ID,
		appleIdPassword: process.env.APPLE_APP_SPECIFIC_PASSWORD, teamId: process.env.APPLE_TEAM_ID });
	say("stapling the ticket");
	execFileSync("xcrun", ["stapler", "staple", appPath], { stdio: "inherit" });
}

/** True for a Mach-O file (thin or universal), by its first bytes; a Java class shares the universal magic, so .class is left out. */
function isMachO(file) {
	if (file.endsWith(".class")) { return false; }
	let fd;
	try {
		fd = fs.openSync(file, "r");
		const b = Buffer.alloc(8);
		if (fs.readSync(fd, b, 0, 8, 0) < 8) { return false; }
		const magic = b.readUInt32BE(0);
		if (magic === 0xcffaedfe || magic === 0xcefaedfe || magic === 0xfeedfacf || magic === 0xfeedface) { return true; }
		return magic === 0xcafebabe && b.readUInt32BE(4) < 20;   // a universal binary's count of architectures
	} catch (e) { return false; } finally { if (fd !== undefined) { fs.closeSync(fd); } }
}

/** codesign's account of one file: its team, whether it runs under the hardened runtime, whether it is timestamped. */
function signatureOf(file) {
	const r = spawnSync("codesign", ["-dv", "--verbose=2", file], { encoding: "utf8" });
	const text = String(r.stderr) + String(r.stdout);
	return { signed: r.status === 0, team: (/TeamIdentifier=(\S+)/.exec(text) || [])[1], runtime: /flags=0x[0-9a-f]+\([^)]*runtime/.test(text),
		timestamped: /^Timestamp=/m.test(text) };
}

/**
 * The app as a downloader gets it. Signed: the bundle verifies (deep and strict), and every program
 * and library in it, Luna DE's resources included, carries the app's own team's signature with the
 * hardened runtime and a timestamp, as notarisation demands. Notarised: the ticket is stapled and
 * Gatekeeper accepts the app as notarised. Throws on a gap.
 */
function verify(appPath, { signed, notarized }) {
	const problems = [];
	const deep = spawnSync("codesign", ["--verify", "--deep", "--strict", appPath], { encoding: "utf8" });
	if (!signed) {
		say(path.basename(path.dirname(appPath)) + ": unsigned (no Developer ID in the keychain)");
		return;
	}
	if (deep.status !== 0) { problems.push("codesign --verify --deep --strict: " + String(deep.stderr).trim()); }
	const app = signatureOf(appPath);
	const files = [];
	(function walk(d) {
		for (const e of fs.readdirSync(d, { withFileTypes: true })) {
			const p = path.join(d, e.name);
			if (e.isDirectory()) { walk(p); } else if (e.isFile() && isMachO(p)) { files.push(p); }
		}
	})(path.join(appPath, "Contents"));
	for (const f of files) {
		const s = signatureOf(f), rel = path.relative(appPath, f);
		if (!s.signed) { problems.push(rel + ": unsigned"); continue; }
		if (s.team !== app.team) { problems.push(rel + ": signed by team " + s.team + ", not " + app.team); }
		if (!s.runtime) { problems.push(rel + ": not under the hardened runtime"); }
		if (!s.timestamped) { problems.push(rel + ": no secure timestamp"); }
	}
	if (notarized) {
		const stapled = spawnSync("xcrun", ["stapler", "validate", appPath], { encoding: "utf8" });
		if (stapled.status !== 0) { problems.push("no stapled notarisation ticket: " + String(stapled.stdout + stapled.stderr).trim()); }
		const gk = spawnSync("spctl", ["--assess", "--type", "execute", "-vv", appPath], { encoding: "utf8" });
		const said = String(gk.stdout) + String(gk.stderr);
		if (gk.status !== 0 || !/source=Notarized Developer ID/.test(said)) { problems.push("Gatekeeper: " + said.trim()); }
	}
	if (problems.length) { throw new Error(appPath + ":\n  " + problems.join("\n  ")); }
	say(path.basename(path.dirname(appPath)) + ": " + files.length + " programs and libraries signed by team " + app.team +
		", hardened and timestamped" + (notarized ? "; notarised, stapled, and accepted by Gatekeeper" : "; not notarised"));
}

module.exports = { readiness, notarize, verify };
