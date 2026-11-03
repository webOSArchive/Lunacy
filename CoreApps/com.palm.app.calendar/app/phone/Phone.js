/**
[Lunacy] The Pre3's Calendar on a phone. Nothing in this folder runs, and no rule in Phone.css
applies, on a TouchPad or a tablet. See Docs/phone.md and this app's NOTICE; it has to be carried
over when webOS CE's tree is taken again.

	- calendar.phone.on		: this card gets the phone layout (the class "pre3" on the app view)
	- calendar.phone.folding: and it is short enough for the Pre3's accordion

The numbers are the Pre3's own (HP webOS 2.2.4 day-assistant.js and week-assistant.js).
**/
window.calendar = window.calendar || {};

calendar.phone =
{	on			: false
,	folding		: false
,	compressed	: true		// As in day-assistant: the whole scene folds and opens together.
,	DAY_HOUR	: 48
,	WEEK_HOUR	: 30
,	TALL_ENOUGH	: 720		// A card this tall holds a working day unfolded (a Fold3's cover does).
,	dayViews	: []

,	fit: function fit () {
		/* Decides for the card as it is now, and answers whether the decision changed. A narrow
		card is a phone's; so is any card of a device told it is a phone, unless it is tall
		enough to be the inside of a foldable that was opened after the app started. */
		var	info	= enyo.fetchDeviceInfo ? enyo.fetchDeviceInfo() : null
		,	told	= !!(info && Math.min (info.screenWidth, info.screenHeight) < 600)
		,	width	= window.innerWidth
		,	height	= window.innerHeight
		,	on		= width < 600 || (told && height < 500)
		,	folding	= on && height < this.TALL_ENOUGH
		,	changed	= on != this.on || folding != this.folding
		;
		this.on			= on;
		this.folding	= folding;
		return changed;
	}

,	hourHeight: function hourHeight (inWeekView) {
		return inWeekView ? this.WEEK_HOUR : this.DAY_HOUR;
	}

,	workHours: function workHours () {
		/* The day's start and end hours, from Preferences (stored as UTC times of day). */
		var prefs = enyo.application.prefsManager && enyo.application.prefsManager.prefs;
		return	{	start	: prefs && isFinite (prefs.startTimeOfDay) ? new Date (prefs.startTimeOfDay).getUTCHours() : 8
				,	end		: prefs && isFinite (prefs.endTimeOfDay) ? new Date (prefs.endTimeOfDay).getUTCHours() : 18
				};
	}

,	shortDay: function shortDay (date) {
		/* The first letter of the weekday, as month-assistant's day labels have it. */
		return (new enyo.g11n.DateFmt ({format: "EEE"})).format (date).charAt (0);
	}

,	titles: {
		/* The Pre3's view titles (DateHeader's formats.phone). */
		day: function day (date) {
			var	today	= +new Date (date).clearTime() == +new Date().clearTime()
			,	fmt		= new enyo.g11n.DateFmt ({format: today ? "EEE, MMM d" : "EEE, MMM d, yyyy"})
			;
			return (today ? $L("Today") + ", " : "") + fmt.format (date);
		}
	,	week: function week (date) {
			return new enyo.g11n.Template ($L("Week of #{date}")).evaluate
				({date: (new enyo.g11n.DateFmt ({format: "MMM d, yyyy"})).format (date)});
		}
	,	month: function month (date) {
			return (new enyo.g11n.DateFmt ({date: "medium", dateComponents: "my"})).format (date);
		}
	}
};
