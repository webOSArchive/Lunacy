enyo.kind({
	name: "Analog",
	kind: "Control",
	className:"analog clockbg",
	published: {boolShowSeconds: true},
	components: [
			
		{name: "handHour", className:"clockHand Hour"},
		{name: "handMin", className:"clockHand Min"},
		{name: "handSec", className:"clockHand Sec"},
		{kind: "Control", layoutKind: "HLayout", className: "clockLabel", components: [
			{name: "lblWeekday", className:" weekday"},
			{width: "110px"},
			{name: "lblDay", className:"day"}												 
			]
		}
		
	],
	
	
	create: function ()
	{
		this.inherited(arguments);
		this.log();
		this.local_weekday_formatter = new enyo.g11n.DateFmt({format: "EEE"});	
		this.local_day_formatter = new enyo.g11n.DateFmt({format: "dd"});	
	},
	
	tock: function ()
	{
		this.setClockHand(this.$.handHour, enyo.application.utilities.getCurrentHourPartial(false), 12);
		this.setClockHand(this.$.handMin, enyo.application.utilities.getCurrentMinutePartial(),60);
		this.setClockHand(this.$.handSec, enyo.application.utilities.getCurrentSecond(),60);
		this.setDateDisplay();
	},
	
	setClockHand: function (ctrlHand, intPos, intPosMax)
	{
		
		var intRotation = (360) * (intPos/intPosMax);
		//var intRotation = Math.round((360) * (intPos/intPosMax));
		
		/*
		this.log("intPos", intPos);
		this.log("intPosMax", intPosMax);
		this.log("intRotation", intRotation);
		this.log("-webkit-transform:rotate(" + intRotation + "deg);");
		*/
		
		
		ctrlHand.applyStyle("-webkit-transform","rotate(" + intRotation + "deg)");
		//ctrlHand.setStyle("-webkit-transform:rotate(" + intRotation + " deg);");
		
	}	,
	
	setDateDisplay: function ()
	{
		//this.log(this.local_weekday_formatter.format(enyo.application.utilities.now()));
		
		
		this.$.lblWeekday.setContent(this.local_weekday_formatter.format(enyo.application.utilities.now()));
		this.$.lblDay.setContent(this.local_day_formatter.format(enyo.application.utilities.now()));
	},
	
	boolShowSecondsChanged: function ()
	{
		this.$.handSec.setShowing(this.boolShowSeconds);
		
	}
	

});