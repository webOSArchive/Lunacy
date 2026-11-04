/**
 * Lunacy's Android Notifications: Android's notifications as webOS dashboards.
 *
 * Headless. Lunacy starts it when Android has a notification to show, and it listens to
 * palm://org.webosarchive.lunacy/android/notifications, which answers again whenever they
 * change. Each Android app gets an enyo.Dashboard, its notifications that dashboard's layers
 * (each with the app's icon, which Lunacy serves at an absolute path, as Enyo wants),
 * newest on top, as webOS Email kept one per account. Tapping a dashboard opens its top
 * notification as Android's shade would; swiping a layer away, or the whole dashboard,
 * dismisses those notifications on Android too. A notification Android ranks as urgent
 * arrives with a banner, whose tap opens it.
 *
 * Android has already played the app's sound, so the banner has none. When nothing is left to
 * show, the app closes itself; Lunacy starts it again for the next one.
 */
enyo.kind({
	name: "AndroidNotifications",
	kind: enyo.Component,
	components: [
		{kind: "ApplicationEvents", onApplicationRelaunch: "relaunched"},
		{kind: "PalmService", service: "palm://org.webosarchive.lunacy/", components: [
			{name: "watch", method: "android/notifications", subscribe: true, onSuccess: "gotNotifications", onFailure: "watchFailed"},
			{name: "open", method: "android/openNotification"},
			{name: "dismiss", method: "android/dismissNotifications"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		// By Android package: {dashboard, signature}.
		this.dashboards = {};
		// By Android package: the open Now Playing window (showMedia), and the title the owner
		// swiped away, which stays away until the next track.
		this.players = {};
		this.dismissedTitles = {};
		window.mediaDashboardClosed = enyo.bind(this, "mediaDashboardClosed");
		this.openFrom(enyo.windowParams);
		this.$.watch.call({subscribe: true});
	},
	//* A banner tapped: Lunacy launches the app again with its notification.
	relaunched: function() {
		this.openFrom(enyo.windowParams);
		return true;
	},
	openFrom: function(params) {
		var key = params && params.androidNotification;
		if (key) { this.$.open.call({key: key}); }
	},
	gotNotifications: function(inSender, r) {
		var apps = r.apps || [], seen = {}, i;
		for (i = 0; i < apps.length; i++) {
			seen[apps[i].package] = true;
			this.show(apps[i]);
		}
		for (var pkg in this.dashboards) {
			if (!seen[pkg]) { this.drop(pkg); }
		}
		this.showMedia(r.media || []);
		var alerts = r.alerts || [];
		for (i = 0; i < alerts.length; i++) {
			var a = alerts[i];
			var message = a.text ? a.title + ": " + a.text : a.title;
			enyo.windows.addBannerMessage(message, enyo.json.stringify({androidNotification: a.key}), a.icon);
		}
		this.closeIfEmpty();
	},
	watchFailed: function(inSender, r) {
		console.error("Android Notifications: " + (r && r.errorText));
		this.closeIfEmpty();
	},
	//* One app's dashboard, made or brought up to date. Its layers are set only when they changed, so it doesn't redraw for nothing.
	show: function(app) {
		var layers = [], sig = app.icon.length + "|";
		for (var i = 0; i < app.notifications.length; i++) {
			var n = app.notifications[i];
			layers.push({icon: app.image, title: n.title, text: n.text, key: n.key});
			sig += n.key + "\u0000" + n.title + "\u0000" + n.text + "\u0001";
		}
		var d = this.dashboards[app.package];
		if (!d) {
			d = this.dashboards[app.package] = {
				dashboard: this.createComponent({kind: "Dashboard", name: "android_" + app.package.replace(/[^A-Za-z0-9_]/g, "_"),
					smallIcon: app.icon, pkg: app.package,
					onTap: "tapped", onLayerSwipe: "layerSwiped", onUserClose: "userClosed"})
			};
		}
		if (d.signature === sig) { return; }
		d.signature = sig;
		d.keys = layers.map(function(l) { return l.key; });
		d.dashboard.setLayers(layers);
	},
	drop: function(pkg) {
		var d = this.dashboards[pkg];
		delete this.dashboards[pkg];
		d.dashboard.setLayers([]);
		d.dashboard.destroy();
	},
	tapped: function(inSender, layer) {
		if (layer && layer.key) { this.$.open.call({key: layer.key}); }
	},
	layerSwiped: function(inSender, layer) {
		if (layer && layer.key) { this.$.dismiss.call({keys: [layer.key]}); }
	},
	//* The whole dashboard swiped away: every notification it held goes.
	userClosed: function(inSender) {
		var d = this.dashboards[inSender.pkg];
		if (!d) { return; }
		delete this.dashboards[inSender.pkg];
		this.$.dismiss.call({keys: d.keys});
		inSender.destroy();
	},
	/**
	 * What Android apps are playing: one webOS Music dashboard each (media-dashboard.html),
	 * opened as Music opened its own, and kept up to date through its window params.
	 */
	showMedia: function(media) {
		this.lastMedia = media;
		var seen = {}, i;
		for (i = 0; i < media.length; i++) {
			var m = media[i], pkg = m["package"];
			seen[pkg] = true;
			var params = {objTrackInfo: {strTrackTitle: m.title, strTrackArtist: m.artist}, boolAudioPlaying: m.playing};
			if (this.dismissedTitles[pkg] !== undefined) {
				if (this.dismissedTitles[pkg] === m.title) { continue; }
				delete this.dismissedTitles[pkg];
			}
			var w = this.players[pkg];
			if (w && !w.closed) {
				enyo.windows.setWindowParams(w, params);
			} else {
				params["package"] = pkg;
				this.players[pkg] = enyo.windows.openDashboard("media-dashboard.html", "media_" + pkg.replace(/[^A-Za-z0-9_]/g, "_"),
					params, {clickableWhenLocked: true, icon: m.icon});
			}
		}
		for (var p in this.players) {
			if (!seen[p]) { this.closePlayer(p); }
		}
		for (var d in this.dismissedTitles) {
			if (!seen[d]) { delete this.dismissedTitles[d]; }
		}
	},
	closePlayer: function(pkg) {
		var w = this.players[pkg];
		delete this.players[pkg];
		if (w && !w.closed) { w.close(); }
	},
	//* A Now Playing dashboard unloaded: swiped away by the owner, unless this app closed it.
	mediaDashboardClosed: function(pkg) {
		if (!pkg || !this.players[pkg]) { return; }
		delete this.players[pkg];
		this.dismissedTitles[pkg] = this.lastTitle(pkg);
		this.closeIfEmpty();
	},
	lastTitle: function(pkg) {
		var media = (this.lastMedia || []);
		for (var i = 0; i < media.length; i++) { if (media[i]["package"] === pkg) { return media[i].title; } }
		return "";
	},
	closeIfEmpty: function() {
		for (var pkg in this.dashboards) { return; }
		for (var p in this.players) { return; }
		// A player swiped away still plays: staying keeps it away (Lunacy would start this app
		// again for it, with its dashboard).
		for (var d in this.dismissedTitles) { return; }
		window.close();
	}
});
