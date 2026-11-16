// Luna DE's log window: every app page's console, in one place, as palm-log -f gave it on a
// device. main.js keeps the lines (from when Luna DE started) and sends each new one.
"use strict";
(function () {
	var MAX = 5000;
	var box = document.getElementById("lines"), filter = document.getElementById("filter"), follow = document.getElementById("follow");
	function shown(el) {
		var f = filter.value.toLowerCase();
		return document.getElementById("lv-" + el.dataset.level).checked && (!f || el.dataset.text.indexOf(f) >= 0);
	}
	function span(cls, text) { var s = document.createElement("span"); s.className = cls; s.textContent = text; return s; }
	function add(line) {
		var el = document.createElement("div");
		el.className = "l " + line.level;
		el.dataset.level = line.level;
		el.dataset.text = (line.app + " " + line.message).toLowerCase();
		el.appendChild(span("t", line.time));
		el.appendChild(span("a", line.app));
		el.appendChild(span("m", line.message));
		var s = span("s", line.source); s.title = line.source; el.appendChild(s);
		if (!shown(el)) { el.classList.add("hide"); }
		box.appendChild(el);
		while (box.childNodes.length > MAX) { box.removeChild(box.firstChild); }
	}
	function bottom() { if (follow.checked) { box.scrollTop = box.scrollHeight; } }
	function refilter() {
		for (var i = 0; i < box.childNodes.length; i++) { box.childNodes[i].classList.toggle("hide", !shown(box.childNodes[i])); }
		bottom();
	}
	filter.addEventListener("input", refilter);
	["debug", "info", "warning", "error"].forEach(function (l) { document.getElementById("lv-" + l).addEventListener("change", refilter); });
	follow.addEventListener("change", bottom);
	document.getElementById("clear").addEventListener("click", function () { window.lunaLog.clear(); box.textContent = ""; });
	window.lunaLog.history().then(function (lines) {
		lines.forEach(add); bottom();
		window.lunaLog.onLine(function (line) { add(line); bottom(); });
	});
})();
