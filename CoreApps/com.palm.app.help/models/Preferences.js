/**
 * Preferences Cookie Wrapper
 * Taken from the Maps application and tweaked.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.Preferences",
	kind: enyo.Component,
	published: {
		cookieName: ""
	},
	create: function() {
		this.inherited(arguments);
		this.cookieNameChanged();
	},
	cookieNameChanged: function() {
		if (this.cookieName && this.cookieName.length > 0) {
			this.fetch();
		}
	},
	fetch: function() {
		this.preferences = this._fetch();
		return this.preferences;
	},
	_fetch: function() {
		var cookieString = enyo.getCookie(this.cookieName), p = {};
		if (cookieString) {
			var i, prefs = cookieString.split(";");
			for (i = 0; i < prefs.length; ++i) {
				var pref = prefs[i], j = pref.indexOf("=");
				if (j !== -1) {
					p[pref.substring(0, j)] = pref.substring(j+1);
				}
			}
		}
		return p;
	},
	save: function() {
		var s = "", p;
		for (p in this.preferences) {
			if (typeof this.preferences[p] === 'string') {
				s += (p + "=" + this.preferences[p] + ";");
			}
		}
		enyo.setCookie(this.cookieName, s);
	},
	get: function(inName) {
		return this.preferences[inName];
	},
	set: function(inPrefs) {
		var p;
		for (p in inPrefs) {
			if (typeof inPrefs[p] === 'string') {
				this.preferences[p] = inPrefs[p];
			}
		}
		this.save();
	},
	clear: function() {
		this.preferences = {};
		enyo.setCookie(this.cookieName, "");
	}
});
