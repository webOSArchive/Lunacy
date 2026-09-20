/**
 * Views used for missing network connectivity.
 * Also displays the Help information on connecting to a network.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.CarePackView",
	kind: "help.SwipeableStackView",
	components: [
		{kind: "VFlexBox", flex: 1, components: [
			{kind: "PageHeader", className: "help-json-header", align: "center", pack: "start", components: [
				{kind: "Image", src: "images/carepack-32.png", style: "width: 32px; height: 32px"},
				{kind: "Control", content: $L("Care Pack Information"), style: "padding-left: 10px;"}
			]},
			{kind: "Scroller", flex: 1, components: [
				{kind: "VFlexBox", className: "carepack-content", align: "center", pack: "start", flex: 1, components: [
					{content: $L("Download the Care Pack App to learn about HP Care Pack protection for your webOS device. With an HP Care Pack, you can extend your device warranty, with or without accidental damage protection, and receive complimentary telephone support. Purchase within the app, right from your device, and enjoy greater peace of mind knowing you are covered by the company who  knows your product best."), className: "enyo-paragraph"},
					{kind: "Button", name: "careButton", className: "carepack-button", onclick: "handleButtonClick"}
	            ]}
            ]}
		]},
		{kind: "PalmService", service: "palm://com.palm.applicationManager/", name: "applicationManager", method: "open", onResponse: "handleOpenResponse"}
	],
	create: function () {
		this.inherited(arguments);
		this.isInstalled = this.fileUrl;
		this.updateButton();
	},
	handleButtonClick: function () {
		this.log();
		if (this.fileUrl) {
			this.$.applicationManager.call({ id: help.CarePackManager.PackageId });
		} else {
			var target = "http://developer.palm.com/appredirect/?packageid=" + help.CarePackManager.PackageId;
			this.$.applicationManager.call({ target: target });
		}
	},
	handleOpenResponse: function (inSender, inResponse) {
		this.log(inResponse);
	},
	handleInfo: function (info) {
		this.log(info);
		if (info.carepack === "installed") {
			this.isInstalled = true;
			this.updateButton();
		} else if (info.carepack === "removed"){
			this.isInstalled = false;
			this.updateButton();
		}
	},
	updateButton: function() {
		if (this.isInstalled) {
			this.$.careButton.setCaption($L("Launch Care Pack Application"));
		} else {
			this.$.careButton.setCaption($L("Download Care Pack Application"));
		}
	}
});
