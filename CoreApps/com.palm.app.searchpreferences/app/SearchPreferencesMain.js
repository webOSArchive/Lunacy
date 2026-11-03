enyo.kind({
	name:"SearchPreferencesMain",
	kind:"Control",
	published: {
		justTypeList:{},
		searchPreferences:{},
		optionalSearchList:{},
		defaultSearchEngineInfo:""
	},
	components: [  
	             {name:"headerTitle", content:$L("Select the items you would like to appear in your search results."), className:"accounts-body-title"},
		{kind:"RowGroup", className:"accounts-group", components:[
			{kind: "HFlexBox", name:"appSearch", align: "center", tapHighlight: false, components: [
				{flex:1, content: $L("Applications")},
				{kind:"ToggleButton", prefKey:"AppSearch", style:"margin-right:-10px;", state:false, onChange:"handlePrefChange"}
			]},
		]},
		{kind:"RowGroup", className:"accounts-group", components:[
			{kind: "HFlexBox", name:"contactSearch", align: "center", tapHighlight: false, components: [
				{flex:1, content: $L("Contacts")},
				{kind:"ToggleButton", prefKey:"ContactSearch", state:false, style:"margin-right:-10px;", onChange:"handlePrefChange"},
			]},
			{kind: "HFlexBox", align: "center", name:"galItem", showing:true, tapHighlight: false, components: [
				{flex: 1, content: $L("Automatic Global Address Lookup")},
				{kind: "ToggleButton", prefKey:"GAL", state:false, style:"margin-right:-10px;", onChange:"handlePrefChange"}
			]}
		]},
		{kind:"RowGroup", className:"accounts-group", name:"defaultSearchEngineGroup", components:[
			{kind: "Item", layoutKind: "HFlexLayout", name:"defaultSearchEngine", align: "center", className:"search-row-title", tapHighlight: false, components: [
				{flex:1, content: $L("Default Search Engine")},
				{kind:"ToggleButton", state:false, prefKey:"defaultSearch", onChange:"handlePrefChange"}
			]}
		]},
		{kind: "RowGroup", className:"accounts-group", name:"contentGroup",	components: []},
		{kind: "RowGroup", className:"accounts-group", name:"searchUsingGroup", components: []},
		{kind: "RowGroup", className:"accounts-group", name:"actionGroup", components: []},
		{kind: "Button", onclick:"handleFindMore", layoutKind:"HFlexLayout", align:"center", className:"accounts-btn", components:[
			{kind:"Image", src:"images/appcatalog.png", className:"icon-image"},
			{content:$L("Find More...")}
		]},
		{content: $L("Launch the App Catalog to find more applications that have Search Services or Quick Actions."), className:"accounts-body-text", style:"padding-bottom:30px"},
		{kind:"PalmService", service:"palm://com.palm.universalsearch/", components: [
			{name:"setSearchPreference", method:"setSearchPreference"},
			{name:"updateSearchItem", method:"updateSearchItem"},
			{name:"addSearchItem", method:"addSearchItem"},
			{name:"updateAllSearchItems", method:"updateAllSearchItems"}
		]},
		{kind:"PalmService",  service:"palm://com.palm.applicationManager/", name:"appLaunch", method:"open"}
	],
	
	create: function() {
		this.inherited(arguments);
		this.searchList = [];
		this.contentList = [];
		this.actionsList = [];
		this.openSearchList = [];
		this.defaultSearchEngineId = "";
		this.refreshTimer = undefined;
		this.ignoreOptionalListUpdate = false;
	},
	
	searchPreferencesChanged: function() {
		var obj;
		obj = this.$.appSearch.getControls()[1];
		if(obj instanceof enyo.ToggleButton) 
			obj.setState(this.searchPreferences.SearchPreference.AppSearch == "true" ? true : false);
		
		obj = this.$.contactSearch.getControls()[1];
		if(obj instanceof enyo.ToggleButton)
			obj.setState(this.searchPreferences.SearchPreference.ContactSearch == "true" ? true : false);
		
		obj = this.$.defaultSearchEngine.getControls()[1];
		if(obj instanceof enyo.ToggleButton)
			obj.setState(this.searchPreferences.SearchPreference.defaultSearch == "true" ? true : false);
		
		obj = this.$.galItem.getControls()[1];
		if(obj instanceof enyo.ToggleButton)
			obj.setState(this.searchPreferences.SearchPreference.GAL == "true" ? true : false);
		
		this.defaultSearchEngineId = this.searchPreferences.SearchPreference.defaultSearchEngine;
	},
	
	justTypeListChanged: function() {
		
		if(this.justTypeList.UniversalSearchList) {
			this.searchList = this.justTypeList.UniversalSearchList;
			//We refresh the UI group only for events "newapp" or "remove"
			if(this.justTypeList.event && this.justTypeList.event != "newapp" && this.justTypeList.event != "remove")
				return;
			this.$.searchUsingGroup.destroyControls();
			this.populateSearchUsingList();
		}
		
		
		if(this.justTypeList.DBSearchItemList) {
			this.contentList = this.justTypeList.DBSearchItemList;
			this.$.contentGroup.destroyControls();
			this.populateContentList();
		}
		
		if(this.justTypeList.ActionList) {
			this.actionsList = this.justTypeList.ActionList;
			this.$.actionGroup.destroyControls();
			this.populateActionsList();
		}
	},
	
	optionalSearchListChanged: function() {
		this.openSearchList = this.optionalSearchList.Options;
		if(this.ignoreOptionalListUpdate) {
			this.ignoreOptionalListUpdate = false;
			this.appendOpenSearchItems();
			return;
		}
		if(this.owner.initQuery) {
			if(this.refreshTimer) {
				try{
					clearTimeout(this.refreshTimer);
					this.refreshTimer = undefined;
				}catch(e) {}
			}
			this.refreshTimer = setTimeout(enyo.bind(this, "appendOpenSearchItems"), 10000);
		}
		else
			this.appendOpenSearchItems();
	},
	
	defaultSearchEngineInfoChanged: function() {
		
		this.$.searchUsingGroup.destroyControls();
		this.$.defaultSearchEngineGroup.getControls()[1].destroy(); //index:2 is the default Search Item.
		
		this.defaultSearchEngineId = this.defaultSearchEngineInfo.id;
		this.populateSearchUsingList();
		this.appendOpenSearchItems();
		this.defaultSearchEngineInfo.setDefault = true;
		this.defaultSearchEngineInfo.enabled = true;
		this.defaultSearchEngineInfo.category = "search";
		var itemExist = false;
		for(var i = 0; i < this.searchList.length; i++) {
			if(this.searchList[i].id == this.defaultSearchEngineId) {
				itemExist = true;
			}
		}
		this.ignoreOptionalListUpdate = true;
		this.callUpdateService(this.defaultSearchEngineInfo, itemExist);
	},
	
	appendOpenSearchItems: function() {

		var componentsArr = []; 
		var controlsArr = this.$.searchUsingGroup.getControls();
		
		for(var i = 1; i < controlsArr.length; i++) {
			var c = controlsArr[i];
			if(c instanceof JustTypeItem && c.getItemInfo().type && c.getItemInfo().type == "opensearch" && c.getItemInfo().enabled === false && !c.getItemInfo().searchListItem) {
				c.destroy();
			}
		}
		
		if(this.openSearchList.length == 0)
			return;
		
		for (var i = 0; i < this.openSearchList.length; i++) {
			if(this.openSearchList[i].id === this.defaultSearchEngineId) {
				if(this.$.defaultSearchEngineGroup.getControls().length == 1) {
					var itemNode = {kind: "Item", align: "center", tapHighlight: true, layoutKind: "HFlexLayout", onclick:"changeDefaultSearchEngine",
									components: [
									             {kind:"Image", src:this.openSearchList[i].iconFilePath, className:"list-icon"},
					                             {flex: 1, content: enyo.string.escapeHtml(this.openSearchList[i].displayName)},
					                             {content:$L("Edit")}
					               ]};
					this.$.defaultSearchEngineGroup.createComponent(itemNode, {owner:this}); 
					this.$.defaultSearchEngineGroup.render();
				}
				continue;
			}
			this.openSearchList[i].type = "opensearch";
			this.openSearchList[i].category = "search";
			this.openSearchList[i].enabled = false;
			this.openSearchList[i].iconFilePath = this.openSearchList[i].imageData;
			
			componentsArr.push({kind:"JustTypeItem", itemInfo:this.openSearchList[i], onItemChange: "handleItemChange"});
		}
		this.$.searchUsingGroup.createComponents(componentsArr, {owner:this});
		this.$.searchUsingGroup.render();
	},
	
	populateSearchUsingList: function() {
		var enableGroup = false;
		var componentsArr = [];
		
		for(var i = 0; i < this.searchList.length; i++) {
			if(this.searchList[i].id === this.defaultSearchEngineId) {
				if(this.$.defaultSearchEngineGroup.getControls().length == 1) {
					var itemNode = {kind: "Item", align: "center", tapHighlight: true, layoutKind: "HFlexLayout", onclick:"changeDefaultSearchEngine",
									components: [
									             {kind:"Image", src:this.searchList[i].iconFilePath, className:"list-icon"},
					                             {flex: 1, content: enyo.string.escapeHtml(this.searchList[i].displayName)},
					                             {content:$L("Edit")}
					               ]};
					this.$.defaultSearchEngineGroup.createComponent(itemNode, {owner:this}); 
					this.$.defaultSearchEngineGroup.render();
				}
				continue;
			}
			if(this.searchList[i].enabled)
				enableGroup = true;
			this.searchList[i].category = "search";
			this.searchList[i].searchListItem = true;
			componentsArr.push({kind:"JustTypeItem", itemInfo:this.searchList[i], onItemChange: "handleItemChange"});
		}
		componentsArr.splice(0,0,{kind:"JustTypeGroupItem", groupItemInfo:{displayName:$L("Search Using"), category:"search", enabled:enableGroup}, className:"search-row-title", onGroupItemChange:"groupCheckBoxHandler"});
		this.$.searchUsingGroup.createComponents(componentsArr, {owner:this});
		this.$.searchUsingGroup.render();
	},
	
	populateContentList: function() {
		var enableGroup = false;
		var componentsArr = [];
		
		for(var i = 0; i < this.contentList.length; i++) {
			if(this.contentList[i].enabled)
				enableGroup = true;
			this.contentList[i].category = "dbsearch";
			componentsArr.push({kind:"JustTypeItem", itemInfo:this.contentList[i], onItemChange: "handleItemChange"});
		}
		componentsArr.splice(0,0,{kind:"JustTypeGroupItem", groupItemInfo:{displayName:$L("Content"), category:"dbsearch", enabled:enableGroup}, className:"search-row-title", onGroupItemChange:"groupCheckBoxHandler"});
		this.$.contentGroup.createComponents(componentsArr, {owner:this});
		this.$.contentGroup.render();
	},
	
	populateActionsList: function() {
		var enableGroup = false;
		var componentsArr = [];
		
		for(var i = 0; i < this.actionsList.length; i++) {
			if(this.actionsList[i].enabled)
				enableGroup = true;
			this.actionsList[i].category = "action";
			componentsArr.push({kind:"JustTypeItem", itemInfo:this.actionsList[i], onItemChange: "handleItemChange"});
		}
		componentsArr.splice(0,0,{kind:"JustTypeGroupItem", groupItemInfo:{displayName:$L("Actions"), category:"action", enabled:enableGroup}, className:"search-row-title", onGroupItemChange:"groupCheckBoxHandler"});
		this.$.actionGroup.createComponents(componentsArr, {owner:this});
		this.$.actionGroup.render();
	},
	
	handleItemChange: function(inSender, inEvent) {
		//update the list
		var listArray;
		var category = inSender.getItemInfo().category;
		var nodeId = inSender.getItemInfo().id;
		var newValue = inSender.getItemInfo().enabled;
		var itemInfo = inSender.getItemInfo();
		var enableGroup = false;
		var controlsArray;
		var itemExist = false;
		
		if(category == "search") {
			listArray = this.searchList;
			controlsArray = this.$.searchUsingGroup.getControls();
		}
		else if(category == "dbsearch") {
			listArray = this.contentList;
			controlsArray = this.$.contentGroup.getControls();
		}
		else if(category == "action") {
			listArray = this.actionsList;
			controlsArray = this.$.actionGroup.getControls();
		}
		
		for(var i = 0; i < listArray.length; i++) {
			if(listArray[i].id === nodeId) {
				listArray[i].enabled = newValue;
				itemExist = true;
			}
			if(listArray[i].enabled && (listArray[i].id !== this.defaultSearchEngineId)) { 
				enableGroup = true;
			}
		}
		
		if(controlsArray[0] instanceof JustTypeGroupItem) {
			controlsArray[0].$.groupItemChecked.setState(enableGroup);
		}
		
		this.callUpdateService(itemInfo, itemExist);
		
	},
	
	callUpdateService: function(itemInfo, itemExistInSearchList) {
		var params = {"id": itemInfo.id,
				"enabled": itemInfo.enabled,
				"category": itemInfo.category,
				"setDefault": itemInfo.setDefault
		};
		
		if(itemInfo.type == "opensearch" && !itemExistInSearchList) {
			params.url = itemInfo.searchUrl;
			params.displayName = itemInfo.displayName;
			params.iconFilePath = itemInfo.iconFilePath;
			params.suggestURL = itemInfo.suggestionUrl;
			params.type = itemInfo.type;
			
			this.$.addSearchItem.call(params);
		}
		else
			this.$.updateSearchItem.call(params);
	},
	
	groupCheckBoxHandler: function(inSender, inEvent) {
		
		if(!inSender || !inSender.getGroupItemInfo())
			return;
		
		var listArray;
		var controlsArray;
		
		var category = inSender.getGroupItemInfo().category;
		
		if(category == "search") {
			listArray = this.searchList;
			controlsArray = this.$.searchUsingGroup.getControls();
		}
		else if(category == "dbsearch") {
			listArray = this.contentList;
			controlsArray = this.$.contentGroup.getControls();
		}
		else if(category == "action") {
			listArray = this.actionsList;
			controlsArray = this.$.actionGroup.getControls();
		}
		//update the list
		for(var i = 0; i < listArray.length; i++) {
			listArray[i].enabled = inSender.getGroupItemInfo().enabled;
		}
		//update the List.
		for(var i = 1; i < controlsArray.length; i++) {
			if(controlsArray[i] instanceof JustTypeItem) {
				controlsArray[i].$.itemChecked.setChecked(inSender.getGroupItemInfo().enabled);
			}
		}
		
		//Notify the Service
		this.$.updateAllSearchItems.call({"category": category, "enabled": inSender.getGroupItemInfo().enabled});
		
		//TODO: May want to block the UI until we get the response from the service.
	},
	
	changeDefaultSearchEngine: function(inSender, inEvent) {
		var list = this.searchList;
		list = list.concat(this.openSearchList);
		this.owner.$.searchPreferencesEdit.setCurrentDefaultSeachEngineId(this.defaultSearchEngineId);
		this.owner.$.searchPreferencesEdit.setSearchItems(list);
		this.owner.$.pane.selectViewByName("searchPreferencesEdit");
	},
	
	handlePrefChange: function(inSender, inEvent) {
		var prefKey = inSender.prefKey;
		var prefValue;
		
		prefValue = inSender.getState();
		this.$.setSearchPreference.call({key: prefKey, value: prefValue});
	},
	
	galAvailability: function(galExist) {
		this.$.galItem.setShowing(galExist);
	},
	handleFindMore: function(inSender, inEvent) {
		var options = { common: {sceneType: "search"}};
		  options.common.params = {
		    sublaunch : true,
		    type: "connector",
		    connectorInfo: {
			  types: ["universalSearch"],
		      searchBarIcon: "images/appcatalog.png",
		      searchBarTitle: $L("Just Type Services")
		    }
		  };

		  this.$.appLaunch.call({id:"com.palm.app.enyo-findapps",params:options}); 
	},
});

enyo.kind({
	name:"JustTypeGroupItem",
	kind:"Item",
	tapHighlight: false,
	align:"center",
	layoutKind:"HFlexLayout",
	published:{
		groupItemInfo:{}
	},
	events:{
		onGroupItemChange:""
	},
	components: [
	             {flex: 1, name:"groupItemName"},
	             {kind:"ToggleButton", name:"groupItemChecked", state:false, onChange:"groupCheckBoxChangeHandler"}
	],
	
	create: function() {
		this.inherited(arguments);
		this.decorateRow();
	},
	
	decorateRow: function() {
		this.$.groupItemChecked.setState(this.groupItemInfo.enabled);
		this.$.groupItemName.setContent(this.groupItemInfo.displayName);
	},
	
	groupCheckBoxChangeHandler: function(inSender, inEvent) {
		this.groupItemInfo.enabled = inSender.getState();
		this.doGroupItemChange();
	}
});

enyo.kind({
	name:"JustTypeItem",
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
	            	              {kind:"Image", name:"itemImage", className:"list-icon"},
	            	              {flex: 1, name:"itemName", style:"margin-left:5px"},
	            	              //{name:"itemType", className:"list-label"},
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
		this.$.itemImage.setSrc(this.itemInfo.iconFilePath);
		//if(this.itemInfo.category == "search")
			//this.$.itemType.setContent(this.itemInfo.type == "app" ? $L("App") : $L("Web"));
	},
	
	checkBoxChangeHandler: function(inSender, inEvent) {
		this.itemInfo.enabled = inSender.getChecked();
		this.doItemChange();
	}
});