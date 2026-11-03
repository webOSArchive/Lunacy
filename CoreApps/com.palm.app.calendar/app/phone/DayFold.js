/**
[Lunacy] The Pre3's day view: hours 48 px tall and the accordion, from day-assistant.js
(calcEventCompression, compressPos, toggleCompression).

A gap of more than an hour and a half between the day's first and last events folds to the
height of one hour, pleated, with a pill saying how long it is. A tap on a fold opens every fold
of the day, the pill staying where the gap's middle is; a tap on the pill closes them.
BusyFreeManager is the same code as the Pre3's, so it picks the same gaps. The Pre3 draws all of
this on a canvas; here the hours are the day's own hour labels, moved.
**/
calendar.phone.day =
{	layout: function layout (view) {
		/* Works out the day's busy times, folds and timeline from its events. */
		var	day		= view.day
		,	events	= (day && day.events) || []
		,	h		= calendar.phone.DAY_HOUR
		,	mgr		= enyo.application.busyFreeManager
		,	busy	= mgr.getBusyTimes (events)
		,	free	= calendar.phone.folding ? mgr.getFreeTimes (busy) : []
		,	folds	= []
		;
		for (var f, i=0; i < free.length; i++) {
			f = free[i];
			if (f.duration > 150) {
				var	hours	= parseInt (f.duration / 100, 10)
				,	mins	= +(((f.duration % 100) / 100) * 60).toFixed (0)
				;
				folds.push (
				{	start	: f.start_decimal
				,	end		: f.end_decimal
				,	height	: f.duration * h / 100
				,	text	: new enyo.g11n.Template (mins ? $L("#{hour} HRS, #{min} MINS FREE") : $L("#{hour} HRS FREE")).evaluate ({hour:hours, min:mins})
				});
			}
		}
		return {busy:busy, folds:folds, height:h};
	}

,	y: function y (map, decimal, v) {
		/* A time's height in the day, v of the way from open (0) to compressed (1). */
		var	h		= map.height
		,	open	= decimal * h / 100
		,	delta	= 0
		;
		for (var f, i=0; i < map.folds.length; i++) {
			f = map.folds[i];
			if (decimal > f.start) {
				delta += decimal < f.end
					?	(decimal - f.start) * h / 100 * (1 - h / f.height)		// Inside the fold, squeezed with it.
					:	f.height - h;
			}
		}
		return open - delta * v;
	}

,	inFold: function inFold (map, decimal) {
		for (var i=0; i < map.folds.length; i++) {
			if (decimal >= map.folds[i].start && decimal < map.folds[i].end) { return true; }
		}
		return false;
	}

,	hourAt: function hourAt (map, y, v) {
		/* The hour at a height in the day, for a tap on an empty hour. */
		for (var hour=23; hour > 0; hour--) {
			if (this.y (map, hour * 100, v) <= y) { return hour; }
		}
		return 0;
	}

,	fit: function fit (view) {
		var ph = calendar.phone;
		if (view.inWeekView) { return; }
		view.day && this.fitAllDay (view, (view.day.allDayEvents || []).length);

		var ui = view.$;
		if (!ph.on) {
			if (view.phoneLayer) {
				view.phoneLayer.parentNode && view.phoneLayer.parentNode.removeChild (view.phoneLayer);
				view.phoneLayer = null;
				this.size (view, 1440);
				var hours = ui.hourLabels.hours;
				for (var i=0; i < 24; i++) { hours[i].applyStyle ("top", null); hours[i].removeClass ("offhours"); hours[i].removeClass ("workstart"); }
				view.eventGroup.setDayMap (null);
			}
			return;
		}
		var node = view.dayContainer.hasNode();
		if (!node) { return; }

		if (!view.phoneLayer || view.phoneLayer.parentNode != node) {
			view.phoneLayer = document.createElement ("div");
			view.phoneLayer.className = "pre3-layer";
			node.appendChild (view.phoneLayer);
			view.phoneLayer.addEventListener ("click", enyo.bind (this, this.layerClick, view), false);
		}
		var	map		= view.phoneMap = this.layout (view)
		,	layer	= view.phoneLayer
		,	html	= ""
		,	i
		;
		for (i=0; i < map.busy.length; i++) { html += '<div class="pre3-busy"></div>'; }
		for (i=0; i < map.folds.length; i++) {
			html	+= '<div class="pre3-fold" data-fold="' + i + '"><span class="pre3-pill">'
					+	enyo.string.escapeHtml (map.folds[i].text) + '</span></div>';
		}
		layer.innerHTML = html;
		map.busyNodes = layer.querySelectorAll (".pre3-busy");
		map.foldNodes = layer.querySelectorAll (".pre3-fold");

		var work = ph.workHours(), blocks = ui.hourLabels.hours;
		for (i=0; i < 24; i++) {
			blocks[i].addRemoveClass ("offhours", i < work.start || i >= work.end);
			blocks[i].addRemoveClass ("workstart", i == work.start || i == work.end);
			if (view.phoneAmpm === i) { blocks[i].removeClass ("pre3-ampm"); view.phoneAmpm = undefined; }
		}
		view.eventGroup.dayMap = map;
		view.eventGroup.dayV = ph.compressed ? 1 : 0;
		view.eventGroup.eventsChanged();
		this.draw (view, ph.compressed ? 1 : 0, true);
	}

,	dayHeight: function dayHeight (view, v) {
		/* A last hour, so 11 pm clears the view selector; and on a card taller than a Pre3's, as
		much again as it is taller, so a folded day can still bring its first event to the top. */
		var	map		= view.phoneMap
		,	shown	= view.$.hours.hasNode() ? view.$.hours.node.offsetHeight : 0
		;
		return this.y (map, 2400, v) + map.height + Math.max (0, shown - 476);
	}

,	size: function size (view, height) {
		view.dayContainer.applyStyle ("height", height + "px");
		view.hoursContainer.applyStyle ("height", height + "px");
		view.eventGroup.applyStyle ("height", height + "px");
	}

,	draw: function draw (view, v, resize) {
		/* Puts everything where it belongs, v of the way from open to compressed. */
		var	map		= view.phoneMap
		,	blocks	= view.$.hourLabels.hours
		,	h		= map.height
		,	i
		;
		for (i=0; i < 24; i++) {
			var	node	= blocks[i].hasNode()
			,	folded	= v > 0 && this.inFold (map, i * 100)		// Under a fold, and hidden while it moves too, as renderTimeLines does.
			;
			blocks[i].applyStyle ("top", this.y (map, i * 100, v) + "px");
			blocks[i].applyStyle ("visibility", folded ? "hidden" : null);
		}
		for (i=0; i < map.busy.length; i++) {
			var b = map.busy[i], bs = map.busyNodes[i].style, top = this.y (map, b.start_decimal, v);
			bs.top		= top + "px";
			bs.height	= (this.y (map, b.end_decimal, v) - top) + "px";
		}
		for (i=0; i < map.folds.length; i++) {
			var	f		= map.folds[i]
			,	fs		= map.foldNodes[i]
			,	top		= this.y (map, f.start, v)
			;
			fs.style.top	= top + "px";
			fs.style.height	= (this.y (map, f.end, v) - top) + "px";
			fs.className	= "pre3-fold" + (v > 0 ? " folded" : "");
		}
		view.eventGroup.placeEvents (v);
		resize && this.size (view, this.dayHeight (view, v));
		this.ampm (view);
	}

,	layerClick: function layerClick (view, domEvent) {
		var	target	= domEvent.target
		,	pill	= target.className == "pre3-pill"
		,	fold	= pill ? target.parentNode : target
		,	index	= fold.getAttribute ("data-fold")
		;
		if (index === null) { return; }
		if (calendar.phone.compressed || pill) {
			domEvent.stopPropagation();
			this.toggle (view, +index);
		}
	}

,	toggle: function toggle (view, index) {
		/* toggleCompression: 0.4 s, or 0.3 when no gap reaches three hours, ease-in-out, keeping
		the tapped fold's middle where it is on the screen. */
		var	ph		= calendar.phone
		,	map		= view.phoneMap
		,	fold	= map.folds[index]
		,	close	= !ph.compressed
		,	from	= close ? 0 : 1
		,	to		= close ? 1 : 0
		,	mid		= (fold.start + fold.end) / 2
		,	self	= this
		,	scroller= view.$.hours
		,	top		= scroller.getScrollTop()
		,	longest	= 0
		;
		for (var i=0; i < map.folds.length; i++) { longest = Math.max (longest, map.folds[i].end - map.folds[i].start); }
		var	duration= longest < 300 ? 300 : 400
		,	start	= Date.now()
		,	shift	= this.y (map, mid, to) - this.y (map, mid, from)
		;
		ph.compressed = close;
		this.size (view, this.y (map, 2400, 0) + map.height);	// Room for the open day throughout.

		var step = function step () {
			var	t		= Math.min (1, (Date.now() - start) / duration)
			,	e		= t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t)
			,	v		= from + (to - from) * e
			;
			self.draw (view, v, false);
			scroller.setScrollTop (top + shift * e);
			if (t < 1) {
				window.requestAnimationFrame ? window.requestAnimationFrame (step) : setTimeout (step, 16);
				return;
			}
			self.size (view, self.dayHeight (view, to));
			scroller.setScrollTop (top + shift);
			view.eventGroup.dayV = to;
			self.others (view);
		};
		step();
	}

,	others: function others (view) {
		/* The carousel's neighbouring days follow. */
		var views = calendar.phone.dayViews;
		for (var i=0; i < views.length; i++) {
			if (views[i] != view && views[i].day) { this.fit (views[i]); }
		}
	}

,	scrollDefault: function scrollDefault (view) {
		/* setDefaultTimeforDay: the day's first event, or the hour before the working day. */
		view.$.hours.setScrollTop (this.y (view.phoneMap, this.firstHour (view) * 100, calendar.phone.compressed ? 1 : 0));
		this.ampm (view);
	}

,	firstHour: function firstHour (view) {
		var	events	= (view.day && view.day.events) || []
		,	first	= null
		;
		for (var i=0; i < events.length; i++) {
			if (first === null || events[i].start_decimal < first) { first = events[i].start_decimal; }
		}
		return first === null ? Math.max (0, calendar.phone.workHours().start - 1) : Math.floor (first / 100);
	}
};

// The all-day events: bars stacked in a band under the header, at most 135 px of them, scrolling.
calendar.phone.day.fitAllDay = function fitAllDay (view, count) {
	var	ui		= view.$
	,	phone	= calendar.phone.on
	,	scroller= ui.allDayScroller
	;
	view.addRemoveClass ("pre3-has-allday", phone && count > 0);
	scroller.vertical = scroller.autoVertical = phone;
	scroller.horizontal = scroller.autoHorizontal = !phone;
	ui.allDayContainer.applyStyle ("height", phone && count ? Math.min (135, count * 34 + 9) + 7 + "px" : null);
};

// The am/pm under the hour nearest the top of the view, as day-assistant's updateAmPm draws it.
calendar.phone.day.ampm = function ampm (view) {
	var	map		= view.phoneMap
	,	blocks	= view.$.hourLabels && view.$.hourLabels.hours
	;
	if (!map || !blocks) { return; }
	var	top		= view.$.hours.getScrollTop()
	,	v		= calendar.phone.compressed ? 1 : 0
	,	shown	= 0
	;
	for (var i=0; i < 24; i++) {
		if (this.y (map, i * 100, v) >= top - 4) { shown = i; break; }
	}
	if (view.phoneAmpm !== shown) {
		isFinite (view.phoneAmpm) && blocks[view.phoneAmpm].removeClass ("pre3-ampm");
		blocks[shown].addClass ("pre3-ampm");
		view.phoneAmpm = shown;
	}
};
