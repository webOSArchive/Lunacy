enyo.kind({
	name:"SearchPreferencesApp",
	kind:"VFlexBox",
	className:"enyo-bg",
	components:[
		{kind:"Toolbar", className:"enyo-toolbar-light accounts-header", pack:"center", components: [
			{kind: "Image", src: "images/searchpreference_48x48.png"},
			{kind: "Control", content: $L("Just Type")}
		]},
		{className:"accounts-header-shadow"},
		{kind:"Scroller", flex:1, components:[
			//{kind:"Control", className:"box-center", style:"margin-top:14px", components:[
				//
				{kind:"Pane", flex:1, onSelectView: "processViewSelected", components:[
					{kind: "HFlexBox", className:"box-center enyo-bg", flex:1, pack:"center", align:"center", 
						   components: [
					                	{kind:"Spinner", name: "getPreferencesSpinner"},
					                	{content: $L("Loading Preferences...")}
					 ]},  
					{kind:"SearchPreferencesMain", className:"box-center enyo-bg", style:"margin-top:14px"},
					{kind:"SearchPreferencesEdit", className:"box-center enyo-bg", style:"margin-top:14px", onItemSelected:"handleDefaultItemSelected"}
 				]},
			//]}
		]},
		{name:"toolbarBottomShadow", className:"accounts-footer-shadow", showing:false},
		{kind:"Toolbar", name:"toolbarBottom", className:"enyo-toolbar-light", showing:false, components:[
			{kind: "Button", caption: $L("Cancel"), className:"accounts-toolbar-btn", onclick: "cancelHandler"}
		]},
		{kind: "AppMenu", components: [
			{kind: "HelpMenu", target: "http://help.palm.com/searchpreferences/index.html"}
		]},
		{kind:"PalmService", service:"palm://com.palm.universalsearch/", components: [
			{name:"getAllSearchPreference", method:"getAllSearchPreference", onResponse:"handleGetAllSearchPreference"},
			{name:"getUniversalSearchList", method:"getUniversalSearchList", subscribe:true, onResponse:"handleGetUniversalSearchList"},
			{name:"getOptionalSearchList", method:"getOptionalSearchList", subscribe:true, onResponse:"handleGetOptionalSearchList"}
		]},
		{name: "accounts", kind: "Accounts.getAccounts", onGetAccounts_AccountsAvailable: "collectRemoteSearchItems"}
	],
	create: function() {
		this.inherited(arguments);
		this.initQuery = false;
		this.$.getPreferencesSpinner.show();
		this.$.getAllSearchPreference.call();
	},
	
	processViewSelected: function(inSender, inView, inPreviousView) {
		if(inView.name == "searchPreferencesEdit") {
			this.$.toolbarBottom.setShowing(true);
			this.$.toolbarBottomShadow.setShowing(true);
		}
		else {
			this.$.toolbarBottom.setShowing(false);
			this.$.toolbarBottomShadow.setShowing(false);
		}
	},
	
	handleGetAllSearchPreference: function(inSender, inResponse) {
		
		if(inResponse && inResponse.returnValue)
			this.$.searchPreferencesMain.setSearchPreferences(inResponse);
		this.$.getUniversalSearchList.call();
	},
	
	handleGetUniversalSearchList: function(inSender, inResponse) {
		
		if(inResponse && inResponse.returnValue)
			this.$.searchPreferencesMain.setJustTypeList(inResponse);
		if(!this.initQuery) {
			this.$.getOptionalSearchList.call();
			this.$.accounts.getAccounts({capability: 'REMOTECONTACTS'});
		}
	},
	
	handleGetOptionalSearchList: function(inSender, inResponse) {
		if(inResponse && inResponse.returnValue)
			this.$.searchPreferencesMain.setOptionalSearchList(inResponse);
		this.initQuery = true;
	},
	
	handleDefaultItemSelected: function(inSender, itemInfo) {
		this.$.searchPreferencesMain.setDefaultSearchEngineInfo(itemInfo);
		this.$.pane.selectViewByName("searchPreferencesMain")
	},
	
	collectRemoteSearchItems: function(inSender, inResponse) {
		var galExist = false;
		var accounts = inResponse.accounts;
		
		for(var i = 0; i < accounts.length; i++) {
			if(accounts[i].templateId != "com.palm.eas") //EAS support only for now.
				continue;
			enyo.forEach(accounts[i].capabilityProviders, function(capabilityProvider) {
	            if ( capabilityProvider.capability === "REMOTECONTACTS" ) {
					 galExist = true;
	            }
	        }, this);
		}
		
		this.$.searchPreferencesMain.galAvailability(galExist);
		this.$.pane.selectViewByName("searchPreferencesMain");
		setTimeout(enyo.bind(this.$.getPreferencesSpinner, "hide"), 1000);
	},
	
	 cancelHandler: function() {
    	this.$.pane.selectViewByName("searchPreferencesMain");
    }
	
});