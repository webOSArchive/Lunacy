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
 * service itself. Lunacy starts this app with an alert to show, and it closes once none is up.
 */
enyo.kind({
	name: "SystemAlerts",
	kind: enyo.Component,
	components: [
		{kind: "ApplicationEvents", onApplicationRelaunch: "relaunched"}
	],
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
		if (!params || params.alert != "LocationAlert" || enyo.windows.fetchWindow("LocationAlert")) {
			return;
		}
		var height = params.type == "web" ? 240 : 200;
		enyo.windows.openPopup(this.page, "LocationAlert", {appId: params.appId, type: params.type, appName: params.appName}, undefined, height);
	},
	closeIfDone: function() {
		if (!enyo.windows.fetchWindow("LocationAlert")) {
			clearInterval(this.watch);
			window.close();
		}
	}
});
