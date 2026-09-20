/**
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.TocItem",
	kind: "Control",
	className: "enyo-item",
	published: {
		icon: "",
		title: "",
		description: "",
		highlight: false
	},
	events: {
		onSelected: ""
	},
	components: [
		{kind: "HFlexBox", components: [
			{kind: "VFlexBox", pack: "center", components: [
				{kind: "Image", name: "icon"}
			]},
			{kind: "Control", flex: 1, className: "help-toc-item-text", components: [
				{kind: "Control", name: "title", className: "enyo-text-ellipsis"},
				{kind: "Control", name: "description", className: "enyo-subtext enyo-text-ellipsis"}
			]}
		]}
	],
	create: function(inProps) {
		this.inherited(arguments);
		this.titleChanged();
		this.descriptionChanged();
		this.iconChanged();
		this.highlightChanged();
	},
	titleChanged: function() {
		this.$.title.setContent(this.title);
	},
	descriptionChanged: function() {
		this.$.description.setContent(this.description);
	},
	iconChanged: function() {
		this.$.icon.setSrc(this.icon);
	},
	highlightChanged: function() {
		this.addRemoveClass("enyo-held", this.highlight);
	},
	mousedownHandler: function(inSender, inEvent) {
		this.trackMouse = false;
		if (!this.selected) {
			this.trackMouse = true;
			this.addRemoveClass("enyo-held", true);
		}
	},
	mouseoutHandler: function(inSender, inEvent) {
		if (this.trackMouse) {
			this.addRemoveClass("enyo-held", false);
			this.trackMouse = false;
		}
	},
	mouseoverHandler: function(inSender, inEvent) {
		
	},
	mouseupHandler: function(inSender, inEvent) {
		if (this.trackMouse) {
			this.trackMouse = false;
			this.setHighlight(true);
			this.doSelected();
		}
	}
});
