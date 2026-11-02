/*
 * Android Contacts in Accounts: the account type's own page (validator.customUI), shown instead
 * of a username and password form, since Android already holds these contacts.
 *
 * Added from Add Account ("create"), it says what the account does, asks Android for the
 * permission, and hands Accounts an account to make, with nothing to sign in with. From the
 * account in Contacts ("modify") this page is all there is, so it carries Accounts' own Remove
 * Account, which takes the copy away.
 */
enyo.kind({
	name: "AndroidContactsSetup",
	kind: "VFlexBox",
	className: "enyo-bg",
	width: "100%",
	published: {
		params: null
	},
	components: [
		{kind: "Toolbar", pack: "center", className: "enyo-toolbar-light accounts-header", components: [
			{kind: "Control", content: $L("Android Contacts")}
		]},
		{className: "accounts-header-shadow"},
		{kind: "Scroller", flex: 1, components: [
			{kind: "Control", className: "box-center", components: [
				{name: "title", className: "accounts-body-title"},
				{name: "text", className: "accounts-body-text", style: "margin-top: 8px;"},
				{name: "problem", className: "accounts-body-text", style: "margin-top: 16px;", showing: false},
				{name: "goButton", kind: "Button", className: "enyo-button-affirmative", style: "margin-top: 16px;", caption: $L("Show Contacts"), onclick: "ask"},
				{name: "remove", kind: "Accounts.RemoveAccount", style: "margin-top: 16px;", showing: false, onAccountsRemove_Done: "cancel"}
			]}
		]},
		{className: "accounts-footer-shadow"},
		{kind: "Toolbar", className: "enyo-toolbar-light", components: [
			{name: "cancelButton", kind: "Button", className: "accounts-toolbar-btn", caption: $L("Cancel"), onclick: "cancel"}
		]},
		{kind: "CrossAppResult"},
		{name: "request", kind: "PalmService", service: "palm://org.webosarchive.lunacy/", method: "permissions/request", onResponse: "answered"}
	],

	create: function () {
		this.inherited(arguments);
		var p = this.params || {};
		this.$.title.setContent($L("See Android's contacts here"));
		this.$.text.setContent($L("Shows the contacts this device keeps in Android, from all of its accounts, in Contacts. They are a copy for reading: nothing in Lunacy changes them, and nothing is sent anywhere. Removing this account removes the copy."));
		if (p.mode === "modify") {
			this.$.goButton.hide();
			this.$.cancelButton.setCaption($L("Done"));
			this.$.remove.show();
			this.$.remove.init(p.account);
		}
	},

	ask: function () {
		this.$.goButton.setDisabled(true);
		this.$.request.call({permission: "contacts", viaSettings: Boolean(this.useSettings)});
	},

	answered: function (inSender, r) {
		this.$.goButton.setDisabled(false);
		if (r && r.returnValue && r.granted) {
			this.$.crossAppResult.sendResult({
				returnValue: true,
				username: "android",
				alias: $L("Android Contacts"),
				credentials: {},
				config: {}
			});
			return;
		}
		// Once Android won't ask again, only its own screen for Lunacy can give the permission.
		this.useSettings = Boolean(r && r.returnValue && r.askAgain === false);
		this.$.problem.setContent(this.useSettings ?
			$L("Android won't ask again. Turn on Contacts in Lunacy's permissions, then come back and try once more.") :
			$L("Lunacy needs permission to read Android's contacts to show them here."));
		this.$.problem.show();
		if (r && r.opened) {
			this.$.goButton.setCaption($L("Try Again"));
			this.useSettings = false;
		} else if (this.useSettings) {
			this.$.goButton.setCaption($L("Open Android Settings"));
		}
	},

	cancel: function () {
		this.$.crossAppResult.sendResult({returnValue: false});
	}
});
