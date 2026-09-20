/**
 * Lunacy's Device Info.
 *
 * Palm's app reports a webOS device. Lunacy isn't one: it is a shell and a set of
 * frameworks on somebody's Android tablet, and the honest answer to "what am I running on?"
 * has three parts - the device, Android, and Lunacy itself. So the rows Palm's app filled
 * from the Palm profile, the carrier and the modem are replaced by the ones that decide
 * whether an app will work here.
 *
 * Everything comes from palm://org.webosarchive.lunacy/system/getEnvironment. That service
 * is Lunacy's own, under its own name: none of this is webOS, and the bus says so.
 */
enyo.kind({
	name: "DeviceInfo",
	kind: "VFlexBox",
	components: [
		{kind: "Control", className: "enyo-toolbar-light header-welcome", components: [
			{kind: "Image", src: "images/header-icon-deviceinfo.png"},
			{content: $L("Device Information"), style: "padding-left: 10px;"}
		]},
		{className: "header-shadow"},
		{kind: "Scroller", flex: 1, components: [
			{kind: "Pane", flex: 1, components: [
				{kind: "HFlexBox", flex: 1, pack: "center", align: "center", components: [
					{kind: "Spinner"},
					{content: $L("Reading the environment...")}
				]},
				{name: "info", className: "box-center", components: [
					{kind: "RowGroup", caption: $L("Device"), components: [
						{kind: "InfoRow", name: "model", label: $L("Model")},
						{kind: "InfoRow", name: "android", label: $L("Android")},
						{kind: "InfoRow", name: "webview", label: $L("WebView")},
						{kind: "InfoRow", name: "webviewPackage", label: $L("Provided By")},
						{kind: "InfoRow", name: "memory", label: $L("RAM")},
						{kind: "InfoRow", name: "storage", label: $L("Storage")},
						{kind: "InfoRow", name: "available", label: $L("Available")},
						{kind: "InfoRow", name: "battery", label: $L("Battery")},
						{kind: "InfoRow", name: "serial", label: $L("Serial Number")}
					]},
					{kind: "RowGroup", caption: $L("Lunacy"), components: [
						{kind: "InfoRow", name: "version", label: $L("Version")},
						{kind: "InfoRow", name: "reports", label: $L("Reports To Apps As")},
						{kind: "InfoRow", name: "enyo", label: $L("Enyo Framework")},
						{kind: "InfoRow", name: "node", label: $L("Node (JS Services)")},
						{kind: "InfoRow", name: "apps", label: $L("Apps Installed")},
						{kind: "InfoRow", name: "services", label: $L("JS Services")}
					]},
					{name: "serviceNames", className: "note", showing: false},
					{kind: "RowGroup", caption: $L("Display"), components: [
						{kind: "InfoRow", name: "screen", label: $L("Screen")},
						{kind: "InfoRow", name: "card", label: $L("Card")},
						{kind: "InfoRow", name: "scale", label: $L("Scale")},
						{kind: "InfoRow", name: "orientation", label: $L("Orientation")}
					]},
					{content: $L("Apps see the card size in TouchPad pixels, so they lay out as they did on a TouchPad whatever this screen's density is."), className: "note"},
					{kind: "Button", className: "enyo-button", caption: $L("Android Device Info"), onclick: "openAndroid"},
					{content: $L("Android owns the rest: its own About screen has the hardware, the build and the legal notices."), className: "note"}
				]}
			]}
		]},
		{kind: "Dialog", lazy: false, components: [
			{name: "errorText"},
			{layoutKind: "HFlexLayout", pack: "center", components: [
				{kind: "Button", caption: $L("Close"), onclick: "closeError"}
			]}
		]},
		// The shell keeps PalmSystem.screenOrientation current and the window resizes, so Enyo
		// raises windowRotated: the display rows are worth re-reading then.
		{kind: "ApplicationEvents", onWindowRotated: "refresh"},
		{kind: "AppMenu", components: [
			{caption: $L("Refresh"), onclick: "refresh"}
		]},
		{kind: "PalmService", service: "palm://org.webosarchive.lunacy/", components: [
			{name: "getEnvironment", method: "system/getEnvironment", onResponse: "gotEnvironment"},
			{name: "openSettings", method: "android/openSettings", onResponse: "openedSettings"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.refresh();
	},
	refresh: function() {
		this.$.getEnvironment.call({});
	},
	gotEnvironment: function(inSender, r) {
		if (!r || !r.returnValue) {
			this.showError(r && r.errorText || $L("Lunacy's own service didn't answer."));
			return;
		}
		var d = r.device || {}, a = r.android || {}, w = r.webview || {}, l = r.lunacy || {}, s = r.display || {};
		this.$.model.setValue(this.join(d.manufacturer, d.model));
		this.$.android.setValue(a.release ? a.release + " (API " + a.sdk + ", " + a.abi + ")" : "");
		this.$.webview.setValue(w.version || w.chromium || $L("unknown"));
		this.$.webviewPackage.setValue(w.package);
		this.$.memory.setValue(this.bytes(d.ram));
		this.$.storage.setValue(this.bytes(d.storageTotal));
		this.$.available.setValue(this.bytes(d.storageFree));
		this.$.battery.setValue(d.battery >= 0 ? d.battery + "%" + (d.charging ? " " + $L("(charging)") : "") : $L("unknown"));
		this.$.serial.setValue(d.serial);
		this.$.version.setValue(l.version ? l.version + " (" + $L("build") + " " + l.build + ")" : "");
		// The one row that says the quiet part: apps are told this is a webOS device, because
		// that is the contract they were written against. Nothing else here pretends.
		this.$.reports.setValue(l.reportsAs ? l.reportsAs + ", " + l.reportsWebOS : l.reportsWebOS);
		this.$.enyo.setValue(l.enyo);
		this.$.node.setValue(l.node || $L("not available"));
		this.$.apps.setValue(String(l.apps));
		this.$.services.setValue(this.services(l.services));
		this.$.screen.setValue(s.width ? s.width + " × " + s.height + " px, " + s.dpi + " dpi" : "");
		this.$.card.setValue(s.cardWidth ? s.cardWidth + " × " + s.cardHeight + " " + $L("TouchPad px") : "");
		this.$.scale.setValue(s.scale ? s.scale + "×" : "");
		this.$.orientation.setValue(s.orientation);
		this.$.pane.selectViewByName("info");
	},
	//* The count goes in the row; the names are too long for it, so they wrap underneath.
	services: function(names) {
		this.$.serviceNames.setShowing(Boolean(names && names.length));
		if (!names || !names.length) { return $L("none"); }
		this.$.serviceNames.setContent(names.join(", "));
		return String(names.length);
	},
	join: function(a, b) {
		if (!a) { return b || ""; }
		// "HP HP 10 G2" reads badly; the model often carries the maker already.
		return String(b).indexOf(a) === 0 ? b : a + " " + b;
	},
	bytes: function(n) {
		if (!n) { return ""; }
		var gb = n / (1024 * 1024 * 1024);
		return gb >= 1 ? (Math.round(gb * 10) / 10) + " GB" : Math.round(n / (1024 * 1024)) + " MB";
	},
	openAndroid: function() {
		this.$.openSettings.call({panel: "about"});
	},
	openedSettings: function(inSender, r) {
		if (!r || !r.returnValue) { this.showError(r && r.errorText || $L("Couldn't open Android's settings.")); }
	},
	showError: function(text) {
		this.$.errorText.setContent(text);
		this.$.dialog.open();
	},
	closeError: function() {
		this.$.dialog.close();
	}
});

/** One label-and-value row, as webOS's settings apps drew them. */
enyo.kind({
	name: "InfoRow",
	kind: "Item",
	tapHighlight: false,
	layoutKind: "HFlexLayout",
	published: {label: "", value: ""},
	components: [
		{name: "label", className: "info-label"},
		{name: "value", flex: 1, className: "info-value"}
	],
	create: function() {
		this.inherited(arguments);
		this.labelChanged();
		this.valueChanged();
	},
	labelChanged: function() {
		this.$.label.setContent(this.label);
	},
	valueChanged: function() {
		// An empty value means Lunacy couldn't find one out; don't leave a blank row.
		this.$.value.setContent(this.value === "" || this.value === undefined || this.value === "undefined" ? $L("unknown") : this.value);
	}
});
