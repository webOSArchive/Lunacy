/**
 * Lunacy's Software Manager.
 *
 * Palm's app listed the device's apps under two tabs, Apps and Other, each row an icon, the
 * title and vendor, and the version on the right. Here the tabs are webOS and Android, and
 * each row has a switch: Show keeps the app on the launcher, Hide takes it off the launcher,
 * the dock and Just Type. A hidden app is still installed and still opens from another app.
 *
 * The list comes from palm://org.webosarchive.lunacy/launcher/listApps, which Lunacy only
 * answers for its own apps, and stays subscribed, so an app installed, removed, hidden or
 * shown elsewhere turns up here at once. Software Manager can't hide itself: it is how a
 * hidden app comes back.
 *
 * When Lunacy isn't Android's home app, a webOS app's row offers Pin in place of the switch:
 * launcher/pin asks Android's home screen for an icon that opens the app straight away, and
 * someone who keeps Android's launcher never needs Lunacy's, so there is nothing to hide.
 * Each tab is its own list in a Pane, as Palm's were, so each keeps its own place, and each has
 * its own background (enyo-bg), as Palm's did: Enyo's Fade brings the new view in over the old
 * one without fading the old one out, so a transparent view shows both lists at once.
 */
enyo.kind({
	name: "SoftwareManager",
	kind: "VFlexBox",
	className: "swManager",
	components: [
		{kind: "ApplicationEvents", onWindowActivated: "askPinStatus"},
		{kind: "Header", pack: "center", className: "page-header", components: [
			{className: "header-icon"},
			{content: $L("Software Manager"), style: "padding-left: 10px;"}
		]},
		{kind: "VFlexBox", flex: 1, className: "enyo-bg", components: [
			{layoutKind: "HFlexLayout", pack: "center", components: [
				{name: "tabs", kind: "RadioToolButtonGroup", className: "app-tabs", onChange: "tabChanged", components: [
					{label: $L("webOS"), value: "webos"},
					{label: $L("Android"), value: "android"}
				]}
			]},
			{name: "pane", kind: "Pane", flex: 1, components: [
				{name: "webos", kind: "Scroller", className: "enyo-bg", components: [
					{className: "managerbox-outer item-center", components: [
						{name: "noApps_webos", className: "no-apps", showing: false},
						{name: "list_webos"}
					]}
				]},
				{name: "android", kind: "Scroller", className: "enyo-bg", components: [
					{className: "managerbox-outer item-center", components: [
						{name: "noApps_android", className: "no-apps", showing: false},
						{name: "list_android"}
					]}
				]}
			]}
		]},
		{name: "scrim", kind: "Scrim", layoutKind: "VFlexLayout", align: "center", pack: "center", showing: true, components: [
			{kind: "SpinnerLarge", showing: true}
		]},
		{kind: "PalmService", service: "palm://org.webosarchive.lunacy/", components: [
			{name: "listApps", method: "launcher/listApps", subscribe: true, onSuccess: "gotApps", onFailure: "listFailed"},
			{name: "setHidden", method: "launcher/setHidden", onSuccess: "hiddenSet", onFailure: "hiddenSet"},
			{name: "pinStatus", method: "launcher/pinStatus", onSuccess: "gotPinStatus"},
			{name: "pin", method: "launcher/pin", onSuccess: "pinned", onFailure: "pinned"}
		]},
		{name: "errorDialog", kind: "ModalDialog", caption: $L("Software Manager"), components: [
			{name: "errorText", className: "enyo-paragraph"},
			{kind: "Button", caption: $L("OK"), onclick: "closeError"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.apps = [];
		this.tab = "webos";
		this.$.tabs.setValue(this.tab);
		this.pinning = false;
		this.askPinStatus();
		this.$.listApps.call({subscribe: true});
	},
	//* Whether to offer Pin: Lunacy isn't the home app, and the home app takes pins. Asked again each time the card comes back, as the home app may have changed.
	askPinStatus: function() {
		this.$.pinStatus.call({});
	},
	gotPinStatus: function(inSender, r) {
		var pinning = Boolean(r && !r.lunacyIsHome && r.canPin);
		if (pinning === this.pinning) { return; }
		this.pinning = pinning;
		if (this.rendered) { this.renderList("webos"); }
	},
	gotApps: function(inSender, r) {
		var apps = r.apps || [];
		apps.sort(function(a, b) {
			var x = a.title.toLowerCase(), y = b.title.toLowerCase();
			return x < y ? -1 : (x > y ? 1 : 0);
		});
		this.$.scrim.hide();
		// The same apps as before, one hidden or shown: only the switches change, so the list
		// doesn't jump back to its top.
		if (this.sameApps(apps)) {
			this.apps = apps;
			this.updateSwitches();
			return;
		}
		this.apps = apps;
		this.switches = {};
		this.renderList("webos");
		this.renderList("android");
		this.rendered = true;
		this.sizeSwitches();
	},
	listFailed: function(inSender, r) {
		this.$.scrim.hide();
		var text = (r && r.errorText) || $L("The apps couldn't be listed.");
		this.$.noApps_webos.setContent(text); this.$.noApps_webos.show();
		this.$.noApps_android.setContent(text); this.$.noApps_android.show();
	},
	tabChanged: function() {
		this.tab = this.$.tabs.getValue();
		this.$.pane.selectViewByName(this.tab);
		this.sizeSwitches();
	},
	appsOf: function(kind) {
		return this.apps.filter(function(a) { return a.kind === kind; });
	},
	sameApps: function(apps) {
		var old = this.apps;
		if (!this.rendered || old.length !== apps.length) { return false; }
		for (var i = 0; i < apps.length; i++) {
			if (old[i].id !== apps[i].id || old[i].title !== apps[i].title || old[i].version !== apps[i].version) { return false; }
		}
		return true;
	},
	//* One row per app in a tab's list: the framed icon, the title, the vendor and version, and its switch.
	renderList: function(kind) {
		var list = this.$["list_" + kind], apps = this.appsOf(kind);
		list.destroyControls();
		for (var i = 0; i < apps.length; i++) {
			var a = apps[i];
			var details = a.vendor ? a.vendor + (a.version ? " · " + a.version : "") : a.version;
			list.createComponent({kind: "Item", tapHighlight: false, className: "enyo-item app-row", layoutKind: "HFlexLayout", align: "center", components: [
				// An Image, as Palm's rows had: a style string would be split at the data: URL's ";".
				{kind: "Image", className: "appIcon", src: a.icon || "images/swmanager_48x48.png"},
				{kind: "VFlexBox", flex: 1, className: "app-details", components: [
					{content: enyo.string.escapeHtml(a.title), className: "appTitle enyo-text-ellipsis"},
					{content: enyo.string.escapeHtml(details || ""), className: "appVendor enyo-text-ellipsis"}
				]},
				{kind: "HFlexBox", align: "center", components: [this.pinning && kind === "webos" ?
					{kind: "Button", caption: $L("Pin"), className: "pin-button", appId: a.id, onclick: "pinClicked"} :
					{name: "switch_" + kind + "_" + i, kind: "ToggleButton", onLabel: $L("Show"), offLabel: $L("Hide"),
						state: !a.hidden, disabled: !a.canHide, appId: a.id, onChange: "switched"}
				]}
			]}, {owner: this});
			if (this.$["switch_" + kind + "_" + i]) { this.switches[a.id] = this.$["switch_" + kind + "_" + i]; }
		}
		var noApps = this.$["noApps_" + kind];
		noApps.setContent(kind === "android" ? $L("No Android apps are shown in the launcher.") : $L("You have no apps installed."));
		noApps.setShowing(apps.length === 0);
		list.render();
	},
	/**
	 * Every switch as wide as its wider label. Enyo sizes the bar by the label it shows, and
	 * the other is display: none, so both are measured once, shown for a moment, and the
	 * bars get the wider one's width as their least.
	 */
	sizeSwitches: function() {
		if (this.switchRule) { return; }
		// A switch in the tab on screen: the other tab's are in a hidden view and measure 0.
		var first, tab = this.tab;
		for (var i = 0; i < this.apps.length && !first; i++) {
			if (this.apps[i].kind === tab) { first = this.switches[this.apps[i].id]; }
		}
		var on = first && first.$.labelOn.hasNode(), off = first && first.$.labelOff.hasNode();
		if (!on || !off) { return; }
		var shown = [on.style.display, off.style.display];
		on.style.display = off.style.display = "inline";
		var w = Math.max(on.offsetWidth, off.offsetWidth);
		on.style.display = shown[0]; off.style.display = shown[1];
		if (!w) { return; }
		this.switchRule = document.createElement("style");
		this.switchRule.textContent = ".app-row .enyo-toggle-button-bar { min-width: " + w + "px; }";
		document.head.appendChild(this.switchRule);
	},
	updateSwitches: function() {
		var apps = this.apps;
		for (var i = 0; i < apps.length; i++) {
			var s = this.switches && this.switches[apps[i].id];
			if (s && s.getState() === Boolean(apps[i].hidden)) { s.setState(!apps[i].hidden); }
		}
	},
	pinClicked: function(inSender) {
		this.$.pin.call({id: inSender.appId});
	},
	pinned: function(inSender, r) {
		// Android's home screen asks the user itself; only a refusal is said here.
		if (!r || !r.returnValue) { this.showError((r && r.errorText) || $L("The app couldn't be pinned.")); }
	},
	switched: function(inSender, inState) {
		this.$.setHidden.call({id: inSender.appId, hidden: !inState});
	},
	hiddenSet: function(inSender, r) {
		// The subscription brings the list back either way; a refusal is said, and the switch
		// goes back to where it was with the next list.
		if (!r || !r.returnValue) { this.showError((r && r.errorText) || $L("The app couldn't be hidden.")); }
	},
	showError: function(text) {
		this.$.errorDialog.openAtCenter();
		this.$.errorText.setContent(text);
	},
	closeError: function() {
		this.$.errorDialog.close();
		this.updateSwitches();
	}
});
