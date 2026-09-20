/**
 * SwipeableStackView used to display JSON lists from the Help server.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.JsonView",
	kind: "help.SwipeableStackView",
	published: {
		icon: "",
		title: "",
		fileUrl: ""
	},
	events: {
		onLoadUrl: ""
	},
	components: [
		{kind: "Pane", name: "outerPane", flex: 1, components: [
			{kind: "VFlexBox", name: "jsonView", flex: 1, components: [
                {kind: "PageHeader", name: "jsonHeader", align: "center", showing: false, className: "enyo-text-ellipsis help-json-header", components: [
					{kind: "help.HelpImage", name: "icon", style: "width: 32px; height: 32px"},
					{kind: "Control", name: "title", flex: 1, allowHtml: true, style: "padding-left: 10px;"},
					{kind: "Button", name: "jsonSort", components: [
						{kind: "ListSelector", value: 1, onChange: "itemSortChanged", items: [
							{caption: $L("Alphabetical"), value: 0},
							{caption: $L("By Category"), value: 1}
						]}
					]}
				]},
				{kind: "Scroller", name: "scroller", flex: 1, components: [
					{kind: "VirtualRepeater", name: "jsonList", flex: 1, onSetupRow: "listSetupRow", components: [
						{kind: "Divider", name: "divider", showing: false},
						{kind: "help.JsonItem", name: "jsonItem", onclick: "onItemClick"}
					]}
				]}
			]},
			{kind: "help.FailView", name: "jsonFail", flex: 1},
			{kind: "help.HelpScrim", name: "viewScrim", showing: false}
		]},
		{kind: "help.AuthPopup", name: "auth", onSubmit: "onAuthSubmit", onCancel: "onAuthCancel"},
		{kind: "WebService", name: "getJson", onResponse: "onJsonResponse"}
	],
	//* @protected
	create: function() {
		this.log();
		this.items = [ ];
		this.inherited(arguments);
		this.fileUrlChanged();
	},
	ready: function() {
		this.log();
	},
	rendered: function() {
		this.log();
		this.inherited(arguments);
		this.showScrim();
	},
	titleChanged: function() {
		this.$.title.setContent(this.title.stripTags());
	},
	iconChanged: function() {
		this.$.icon.setSrc(this.icon);
	},
	fileUrlChanged: function() {
		this.log("JsonPane: Loading " + this.fileUrl);
		
		var customRequestHeaders = {
			"X-Palm-Carrier": enyo.application.xPalmCarrier,
			"X-Palm-Locale": enyo.application.locale,
			"X-Palm-Device": enyo.application.deviceId,
			"X-Palm-Device-Model": enyo.application.deviceModel,
			"X-Palm-Carrier-Name": enyo.application.deviceInfo.carrierName,
			"X-Palm-Device-Name": escape(enyo.application.deviceInfo.modelName)
		};
		
		this.$.getJson.setUsername(enyo.application.username);
		this.$.getJson.setPassword(enyo.application.password);		
		this.$.getJson.setHeaders(customRequestHeaders);
		this.$.getJson.setUrl(this.fileUrl);
		this.$.getJson.call();
	},
	listSetupRow: function(inSender, inIndex) {
		var item = this.items[inIndex];
		if (item) {
			// Clean the item
			this.$.jsonItem.removeClass("no-top-border");
			this.$.jsonItem.removeClass("no-bottom-border");
			// Use divider if we are sorting by category
			if (item.category && (this.sortBy === "category")) {
				this.$.divider.setCaption(item.category);

				var prev = this.items[inIndex - 1];
				if (prev && (prev.category === item.category)) {
					prev.category = item.category; // jslint happy
				} else {
					this.$.jsonItem.addClass("no-top-border");
					this.$.divider.show();				
				}
				
				var next = this.items[inIndex + 1];
				if (!next || (next && (next.category !== item.category))) {
					this.$.jsonItem.addClass("no-bottom-border");
				}
			} else {
				if (inIndex === 0) {
					this.$.jsonItem.addClass("no-top-border");
				}
				if (inIndex === this.items.length - 1) {
					this.$.jsonItem.addClass("no-bottom-border");
				}
			}
			
			// Item info
			if (item.text) {
				this.$.jsonItem.setText(item.text);
			}
			if (item.icon) {
				this.$.jsonItem.setIcon(item.icon);
			} else {
				this.$.jsonItem.hideIcon();
			}
			return true;
		}
	},
	showScrim: function() {
		this.$.viewScrim.show();
	},
	hideScrim: function() {
		this.$.viewScrim.hide();
	},
	onItemClick: function(inSender, inEvent, inRowIndex) {
		this.doLoadUrl(this.items[inRowIndex].url, this.items[inRowIndex].text);
	},
	onAuthSubmit: function(inSender, inUsername, inPassword) {
		inUsername = inUsername.trim();
		inPassword = inPassword.trim();
		// Save the username and password
		enyo.application.setUsername(inUsername);
		enyo.application.setPassword(inPassword);
		this.$.auth.close();
		// Retry to load the JSON file
		this.showScrim();
		this.fileUrlChanged();
	},
	onAuthCancel: function(inSender) {
		this.showFailView($L("401: Authentication Required"));
	},
	onJsonResponse: function(inSender, inResponse, inRequest) {
		this.log(" response/request: " + inResponse + " / " + inRequest);
		if (this.isOpen) {
			this.handleJsonResponse(inResponse, inRequest);
		} else {
			this.savedResponse = inResponse || "";
			this.savedRequest = inRequest;
		}
	},
	doOpenDone: function(inSender) {
		this.log();
		this.isOpen = true;
		if (this.savedResponse || this.savedRequest) {
			this.handleJsonResponse(this.savedResponse, this.savedRequest);
		}
	},
	showFailView: function(details) {
		this.$.jsonFail.setDetails(details);
		this.$.jsonFail.setUrl(this.fileUrl);
		this.$.outerPane.selectView(this.$.jsonFail);
	},
	handleJsonResponse: function(inResponse, inRequest) {
		this.log();
		if (!inRequest) {
			this.showFailView($L("Fatal! You should not have seen this error!"));
		} else if (inRequest.xhr.status === 404) {
			this.showFailView($L("404: URL does not exist"));
		} else if (inRequest.xhr.status === 401) {
			this.$.auth.setUsername(enyo.application.getUsername());
			this.$.auth.setPassword(enyo.application.getPassword());
			this.$.auth.openAtCenter();
		} else if (inRequest.xhr.status === 500) {
			this.showFailView($L("500: Internal Server Error"));
		} else if (inRequest.xhr.status === 0 && inResponse.length === 0) {
			this.showFailView($L("No Internet Connection or Cross-Origin problem"));
		} else if (inResponse.length <= 0 ) {
			this.showFailView($L("URL response was empty"));
		} else if (typeof inResponse !== "object") {
			this.showFailView($L("Malformed JSON file"));
		} else if (!inResponse.items) {
			this.showFailView($L("No items found in data"));
		} else {
			var i;
			// Keep a copy for us
			this.jsonResponse = inResponse;
			// Set the header title/icon
			if (inResponse.title) {
				if (inResponse.title) {
					this.setTitle(inResponse.title);
				}
				inResponse.icon = (inResponse.app_name && enyo.application.appIcons[inResponse.app_name]) || inResponse.icon;
				if (inResponse.icon) {
					this.setIcon(inResponse.icon);
				}
				this.$.jsonHeader.show();
			}
			// Try to use the device application icons			
			this.items = inResponse.items;
			for (i = 0; i < this.items.length; ++i) {
				this.items[i].icon = (this.items[i].app_name && enyo.application.appIcons[this.items[i].app_name]) || this.items[i].icon;
				this.items[i].categoryKey = i; // So sorting by category leads to the same result
	        }
			// Sort the items
			if (inResponse.sort_by && inResponse.sort_by[0]) {
				if (inResponse.sort_by[0] === "category") {
					this.sortByCategory(); // Will render
				} else if (inResponse.sort_by[0] === "text") {
					this.sortAlphabetically(); // Will render
				} else {
					this.log("Unknown sort method: " + inResponse.sort_by[0]);
					this.$.jsonSort.hide();
					this.$.jsonList.render();
				}
			} else {
				this.$.jsonSort.hide();
				this.$.jsonList.render();
			}
		}
		this.hideScrim();
	},
	sortAlphabetically: function() {
		this.sortBy = "text";
		this.items.sort(function(a, b) {
			if (a.text && b.text) {
				var textA = a.text.toLowerCase();
	            var textB = b.text.toLowerCase();
	            return textA.localeCompare(textB);
			} else {
				return 0;
			}
		});
		this.$.scroller.setScrollTop(0);
		this.$.jsonList.render();
	},
	sortByCategory: function() {
		this.sortBy = "category";
		this.items.sort(function(a, b) {
			return a.categoryKey - b.categoryKey;
		});
		this.$.scroller.setScrollTop(0);
		this.$.jsonList.render();
	},
	itemSortChanged: function(inSender, inValue, inOldValue) {
		if (inValue === 0) {
			this.sortAlphabetically();
		} else {
			this.sortByCategory();
		}
	},
	getChatInfo: function() {
		var info = {};
		if (this.jsonResponse && this.jsonResponse.chat_appId && this.jsonResponse.chat_appId.length) {
			info.extraInfo = {
				jsonApplication: this.jsonResponse.chat_appId
			};
		} 
		return info;
	}
});
