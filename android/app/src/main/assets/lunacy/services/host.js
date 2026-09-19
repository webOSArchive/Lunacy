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
function real(p) { return typeof p === "string" && WEBOS.test(p) ? ROOT + p : p; }
function realAll(s) {
	// Absolute webOS paths inside a shell command line.
	return String(s).replace(/(^|[\s'"=(;|&<>])(\/(?:usr|media|bin|sbin|var|etc|tmp|home|opt|lib)(?:\/[^\s'";|&<>)]*)?)/g,
		(m, pre, p) => pre + ROOT + p);
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
	return spawn0.call(cp, real(file), childArgs(file, args), childOptions(opts));
};
cp.spawnSync = function (file, args, opts) {
	if (!Array.isArray(args)) { opts = args; args = []; }
	return spawnSync0.call(cp, real(file), childArgs(file, args), childOptions(opts));
};
cp.execFile = function (file, args, opts, cb) {
	if (typeof args === "function") { cb = args; args = []; opts = {}; }
	else if (!Array.isArray(args)) { cb = opts; opts = args; args = []; }
	if (typeof opts === "function") { cb = opts; opts = {}; }
	return execFile0.call(cp, real(file), childArgs(file, args), childOptions(opts), cb);
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
	senderServiceName() { return this.m.senderService || ""; }
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
		if (!h) {
			send({ t: "response", id: m.id, payload: JSON.stringify({ returnValue: false, errorCode: -1,
				errorText: "Unknown method \"" + m.method + "\" for category \"" + m.category + "\"" }) });
			return;
		}
		h.emit("request", new Message(m));
	} else if (m.t === "cancel") {
		for (const h of handles) { h.emit("cancel", new Message(m)); }
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
