/**
 * Module to help monitoring the internet availability from the connection manager
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.ConnectionManager",
	kind: "Component",
	published: {
		/**
		 * True if we have internet access
		 */
		online: false
	},
	events: {
		/**
		 * Called when the connection status changes
		 */
		onConnectionChange: ""
	},
	components: [
	     {kind: "PalmService", name: "checkBusStatus", service: "palm://com.palm.bus/signal/", method: "registerServerStatus", onResponse: "onServerStatus", subscribe: true},
	     {kind: "PalmService", name: "getStatus", service: "palm://com.palm.connectionmanager/", method: "getstatus", onResponse: "onConnectionStatus", subscribe: true}
	],
	create: function () {
		this.inherited(arguments);
		this.$.checkBusStatus.call({
			serviceName: "com.palm.connectionmanager"
		});
	},
	onServerStatus: function (inSender, response) {
		if (response.connected) {
			this.$.getStatus.call({
				subscribe: true
			});
		} else {
			if (!window.PalmSystem || window.PalmSystem.version.match("desktop")) {
				this.online = true; // Browser and Desktop assumed to be connected
			} else {
				this.online = false;
				this.connected = false;
				this.$.getStatus.cancel();
			}
			this.doConnectionChange(this.online);
		}
	},
	onConnectionStatus: function (inSender, response) {
		this.online = response.isInternetConnectionAvailable;
		this.doConnectionChange(this.online);
	}
});