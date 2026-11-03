/**
[Lunacy] The Pre3's calendar source: the ALL pill on the view header and its popup, standing in
for the toggles a tablet's header has. A row shows that calendar alone; All calendars shows
every one. The choice goes through CalendarList, so it is the same state a tablet would keep.
**/
enyo.kind({
	name		: "calendar.phone.Source",
	kind		: enyo.Control,
	className	: "pre3-source",

	published:
	{	calendars	: null		// Object	: For watching calendars' on/off state.
	},

	components: [
		{name:"name", className:"pre3-source-name"},
		{className:"pre3-source-arrow"},
		{name:"popup", kind:enyo.Popup, className:"pre3-source-popup", scrim:true, showing:false, components: [
			{name:"rows", kind:enyo.Control}
		]}
	],

	create: function create () {
		this.inherited (arguments);
		enyo.application.watch ({calendars:this});
		this.calendarsChanged();
	},

	destroy: function destroy () {
		enyo.application.ignore ({calendars:this});
		this.inherited (arguments);
	},

	calendarsChanged: function calendarsChanged () {
		var	map		= this.map()
		,	all		= $L("All")
		,	on		= []
		,	total	= 0
		;
		for (var id in map) {
			if (map.hasOwnProperty (id)) {
				total++;
				map[id].on && on.push (id);
			}
		}
		this.$.name.setContent (on.length == 1 && total > 1 ? this.calendarName (on[0]) : all);
	},

	map: function map () {
		var list = this.owner && this.owner.$.calendarList;
		return (list && list.calendarMap) || this.calendars || {};
	},

	calendarName: function calendarName (id) {
		var list = enyo.application.calendarsManager.getCalendarsList ({sorted:true});
		for (var i=0; i < list.length; i++) {
			if (list[i]._id == id) { return this.nameOf (list[i]); }
		}
		return "";
	},

	nameOf: function nameOf (c) {
		var mgr = enyo.application.calendarsManager;
		return c.syncSource == "Local" ? mgr.LOCAL_CALENDAR_SHORT_NAME : c.showName;
	},

	clickHandler: function clickHandler (from, domEvent) {
		var popup = this.$.popup;
		if (popup.isOpen) { return true; }
		popup.validateComponents();			// An enyo.Popup makes its children when first opened.
		this.fill();
		popup.openAt ({top: 46, right: 4});
		return true;
	},

	fill: function fill () {
		/* calendars-manager.js's menu: All calendars, each account's calendars under its name, and
		Calendar View Options. */
		var	mgr		= enyo.application.calendarsManager
		,	rows	= this.$.rows
		,	map		= this.map()
		,	list	= mgr.getCalendarsList ({sorted:true})
		,	accounts= mgr.accounts || []
		,	onCount	= 0
		,	i
		;
		rows.destroyControls();
		for (i=0; i < list.length; i++) { map[list[i]._id] && map[list[i]._id].on && onCount++; }
		this.addRow ("all", $L("All calendars"), "", onCount == list.length);

		for (var account, a=0; a < accounts.length; a++) {
			account = accounts[a];
			var name = account.accountName || account.loc_name || account.name || "";
			name && rows.createComponent ({className:"pre3-source-account", content:enyo.string.escapeHtml (name), owner:this});
			for (i=0; i < account.calendars.length; i++) {
				var c = account.calendars[i];
				this.addRow (c._id, this.nameOf (c), c.color, list.length > 1 && onCount == 1 && map[c._id] && map[c._id].on);
			}
		}
		this.addRow ("options", $L("Calendar View Options..."), "", false);
		rows.render();
	},

	addRow: function addRow (id, name, color, checked) {
		var options = id == "options";
		this.$.rows.createComponent (
		{	className	: "pre3-source-row" + (checked ? " checked" : "") + (options ? " options" : "")
		,	calendarId	: id
		,	onclick		: "rowClicked"
		,	owner		: this
		,	components	:
			[	{className: color ? "theme-" + color + " legend" : (options ? "" : "pre3-source-all")}
			,	{className: "ellipsis name", content: enyo.string.escapeHtml (name)}
			,	{className: checked ? "pre3-source-check" : ""}
			]
		});
	},

	rowClicked: function rowClicked (row) {
		while (row && !row.calendarId) { row = row.parent; }
		this.$.popup.close();
		if (row.calendarId == "options") {
			this.owner.showPreferences();
		} else {
			enyo.application.share ({phoneSource: {data: row.calendarId, wait: true}});
			this.calendarsChanged();
		}
		return true;
	}
});
