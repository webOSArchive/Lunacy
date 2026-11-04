/*globals enyo, $L, event, Utilities, window, setTimeout, clearTimeout, $L */
enyo.kind({
    name: "kindNavPanel",
    kind: "VFlexBox",
    events: {onClick_NowPlaying: "", onClick_ListView: "", onResetDetailView: "", onClick_ExhibitionMode: "", onRequestPlaylists_NavPanel: "", onClickPlaylist_NavPanel: "", onCreatePlaylist_NavPanel: "", onDeletePlaylist_NavPanel: ""},
    components: [
		{name: "navNowPlaying", className: 'playing enyo-toolbar enyo-toolbar-light', kind: 'Drawer', open: true, onOpenAnimationComplete: "onOpenAnimationComplete", components: [
			{kind: "Control", className: "content", layoutKind: "HFlexLayout", pack: 'start', align: "start", height: "48px"/*Take out this height, causes alignment to be mismatched after choosing song*/, onclick: "onclick_NowPlaying", components: [
				{kind: "Control", name: "ctrlDefaultThumb",showing: false, className: 'cover', layoutKind: "VFlexLayout", align: "center", pack: "center", components: [
					{className: "img", name: "imgContainer", layoutKind: "VFlexLayout", align: "center", pack: "center", components: [
						{name: "imgAlbumArt", kind: "Image"}
					]}
				]},
				{kind: 'Control', className: "info",  flex: 1, components: [
					//{name: "lblNowPlayingHeader" ,content: $L("Now Playing"), className: 'label'},
					{name: 'lblNowPlayingText', content: "", className: 'label'},
					{name: 'lblNowPlayingSubText', content: "", className: 'title'}
				]},
				{kind: "Control", className: "shadow"}
			]}
		]},
		{name: 'navLibrary', kind: 'DividerDrawer', className: "library", caption:$L("Library"), animate: false, onOpenChanged: "onOpenAnimationComplete", components: [
			
			{name: "libSongs", kind:'Item', layoutKind: "HFlexLayout", pack:"start", align:"center", navstring: "Songs", className: "songs active", onclick: "onClickLibrary", components:[
			//{name: "libSongs", kind:'Item', layoutKind: "HLayout", navstring: "Songs", className: "songs active", onclick: "onClickLibrary", components:[
				{className: "icon"},
				{content: $L("Songs"), className: "name", flex:1},
				//{content: $L("Songs"), className: "name"},
				//{kind: "Spacer",width:"180px"},
				{kind: "Spinner", showing: false, name:"songSpinner"}
			]},
			{name: "libArtists", kind:'Item', layoutKind: "HFlexLayout", pack:"start", align:"center", navstring: "Artists", className: "artists", onclick: "onClickLibrary", components: [
			//{name: "libArtists", kind:'Item', layoutKind: "HLayout", navstring: "Artists", className: "artists", onclick: "onClickLibrary", components: [
				{className: "icon"},
				{content: $L("Artists"), className: "name", flex:1}
				//{content: $L("Artists"), className: "name"}
			]},
			{name: "libAlbums", kind:'Item', layoutKind: "HFlexLayout", pack:"start", align:"center", navstring: "Albums", className: "albums", onclick: "onClickLibrary" , components: [
			//{name: "libAlbums", kind:'Item', layoutKind: "HLayout", navstring: "Albums", className: "albums", onclick: "onClickLibrary" , components: [
				{className: "icon"},
				{content: $L("Albums"), className: "name", flex:1}
				//{content: $L("Albums"), className: "name"}
			]},
			{name: "libGenres", kind:'Item', layoutKind: "HFlexLayout", pack:"start", align:"center", navstring: "Genres", className: "genres", onclick: "onClickLibrary" , components: [
			//{name: "libGenres", kind:'Item', layoutKind: "HLayout", navstring: "Genres", className: "genres", onclick: "onClickLibrary" , components: [
				{className: "icon"},
				{content: $L("Genres"), className: "name", flex:1}
				//{content: $L("Genres"), className: "name"}
			]}
		]},
		{name: "navPlaylistDrawer", kind: "DividerDrawer", caption:$L("Playlists"), flex:1, animate: false, onOpenChanged: "onOpenAnimationComplete", components: [
//		{ kind: 'Control', layoutKind: "VFlexLayout", className: 'group playlists', flex:1, components: [
			{name: "navPlaylists", kind: "navplaylists", flex:1, onRequestPlaylists: "doRequestPlaylists_NavPanel", onClickPlaylist: "onClickPlaylist", onCreatePlaylist: "doCreatePlaylist_NavPanel", onDeletePlaylist: "doDeletePlaylist_NavPanel"}
//		]}
		]},
		{kind: "Toolbar", pack: "center", className:"enyo-toolbar-light", components: [
			{name: 'btnNewPlaylist', kind: 'IconButton', label: $L("New Playlist"), className:"enyo-button-light", onclick: "onclick_btnNewPlaylist"}
		]},
       {name: "navExhibitionTest", kind: "HFlexBox", tapHighlight: false, height: "40px", className: "navMainOff", showing: false, components: [
            {content: $L("Exhibition"), className: "navMainLbl", onclick: "doClick_ExhibitionMode" }
        ]}
        
       
    ],
    
    
    statics: {
		_playBackMap:{
			"song": $L("Song"),
			"genre": $L("Genre"),
			"album": $L("Album"),
			"artist": $L("Artist"),
			"playlist": $L("Playlist")
		}
    },
    
    strNowPlayingType: "",
	strNowPlayingText: "",
	strNowPlayingArtist: "",
	arNowPlayingThumbs: [],
	
	
	strPlaylistKind: "",
	strPlaylistID: "",
    create: function()
    {
		this.inherited(arguments);
        
        this.objCurrNavItem = this.$.libSongs;
    },
	
	
	rendered: function ()
	{
		
		this.log();
		this.inherited(arguments);
		this.onOpenAnimationComplete();
		this.$.navPlaylists.renderList();
		

	},

	
	onclick_btnNewPlaylist: function (sender, event)
	{
		this.$.navPlaylists.ShowPopNewPlaylist();	
	},


   onClickLibrary: function (sender, event)
   {
		this.changeSelectedLibrary(sender);
		enyo.asyncMethod(this, "doClick_ListView", sender.navstring);
		
    },
    
   changeSelectedLibrary: function(objNavLibraryItem, reset){

	  this.log("objNavLibraryItem: ", objNavLibraryItem);
	  this.log("reset: ", reset);
	
	  if(reset === undefined){
		  reset = true;
	  }
		
		this.log(objNavLibraryItem.navstring);
	  
		if(this.objCurrNavItem === objNavLibraryItem)
		{
			if(reset)
			{
				enyo.asyncMethod(this, "doResetDetailView");
			}
			return;
		}
		
	  if(this.objCurrNavItem)
	  {
		  this.objCurrNavItem.removeClass("active");
	  }
	  
	  objNavLibraryItem.addClass("active");
	  
	  this.objCurrNavItem = objNavLibraryItem;
	  
	  this.$.navPlaylists.clearSelection();	
	  
			
	
		
   },
	 
	
	onClickPlaylist: function (sender, objClickPlaylist)
	{
		this.changeSelectedPlaylist(objClickPlaylist);
	},
	
	changeSelectedPlaylist: function(objClickPlaylist){
		if(this.objCurrNavItem)
		{
			this.objCurrNavItem.removeClass("active");
			enyo.asyncMethod(this, "doResetDetailView");
		}
		
		this.objCurrNavItem = undefined;
		
		if(objClickPlaylist.strPlaylistID !== undefined && objClickPlaylist.strPlaylistID !== "")
		{
			this.$.navPlaylists.setStrSelectedPlaylistID(objClickPlaylist.strPlaylistID);        
		}
		else if (objClickPlaylist.strPlaylistFile !== undefined && objClickPlaylist.strPlaylistFile !== "")
		{
			this.$.navPlaylists.setStrSelectedPlaylistFile(objClickPlaylist.strPlaylistFile);
		}
		
		enyo.asyncMethod(this, "doClickPlaylist_NavPanel", objClickPlaylist);
	},
	
	editPlaylist: function (strEditPlaylistID)
	{   
		this.log();	
		this.$.navPlaylists.setStrEditPlaylistID(strEditPlaylistID);
		
	},
	
    //already is open, commmenting out all uses
    /*showNowPlaying: function ()
    {
		this.$.navNowPlaying.setOpen(true);
    },*/
	
	
	setNowPlayingMode: function (objSetPlaybackList)
	{
		
		this.log();
		
		this.strNowPlayingType = objSetPlaybackList.strListType;
		this.strNowPlayingText = objSetPlaybackList.strListText;
		this.strNowPlayingArtist = objSetPlaybackList.strArtist;
		this.arNowPlayingThumbs = objSetPlaybackList.arThumbnails;
		
		
		if(objSetPlaybackList.strListType == "Playlist")
		{
			this.strPlaylistKind = objSetPlaybackList.strPlaylistKind;
			this.strPlaylistID = objSetPlaybackList.strPlaylistID;
		}
		else
		{
			this.strPlaylistKind = "";
			this.strPlaylistID = "";
		}
		// Use g11n template for "Now Playing: {Artist|Album|Genre|Playlist}"
		// WARNING! REMEMBER TO UPDATE _playBackMap ABOVE IF YOU ADD ANY NEW TYPES
		//var playbackType = enyo.JCtest.navPanel._playBackMap[objSetPlaybackList.strListType.toLowerCase()] || objSetPlaybackList.strListType;
		//var templ = new enyo.g11n.Template($L("Now Playing: #{playbackType}"));
		//var playMsg = templ.evaluate({playbackType: playbackType});
		//if(objSetPlaybackList.strListType !== "Song")
		//{
		//	this.$.lblNowPlayingHeader.setContent(playMsg);
		//}
		//else
		//{
		//	this.$.lblNowPlayingHeader.setContent($L("Now Playing"));			
		//}
		
		this.$.lblNowPlayingText.setContent(objSetPlaybackList.strListText);
		this.$.lblNowPlayingSubText.setContent(objSetPlaybackList.strListSubText);
		this.$.ctrlDefaultThumb.show();
		
		var strThumbURL = "";
		if(objSetPlaybackList.arThumbnails)
		{
			if(objSetPlaybackList.arThumbnails.length > 0)
			{
				if(objSetPlaybackList.arThumbnails[0])
				{
					if(objSetPlaybackList.arThumbnails[0].data)
					{
						strThumbURL = Utilities.getTrackImage(objSetPlaybackList.arThumbnails[0].data, 54);
					}
				}
			}
			
		}			
		if(strThumbURL !== "")
		{
			this.$.imgAlbumArt.setSrc(strThumbURL);
			this.$.imgContainer.show();
		}
		else
		{
			this.$.imgContainer.hide();
		}
		
	},
	
	
	updateNowPlaying: function (objTrackInfo)
	{
		
		if(this.strNowPlayingType === "Song")
		{
			this.$.lblNowPlayingText.setContent(objTrackInfo.strTrackTitle);
			this.$.lblNowPlayingSubText.setContent(objTrackInfo.strTrackArtist);
			

			this.$.ctrlDefaultThumb.show();
			if(objTrackInfo.strTrackImage !== "")
			{
				this.$.imgAlbumArt.setSrc( Utilities.getTrackImage(objTrackInfo.strTrackImage, 56));
				this.$.imgContainer.show();
			}
			else
			{
				this.$.imgContainer.hide();
			}	
				
				
		}
		
		
	},
	
	onclick_NowPlaying: function (sender, event)
	{
		var objParams = {name			: this.strNowPlayingText, 
						 artist			: this.strNowPlayingArtist,
						 thumbnails		: this.arNowPlayingThumbs,
						 showParentView	: true};
		
		switch(this.strNowPlayingType)
		{
			case "Song":
				this.changeSelectedLibrary(this.$.libSongs, false);
				enyo.asyncMethod(this, "doClick_ListView", "Songs", {forceJump: true});
				return;
			case "Artist":
				this.changeSelectedLibrary(this.$.libArtists, false);
				objParams.strListViewType="Artists";
				break;
			case "Album":
				this.changeSelectedLibrary(this.$.libAlbums, false);
				objParams.strListViewType="Albums";
				break;
			case "Genre":
				this.changeSelectedLibrary(this.$.libGenres, false);
				objParams.strListViewType="Genres";
				break;
			case "Playlist":
				var objPlaylist = {strPlaylistName	:  this.strNowPlayingText,
						strPlaylistID	: this.strPlaylistID,
						strPlaylistKind	: this.strPlaylistKind,
						boolForceRender	: true};
				this.changeSelectedPlaylist(objPlaylist, false);
				this.$.navPlaylists.refreshList();
				
				return;
			default:
				return;
		}
		
		//song and playlist returned earlier
		enyo.asyncMethod(this, "doClick_NowPlaying", objParams);
	},
	
	playlistDeleted: function(playlistObj)
	{
		if(this.strPlaylistID === playlistObj.strPlaylistID){
			this.strPlaylistID = undefined;
		}
	},
	
	onOpenAnimationComplete: function (sender, event)
	{
		this.resizeContent();
	
	},
	
	resizeContent: function ()
	{
		this.resized();
		
		this.log(this.$.navPlaylistDrawer.getBounds().height);
		
		var intContentHeight = this.$.navPlaylistDrawer.getBounds().height - this.$.navPlaylistDrawer.$.caption.getBounds().height;
		this.$.navPlaylists.resize(intContentHeight);	
	},
    
    refreshPlaylists: function ()
	{
		this.$.navPlaylists.renderList();
	},
    

    
	setIndexingStatus: function(boolIndexing)
	{
			this.$.songSpinner.setShowing(boolIndexing);
	}
	
});