/*globals enyo, $L, event */
enyo.kind({
	name: "ItemGrid",
	kind: "Item",
	className: "enyo-item genre",
	layout: "VFlexLayout",
	
	published: {strImageURL: "", strItemText: "", strItemSubText: "", intThumbCount: 1, boolLandscape: true},
	
	components: [
		{name: "covers", className: "column cover", onclick: "onclick_imgMenu", components: [{className: "img", name: "imgContainer", showing: false, components: [{name: "imgThumb", kind: "Image", className: "thumb", onerror: "onError_imgThumb"}]}]},
		{name: "lblItemText",className: "column name", content: "The Smashing Pumpkins"},
		{name: "lblItemSubText",className: "column count", content: "2 genres, 23 tracks"}
	],
	
	create: function ()
	{
		this.inherited(arguments);
	},
	
	strImageURLChanged: function ()
	{
		if(this.strImageURL === undefined)
		{
			this.strImageURL = "";
		}
		this.$.imgContainer.setShowing(this.strImageURL !== "");
		this.$.imgThumb.setSrc(this.strImageURL);
	},
	
	strItemTextChanged: function ()
	{
		this.$.lblItemText.setContent(this.strItemText);
	},
	
	strItemSubTextChanged: function ()
	{
		this.$.lblItemSubText.setContent(this.strItemSubText);
	},
	
	intTumbCountChanged: function ()
	{
		this.$.covers.addRemoveClass("many", this.intThumbCount > 1);
	},
	
	boolLandscapeChanged: function ()
	{
//		this.addRemoveClass("portrait", !this.boolLandscape);
		
	},
	
	onError_imgThumb: function (sender, event)
	{
		//this.log(sender.getSrc());
		this.$.imgContainer.setShowing(false);
	}
	
});