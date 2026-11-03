enyo.kind({
	name:"SearchPreferencesEdit",
	kind:"Control",
	published: {
		searchItems:[],
		currentDefaultSeachEngineId:""
    },
    events: {
    	onItemSelected:""
    },
    components:[
                {name:"headerTitle", content:$L("Select the default Search Engine.")},
                {kind: "RowGroup", className:"accounts-group", name:"searchEngineGroup",components: []}
    ],
    
    create: function() {
    	this.inherited(arguments);
    },
    
    searchItemsChanged: function() {
    	this.$.searchEngineGroup.destroyControls();
		var componentsArr = [];
		
		for(var i = 0; i < this.searchItems.length; i++) {
			if(this.searchItems[i].type == "app")
				continue;
			this.searchItems[i].isDefaultSearchEngine = false;
			if(this.searchItems[i].id == this.currentDefaultSeachEngineId)
				this.searchItems[i].isDefaultSearchEngine = true;
			componentsArr.push({kind:"SearchItemEdit", itemInfo:this.searchItems[i], onclick: "handleItemSelected"});
		}
		this.$.searchEngineGroup.createComponents(componentsArr, {owner:this});
		this.$.searchEngineGroup.render();
    },
    
    handleItemSelected: function(inSender, inEvent) {
    	if(!inSender.getItemInfo().isDefaultSearchEngine) {
    		this.doItemSelected(inSender.getItemInfo());
    	}
    }	
});


enyo.kind({
	name:"SearchItemEdit",
	kind:"Item",
	tapHighlight: true,
	align:"center",
	published:{
		itemInfo:{}
	},
	components: [{
		kind: "HFlexBox", align: "center", components: [
			{kind:"Image", name:"itemImage", className:"list-icon"},
			{flex: 1, name:"itemName", style:"margin-left:5px"},
			{name:"isDefault", style:"width:32px; height:25px"}
		]}
	],
	
	create: function() {
		this.inherited(arguments);
		this.decorateRow();
	},
	
	decorateRow: function() {
		this.$.itemName.setContent(this.itemInfo.displayName);
		this.$.itemImage.setSrc(this.itemInfo.iconFilePath);
		if(this.itemInfo.isDefaultSearchEngine)
			this.$.isDefault.setClassName("item-checked");
	}
});