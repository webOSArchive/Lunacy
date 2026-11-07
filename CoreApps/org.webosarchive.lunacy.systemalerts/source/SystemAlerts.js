/**
 * Lunacy's System Alerts: puts up webOS SystemUI's popup alerts for the system.
 *
 * On webOS, luna-systemui opened these itself as services asked (SystemManagerService's
 * uiAlertsMethods): the location service's request for a position became its LocationAlert,
 * "Allow the following application to use your location for this session?". Lunacy serves
 * SystemUI's alert page where webOS had it,
 * /usr/lib/luna/system/luna-systemui/app/SystemManagerAlerts/systemmanageralerts.html, unchanged,
 * and this headless app opens it as SystemUI's showLocationServiceAlert did: the window name
 * picks the alert, the launch parameters are its windowParams, and the height is SystemUI's
 * (200 px for an app, 240 for a website, which has a third button). The alert answers the
 * service itself.
 *
 * A new Lunacy release found by the daily check (UpdateService) is announced as SystemUI
 * announced a waiting system update: its dashboard, "Update available" with the version under
 * it and the update icon (SysUpdateWaitingDash), which opens System Updates when tapped, with
 * a banner as it arrives. SystemUI's own dashboard kind calls into SystemUI, so this draws the
 * same content with Enyo's Dashboard.
 *
 * Lunacy starts this app with something to show, and it closes once nothing is up.
 */
enyo.kind({
	name: "SystemAlerts",
	kind: enyo.Component,
	components: [
		{kind: "ApplicationEvents", onApplicationRelaunch: "relaunched"},
		{kind: "PalmService", name: "openApp", service: "palm://com.palm.applicationManager/", method: "open"}
	],
	updateSmallIcon: "/usr/lib/luna/system/luna-systemui/images/notification-small-update.png",
	updateLargeIcon: "/usr/lib/luna/system/luna-systemui/images/notification-large-update.png",
	page: "/usr/lib/luna/system/luna-systemui/app/SystemManagerAlerts/systemmanageralerts.html",
	create: function() {
		this.inherited(arguments);
		this.show(enyo.windowParams);
		this.watch = setInterval(enyo.bind(this, "closeIfDone"), 1000);
	},
	relaunched: function() {
		this.show(enyo.windowParams);
		return true;
	},
	show: function(params) {
		if (params && params.openUpdates) {
			// The banner tapped.
			this.openUpdates();
			return;
		}
		if (params && params.alert == "UpdateAvailable") {
			this.showUpdate(params.version);
			return;
		}
		if (!params || params.alert != "LocationAlert" || enyo.windows.fetchWindow("LocationAlert")) {
			return;
		}
		var height = params.type == "web" ? 240 : 200;
		enyo.windows.openPopup(this.page, "LocationAlert", {appId: params.appId, type: params.type, appName: params.appName}, undefined, height);
	},
	showUpdate: function(version) {
		enyo.windows.addBannerMessage($L("Update available"), enyo.json.stringify({openUpdates: true}), this.updateSmallIcon);
		if (!this.updateDashboard) {
			this.updateDashboard = this.createComponent({kind: "Dashboard", name: "updateDashboard", smallIcon: this.updateSmallIcon,
				onMessageTap: "updateTapped", onIconTap: "updateTapped", onUserClose: "updateClosed"});
		}
		this.updateDashboard.setLayers([{icon: this.updateLargeIcon, title: $L("Update available"), text: version}]);
	},
	updateTapped: function() {
		this.openUpdates();
		this.updateClosed();
	},
	updateClosed: function() {
		if (this.updateDashboard) {
			this.updateDashboard.destroy();
			this.updateDashboard = null;
		}
	},
	openUpdates: function() {
		this.$.openApp.call({id: "com.palm.app.updates"});
	},
	closeIfDone: function() {
		if (!enyo.windows.fetchWindow("LocationAlert") && !this.updateDashboard) {
			clearInterval(this.watch);
			window.close();
		}
	}
});
