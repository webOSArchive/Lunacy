/*globals enyo, $L, event */
enyo.kind({
	name: "SearchControl",
	kind: "Control",
	className: "search",
	published: {TypeDownSearch: false},
	events: {onSearch:"", onClose: ""},
		components: [
			{
			align: "center",
			name: "txtSearch",
			kind: "RoundedInput",
			hint: "",
			//hint: $L("Search"),
			autocorrect: false,
			spellcheck: false,
			changeOnInput: true,
			disabled: false,
			keypressInputDelay: 0.5,
			//onkeypress: "onkeypress_SearchInput",
			onchange: "onChangeSearch",
			components: [
				{name: "imgSearchClose", kind: "Control", className:"close", onclick: "onClickClose"}
				]
			
			}
		],
	
	boolClearSearch: false,
	
	clearSearchInput: function (boolForceRefresh)
	{	
		this.log();
		
		if(boolForceRefresh === undefined)
		{
			boolForceRefresh = true;
		}
	
		this.$.txtSearch.setValue("");
		
		if(boolForceRefresh)
		{
			this.doSearch("");
		}
	//this.boolClearSearch = false;

	},
	
	focusSearch: function ()
	{
		this.$.txtSearch.setDisabled(false);
		this.$.txtSearch.forceFocus();		
	},
	
	runSearch: function ()
	{
		this.log(this.$.txtSearch.getValue());
		//this.boolClearSearch = true;
		
		this.doSearch(this.$.txtSearch.getValue());		
	},
	
	
	onClickSearch: function ()
	{
		this.log(this.$.txtSearch.getValue());

	},
	
	onClickClose: function ()
	{
		this.log();
		this.doClose();
		this.$.txtSearch.forceBlur();
		this.$.txtSearch.setDisabled(true);
		this.clearSearchInput(true);
		//this.$.imgSearchClose.hide();
		//this.log(this.$.txtSearch.getValue());
		this.$.txtSearch.setValue("");
		this.doSearch("");		

	},
	
	/*
	onkeypress_SearchInput: function (sender, event)
	{
		this.log(event.keyCode);
		
		if(event.keyCode === 13)
		{
			this.runSearch();
			//this.$.txtSearch.forceBlur();
		}
		
		
	},*/
	
	onChangeSearch: function (sender, event)
	{
		
		this.log(this.$.txtSearch.getValue());
		
		/*
		if(this.$.txtSearch.getValue() !== "")
		{
			this.$.imgSearchClose.show();
		}
		else
		{
			this.$.imgSearchClose.hide();
		}
		*/
		
		
		if(this.TypeDownSearch)
		{
			this.runSearch();
		}

		
		
	}
});