/**
 * Lunacy's First Use.
 *
 * webOS put a First Use app in front of a new device - language, Wi-Fi, terms, the Palm
 * profile, one page at a time on the blue First Use background - and nothing else was
 * reachable until it was done. Lunacy's First Use asks for what Lunacy needs from Android
 * in the same way: the shared storage that stands in for /media/internal, and the system
 * settings that Screen & Lock's brightness and screen-off time really set. The pages that
 * don't apply on this Android version are skipped: Android 5 granted both at install, so
 * there it is a welcome and a Done.
 *
 * Everything it asks is Lunacy's own service, palm://org.webosarchive.lunacy/: permissions
 * are Android's, not webOS's, and the bus says so. The look is Palm's First Use's (NOTICE).
 */
enyo.kind({
	name: "FirstUse",
	kind: "VFlexBox",
	className: "firstuse",
	components: [
		{kind: "Pane", name: "pane", flex: 1, className: "pages", transitionKind: "enyo.transitions.LeftRightFlyin", components: [
			{name: "welcome", kind: "VFlexBox", className: "page", align: "center", pack: "center", components: [
				{content: $L("Welcome"), className: "title"},
				{kind: "VFlexBox", className: "contentDefaults box", components: [
					{content: $L("Lunacy runs webOS apps on this device, the way a TouchPad ran them."), className: "text"},
					{content: $L("A couple of things need Android's permission before the first app opens. Each is asked for once, here."), className: "text"}
				]},
				{kind: "HFlexBox", className: "buttons", pack: "center", components: [
					{kind: "Button", caption: $L("Next"), className: "enyo-button-affirmative", onclick: "next"}
				]}
			]},
			{name: "storage", kind: "VFlexBox", className: "page", align: "center", pack: "center", components: [
				{content: $L("Your Files"), className: "title"},
				{kind: "VFlexBox", className: "contentDefaults box", components: [
					{content: $L("On webOS, your photos, music, videos and documents lived in /media/internal, and every app could read and write them there."), className: "text"},
					{content: $L("Lunacy keeps that folder in this device's shared storage, which Android lets an app use only once you allow it."), className: "text"},
					{name: "storageOk", className: "ok", showing: false},
					{name: "storageMessage", className: "message", showing: false}
				]},
				{kind: "HFlexBox", className: "buttons", pack: "center", components: [
					{kind: "Button", name: "storageAllow", caption: $L("Allow"), className: "enyo-button-affirmative", onclick: "allowStorage"},
					{kind: "Button", name: "storageNext", caption: $L("Next"), onclick: "next"}
				]}
			]},
			{name: "settings", kind: "VFlexBox", className: "page", align: "center", pack: "center", components: [
				{content: $L("Screen & Lock"), className: "title"},
				{kind: "VFlexBox", className: "contentDefaults box", components: [
					{content: $L("Screen & Lock sets the brightness and how long the screen stays on. On this device those are Android's settings."), className: "text"},
					{content: $L("Android allows that on a screen of its own: turn on \"Allow modifying system settings\" for Lunacy there, then come back here."), className: "text"},
					{name: "settingsOk", className: "ok", showing: false},
					{name: "settingsMessage", className: "message", showing: false}
				]},
				{kind: "HFlexBox", className: "buttons", pack: "center", components: [
					{kind: "Button", name: "settingsOpen", caption: $L("Open Android Settings"), className: "enyo-button-affirmative", onclick: "openSettings"},
					{kind: "Button", name: "settingsNext", caption: $L("Next"), onclick: "next"}
				]}
			]},
			{name: "home", kind: "VFlexBox", className: "page", align: "center", pack: "center", components: [
				{content: $L("Home Screen"), className: "title"},
				{kind: "VFlexBox", className: "contentDefaults box", components: [
					{content: $L("On a TouchPad, webOS was the whole device: the card view was the home screen."), className: "text"},
					{content: $L("Android can start Lunacy the same way, in place of its own home screen, with Android's apps in Lunacy's launcher. You can change this later in Android's settings."), className: "text"},
					{name: "homeOk", className: "ok", showing: false},
					{name: "homeMessage", className: "message", showing: false}
				]},
				{kind: "HFlexBox", className: "buttons", pack: "center", components: [
					{kind: "Button", name: "homeSet", caption: $L("Set Lunacy as Home"), className: "enyo-button-affirmative", onclick: "setHome"},
					{kind: "Button", name: "homeNext", caption: $L("Skip"), onclick: "next"}
				]}
			]},
			{name: "done", kind: "VFlexBox", className: "page", align: "center", pack: "center", components: [
				{content: $L("Setup Complete"), className: "title"},
				{kind: "VFlexBox", className: "contentDefaults box", components: [
					{content: $L("Lunacy is ready."), className: "text"},
					{content: $L("The launcher has the apps that come with Lunacy; App Catalog, on its Downloads tab, has the rest."), className: "text"},
					{content: $L("Anything not allowed here can be allowed later in Android's settings for Lunacy."), className: "text small"}
				]},
				{kind: "HFlexBox", className: "buttons", pack: "center", components: [
					{kind: "Button", caption: $L("Done"), className: "enyo-button-affirmative", onclick: "finish"}
				]}
			]}
		]},
		// Start Over, bottom left, as Palm's First Use had it, with its confirmation. Above
		// the pane's views, which carry a z-index of their own (their transitions set it).
		{name: "startOver", kind: "Control", showing: false, style: "position: fixed; height: 60px; width: 150px; bottom: 0; left: 0; z-index: 10;", components: [
			{kind: "IconButton", className: "restart", caption: $L("Start Over"), icon: "images/btn_start_over.png", onclick: "confirmStartOver"}
		]},
		{name: "confirmDialog", kind: "ModalDialog", lazy: false, className: "popup", caption: $L("Confirm"), components: [
			{content: $L("Would you like to start over from the beginning?"), className: "enyo-text-body"},
			{kind: "Control", layoutKind: "HFlexLayout", components: [
				{kind: "Button", caption: $L("No"), flex: 1, style: "margin-right: 10px;", onclick: "closeConfirm"},
				{kind: "Button", caption: $L("Yes"), flex: 1, className: "enyo-button-affirmative", onclick: "startOver"}
			]}
		]},
		{kind: "PalmService", name: "status", service: "palm://org.webosarchive.lunacy/", method: "permissions/status", onSuccess: "gotStatus", onFailure: "statusFailed"},
		{kind: "PalmService", name: "request", service: "palm://org.webosarchive.lunacy/", method: "permissions/request", onSuccess: "requested", onFailure: "requestFailed"},
		{kind: "PalmService", name: "finishService", service: "palm://org.webosarchive.lunacy/", method: "firstUse/done", onResponse: "finished"},
	],
	/** The pages in order, once the status says which apply. */
	steps: ["welcome", "done"],
	step: 0,
	permissions: null,
	create: function() {
		this.inherited(arguments);
		this.$.status.call({});
	},
	gotStatus: function(inSender, inResponse) {
		this.permissions = inResponse;
		var steps = ["welcome"];
		if (inResponse.storage && inResponse.storage.asked) { steps.push("storage"); }
		if (inResponse.systemSettings && inResponse.systemSettings.asked) { steps.push("settings"); }
		if (inResponse.homeLauncher && inResponse.homeLauncher.asked) { steps.push("home"); }
		steps.push("done");
		this.steps = steps;
		this.showStatus();
	},
	statusFailed: function(inSender, inResponse) {
		// Without an answer there is nothing to ask for; the welcome still leads to Done.
		enyo.log("First Use: permissions/status failed", enyo.json.stringify(inResponse));
	},
	/**
	 * Android's own screens and dialogs (Modify system settings, the home screen choice,
	 * Lunacy's app settings) grant without telling anyone, and the card is never deactivated
	 * while they are up, so a page waiting on one asks again every couple of seconds; the
	 * asking stops once the grant is seen or the page is left.
	 */
	watchStatus: function(on) {
		if (this.statusWatch) { clearInterval(this.statusWatch); this.statusWatch = null; }
		if (on) { this.statusWatch = setInterval(enyo.bind(this, function() { this.$.status.call({}); }), 2000); }
	},
	/** What each page says about its permission, from the latest status. */
	showStatus: function() {
		var p = this.permissions;
		if (!p) { return; }
		var s = p.storage && p.storage.granted;
		if (s && this.steps[this.step] == "storage") { this.watchStatus(false); }
		this.$.storageOk.setContent(s ? $L("Allowed.") : "");
		this.$.storageOk.setShowing(!!s);
		this.$.storageAllow.setShowing(!s);
		// Going on without the grant is a skip, and says so; once granted, the one button
		// left is the green one, as webOS drew a page's one action.
		this.$.storageNext.setCaption(s ? $L("Next") : $L("Skip"));
		this.$.storageNext.addRemoveClass("enyo-button-affirmative", !!s);
		if (s) { this.$.storageMessage.setShowing(false); }
		var w = p.systemSettings && p.systemSettings.granted;
		if (w && this.steps[this.step] == "settings") { this.watchStatus(false); }
		this.$.settingsOk.setContent(w ? $L("Allowed.") : "");
		this.$.settingsOk.setShowing(!!w);
		this.$.settingsOpen.setShowing(!w);
		this.$.settingsNext.setCaption(w ? $L("Next") : $L("Skip"));
		this.$.settingsNext.addRemoveClass("enyo-button-affirmative", !!w);
		if (w) { this.$.settingsMessage.setShowing(false); }
		var h = p.homeLauncher && p.homeLauncher.granted;
		this.$.homeOk.setContent(h ? $L("Lunacy is the home screen.") : "");
		this.$.homeOk.setShowing(!!h);
		this.$.homeSet.setShowing(!h);
		this.$.homeNext.setCaption(h ? $L("Next") : $L("Skip"));
		this.$.homeNext.addRemoveClass("enyo-button-affirmative", !!h);
		if (h) { this.$.homeMessage.setShowing(false); }
		if (this.steps[this.step] == "home") { this.watchStatus(!h); }
	},
	next: function() {
		if (this.step < this.steps.length - 1) {
			this.step++;
			this.showPage(this.steps[this.step]);
		}
	},
	showPage: function(page) {
		this.$.pane.selectViewByName(page);
		this.$.startOver.setShowing(page != "welcome");
		var p = this.permissions;
		var storageGranted = p && p.storage && p.storage.granted;
		var waiting = (page == "settings" && !(p && p.systemSettings && p.systemSettings.granted)) ||
			(page == "home" && !(p && p.homeLauncher && p.homeLauncher.granted));
		this.watchStatus(waiting);
		// The storage page asks as soon as it is up: Android's dialog over the page that
		// explains it, as a webOS page put its question up at once. Not when Android has
		// refused for good; then the button is the way, and it says where.
		if (page == "storage" && !storageGranted && !this.storageViaSettings) { this.allowStorage(); }
	},
	confirmStartOver: function() {
		this.$.confirmDialog.openAtCenter();
	},
	closeConfirm: function() {
		this.$.confirmDialog.close();
	},
	startOver: function() {
		this.$.confirmDialog.close();
		this.step = 0;
		this.showPage("welcome");
	},
	/** Android won't put its storage question again once it has been refused twice; then the button opens Android's settings for Lunacy instead. */
	storageViaSettings: false,
	allowStorage: function() {
		this.$.storageAllow.setDisabled(true);
		this.$.request.call({permission: "storage", viaSettings: this.storageViaSettings});
		if (this.storageViaSettings) { this.watchStatus(true); }
	},
	setHome: function() {
		this.$.request.call({permission: "homeLauncher"});
		this.watchStatus(true);
	},
	openSettings: function() {
		this.$.request.call({permission: "systemSettings"});
	},
	requested: function(inSender, inResponse) {
		if (inResponse.permission == "storage") {
			this.$.storageAllow.setDisabled(false);
			if (!inResponse.granted && !inResponse.opened) {
				if (inResponse.askAgain === false) {
					this.storageViaSettings = true;
					this.$.storageAllow.setCaption($L("Open Android Settings"));
					this.$.storageMessage.setContent($L("Not allowed, and Android won't ask again. Apps won't see your files in /media/internal unless it is allowed under Permissions in Android's settings for Lunacy."));
				} else {
					this.$.storageMessage.setContent($L("Not allowed. Apps won't see your files in /media/internal until it is allowed."));
				}
				this.$.storageMessage.setShowing(true);
			}
		}
		this.$.status.call({});
	},
	requestFailed: function(inSender, inResponse) {
		var text = (inResponse && inResponse.errorText) || $L("Android didn't answer.");
		var where = inSender && inSender.params && inSender.params.permission == "systemSettings" ? this.$.settingsMessage : this.$.storageMessage;
		this.$.storageAllow.setDisabled(false);
		where.setContent(text);
		where.setShowing(true);
	},
	finish: function() {
		this.watchStatus(false);
		this.$.finishService.call({});
	},
	finished: function() {
		window.close();
	}
});
