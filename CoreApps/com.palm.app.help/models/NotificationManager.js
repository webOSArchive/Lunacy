/**
 * Manages notifications (Banner and Dashboard) for the LiveChat application
 * Could be extended easily for the Help application
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.NotificationManager",
	kind: "Component",
	//@ protected
	create: function () {
		this.chatWindow = null;
		this.inherited(arguments);
	},
	handleTap: function (inSender, inEvent) {
		this.hideDashboard();
		enyo.application.openChatApp();
	},
	handleClosed: function (inSender) {
		// Do we care?
	},
	
	//@ public
	/**
	 * Show a banner message
	 */
	showBanner: function (text, params) {
		enyo.windows.addBannerMessage(text, enyo.json.stringify(params), "chat/images/chat-icon-24.png");
	},
	/**
	 * Show or Update the dashboard message
	 */
	showDashboard: function (text) {
		this.chatWindow = enyo.windows.activate("dashboard/index.html", "ChatDash", { text: text }, { window: "dashboard" });
	},
	/**
	 * Hide the dashboard
	 */
	hideDashboard: function () {
		if (this.chatWindow) {
			this.chatWindow.close();
			this.chatWindow = null;
		}
	}
});