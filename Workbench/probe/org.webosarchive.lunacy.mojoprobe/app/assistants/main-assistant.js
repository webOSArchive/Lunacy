// Measures drPodder's playback row, with Mojo's own ProgressSlider in it, so the same
// markup can be read on the reference TouchPad and in Lunacy and the two compared. The row
// is three table-cells (20% / 60% / 20%) in a plain block, which is the shape a lot of Mojo
// chrome has: whether the anonymous table around them takes the container's width or
// shrinks to its content decides whether the row spans the card or huddles in the corner.
function MainAssistant() {}

MainAssistant.prototype.setup = function () {
	this.progressModel = { value: 20, progress: 0, progressStart: 0, progressEnd: 0 };
	this.controller.setupWidget("progress",
		{ sliderProperty: "value", minValue: 0, maxValue: 100, round: true },
		this.progressModel);
	this.controller.window.setTimeout(this.report.bind(this), 1500);
};

MainAssistant.prototype.report = function () {
	var doc = this.controller.document, win = this.controller.window;
	var lines = [];

	function say(s) {
		try { console.log("MOJOPROBE " + s); } catch (e) {}
		try { console.error("MOJOPROBE " + s); } catch (e) {}
		lines.push(s);
	}
	function rec(k, v) {
		var s;
		try { s = JSON.stringify(v); } catch (e) { s = String(v); }
		say(k + " = " + s);
	}
	function box(e) {
		if (!e) { return null; }
		var r = e.getBoundingClientRect();
		return [Math.round(r.left * 100) / 100, Math.round(r.top * 100) / 100,
			Math.round(r.width * 100) / 100, Math.round(r.height * 100) / 100];
	}
	function get(id) { return doc.getElementById(id); }

	rec("win.inner", [win.innerWidth, win.innerHeight]);
	rec("win.client", [doc.documentElement.clientWidth, doc.documentElement.clientHeight]);
	rec("body.offsetHeight", doc.body.offsetHeight);
	rec("html.offsetHeight", doc.documentElement.offsetHeight);

	rec("topContent", box(get("topContent")));
	rec("progress-info", box(get("progress-info")));
	rec("elapsed", box(get("playback-progress")));
	rec("slider", box(get("progress")));
	rec("duration", box(get("playback-remaining")));

	// What the slider's own children came out as: if one of them is wide, that is what pushes
	// the anonymous table out to the container's width.
	var slider = get("progress"), kids = [];
	if (slider) {
		var all = slider.getElementsByTagName("div");
		for (var i = 0; i < all.length && i < 12; i++) {
			kids.push(all[i].className + " " + JSON.stringify(box(all[i])));
		}
	}
	rec("slider.children", kids);
	rec("slider.innerHTML", slider ? slider.innerHTML.replace(/\s+/g, " ").substring(0, 300) : null);
	rec("slider.display", slider ? win.getComputedStyle(slider).display : null);
	rec("slider.computedWidth", slider ? win.getComputedStyle(slider).width : null);
	say("--- end ---");

	var r = get("report");
	if (r) { r.innerHTML = ""; r.appendChild(doc.createTextNode(lines.join("\n"))); }
};
