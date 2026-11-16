// Finds the JDK Luna DE is built and run with, without asking anyone to change their machine's
// default Java (codepoet's Mac keeps Java 8 as its default, with 17 installed beside it).
// Gradle 8.9 runs on Java 17 to 22 and the host is built for 17, so that is what is looked for:
//
//   LUNA_DE_JDK, if set (a JDK's home folder);
//   JAVA_HOME, if it is one of those versions;
//   on a Mac, what /usr/libexec/java_home knows of; elsewhere, the usual folders JDKs install to;
//   last, whatever `java` the PATH has.
//
// 17 is preferred, then the newest of the others. Used by package.js (which needs the JDK's jlink
// and jar too) and by main.js when it starts the host from a checkout; a packaged Luna DE carries
// its own runtime and never comes here.
"use strict";
const fs = require("fs"), path = require("path"), os = require("os");
const { spawnSync } = require("child_process");
const WIN = process.platform === "win32", MAC = process.platform === "darwin";
const EXE = WIN ? ".exe" : "";
const MIN = 17, MAX = 22;

/** The major version of the JDK at home (8 for "1.8"), or 0 if there is no java there. */
function majorOf(home) {
	const java = path.join(home, "bin", "java" + EXE);
	if (!fs.existsSync(java)) { return 0; }
	const r = spawnSync(java, ["-version"], { encoding: "utf8" });
	const m = /version "(\d+)(?:\.(\d+))?/.exec(String(r.stderr) + String(r.stdout));
	return !m ? 0 : m[1] === "1" ? Number(m[2]) : Number(m[1]);
}
const folders = (dir) => { try { return fs.readdirSync(dir).map((n) => path.join(dir, n)); } catch (e) { return []; } };

function candidates() {
	const list = [];
	if (process.env.LUNA_DE_JDK) { list.push(process.env.LUNA_DE_JDK); }
	if (process.env.JAVA_HOME) { list.push(process.env.JAVA_HOME); }
	if (MAC) {
		for (let v = MIN; v <= MAX; v++) {
			const r = spawnSync("/usr/libexec/java_home", ["-v", String(v)], { encoding: "utf8" });
			if (r.status === 0 && r.stdout.trim()) { list.push(r.stdout.trim()); }
		}
		for (const d of [...folders("/Library/Java/JavaVirtualMachines"), ...folders(path.join(os.homedir(), "Library/Java/JavaVirtualMachines")),
			...folders("/opt/homebrew/opt").filter((p) => /openjdk/.test(p)), ...folders("/usr/local/opt").filter((p) => /openjdk/.test(p))]) {
			list.push(path.join(d, "Contents", "Home"), path.join(d, "libexec", "openjdk.jdk", "Contents", "Home"), d);
		}
	} else if (WIN) {
		for (const base of [process.env.ProgramFiles, process.env["ProgramFiles(x86)"], process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, "Programs")].filter(Boolean)) {
			for (const vendor of ["Eclipse Adoptium", "Java", "Amazon Corretto", "Microsoft", "Zulu", "BellSoft"]) { list.push(...folders(path.join(base, vendor))); }
		}
	} else {
		list.push(...folders("/usr/lib/jvm"), ...folders(path.join(os.homedir(), ".sdkman", "candidates", "java")), ...folders("/opt").filter((p) => /jdk|java/i.test(p)));
	}
	// Whatever `java` the PATH has: its home is two folders up from the program itself.
	for (const dir of (process.env.PATH || "").split(path.delimiter)) {
		const java = path.join(dir, "java" + EXE);
		try { if (fs.existsSync(java)) { list.push(path.dirname(path.dirname(fs.realpathSync(java)))); } } catch (e) {}
	}
	return list;
}

/**
 * The JDK to use: { home, major }, or null with `seen` saying what was looked at. needTools names
 * programs the JDK must also have ("jlink", "jar"): a bare runtime has java alone.
 */
function findJdk(needTools) {
	const seen = [], good = [];
	const tried = new Set();
	const explicit = process.env.LUNA_DE_JDK;
	for (const home of candidates()) {
		let real;
		try { real = fs.realpathSync(home); } catch (e) { continue; }
		if (tried.has(real)) { continue; }
		tried.add(real);
		const major = majorOf(real);
		if (!major) { continue; }
		const tools = (needTools || []).every((t) => fs.existsSync(path.join(real, "bin", t + EXE)));
		seen.push(real + " (Java " + major + (tools ? "" : ", a runtime without " + needTools.join("/")) + ")");
		if (major >= MIN && major <= MAX && tools) {
			if (explicit && home === explicit) { return { home: real, major, seen }; }   // named outright: that one
			good.push({ home: real, major });
		}
	}
	good.sort((a, b) => (b.major === MIN) - (a.major === MIN) || b.major - a.major);
	return good.length ? Object.assign(good[0], { seen }) : { home: null, major: 0, seen };
}

module.exports = { findJdk, MIN, MAX };
