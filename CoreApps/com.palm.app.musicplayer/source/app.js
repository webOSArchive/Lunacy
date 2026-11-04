/*globals enyo, setTimeout, $L, event, Utilities, window, PalmSystem */
enyo.kind({
	name: "MusicPlayerApp",
	kind: "Control",
	//className: "landscape",
	width: "100%",
	components: [
		{ name: "appEvent", kind: "ApplicationEvents", onWindowActivated: "windowActivatedHandler", onWindowDeactivated: "windowDeactivatedHandler", onWindowHidden: "windowHiddenHandler", onWindowShown: "windowShownHandler", onWindowParamsChange: "windowParamsChangeHandler", onUnload: "unloadHandler", onApplicationRelaunch: "applicationRelaunchHandler"},
	
		{name: "psSetVolumeLock", kind: "PalmService", service: "palm://com.palm.audio/", method: "media/lockVolumeKeys", subscribe: true},
		{name: "psSetVolume", kind: "PalmService", service: "palm://com.palm.audio/", method: "media/setVolume"},
		{name: "psGetVolume", kind: "PalmService", service: "palm://com.palm.audio/", method: "media/getVolume", onSuccess: "onSuccess_RequestSysVolume", onFailure: "onFailure_RequestSysVolume"},
		{name: "psDisplay", kind: "PalmService", service: "palm://com.palm.display/control/", method: "status", onSuccess: "onSuccess_RequestDisplayStatus", onFailure: "onFailure_RequestDisplayStatus", subscribe: true},
		
		{name: "psMediaStatus", kind: "PalmService", service: "palm://com.palm.audio/", method: "media/status", onSuccess: "onSuccess_RequestMediaStatus", onFailure: "onFailure_RequestMediaStatus", subscribe: true},
		{name: "psAVRCPStatus", kind: "PalmService", service: "palm://com.palm.keys/", method: "media/status", onSuccess: "onSuccess_RequestAVRCPStatus", onFailure: "onFailure_RequestAVRCPStatus", subscribe: true},
		{name: "psHeadsetStatus", kind: "PalmService", service: "palm://com.palm.keys/headset/", method: "status", onSuccess: "onSuccess_RequestHeadsetStatus", onFailure: "onFailure_RequestHeadsetStatus", subscribe: true},
		{name: "psBroadcaster", kind: "PalmService", service: "palm://com.palm.service.mediabroadcast/", method: "registerBroadcaster", onSuccess: "onSuccess_SetBroadcaster", onFailure: "onFailure_ SetBroadcaster", subscribe: true},
		{name: "psUpdateBroadcaster", kind: "PalmService", service: "palm://com.palm.service.mediabroadcast/", method: "update", onSuccess: "onSuccess_SetBroadcaster", onFailure: "onFailure_ SetBroadcaster"},


		{name: "MediaIndex", kind: "kindMediaIndex", onSetPlaybackList: "onSetPlaybackList"},
		{name: "MediaWatcher", kind: "MediaWatcher", onSongChange: "onSongChange", onPlaylistChange: "refreshPlaylists", onIndexingStart: "onIndexingStart", onIndexingStop: "onIndexingStop"},
		{name: "PlaylistManager", kind: "kindPlaylistManager", onRequestMedia: "onRequestMedia_ListView", onRefreshPlaylists: "refreshPlaylists", onPlaylistDeleted: "onPlaylistDeleted"},
		{name: "Playback", kind: "kindPlayback", onPlaybackListIDChanged: "onPlaybackListIDChanged", onSongEnd: "onSongEnd", onTrackPlaying: "onTrackPlaying", onTrackEnded: "onTrackEnded", onTrackPausePlay: "onTrackPausePlay", onTrackSrcChanged:"onTrackSrcChanged", onUpdateTrackInfo: "onUpdateTrackInfo", onUpdateTrackTime: "onUpdateTrackTime", onPlaybackShuffleChanged: "onShuffleChanged_Playback", onPlaybackRepeatChanged: "onRepeatChanged_Playback", onPlaybackListSet: "onPlaybackListSet"},

		{name: "DashboardManager", kind: "kindDashboardManager"},
		{name: "paneAll", className: "wrapper", flex:1, height: "100%", width: "100%", kind: "Pane",components: [
			{kind: "VFlexBox", tapHighlight: true,  components: [
				{name: "paneTop", className: "paneTop", flex:1, width: "100%", kind: "Pane", transitionKind: "enyo.transitions.Simple", components: [
					{kind: "HFlexBox"/*, multiView: true, multiViewMinWidth: 320 //Commented out for DFISH-20546 fix */,  flex: 1, height: "100%", width: "100%", /*style: "border: solid 1px green;", */ components: [  //Changed from SlidingPane to HFlexBox out for DFISH-20546 fix
						//{width:"320px", fixedWidth: true, components: [	 //Commented out for DFISH-20546 fix
						{name: "navPanel", width:"320px", className: "navigation", kind: "kindNavPanel", height:"100%", onClick_ListView: "onClick_ListView",  onResetDetailView: "onResetDetailView", onClick_ExhibitionMode: "onClick_ExhibitionMode", onRequestPlaylists_NavPanel: "onRequestPlaylists", onClickPlaylist_NavPanel: "onClickPlaylist_NavPanel", onCreatePlaylist_NavPanel: "onCreatePlaylist_NavPanel", onDeletePlaylist_NavPanel: "onDeletePlaylist", onClick_NowPlaying: "onClick_NowPlaying"},
						//]},	 //Commented out for DFISH-20546 fix
						//{flex:1, peekWidth: 320, multiView: true, fixedWidth: true, components: [	 //Commented out for DFISH-20546 fix
						{name: "paneMainView", className: "view", flex: 1, kind: "Pane", onSelectView: "onSelectView_PaneMainView", transitionKind: "enyo.transitions.Simple", components: [
							{name: "viewListViewSongs", flex: 1, components: [
									{name: "listViewSongs", kind: "kindListViewSongs", flex: 1, onSetPlaybackList: "onSetPlaybackList", onRequestMedia: "onRequestMedia_ListView", onListChanged:"onUIListChanged", onListSorted: "onUIListSorted", onEnableControls:"onControlsEnabled",onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon",onRequestCurrTrackInfo: "onRequestCurrTrackInfo"}								 

							]},
							{name: "viewListViewArtists", kind: "SlidingPane", multiView: false, multiViewMinWidth: 1100, flex: 1, components: [
								{name: "sldListViewArtists", flex: 1, components: [
									{name: "listViewArtists", kind: "kindListViewArtists", onSetPlaybackList: "onSetPlaybackList", onRequestMedia: "onRequestMedia_ListView", onSelectArtist: "onSelectArtistDetail", onRequestPlaylists: "onRequestPlaylists", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}
								]},
								{name: "sldListViewArtistDetail", edgeDragging: false, dragAnywhere: false, components: [
									{name: "listViewArtistDetail", kind: "kindListViewArtistDetail", flex: 1, showing: true, onRequestMedia: "onRequestMedia_ListView", onRequestCurrTrack: "onRequestCurrTrackInfo", onSetPlaybackList: "onSetPlaybackList", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}
								]}

							]},
							{name: "viewListViewAlbums", kind: "SlidingPane", multiView: false, multiViewMinWidth: 1100, components: [
								{name: "sldListViewAlbums", components: [
									{name: "listViewAlbums", kind: "kindListViewAlbums", onSetPlaybackList: "onSetPlaybackList", onRequestMedia: "onRequestMedia_ListView", onSelectAlbum: "onSelectAlbumDetail", onRequestPlaylists: "onRequestPlaylists", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}
								]},
								{name: "sldListViewAlbumDetail", edgeDragging: false, dragAnywhere: false, components: [
									{name: "listViewAlbumDetail", kind: "kindListViewAlbumDetail", flex: 1, showing: true, onRequestMedia: "onRequestMedia_ListView", onListChanged: "onUIListChanged", onRequestCurrTrack: "onRequestCurrTrackInfo", onSetPlaybackList: "onSetPlaybackList", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}
								]}
								
							]},
							{name: "viewListViewGenres", kind: "SlidingPane", multiView: false, multiViewMinWidth: 1100, components: [
								{name: "sldListViewGenres", components: [
									{name: "listViewGenres", kind: "kindListViewGenres", onSetPlaybackList: "onSetPlaybackList", onRequestMedia: "onRequestMedia_ListView", onSelectGenre: "onSelectGenreDetail", onRequestPlaylists: "onRequestPlaylists", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}
								]},
								{name: "sldListViewGenreDetail", edgeDragging: false, dragAnywhere: false, components: [
									{name: "listViewGenreDetail", kind: "kindListViewGenreDetail", flex: 1, showing: true, onRequestMedia: "onRequestMedia_ListView", onRequestCurrTrack: "onRequestCurrTrackInfo", onSetPlaybackList: "onSetPlaybackList", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}
								]}
							]},
							{name: "viewListViewPlaylist", flex: 1, components: [
								{name: "listViewPlaylist", kind: "kindListViewPlaylist", flex: 1, onSetPlaybackList: "onSetPlaybackList", onRequestMedia: "onRequestMedia_ListView", onRequestPlaylist: "onRequestPlaylists", onUpdatePlaylist: "onUpdatePlaylist", onDeletePlaylist: "onDeletePlaylist", onListChanged:"onUIListChanged", onListSorted: "onUIListSorted", onEditPlaylist: "onEditPlaylist", onRequestCurrTrack: "onRequestCurrTrackInfo", onAddToPlaylist: "onAddToPlaylist", onItemDrag: "onItemDrag", onItemShowAvatar: "onItemShowAvatar", onItemShowAvatarIcon: "onItemShowAvatarIcon"}								 
							]}
						]}
						//]} //Commented out for DFISH-20546 fix
						
						
					]},
					{name:"AlbumArtView", kind: "KindAlbumArtView", onRequestTracks: "onRequestTracks_AlbumArtView", onClickAlbumArtView: "onClickAlbumArtViewTrack"}
				]},
				{name: "PlayerControl", kind: 'kindPlayerControl', onClickNext: "onClickNext", onClickPrev: "onClickPrev" , onClickPlayPause: "onClickPlayPause", onSetPlaybackTime:"onSetPlaybackTime", onShuffleClick: "onShuffleClick_PlayModeControls", onRepeatClick: "onRepeatClick_PlayModeControls", onSetVolume: "onSetPlaybackVolume" , onRequestVolume: "onRequestSysVolume", onClickFullScreen: "onClick_FullScreen"},
				{name: "btnBack", content: $L("Back"), onclick: "onClick_btnBackAlbumArtView", style:"position: fixed; right: 20px; top: 20px;", showing: false}// this is not in designs, in process of finding out if there should be one at all
			]}
		]},
		{name: "avatartop", kind: "Control", showing: false, className: "drag", components: [
			{className: "cover", components: [
				{name: "imgContainer", className: "img", components: [
					{name: "imgAvatar", kind: "Image", onerror: "onError_imgAvatar"}
				]}
			]},
			{name: "imgAvatarIcon", className: "action", components: [ // not sure what 'on' is for, but i kept it.
				{kind: "Control", className: "add"} // set this class to "order" when re-ordering is implemented so that the icon will change.
			]}
		]},
		{kind: "AppMenu", components: [
			{kind: "HelpMenu", target: "http://help.palm.com/music/index.json"}
		]}
		
	],
		
	boolWindowActive: false,
	
	boolLandscape: true,

	boolAlbumArtViewDisplay: false,
	
	boolExhibitionViewDisplay: false,
	
	boolPlaybackListSet: false,
	boolCanPlay: false,
	boolPlayStarted: false,
	
	ListView: null,
	cacheTrackInfo: null,
	
	boolDefaultLoad: true,
	boolAppLoaded: false,

	showParams: {},


	
	
	handleLaunch: function (launchParams)
	{
		
		this.log("launchParams: ", launchParams);
	
	},
	
	
	create: function (launchParams)
	{
		this.inherited(arguments);
		
		this.log();	
		try
		{
			
			if(window.PalmSystem)
			{
				this.log("Setting keepAlive");
				PalmSystem.keepAlive(true);
				this.log("keepAlive set");
			}
			
			this.log("launchParams: ", launchParams);
			this.log("enyo.windowParamas: ", enyo.windowParams);
			
			this.RequestHeadsetStatus();
			this.RequestAVRCPStatus();
			
			this.$.psBroadcaster.call();
			
			enyo.keyboard.setResizesWindow(false);
			
		}
		catch(err)
		{
			this.log(err);
		}
	
	},


	ready: function ()
	{
		
	},
	
	
	rendered: function()
	{
		this.inherited(arguments);
		
		this.log();
		this.log("enyo.windowParamas: ", enyo.windowParams);
		
		if(enyo.windowParams.action)
		{
			this.boolDefaultLoad = !(enyo.windowParams.action === "show" && enyo.windowParams.showparams);
		}
		
		this.log("this.boolDefaultLoad: ", this.boolDefaultLoad);
		if(this.boolDefaultLoad)
		{
			this.selectListView("Songs");
			setTimeout(enyo.bind(this, "refreshListViews", ["listViewArtists", "listViewAlbums", "listViewGenres"]), 1000);
		}
		else
		{	
			this.showContentItem(enyo.windowParams.showparams);			
		}
	
		this.RequestDisplayStatus();
		
		this.$.MediaWatcher.startWatch();
		
		
			
	},
	
	/**
	* @breif refreshes all listviews passed in (rerenders if needs to)
	* @param arrListViews array containing names of all listviews to refresh 
	*	(e.g. "listViewArtists", "listViewAlbums", "listViewGenres", "listViewSongs")
	*/
	refreshListViews: function(arrListViews){
		for(var i in arrListViews)
		{
			if(arrListViews[i])
			{
				this.$[arrListViews[i]].refreshList();
			}
		}
	},
	
	/**
	* @breif rerenders all listviews passed in
	* @param arrListViews array containing names of all listviews to refresh
	*	(e.g. "listViewArtists", "listViewAlbums", "listViewGenres", "listViewSongs")
	*/
	renderListViews: function(arrListViews){
		for(var i in arrListViews)
		{
			if(arrListViews[i])
			{
				this.$[arrListViews[i]].renderList();
			}
		}
	},
	
	applicationLaunchHandler: function (launchParams)
	{
		this.log();
		this.log("launchParams: ", launchParams);
		
		this.log("enyo.windowParams: ", enyo.windowParams);
		
	},
		
	
	applicationRelaunchHandler: function (sender, launchParams)
	{
		this.log();
		this.log("launchParams: ", launchParams);
		
		this.log("enyo.windowParams: ", enyo.windowParams);
				
	},
	
	
	windowHiddenHandler: function()
	{
		this.log();
		this.playPause(false);
		this.$.Playback.setBoolSuspendUpdates(true);
		this.$.Playback.setBoolSuspendPlayback(true);

		this.$.DashboardManager.closeControlDashboard();
	},
	
	
	windowShownHandler: function()
	{
		this.log();
		//this.playPause(true);		
		this.$.Playback.setBoolSuspendUpdates(false);
		this.$.Playback.setBoolSuspendPlayback(false);
	},
	
	
	windowActivatedHandler: function (sender, launchParams)
	{
		
		this.boolWindowActive = true;
		//Called Without this
		//this.resizeHandler();
		
		this.setVolumeLock(true);

		
		this.$.DashboardManager.closeControlDashboard();
		this.$.Playback.setIntUpdateRate(333);
		
	},
	
	
	windowDeactivatedHandler: function ()
	{
		this.log();
		this.boolWindowActive = false;
		this.$.DashboardManager.openControlDashboard();
		this.$.Playback.setIntUpdateRate(3000);
		this.setVolumeLock(false);
		
	},

	
	unloadHandler: function ()
	{
	
		this.log();
		this.$.DashboardManager.closeControlDashboard();
		this.setVolumeLock(false);
		
		try{
		
		var mainWin = enyo.windows.fetchWindow("com.palm.app.musicplayer");
		if(mainWin)
		{
			this.log("closing main win");
			mainWin.close();
			mainWin = null;
		}
		}
		catch (err)
		{
			this.log("err:", err);
		}
	},

	
	windowRotatedHandler: function (sender, orientation)
	{
		this.log();

	},
	
	
	windowParamsChangeHandler: function()
	{
		this.log();
		
		this.log("enyo.windowParams: ", enyo.windowParams);
		
		if(enyo.windowParams.gotoNowPlaying)
		{
			this.$.navPanel.onclick_NowPlaying();
		}
		if(enyo.windowParams.cmdType)
		{
			this.processDashCommand(enyo.windowParams.cmdType);
		}
		
		if(enyo.windowParams.action)
		{
			if(enyo.windowParams.action == "show")
			{
				this.showContentItem(enyo.windowParams.showparams);			
			}
		}

	},
	

	
	showContentItem: function (showParams)
	{
		
		this.log();
		this.log("showParams: ", showParams);
		
		if(showParams.showtype)
		{
			switch (showParams.showtype)
			{
				
				case "song":
					this.log("song");
					if((showParams.songtitle && showParams.artistname ) || showParams.songfile)
					{
						this.log("valid params");
						this.$.navPanel.changeSelectedLibrary(this.$.navPanel.$.libSongs, false);
						if(showParams.songtitle && showParams.artistname )
						{
							this.onClick_ListView({}, "Songs", {strSongTitle: showParams.songtitle, strSongArtist: showParams.artistname, boolForceRender: true});
						}
						else if(showParams.songfile)
						{
							this.onClick_ListView({}, "Songs", {strSongFile: showParams.songfile, boolForceRender: true});
						}
						
						setTimeout(enyo.bind(this, "refreshListViews", ["listViewArtists", "listViewAlbums", "listViewGenres"]), 1000);
					}
					else
					{
						this.log("invalid params");
					}
					break;
				
				case "artist":
					this.log("artist");
					if(showParams.artistname)
					{
						this.log("valid params");
						this.$.navPanel.changeSelectedLibrary(this.$.navPanel.$.libArtists, false);
						this.onSelectArtistDetail({}, {strListViewType: "Artists", name: showParams.artistname, showParentView: true, boolForceRender: true});
						setTimeout(enyo.bind(this, "refreshListViews", ["listViewSongs", "listViewAlbums", "listViewGenres"]), 1000);
						
					}
					else
					{
						this.log("invalid params");
					}
					break;
				
				case "album":
					this.log("album");
					if(showParams.albumname && (showParams.artistname || showParams.albumartist))
					{
						this.log("valid params");
						this.$.navPanel.changeSelectedLibrary(this.$.navPanel.$.libAlbums, false);
						this.onSelectAlbumDetail({}, {strListViewType: "Albums", name: showParams.albumname, artist: showParams.albumartist || showParams.artistname, showParentView: true, boolForceRender: true});
						setTimeout(enyo.bind(this, "refreshListViews", ["listViewSongs", "listViewArtists", "listViewGenres"]), 1000);
					}
					else
					{
						this.log("invalid params");
					}
					break;
				
				case "genre":
					this.log("genre");
					if(showParams.genrename)
					{
						this.$.navPanel.changeSelectedLibrary(this.$.navPanel.$.libGenres, false);
						this.onSelectGenreDetail({}, {strListViewType: "Genres", name: showParams.genrename, showParentView: true, boolForceRender: true});
						
						setTimeout(enyo.bind(this, "refreshListViews", ["listViewSongs", "listViewArtists", "listViewAlbums"]), 1000);
						this.log("valid params");
					}
					else
					{
						this.log("invalid params");
					}
					break;
				
				case "playlist":
					this.log("playlist");
					if(showParams.playlistid  || showParams.playlistfile )
					{
						this.log("valid params");
						var objPlaylist = {};
						
						if(showParams.playlistid !== undefined && showParams.playlistid !== "")
						{
							objPlaylist = {strPlaylistID : showParams.playlistid, strPlaylistKind	: "com.palm.music.staticplaylists:1", boolForceRender	: true};		
						}
						else if(showParams.playlistfile !== undefined && showParams.playlistfile !== "")
						{
							objPlaylist = {strPlaylistFile : showParams.playlistfile, strPlaylistKind	: "com.palm.media.playlist.file:1", boolForceRender	: true};		
						}						
						
						this.log("calling selectListView with : " , objPlaylist);
						
						this.$.navPanel.changeSelectedPlaylist(objPlaylist);
						
						//this.selectListView("Playlist", objPlaylist);
			
						setTimeout(enyo.bind(this, "refreshListViews", ["listViewSongs", "listViewArtists", "listViewAlbums", "listViewGenres"]), 1000);

					}
					else
					{
						this.log("invalid params");
					}
					break;
				
				
			}
		}
	},
	
	
	openAppMenuHandler: function()
	{
		this.log();
		this.$.appMenu.open();
	},


	closeAppMenuHandler: function()
	{
		this.log();
		this.$.appMenu.close();
	},
	
	
	processDashCommand: function (cmdType)
	{
		
		this.log(cmdType);
		
		switch (cmdType)
		{
			
			case "prev":
				this.onClickPrev();
				break;
			
			case "playpause":
				this.onClickPlayPause();
				break;
			
			case "next":
				this.onClickNext();
				break;
			
		}
		
	},
	
	
	resizeHandler: function()
	{
		this.log("window.innerWidth: ", window.innerWidth);
		this.log("window.innerHeight: ", window.innerHeight);
		
		this.boolLandscape = window.innerWidth > window.innerHeight;
		
		this.setClassName(( this.boolLandscape ? "landscape enyo-fit enyo-vflexbox" : "portrait enyo-fit enyo-vflexbox"));
		
		//this.render();
		this.$.AlbumArtView.orientationChanged(this.boolLandscape);
		
		this.$.navPanel.resizeContent();
		if(this.ListView)
		{
			if(this.ListView.resizeList)
			{
				this.ListView.resizeList();
				//this.$.viewListVieArtists.resized();

			}
		}
		
		this.$.listViewGenres.setIntColumns(( this.boolLandscape ? 4: 3));
		//force redraw if not sliding
		
		
		if(this.$.paneMainView.getView())
		{
			this.$.paneMainView.getView().resized();
		}
		
		this.$.PlayerControl.resized();
		
	},
	
	
	RequestDisplayStatus: function ()
	{
		this.log();
		this.$.psDisplay.call();
		
	},
	
	
	onSuccess_RequestDisplayStatus: function (sender, response)
	{
		this.log();
		this.log(response);
		
		switch (response.event)
		{
			
			case "displayOff":
				this.$.Playback.setBoolSuspendUpdates(true);
				break;
			
			case "displayOn":
				this.$.Playback.setBoolSuspendUpdates(false);
				break;
			
			
		}
		
	},

	onFailure_RequestDisplayStatus: function (sender, response)
	{
		this.log();
	},
	
	
	//Playback Events
	
	onSongEnd: function(){
		if(!this.$.PlayerControl.boolDraggingSlider){
			this.$.Playback.nextTrack();
		}
		if(this.ListView.onTrackEnd){
			this.ListView.onTrackEnd();
		}
	},
	
	onTrackPlaying: function ()
	{
		this.log();
		//this.$.navPanel.showNowPlaying();
		if(this.ListView.onTrackPlaying){
			this.ListView.onTrackPlaying();
		}
		
		this.boolPlayStarted = true;
		//this.onControlsEnabled(null,true, true);
		//this.$.PlayerControl.startTrackTimeUpdate();
	},
	
	onTrackEnded: function (changeTrack)
	{
		this.log();
		
	},
	
	onTrackSrcChanged: function(sender, objTrackInfo)
	{
		this.log();
		this.cacheTrackInfo = objTrackInfo;
		this.sendTrackInfo(true);

	},
	
	
	sendTrackInfo: function (boolShowBanner, objParams) // Sends track info to various objects that need it. If boolShowBanner is true, a track change banner notification is sent
	{
		this.log();
		if(this.cacheTrackInfo)
		{
		this.$.PlayerControl.updateTrackInfoDisplay(this.cacheTrackInfo);
		this.log("this.boolWindowActive: ", this.boolWindowActive);
		
		this.$.DashboardManager.updateControlDashboardInfo(this.cacheTrackInfo, this.boolWindowActive, boolShowBanner);		
	
		//if(this.ListView.boolViewActive && this.ListView.highlightTrack != undefined)
		if(this.ListView.highlightTrack !== undefined) 
		{
			var jump = false;
			if(objParams)
			{
				jump = objParams.forceJump;
			}
			
			this.ListView.highlightTrack(this.cacheTrackInfo, jump);
		}
		
		if(this.boolAlbumArtViewDisplay)
		{
			this.$.AlbumArtView.changeTrack(this.cacheTrackInfo);
		}
		else
		{
			this.$.navPanel.updateNowPlaying(this.cacheTrackInfo);			
		}
		
		this.updateBroadcaster({type: "trackChanged", track: this.cacheTrackInfo});
		}
	},
	
	
	onRequestCurrTrackInfo: function (sender, objParams)		//sends track info w/ no banner notification. 
	{	
			this.sendTrackInfo(false, objParams);
	},

	
	onRequestCurrTrack_ListView: function (sender)
	{
		this.log();
		this.sendTrackInfo(true);
	},
	
	
	onTrackPausePlay: function (sender, boolAudioPlaying)
	{
		this.log();
		this.log("boolAudioPlaying",boolAudioPlaying);
		
		this.$.PlayerControl.setPlayPause(boolAudioPlaying);
		
		this.$.DashboardManager.setPlayPause(boolAudioPlaying);
		
		if(this.boolAlbumArtViewDisplay)
		{		
			this.$.AlbumArtView.setPlayPause(boolAudioPlaying);
		}
		
		this.updateBroadcaster({type: "playChanged", boolPlaying: boolAudioPlaying});
		
		
	},	
	
	onUpdateTrackInfo: function ()
	{
		
	},
	
	onUpdateTrackTime: function (sender, objTrackTimes)
	{
		//this.log("objTrackTimes: " , objTrackTimes);
		this.$.PlayerControl.updateTrackTimeDisplay(objTrackTimes);
	},
	
	
	//PlayerControl Events
	
	resetScrollWatch: function(){
		if(this.ListView._boolWaitToJump){
			this.ListView._boolWaitToJump = false;
		}
	},
	
	onClickNext: function ()
	{
		this.resetScrollWatch();
		this.$.Playback.nextTrack(true);
	},
	
	onClickPrev: function ()
	{
		this.resetScrollWatch();
		this.$.Playback.prevTrack(true);
	},
	
	onClickPlayPause: function ()
	{
		
		this.playPause();

		
	},
	
	playPause: function (boolForcePlayPause)
	{
		this.log();
		if(this.$.Playback.getBoolPlaybackListSet())
		{
			this.$.Playback.pausePlayback(boolForcePlayPause);
		}	
	},
	
	
	onControlsEnabled: function (sender, boolCanPlay, boolCanSkip)
	{
		this.log("boolCanPlay: ", boolCanPlay);
		this.log("boolCanSkip: ", boolCanSkip);
	
		if(boolCanPlay !== undefined)
		{
			this.$.PlayerControl.setPlayEnabled(boolCanPlay);			
		}
			
		this.log(boolCanSkip);
		if(boolCanSkip !== undefined)
		{
			this.$.PlayerControl.setPrevNextEnabled(boolCanSkip);
		}	
		
	},
	
	
	onSetPlaybackList: function (sender, objSetPlaybackList)
	{
		try
		{
			this.$.Playback.setPlaybackList(objSetPlaybackList);
			
			this.$.navPanel.setNowPlayingMode(objSetPlaybackList);

			
			
		}
		catch(err)
		{
			this.log("error: ", err);
		}
	},
	
	onPlaybackListIDChanged: function(){
		//Sets playlist plaingID, so we know what display scene to use
		this.$.listViewPlaylist.setStrPlaybackListID(this.$.Playback.getPlaybackListID());
	},
	
	onPlaybackListSet: function (sender, boolPlaybackListSet, intTrackCount, strShuffle, strRepeat)
	{
		this.log(boolPlaybackListSet);
		//this.$.navPanel.showNowPlaying();
		this.onControlsEnabled(null, boolPlaybackListSet, boolPlaybackListSet);
		this.updateBroadcaster({type: "playlistStart", intTrackCount: intTrackCount, strShuffle: strShuffle, strRepeat: strRepeat});
		this.boolPlaybackListSet = boolPlaybackListSet;
	},
	
	
	onShuffleClick_PlayModeControls: function(sender)
	{
		
		this.log("****");
		this.$.Playback.shufflePlaylist();
		
	},
	
	
	onShuffleChanged_Playback: function (sender, boolShuffleOn, intCurrTrack)
	{
		this.log(sender);
		this.log(boolShuffleOn);
		this.$.PlayerControl.setShuffleButton(boolShuffleOn);
		if(this.boolAlbumArtViewDisplay)
		{
			this.$.AlbumArtView.requestTrackList();
		}
		this.updateBroadcaster({type: "shuffleChanged", boolShuffle: boolShuffleOn, intCurrTrack: intCurrTrack});
	},
	
	
	onRepeatClick_PlayModeControls: function(sender)
	{
		this.log("****");
		this.$.Playback.setRepeatMode();
		
	},
	
	
	onRepeatChanged_Playback: function (sender, strRepeatMode, intRepeatMode)
	{
		this.log(sender);
		this.log(strRepeatMode);
		this.$.PlayerControl.setRepeatButton(strRepeatMode);
		this.updateBroadcaster({type: "repeatChanged", intRepeatMode: intRepeatMode});
	
	},
		
		
	//Music library methods and handlers
	onRequestMedia_ListView: function (sender, objGetSongRequest) // A music lib view has made a request for a list of data (songs, artists, etc.)
	{
		this.log();
		this.$.MediaIndex.requestMedia(objGetSongRequest);
	},
	
	onRequestPlaylists: function (sender, objGetPlaylistsRequest)
	{
		this.log();
		this.$.PlaylistManager.requestPlaylists(objGetPlaylistsRequest);
		
	},
	
	onCreatePlaylist_NavPanel: function (sender, objPutPlaylistsRequest)
	{
		this.log(objPutPlaylistsRequest.strPlaylistName);
		this.$.PlaylistManager.insertStaticPlaylist(objPutPlaylistsRequest);
	},

	onEditPlaylist: function (sender, strEditPlaylistID)
	{
		this.$.navPanel.editPlaylist(strEditPlaylistID);
	},
	
	onAddToPlaylist: function (sender, objAddToPlaylist)
	{
		this.log();
		this.$.PlaylistManager.addToPlaylist(objAddToPlaylist);
	},
	
	onUpdatePlaylist: function (sender, objUpdatePlaylist)
	{
		
		this.log();
		this.$.PlaylistManager.updateStaticPlaylist(objUpdatePlaylist);
		
	},
	
	onDeletePlaylist: function (sender, objDeletePlaylist)
	{
		this.log();
		this.$.PlaylistManager.deletePlaylist(objDeletePlaylist);
		//to let the navpanel know to not show the playlist if it's deleted
		this.$.navPanel.playlistDeleted(objDeletePlaylist);
	},
	
	onPlaylistDeleted: function ()
	{
		this.$.listViewPlaylist.playlistDeleted();
	},
	
	refreshPlaylists: function ()
	{
		this.$.navPanel.refreshPlaylists();
	},
	
	onUIListChanged: function (sender, arChangedUIList, intCurrTrackOrigIndex)
	{
		this.$.Playback.songListChanged(/*arChangedUIList, */intCurrTrackOrigIndex);
		
	},
	
	
	onUIListSorted: function(sender, strSortMode, boolSortAsc)
	{
		this.$.Playback.sortSongList(strSortMode, boolSortAsc);
	},

	
	onSongChange: function ()
	{
		this.log();
		this.refreshCurrentListView();
		var listViews = ["listViewSongs", "listViewArtists", "listViewAlbums", "listViewGenres", "listViewSongs"];
		var toRender = [];
		for(var i in listViews)
		{
			if(this.$[listViews[i]] !== this.listVeiw)
			{
				toRender.push(listViews[i]);
			}
		}
		
		setTimeout(enyo.bind(this, "renderListViews", toRender), 1000);
		
		
	},

	
	onPlaylistChange: function ()
	{
		this.log();
		this.refreshPlaylists();
	},
	
	
	onIndexingStart: function ()
	{
		this.log();
		this.$.navPanel.setIndexingStatus(true);

		if(this.ListView)
		{
			if(this.ListView.resizeList)
			{
				this.log("forcing resize of current list");
				this.ListView.resizeList();
			}
		}
	},


	onIndexingStop: function ()
	{
		this.log();
		this.$.navPanel.setIndexingStatus(false);
		
		if(this.ListView)
		{
			if(this.ListView.resizeList)
			{
				this.log("forcing resize of current list");
				this.ListView.resizeList();
			}
		}
	},
	
	
	//Playback related methods
	
	onSetPlaybackTime: function(sender, intPos)
	{
		this.log();
		this.$.Playback.setTrackTime(intPos);
		
		
	},
	
	
	///*****************************************
	// Volume control methods and service calls
	///*****************************************
	
	
	setVolumeLock: function (boolVolumeLock)
	{
		this.log(boolVolumeLock);
		try
		{
			
		if(boolVolumeLock)
		{
			this.$.psSetVolumeLock.call({});
		}
		else
		{
			this.$.psSetVolumeLock.cancel({});
		}
		}
		catch (err)
		{
			this.log("error: " + err);
		}
	},
	
	onSetPlaybackVolume: function(sender, intPos)
	{
		this.log();

		
		if(window.PalmSystem)
		{
			this.log("calling psSetVolume");
			this.$.psSetVolume.call({"volume":intPos});
		}
		else
		{
			this.$.Playback.setVolume(intPos);
		}
		
		
		
		
	},
	
	onRequestSysVolume: function (sender, callback)
	{
		
		this.RequestSysVolume(sender, callback);
		this.RequestMediaStatus(sender, callback);
	
	},
	
	
	RequestSysVolume: function (sender, callback) // Requests system volume. Used to initially set the volume slider to the current system media volume
	{
		this.log();
		
		//return true;
		
		this.cbRequestVol = callback;		
		
		if(window.PalmSystem)
		{
			var req = this.$.psGetVolume.call({});
			req.callback = callback;
		}
		else
		{
			this.cbRequestVol(this.$.Playback.getVolume()); // FIX ME
		}
		
	},
	
	onSuccess_RequestSysVolume: function (sender, response, request)
	{
		this.log();
		this.log(response);
		//this.cbRequestVol(response.volume);
		request.callback(response.volume);
	},
	
	onFailure_RequestSysVolume: function (sender, response)
	{
		this.log(response);
		

	},
	
	
	RequestMediaStatus: function (sender, callback) // Subscribes to media status service. Currently used for setting volume slider when system media volume is changed with the HW volume rocker.
	{
		this.log();
		//return true;
		
		this.cbMediaStatus = callback;		
		
		if(window.PalmSystem)
		{
			var req = this.$.psMediaStatus.call({});
			req.callback = callback;
		}
		
	},
	
	onSuccess_RequestMediaStatus: function (sender, response, request)
	{
		this.log();
		
		this.log(response.action);
		
		//changed for changing volume
		//enabled for plugging in headphones
		if(response.action === "changed" || response.action === "enabled")
		{
			//this.cbMediaStatus(response.volume);
			request.callback(response.volume);
		}
		

	},
	
	onFailure_RequestMediaStatus: function (sender, response)
	{
		this.log(response);
		

	},
	
	
	
	RequestHeadsetStatus: function () // Subscribes to headset status service. Used for AVRCP and headset button controls.
	{
		this.log();
		
		if(window.PalmSystem)
		{
			this.$.psHeadsetStatus.call({});
		}
		
	},
	
	onSuccess_RequestHeadsetStatus: function (sender, response)
	{
		this.log();
		this.log(response);
		//this.log(response.action);
		
		if(response.key === "headset_button" && response.state)
		{
			
			switch (response.state)
			{
				case "single_click":
					this.playPause();
					break;
				
				case "double_click":
					this.onClickNext();
					break;
			}
			
		}
		

	},
	
	onFailure_RequestHeadsetStatus: function (sender, response)
	{
		this.log(response);
		

	},
	
	
	
		
	RequestAVRCPStatus: function () // Subscribes to headset status service. Used for AVRCP and headset button controls.
	{
		this.log();
		
		if(window.PalmSystem)
		{
			this.$.psAVRCPStatus.call({});
		}
		
	},
	
	onSuccess_RequestAVRCPStatus: function (sender, response)
	{
		this.log();
		this.log(response);
		
		if(response.state === "down")
		{
				
			switch (response.key)
			{
				case "next":
					this.onClickNext();
					break;
					
				case "prev":
					this.onClickPrev();
					break;
					
				case "pause":
					this.playPause(false);
					break;
					
				case "stop":
					this.log("stop not supported");
					this.playPause(false);
					break;
					
				case "play":
					this.playPause(true);
					break;
	
				case "nextAndPlay":
					this.log("nextAndPlays not supported");
					
					break;			
					
				case "togglePausePlay":
					this.log("togglePausePlay");
					this.playPause();
					break;
					
				case "repeat-all":
					this.$.Playback.setRepeatMode(1);
					break;
				case "repeat-track":
					this.$.Playback.setRepeatMode(2);
					break;
				case "repeat-none":
					this.$.Playback.setRepeatMode(0);
					break;
				case "shuffle-on":
					this.$.Playback.shufflePlaylist(true);

					break;
				case "shuffle-off":
					this.$.Playback.shufflePlaylist(false);

					break;
					
				default:
					this.log("Unknown AVRCP event: " + response.key);
					break;
					
			}
		
		}	
		
		
		
		

	},
	
	onFailure_RequestAVRCPStatus: function (sender, response)
	{
		this.log(response);
		

	},
	
	
	SONG_CHANGED: "SONG_CHANGED",
	DURATION_CHANGED: "DURATION_CHANGED",
	PLAYLIST_ENDED: "PLAYLIST_ENDED",
	PLAYLIST_STARTED: "PLAYLIST_STARTED",
	SHUFFLE_MODE_CHANGED: "SHUFFLE_MODE_CHANGED",
	REPEAT_MODE_CHANGED: "REPEAT_MODE_CHANGED",
	PAUSE_MODE_CHANGED: "PAUSE_MODE_CHANGED",
	SHUFFLE_MODE_OFF: "OFF",
	SHUFFLE_MODE_ON: "ON",
	REPEAT_MODE_TRACK: "TRACK",
	REPEAT_MODE_ALL: "ALL",
	REPEAT_MODE_OFF: "OFF",
	PAUSE_MODE_OFF: "OFF",
	PAUSE_MODE_ON: "ON",
	AR_REPEAT_MODES: ["OFF", "ALL","TRACK"],
	
	
	updateBroadcaster: function (objBroadCastParams)
	{
		
		this.log();
		this.log(objBroadCastParams);
		var params = {};
		
		switch (objBroadCastParams.type)
		{
			
			case "playChanged":
				
				//type: "playChanged", boolPlaying: boolAudioPlaying
				params = {event: this.PAUSE_MODE_CHANGED, song: {}};
				params.song.pause = (!objBroadCastParams.boolPlaying)? this.PAUSE_MODE_ON: this.PAUSE_MODE_OFF;
				break;
			
			case "trackChanged":
				var objTrack = objBroadCastParams.track;
				params = {
				  event: "SONG_CHANGED",
					//todo: current time
				  song:{
						title: objTrack.strTrackTitle,
						artist: objTrack.strTrackArtist,
						album: objTrack.strTrackAlbum,
						genre: objTrack.strTrackGenre,
						currentTime: "" + parseInt(objTrack.intTrackTime, 10),
						duration: "" + parseInt(objTrack.intTrackDuration, 10)
				  },
				  playlist:{
						currentTrackNumber: "" + (objTrack.intTrackIndex + 1)
				  }
				};				
				break;
			
			case "playlistStart":
				//return;
				params = {
				event: this.PLAYLIST_STARTED,
				playlist: {
					repeat: objBroadCastParams.strRepeat.toUpperCase(),
					shuffle: objBroadCastParams.strShuffle.toUpperCase(),
					numberOfTracks: "" + objBroadCastParams.intTrackCount
				}
		};
				
				break;
			
			case "playlistEnd":
				
				break;
			
			case "shuffleChanged":
				//boolShuffle: boolShuffleOn, intCurrTrack
				
				var strShuffle =  (objBroadCastParams.boolShuffle)? this.SHUFFLE_MODE_ON : this.SHUFFLE_MODE_OFF;
				
				params = {
					event:this.SHUFFLE_MODE_CHANGED,
					playlist:{
						shuffle: strShuffle,
						currentTrackNumber:  "" + (objBroadCastParams.intCurrTrack + 1)
					}
				};
				break;
			
			case "repeatChanged":
				params = {
					event:this.REPEAT_MODE_CHANGED,
					playlist:{repeat: this.AR_REPEAT_MODES[objBroadCastParams.intRepeatMode]}
				};
				break;
						
			
		}
		
		this.log(params);
		
		//this.$.psBroadcaster.method = "update";
		this.$.psUpdateBroadcaster.call(params);
			
			
	},

	onSuccess_SetBroadcaster: function (sender, response)
	{
		this.log();
		this.log(response);
		
	},
	
	onFailure_SetBroadcaster: function (sender, response)
	{
		
		this.log();
	
	},
		
		
	

	
	


	///*****************************************
	// Album Art View Methods
	///*****************************************
	
	onClick_FullScreen: function ()
	{
		if(!this.boolAlbumArtViewDisplay)
		{
			if(this.boolPlaybackListSet)
			{
				this.log();
				this.ListView.boolViewActive = false;
		
				this.boolAlbumArtViewDisplay = true;
		
				this.$.paneTop.selectViewByIndex(1, true);
				
				this.$.AlbumArtView.requestTrackList();
			}
		}
		else
		{
	
			this.boolAlbumArtViewDisplay = false;
		
			//this.$.paneAll.selectViewByIndex(0, true);
			this.$.paneTop.selectViewByIndex(0, true);
			//this.$.btnBack.hide();
			
			if(this.ListView)
			{
				this.ListView.boolViewActive = true;
				if(this.ListView.refreshList)
				{
					this.ListView.refreshList();
				}
			}
			this.$.navPanel.resizeContent();
			
		}

		this.$.PlayerControl.setFullscreen(this.boolAlbumArtViewDisplay);


	},
	
	
	onRequestTracks_AlbumArtView: function (sender, callback)
	{
		this.log();
		callback(this.$.Playback.getTrackList(), this.$.Playback.getTrackIndex(), this.$.Playback.getTrackPlaying());
	},
	
	onClickAlbumArtViewTrack: function(sender, intClickTrackIndex)
	{
		this.log(intClickTrackIndex);
		
		this.$.Playback.clickTrack(intClickTrackIndex);
		//this.$.Playback.switchTrack(intClickTrackIndex);
	},

	///*****************************************
	// Nav Panel Events
	///*****************************************
	 
	onClick_NowPlaying: function(sender, objParams){
		//listView gets changed in navPanel
		//song and playlist are dealt with in navPanel
		
		switch(objParams.strListViewType)
		{
			case "Artists":
				this.onSelectArtistDetail(sender, objParams);
				break;
			case "Albums":
				this.onSelectAlbumDetail(sender, objParams);
				break;
			case "Genres":
				this.onSelectGenreDetail(sender, objParams);
				break;
			default:
				return;
		}
		
	},
	
	
	

	onClick_ListView: function (sender, strListViewType, objParams)
	{
		this.log();
		//webosEvent.start("","SwitchView",strListViewType);
		this.resetListViewSliderGroup(this.$.paneMainView.getView());
		this.selectListView(strListViewType, objParams);
		
	},
	
	
	selectListView: function (strListViewType, objParams)
	{
		
		this.log();
		this.log("strListViewType: " + strListViewType);
		this.log("objParams: " + objParams);
		
		if(strListViewType === undefined)
		{
			strListViewType = "Songs";
		}
		
		//reset listViewPlaylist to prevent previous playlist from showing up on next click
		if(strListViewType === "Playlist")
		{
			this.$.listViewPlaylist.clearPlaylist();
		}
		
		this.log(strListViewType);
		this.boolAlbumArtViewDisplay = false;
		this.boolExhibitionViewDisplay = false;

		this.$.paneMainView.selectViewByName("viewListView" + strListViewType, true);
		
		enyo.asyncMethod(this, "setListViews", this.$["listView" + strListViewType], this.ListView, objParams);

		//this.setListViews(this.$["listView" + strListViewType], this.ListView, objParams)
	
	},
	
	
	setListViews: function (objView, objPrevView, objParams)
	{
		this.log();
		
		var wasActive = objView.boolViewActive;

		//state was wrong prior so forcing it to be right
		this.$.listViewSongs.boolViewActive = false;
		this.$.listViewArtists.boolViewActive = false;
		this.$.listViewAlbums.boolViewActive = false;
		this.$.listViewGenres.boolViewActive = false;
		
		if(objPrevView)
		{
			objPrevView.boolViewActive = false;
		}
		
		var boolForceRender = false;
		
		//refresh previous view so it's nice when we go back to it
		//possible performance issue if needing to rerender the next view
		if(objPrevView && !wasActive)
		{
			objPrevView.refreshList();
		}
		
		
		if(objParams)
		{
			boolForceRender = objParams.boolForceRender || false;
		}

		if(objView !== undefined)
		{
			
			this.log("objView exists");
			
			if(!objParams || objParams.isActive !== false){
				objView.boolViewActive = true;
				this.ListView = objView;
			}
			
			if(!boolForceRender && !wasActive)
			{
				if(objView.refreshList !== undefined)
				{
					objView.refreshList(objParams);
				}				
			}
			else
			{
				if(objView.renderList !== undefined)
				{
					this.log("objView.renderList");
					objView.renderList(objParams);
				}							
			}
			
		}
		
		
	},
	
	
	
	refreshCurrentListView: function ()
	{
		this.log();
		//ONLY songs use the attribute below, and it tells it if it needs to rerender in refresh
		//this.$.listViewSongs.setBoolRefreshData(true);
		if(this.ListView)
		{
			if(this.ListView.renderList !== undefined)
			{
				this.ListView.renderList();
			}							
		}
		
	},


	onResetDetailView: function ()
	{
		this.log();
		this.resetListViewSliderGroup(this.$.paneMainView.getView());
	},

	
	resetListViewSliderGroup: function (slidingPane)
	{
		//this.log("viewListView" + strListViewType);
		
		//var slidingPane = this.$["viewListView" + strListViewType];
		if(slidingPane)
		{
			
			if(slidingPane.getViewIndex)
			{
				if(slidingPane.getViewIndex() > 0)
				{
					
					if(slidingPane.selectViewByIndex)
					{	
						//slidingPane.setCanAnimate(false);
						slidingPane.selectViewByIndex(0);
					}					
				}
			}
			

		}
		
	},
	

	onSelectArtistDetail: function (sender, obj)
	{
		this.log(obj);
		this.onSelectDetail(sender, obj, "Artist");
	},
	
	
	onSelectAlbumDetail: function (sender, obj)
	{
		this.onSelectDetail(sender, obj, "Album");
	},
	
	onSelectGenreDetail: function (sender, obj)
	{
		this.onSelectDetail(sender, obj, "Genre");
	},
	
	onSelectDetail: function(sender, obj, strName){
		this.log(obj);
		this.log(strName);
		
		//resetDetailListView doesn't happen sync (even with sync param) so view gets reset after selected
		if(this.$['listView'+strName+'Detail'] !== this.ListView){
			if(obj.showParentView){
				this.onClick_ListView(sender, strName+"s",{isActive: false});
			}
			//this.$.sldListViewGenreDetail.show();
			//this.$.viewListViewGenres.setCanAnimate(true);
		}
		//Bookkeeping on this.listView is off so we need to reselect view
		this.$['viewListView'+strName+'s'].selectView(this.$['sldListView'+strName+'Detail']);		
		
		this.$['listView'+strName+'Detail'].renderList(obj);
		
		this.ListView = this.$['listView'+strName+'Detail'];
		
		//state was wrong prior so forcing it to be right
		//this.$.listViewSongs.boolViewActive = false;
		//this.$.listViewArtists.boolViewActive = false;
		//this.$.listViewAlbums.boolViewActive = false;
		//this.$.listViewGenres.boolViewActive = false;
		
		this.ListView.boolViewActive = true;
		
	},
	
	
	onClickPlaylist_NavPanel: function (sender, objClickPlaylist)
	{
		
		this.log();
		//var objParams = {"strPlaylistName": strPlaylistName, "strPlaylistID": strPlaylistID, "boolForceRender": true};
		
		if(objClickPlaylist)
		{
			objClickPlaylist.boolForceRender = true;
			this.selectListView("Playlist", objClickPlaylist);			
		}
		
		
	},
	

	
	onClick_ExhibitionMode: function ()
	{
		this.log();
		this.$.paneAll.selectViewByIndex(1, true);
		this.$.btnBack.show();
		
		this.$.ExhibitionView.callWsArtistInfo("Sublime");
	},
	
	
	onClick_btnBackAlbumArtView: function ()
	{
		
		this.$.paneAll.selectViewByIndex(0, true);
		this.$.paneTop.selectViewByIndex(0, true);
		this.$.btnBack.hide();
		
		if(this.ListView)
		{
			this.ListView.boolViewActive = true;
			if(this.ListView.refreshList)
			{
				this.ListView.refreshList();
			}
		}

		
	},
	
	
	onItemDrag: function (sender, event)
	{
		this.log();
		//this.log(event);
		//this.$.avatar.boxToNode({l: event.pageX-425, t: event.pageY - 125});
		
		this.$.avatartop.applyStyle("-webkit-transform", "translate3d(" + (event.pageX-100) + "px," + (event.pageY - 125) + "px,0)");
		
	},
	
	
	onItemShowAvatar: function (sender, boolShowAvatar, strDragThumb)
	{
		this.log(boolShowAvatar);
		this.log("strDragThumb: " +strDragThumb);
		
		
		if(boolShowAvatar)
		{
			if(strDragThumb === undefined)
			{	
//				this.$.imgContainer.hide();
			} else {
				this.$.imgAvatar.setSrc(strDragThumb);
			}
		}
		this.$.imgContainer.setShowing(strDragThumb !== "");
		this.$.avatartop.setShowing(boolShowAvatar);
		
	},
	
	onItemShowAvatarIcon: function (sender, boolShowAvatarIcon)
	{
		this.log(boolShowAvatarIcon);
		
		//this.$.imgAvatarIcon.applyStyle("visibility", (boolShowAvatarIcon ? "visible": "hidden"));
		this.$.imgAvatarIcon.addRemoveClass("on", boolShowAvatarIcon);
		//visibility: hidden
		//this.$.imgAvatarIcon.setShowing(boolShowAvatarIcon);					
		
	},
	
	
	onError_imgAvatar: function ()
	{
		this.log();
		this.$.imgContainer.setShowing(false);		
	}
	
});