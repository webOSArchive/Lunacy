/*
 * The Android account in Accounts: the account type's own page (validator.customUI), shown
 * instead of a username and password form, since Android already holds this data.
 *
 * Added from Add Account ("create"), it says what the account does, asks Android for the
 * permissions, and hands Accounts an account to make, with nothing to sign in with. Opened
 * from the account in Contacts or Calendar ("modify") this page is all there is, so it lists
 * Android's calendars to choose from, and carries Accounts' own Remove Account, which takes the
 * copy away. A calendar left out here is how an account set up on both sides isn't shown twice.
 */
enyo.kind({
	name: "AndroidAccountSetup",
	kind: "VFlexBox",
	className: "enyo-bg",
	width: "100%",
	published: {
		params: null
	},
	components: [
		{kind: "Toolbar", pack: "center", className: "enyo-toolbar-light accounts-header", components: [
			{kind: "Control", content: $L("Android")}
		]},
		{className: "accounts-header-shadow"},
		{kind: "Scroller", flex: 1, components: [
			{kind: "Control", className: "box-center", components: [
				{name: "title", className: "accounts-body-title"},
				{name: "text", className: "accounts-body-text", style: "margin-top: 8px;"},
				{name: "problem", className: "accounts-body-text", style: "margin-top: 16px;", showing: false},
				{name: "goButton", kind: "Button", className: "enyo-button-affirmative", style: "margin-top: 16px;", caption: $L("Show My Android Data"), onclick: "ask"},
				{name: "calendars", kind: "RowGroup", caption: $L("Calendars to show"), style: "margin-top: 16px;", showing: false, components: [
					{name: "calendarList", kind: "VirtualRepeater", onSetupRow: "calendarRow", components: [
						{kind: "HFlexBox", align: "center", components: [
							{name: "calendarName", flex: 1, style: "overflow: hidden; text-overflow: ellipsis;"},
							{name: "calendarSwitch", kind: "ToggleButton", onChange: "calendarSwitched"}
						]}
					]}
				]},
				{name: "remove", kind: "Accounts.RemoveAccount", style: "margin-top: 16px;", showing: false, onAccountsRemove_Done: "cancel"}
			]}
		]},
		{className: "accounts-footer-shadow"},
		{kind: "Toolbar", className: "enyo-toolbar-light", components: [
			{name: "cancelButton", kind: "Button", className: "accounts-toolbar-btn", caption: $L("Cancel"), onclick: "cancel"}
		]},
		{kind: "CrossAppResult"},
		{name: "request", kind: "PalmService", service: "palm://org.webosarchive.lunacy/", method: "permissions/request", onResponse: "answered"},
		{name: "list", kind: "PalmService", service: "palm://org.webosarchive.lunacy.androidaccount/", method: "listCalendars", onResponse: "gotCalendars"},
		{name: "choose", kind: "PalmService", service: "palm://org.webosarchive.lunacy.androidaccount/", method: "chooseCalendar"}
	],

	create: function () {
		this.inherited(arguments);
		var p = this.params || {};
		this.calendarItems = [];
		this.$.title.setContent($L("See Android's contacts and calendars here"));
		this.$.text.setContent($L("Shows what this device keeps in Android, from all of its accounts, in Contacts and Calendar. It is a copy for reading: nothing in Lunacy changes it, and nothing is sent anywhere. Removing this account removes the copy."));
		if (p.mode === "modify") {
			this.$.goButton.hide();
			this.$.cancelButton.setCaption($L("Done"));
			this.$.remove.show();
			this.$.remove.init(p.account);
			this.$.list.call({});
		}
	},

	gotCalendars: function (inSender, r) {
		this.calendarItems = (r && r.returnValue && r.calendars) || [];
		this.$.calendars.setShowing(this.calendarItems.length > 0);
		this.$.calendarList.render();
	},

	calendarRow: function (inSender, inIndex) {
		var c = this.calendarItems[inIndex];
		if (c) {
			this.$.calendarName.setContent(c.name);
			this.$.calendarSwitch.setState(c.on);
			return true;
		}
	},

	calendarSwitched: function (inSender, inState) {
		var c = this.calendarItems[this.$.calendarList.fetchRowIndex()];
		if (c) {
			c.on = inState;
			this.$.choose.call({id: c.id, on: inState});
		}
	},

	// One of Android's dialogs for each: refusing one still leaves the other side of the account.
	ask: function () {
		this.$.goButton.setDisabled(true);
		this.pending = ["contacts", "calendar"];
		this.granted = false;
		this.askNext();
	},

	askNext: function () {
		this.asking = this.pending.shift();
		this.$.request.call({permission: this.asking, viaSettings: Boolean(this.useSettings)});
	},

	answered: function (inSender, r) {
		var ok = r && r.returnValue;
		this.granted = this.granted || Boolean(ok && r.granted);
		this.refused = (this.pending.length === 1 ? false : this.refused) || Boolean(ok && r.askAgain === false);
		this.opened = Boolean(ok && r.opened);
		if (this.pending.length && !this.opened) {
			this.askNext();
			return;
		}
		this.$.goButton.setDisabled(false);
		if (this.granted) {
			this.$.crossAppResult.sendResult({
				returnValue: true,
				username: "android",
				alias: $L("Android"),
				credentials: {},
				config: {}
			});
			return;
		}
		// Once Android won't ask again, only its own screen for Lunacy can give the permission.
		this.useSettings = Boolean(this.refused);
		this.$.problem.setContent(this.useSettings ?
			$L("Android won't ask again. Turn on Contacts or Calendar in Lunacy's permissions, then come back and try once more.") :
			$L("Lunacy needs permission to read Android's contacts or calendars to show them here."));
		this.$.problem.show();
		if (this.opened) {
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
