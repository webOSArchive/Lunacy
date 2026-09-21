// What this device puts on the wire from an app context: the user agent and X-Palm-Carrier.
// httpbin.org echoes the request back. webOS 2.2.4 does not carry console.log to palm-log
// (measured: only errors arrive, at any --system-log-level), so this reports through
// console.error. Read with: palm-log -f org.webosarchive.lunacy.netprobe
(function () {
	var out = document.getElementById("out");
	function rec(k, v) {
		var s;
		try { s = JSON.stringify(v); } catch (e) { s = String(v); }
		out.appendChild(document.createTextNode(k + " = " + s + "\n"));
		console.error("NETPROBE " + k + " " + s);
	}
	rec("page.userAgent", navigator.userAgent);
	try { rec("PalmSystem.deviceInfo", JSON.parse(PalmSystem.deviceInfo)); }
	catch (e) { rec("PalmSystem.deviceInfo.raw", String(window.PalmSystem && PalmSystem.deviceInfo)); }
	try { rec("PalmSystem.version", PalmSystem.version); } catch (e) { rec("PalmSystem.version", "THROWS"); }
	try { rec("PalmSystem.screenOrientation", PalmSystem.screenOrientation); } catch (e) {}
	var x = new XMLHttpRequest();
	x.onreadystatechange = function () {
		if (x.readyState !== 4) { return; }
		rec("wire.status", x.status);
		try {
			var seen = JSON.parse(x.responseText);
			rec("wire.headers", seen.headers);
		} catch (e) {
			rec("wire.parseError", String(e));
			rec("wire.text", String(x.responseText).slice(0, 600));
		}
	};
	try {
		x.open("GET", "http://httpbin.org/get", true);
		x.send(null);
	} catch (e) {
		rec("wire.throw", String(e));
	}
})();
