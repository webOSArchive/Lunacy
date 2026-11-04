/*globals enyo, $L, event, Utilities, window, LibraryTools */
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewGenreDetail",
	kind: "listViewDetail",
	className: "playlist",
	components: [
		// the header 
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchSongs", boolShowSearchBtn: false},
		// the content
		{kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			// the divider
			{name: "header", kind: "Control", layoutKind: "HFlexLayout", className: "divider first", align:'center', components: [
				//{content: " ", className: "column number"},
				{name: "lblSortSong", content: $L("Song"), flex: 2, className: "column", onclick: "onclick_Sort"},
				{name: "lblSortArtist", content: $L("Artist"), flex: 1, className: "column sorted down", onclick: "onclick_Sort"},
				{name: "lblSortAlbum", content: $L("Album"), flex: 1, className: "column", onclick: "onclick_Sort"}
			]},
			// the list
			{name: "listMedia", kind: "VirtualList", onQuery: "listQuery", onSetupRow: "listSetupRow", className: "listMedia", flex: 1, components: [
				{name: "itemMedia", kind: "draggableSong", className:"song", layoutKind: "HFlexLayout", onclick: "onclick_listMedia", components: [
				    {kind: "Control", name: "songPlaying", className: "column song_genre_Blueicon", showing: true},
					//{name: "songNumber", className: "column number", content: $L("00")},
					{name: "songTitle", flex: 2, className: "column title"},
					{name: "songArtist", flex: 1, className: "column artist"},
					{name: "songAlbum", flex: 1, className: "column album"}
				]}
			]},
			// the toolbar
			{kind: "Toolbar", className: "enyo-toolbar-light", /*style: "background:#222", */ slidingHandler: true, components: [{kind: "GrabButton"}]}
			// the empty view, what? wait 
			/*{name: "txtResults", kind: "VFlexBox", align:"center", height:"100%", pack: "justify", className: "listEmpty", showing: false, components: [
				{kind: "Spacer", flex: 1},
				{kind: "Image", src: "images/bg_playlist_empty.png"},
				{content: $L("There are no songs in this playlist."), className: "heading"},
				{content: $L("Drag and drop songs onto this playlist's navigation item to add songs to it."), className: "subheading"},
				{kind: "Spacer", flex: 3}		
			]}, */
		]}	
    ],

	strSortMode: "artist",
	listViewDetailType: "genre",
	
	create: function () {
		//test
		
		try
		{
			this.inherited(arguments);
			this.strSortMode = "artist";
			this.listViewDetailType = "genre";
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
			this.log("intIndex: " + intIndex);
			if(this.arSongs !== undefined)
			{				
				if(this.arSongs[intIndex] !== undefined)
				{
					var objSong = this.arSongs[intIndex];
					
					this.log("title: " + objSong.title);
					//this.log(objSong.title);
					
					//this.$.songNumber.content = intIndex + 1;
					this.$.songTitle.content = objSong.title;
					this.$.songArtist.content = objSong.artist;
					this.$.songAlbum.content = objSong.album;
					
					
					
					this.$.itemMedia.setClassName("enyo-item song");
					this.setPlayingStyles(this.arSongs[intIndex]._id, this.$.itemMedia, this._strListQuery + this.strSortMode);

					//this.$.ThumbDisplay.destroyControls();
					
					var intGenreStartIndex = 0;
				
					if ((intIndex - intGenreStartIndex) % 2 !== 0 )
					{
						this.$.itemMedia.addClass("odd");
					}
					
					return true;
				}
			//return true;
			}

	
		}
		catch(err)
		{
			this.log("error: " + err);
		}		
		
	

	}
	
});
        