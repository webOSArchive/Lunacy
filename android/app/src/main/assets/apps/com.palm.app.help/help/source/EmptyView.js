/**
 * The default StackPane used when the group is empty.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.EmptyView",
	kind: "help.StackView",
	className: "enyo-view help-empty-view",
	components: [
		{kind: "VFlexBox", flex: 1, pack: "center", align: "center", components: [
			{kind: "Image", name: "logo", src: "../images/help-icon-256.png", className: "help-empty-view-logo"},
			{kind: "Control", content: $L("Search or Select Category")}
		]}
	],
	wizzz: function () {
		if (this.$.logo.hasClass("wizzz")) {
			this.$.logo.removeClass("wizzz");
		} else {
			this.$.logo.addClass("wizzz");
		}
	}
});
