// Lunacy's JS service host. Runs one webOS JS service package (written for the TouchPad's
// node 0.4.12 and Palm's mojoservice framework) on nodejs-mobile's Node 12, as webOS's
// run-js-service and jsservicelauncher/bootstrap-node.js did. See docs/architecture.md,
// "JS services".
//
//   node host.js <webos root> <service dir, as a webOS path>
//
// Lunacy's bus is on stdin/stdout, one JSON message per line; logs go to stderr.
// The webOS filesystem (/usr/palm/frameworks, /media/internal, /usr/bin/curl, ...) lives
// under <webos root>; fs and child_process see webOS paths and are pointed there.
"use strict";
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const util = require("util");
const Module = require("module");
const cp = require("child_process");
const EventEmitter = require("events").EventEmitter;

const ROOT = process.argv[2];
const SERVICE_DIR = process.argv[3];

// ---- the bus link ----

const out = process.stdout.write.bind(process.stdout);
function send(msg) { out(JSON.stringify(msg) + "\n"); }
// Anything else a service prints goes to the log, never onto the bus link.
process.stdout.write = function (chunk) { process.stderr.write(chunk); return true; };
for (const level of ["log", "info", "warn", "error", "debug"]) {
	console[level] = function () { process.stderr.write(level + ": " + util.format.apply(null, arguments) + "\n"); };
}

// ---- webOS paths ----

// Absolute paths under these roots are webOS paths; everything else (/data, /system, /proc,
// /dev) is the real Android filesystem.
const WEBOS = /^\/(usr|media|bin|sbin|var|etc|tmp|home|opt|lib)(\/|$)/;
// /media/internal's folders that are Android's ("photos=/storage/emulated/0/Pictures:..."), as
// UserFiles.kt maps them: a folder Lunacy's own tree hasn't got is Android's.
const MEDIA = {};
for (const e of (process.env.LUNACY_MEDIA || "").split(":")) {
	const i = e.indexOf("=");
	if (i > 0) { MEDIA[e.slice(0, i).toLowerCase()] = e.slice(i + 1); }
}
const exists0 = fs.existsSync;
function real(p) {
	if (typeof p !== "string" || !WEBOS.test(p)) { return p; }
	const m = /^\/media\/internal\/([^\/]+)(\/.*)?$/.exec(path.posix.normalize(p));
	const android = m && MEDIA[m[1].toLowerCase()];
	if (android && !exists0(ROOT + "/media/internal/" + m[1])) { return android + (m[2] || ""); }
	return ROOT + p;
}
function realAll(s) {
	// Absolute webOS paths inside a shell command line.
	return String(s).replace(/(^|[\s'"=(;|&<>])(\/(?:usr|media|bin|sbin|var|etc|tmp|home|opt|lib)(?:\/[^\s'";|&<>)]*)?)/g,
		(m, pre, p) => pre + real(p));
}

function wrap(obj, name, argIndexes) {
	const f = obj[name];
	if (typeof f !== "function") { return; }
	obj[name] = function () {
		const a = Array.prototype.slice.call(arguments);
		for (const i of argIndexes) { a[i] = real(a[i]); }
		return f.apply(this, a);
	};
}
for (const n of ["access", "appendFile", "chmod", "chown", "createReadStream", "createWriteStream", "exists", "lchown",
	"lstat", "mkdir", "mkdtemp", "open", "opendir", "readdir", "readFile", "readlink", "realpath", "rmdir", "stat",
	"truncate", "unlink", "utimes", "watch", "watchFile", "unwatchFile", "writeFile"]) {
	wrap(fs, n, [0]);
	wrap(fs, n + "Sync", [0]);
}
for (const n of ["rename", "copyFile", "link", "symlink"]) { wrap(fs, n, [0, 1]); wrap(fs, n + "Sync", [0, 1]); }

// webOS ran services as root, which file modes don't bind: the photos service makes its cache
// folder with mkdir(path, 666) - decimal, so d-w--w--wT - and goes on using it. Lunacy's
// service isn't root, so what a service makes or chmods keeps its owner's access.
function ownerKeeps(mode, bits) {
	if (mode === undefined || mode === null || typeof mode === "function") { return mode; }
	if (typeof mode === "object") { return Object.assign({}, mode, { mode: ownerKeeps(mode.mode, bits) }); }
	const m = typeof mode === "string" ? parseInt(mode, 8) : mode;
	return isNaN(m) ? mode : (m | bits);
}
for (const n of ["mkdir", "mkdirSync"]) {
	const f = fs[n];
	fs[n] = function (p, mode) {
		const a = Array.prototype.slice.call(arguments);
		if (a.length > 1) { a[1] = ownerKeeps(mode, 0o700); }
		return f.apply(fs, a);
	};
}
for (const n of ["chmod", "chmodSync"]) {
	const f = fs[n];
	fs[n] = function (p, mode) {
		const a = Array.prototype.slice.call(arguments);
		let dir = false;
		try { dir = fs.statSync(p).isDirectory(); } catch (e) {}
		a[1] = ownerKeeps(mode, dir ? 0o700 : 0o600);
		return f.apply(fs, a);
	};
}

// extractfs: webOS's thumbnailer was a filesystem, /var/luna/data/extractfs<image>:<size...>,
// that services read like any file (the photos service streams its JSON and cp's its JPEGs).
// Lunacy makes the file on request (Extractfs.kt): a read of one waits for it, then reads
// the copy. A read the device would have failed fails as it did there.
const EXTRACTFS = "/var/luna/data/extractfs";
function isExtractfs(p) { return typeof p === "string" && p.indexOf(EXTRACTFS) === 0; }
let nextExtract = 1;
const extracting = new Map();  // id -> callback(webOS path or null)
function extract(p, cb) {
	const id = "x" + nextExtract++;
	extracting.set(id, cb);
	send({ t: "extractfs", id: id, spec: p.slice(EXTRACTFS.length) });
}
function noEntry(p) {
	const e = new Error("ENOENT: no such file or directory, open '" + p + "'");
	e.code = "ENOENT"; e.errno = -2; e.path = p;
	return e;
}
const createReadStream0 = fs.createReadStream, readFile0 = fs.readFile;
fs.createReadStream = function (p, opts) {
	if (!isExtractfs(p)) { return createReadStream0.apply(fs, arguments); }
	const out = new (require("stream").PassThrough)();
	const enc = typeof opts === "string" ? opts : opts && opts.encoding;
	if (enc) { out.setEncoding(enc); }
	extract(p, (made) => {
		if (!made) { out.emit("error", noEntry(p)); return; }
		createReadStream0.call(fs, made).on("error", (e) => out.emit("error", e)).pipe(out);
	});
	return out;
};
fs.readFile = function (p) {
	const a = Array.prototype.slice.call(arguments);
	if (!isExtractfs(p)) { return readFile0.apply(fs, a); }
	const cb = a[a.length - 1];
	extract(p, (made) => made ? (a[0] = made, readFile0.apply(fs, a)) : cb(noEntry(p)));
};

// A child process given an extractfs file starts once the file is there. Until then it is a
// stand-in with the same streams and events; one whose file can't be made fails as cp did
// on the device ("cp: read error: No such file or directory", exit 1).
function withExtracts(args, start) {
	const wanted = (args || []).filter(isExtractfs);
	if (!wanted.length) { return start(args); }
	const PassThrough = require("stream").PassThrough;
	const child = new EventEmitter();
	child.stdin = new PassThrough(); child.stdout = new PassThrough(); child.stderr = new PassThrough();
	child.kill = function (sig) { child.killed = true; if (child.real) { child.real.kill(sig); } };
	const made = {};
	let left = wanted.length, failed = false;
	const go = () => {
		if (child.killed) { return; }
		if (failed) {
			child.stderr.end("cp: read error: No such file or directory\n"); child.stdout.end();
			setImmediate(() => { child.emit("exit", 1, null); child.emit("close", 1, null); });
			return;
		}
		const real = child.real = start(args.map((a) => made[a] || a));
		child.pid = real.pid;
		real.stdout && real.stdout.pipe(child.stdout);
		real.stderr && real.stderr.pipe(child.stderr);
		real.stdin && child.stdin.pipe(real.stdin);
		for (const ev of ["exit", "close", "error"]) { real.on(ev, function () { child.emit.apply(child, [ev].concat(Array.prototype.slice.call(arguments))); }); }
	};
	for (const p of wanted) {
		extract(p, (m) => { if (m) { made[p] = m; } else { failed = true; } if (--left === 0) { go(); } });
	}
	return child;
}

// Child processes: the executable, arguments that are webOS paths, and "sh -c" command lines.
// PATH puts webOS's /usr/bin (curl) and /bin (sh) first.
const ENV_PATH = ROOT + "/usr/bin:" + ROOT + "/bin:" + (process.env.PATH || "/system/bin");
function childOptions(o) {
	o = Object.assign({}, o || {});
	o.env = Object.assign({}, process.env, o.env || {}, { PATH: ENV_PATH });
	if (o.cwd) { o.cwd = real(o.cwd); }
	return o;
}
function childArgs(file, args) {
	const shell = /(^|\/)sh$/.test(file) && args[0] === "-c";
	return args.map((a, i) => shell && i === 1 ? realAll(a) : real(a));
}
function splitArgs(args, opts) {
	if (!Array.isArray(args)) { return [[], args, opts]; }
	return [args, opts];
}
const spawn0 = cp.spawn, execFile0 = cp.execFile, exec0 = cp.exec, spawnSync0 = cp.spawnSync, execSync0 = cp.execSync, execFileSync0 = cp.execFileSync;
cp.spawn = function (file, args, opts) {
	if (!Array.isArray(args)) { opts = args; args = []; }
	return withExtracts(args, (a) => spawn0.call(cp, real(file), childArgs(file, a), childOptions(opts)));
};
cp.spawnSync = function (file, args, opts) {
	if (!Array.isArray(args)) { opts = args; args = []; }
	return spawnSync0.call(cp, real(file), childArgs(file, args), childOptions(opts));
};
cp.execFile = function (file, args, opts, cb) {
	if (typeof args === "function") { cb = args; args = []; opts = {}; }
	else if (!Array.isArray(args)) { cb = opts; opts = args; args = []; }
	if (typeof opts === "function") { cb = opts; opts = {}; }
	return withExtracts(args, (a) => execFile0.call(cp, real(file), childArgs(file, a), childOptions(opts), cb));
};
cp.execFileSync = function (file, args, opts) {
	if (!Array.isArray(args)) { opts = args; args = []; }
	return execFileSync0.call(cp, real(file), childArgs(file, args), childOptions(opts));
};
cp.exec = function (cmd, opts, cb) {
	if (typeof opts === "function") { cb = opts; opts = {}; }
	return exec0.call(cp, realAll(cmd), Object.assign(childOptions(opts), { shell: "/system/bin/sh" }), cb);
};
cp.execSync = function (cmd, opts) {
	return execSync0.call(cp, realAll(cmd), Object.assign(childOptions(opts), { shell: "/system/bin/sh" }));
};

// ---- node 0.4 ----

// path.exists moved to fs in node 0.8.
path.exists = (p, cb) => fs.exists(p, cb);
path.existsSync = (p) => fs.existsSync(p);
// webOS's node took these from the service launcher.
process.setArgs = function () {};
process.setName = function () {};

// ---- palmbus: the bus, over stdin/stdout ----

let nextCall = 1;
const handles = [];
const outgoing = new Map();  // id -> Request

class Message {
	constructor(m) { this.m = m; }
	payload() { return this.m.payload; }
	category() { return this.m.category || "/"; }
	method() { return this.m.method; }
	uniqueToken() { return String(this.m.id); }
	token() { return this.m.id; }
	sender() { return this.m.sender || ""; }
	// A service's own bus name when a service called: the framework's commands adopt the
	// activity they were called with only when this is com.palm.activitymanager, and merely
	// monitor it otherwise, which leaves them unable to complete it (mojoservice
	// controller_command.js). An app's call has none.
	senderServiceName() { return this.m.senderService || (this.m.fromService ? this.m.sender : "") || ""; }
	applicationID() { return this.m.sender || ""; }
	isSubscription() { return !!this.m.subscribe; }
	respond(json) { send({ t: "response", id: this.m.id, payload: String(json) }); return true; }
	print() {}
}

class Request extends EventEmitter {
	constructor(url, payload, subscribe) {
		super();
		this.id = "c" + nextCall++;
		outgoing.set(this.id, this);
		send({ t: "call", id: this.id, url: url, payload: payload, subscribe: subscribe });
	}
	cancel() {
		if (outgoing.delete(this.id)) { send({ t: "cancelCall", id: this.id }); }
	}
}

class Handle extends EventEmitter {
	constructor(name, isPublic) {
		super();
		this.name = name;
		this.isPublic = !!isPublic;
		this.methods = new Set();
		handles.push(this);
	}
	registerMethod(category, method) {
		let c = category || "/";
		if (c.charAt(0) !== "/") { c = "/" + c; }
		this.methods.add(c.replace(/\/$/, "") + "/" + method);
	}
	subscriptionAdd() {}
	unregister() { const i = handles.indexOf(this); if (i >= 0) { handles.splice(i, 1); } }
	pushRole() {}
	call(url, payload) { return new Request(url, payload, false); }
	subscribe(url, payload) { return new Request(url, payload, true); }
	cancel(req) { if (req && req.cancel) { req.cancel(); } }
}

// A request reaches the handle that registered its method: the public one for callers
// outside the service's package, as ls-hubd's public and private buses did.
function handleFor(key, fromOutside) {
	const has = handles.filter((h) => h.name && h.methods.has(key));
	return has.find((h) => h.isPublic === fromOutside) || (fromOutside ? undefined : has[0]);
}

function receive(m) {
	if (m.t === "request") {
		const key = (m.category === "/" ? "" : m.category) + "/" + m.method;
		const h = handleFor(key, m.outside);
		// A service with nothing on the public bus isn't there at all for a public caller.
		if (!h && m.outside && !handles.some((x) => x.name === m.service && x.isPublic)) {
			send({ t: "response", id: m.id, payload: JSON.stringify({ returnValue: false, errorCode: -1,
				errorText: "Service does not exist: " + m.service + "." }) });
			return;
		}
		if (!h) {
			send({ t: "response", id: m.id, payload: JSON.stringify({ returnValue: false, errorCode: -1,
				errorText: "Unknown method \"" + m.method + "\" for category \"" + m.category + "\"" }) });
			return;
		}
		h.emit("request", new Message(m));
	} else if (m.t === "cancel") {
		for (const h of handles) { h.emit("cancel", new Message(m)); }
	} else if (m.t === "extractfs") {
		const cb = extracting.get(m.id);
		if (cb) { extracting.delete(m.id); cb(m.path || null); }
	} else if (m.t === "callResponse") {
		const r = outgoing.get(m.id);
		if (r) {
			if (!m.subscribe) { outgoing.delete(m.id); }
			r.emit("response", new Message({ id: m.id, payload: m.payload, sender: m.sender, senderService: m.sender }));
		}
	}
}

let buffered = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (d) => {
	buffered += d;
	let i;
	while ((i = buffered.indexOf("\n")) >= 0) {
		const line = buffered.slice(0, i);
		buffered = buffered.slice(i + 1);
		if (line) { try { receive(JSON.parse(line)); } catch (e) { console.error("host: " + (e.stack || e)); } }
	}
});
// Lunacy closing the link ends the service.
process.stdin.on("end", () => process.exit(0));

// ---- webos: include and the MojoLoader require ----

const webos = {
	// Runs a file in the service's global scope (sources.json "source" entries).
	include(p) {
		vm.runInThisContext(fs.readFileSync(p, "utf8"), { filename: p });
	},
	// Loads a MojoLoader library: its files share a fresh context holding MojoLoader,
	// require and exports; the context's global is the result.
	require(req, loader, files) {
		const sandbox = {
			// root is the service's global, where its sources define the command assistants.
			MojoLoader: loader, require: req, exports: {}, root: global, console: console, process: process, Buffer: Buffer,
			setTimeout, clearTimeout, setInterval, clearInterval, setImmediate, clearImmediate
		};
		const ctx = vm.createContext(sandbox);
		sandbox.global = sandbox;
		for (const f of files) { vm.runInContext(fs.readFileSync(f, "utf8"), ctx, { filename: f }); }
		return sandbox;
	}
};

// pmloglib: webOS's console for services, with a "name" set by the launcher.
const pmloglib = {};
for (const level of ["log", "info", "warn", "error", "debug"]) { pmloglib[level] = console[level]; }

const natives = {
	palmbus: { Handle: Handle },
	webos: webos,
	pmloglib: pmloglib,
	sys: util,
	mojoloader: null
};
const load0 = Module._load;
Module._load = function (request, parent, isMain) {
	if (Object.prototype.hasOwnProperty.call(natives, request)) {
		if (request === "mojoloader" && !natives.mojoloader) {
			natives.mojoloader = load0.call(Module, ROOT + "/usr/palm/frameworks/mojoloader.js", parent, false);
		}
		return natives[request];
	}
	return load0.call(Module, real(request), parent, isMain);
};

process.on("uncaughtException", (e) => { console.error("uncaught: " + (e && e.stack || e)); });

// ---- start, as run-js-service did ----

process.chdir(real(SERVICE_DIR));
// After "--": the cgroup tasks file, then the service directory. Lunacy has no cgroups; this
// is the path run-js-service passed when there were none.
process.argv = [process.argv[0], "bootstrap-node.js", "--", "/no-group/not-present", SERVICE_DIR];
require(ROOT + "/usr/palm/services/jsservicelauncher/bootstrap-node.js");
send({ t: "ready" });
