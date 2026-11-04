/*globals enyo, $L, event, Utilities, window, LibraryTools */
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewArtistDetail",
	kind: "listViewDetail",
	height: "100%",
	events: {onResetView: ""},
	className: "artistDetail",
	components: [
		// the header
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchSongs", boolShowSearchBtn: false},
		// the content
		{kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			{name: "txtResults"},
			// the list
			{name: "listMedia", kind: "VirtualList", onQuery: "listQuery", onSetupRow: "listSetupRow", className: "scroll", flex: 1, components: [
				{name: "itemMedia", kind: "draggableItem", className: "ArtistDetailMedia", onclick: "onclick_listMediaFilter", onmousehold: "mousehold_itemmedia", components: [
					// album divider
					{name: "headerAlbum", kind: "Item", className:"header album", showing: false, components: [
						{kind: "Control", className: "content", align:"center", layoutKind: "HFlexLayout", components: [
							{className: "cover", onclick: "onclick_playArtist", components: [
								{name: "imgContainer", className: "img", components: [
									{name: "imgThumb", kind: "Image", className: "thumb"}
								]}
							]},
							{kind: "Control", components: [
								{name: "albumName", className: "name", content: "Siamese Dream"},
								{name: "albumCounts", className: "count", content: "10 songs", showing: false} // todo: track count
							]}
						]}
					]},
					// the divider
					{name: "header", kind: "Control", layoutKind: "HFlexLayout", className: "divider first", align:'center', showing: false, components: [ //there is only one header, set class manually :)
						{name: "lblSortPos", content: $L("#"), className: "column number sorted", onclick: "onclick_Sort"},
						{name: "lblSortSong", content: $L("Song"), flex: 10, className: "column", onclick: "onclick_Sort"},
						{name: "lblSortAlbum", content: $L("Time"), flex: 2, className: "column", showing: false}
					]},
					// the song
					{name: "song", kind: "Item", layoutKind: "HFlexLayout", className: "song", components: [
					    {kind: "Control", name: "songPlaying", className: "column album_artist_Blueicon", showing: true},
						{name: "songTrack", className: "column number"},
						{name: "songTitle", flex: 10, className: "column title"},
						{name: "songTime", flex: 2, className: "column time", showing: false}
					]}
				]}
			]}
		]},
		// the toolbar
		{kind: "Toolbar", className: "enyo-toolbar-light", /*style: "background:#222", */ slidingHandler: true, components: [{kind: "GrabButton"}]}
    ],

    listViewDetailType: "artist",
    strSortMode: "pos", //overwrite listVeiwDetails version
    
	create: function () {
		//test
		
		try
		{
			this.inherited(arguments);
			this.$.ctrlListViewHeader.setIntHeaderCount("");
			this.listViewDetailType = "artist";
		}
		catch (err)
		{
			this.log("**** create error: " + err);
		}

		//this.$.listMedia.update();
		//this.$.listMedia.setPageSize(200);
		
		
		
	},
		
	ready: function ()
	{

		this.inherited(arguments);	
	
	},
	
	rendered: function()
	{
		this.inherited(arguments);
		
	},
	
	listSetupRow: function (inSender, intIndex)
	{
		try
		{
			//this.log("intIndex: " + intIndex);
			
			this.log(intIndex);
			
			if(this.arSongs !== undefined)
			{				
				if(this.arSongs[intIndex] !== undefined)
				{
					var objSong = this.arSongs[intIndex];
					
					this.log("title: ", objSong.title);
					//this.log(objSong.title);
				
					this.updateUISongInfo(objSong);

					//this.$.song.addRemoveClass("playing", (objSong._id === this.strCurrTrackID));
					this.setPlayingStyles(objSong._id, this.$.song, this._strListQuery + this.strSortMode);
					
					var intAlbumStartIndex = 0;
					
					
					var strPrevItemAlbum = null;
					//var intPrevItemDiscPos = null;
					
					if(this.arSongs[intIndex-1])
					{
						strPrevItemAlbum = this.arSongs[intIndex-1].album;
					}
					
					this.log(strPrevItemAlbum);
					this.log(objSong.album);
					
					if(strPrevItemAlbum === null || objSong.album !== strPrevItemAlbum) // Checks for a change in the album name (also done in sort)
					{
						
						intAlbumStartIndex = intIndex;
						this.$.headerAlbum.addClass("first");
						this.$.headerAlbum.show();
						this.$.header.show();
						
				//		this.$.itemMedia.addClass("itemDivider");
						
						this.$.albumName.setContent(objSong.album);
						
						var strThumbPath;	
					
						if(objSong.thumbnails)
						{
							var objThumb = Utilities.getItemThumb(objSong.thumbnails, 54);
							
							if(objThumb)
							{
								
								//var intThumbsTotalCount = objThumb.intThumbsTotalCount;
								
								//var strThumbRUL = objThumb.strThumbURL;
								
								if(objThumb.intThumbsTotalCount > 0)
								{
									strThumbPath = objThumb.strThumbURL;
									this.log("thumbnail: ", objThumb.strThumbURL);
									this.$.imgThumb.setSrc(objThumb.strThumbURL);
									this.$.imgContainer.show();
								}
								else
								{
									this.log("No thumbnail");
									this.$.imgContainer.hide();
								}
									
								}
							else
							{
								this.$.imgContainer.hide();
							}
							
						}
						else
						{
							this.log("empty thumbnail array");
							this.$.imgContainer.hide();
						}
						
				

					}			
					else
					{
						this.$.headerAlbum.removeClass("first");
						this.$.headerAlbum.hide();	
						//this.$.header.show();
					}
					this.log("song addRemoveClass");
					this.$.song.addRemoveClass("odd", ((intIndex - intAlbumStartIndex) % 2 === 0 ));
				
				
					this.log("Returning true");
					return true;
				}
				this.log("Record not found");
				
			//return true;
			}

	
		}
		catch(err)
		{
			this.log("error: " + err);
		}
	},
	
	onclick_listMediaFilter: function(sender, event){
		var domTarget = event.dispatchTarget;
		while(domTarget && domTarget.parent && domTarget.id != "musicPlayerApp_listViewArtistDetail_itemMedia"){
			if(domTarget.id == "musicPlayerApp_listViewArtistDetail_headerAlbum" || domTarget.id == "musicPlayerApp_listViewArtistDetail_header")
			{
				return;
			}
			domTarget = domTarget.parent;
		}
		this.onclick_listMedia(sender, event);
	},
	
	
	onclick_grabButton: function ()
	{
		this.log();
		this.doResetView();	
	},
	
	mousehold_itemmedia: function(sender, event){
		//check the id's of the album top to see if tapping a song or the album
		var domTarget = event.dispatchTarget;
		this.dragObjType = "song";
		while(domTarget && domTarget.parent && domTarget.id != "musicPlayerApp_listViewArtistDetail_itemMedia"){
			if(domTarget.id == "musicPlayerApp_listViewArtistDetail_headerAlbum")
			{
				this.albumMousehold(sender, event);
				return;
			}
			if(domTarget.id == "musicPlayerApp_listViewArtistDetail_header")
			{
				return;
			}
			domTarget = domTarget.parent;
		}
		this.songMousehold(sender, event);
	},
	
	setItemHighlighted: function(inHighlight){
		if(this.dragObjType == "song")
		{
			this.$.song.addRemoveClass("selected", inHighlight);
		}
		else //if album
		{
			this.$.headerAlbum.addRemoveClass("selected", inHighlight);	
		}
	}
	
});
        