/**
 * Views used for missing network connectivity.
 * Also displays the Help information on connecting to a network.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.NoNetworkView",
	kind: "help.SwipeableStackView",
	published: {
		fileUrl: ""
	},
	components: [
		{kind: "Pane", name: "outerPane", flex: 1, components: [
			{kind: "VFlexBox", name: "noInternet", flex: 1, pack: "center", align: "center", className: "enyo-view", style: "color: grey;", components: [
				{kind: "Image", src: "../images/no-internet.png"},
				{kind: "Control", content: $L("No Network Connection")},
				{kind: "Button", content: $L("Help"), onclick: "showHelp", className: "help-nonetwork-button"}
			]},
			{kind: "VFlexBox", name: "helpConnect", lazy: true, flex: 1, components: [
				{kind: "PageHeader", className: "help-json-header", align: "center", pack: "center", components: [
					{kind: "Control", content: $L("Connect to a Network")}
				]},
				{kind: "Scroller", flex: 1, components: [
					{style: "padding: 10px 20px", components: [
						{content: $L("Are you trying to download using the connection to your phone network? Check the following:"), className: "enyo-paragraph"},
						{nodeTag: "ul", className: "enyo-paragraph", components: [
		                     {nodeTag: "li", content: $L("Is airplane mode on? Turn it off: Tap the upper-right corner of the screen, and then tap <strong>Turn off Airplane Mode.</strong>")},
		                     {nodeTag: "li", content: $L("Do you have a signal? If not, or if it seems weak, move to a location that has a strong signal.")},
		                     {nodeTag: "li", content: $L("Do you see an Ev or H icon in the upper-right corner of the screen? If not, wait until you see the icon, which means that you are in an area that supports data services. Or use a Wi-Fi connection (see below).")},
		                     {nodeTag: "li", content: $L("Are you roaming? Turn on data services while roaming: <strong>Quick Launch</strong> &gt; <strong>Phone</strong> &gt; <strong>Menu</strong> &gt; <strong>Preferences</strong> &gt; <strong>Data Roaming</strong> &gt; <strong>Enabled</strong>.")}
		                ]},
		                {content: $L("Are you trying to connect using Wi-Fi? Check the following:"), className: "enyo-paragraph"},
		                {nodeTag: "ul", className: "enyo-paragraph", components: [
		                     {nodeTag: "li", content: $L("Is Wi-Fi off? Turn it on: Tap the upper-right corner of the screen, and then tap <strong>Wi-Fi</strong> &gt; <strong>Turn on Wi-Fi</strong>.")},
		                     {nodeTag: "li", content: $L("Do you have a Wi-Fi signal? If not, or if it seems weak, move to a location that has a strong signal.")},
		                     {nodeTag: "li", content: $L("Do you need to connect to a Wi-Fi access point? <strong>Launcher</strong> &gt; <strong>Wi-Fi</strong> &gt; tap a network from the list. Or tap Join network and enter the necessary information (get this information from someone who knows about the access point).")},
		                     {nodeTag: "li", content: $L("Do you need to complete the connection by signing in? Sometimes you need to enter information about an access point on a website. <strong>Launcher</strong> &gt; <strong>Web</strong> &gt; enter the information.")}
		                ]},
		                {content: $L("Did you get a data connect error? Try the following:"), className: "enyo-paragraph"},
		                {nodeTag: "ul", className: "enyo-paragraph", components: [
		                     {nodeTag: "li", content: $L("Wait a few minutes and then try again.")},
		                     {nodeTag: "li", content: $L("Update your network settings and try again: <strong>Quick Launch</strong> &gt; <strong>Phone </strong> &gt; <strong>Menu </strong> &gt; <strong>Preferences </strong> &gt; <strong>Update Network Settings</strong> (if available).")}
		                ]}
		            ]}
	            ]}
			]}
		]}
	],
	create: function() {
		this.inherited(arguments);
		if (this.fileUrl === "no-network") {
			this.showHelp();
		}
	},
	showHelp: function() {
		this.$.outerPane.setTransitionKind("enyo.transitions.Simple");
		this.$.outerPane.selectViewByName("helpConnect");
	}
});
