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
 * Each row's controls sit in one box on the right, where a second action can join the switch.
 */
enyo.kind({
	name: "SoftwareManager",
	kind: "VFlexBox",
	className: "swManager",
	components: [
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
			{kind: "Scroller", flex: 1, components: [
				{className: "managerbox-outer item-center", components: [
					{name: "spinner", kind: "HFlexBox", pack: "center", components: [{kind: "SpinnerLarge", showing: true}]},
					{name: "noApps", className: "no-apps", showing: false},
					{name: "list"}
				]}
			]}
		]},
		{kind: "PalmService", service: "palm://org.webosarchive.lunacy/", components: [
			{name: "listApps", method: "launcher/listApps", subscribe: true, onSuccess: "gotApps", onFailure: "listFailed"},
			{name: "setHidden", method: "launcher/setHidden", onSuccess: "hiddenSet", onFailure: "hiddenSet"}
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
		this.$.listApps.call({subscribe: true});
	},
	gotApps: function(inSender, r) {
		var apps = r.apps || [];
		apps.sort(function(a, b) {
			var x = a.title.toLowerCase(), y = b.title.toLowerCase();
			return x < y ? -1 : (x > y ? 1 : 0);
		});
		this.$.spinner.hide();
		// The same apps as before, one hidden or shown: only the switches change, so the list
		// doesn't jump back to its top.
		if (this.sameApps(apps)) {
			this.apps = apps;
			this.updateSwitches();
			return;
		}
		this.apps = apps;
		this.renderList();
	},
	listFailed: function(inSender, r) {
		this.$.spinner.hide();
		this.$.noApps.setContent((r && r.errorText) || $L("The apps couldn't be listed."));
		this.$.noApps.show();
	},
	tabChanged: function() {
		this.tab = this.$.tabs.getValue();
		this.renderList();
	},
	shown: function() {
		var tab = this.tab;
		return this.apps.filter(function(a) { return a.kind === tab; });
	},
	sameApps: function(apps) {
		var old = this.apps;
		if (!this.rendered || old.length !== apps.length) { return false; }
		for (var i = 0; i < apps.length; i++) {
			if (old[i].id !== apps[i].id || old[i].title !== apps[i].title || old[i].version !== apps[i].version) { return false; }
		}
		return true;
	},
	//* One row per app: the framed icon, the title, the vendor and version, and its switch.
	renderList: function() {
		var list = this.$.list, apps = this.shown();
		list.destroyControls();
		this.switches = {};
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
				{kind: "HFlexBox", align: "center", components: [
					{name: "switch_" + i, kind: "ToggleButton", onLabel: $L("Show"), offLabel: $L("Hide"),
						state: !a.hidden, disabled: !a.canHide, appId: a.id, onChange: "switched"}
				]}
			]}, {owner: this});
			this.switches[a.id] = this.$["switch_" + i];
		}
		var empty = this.tab === "android" ? $L("No Android apps are shown in the launcher.") : $L("You have no apps installed.");
		this.$.noApps.setContent(empty);
		this.$.noApps.setShowing(apps.length === 0);
		list.render();
		this.rendered = true;
		this.sizeSwitches();
	},
	/**
	 * Every switch as wide as its wider label. Enyo sizes the bar by the label it shows, and
	 * the other is display: none, so both are measured once, shown for a moment, and the
	 * bars get the wider one's width as their least.
	 */
	sizeSwitches: function() {
		if (this.switchRule) { return; }
		var first;
		for (var id in this.switches) { first = this.switches[id]; break; }
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
		var apps = this.shown();
		for (var i = 0; i < apps.length; i++) {
			var s = this.switches && this.switches[apps[i].id];
			if (s && s.getState() === Boolean(apps[i].hidden)) { s.setState(!apps[i].hidden); }
		}
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
