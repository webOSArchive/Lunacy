/*globals enyo, $L, event */
enyo.kind({
	name: "ctrlListViewHeader",
	kind: "Toolbar",
	className: "enyo-toolbar enyo-toolbar-light list-header",
	pack: "justify",
	layoutKind: "HFlexLayout",
	events: {onSearch_Header: "", onEditMode: "", onSetMultiSelect: "" },
	published: {strHeaderTitle: $L("Songs"), intHeaderCount: -1, boolShowDragHandle: false, boolShowEditPlaylist: false, boolShowMultiSelect: false, boolShowSearchBtn: true},
	components: [

			{name: "dragHandle", slidingHandler: true, kind: "GrabButton", showing: false},
			{kind: "Control", layoutKind: "HLayout", className: "list-header-title-layout",components:[
				{name: "lblHeaderTitle", content: "", className: "title enyo-text-ellipsis truncating-text",flex:1},
				{name: "lblSongTitleCount", content: "", className: "count", showing: false}
			]},
			{name: "buttonContainer", className: "buttons", kind: "Control", layoutKind: "HFlexLayout",components: [
			  {name: "btnEditPlaylist", kind: "IconButton", icon: "images/btn_edit.png", showing: false, onclick: "doEditMode"},			
			  {name: "btnMultiSelect", kind: "IconButton",  caption: $L("MS"), /*icon: "images/btn_editPlaylist.png",*/ showing: false, onclick: "doSetMultiSelect"},
			  {name: "btnGetSearch", kind: "IconButton", icon: "images/btn_search.png", onclick: "onClick_btnGetSearch", showing: true}
			]},

			{name: "SearchInput", kind: "SearchControl", onSearch: "doSearch_Header", onClose: "onClose_SearchInput", TypeDownSearch: true, showing: false}
	
	],
	
	
	boolShowSearch: false,
	
	drawHeader: function()
	{
		
		this.$.lblHeaderTitle.setContent(this.strHeaderTitle);
		if(this.intHeaderCount >= 0)
		{
			this.$.lblSongTitleCount.setContent("(" + this.intHeaderCount + ")");
			this.$.lblSongTitleCount.show();
		}
		else
		{
			this.$.lblSongTitleCount.hide();
		}
		this.$.dragHandle.setShowing(this.boolShowDragHandle);
		this.$.btnEditPlaylist.setShowing(this.boolShowEditPlaylist);
		this.$.btnMultiSelect.setShowing(this.boolShowMultiSelect);
		this.$.btnGetSearch.setShowing(this.boolShowSearchBtn);
		
	},
	
	strHeaderTitleChanged: function ()
	{
		this.drawHeader();
	},
	
	intHeaderCountChanged: function ()
	{
		this.drawHeader();
	},
	
	boolShowDragHandleChanged: function ()
	{
		this.drawHeader();
	},
	
	boolShowEditPlaylistChanged: function ()
	{
		this.drawHeader();
	},
	
	boolShowMultiSelectChanged: function ()
	{
		this.drawHeader();
	},
	
	boolShowSearchBtnChanged: function ()
	{
		this.drawHeader();
	},
	
	clearSearchInput: function (boolForceRefresh)
	{
		
		this.$.SearchInput.clearSearchInput(boolForceRefresh);		
		this.onClose_SearchInput();
		
	},
	
	onClick_btnGetSearch: function (sender, event)
	{
		
		
		this.boolShowSearch = true;
		this.$.SearchInput.show();
		this.$.buttonContainer.hide();
		this.log(this.boolShowSearch);
		
		this.$.SearchInput.focusSearch();
		
	},
	
	onClose_SearchInput: function (sender, event)
	{
		
		this.boolShowSearch = false;
		this.$.SearchInput.hide();
		this.$.buttonContainer.show();
		
	},
	
	onClick_btnEditPlaylist: function (sender, event)
	{
				
		this.doEditMode();
		
	}
	

	
});