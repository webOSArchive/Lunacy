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
					{kind: "RowGroup", caption: $L("webOS Identity"), components: [
						{kind: "InfoRow", name: "reports", label: $L("Apps Are Told")},
						{kind: "InfoRow", name: "webosSerial", label: $L("Serial Number")},
						{kind: "InfoRow", name: "deviceId", label: $L("Device ID"), small: true, tapHighlight: true, onclick: "editDeviceId"}
					]},
					{name: "deviceIdNote", className: "note"},
					{kind: "RowGroup", caption: $L("Lunacy"), components: [
						{kind: "InfoRow", name: "version", label: $L("Version")},
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
		// Changing the device id: a power user's migration, so it explains itself and takes the
		// id written however it was copied off the old device.
		// Enyo 1's own dialog, as every app's dialogs look here: it isn't centred the way the
		// framework's openAtCenter() intends, which is worth comparing against a TouchPad -
		// it would be the same for every app's dialogs, not just this one.
		{kind: "Dialog", name: "idDialog", lazy: false, components: [
			{content: $L("webOS Device ID"), className: "dialog-title"},
			{className: "note", content: $L("webOS Archive's services and some apps know a device by this id, and old licences were tied to it. Set it to a TouchPad's own id to carry that device's history into Lunacy.")},
			{kind: "Input", name: "idInput", className: "id-input", spellcheck: false, autocapitalize: "lowercase", hint: $L("40 hexadecimal digits")},
			{name: "idError", className: "id-error", showing: false},
			{className: "note", content: $L("Apps that are open keep the old id until they are closed and started again.")},
			{layoutKind: "HFlexLayout", pack: "center", components: [
				{kind: "Button", caption: $L("Cancel"), onclick: "closeDeviceId"},
				// Only once an id has been carried over, and it puts back the one derived from
				// this device rather than a new random one: a service counting devices
				// shouldn't see a new one every time somebody taps a button.
				{kind: "Button", name: "resetId", showing: false, caption: $L("Use This Device's ID"), onclick: "resetDeviceId"},
				{kind: "Button", className: "enyo-button-affirmative", caption: $L("Save"), onclick: "saveDeviceId"}
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
			{name: "openSettings", method: "android/openSettings", onResponse: "openedSettings"},
			{name: "setDeviceId", method: "system/setDeviceId", onResponse: "deviceIdSet"}
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
		// The rows that say the quiet part: apps are told this is a webOS device, because that
		// is the contract they were written against. Nothing else here pretends.
		var w = r.webos || {};
		this.$.reports.setValue(w.model ? w.model + ", " + w.version : l.reportsWebOS);
		this.$.webosSerial.setValue(w.serial);
		this.deviceId = w.nduid || "";
		this.$.deviceId.setValue(this.deviceId);
		this.carriedOver = w.nduidSource === "user";
		this.$.deviceIdNote.setContent(this.carriedOver
			? $L("This device id was carried over by you. Tap it to change it.")
			: $L("This device id comes from this tablet's own hardware, so it stays the same if Lunacy is reinstalled. Tap it to use a TouchPad's id instead."));
		this.$.enyo.setValue(l.enyo);
		this.$.node.setValue(l.node || $L("not available"));
		this.$.apps.setValue(String(l.apps));
		this.$.services.setValue(this.services(l.services));
		this.$.screen.setValue(s.width ? s.width + " × " + s.height + " px, " + s.dpi + " dpi" : "");
		// The card's size, and - when the page's own pixels don't land on the screen's - what
		// they come out as instead. That is what puts the faint seams in framework artwork,
		// and it is a rounding coincidence of the screen rather than anything Lunacy chose.
		var card = s.cardWidth ? s.cardWidth + " × " + s.cardHeight + " " + $L("TouchPad px") : "";
		if (card && s.pixelGrid && s.pixelGrid !== "1:1") {
			card += " — " + $L("page") + " " + s.pixelGrid;
		}
		this.$.card.setValue(card);
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
	editDeviceId: function() {
		this.$.idInput.setValue(this.deviceId || "");
		this.$.idError.setShowing(false);
		this.$.resetId.setShowing(Boolean(this.carriedOver));
		this.$.idDialog.open();
	},
	closeDeviceId: function() {
		this.$.idDialog.close();
	},
	resetDeviceId: function() {
		this.$.setDeviceId.call({reset: true});
	},
	saveDeviceId: function() {
		this.$.setDeviceId.call({nduid: this.$.idInput.getValue()});
	},
	deviceIdSet: function(inSender, r) {
		if (!r || !r.returnValue) {
			this.$.idError.setContent(r && r.errorText || $L("Couldn't set the device id."));
			this.$.idError.setShowing(true);
			return;
		}
		this.$.idDialog.close();
		this.refresh();
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
	// enyo-item is Item's own class and carries the row's padding; naming a class here
	// replaces it rather than adding to it, so it has to be spelled out.
	className: "enyo-item info-row",
	tapHighlight: false,
	layoutKind: "HFlexLayout",
	//* small: for a value too long for the row's usual size, like a 40-digit device id.
	published: {label: "", value: "", small: false},
	components: [
		{name: "label", className: "info-label"},
		{name: "value", flex: 1, className: "info-value"}
	],
	create: function() {
		this.inherited(arguments);
		this.labelChanged();
		this.valueChanged();
		this.$.value.addRemoveClass("info-value-small", this.small);
	},
	labelChanged: function() {
		this.$.label.setContent(this.label);
	},
	valueChanged: function() {
		// An empty value means Lunacy couldn't find one out; don't leave a blank row.
		this.$.value.setContent(this.value === "" || this.value === undefined || this.value === "undefined" ? $L("unknown") : this.value);
	}
});
