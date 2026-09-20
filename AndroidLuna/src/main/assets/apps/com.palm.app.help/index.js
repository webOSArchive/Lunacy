/*jslint */
/*global */
(function() {
	console.log("Starting Help App: " + enyo.json.stringify(enyo.windowParams));
			
	// Get device info
	var deviceInfo = (window.PalmSystem && enyo.json.parse(window.PalmSystem.deviceInfo)) || { carrierName: "ROW", modelName: "HSTNH-I30C" };
	
	// Application globals & (re)launch handler
	enyo.application = {
		headless: window,			// Save the top window
		deviceInfo: deviceInfo,		// Store deviceInfo		
		isTablet: (deviceInfo && deviceInfo.screenWidth === 1024), // Check if we are on a Topaz device
		locale: "en-us",			// Default to English/US
		countryCode: "us",			// Default to US
		languageCode: "en",			// Default to English
		deviceId: "d500-02",		// Default to TouchPad 3G
		deviceModel: "HSTNH-I30C",	// Default to TouchPad 3G
		xPalmCarrier: "c000-01",	// Default to ROW
		profileAlias: null,			// The email associated with the device account
		username: null,				// User name used for authentication 
		password: null,				// Password used for authentication
		appIcons: [ ],				// List of application icons
			
		// The notification manager (Dashboard & Banners)
		notificationManager: enyo.create({kind: "help.NotificationManager"}),
		// Device manager to load the product ID, locale,...
		deviceManager: enyo.create({kind: "help.DeviceManager"}),
		
		// Shortcut to open the Help Application
		openHelpApp: function (params) {
			enyo.windows.activate("help/index.html", "HelpApp", params);
		},
		// Shortcut to open the Chat Application
		openChatApp: function (params) {
			enyo.windows.activate("chat/index.html", "ChatApp", params);
		},
		
		ready: function () {
			// Open the Help Application
			enyo.application.openHelpApp(enyo.windowParams);
		}
	};
	
	// Setup the re-launch handler
	enyo.applicationRelaunchHandler = function (params) {
		console.log("applicationRelaunchHandler: " + JSON.stringify(params));
		
		// Handle chat dashboard/banner
		if (params.source === 'chat') {
			if (params.status !== "disconnected") {
				enyo.application.openChatApp(params);
			}
			return true;
		}
		
		// Otherwise launch the Help App
		enyo.application.openHelpApp(params);
		return true;
	};
		
}());