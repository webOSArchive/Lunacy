/**
 * Tablet UI
 * 
 * Table of content is a fixed size pane (320px) on the left.
 * The StackPane is flexible and is on the right.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.TabletUI",
	kind: "HelpApp",
	components: [
		{kind: "Pane", name: "outerPane", flex: 1, components: [
			{kind: "HFlexBox", name: "mainView", flex: 1, components: [
				{kind: "VFlexBox", className: "help-topics", components: [
					{kind: "SearchInput", name: "searchField", hint: $L("Search"), className: "enyo-middle help-search", onSearch: "searchClick", onkeydown: "searchFieldKeydown", changeOnInput: false, autoCapitalize: "lowercase", autocorrect: false, spellcheck: false},
					{flex: 1, components: [
						{defaultKind: "help.TocItem", components: [
							{name: "tips", title: $L("Tips"), description: $L("Read short how-tos"), icon: "images/tips-48.png", onSelected: "tipsClick"},
							{name: "clips", title: $L("Clips"), description: $L("Watch short animations"),  icon: "images/clips-48.png", onSelected: "clipsClick"},
							{name: "featured", title: $L("Featured"), description: $L("Browse featured articles"), icon: "images/featured-48.png", onSelected: "featuredClick", className: "enyo-last"},
							{name: "carepack", title: $L("Care Pack"), description: $L("Extend your coverage"), icon: "images/carepack-48.png", onSelected: "carepackClick", showing: false, className: "enyo-last"}
						]}
					]},			
					{kind: "Toolbar", className: "enyo-toolbar-light", components: [
						{kind: "Button", name: "chat", caption: $L("Live Chat"), className: "help-chat-button", disabled: true, showing: false, onclick: "chatClick"}
					]}					
				]},
				{kind: "Control", className: "help-vertical-separator"},
				{kind: "help.StackPane", name: "stack", flex: 1, onEmpty: "stackEmpty", components: [
					{kind: "help.EmptyView", name: "emptyView"}
				]}
			]},
			{kind: "help.VideoView", name: "videoView", onBack: "goToMainView"}
		]}
	]
});
