// Lunacy network shim. ES5 only: must run on Chromium 37.
// webOS apps ran from file:// and could read any server's response: no CORS check, any request
// header, and the device's cookie jar (measured with spike/probe 0.0.7, see
// spike/results/touchpad-net.txt). Here pages live on https origins, so XMLHttpRequest is
// replaced: same-origin requests (the app's files, the frameworks) go to the real XHR, and
// everything else to native HTTP (NetShim.kt), which sends it as the TouchPad did.
(function () {
	var N = window.LunacyNative, Real = window.XMLHttpRequest;
	if (!N || !N.netSend || !Real) { return; }
	var EVENTS = ["readystatechange", "loadstart", "progress", "load", "error", "abort", "timeout", "loadend"];
	var inFlight = {}, link = document.createElement("a");
	// Request ids, like the bridge's tokens, come from one counter shared by a window's
	// same-origin frames, and a result is handed to whichever frame is waiting for it:
	// native answers through evaluateJavascript, which only runs in the main frame.
	var shared = (function () {
		try {
			var t = window.top;
			if (!t.__lunacyNetFrames) { t.__lunacyNetFrames = []; }
			t.__lunacyNetFrames.push(window);
			return t;
		} catch (e) { return null; }
	})();
	function nextId() {
		var holder = shared || window;
		holder.__lunacyNetId = (holder.__lunacyNetId || 0) + 1;
		return holder.__lunacyNetId;
	}

	function absolute(url) { link.href = url; return link.href; }
	function crossOrigin(abs) {
		link.href = abs;
		return (link.protocol === "http:" || link.protocol === "https:") && link.host !== location.host &&
			!/\.media\.cryptofs\.apps$/.test(link.hostname);
	}
	function find(list, name) {
		name = name.toLowerCase();
		for (var i = 0; i < list.length; i++) { if (list[i][0].toLowerCase() === name) { return i; } }
		return -1;
	}
	// Handlers run as the browser runs them: an exception is reported, with the handler's own
	// stack, and the rest still run.
	function call(fn, self, e) {
		try { fn.call(self, e); } catch (err) { console.error("Uncaught " + (err && err.stack || err)); }
	}
	function toBase64(bytes) {
		var s = "";
		for (var i = 0; i < bytes.length; i += 8192) { s += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192)); }
		return btoa(s);
	}
	function fromBase64(b64) {
		var s = atob(b64), bytes = new Uint8Array(s.length);
		for (var i = 0; i < s.length; i++) { bytes[i] = s.charCodeAt(i); }
		return bytes;
	}
	function charsetOf(mime) { var m = /charset\s*=\s*"?([\w.:-]+)/i.exec(mime || ""); return m ? m[1] : ""; }
	// The old binary trick: charset=x-user-defined maps each byte to itself below 0x80 and to
	// U+F700 + byte above, as the TouchPad returned it.
	function userDefined(bytes) {
		var s = "", chunk = [];
		for (var i = 0; i < bytes.length; i++) {
			chunk.push(bytes[i] < 0x80 ? bytes[i] : 0xF700 + bytes[i]);
			if (chunk.length === 8192) { s += String.fromCharCode.apply(null, chunk); chunk = []; }
		}
		return s + String.fromCharCode.apply(null, chunk);
	}
	function stateError() { var e = new Error("INVALID_STATE_ERR: DOM Exception 11"); e.code = 11; return e; }
	// What a failed synchronous send threw on the TouchPad.
	function networkError() { var e = new Error("NETWORK_ERR: XMLHttpRequest Exception 101"); e.code = 101; return e; }

	function XHR() {
		var self = this;
		this._listeners = {};
		this._x = null;
		this._responseType = "";
		this._timeout = 0;
		this._withCredentials = false;
		this._mime = "";
		this._upload = { addEventListener: function () {}, removeEventListener: function () {} };
		EVENTS.forEach(function (t) { self["on" + t] = null; });
		this._reset();
	}
	var P = XHR.prototype;

	P._reset = function () {
		this._rs = 0; this._req = null; this._sent = false; this._id = 0;
		this._status = 0; this._headers = []; this._url = ""; this._text = ""; this._bytes = null; this._doc = undefined;
	};

	P._fire = function (type, loaded, total) {
		var self = this, e = {
			type: type, target: this, currentTarget: this, srcElement: this, bubbles: false, cancelable: false,
			lengthComputable: total > 0, loaded: loaded || 0, total: total || 0, timeStamp: Date.now(),
			preventDefault: function () {}, stopPropagation: function () {}, stopImmediatePropagation: function () {}
		};
		if (typeof this["on" + type] === "function") { call(this["on" + type], this, e); }
		(this._listeners[type] || []).slice().forEach(function (fn) { call(fn, self, e); });
	};
	P._state = function (s) { this._rs = s; this._fire("readystatechange"); };

	P.addEventListener = function (type, fn) {
		var l = this._listeners[type] || (this._listeners[type] = []);
		if (fn && l.indexOf(fn) < 0) { l.push(fn); }
	};
	P.removeEventListener = function (type, fn) {
		var l = this._listeners[type], i = l ? l.indexOf(fn) : -1;
		if (i >= 0) { l.splice(i, 1); }
	};
	P.dispatchEvent = function (e) { this._fire(e.type); return true; };

	// A same-origin request: the real XHR does the work and its events are passed on.
	P._attach = function (x) {
		var self = this;
		EVENTS.forEach(function (t) {
			x.addEventListener(t, function (ev) { if (self._x === x) { self._fire(t, ev.loaded, ev.total); } }, false);
		});
	};

	P.open = function (method, url, async, user, password) {
		this._cancel();
		this._reset();
		// open() ends any earlier request; its events no longer reach the page.
		var old = this._x;
		this._x = null;
		if (old) { try { old.abort(); } catch (e) {} }
		async = arguments.length < 3 || !!async;
		if (window.__lunacyFileUrl) { url = __lunacyFileUrl(url); }
		var abs = absolute(url);
		if (!crossOrigin(abs)) {
			var x = new Real();
			this._x = x;
			this._attach(x);
			x.open.apply(x, arguments);
			if (this._responseType) { try { x.responseType = this._responseType; } catch (e) {} }
			if (async && this._timeout) { x.timeout = this._timeout; }
			x.withCredentials = this._withCredentials;
			if (this._mime) { x.overrideMimeType(this._mime); }
			return;
		}
		method = String(method);
		if (/^(delete|get|head|options|post|put)$/i.test(method)) { method = method.toUpperCase(); }
		this._req = { method: method, url: abs, headers: [], async: async };
		if (user !== undefined && user !== null) { this._req.user = String(user); this._req.password = password == null ? "" : String(password); }
		this._state(1);
	};

	// The TouchPad let pages set any header, including Cookie, Host, Origin and User-Agent.
	P.setRequestHeader = function (name, value) {
		if (this._x) { return this._x.setRequestHeader(name, value); }
		if (this._rs !== 1 || this._sent) { throw stateError(); }
		var h = this._req.headers, i = find(h, name);
		if (i >= 0) { h[i][1] += ", " + value; } else { h.push([String(name), String(value)]); }
	};

	P.overrideMimeType = function (mime) {
		this._mime = String(mime);
		if (this._x) { this._x.overrideMimeType(mime); }
	};

	P.send = function (body) {
		if (this._x) { return arguments.length ? this._x.send(body) : this._x.send(); }
		if (this._rs !== 1 || this._sent) { throw stateError(); }
		var r = this._req, h = r.headers, payload = null, base64 = false;
		if (body !== undefined && body !== null && r.method !== "GET" && r.method !== "HEAD") {
			if (window.ArrayBuffer && (body instanceof ArrayBuffer || ArrayBuffer.isView(body))) {
				payload = toBase64(body instanceof ArrayBuffer ? new Uint8Array(body) : new Uint8Array(body.buffer, body.byteOffset, body.byteLength));
				base64 = true;
			} else if ((window.FormData && body instanceof FormData) || (window.Blob && body instanceof Blob)) {
				console.error("Lunacy: cross-origin send() of FormData or Blob isn't supported yet: " + r.url);
				return this._finish({ error: "error" }, !r.async);
			} else {
				payload = body.nodeType === 9 ? new XMLSerializer().serializeToString(body) : String(body);
				// As the TouchPad sent text: application/xml unless the page chose a type, and a
				// charset added to the page's type when it gave none.
				var i = find(h, "Content-Type");
				if (i < 0) { h.push(["Content-Type", "application/xml"]); }
				else if (!/charset=/i.test(h[i][1])) { h[i][1] += "; charset=UTF-8"; }
			}
		}
		var rt = this._responseType, over = charsetOf(this._mime);
		var req = {
			method: r.method, url: r.url, headers: h, body: payload, bodyBase64: base64,
			timeout: r.async ? this._timeout : 0,
			binary: rt === "arraybuffer" || rt === "blob" || /^x-user-defined$/i.test(over),
			charset: /^x-user-defined$/i.test(over) ? "" : over
		};
		if (r.user !== undefined) { req.user = r.user; req.password = r.password; }
		this._sent = true;
		if (!r.async) { return this._finish(JSON.parse(N.netSendSync(JSON.stringify(req))), true); }
		this._id = nextId();
		inFlight[this._id] = this;
		this._fire("loadstart", 0, 0);
		N.netSend(this._id, JSON.stringify(req));
	};

	//* This frame's request, if it is this frame's.
	window.__lunacyNetDeliver = function (id) {
		var x = inFlight[id];
		if (!x) { return false; }
		delete inFlight[id];
		var res = JSON.parse(N.netResult(id));
		if (x._id === id) { x._id = 0; x._finish(res, false); }
		return true;
	};
	//* Native calls this in the main frame; the request may be a child frame's.
	window.__lunacyNetDone = function (id) {
		if (window.__lunacyNetDeliver(id)) { return; }
		var all = [];
		try { all = (shared && shared.__lunacyNetFrames) || []; } catch (e) {}
		for (var i = 0; i < all.length; i++) {
			try {
				if (all[i] !== window && all[i].__lunacyNetDeliver && all[i].__lunacyNetDeliver(id)) { return; }
			} catch (e) {}
		}
	};

	// Delivers a result. The TouchPad's sequence: readyState 2, then 3 with progress when there
	// is a body, then 4 and load. A synchronous request goes straight to 4, and a failed one to
	// 4 and error, then its send() throws.
	P._finish = function (res, sync) {
		if (res.error) {
			this._status = 0;
			this._state(4);
			this._fire(res.error === "timeout" ? "timeout" : "error", 0, 0);
			this._fire("loadend", 0, 0);
			if (sync) { throw networkError(); }
			return;
		}
		this._status = res.status;
		this._headers = res.headers;
		this._url = res.url;
		if (res.base64 !== undefined) { this._bytes = fromBase64(res.base64); } else { this._text = res.text; }
		var len = this._bytes ? this._bytes.length : this._text.length;
		if (!sync) {
			this._state(2);
			if (len > 0) { this._state(3); this._fire("progress", len, len); }
		}
		this._state(4);
		this._fire("load", len, len);
		this._fire("loadend", len, len);
	};

	// Drops a native request without events: open() on a busy XHR.
	P._cancel = function () {
		if (this._id) { N.netAbort(this._id); delete inFlight[this._id]; this._id = 0; }
	};

	P.abort = function () {
		if (this._x) { return this._x.abort(); }
		if (this._id) {
			this._cancel();
			this._status = 0;
			this._state(4);
			this._fire("abort", 0, 0);
			this._fire("loadend", 0, 0);
		}
		this._rs = 0;
		this._sent = false;
	};

	P.getResponseHeader = function (name) {
		if (this._x) { return this._x.getResponseHeader(name); }
		if (this._rs < 2) { return null; }
		var v = [];
		name = String(name).toLowerCase();
		this._headers.forEach(function (h) { if (h[0].toLowerCase() === name) { v.push(h[1]); } });
		return v.length ? v.join(", ") : null;
	};

	// Set-Cookie included: the TouchPad showed it to pages.
	P.getAllResponseHeaders = function () {
		if (this._x) { return this._x.getAllResponseHeaders(); }
		if (this._rs < 2) { return ""; }
		return this._headers.map(function (h) { return h[0] + ": " + h[1] + "\r\n"; }).join("");
	};

	P._mimeType = function () { return this._mime || this.getResponseHeader("Content-Type") || ""; };
	P._textValue = function () {
		if (this._rs < 3) { return ""; }
		return this._bytes ? userDefined(this._bytes) : this._text;
	};
	P._document = function (html) {
		if (this._doc === undefined) {
			this._doc = null;
			var type = html ? "text/html" : (/html/i.test(this._mimeType()) ? "text/html" : "application/xml");
			try {
				var d = new DOMParser().parseFromString(this._textValue(), type);
				if (d && !d.getElementsByTagName("parsererror").length) { this._doc = d; }
			} catch (e) {}
		}
		return this._doc;
	};

	function prop(name, get, set) { Object.defineProperty(P, name, { get: get, set: set, enumerable: true, configurable: true }); }
	prop("readyState", function () { return this._x ? this._x.readyState : this._rs; });
	prop("status", function () { return this._x ? this._x.status : this._status; });
	// Always empty on the TouchPad for these requests.
	prop("statusText", function () { return this._x ? this._x.statusText : ""; });
	prop("responseURL", function () { return this._x ? this._x.responseURL : (this._rs >= 2 ? this._url : ""); });
	prop("responseText", function () {
		if (this._x) { return this._x.responseText; }
		if (this._responseType && this._responseType !== "text") { throw stateError(); }
		return this._textValue();
	});
	prop("responseXML", function () {
		if (this._x) { return this._x.responseXML; }
		if (this._rs < 4 || !/xml/i.test(this._mimeType())) { return null; }
		return this._document(false);
	});
	prop("response", function () {
		if (this._x) { return this._x.response; }
		var rt = this._responseType;
		if (!rt || rt === "text") { return this._textValue(); }
		if (this._rs < 4) { return null; }
		if (rt === "json") { try { return JSON.parse(this._text); } catch (e) { return null; } }
		if (rt === "arraybuffer") { return this._bytes ? this._bytes.buffer : null; }
		if (rt === "blob") { return this._bytes ? new Blob([this._bytes], { type: this.getResponseHeader("Content-Type") || "" }) : null; }
		if (rt === "document") { return this._document(/html/i.test(this._mimeType())); }
		return null;
	});
	prop("responseType", function () { return this._x ? this._x.responseType : this._responseType; },
		function (v) { this._responseType = String(v); if (this._x) { this._x.responseType = v; } });
	prop("timeout", function () { return this._x ? this._x.timeout : this._timeout; },
		function (v) { this._timeout = Number(v) || 0; if (this._x) { this._x.timeout = v; } });
	prop("withCredentials", function () { return this._x ? this._x.withCredentials : this._withCredentials; },
		function (v) { this._withCredentials = !!v; if (this._x) { this._x.withCredentials = v; } });
	prop("upload", function () { return this._x ? this._x.upload : this._upload; });

	["UNSENT", "OPENED", "HEADERS_RECEIVED", "LOADING", "DONE"].forEach(function (k, i) { XHR[k] = i; P[k] = i; });
	window.XMLHttpRequest = XHR;
})();
