/**
 * Lunacy's Sounds & Alerts.
 *
 * Palm's app (com.palm.app.soundsandalerts, webOS 3.x) sets webOS's sounds: a master switch,
 * the system volume, system sounds, keyboard clicks, the ringtone and its volume, vibration and
 * Beats Audio. On Lunacy some of those are Lunacy's and some are Android's, so this app keeps
 * Palm's layout and the settings Lunacy really has, and hands the rest to Android:
 *
 *   Sounds         muteSound (com.palm.systemservice): off, the shell plays no sound at all.
 *   Volume         com.palm.audio/system: Android's system volume, which on a tablet is its
 *                  notification volume too.
 *   System Sounds  systemSounds, and the system class muted, as Palm's app set both: the
 *                  shell's feedback sounds - the swoosh as a card opens or closes.
 *
 * Keyboard clicks belong to the Android keyboard, and the ringtone, vibration and Beats Audio
 * to Android or to hardware Lunacy doesn't have; Android's own sound settings are a tap away.
 */
enyo.kind({
	name: "SoundsAndAlerts",
	kind: "VFlexBox",
	components: [
		{kind: "Toolbar", className: "enyo-toolbar-light accounts-header", pack: "center", components: [
			{kind: "Image", src: "images/soundsandalerts_48x48.png"},
			{kind: "Control", content: $L("Sounds & Alerts")}
		]},
		{className: "accounts-header-shadow"},
		{kind: "Scroller", flex: 1, components: [
			{kind: "Pane", flex: 1, components: [
				{kind: "HFlexBox", className: "box-center enyo-bg", flex: 1, pack: "center", align: "center", components: [
					{kind: "Spinner", name: "spinner"},
					{content: $L("Loading preferences...")}
				]},
				{kind: "Control", name: "prefView", className: "box-center enyo-bg", components: [
					{kind: "RowGroup", className: "accounts-group", components: [
						{kind: "Item", tapHighlight: false, layoutKind: "HFlexLayout", components: [
							{flex: 1, content: $L("Sounds")},
							{kind: "ToggleButton", name: "allSounds", state: true, onChange: "toggleSounds"}
						]}
					]},
					{kind: "RowGroup", className: "accounts-group", caption: $L("Sounds"), name: "soundsGroup", components: [
						{kind: "Item", layoutKind: "VFlexLayout", tapHighlight: false, components: [
							{content: $L("Volume")},
							{kind: "Slider", name: "volume", position: 0, onChange: "volumeChanged"}
						]},
						{kind: "Item", tapHighlight: false, layoutKind: "HFlexLayout", components: [
							{flex: 1, content: $L("System Sounds")},
							{kind: "ToggleButton", name: "sysSounds", state: true, onChange: "toggleSystemSounds"}
						]}
					]},
					{kind: "Button", className: "enyo-button accounts-btn", caption: $L("Android Sound Settings"), onclick: "openAndroid"},
					{content: $L("Use Android settings for: ringtones, notification sounds, vibration and keyboard clicks."), className: "accounts-body-text"}
				]}
			]}
		]},
		{kind: "Dialog", lazy: false, components: [
			{name: "errorText"},
			{layoutKind: "HFlexLayout", pack: "center", components: [
				{kind: "Button", caption: $L("Close"), onclick: "closeDialog"}
			]}
		]},
		{kind: "PalmService", service: "palm://com.palm.systemservice/", components: [
			{name: "getMute", method: "getPreferences", subscribe: true, onResponse: "gotMute"},
			{name: "getPrefs", method: "getPreferences", onResponse: "gotPrefs"},
			{name: "setPrefs", method: "setPreferences", onResponse: "setDone"}
		]},
		{kind: "PalmService", service: "palm://com.palm.audio/system/", components: [
			{name: "getVolume", method: "status", subscribe: true, onResponse: "gotVolume"},
			{name: "setVolume", method: "setVolume", onResponse: "volumeSet"},
			{name: "setMuted", method: "setMuted"}
		]},
		{kind: "PalmService", name: "playFeedback", service: "palm://com.palm.audio/systemsounds/", method: "playFeedback"},
		{kind: "PalmService", name: "openSettings", service: "palm://org.webosarchive.lunacy/", method: "android/openSettings", onResponse: "openedSettings"}
	],
	create: function() {
		this.inherited(arguments);
		this.$.spinner.show();
		this.loaded = false;
		// Palm's order: the master switch, the volume, then the other preferences.
		this.$.getMute.call({keys: ["muteSound"]});
	},
	gotMute: function(inSender, r) {
		var on = !(r && r.muteSound);
		this.$.allSounds.setState(on);
		this.$.soundsGroup.setShowing(on);
		if (!this.loaded) { this.$.getVolume.call(); }
	},
	gotVolume: function(inSender, r) {
		if (r && r.returnValue !== false && r.volume !== undefined) { this.$.volume.setPosition(r.volume); }
		if (!this.loaded) { this.$.getPrefs.call({keys: ["systemSounds"]}); }
	},
	gotPrefs: function(inSender, r) {
		// Unset, as on the reference TouchPad, means on.
		if (r && r.systemSounds !== undefined) { this.$.sysSounds.setState(r.systemSounds); }
		if (!this.loaded) {
			this.loaded = true;
			this.$.pane.selectViewByName("prefView", true);
			this.$.spinner.hide();
		}
	},
	toggleSounds: function(inSender) {
		var on = inSender.getState();
		this.$.setPrefs.call({muteSound: !on});
		this.$.soundsGroup.setShowing(on);
	},
	volumeChanged: function(inSender, inPosition) {
		this.$.setVolume.call({volume: Math.round(inPosition)});
	},
	// Palm's app plays the volume-adjust sound once the new volume is set.
	volumeSet: function(inSender, r) {
		if (r && r.returnValue) { this.$.playFeedback.call({name: "AdjustVolume"}); }
	},
	toggleSystemSounds: function(inSender) {
		var on = inSender.getState();
		this.$.setPrefs.call({systemSounds: on});
		this.$.setMuted.call({muted: !on});
	},
	setDone: function(inSender, r) {
		if (!r || !r.returnValue) { this.showError($L("Unable to set preference")); }
	},
	openAndroid: function() {
		this.$.openSettings.call({panel: "sound"});
	},
	openedSettings: function(inSender, r) {
		if (!r || !r.returnValue) { this.showError(r && r.errorText || $L("Couldn't open Android's settings.")); }
	},
	showError: function(text) {
		this.$.errorText.setContent(text);
		this.$.dialog.open();
	},
	closeDialog: function() {
		this.$.dialog.close();
	}
});
