enyo.kind({
	name: "AcceptCancelPopup",
	kind: "ModalDialog",
	published: {
		acceptCaption: $L("OK"),
		cancelCaption: $L("Cancel")
	},
	events: {
		onAccept: "",
		onResponse: ""
	},
	chrome: [
		{className: "enyo-modaldialog-container", components: [
			{name: "modalDialogTitle", className: "enyo-modaldialog-title"},
			{name: "client"},
			{kind: enyo.HFlexBox, components: [
				{name: "cancel", kind: "NoFocusButton", flex: 1, onclick: "cancelClick"},
				{name: "accept", kind: "NoFocusButton", className: "enyo-button-dark", flex: 1, onclick: "acceptClick"}
			]}
		]}
	],
	//* @protected
	accepted: false,
	componentsReady: function() {
		this.inherited(arguments);
		this.acceptCaptionChanged();
		this.cancelCaptionChanged();
	},
	acceptCaptionChanged: function() {
		if (this.acceptCaption) {
			this.$.accept.setCaption(this.acceptCaption);
			this.$.accept.show();
		} else {
			this.$.accept.hide();
		}
	},
	cancelCaptionChanged: function() {
		if (this.cancelCaption) {
			this.$.cancel.setCaption(this.cancelCaption);
			this.$.cancel.show();
		} else {
			this.$.cancel.hide();
		}
	},
	acceptClick: function() {
		this.accepted = true;
		this.doAccept();
		this.close();
	},
	cancelClick: function() {
		this.accepted = false;
		this.close();
	},
	prepareClose: function() {
		this.inherited(arguments);
		this.sendResponse(this.accepted);
	},
	sendResponse: function(inAccepted) {
		this.doResponse(this.accepted);
	},
	//* @public
	openPopup: function(inMsg) {
		this.accepted = false;
		this.openAtCenter();
	},
});
