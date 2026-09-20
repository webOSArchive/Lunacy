/**
 * Manages Device Information.
 * 
 * Notifies the main window when all the info has been gathered.
 * 
 * We are getting the following:
 *  - Locale & X-Palm-Carrier
 *    luna-send -n 1 palm://com.palm.systemservice/getPreferences '{"keys":["locale","x_palm_carrier"]}'
 *  - Product ID
 *    luna-send -n 1 palm://com.palm.preferences/systemProperties/getSysProperty '{"key":"com.palm.properties.PRODoID"}'
 *  - Account Alias
 *    luna-send -n 1 palm://com.palm.accountservices/getAccountToken {}
 *  - Connection Status
 *    luna-send -n 1 palm://com.palm.connectionmanager/getStatus {}
 *  - Application list
 *    luna-send -n 1 palm://com.palm.applicationManager/listApps {}
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.DeviceManager",
	kind: "Component",
	components: [
		{kind: "PalmService", service: "palm://com.palm.accountservices/", name: "getAccountToken", onResponse: "onAccountToken"},
		{kind: "PalmService", service: "palm://com.palm.preferences/systemProperties/", method: "getSysProperty", name: "getProductId", onResponse: "onSystemProperties"},
		{kind: "PalmService", service: "palm://com.palm.systemservice/", method: "getPreferences", name: "getLocaleAndCarrier", onResponse: "onSystemPreferences"},
		{kind: "PalmService", service: "palm://com.palm.applicationManager/", name: "listApps", onResponse: "onListApps"},	
		{kind: "PalmService", service: "palm://com.palm.connectionmanager/", method: "getStatus", name: "getStatus", onResponse: "onConnectionStatus"}
    ],
	//@ protected
	create: function () {
		this.inherited(arguments);
		this.$.getAccountToken.call();
	},
	/**
	 * Convert Device Product Model to Device ID
	 */
	getDeviceId: function(model) {
		if (!model) {
			return 'd100-01'; // Default to Pre 1.0
		} else if (model.match(/^P100/)) {
			return 'd100-01'; // Castle aka Pre
		} else if (model.match(/^P101/)) {
			return 'd100-03'; // Castle+ aka Pre+
		} else if (model.match(/^P102/)) {
			return 'd100-04'; // Roadrunner aka Pre2
		} else if (model.match(/^P120/)) {
			return 'd200-01'; // Pixie
		} else if (model.match(/^P121/)) {
			return 'd200-03'; // Pixie+ 
		} else if (model.match(/^P160/)) {
			return 'd300-01'; // Broadway aka Veer
		} else if (model.match(/^P130/)) {
			return 'd400-01'; // Mantaray aka Pre3
		} else if (model === "HSTNH-I29C") {
			return 'd500-01'; // Topaz WiFi aka Touchpad
		} else if (model === "HSTNH-I30C") {
			return 'd500-02'; // Topaz 3G aka Touchpad
		} else {
			return 'd100-01'; // Default to Pre 1.0 - Unknown Model
		}
	},	
	/**
	 * Handle Account Token
	 */
	onAccountToken: function(sender, response, request) {
		if (response.returnValue) {
			enyo.application.profileAlias = response.accountAlias;
			enyo.application.accountToken = response.token;
			this.log(enyo.application.profileAlias, enyo.application.accountToken);
		} else {
			this.log("No ProfileAlias available");
		}
		// Now get the product ID
		this.$.getProductId.call({key: "com.palm.properties.PRODoID"});
	},
	/**
	 * Handle Device Product ID
	 */
	onSystemProperties: function(sender, response, request) {
		if (response.returnValue === true) {
			enyo.application.deviceModel = response['com.palm.properties.PRODoID'];
			enyo.application.deviceId = this.getDeviceId(enyo.application.deviceModel);
			this.log("DeviceModel: " + enyo.application.deviceModel + " - DeviceId: " + enyo.application.deviceId);
		} else {
			this.log("No Product ID available");
		}
		// Now get the locale & x-palm-carrier
		this.$.getLocaleAndCarrier.call({keys: ["locale","x_palm_carrier"]});
	},
	/**
	 * Handle System Preferences (locale & x-palm-carrier)
	 */
	onSystemPreferences: function(sender, response, request) {
		if (response.returnValue === true) {
			// Check the current locale
			if (response.locale) {
				enyo.application.languageCode = response.locale.languageCode;
				enyo.application.countryCode = response.locale.countryCode;
				enyo.application.locale = (enyo.application.languageCode + '-' + enyo.application.countryCode).toLocaleLowerCase();
				this.log("Locale: " + enyo.application.locale);
			} else {
				this.log("No locale available");
			}
			// Check the X-Palm-Carrier
			if (response.x_palm_carrier) {
				enyo.application.xPalmCarrier = response.x_palm_carrier;
				this.log("X-Palm-Carrier: " + enyo.application.xPalmCarrier);				
			} else {
				this.log("No X-Palm-Carrier available");
			}
		} else {
			this.log("onSystemPreferences failed!");
		}
		// Get the list of apps (for icons and care pack check)
		this.$.listApps.call({});
	},
	/**
	 * Handle Application List
	 */
	onListApps: function(sender, response, request) {
		if (response.returnValue && response.apps) {
			var i;
			this.log("ListApp: Found " + response.apps.length + " apps");
			enyo.application.appIcons = [];
			for (i = 0; i < response.apps.length; ++i) {
				var id = response.apps[i].id;
				var icon = response.apps[i].icon;
				enyo.application.appIcons[ String(id) ] = icon;
			}
		}
		// Finally, get the online status
		this.$.getStatus.call({});
	},
	/**
	 * Initial connection status
	 */
	onConnectionStatus: function (inSender, response) {
		enyo.application.online = response.isInternetConnectionAvailable;
		// Ready to launch the application
		enyo.application.ready();
	}
});