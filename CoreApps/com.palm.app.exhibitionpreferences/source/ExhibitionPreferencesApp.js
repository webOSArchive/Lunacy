enyo.kind({
	name:"ExhibitionPreferencesApp",
	kind:"VFlexBox",
	components:[
		{kind:"Header", className:"page-header", pack:"center", components: [
			{className: "header-icon"},
			{name:"headerTitle", content: $L("Exhibition"), style: "padding-left: 10px;"}
		]},
		{kind:"Scroller", flex:1, components:[
			{kind:"Control", className:"box-center", components:[
				{kind: "RowGroup", name:"applicationGroup", caption:$L("Applications"), className:"accounts-group", components: []},
				{kind: "Button", onclick:"handleFindMore", layoutKind:"HFlexLayout", className:"accounts-btn", align:"center", components:[
					{kind:"Image", src:"images/appcatalog.png", className:"icon-image"},
                    {content:$L("Find More...")}
                ]},
				{content: $L("Tap to find other applications that support Exhibition Mode."), className:"accounts-body-text"},
				// [Lunacy] Exhibition as Android's screen saver (shell/ExhibitionDream), chosen in
				// Android's own settings: codepoet, 2026-11-07. Styled as the TouchPad's buttons here.
				{kind: "Button", className: "accounts-btn", style: "margin-top: 16px;", caption: $L("Android Screen Saver Settings"), onclick: "openScreenSaver"},
				{name: "screenSaverText", content: $L("Choose Lunacy Exhibition as Android's screen saver to show Exhibition while the device charges or sits in its dock."), className: "accounts-body-text"},
     	     ]}
     	]},
		{kind:"Toolbar", className:"enyo-toolbar-light", components:[
			{kind: "Button", label:$L("Start Exhibition"), className:"enyo-button-dark accounts-toolbar-btn", onclick:"enterExhibitionMode"}
		]},
		{kind: "AppMenu", components: [
			{kind: "HelpMenu", target: "http://help.palm.com/exhibition/index.html"}
        ]},
		{kind:"PalmService",  service:"palm://com.palm.applicationManager/", components:[ 
			{name:"getDockList",method:"listDockModeLaunchPoints", subscribe:true, onResponse:"handleDockAppList"},
			{name:"setDockEnabled"},
			{name:"appLaunch", method:"open"}
		]},
		{kind:"PalmService", name:"startExhitbitionMode", service:"palm://com.palm.display/control/", method:"setState"},
		// [Lunacy] Android's screen saver settings.
		{kind:"PalmService", name:"openSettings", service:"palm://org.webosarchive.lunacy/", method:"android/openSettings", onResponse:"openedScreenSaver"}
	],
	create:function() {
		this.inherited(arguments);
		this.dockApps = [];
		this.$.getDockList.call();
	},
	handleDockAppList: function(inSender, inResponse) {
		if(inResponse && !inResponse.returnValue)
			return;
		this.$.applicationGroup.destroyControls();
		
		var launchPoints = inResponse.launchPoints;
		//clear items
		this.dockApps = this.dockApps.splice(0, this.dockApps.length);
		this.addDefaultItem();
		
		for(var i = 0; i < launchPoints.length; i++) {
			this.dockApps.push({displayName: launchPoints[i].title, appId: launchPoints[i].appId, enabled:launchPoints[i].enabled, disabled:false});
		}
		var componentsArr = [];
		
		for(var i = 0; i < this.dockApps.length; i++) {
			componentsArr.push({kind:"ExhibitAppItem", itemInfo:this.dockApps[i], onItemChange: "handleItemChange"});
		}
		this.$.applicationGroup.createComponents(componentsArr, {owner:this});
		this.$.applicationGroup.render();
	},
	addDefaultItem: function() {
		var item = {displayName: $L("Time"), appId: "default_time", enabled:true, disabled:true};
		this.dockApps.push(item);
	},
	handleItemChange: function(inSender, inEvent) {
		var item = inSender.getItemInfo();
		if(item.appId == "default_time")
			return;
		var methodName = item.enabled ? "addDockModeLaunchPoint" : "removeDockModeLaunchPoint";
		this.$.setDockEnabled.call({appId:item.appId}, {method:methodName});
	},
	// [Lunacy] Android's screen saver settings; if the device has none, the line under the
	// button says so.
	openScreenSaver: function() {
		this.$.openSettings.call({panel: "screensaver"});
	},
	openedScreenSaver: function(inSender, r) {
		if (!r || !r.returnValue) {
			this.$.screenSaverText.setContent($L("This device has no screen saver settings."));
		}
	},
	enterExhibitionMode : function(inSender, inEvent) {
		this.$.startExhitbitionMode.call({state:"dock"});
	},
	handleFindMore: function(inSender, inEvent) {
		
		var options = { common: {sceneType: "search"}};
		  options.common.params = {
		    sublaunch : true,
		    type: "connector",
		    connectorInfo: {
		      "types": [
		        "dockMode"
		      ],
		      searchBarIcon: "images/appcatalog.png",
		      searchBarTitle: $L("Exhibition Mode")
		    }
		  };

		  this.$.appLaunch.call({id:"com.palm.app.enyo-findapps",params:options}); 
	},
});

enyo.kind({
	name:"ExhibitAppItem",
	kind:"Item",
	tapHighlight: false,
	align:"center",
	published:{
		itemInfo:{}
	},
	events:{
		onItemChange:""
	},
	components: [
	             {
	            	 kind: "HFlexBox", align: "center",
	            	 components: [
	            	              {flex: 1, name:"itemName", style:"margin-left:5px"},
	            	              {kind:"CheckBox", name:"itemChecked", checked:false, onChange:"checkBoxChangeHandler"}
	            	             ]
	             }
	],
	
	create: function() {
		this.inherited(arguments);
		this.decorateRow();
	},
	
	decorateRow: function() {
		this.$.itemChecked.setChecked(this.itemInfo.enabled);
		this.$.itemName.setContent(this.itemInfo.displayName);
		this.$.itemChecked.setDisabled(this.itemInfo.disabled);
	},
	
	checkBoxChangeHandler: function(inSender, inEvent) {
		this.itemInfo.enabled = inSender.getChecked();
		this.doItemChange();
	}
});