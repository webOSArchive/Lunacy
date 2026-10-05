// Keyring SD 0.0.6's item fields (Workbench/probe, mojoprobe 0.0.3), to compare the reference
// TouchPad with Lunacy: where a labelled TextField's text sits, and what a tap and a hold do
// to a holdToEnable field. The geometry is logged once the scene is up; every focus, blur,
// tap, hold and holdEnd on the fields is logged as it happens, with the time since launch.
function MainAssistant() {}

MainAssistant.prototype.setup = function () {
	this.t0 = Date.now();
	this.item = { title: "Bank", username: "", pass: "" };
	// Keyring's own attributes, from item-assistant.js.
	var base = {
		autoFocus: false,
		holdToEnable: true,
		focusMode: Mojo.Widget.focusSelectMode,
		changeOnKeyPress: false,
		textCase: Mojo.Widget.steModeLowerCase,
		autoReplace: false,
		requiresEnterKey: false
	};
	var fields = { title: "Title", username: "Username", pass: "Password" };
	for (var f in fields) {
		var attrs = Object.clone(base);
		attrs.hintText = fields[f];
		attrs.inputName = f;
		attrs.modelProperty = f;
		this.controller.setupWidget(f + "Field", attrs, this.item);
	}
	this.controller.window.setTimeout(this.report.bind(this), 1500);
};

MainAssistant.prototype.say = function (s) {
	s = "MOJOPROBE +" + (Date.now() - this.t0) + "ms " + s;
	try { console.log(s); } catch (e) {}
	try { console.error(s); } catch (e) {}
	return s;
};

MainAssistant.prototype.activate = function () {
	var doc = this.controller.document, self = this;
	function name(e) {
		var t = e.target;
		return t ? (t.tagName + "#" + (t.id || "") + "." + (t.className || "")) : "?";
	}
	var log = function (e) { self.say("event " + e.type + " " + name(e)); };
	var ids = ["titleField", "usernameField", "passField"];
	for (var i = 0; i < ids.length; i++) {
		var el = doc.getElementById(ids[i]);
		el.addEventListener("focus", log, true);
		el.addEventListener("blur", log, true);
		el.addEventListener("mousedown", log, true);
		el.addEventListener("mouseup", log, true);
		this.controller.listen(el, Mojo.Event.tap, log, true);
		this.controller.listen(el, Mojo.Event.hold, log, true);
		this.controller.listen(el, Mojo.Event.holdEnd, log, true);
	}
};

MainAssistant.prototype.report = function () {
	var doc = this.controller.document, win = this.controller.window, lines = [], self = this;
	function rec(k, v) {
		var s;
		try { s = JSON.stringify(v); } catch (e) { s = String(v); }
		lines.push(self.say(k + " = " + s));
	}
	function box(e) {
		if (!e) { return null; }
		var r = e.getBoundingClientRect();
		return [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)];
	}
	function get(id) { return doc.getElementById(id); }

	rec("win.inner", [win.innerWidth, win.innerHeight]);
	var ids = ["titleField", "usernameField", "passField"];
	for (var i = 0; i < ids.length; i++) {
		var f = get(ids[i]);
		var row = f.parentNode.parentNode.parentNode;
		var label = f.parentNode.getElementsByTagName("div")[0];
		rec(ids[i] + ".row", box(row));
		rec(ids[i] + ".label", box(label));
		rec(ids[i] + ".field", box(f));
		var kids = f.childNodes;
		for (var k = 0; k < kids.length; k++) {
			if (kids[k].nodeType !== 1) { continue; }
			rec(ids[i] + ".child" + k, [kids[k].tagName, kids[k].id.replace(/.*Field/, ""), kids[k].className,
				box(kids[k]), kids[k].getAttribute("style"), win.getComputedStyle(kids[k]).display]);
		}
	}
	rec("bareFloat", box(get("bareFloat")));
	rec("bareBlock", box(get("bareBlock")));
	rec("bare", box(get("bare")));
	lines.push(this.say("--- end ---"));
	var r = get("report");
	if (r) { r.innerHTML = ""; r.appendChild(doc.createTextNode(lines.join("\n"))); }
};
