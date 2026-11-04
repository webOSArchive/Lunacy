/**
 * An Android player's dashboard: webOS Music's own (palm/dashControls.js, unchanged), with its
 * buttons sent to the Android app playing rather than to Music's window. The app's root
 * (AndroidNotifications.js) opens it with the package in its window params and keeps the title,
 * artist and play state up to date the way Music did, through objTrackInfo and boolAudioPlaying.
 */
enyo.kind({
	name: "AndroidMediaDashboard",
	kind: "DashControlsApp",
	create: function() {
		this.inherited(arguments);
		// Made here, so Music's own components stay as they are.
		this.createComponent({name: "control", kind: "PalmService", service: "palm://org.webosarchive.lunacy/", method: "android/mediaControl"});
		this.pkg = enyo.windowParams && enyo.windowParams.package;
		this.windowParamsChangeHandler();
		// Swiped away by the owner: the root keeps it closed until the next track (an unload
		// the root caused itself it ignores).
		var self = this;
		window.addEventListener("unload", function () {
			try { if (window.opener && window.opener.mediaDashboardClosed) { window.opener.mediaDashboardClosed(self.pkg); } } catch (e) {}
		});
	},
	// Music's commands, as the Android player's transport controls.
	sendCommand: function(cmdType) {
		var command = {prev: "previous", playpause: "playpause", next: "next"}[cmdType];
		if (command && this.pkg) { this.$.control.call({"package": this.pkg, command: command}); }
	},
	// Music brought its Now Playing view forward; this brings the Android player forward.
	onclick_focus: function() {
		if (this.pkg) { this.$.control.call({"package": this.pkg, command: "open"}); }
	}
});
