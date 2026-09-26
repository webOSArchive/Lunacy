// Which face webOS's engine draws for a Prelude family name asked for in normal and bold.
// Palm's Calculator asks for "Prelude Medium" at font-weight: bold. Each case's widths (three
// strings) are compared with each font file's, loaded under a name of its own, so the report
// names the file the engine drew with - several when faces measure alike - or "none" (a
// synthesized face, or a font that isn't one of these).
// Read against the reference TouchPad with:
//   Workbench/tp.sh 'luna-send -n 1 palm://com.palm.applicationManager/launch
//                    "{\"id\":\"org.webosarchive.lunacy.fontprobe\"}"; sleep 6;
//                    grep FONTPROBE /var/log/messages'
// and in Lunacy with Workbench/cdp.sh eval 'window.__fontprobe' org.webosarchive.lunacy.fontprobe.
(function () {
	var out = document.getElementById("out"), results = {};
	var TEXTS = ["MC M+ 7 8 9 0123456789", "Calculator Memos Help", "iiilll WWWMMM ,.;:"];
	// Every face on the device, loaded from its own file so each is known for certain.
	var FILES = ["Prelude-Medium", "Prelude-Bold", "PreludeCondensed-Medium", "PreludeCondensed-Bold",
		"PreludeWGL-Light", "PreludeWGL-Medium", "PreludeWGL-Bold", "PreludeWGL-Black",
		"PreludeCondWGL-Light", "PreludeCondWGL-Medium", "PreludeCondWGL-Bold", "PreludeCondWGL-Black",
		"PreludeCompWGL-Light", "PreludeCompWGL-Medium", "PreludeCompWGL-Bold", "PreludeCompWGL-Black",
		"arial", "arialbd", "verdana", "verdanab", "georgia", "times", "cour"];

	function say(s) {
		try { console.log("FONTPROBE " + s); } catch (e) {}
		try { console.error("FONTPROBE " + s); } catch (e) {}
	}
	function width(family, weight) {
		var r = [];
		for (var i = 0; i < TEXTS.length; i++) {
			var d = document.createElement("span");
			d.className = "probe";
			d.style.fontFamily = family;
			d.style.fontWeight = weight;
			d.appendChild(document.createTextNode(TEXTS[i]));
			document.body.appendChild(d);
			r.push(Math.round(d.getBoundingClientRect().width * 10) / 10);
			d.parentNode.removeChild(d);
		}
		return r;
	}
	function same(a, b) {
		for (var i = 0; i < a.length; i++) { if (Math.abs(a[i] - b[i]) > 0.6) { return false; } }
		return true;
	}
	// When the default face is ready: an app that measures text as it starts (Enyo renders in
	// the body's script) gets whatever face is ready then. On a device Prelude is installed.
	// Measured against the same text in monospace and in Prelude once everything has loaded.
	function early(tag) {
		results.timing = results.timing || {};
		results.timing[tag] = [width("Prelude", "normal")[0], width("Prelude", "bold")[0]];
		say("timing " + tag + " = " + results.timing[tag].join(" "));
	}
	early("script");
	setTimeout(function () { early("timeout0"); }, 0);
	window.addEventListener("load", function () { early("onload"); });
	setTimeout(function () { early("t500"); }, 500);

	// The files travel in the package (fonts/, added when it is built: see fontprobe.sh), since
	// the device's WebKit won't load a font from /usr/share/fonts by URL.
	var BASE = "fonts/";
	var css = "";
	for (var f = 0; f < FILES.length; f++) {
		css += "@font-face{font-family:'probe-" + FILES[f] + "';src:url('" + BASE + FILES[f] + ".ttf')}\n";
	}
	var st = document.createElement("style");
	st.appendChild(document.createTextNode(css));
	document.head.appendChild(st);
	// An engine fetches a web font only once text uses it, so every face is used now and
	// measured once they have had time to arrive.
	for (var u = 0; u < FILES.length; u++) {
		var sp = document.createElement("span");
		sp.style.fontFamily = "'probe-" + FILES[u] + "'";
		sp.appendChild(document.createTextNode(FILES[u] + " "));
		out.parentNode.appendChild(sp);
	}

	function run() {
		var known = {}, k;
		for (var f = 0; f < FILES.length; f++) { known[FILES[f]] = width("'probe-" + FILES[f] + "'", "normal"); }
		results.known = known;
		for (k in known) { say("file " + k + " = " + known[k].join(" ")); }
		function name(v) {
			var hits = [];
			for (var f in known) { if (same(known[f], v)) { hits.push(f); } }
			return hits.length ? hits.join("|") : "none";
		}
		var families = ["Prelude", "Prelude Medium", "Prelude-Medium", "Prelude Bold", "Prelude-Bold",
			"Prelude Condensed", "PreludeCondensed",
			"PreludeWGL", "PreludeWGL Light", "PreludeWGL-Light", "PreludeWGL Medium", "PreludeWGL Bold", "PreludeWGL Black",
			"PreludeCondWGL", "PreludeCondWGL Light", "PreludeCondWGL Medium", "PreludeCondWGL Bold",
			"PreludeCompWGL", "PreludeCompWGL Medium",
			// The names inside the files (fc-scan), where they differ from the files' own names.
			"PreludeWGL-Medium", "PreludeWGL-Bold", "PreludeWGL-Black",
			"PreludeCondensedWGL", "PreludeCondensedWGL Light", "PreludeCondensedWGL Medium",
			"PreludeCondensedWGL Bold", "PreludeCondensedWGL Black",
			"PreludeCompressedWGL", "PreludeCompressedWGL Light", "PreludeCompressedWGL Medium",
			"PreludeCompressedWGL Bold", "PreludeCompressedWGL Black",
			"Prelude Condensed Medium", "Prelude Condensed Bold", "Prelude-CondensedMedium", "Prelude-CondensedBold",
			"sans-serif", "serif", "Arial", "Helvetica", "no-such-font"];
		var weights = ["normal", "bold", "300", "900"];
		results.cases = {};
		for (var i = 0; i < families.length; i++) {
			for (var j = 0; j < weights.length; j++) {
				var v = width("'" + families[i] + "'", weights[j]);
				var key = families[i] + " @" + weights[j];
				results.cases[key] = [v, name(v)];
				say(key + " = " + name(v) + " " + v.join(" "));
				out.appendChild(document.createTextNode(key + " = " + name(v) + " " + v.join(" ") + "\n"));
			}
		}
		window.__fontprobe = results;
	}
	// The font files load asynchronously, and slowly on the device.
	setTimeout(run, 20000);
}());
