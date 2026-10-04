enyo.kind({
	name: "StartPage",
	kind: enyo.VFlexBox,
	className: "startpage",
	published: {
		url: "",
		searchPreferences: {},
		defaultSearch: ""
	},
	events: {
		onUrlChange: "",
		onOpenBookmarks: "",
		onNewCard: ""
	},
	components: [
		{name: "actionbar", kind: "ActionBar", canShare: false, onLoad: "addressSelect", onOpenBookmarks: "doOpenBookmarks", onNewCard: "doNewCard"},
		{name: "tall", className: "startpage-placeholder-tall", components: [
			{name: "placeholder", className: "startpage-placeholder"}
		]}
	],
	//* @protected
	create: function() {
		this.inherited(arguments);
		this.searchPreferencesChanged();
		this.defaultSearchChanged();
	},
	addressSelect: function(inSender, inUrl) {
		this.doUrlChange(inUrl);
	},
	showingChanged: function() {
		this.inherited(arguments);
		// Always focus the action bar when start page is shown.
		if (this.showing) {
			this.$.actionbar.forceFocus();
		} else {
			this.$.actionbar.forceBlur();
		}
	},
	urlChanged: function() {
		this.$.actionbar.setUrl(this.url);
	},
	searchPreferencesChanged: function() {
		this.$.actionbar.setSearchPreferences(this.searchPreferences);
	},
	defaultSearchChanged: function() {
		this.$.actionbar.setDefaultSearch(this.defaultSearch);
	},
	//* @public
	resize: function() {
		this.$.actionbar.resize();
		// XXX hack to workaround a repaint issue where the area behind
		// the keyboard does not get repainted when switch cards, if the
		// keyboard resizes the window. This is temporary until the native
		// issue is fixed. (DFISH-17918)
		var b = enyo.calcModalControlBounds(this.$.tall);
		this.$.placeholder.applyStyle("height", b.height + "px");
	},
	getUrl: function() {
		return this.$.actionBar.getUrl();
	}
});
