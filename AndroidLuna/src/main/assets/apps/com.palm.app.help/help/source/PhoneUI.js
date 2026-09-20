/**
 * Phone UI
 * 
 * Table of content, StackPane and Video are inside a pane.
 * Handle back events and some return code
 *  
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.PhoneUI",
	kind: "HelpApp",
	components: [
		{kind: "Pane", name: "outerPane", flex: 1, components: [
			{kind: "VFlexBox", name: "mainView", flex: 1, components: [
				{flex: 1, components: [
					{kind: "SearchInput", name: "searchField", hint: $L("Search"), className: "enyo-middle", onSearch: "searchClick", onkeydown: "searchFieldKeydown", changeOnInput: false, autoCapitalize: "lowercase", autocorrect: false, spellcheck: false},
					{defaultKind: "help.TocItem", components: [
						{name: "tips", title: $L("Tips"), description: $L("Read short how-tos"), icon: "images/tips-48.png", onSelected: "tipsClick", className: "enyo-first"},
						{name: "clips", title: $L("Clips"), description: $L("Watch short animations"),  icon: "images/clips-48.png", onSelected: "clipsClick"},
						{name: "featured", title: $L("Featured"), description: $L("Browse featured articles"), icon: "images/featured-48.png", onSelected: "featuredClick", className: "enyo-last"}
					]}
				]},			
				{kind: "Toolbar", className: "enyo-toolbar-light", components: [
					{kind: "Button", name: "chat", caption: $L("Live Chat"), className: "help-chat-button", disabled: true, showing: false, onclick: "chatClick"}
				]}
			]},
			{kind: "help.StackPane", name: "stack", flex: 1, onEmpty: "stackEmpty", components: [ ]},
			{kind: "help.VideoView", name: "videoView", onBack: "goToStackNoTransition"}
		]}
	],
	goToMainView: function() {
		this.currentView = this.$.mainView;
		this.$.outerPane.setTransitionKind("enyo.transitions.Fade");
		this.$.outerPane.selectView(this.$.mainView);
	},
	goToStackView: function() {
		this.currentView = this.$.stack;
		this.$.outerPane.setTransitionKind("enyo.transitions.LeftRightFlyin");
		this.$.outerPane.selectView(this.$.stack);
	},
	goToStackNoTransition: function () {
		this.currentView = this.$.stack;
		this.$.outerPane.setTransitionKind("enyo.transitions.Simple");
		this.$.outerPane.selectView(this.$.stack);
	},
	goToVideoView: function(inUrl) {
		this.currentView = this.$.videoView;
		this.$.outerPane.setTransitionKind("enyo.transitions.Fade"); 
		this.$.outerPane.selectView(this.$.videoView);
		this.$.videoView.play(inUrl);
	},
	backHandler: function(inSender, inEvent) {
		if (this.currentView === this.$.videoView) {
			this.$.videoView.stop();
			inEvent.preventDefault();
		} else if (this.currentView === this.$.stack) {
			this.$.stack.popView();
			inEvent.preventDefault();
		}
	},
	
	// STACK
	stackEmpty: function() {
		this.inherited(arguments);
		this.goToMainView();
	},
	closePane: function(inSender, inPane) {
		this.$.stack.popView(inPane);
	},
//	goToHome: function() {
//		if (this.currentSection === this.$.searchField) {
//			// Set focus on search field
//		} else {
//			this.$.stack.popAllPanesButOne();
//		}
//	},
	

	// Override some events
	searchClick: function (inSender, inEvent) {
		if (this.inherited(arguments)) {
			this.goToStackView();
		}
	},	
	tipsClick: function (inSender, inEvent) {
		if (this.inherited(arguments)) {
			this.goToStackView();
		}
	},
	clipsClick: function () {
		if (this.inherited(arguments)) {
			this.goToStackView();
		}
	},
	featuredClick: function () {
		if (this.inherited(arguments)) {
			this.goToStackView();
		}
	}
});
