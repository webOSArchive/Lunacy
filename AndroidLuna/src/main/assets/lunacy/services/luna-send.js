// luna-send, for package scripts and JS services' child processes in Lunacy's webOS root.
// It hands one call to Lunacy's bus over the socket in LUNACY_BUS and prints each reply on a
// line, as webOS's luna-send did. See WebosRoot.kt (LunaSendServer) and
// Docs/architecture.md, "The webOS root".
//
//   luna-send [-n count] [-a appId] [-f] [-P] [-i] <url> [<json payload>]
//
// Who is calling comes from LUNACY_BUS_TOKEN, which Lunacy gave this process; -a is accepted
// and ignored. Calls go on the private bus, or the public one with -P. -f prints the replies
// indented; -i changes nothing here.
"use strict";
const net = require("net");

const args = process.argv.slice(2);
let count = 0, pretty = false, pub = false;
const rest = [];
for (let i = 0; i < args.length; i++) {
	const a = args[i];
	if (a === "-n") { count = parseInt(args[++i], 10) || 0; }
	else if (/^-n\d+$/.test(a)) { count = parseInt(a.slice(2), 10); }
	else if (a === "-a" || a === "-m" || a === "-w") { i++; }
	else if (a === "-f") { pretty = true; }
	else if (a === "-P") { pub = true; }
	else if (/^-[iqlS]$/.test(a)) { /* accepted */ }
	else if (a === "-h" || a === "--help") { rest.length = 0; break; }
	else { rest.push(a); }
}
if (rest.length < 1) {
	process.stderr.write("Usage: luna-send [-n count] [-a appId] [-f] <url> <payload>\n");
	process.exit(1);
}
const url = rest[0];
const payload = rest.length > 1 ? rest[1] : "{}";
try { JSON.parse(payload); } catch (e) {
	process.stderr.write("Invalid JSON: " + payload + "\n");
	process.exit(1);
}

const sock = net.connect({ path: process.env.LUNACY_BUS || "" });
let seen = 0, buffered = "";
sock.on("connect", () => {
	sock.write(JSON.stringify({ token: process.env.LUNACY_BUS_TOKEN || "", url: url, payload: payload, public: pub }) + "\n");
});
sock.setEncoding("utf8");
sock.on("data", (d) => {
	buffered += d;
	let i;
	while ((i = buffered.indexOf("\n")) >= 0) {
		const line = buffered.slice(0, i);
		buffered = buffered.slice(i + 1);
		let out = line;
		if (pretty) { try { out = JSON.stringify(JSON.parse(line), null, 4); } catch (e) {} }
		process.stdout.write(out + "\n");
		if (count && ++seen >= count) { sock.end(); process.exit(0); }
	}
});
sock.on("end", () => process.exit(0));
sock.on("error", (e) => { process.stderr.write("luna-send: " + e.message + "\n"); process.exit(1); });
