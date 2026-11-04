/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "draggableItem",
	kind: "Item",
	onmouserelease: "itemMouserelease", ondragstart: "itemDragStart", ondragfinish: "itemDragFinish", ondrag: "onDrag_itemMedia", ondrop: "onDrop_itemMedia"
});

enyo.kind({
	name: "draggableSong",
	kind: "draggableItem",
	onmousehold: "songMousehold"
});

enyo.kind({
	name: "draggableAlbum",
	kind: "draggableItem",
	onmousehold: "albumMousehold"
});

enyo.kind({
	name: "draggableAlbumGroup", //from listViewAlbums
	kind: "draggableItem",
	onmousehold: "albumGroupMousehold"
});

enyo.kind({
	name: "draggableArtist",
	kind: "draggableItem",
	onmousehold: "artistMousehold"
});

enyo.kind({
	name: "draggableGenre",
	kind: "ItemGrid",
	onmousehold: "genreMousehold", onmouserelease: "itemMouserelease", ondragstart: "itemDragStart", ondragfinish: "itemDragFinish", ondrag: "onDrag_itemMedia", ondrop: "onDrop_itemMedia"
});

enyo.kind({
	name: "listViewDraggable",
	kind: "listView",
	events: {onAddToPlaylist: "", onItemDrag: "", onItemShowAvatar: "", onItemShowAvatarIcon: ""},
	intJumpRowOffset: 0,
	
	boolDragHold: false,
	boolDragItem: false,
	boolDraggingList: false,
	dragObjType: null,
	arItems: [], //will take on array of draggable items
	albumGroup: false,
	scrolling: false, //will only allow dragging when not scrolling
	
	//Drag & Drop related events	
	create: function () {
		this.inherited(arguments);
	},
	
	ready: function(){
		this.inherited(arguments);
		if(this.$.listMedia){
			this.$.listMedia.$.scroller.scrollStop = enyo.bind(this, function(){this.setScrolling(false);});
			this.$.listMedia.$.scroller.scrollStart = enyo.bind(this, function(){this.setScrolling(true);}); 
		}
	},
	
	setScrolling: function(bool){
		this.scrolling = bool;
	},
	
	setPlayingStyles: function(id, itemMedia, strListQuery){
		if(id === this.strCurrTrackID)
		{
			itemMedia.addRemoveClass("playingHere", (this.strCurrTrackListQuery === strListQuery));
			itemMedia.addClass("playing");
		}
		else
		{
			itemMedia.removeClass("playingHere");
			itemMedia.removeClass("playing");
		}
	},
	
	callAddToPlaylist: function (intIndex, strPlaylistID, addType, callback)
	{
		this.log();
		var objAddToPlaylist;
		var objItem = this.arItems[intIndex];
		switch(addType){
			case "album": //strMediaFilter should be name when in albums view
				if(this.albumGroup){
					objAddToPlaylist = {strMediaType: "album", strMediaFilter: objItem.name, strMediaFilterSub: objItem.artist, strPlaylistID: strPlaylistID};	
				}
				else
				{
					objAddToPlaylist = {strMediaType: "album", strMediaFilter: objItem.album, strMediaFilterSub: objItem.albumArtist, strPlaylistID: strPlaylistID, callback: callback};
				}
				break;
			case "artist":
			case "genre":
				objAddToPlaylist = {strMediaType: addType, strMediaFilter: objItem.name, strPlaylistID: strPlaylistID};
				break;
			default: //case "song":
				objAddToPlaylist = {strMediaType: "song", arTracks: [Utilities.getPlaylistSongData(objItem)], strPlaylistID: strPlaylistID, callback: callback};
				break;
		}
		
		this.log("**** raising doAddToPlaylist for clicked song");
		this.doAddToPlaylist(objAddToPlaylist);
		
	},
	
	songMousehold: function(sender, event){
		this.dragObjType = "song";
		this.arItems = this.arSongs;
		this.itemMousehold(sender, event);
	},
	
	albumMousehold: function(sender, event){
		this.dragObjType = "album";
		this.arItems = this.arSongs;
		this.albumGroup = false;
		this.itemMousehold(sender, event);
	},
	
	albumGroupMousehold: function(sender, event){
		this.dragObjType = "album";
		this.arItems = this.arAlbums;
		this.albumGroup = true;
		this.itemMousehold(sender, event);	
	},
	
	artistMousehold: function(sender, event){
		this.dragObjType = "artist";
		this.arItems = this.arArtists;
		this.itemMousehold(sender, event);
	},
	
	genreMousehold: function(sender, event){
		this.dragObjType = "genre";
		this.arItems = this.arGenres;
		this.itemMousehold(sender, event);
	},
	
	itemMousehold: function(sender, event)
	{
		this.log();
		if(this.boolEditMode === undefined)
		{
			this.boolEditMode = false;
		}
		if(this.boolEditMode || this.scrolling)
		{
			return false;
		}
		var intDragIndex = this.getDragIndex(sender, event);
		
		this.setItemHighlighted(true);
		var strDragThumb = "";
		
		var objItem = this.arItems[intDragIndex];			
		
		if(objItem && window.PalmSystem)
		{
				var objThumb = Utilities.getItemThumb(objItem.thumbnails, 88);
				if(objThumb)
				{
					if(objThumb.intThumbsTotalCount > 0)
					{
						strDragThumb = objThumb.strThumbURL;
					}
				}
		}
		
		this.boolDragHold = true;

		this.doItemDrag(event);
		this.doItemShowAvatar(true, strDragThumb);
	
		return false;
	
	},
	
	
	itemMouserelease: function()
	{
		this.log();
		if (!this.boolDragItem)
		{
			this.setItemHighlighted(false);
			this.doItemShowAvatar(false);
			this.dragObjType = null;
		}

		this.boolDragHold = false;
		return true;
	},	


	itemDragStart: function(sender, event)
	{

		if(this.boolDragHold && !this.boolEditMode)
		{	

			var intDragIndex = this.getDragIndex(sender, event);
			
			var objItem = this.arItems[intDragIndex];
			//event.rowIndex is sometimes off
			if(objItem === undefined){
				this.log("Given an invalid dragIndex");
				this.itemMouserelease();
				return;
			}
			
			this.boolDraggingList = true;
			this.boolDragItem = true;
			
			
			
			switch(this.dragObjType)
			{
				case "song":
					event.dragInfo = {dragItemIndex: intDragIndex, addType: "song", addTitle: objItem.title};
					break;
				case "album":
					if(this.albumGroup)
					{
						event.dragInfo = {dragItemIndex: intDragIndex, addType: "album", addTitle: objItem.name, boolShowIcon: false};
					}
					else
					{
						event.dragInfo = {dragItemIndex: intDragIndex, addType: "album", addTitle: objItem.album, boolShowIcon: false};	
					}
					break;
				case "artist":
					event.dragInfo = {dragItemIndex: intDragIndex, addType: "artist", addTitle: objItem.name, boolShowIcon: false};
					break;
				case "genre":
					event.dragInfo = {dragItemIndex: intDragIndex, addType: "genre", addTitle: objItem.name, boolShowIcon: false};
					break;
			}
			
			this.setItemHighlighted(true);
			this.doItemDrag(event);			
			return true;
		}
	},
	
	onDrag_itemMedia: function (sender, event)
	{
		//this.log();
		if(event.dragInfo)
		{
			this.doItemShowAvatarIcon(event.dragInfo.boolShowIcon);
		}
		
		if (this.boolDragItem)
		{
			this.doItemDrag(event);
		}
		
	},
	
	
	itemDragFinish: function(sender, event)
	{
		//NOTE: listViewPlaylist has it's own
		this.log();
		this.boolDraggingList = false;		
		if (this.boolDragItem) {
			event.dragInfo.boolShowIcon = false;
			
			if(event.dragInfo.strPlaylistID)
			{
				this.callAddToPlaylist(event.dragInfo.dragItemIndex, event.dragInfo.strPlaylistID, event.dragInfo.addType);
			}
			
			if(this.dragObjType === "genre"){
				this.$.listMedia.prepareRow(Math.floor(event.dragInfo.dragItemIndex/this.intColumns));
			}else{ 
				this.$.listMedia.prepareRow(event.dragInfo.dragItemIndex);
			}
			
			this.setItemHighlighted(false);
		
			this.doItemShowAvatarIcon(false);

			this.doItemShowAvatar(false);
		
			this.boolDragItem = false;

			this.dragObjType = null;

		}
		
		//return true;
		
		
	},
	
	setItemHighlighted: function(inHighlight)
	{
		if(this.$.itemMedia){
			this.$.itemMedia.addRemoveClass("selected", inHighlight);
		}
	},
	
	onDrop_itemMedia: function (sender, event)
	{
		this.log(event.keys);

		if(this.boolDraggingList)
		{
			this.boolSuspendClick = true;
		}

		return true;

	},
	
	getDragIndex: function(sender, event){
		if(this.dragObjType === "genre"){
			return (event.rowIndex * this.intColumns) + sender.intColumnIndex;
		}else{ 
			return event.rowIndex + this.intJumpRowOffset;
		}
	}
	
});