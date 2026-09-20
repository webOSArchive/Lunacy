/**
 * Handle Care Pack application checks
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.CarePackManager",
	kind: "Component",
	statics: {
		PackageId: "com.hp.app.carepack"
	},
	events: {
		onCarePackAvailable: "",
		onCarePackInstalled: "",
		onCarePackRemoved: ""
	},
	components: [
		{kind: "PalmService", service: "palm://com.palm.accountservices/", name: "getServerUrl", onSuccess: "onGetServerUrl"},
		{kind: "PalmService", service: "palm://com.palm.deviceprofile/", name: "getDeviceId", onSuccess: "onGetDeviceId"},
		{kind: "PalmService", service: "palm://com.palm.appInstallService/", name: "status", onSuccess: "onInstallStatus", subscribe: true},
		{kind: "WebService", name: "catalogServer", method: "POST", contentType: "application/json", handleAs: "json", url: "", onSuccess: "onServerResponse"},
		{kind: "help.Preferences", name: "preferences", cookieName: "CarePackPreferences"}
    ],
	//@ protected
	create: function () {
		this.inherited(arguments);
	},
	/**
	 * Returns true if Care Pack app is installed
	 */
	isCarePackInstalled: function () {
		return !!enyo.application.appIcons[help.CarePackManager.PackageId];
	},
	/**
	 * Check if the care pack application is available
	 */
	checkAppAvailablility: function () {
		this.log();
		this.$.status.call(); // Monitor app install/uninstall
		if (this.$.preferences.get("available") === "yes") {
			this.log("CarePack available (prefs)");
			this.doCarePackAvailable();
		} else if (enyo.application.appIcons[help.CarePackManager.PackageId]) {
			this.log("CarePack available (app list)");
			this.$.preferences.set({"available": "yes"});
			this.doCarePackAvailable();
		} else {
			this.$.getServerUrl.call();
		}
	},
	/**
	 * Store the server URL
	 */
	onGetServerUrl: function(sender, response, request) {
		this.log("ServerURL", response.serverUrl);
		this.serverUrl = response.serverUrl;
		this.$.getDeviceId.call();		
	},
	/**
	 * Store the device ID (from device profile)
	 */
	onGetDeviceId: function(sender, response, request) {
		this.log("DeviceId", response.deviceId);
		this.deviceId = response.deviceId;
		this.getApplicationDetails();
	},
	/**
	 * Retrieves application details online
	 */
	getApplicationDetails: function() {
		this.log();
		var request = {
			InGetAppDetailV2: {				
                accountTokenInfo: {
                	token: enyo.application.accountToken,
                	deviceId: this.deviceId,
                	email: enyo.application.profileAlias
                },
                packageId: help.CarePackManager.PackageId,
                locale: enyo.g11n.currentLocale().toISOString()
            }
		};
		this.$.catalogServer.setUrl(this.serverUrl + "appDetail_ext2");
		this.$.catalogServer.setTimeout(15000);
		this.$.catalogServer.call(enyo.json.stringify(request));
	},
	/**
	 * Server giving back some good news
	 */
	onServerResponse: function(sender, response, request) {		
		if (response && response.OutGetAppDetailV2 && response.OutGetAppDetailV2.appDetail) {
			this.log("CarePack available in app catalog");
			this.$.preferences.set({"available": "yes"});
			this.doCarePackAvailable();
		} else {
			this.log(response);
		}
	},
	/**
	 * Monitor install/uninstall of the carepack application
	 */
	onInstallStatus: function(sender, response) {
		var i, apps = (response.status) ? response.status.apps : [ response ];
		for (i = 0; i < apps.length; i++) {
			if (apps[i].id === help.CarePackManager.PackageId) {
				if (apps[i].details.state === "installed") {
					this.log("CarePack available (just installed)");
					this.$.preferences.set({"available": "yes"});
					enyo.application.appIcons[help.CarePackManager.PackageId] = apps[i].details;
					this.doCarePackInstalled();
				} else if (apps[i].details.state === "removed") {
					this.log("CarePack unavailable (just removed)");
					enyo.application.appIcons[help.CarePackManager.PackageId] = undefined;
					this.doCarePackRemoved();
				}
			}
		}
	}
});