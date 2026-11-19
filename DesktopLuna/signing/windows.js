// Windows code signing for Luna DE's package, from whatever machine builds it (package.js).
//
// Luna DE's Windows package is made on Linux or a Mac (`npm run dist -- --win`), where Windows'
// own signtool.exe can't run. jsign (https://github.com/ebourg/jsign) writes the same Authenticode
// signature from any JVM, and the build has a JDK already. It signs with whichever key the
// builder has; set the variables in the environment or in signing/set-windows-vars.sh
// (git-ignored; signing/set-windows-vars.example is the template):
//
//   a code-signing certificate in a .pfx/.p12 file:
//     LUNA_DE_WIN_CERT (its path), LUNA_DE_WIN_CERT_PASSWORD, and LUNA_DE_WIN_TSA for another
//     timestamp server than DigiCert's;
//   Azure Trusted Signing (Microsoft's managed signing service), with the same six variables
//   electron-builder's and signtool's Azure setups use:
//     AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TRUSTED_SIGNING_ENDPOINT,
//     AZURE_TRUSTED_SIGNING_ACCOUNT, AZURE_TRUSTED_SIGNING_PROFILE. The app registration needs
//     the "Trusted Signing Certificate Profile Signer" role on the signing account.
//
// Neither: the package is unsigned and the build says so. Only some of Azure's: the build stops,
// rather than ship half-signed.
//
// **Deep.** electron-builder signs the app's exe and what is in resources/app.asar.unpacked, and
// nothing else in resources/; Luna DE's Java runtime, node.exe and SQLite's DLL sit there, and
// Windows 11's Smart App Control checks every exe and DLL a program loads. So deepSign() (from
// package.js's afterPack, before anything is zipped or packed into the installer) signs every
// .exe and .dll in the app, and electron-builder's hook (sign()) signs the app's exe after it has
// written its icon and version into it, then the installer and uninstaller. A file that already
// carries a valid, timestamped signature (node.exe, the OpenJS Foundation's) keeps it rather
// than have its maker's name replaced by Luna DE's. electron-builder only warns when a hook
// fails, so verify() reads every signature back at the end and fails the build on any gap.
"use strict";
const fs = require("fs"), path = require("path"), https = require("https"), crypto = require("crypto");
const { execFileSync } = require("child_process");

const JSIGN = { version: "7.4", sha256: "2abf2ade9ea322acc2d60c24794eadc465ff9380938fca4c932d09e0b25f1c28" };
const AZURE = ["AZURE_TENANT_ID", "AZURE_CLIENT_ID", "AZURE_CLIENT_SECRET", "AZURE_TRUSTED_SIGNING_ENDPOINT",
	"AZURE_TRUSTED_SIGNING_ACCOUNT", "AZURE_TRUSTED_SIGNING_PROFILE"];
const CHECK = path.join(__dirname, "AuthenticodeCheck.java");
const SIGNABLE = /\.(exe|dll)$/i;

let java = null, jar = null, token = null;
const say = (m) => console.log("package: windows signing: " + m);

/** How the package will be signed: null (unsigned), or { kind, describe }. Throws when Azure's set is incomplete. */
function key(env = process.env) {
	const azure = AZURE.filter((v) => env[v]);
	if (azure.length && azure.length < AZURE.length) {
		throw new Error("Windows signing is half-configured: missing " + AZURE.filter((v) => !env[v]).join(", "));
	}
	if (azure.length) {
		return { kind: "azure", describe: "Azure Trusted Signing (" + env.AZURE_TRUSTED_SIGNING_ACCOUNT + "/" + env.AZURE_TRUSTED_SIGNING_PROFILE + ")" };
	}
	if (env.LUNA_DE_WIN_CERT) {
		if (!fs.existsSync(env.LUNA_DE_WIN_CERT)) { throw new Error("LUNA_DE_WIN_CERT names no file: " + env.LUNA_DE_WIN_CERT); }
		return { kind: "pfx", describe: "the certificate in " + env.LUNA_DE_WIN_CERT };
	}
	return null;
}

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

/** jsign into the build's cache (checked against its pinned checksum), and the Java that runs it. */
async function prepare(javaBin, cache) {
	java = javaBin;
	jar = path.join(cache, "jsign-" + JSIGN.version + ".jar");
	if (fs.existsSync(jar) && sha256(jar) === JSIGN.sha256) { return; }
	fs.mkdirSync(cache, { recursive: true });
	const url = "https://repo1.maven.org/maven2/net/jsign/jsign/" + JSIGN.version + "/jsign-" + JSIGN.version + ".jar";
	say(url);
	await fetchTo(url, jar);
	if (sha256(jar) !== JSIGN.sha256) { fs.rmSync(jar, { force: true }); throw new Error("the jsign download doesn't match its pinned checksum"); }
}

/** An Azure access token for Trusted Signing (the app registration's client credentials), renewed before it lapses. */
async function azureToken() {
	if (token && token.until > Date.now()) { return token.value; }
	const env = process.env;
	const body = new URLSearchParams({ grant_type: "client_credentials", client_id: env.AZURE_CLIENT_ID,
		client_secret: env.AZURE_CLIENT_SECRET, scope: "https://codesigning.azure.net/.default" }).toString();
	const answer = await new Promise((resolve, reject) => {
		const req = https.request({ method: "POST", host: "login.microsoftonline.com", path: "/" + encodeURIComponent(env.AZURE_TENANT_ID) + "/oauth2/v2.0/token",
			headers: { "Content-Type": "application/x-www-form-urlencoded", "Content-Length": Buffer.byteLength(body) } }, (res) => {
			let text = "";
			res.on("data", (d) => { text += d; });
			res.on("end", () => { try { resolve(JSON.parse(text)); } catch (e) { reject(new Error("Azure's sign-in answered " + res.statusCode)); } });
		});
		req.on("error", reject);
		req.end(body);
	});
	if (!answer.access_token) { throw new Error("Azure sign-in failed: " + (answer.error_description || answer.error || "no token")); }
	token = { value: answer.access_token, until: Date.now() + (Number(answer.expires_in || 3600) - 300) * 1000 };
	return token.value;
}

/** Signs files with Luna DE's key, replacing any signature they had. */
async function signFiles(files) {
	const k = key();
	if (!k || !files.length) { return; }
	if (!jar) { throw new Error("windows signing used before prepare()"); }
	const env = process.env;
	let args, secret;
	if (k.kind === "azure") {
		secret = await azureToken();
		args = ["--storetype", "TRUSTEDSIGNING", "--keystore", env.AZURE_TRUSTED_SIGNING_ENDPOINT.replace(/^https?:\/\//, "").replace(/\/+$/, ""),
			"--alias", env.AZURE_TRUSTED_SIGNING_ACCOUNT + "/" + env.AZURE_TRUSTED_SIGNING_PROFILE, "--tsaurl", "http://timestamp.acs.microsoft.com"];
	} else {
		secret = env.LUNA_DE_WIN_CERT_PASSWORD || "";
		args = ["--storetype", "PKCS12", "--keystore", env.LUNA_DE_WIN_CERT, "--tsaurl", env.LUNA_DE_WIN_TSA || "http://timestamp.digicert.com"];
	}
	// The secret goes to jsign through its environment (env:), never on a command line others can read.
	args.push("--storepass", "env:LUNA_DE_JSIGN_SECRET", "--alg", "SHA-256", "--tsmode", "RFC3161", "--tsretries", "3",
		"--name", "Luna DE", "--url", "https://github.com/webOSArchive/Lunacy", "--replace", "--quiet");
	for (let i = 0; i < files.length; i += 40) {
		const batch = files.slice(i, i + 40);
		try {
			execFileSync(java, ["-jar", jar, ...args, ...batch], { stdio: "pipe", encoding: "utf8", env: Object.assign({}, env, { LUNA_DE_JSIGN_SECRET: secret }) });
		} catch (e) {
			throw new Error("jsign failed (exit " + e.status + ") on " + batch.map((f) => path.basename(f)).join(", ") + "\n" + [e.stdout, e.stderr].filter(Boolean).join("\n").trim());
		}
	}
}

/** Each file's signature as AuthenticodeCheck.java reads it: { file, valid, timestamped, subject, problem }. */
function check(files) {
	const out = [];
	for (let i = 0; i < files.length; i += 200) {
		const text = execFileSync(java, ["-cp", jar, CHECK, ...files.slice(i, i + 200)], { encoding: "utf8", maxBuffer: 64 << 20 });
		text.split("\n").filter(Boolean).forEach((l) => out.push(JSON.parse(l)));
	}
	return out;
}
const sound = (r) => r.valid && r.timestamped;

function signableIn(dir) {
	const found = [];
	(function walk(d) {
		for (const e of fs.readdirSync(d, { withFileTypes: true })) {
			const p = path.join(d, e.name);
			if (e.isDirectory()) { walk(p); } else if (e.isFile() && SIGNABLE.test(e.name)) { found.push(p); }
		}
	})(dir);
	return found;
}

/**
 * Every .exe and .dll in the unpacked app but the app's own exe (electron-builder signs that after
 * writing its resources): those without a sound signature get Luna DE's. Throws on any left unsound.
 */
async function deepSign(appOutDir, mainExe) {
	if (!key()) { say("no key: " + path.basename(appOutDir) + " is left unsigned"); return; }
	const files = signableIn(appOutDir).filter((f) => path.resolve(f) !== path.resolve(mainExe));
	const before = check(files);
	const kept = before.filter(sound), todo = before.filter((r) => !sound(r)).map((r) => r.file);
	say(files.length + " programs and libraries: " + kept.length + " keep their makers' signatures, " + todo.length + " to sign");
	await signFiles(todo);
	const bad = check(todo).filter((r) => !sound(r));
	if (bad.length) { throw new Error("signed, but not soundly: " + bad.map((r) => path.relative(appOutDir, r.file) + " (" + r.problem + ")").join("; ")); }
}

/**
 * electron-builder's hook (win.signtoolOptions.sign): the app's exe once its icon and version are
 * written, each .exe it copies in with the resources, the uninstaller and the installer. A file
 * whose signature is already sound keeps it (node.exe's maker's); the app's exe never has one by
 * then, since writing its resources breaks whatever it came with, nor do the installers.
 */
async function sign(configuration) {
	const name = path.basename(configuration.path);
	if (!key()) { console.log("  • not signed (no Windows key)  file=" + name); return; }
	if (sound(check([configuration.path])[0])) { console.log("  • keeps its maker's signature  file=" + name); return; }
	console.log("  • signing (jsign)  file=" + name);
	await signFiles([configuration.path]);
}

/**
 * Reads back every signature in the unpacked app and the installers. With a key: every file's
 * signature is valid and timestamped, and the app's exe and each installer are signed by the same
 * signer (Luna DE's). Without one: says what is unsigned. Throws on a gap.
 */
function verify(appOutDir, mainExe, installers) {
	if (!jar) { return; }
	const files = [...signableIn(appOutDir), ...installers];
	const results = check(files);
	const rel = (f) => installers.includes(f) ? path.basename(f) : path.relative(appOutDir, f);
	if (!key()) {
		const unsigned = results.filter((r) => !r.valid).length;
		say("unsigned package: " + unsigned + " of " + results.length + " programs and libraries carry no valid signature");
		return;
	}
	const ours = results.find((r) => path.resolve(r.file) === path.resolve(mainExe));
	const problems = results.filter((r) => !sound(r)).map((r) => rel(r.file) + ": " + (r.valid ? "no timestamp" : r.problem));
	if (!ours || !ours.valid) { problems.push(path.basename(mainExe) + ": not signed"); }
	else {
		for (const r of results.filter((x) => installers.includes(x.file) || path.resolve(x.file) === path.resolve(mainExe))) {
			if (r.subject !== ours.subject) { problems.push(rel(r.file) + ": signed by " + r.subject + ", not " + ours.subject); }
		}
	}
	if (problems.length) { throw new Error("Windows signatures don't hold:\n  " + problems.join("\n  ")); }
	const byUs = results.filter((r) => r.subject === ours.subject).length;
	say("verified " + results.length + " files: every one validly signed and timestamped; " + byUs + " by " + ours.subject + ", " +
		(results.length - byUs) + " by their makers");
}

module.exports = { key, prepare, deepSign, sign, verify };
