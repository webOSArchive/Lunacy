/*globals enyo, $L, event, Utilities, window, LibraryTools */
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewAlbumDetail",
	kind: "listViewDetail",
	className: "DetailView",
	components: [
		// the header
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchSongs", boolShowSearchBtn: false},
		// the content
		{kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			// the list
			{name: "listMedia", kind: "VirtualList", onQuery: "listQuery", onSetupRow: "listSetupRow", className: "listMedia", flex: 1, components: [
			  // the album info
			  {name: "headerAlbum", kind: "draggableAlbum", className:"header album first", showing: false, components: [
				  { kind: "Control", className: "content", align:"center", layoutKind: "HFlexLayout", components: [
					  {className: "cover", onclick: "onclick_playArtist", components: [
						  {name: "imgThumb", kind: "Image", className: "thumb"}
					  ]},
					  {kind: "Control", flex: 1, components: [
						  {name: "albumName", className: "name", content: "Siamese Dream"},
						  {name: "albumCounts", className: "count", content: "10 songs"} // todo: track count
					  ]}
					  
				  ]}
			  ]},
			  // the divider
			  {name: "header", kind: "HFlexBox", className: "divider first", showing: false, components: [
				  {name: "lblSortPos", content: $L("#"), className: "column number sorted", onclick: "onclick_Sort"},
				  {name: "lblSortSong", content: $L("Song"), flex: 1, className: "column", onclick: "onclick_Sort"}
			  ]},
				{name: "itemMedia", kind: "draggableSong", layoutKind: "HFlexLayout", align: "center", className:"song", onclick: "onclick_listMedia", components: [
				    {kind: "Control", name: "songPlaying", className: "column album_artist_Blueicon", showing: true},
					{name: "songTrack", className: "column number"},
					{name: "songTitle", flex: 1, className: "column title"}
				]}
			]}
		]},
		// the toolbar
		{kind: "Toolbar", className: "enyo-toolbar-light", /*style: "background:#222", */ slidingHandler: true, components: [{kind: "GrabButton"}]},
		{name: "diagAlbumDetails", kind: "ModalDialog", width: "750px", height: "750px", dismissWithClick: true, components: [
				
				{layoutKind: "HFlexLayout", components: [
					{kind: "Control", style: "margin-right: 6px;", components: [
						{content: "Name: "},
						{content: "Artist: "},
						{content: "Genre: "},
						{content: "Year: "}
															 
					]},
					{kind: "Control", style: "margin-right: 6px;", components: [
						{name: "lblAlbumName"},
						{name: "lblAlbumArtist"},
						{name: "lblAlbumGenre"},
						{name: "lblAlbumYear"}
					]},
					{kind: "Control", components: [
						{name: "imgAlbum", kind: "Image"}
					]}
				]},
				{content: "Tracks: "},
				{name: "listTracks", kind: "VirtualList", height: "200px;", onSetupRow: "listSetupRow_listTracks", components: [
					{name: "itemTrack", kind: "Item", components: [
						{name: "lblTrackName"}
					]} 
				]}
		]}
		
		/*
		{name: "txtResults", kind: "VFlexBox", align:"center", height:"100%", pack: "justify", className: "listEmpty", showing: false, components: [
			{kind: "Spacer", flex: 1},
			{kind: "Image", src: "images/bg_playlist_empty.png"},
			{content: $L("There are no songs in this playlist."), className: "heading"},
			{content: $L("Drag and drop songs onto this playlist's navigation item to add songs to it."), className: "subheading"},
			{kind: "Spacer", flex: 3}		
		]}
		*/
    ],

    listViewDetailType: "album",
    strSortMode: "pos", //overwrite listVeiwDetails version
    
	create: function () {
		//test
		
		try
		{
			this.inherited(arguments);
			this.listViewDetailType = "album";
		}
		catch (err)
		{
			this.log("**** create error: " + err);
		}

		//this.$.listMedia.update();
		//this.$.listMedia.setPageSize(200);
		this.local_tracks_Templ = new enyo.g11n.Template($L("(#{num} songs)"));
		this.local_track_Templ = new enyo.g11n.Template($L("(#{num} song)"));	
		this.$.ctrlListViewHeader.setIntHeaderCount("");
	},
		
	ready: function ()
	{

		this.inherited(arguments);	
		
	},
	
	rendered: function()
	{
		this.inherited(arguments);
		
	},
	
	setStrHeaderTitle: function(){
		//should do nothing
		//overwrites inherrited method
	},
	
	listSetupRow: function (inSender, intIndex)
	{
		try
		{
			this.log("intIndex: " + intIndex);
			if(this.arSongs !== undefined)
			{				
				if(this.arSongs[intIndex] !== undefined)
				{
					var objSong = this.arSongs[intIndex];
					
					this.log("title: " + objSong.title);
				
					this.updateUISongInfo(objSong);
					
					this.setPlayingStyles(objSong._id, this.$.itemMedia, this._strListQuery + this.strSortMode);
					//this.$.itemMedia.addRemoveClass("playing", (objSong._id === this.strCurrTrackID));
					
					var intAlbumStartIndex = 0;
					
					/*var charPrevItemAlbum = null;
				
					if(this.arSongs[intIndex-1])
					{
						charPrevItemAlbum = this.arSongs[intIndex-1].album;
					}*/
					
					if(intIndex === 0)
					{
						this.$.albumName.setContent(objSong.album);
						
						if(this.arSongs.length !== 1)
						{
							this.$.albumCounts.setContent(this.local_tracks_Templ.evaluate({num: this.arSongs.length}));
						}
						else
						{
							this.$.albumCounts.setContent(this.local_track_Templ.evaluate({num: this.arSongs.length}));
						}
						

						
						//this.$.albumCounts.setContent(this.arSongs.length);
							
						this.$.headerAlbum.show();
						this.$.header.show();
					/*}
					
					if(objSong.album !== charPrevItemAlbum || charPrevItemAlbum === null) // Checks for a change in the album name 
					{*/
						
						intAlbumStartIndex = intIndex;	
						
						var objThumb = Utilities.getItemThumb(this.objCurr.thumbnails, 54);
						this.log(objThumb.strThumbURL);
						if(objThumb.strThumbURL !== "")
						{
							
							this.$.imgThumb.setSrc(objThumb.strThumbURL);
							this.$.imgThumb.setShowing(true);
						}
						else
						{
							this.$.imgThumb.setShowing(false);
						}
						
					}			
		
					this.$.itemMedia.addRemoveClass("odd", (intIndex % 2 === 0 ));
					
					
					return true;
				}
			
			}

	
		}
		catch(err)
		{
			this.log("error: " + err);
		}		
		
	

	},
		
	setItemHighlighted: function(inHighlight)
	{
		if(this.dragObjType == "song")
		{
			this.$.itemMedia.addRemoveClass("selected", inHighlight);
		}
		else //if album header
		{
			this.$.headerAlbum.addRemoveClass("selected", inHighlight);	
		}
	}
	
	

	
	
});
        