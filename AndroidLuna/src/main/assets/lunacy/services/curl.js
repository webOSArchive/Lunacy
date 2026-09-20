// /usr/bin/curl for webOS JS services, in Node. The services Lunacy runs shell out to curl
// (webOS Internals' curl-tls13 on a TouchPad). A static curl can't resolve names on Android,
// which has no /etc/resolv.conf, so this implements the options services use; any other option
// fails as curl does ("option ...: is unknown", exit 2). See docs/architecture.md, "JS services".
//
//   node curl.js [options] URL...     (LUNACY_WEBOS_ROOT maps webOS paths)
"use strict";
const fs = require("fs");
const http = require("http");
const https = require("https");
const zlib = require("zlib");
const { URL } = require("url");

const ROOT = process.env.LUNACY_WEBOS_ROOT || "";
const WEBOS = /^\/(usr|media|bin|sbin|var|etc|tmp|home|opt|lib)(\/|$)/;
const real = (p) => (ROOT && WEBOS.test(p) ? ROOT + p : p);
const USER_AGENT = "curl/7.88.1";  // the curl-tls13 package's version

// Exit codes, as curl's.
const E = { UNKNOWN_OPTION: 2, URL_MALFORMAT: 3, RESOLVE: 6, CONNECT: 7, HTTP: 22, WRITE: 23, READ: 26, TIMEOUT: 28, TOO_MANY_REDIRECTS: 47, RECV: 56 };

const opt = { silent: false, showError: false, location: false, fail: false, maxTime: 0, connectTimeout: 0,
	headers: [], method: null, data: null, head: false, dumpHeader: null, writeOut: null, userAgent: USER_AGENT,
	insecure: false, compressed: false, referer: null, user: null, maxRedirs: 50, jobs: [] };
const pendingOutput = [];

function die(code, text) {
	if (!opt.silent || opt.showError) { process.stderr.write("curl: (" + code + ") " + text + "\n"); }
	process.exit(code);
}

// Options that take a value, by long name, with their short forms.
const TAKES = { "max-time": "m", "connect-timeout": null, header: "H", request: "X", data: "d", "data-binary": null,
	"data-raw": null, "data-ascii": null, "dump-header": "D", output: "o", "write-out": "w", "user-agent": "A",
	config: "K", referer: "e", user: "u", "max-redirs": null, retry: null, "retry-delay": null, "retry-max-time": null };
const SHORT = { m: "max-time", H: "header", X: "request", d: "data", D: "dump-header", o: "output", w: "write-out",
	A: "user-agent", K: "config", e: "referer", u: "user", s: "silent", S: "show-error", L: "location",
	f: "fail", I: "head", k: "insecure", v: "verbose", i: "include", O: "remote-name", G: "get" };
const FLAGS = new Set(["silent", "show-error", "location", "fail", "head", "insecure", "compressed", "verbose",
	"no-buffer", "globoff", "http1.1", "no-keepalive", "tr-encoding", "no-progress-meter", "progress-bar"]);

function apply(name, value) {
	switch (name) {
		case "silent": opt.silent = true; break;
		case "show-error": opt.showError = true; break;
		case "location": opt.location = true; break;
		case "fail": opt.fail = true; break;
		case "head": opt.head = true; break;
		case "insecure": opt.insecure = true; break;
		case "compressed": opt.compressed = true; break;
		case "verbose": case "no-buffer": case "globoff": case "http1.1": case "no-keepalive":
		case "tr-encoding": case "no-progress-meter": case "progress-bar": case "retry": case "retry-delay":
		case "retry-max-time": break;
		case "max-time": opt.maxTime = parseFloat(value) * 1000; break;
		case "connect-timeout": opt.connectTimeout = parseFloat(value) * 1000; break;
		case "max-redirs": opt.maxRedirs = parseInt(value, 10); break;
		case "header": opt.headers.push(value); break;
		case "request": opt.method = value; break;
		case "data": case "data-ascii": case "data-binary": case "data-raw":
			if (name !== "data-raw" && value.charAt(0) === "@") { value = fs.readFileSync(real(value.slice(1))); }
			opt.data = opt.data === null ? value : Buffer.concat([Buffer.from(opt.data), Buffer.from("&"), Buffer.from(value)]);
			break;
		case "dump-header": opt.dumpHeader = value; break;
		case "output": pendingOutput.push(value); break;
		case "write-out": opt.writeOut = value; break;
		case "user-agent": opt.userAgent = value; break;
		case "referer": opt.referer = value; break;
		case "user": opt.user = value; break;
		case "config": readConfig(value); break;
		case "url": opt.jobs.push({ url: value, output: null }); break;
		default: die(E.UNKNOWN_OPTION, "option --" + name + ": is unknown");
	}
}

// -K files: "name = value" or "name value" lines, values optionally double-quoted.
function readConfig(file) {
	const text = file === "-" ? fs.readFileSync(0, "utf8") : fs.readFileSync(real(file), "utf8");
	for (let line of text.split(/\r?\n/)) {
		line = line.trim();
		if (!line || line.charAt(0) === "#") { continue; }
		const m = /^-{0,2}([A-Za-z0-9.-]+)\s*(?:[=:]\s*|\s+)?(.*)$/.exec(line);
		if (!m) { continue; }
		let name = m[1], value = m[2];
		if (name.length === 1 && SHORT[name]) { name = SHORT[name]; }
		if (value.charAt(0) === "\"") { value = JSON.parse(value.replace(/\\(?!["\\/bfnrtu])/g, "\\\\")); }
		apply(name, FLAGS.has(name) ? null : value);
	}
}

function parseArgs(argv) {
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i];
		if (a.startsWith("--")) {
			const name = a.slice(2);
			if (name in TAKES || name === "url") { apply(name, argv[++i]); } else if (FLAGS.has(name)) { apply(name); }
			else { die(E.UNKNOWN_OPTION, "option " + a + ": is unknown"); }
		} else if (a.charAt(0) === "-" && a.length > 1) {
			for (let j = 1; j < a.length; j++) {
				const name = SHORT[a.charAt(j)];
				if (!name) { die(E.UNKNOWN_OPTION, "option -" + a.charAt(j) + ": is unknown"); }
				if (name in TAKES) { apply(name, j + 1 < a.length ? a.slice(j + 1) : argv[++i]); break; }
				apply(name);
			}
		} else {
			apply("url", a);
		}
	}
}

// -w: %{variable} and backslash escapes.
function writeOut(fmt, r) {
	return fmt.replace(/%\{([a-z_]+)\}|\\n|\\r|\\t|%%/g, (m, v) => {
		if (m === "\\n") { return "\n"; } if (m === "\\r") { return "\r"; } if (m === "\\t") { return "\t"; } if (m === "%%") { return "%"; }
		switch (v) {
			case "http_code": case "response_code": return String(r.status || 0).padStart(3, "0");
			case "url_effective": return r.url;
			case "size_download": return String(r.size || 0);
			case "content_type": return r.contentType || "";
			case "num_redirects": return String(r.redirects || 0);
			case "redirect_url": return r.redirectUrl || "";
			case "time_total": return ((Date.now() - r.start) / 1000).toFixed(6);
			default: return "";
		}
	});
}

function fetchOnce(job, url, method, body, redirects, start, done) {
	let u;
	try { u = new URL(url); } catch (e) { return done(E.URL_MALFORMAT, "URL rejected: Malformed input to a URL function"); }
	const mod = u.protocol === "https:" ? https : u.protocol === "http:" ? http : null;
	if (!mod) { return done(1, "Protocol \"" + u.protocol.replace(":", "") + "\" not supported"); }
	const headers = { "User-Agent": opt.userAgent, Accept: "*/*" };
	if (opt.compressed) { headers["Accept-Encoding"] = "deflate, gzip"; }
	if (opt.referer) { headers.Referer = opt.referer; }
	if (opt.user) { headers.Authorization = "Basic " + Buffer.from(opt.user).toString("base64"); }
	if (body !== null) { headers["Content-Type"] = "application/x-www-form-urlencoded"; }
	for (const h of opt.headers) {
		const i = h.indexOf(":");
		if (i > 0) { const v = h.slice(i + 1).trim(); if (v) { headers[h.slice(0, i).trim()] = v; } else { delete headers[h.slice(0, i).trim()]; } }
	}
	const req = mod.request(u, { method: method, headers: headers, rejectUnauthorized: !opt.insecure }, (res) => {
		const loc = res.headers.location;
		if (opt.location && loc && [301, 302, 303, 307, 308].indexOf(res.statusCode) >= 0) {
			res.resume();
			if (redirects >= opt.maxRedirs) { return done(E.TOO_MANY_REDIRECTS, "Maximum (" + opt.maxRedirs + ") redirects followed"); }
			const next = new URL(loc, u).toString();
			const keep = res.statusCode === 307 || res.statusCode === 308 || opt.method;
			if (opt.dumpHeader) { dumpHeaders(res); }
			return fetchOnce(job, next, keep ? method : (method === "POST" ? "GET" : method), keep ? body : null, redirects + 1, start, done);
		}
		if (opt.dumpHeader) { dumpHeaders(res); }
		const result = { status: res.statusCode, url: u.toString(), contentType: res.headers["content-type"], redirects: redirects, start: start, size: 0 };
		if (opt.fail && res.statusCode >= 400) {
			res.resume();
			return done(E.HTTP, "The requested URL returned error: " + res.statusCode, result);
		}
		let stream = res;
		const enc = res.headers["content-encoding"];
		if (opt.compressed && (enc === "gzip" || enc === "deflate")) { stream = res.pipe(enc === "gzip" ? zlib.createGunzip() : zlib.createInflate()); }
		const sink = job.output && job.output !== "-" ? fs.createWriteStream(real(job.output)) : null;
		stream.on("data", (d) => { result.size += d.length; if (sink) { sink.write(d); } else if (!opt.head) { process.stdout.write(d); } });
		stream.on("error", (e) => done(E.RECV, "Failure when receiving data from the peer", result));
		stream.on("end", () => {
			if (opt.head && !opt.dumpHeader) { process.stdout.write(rawHeaders(res)); }
			if (sink) { sink.end(() => done(0, null, result)); } else { done(0, null, result); }
		});
	});
	req.on("error", (e) => {
		const code = e.code === "ENOTFOUND" || e.code === "EAI_AGAIN" ? E.RESOLVE : e.code === "ECONNREFUSED" || e.code === "EHOSTUNREACH" ? E.CONNECT : E.RECV;
		done(code, code === E.RESOLVE ? "Could not resolve host: " + u.hostname : code === E.CONNECT ? "Failed to connect to " + u.hostname + " port " + (u.port || (u.protocol === "https:" ? 443 : 80)) : String(e.message));
	});
	if (opt.connectTimeout) { req.setTimeout(opt.connectTimeout, () => { req.destroy(); done(E.TIMEOUT, "Connection timed out"); }); }
	if (body !== null) { req.write(body); }
	req.end();
}

function rawHeaders(res) {
	let s = "HTTP/" + res.httpVersion + " " + res.statusCode + " " + res.statusMessage + "\r\n";
	for (let i = 0; i < res.rawHeaders.length; i += 2) { s += res.rawHeaders[i] + ": " + res.rawHeaders[i + 1] + "\r\n"; }
	return s + "\r\n";
}
function dumpHeaders(res) {
	if (opt.dumpHeader === "-") { process.stdout.write(rawHeaders(res)); } else { fs.appendFileSync(real(opt.dumpHeader), rawHeaders(res)); }
}

parseArgs(process.argv.slice(2));
// Outputs pair with URLs in order, wherever each appeared, as in curl.
opt.jobs.forEach((job, i) => { job.output = pendingOutput[i] || null; });
if (!opt.jobs.length) { die(E.URL_MALFORMAT, "no URL specified"); }
if (opt.dumpHeader && opt.dumpHeader !== "-") { try { fs.writeFileSync(real(opt.dumpHeader), ""); } catch (e) { die(E.WRITE, "Failed writing header"); } }
if (opt.maxTime) { setTimeout(() => die(E.TIMEOUT, "Operation timed out after " + opt.maxTime + " milliseconds"), opt.maxTime).unref(); }

let status = 0;
(function next(i) {
	if (i >= opt.jobs.length) { return process.exit(status); }
	const job = opt.jobs[i];
	const method = opt.method || (opt.head ? "HEAD" : opt.data !== null ? "POST" : "GET");
	fetchOnce(job, job.url, method, opt.data, 0, Date.now(), (code, text, result) => {
		if (opt.writeOut && result) { process.stdout.write(writeOut(opt.writeOut, result)); }
		if (code) {
			status = code;
			if (!opt.silent || opt.showError) { process.stderr.write("curl: (" + code + ") " + text + "\n"); }
		}
		next(i + 1);
	});
})(0);
