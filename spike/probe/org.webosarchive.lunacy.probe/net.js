// The network contract a file:// app had on webOS: cross-origin XHR, what servers saw, and the
// XHR object's shape. httpbin.org echoes each request back. Results go out as "LUNACYPROBE net.*".
(function () {
	var out = document.getElementById("out");
	function rec(k, v) {
		var s;
		try { s = JSON.stringify(v); } catch (e) { s = String(v); }
		out.appendChild(document.createTextNode(k + " = " + s + "\n"));
		console.log("LUNACYPROBE " + k + " " + s);
	}
	var H = "http://httpbin.org";

	// One request. o: method, url, body, headers, mime, type, sync. Records what the page sees.
	function req(key, o, done) {
		var x = new XMLHttpRequest(), states = [], events = [];
		var r = { states: states, events: events };
		function finish() {
			r.status = x.status;
			try { r.statusText = x.statusText; } catch (e) { r.statusText = "THROWS"; }
			try { r.text = x.responseText; } catch (e) { r.text = "THROWS " + e; }
			try { r.headers = x.getAllResponseHeaders(); } catch (e) { r.headers = "THROWS"; }
			try { r.setCookie = x.getResponseHeader("Set-Cookie"); } catch (e) { r.setCookie = "THROWS"; }
			try { r.xml = x.responseXML ? x.responseXML.documentElement.nodeName : null; } catch (e) { r.xml = "THROWS"; }
			r.responseURL = x.responseURL;
			try { r.response = typeof x.response; } catch (e) { r.response = "THROWS"; }
			done(r, x);
		}
		x.onreadystatechange = function () {
			states.push(x.readyState);
			if (x.readyState === 4 && !o.sync) { setTimeout(finish, 50); }
		};
		["load", "error", "abort", "loadend", "progress", "loadstart", "timeout"].forEach(function (t) {
			x.addEventListener(t, function () { events.push(t); }, false);
		});
		try {
			x.open(o.method || "GET", o.url, !o.sync);
			if (o.mime) { x.overrideMimeType(o.mime); }
			if (o.type) { x.responseType = o.type; }
			var hs = o.headers || {};
			r.setHeader = {};
			for (var k in hs) {
				try { x.setRequestHeader(k, hs[k]); r.setHeader[k] = "ok"; } catch (e) { r.setHeader[k] = "THROWS " + e; }
			}
			x.send(o.body === undefined ? null : o.body);
		} catch (e) {
			r.threw = String(e);
			done(r, x);
			return;
		}
		if (o.sync) { finish(); }
	}

	// What the server saw, from httpbin's echo.
	function seen(r) {
		try { var j = JSON.parse(r.text); return { method: j.method, headers: j.headers, data: j.data, form: j.form, url: j.url, cookies: j.cookies }; }
		catch (e) { return String(r.text).slice(0, 120); }
	}
	function brief(r) {
		return { status: r.status, statusText: r.statusText, states: r.states, events: r.events, threw: r.threw, len: (r.text || "").length };
	}

	var x0 = new XMLHttpRequest();
	rec("net.shape", {
		responseType: "responseType" in x0, response: "response" in x0, responseURL: "responseURL" in x0,
		timeout: "timeout" in x0, withCredentials: "withCredentials" in x0, upload: "upload" in x0,
		onload: "onload" in x0, onloadend: "onloadend" in x0, overrideMimeType: typeof x0.overrideMimeType,
		sendAsBinary: typeof x0.sendAsBinary, FormData: typeof window.FormData, ArrayBuffer: typeof window.ArrayBuffer,
		DONE: XMLHttpRequest.DONE, protoDONE: x0.DONE
	});

	var steps = [
		function (next) { req("net.get", { url: H + "/anything?probe=1", headers: { "X-Probe": "1" } }, function (r) {
			rec("net.get", brief(r)); rec("net.get.seen", seen(r)); rec("net.get.headers", r.headers); next(); }); },
		function (next) { req("net.post", { method: "POST", url: H + "/anything", body: "a=1&b=%C3%A9" }, function (r) {
			rec("net.post", brief(r)); rec("net.post.seen", seen(r)); next(); }); },
		function (next) { req("net.postForm", { method: "POST", url: H + "/anything", body: "a=1", headers: { "Content-Type": "application/x-www-form-urlencoded" } }, function (r) {
			rec("net.postForm.seen", seen(r)); next(); }); },
		function (next) { req("net.put", { method: "PUT", url: H + "/anything", body: "{\"x\":1}", headers: { "Content-Type": "application/json" } }, function (r) {
			rec("net.put.seen", seen(r)); next(); }); },
		function (next) { req("net.forbidden", { url: H + "/anything", headers: { "User-Agent": "probe-ua", "Referer": "http://probe.example/", "Cookie": "c=1", "Origin": "http://probe.example", "Accept": "text/probe", "Accept-Encoding": "probe", "Host": "probe.example", "Connection": "close" } }, function (r) {
			rec("net.forbidden.set", r.setHeader); rec("net.forbidden.seen", seen(r)); next(); }); },
		function (next) { req("net.404", { url: H + "/status/404" }, function (r) { rec("net.404", brief(r)); next(); }); },
		function (next) { req("net.500", { url: H + "/status/500" }, function (r) { rec("net.500", brief(r)); next(); }); },
		function (next) { req("net.redirect", { url: H + "/redirect-to?url=" + encodeURIComponent(H + "/get?after=redirect") }, function (r) {
			rec("net.redirect", brief(r)); rec("net.redirect.seen", seen(r)); rec("net.redirect.responseURL", r.responseURL); next(); }); },
		function (next) { req("net.redirectPost", { method: "POST", body: "z=1", url: H + "/redirect-to?url=" + encodeURIComponent(H + "/anything") + "&status_code=302" }, function (r) {
			rec("net.redirectPost.seen", seen(r)); next(); }); },
		function (next) { req("net.cookieSet", { url: H + "/cookies/set?probe=abc" }, function (r) {
			rec("net.cookieSet", brief(r)); rec("net.cookieSet.seen", seen(r)); rec("net.cookieSet.setCookie", r.setCookie); rec("net.cookieSet.headers", r.headers); next(); }); },
		function (next) { req("net.cookieGet", { url: H + "/cookies" }, function (r) {
			rec("net.cookieGet.seen", seen(r)); rec("net.cookieGet.document", document.cookie); next(); }); },
		function (next) { req("net.xml", { url: H + "/xml" }, function (r) { rec("net.xml", [r.status, r.xml]); next(); }); },
		function (next) { req("net.xmlOverride", { url: H + "/get", mime: "text/xml" }, function (r) { rec("net.xmlOverride", [r.status, r.xml]); next(); }); },
		function (next) { req("net.utf8", { url: H + "/encoding/utf8" }, function (r) {
			var t = r.text || "", i = t.indexOf("∀"); rec("net.utf8", [r.status, i >= 0]); next(); }); },
		function (next) { req("net.binary", { url: H + "/bytes/8?seed=1", mime: "text/plain; charset=x-user-defined" }, function (r) {
			var t = r.text || "", c = []; for (var i = 0; i < t.length; i++) { c.push(t.charCodeAt(i)); } rec("net.binary", c); next(); }); },
		function (next) { req("net.arraybuffer", { url: H + "/bytes/8?seed=1", type: "arraybuffer" }, function (r, x) {
			var v = null; try { v = x.response ? Array.prototype.slice.call(new Uint8Array(x.response)) : x.response; } catch (e) { v = "THROWS " + e; }
			rec("net.arraybuffer", [r.status, r.threw, v]); next(); }); },
		function (next) { req("net.badhost", { url: "http://127.0.0.1:1/" }, function (r) { rec("net.badhost", brief(r)); next(); }); },
		function (next) { req("net.syncGet", { url: H + "/get?sync=1", sync: true }, function (r) { rec("net.syncGet", brief(r)); next(); }); },
		function (next) { req("net.syncBadhost", { url: "http://127.0.0.1:1/", sync: true }, function (r) { rec("net.syncBadhost", brief(r)); next(); }); },
		function (next) { req("net.syncPostSeen", { method: "POST", url: H + "/anything", body: "s=1", sync: true }, function (r) { rec("net.syncPost.seen", seen(r)); next(); }); },
		function (next) { req("net.httpsLE", { url: "https://letsencrypt.org/" }, function (r) { rec("net.httpsLE", brief(r)); next(); }); },
		function (next) { req("net.httpsAmazon", { url: "https://www.amazon.com/robots.txt" }, function (r) { rec("net.httpsAmazon", brief(r)); next(); }); },
		function (next) {
			var x = new XMLHttpRequest(), ev = [];
			x.onreadystatechange = function () { ev.push("rs" + x.readyState); };
			x.onabort = function () { ev.push("abort"); };
			x.open("GET", H + "/delay/3", true);
			x.send(null);
			setTimeout(function () { x.abort(); ev.push("after:" + x.readyState + "/" + x.status); setTimeout(function () { rec("net.abort", ev); next(); }, 300); }, 300);
		},
		// How long a request to an address nothing answers on takes to fail (no reply to SYN).
		function (next) { var t0 = Date.now(); req("net.syncUnreachable", { url: "http://10.255.255.1/", sync: true }, function (r) {
			rec("net.syncUnreachable", { ms: Date.now() - t0, threw: r.threw, status: r.status }); next(); }); },
		function (next) { var t0 = Date.now(); req("net.asyncUnreachable", { url: "http://10.255.255.1/" }, function (r) {
			rec("net.asyncUnreachable", { ms: Date.now() - t0, status: r.status, events: r.events }); next(); }); },
		function (next) { rec("net.done", true); next(); }
	];
	(function run(i) { if (i < steps.length) { steps[i](function () { run(i + 1); }); } })(0);
})();
