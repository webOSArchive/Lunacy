enyo.kind({
	name: "BrowserPrompt",
	kind: "VerticalAcceptCancelPopup",
	published: {
		message: ""
	},
	components: [
		{name: "message", className: "browser-dialog-body enyo-text-body"}
	],
	componentsReady: function() {
		this.inherited(arguments);
		this.messageChanged();
	},
	messageChanged: function() {
		this.$.message.setShowing(this.message);
		this.$.message.setContent(this.message);
	}
});

enyo.kind({
	name: "BrowserPreferencePrompt",
	kind: "BrowserPrompt",
	chrome: [
		{className: "enyo-modaldialog-container preference-prompt", components: [
			{name: "modalDialogTitle", className: "enyo-modaldialog-title"},
			{name: "client"},
			{name: "accept", kind: "NoFocusButton", flex: 1, onclick: "acceptClick", className: "enyo-button-negative"},
			{name: "cancel", kind: "NoFocusButton", flex: 1, onclick: "cancelClick"}
		]}
	]
});