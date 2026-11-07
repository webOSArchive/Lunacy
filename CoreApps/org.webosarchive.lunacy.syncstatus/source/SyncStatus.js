/**
 * Lunacy's Sync Status: the part of webOS's luna-systemui that showed account syncs.
 *
 * On webOS, SystemUI ran Enyo's SyncUI.syncDashboard for as long as the device was up. It
 * watches com.palm.account.syncstate:1 in tempdb, which the transports and the accounts
 * service write, and while an account syncs for the first time or is being removed it shows
 * a banner ("Syncing account...", "Removing account...") and a dashboard with the account's
 * icon and name, then a banner when that ends ("Sync completed", "Account removed"). A
 * transport reporting a 401 gets a "Check your login and password" dashboard that opens
 * Accounts. All of that is Enyo's own code, unchanged; this app hosts it.
 *
 * [Lunacy] One addition, codepoet's (2026-11-07, luna-deltas A28): a first sync names what is
 * still syncing, from the same records ("Syncing contacts and calendars...", then "Syncing
 * calendars..." once the contacts are in), so a long sync doesn't look stuck. Its opening
 * banner says the same. A sync whose capabilities can't be named keeps webOS's text.
 *
 * Headless, and only while there is something to show: Lunacy starts it when a sync state is
 * written that would put up a dashboard, and it closes itself once none is up, as Lunacy's
 * Android Notifications does. Started later than SystemUI was, it reads the states already
 * written, so nothing is missed.
 */
enyo.kind({
	name: "SyncStatus",
	kind: "SyncUI.syncDashboard",
	//* How long it lingers once idle, so a state written right after another (the next capability's) finds it running.
	idleCloseMs: 5000,
	//* What each capability is called in "Syncing ...", in the order they're listed.
	capabilityNames: [
		["MAIL", $L("email")],
		["CONTACTS", $L("contacts")],
		["CALENDAR", $L("calendars")],
		["TASKS", $L("tasks")],
		["MEMOS", $L("memos")],
		["MESSAGING", $L("messages")],
		["PHOTO.UPLOAD", $L("photos")],
		["VIDEO.UPLOAD", $L("videos")]
	],
	receivedSyncStatus: function(inSender, inResponse) {
		this.details = this.syncingDetails(inResponse);
		// Enyo's own pass, with its "Syncing account..." banners held back so they can name
		// what is syncing; every other banner (removal, the end of a sync) goes as it is.
		var before = {}, id;
		for (id in this.accountStatus) {
			before[id] = this.accountStatus[id].dashboardStatus;
		}
		var windows = enyo.windows, add = windows.addBannerMessage, held = [];
		windows.addBannerMessage = function(text) {
			if (text === SyncUIUtil.SYNCING_ACCOUNT) {
				held.push(arguments);
			} else {
				return add.apply(windows, arguments);
			}
		};
		try {
			this.inherited(arguments);
		} finally {
			windows.addBannerMessage = add;
		}
		for (id in this.accountStatus) {
			var a = this.accountStatus[id];
			if (!a.dashboard || a.dashboardStatus !== "INITIAL_SYNC") {
				continue;
			}
			var text = this.details[id] || SyncUIUtil.SYNCING_ACCOUNT;
			if (before[id] !== "INITIAL_SYNC" && held.length) {
				var args = Array.prototype.slice.call(held.shift());
				args[0] = text;
				add.apply(windows, args);
			}
			if (a.detailShown !== text) {
				a.detailShown = text;
				a.dashboard.setLayers([{
					icon: a.account.icon.loc_48x48,
					title: a.account.alias || a.account.username,
					text: text
				}]);
			}
		}
		// Any left (an account this can't match up) go as Enyo sent them.
		while (held.length) {
			add.apply(windows, held.shift());
		}
		this.closeWhenIdle();
	},
	//* By account: "Syncing contacts and calendars..." for the capabilities whose first sync is running, or nothing if none can be named.
	syncingDetails: function(inResponse) {
		var caps = {}, out = {}, results = (inResponse && inResponse.results) || [];
		for (var i = 0; i < results.length; i++) {
			var r = results[i];
			if (r.collectionId || r.syncState !== "INITIAL_SYNC") {
				continue;
			}
			var account = SyncUIUtil.getAccount(this.accounts, r.accountId);
			var cap = account && this.capabilityOf(account, r.capabilityProvider);
			if (!cap) {
				// One part that can't be named: the whole account keeps webOS's text.
				caps[r.accountId] = null;
				continue;
			}
			if (caps[r.accountId] !== null) {
				(caps[r.accountId] = caps[r.accountId] || {})[cap] = true;
			}
		}
		for (var id in caps) {
			if (!caps[id]) {
				continue;
			}
			var names = [];
			for (var j = 0; j < this.capabilityNames.length; j++) {
				if (caps[id][this.capabilityNames[j][0]]) {
					names.push(this.capabilityNames[j][1]);
				}
			}
			if (names.length) {
				out[id] = this.syncingText(names);
			}
		}
		return out;
	},
	capabilityOf: function(account, providerId) {
		var providers = account.capabilityProviders || [];
		for (var i = 0; i < providers.length; i++) {
			if (providers[i].id === providerId) {
				var cap = providers[i].capability;
				for (var j = 0; j < this.capabilityNames.length; j++) {
					if (this.capabilityNames[j][0] === cap) {
						return cap;
					}
				}
			}
		}
		return null;
	},
	syncingText: function(names) {
		var list = names.length === 1 ? names[0] :
			names.slice(0, -1).join(", ") + " " + $L("and") + " " + names[names.length - 1];
		return new enyo.g11n.Template($L("Syncing #{what}...")).evaluate({what: list});
	},
	closeWhenIdle: function() {
		if (this.closeTimer) {
			clearTimeout(this.closeTimer);
			this.closeTimer = null;
		}
		for (var id in this.accountStatus) {
			if (this.accountStatus[id].dashboard) {
				return;
			}
		}
		if (this.permissionDashboard) {
			for (var p in this.permissionDashboard) {
				return;
			}
		}
		this.closeTimer = setTimeout(enyo.bind(this, function() {
			this.closeTimer = null;
			window.close();
		}), this.idleCloseMs);
	}
});
