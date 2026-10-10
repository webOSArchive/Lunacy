/**
 * JsonItem extends enyo.Item
 * It provides a simple icon and text.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved. 
 */
enyo.kind({
	name: "help.JsonItem",
	kind: "Item",
	tapHighlight: true,
	published: {
		text: "",
		icon: "images/basics.png"
	},
	components: [
		{kind: "HFlexBox", components: [
			{kind: "help.HelpImage", name: "icon", style: "width: 32px; height: 32px"},
			{kind: "Control", flex: 1, name: "text", allowHtml: true, style: "padding-left: 10px"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.textChanged();
		this.iconChanged();
	},
	textChanged: function() {
		this.text = this.text || "";
		this.$.text.setContent(this.text.stripTags());
	},
	iconChanged: function() {
		this.icon = this.icon || "images/basics.png";
		this.$.icon.setSrc(this.icon);
	},
	hideIcon: function() {
		this.$.icon.hide();
	}
});
