/**
 * A scrim set to fit in its owner.
 * The regular scrim is fullscreen.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.HelpScrim",
	kind: "VFlexBox",
	className: "enyo-view help-scrim",
	align: "center",
	pack: "center",
	components: [
		{kind: enyo.SpinnerLarge, name: "spinner", showing: false}
    ],
	show: function() {
		this.inherited(arguments);
		this.$.spinner.setShowing(true);
	},
	hide: function() {
		this.inherited(arguments);
		this.$.spinner.setShowing(false);
	}
});