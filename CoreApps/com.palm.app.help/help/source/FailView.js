/**
 * Failing view
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.FailView",
	kind: "Scroller",
	className: "help-fail",
	published: {
		message: "",
		details: "",
		url: ""		
	},
	components: [
        {kind: "VFlexBox", flex: 1, className: "enyo-view", pack: "center", align: "center", components: [
			{kind: "Image", src: "images/warning-large.png", onclick: "toggleDetails"},
			{kind: "Control", name: "message", content: $L("Failed to load the requested page.")},
			{kind: "Control", name: "detailBox", showing: false, style: "padding-top: 32px", components: [
				{kind: "Control", name: "details"},
				{kind: "Control", name: "url"},
				{kind: "Control", name: "locale"},
				{kind: "Control", name: "carrier"},
				{kind: "Control", name: "carrierName"},
				{kind: "Control", name: "device"},
				{kind: "Control", name: "deviceName"},
				{kind: "Control", name: "deviceModel"}
			]}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.basicInfo();
	},
	basicInfo: function() {
		this.$.locale.setContent("( Locale: " + enyo.application.locale + " )");
		this.$.carrier.setContent("( Carrier: " + enyo.application.xPalmCarrier + " )");
		this.$.carrierName.setContent("( Carrier Name: " + enyo.application.deviceInfo.carrierName + " )");
		this.$.device.setContent("( Device: " + enyo.application.deviceId + " )");
		this.$.deviceName.setContent("( Device Name: " + enyo.application.deviceInfo.modelName + " )");
		this.$.deviceModel.setContent("( Device Model: " + enyo.application.deviceModel + " )");
	},
	messageChanged: function() {
		this.$.message.setContent(this.message);
	},
	detailsChanged: function() {
		this.$.details.setContent("( " + this.details + " )");
	},
	urlChanged: function() {
		this.$.url.setContent("( " + this.url + " )");
	},
	toggleDetails: function() {
		if (this.$.detailBox.getShowing()) {
			this.$.detailBox.hide();
		} else {
			this.$.detailBox.show();
		}
	}
});
