/**
 * Simple Authentication Popup Dialog.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.AuthPopup",
	kind: "Popup",
	layout: "VFlexLayout",
	scrim: true,
	modal: true,
	published: {
		username: "",
		password: "",
		error: null
	},
	events: {
		onSubmit: "",
		onCancel: ""
	},
	components: [
		{kind: "Control", content: $L("Authentication Required"), style: "font-size: 26px; padding: 6px;"},
		{kind: "Control", domStyles: {"border-top": "1px solid silver", "border-bottom": "1px solid white"}},
		{kind: "Control", content: $L("Username"), style: "padding-top: 6px;" },
		{kind: "PasswordInput", name: "username"},
		{kind: "Control", content: $L("Password")},
		{kind: "PasswordInput", name: "password"},
		{kind: "Control", name: "errorText", showing: false, style: "text-align: center; color: red"},
		{kind: "HFlexBox", style: "padding-top: 6px;", components: [
			{kind: "Button", flex: 1, caption: $L("Submit"), onclick: "submit"},
			{kind: "Spacer"},
			{kind: "Button", flex: 1, caption: $L("Cancel"), onclick: "cancel"}
		]}
	],
	usernameChanged: function() {
		if (this.username) {
			this.$.username.setValue(this.username);
		}
	},
	passwordChanged: function() {
		if (this.password) {
			this.$.password.setValue(this.password);
		}
	},
	errorChanged: function() {
		this.$.errorText.setContent(this.error || "");
		this.$.errorText.setShowing(!!this.error);
	},
	submit: function() {
		this.doSubmit(this.getUsername(), this.getPassword());
	},
	cancel: function() {
		this.close();
		this.doCancel();
	},
	getUsername: function() {
		return this.$.username.getValue();
	},
	getPassword: function() {
		return this.$.password.getValue();
	}
});
