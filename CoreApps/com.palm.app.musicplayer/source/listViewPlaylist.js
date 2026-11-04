/*globals enyo, $L, event, Utilities, window, LibraryTools */
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewPlaylist",
	kind: "listViewDraggable",
	pack: "start",
	className: "playlist",
	published: {boolViewActive: false, boolListRendered: false, strPlaybackListID: ""},
	height: "100%",
	events: {onSetPlaybackList: "", onRequestMedia: "", onRequestPlaylist: "", onRequestCurrTrack: "", onUpdatePlaylist: "", onDeletePlaylist: "", onEditPlaylist: "", onAddToPlaylist: "", onItemDrag: "", onItemShowAvatar: "", onItemShowAvatarIcon: ""},
	components: [
		{name: "paneHeader", components: [
			{kind: "ctrlListViewHeader", onSearch_Header: "onSearchSongs", onEditMode:"onStartEdit", boolShowEditPlaylist: true, boolShowSearchBtn: false},
			{name: "HeaderEdit", kind: "Toolbar", style: "background-color:#155989", pack: "justify", showing: false, components: [
				{name: "txtPlaylistName", kind: "ToolInput", hint: $L("Playlist Name"), autocorrect: false, spellcheck: false, style: "margin-left:5px"},
				{kind: "Spacer", flex:1},
				{caption: $L("Cancel"), kind: "Button", className: "enyo-button-dark", style: "background-color:rgba(0,0,0,0.25);padding-left:1.2em;padding-right:1.2em;", onclick: "onCancelEdit"},
				{caption: $L("Done"), kind: "Button", className: "enyo-button-dark", onclick: "onDoneEdit", style: "background-color:rgba(0,0,0,0.25);padding-left:1.2em;padding-right:1.2em;margin:3px 5px"}
			]}
		]},
		// the content
		{name: "content", kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			// the divider
		  {name: "header", kind: "Control", layoutKind: "HFlexLayout", className: "divider first", align: "center", components: [ //there is only one header, set class manually :)
			  {name: "lblSortPos", content: $L("#"), className: "column number sorted", onclick: "onclick_Sort"},
			  {name: "lblSortSong", content: $L("Song"), flex: 1, className: "column", onclick: "onclick_Sort"},
			  {name: "lblSortArtist", content: $L("Artist"), flex: 1, className: "column down", onclick: "onclick_Sort"}
			  //{name: "lblSortAlbum", content: $L("Album"), flex: 1, className: "column", onclick: "onclick_Sort", showing: false}
		  ]},
		  // the list
			{name: "listMedia", kind: "VirtualList", onSetupRow: "listSetupRow", flex: 1, components: [
				{name: "itemMedia", kind: "draggableSong", layoutKind:"HFlexLayout", className:"song", onclick: "onclick_listMedia", components: [
					{kind: "Control", name: "songPlaying", className: "column", showing: true},
					{name: "songNumber", content: '1', className: "column number", showing: true},
					{name: "songTitle", flex: 1, className: "column title"},
					{name: "songArtist", flex: 1, className: "column artist"},
					{name: "songAlbum", flex: 1, className: "column sAlbum", showing: false},
					{kind: "Control", name: "colDeleteItem", onclick: "onclick_btnDeleteItem", className: "delete", showing: false}
			  ]}
		  ]}
		]},
		{name: "txtResults", className: 'empty'},
		{name: "empty", kind: "Control", layoutKind: "VFlexLayout", align:"center", height:"100%", pack: "justify", className: 'empty', showing: false, components: [
			{kind: "Spacer", flex: 1},
			{kind: "Image", src: "images/bg_empty_songs.png"},
			{content: $L("There are no songs in this playlist."), className: "heading"},
			{content: $L("Drag and drop songs onto this playlist's navigation item to add songs to it."), className: "subheading"},
			{kind: "Spacer", flex: 3}		
		]},
		{name: "footerEdit", kind: "Toolbar",  className: "enyo-toolbar-light", /*style: "background:#222",*/ pack: "end", showing:false, components: [
				{kind: "Button", caption: $L("Add Songs"), showing: false},
				{name: "btnDeletePlaylist", caption: $L("Delete Playlist"), onclick: "onclick_btnDeletePlaylist", style: "padding-left:1.2em;padding-right:1.2em;"}
		]},
		{name: "popupConfirmDone", kind: "ModalDialog", components: [
			{content: $L("Delete the selected songs?"), className: "confirm-content"},
			{ name : "promptConfirmDone", kind: "ConfirmPrompt", className: "confirmpromptbox", confirmCaption: $L("Delete"), onConfirm: "onConfirmDone", onCancel: "onCancelDone"}														
		]},
		{name: "popupConfirmDelete", kind: "ModalDialog", components: [
			{content: $L("Delete this playlist?"), className: "confirm-content"},
			{ name : "promptConfirmDelete", kind: "ConfirmPrompt", className: "confirmpromptbox", confirmCaption: $L("Delete"), onConfirm: "onConfirmDelete", onCancel: "onCancelDelete"}														
		]}
		
    ],
	intCurrTrack:  -1,
	intCurrTrackOrigIndex:  -1,
	strCurrTrackID: "",

	strCurrListID: "",
	
	strPlaylistName: "",
	strPlaylistID: "",
	strPlaylistFile: "",
	strPlaylistKind: "",

	searchQuery: "",
	
	boolSortAsc: true,
	
	boolEditMode: false,
	
	arUpdateTracks : [],
	arSongs : [], 
	boolNotifyListChange: false,
	
	_strListQuery: "{}",
	
	create: function () {
		//test
		
		try
		{
			this.inherited(arguments);
			this.strSortMode = "position";
			this.boolSortAsc = true;
			this.strCurrListID = Utilities.generateGuid();
			
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
	
	
	/**
	* @param: objParams
	*		.strPlaylistName - name of playlist
	*		.strPlaylistID - _id of playlist
	*		.strPlaylistKind - kind of playlist 
	*		.strPlaylistFile - path to file playlist 
	*/
	renderList: function (objParams)
	{
		
		
		this.log("objParams: ", objParams);
		try
		{
		
		//In case mediawatcher fires
		if(objParams  === undefined)
		{
			objParams = {strPlaylistName: this.strPlaylistName, strPlaylistID: this.strPlaylistID, strPlaylistFile: this.strPlaylistFile, strPlaylistKind: this.strPlaylistKind};
		}
		
		this.log();
		
		if(objParams.strPlaylistFile !== undefined || objParams.strPlaylistID !== undefined)
		{
			this.boolEditMode = false;
			
			this.$.txtResults.setContent($L("Playlist Deleted"));
			
			if(this.strPlaylistName !== objParams.strPlaylistID)
			{
				this.strCurrListID = Utilities.generateGuid();
			}
			
			if(objParams.strPlaylistID)
			{
				this.strPlaylistID = objParams.strPlaylistID;
			}
			if(objParams.strPlaylistFile)
			{
				this.strPlaylistFile = objParams.strPlaylistFile;
			}			

			this.strPlaylistKind = objParams.strPlaylistKind;

			this.arSongs = [];

			this.searchQuery = "";
			this.$.ctrlListViewHeader.clearSearchInput(false);
			
			this.doRequestCurrTrack();
			
			this.getPlaylist();
			
			this.$.lblSortPos.addClass("sorted");

		}
		else
		{
			this.playlistDeleted();
			this.log("????");
			
		}
		
		}
		catch(err)
		{
			this.log("err: " + err);
		}
		
		
	},
	
	refreshList: function ()
	{
		this.log();
		
		this.$.listMedia.refresh();
		this.doRequestCurrTrack();
		
	},

	resizeList: function ()
	{
		this.log();
		this.$.listMedia.resized();
	},
	
	getPlaylist: function () 
	{
		
		this.log();
		
		if(this.strPlaylistID !== "" || this.strPlaylistFile !== "")
		{
			var objGetPlaylistsRequest;
			
			if(this.strPlaylistID !== "")
			{
				objGetPlaylistsRequest = {where: [{"prop": "_id", "op": "=", "val": this.strPlaylistID }], playlistKind: this.strPlaylistKind, callback: enyo.bind(this, "gotPlaylist")};
			}
			else if(this.strPlaylistFile !== "")
			{
				objGetPlaylistsRequest = {where: [{"prop": "path", "op": "=", "val": this.strPlaylistFile }], playlistKind: this.strPlaylistKind, callback: enyo.bind(this, "gotPlaylist")};				
			}
	
			this.log("**** raising doRequestMedia");
			this.log("**** objGetMediaRequest: ", objGetPlaylistsRequest);
			
			this.doRequestPlaylist(objGetPlaylistsRequest);
			
			
		}
		else
		{
			this.log("Missing minimum params");
		}
	
		
	},
	
	gotPlaylist: function (inResponse)
	{
		this.log();
		try
			{
			
			
			this.log(inResponse);
		
			if(inResponse.results)
			{
		
				this.arPlaylists = inResponse.results.slice(0);
				
				this.log("this.arPlaylists.length: " + this.arPlaylists.length);
				this.$.ctrlListViewHeader.setBoolShowSearchBtn(true);
				if(this.arPlaylists.length > 0)
				{
					
					switch(this.strPlaylistKind)
					{
						
						case "com.palm.music.staticplaylists:1" :

							this.$.ctrlListViewHeader.setBoolShowEditPlaylist(true);
							
							this.strTracksArrayName = "tracks";
							this.strTracksPathProp = "path";
							this.strWhereProp = "path";
							this.strTitleProp = "name";
							this.parsePlaylistTracks();
							break;
						
						case "com.palm.media.playlist.file:1" :

							this.$.ctrlListViewHeader.setBoolShowEditPlaylist(false);
							
							this.strTracksArrayName = "songPaths";
							this.strTracksPathProp = "";
							this.strWhereProp = "path";
							this.strTitleProp = "title";
							//this.getSongs(this.arPlaylists[0].songIds);
							this.parsePlaylistTracks();
							
							break;
						
					}
					
					if(this.strTitleProp && this.arPlaylists[0][this.strTitleProp])
					{
						this.$.ctrlListViewHeader.setStrHeaderTitle(this.arPlaylists[0][this.strTitleProp]); 
						this.strPlaylistName = this.arPlaylists[0][this.strTitleProp];
					}
					
						
				}
			
			}
			

		}
		catch (err)
		{
			this.log("error: " + err);

		}
		
	},
	
	
	
	parsePlaylistTracks: function ()
	{

		if(this.arPlaylists[0])
		{
			var arrPlaylistTrack = this.arPlaylists[0][this.strTracksArrayName];
			if(arrPlaylistTrack)
			{
				var songCount = 0;
				var arTrackPaths = [];
				if(arrPlaylistTrack.length > 0)
				{
					
					
					for(var intListTrack = 0; intListTrack < arrPlaylistTrack.length; intListTrack++)
					{
						
						if(this.strTracksPathProp === "")
						{
							//limited after search for playlist files
							arTrackPaths[intListTrack] = arrPlaylistTrack[intListTrack];
						}
						else
						{
							//the space in front is used as a simple way to tokenize
							if(enyo.g11n.Char.toUpper(" "+arrPlaylistTrack[intListTrack].artist).indexOf(this.searchQuery) >= 0 || enyo.g11n.Char.toUpper(" "+arrPlaylistTrack[intListTrack].title).indexOf(this.searchQuery)  >= 0)
							{
								arTrackPaths.push(arrPlaylistTrack[intListTrack][this.strTracksPathProp]);
							}
								//this.objSongHash[this.arPlaylists[0][this.strTracksArrayName][intListTrack].path] = {};
						}
						
					}
					
					this.getSongs(arTrackPaths);
					
				}
				else
				{
					this.$.content.hide();
					this.$.listMedia.punt();
					this.$.empty.show();
				}
				
				if(this.$.ctrlListViewHeader){
					this.$.ctrlListViewHeader.setIntHeaderCount(-1);
				}
			}
			
		}
	},
	
	
	
	
	getSongs: function (arTrackPaths)
	{
		
		var objGetMediaRequest = {where: [{"prop": this.strWhereProp, "op": "=", "val": arTrackPaths}], mediaType: "song", callback: enyo.bind(this, "gotSongs")};
		
		if(this.strDbNext !== "")
		{
			objGetMediaRequest.page = this.strDbNext;
		}
		
		
		this.log("raising doRequestMedia");
		this.log("objGetMediaRequest: ", objGetMediaRequest);
		
		this.doRequestMedia(objGetMediaRequest);		
		
		
		
	},
	gotSongs: function (inResponse, inRequest)
	{
		this.log();

		try
			
			{
			
			this.log("**** gotSongs");
			this.log("count: " + inResponse.results.length);
			
			if(inResponse.results.length > 0)
			{
				
				this.log("--: " + inResponse.results[0].title);
				
				
				if(inRequest.objGetMediaRequest.page)
				{
					this.arSongs = this.arSongs.concat(inResponse.results);
				}
				else
				{
					this.arSongs = inResponse.results.slice(0);
			
				}
			
			}
			
			//this.log("inResponse.count: " + inResponse.count);

			
			if(inResponse.next)
			{
				this.log("inResponse.next: " + inResponse.next);
				this.strDbNext = inResponse.next;
				//this.listQuery();
				this.getSongs();
			}
			else
			{
				this.strDbNext = "";
				this.strSortMode = "position";
				this._strListQuery = inRequest.json + inResponse.count;
				
				if(this.arSongs.length > 0)
				{	
					this.$.content.show();
										
					//-------------------					
					if(window.PalmSystem)
					{	
					
						this.objSongHash = {};
						
						for(var intResultIndex = 0; intResultIndex < inResponse.results.length; intResultIndex++)
						{
							this.objSongHash[inResponse.results[intResultIndex][this.strWhereProp]] = this.arSongs[intResultIndex];
						}
						
						this.log("this.objSongHash: ", this.objSongHash);
						
						if(this.arPlaylists[0])
						{
							if(this.arPlaylists[0][this.strTracksArrayName])
							{
							
								if(this.arPlaylists[0][this.strTracksArrayName].length > 0)
								{
								
									//var arTrackPaths = [];
									//arTrackPaths = this.arPlaylists[0][this.strTracksArrayName];
									//grab all track paths
									var arTrackPaths = [];
									var intListTrack;
									for(intListTrack = 0; intListTrack < this.arPlaylists[0][this.strTracksArrayName].length; intListTrack++)
									{
										
										if(this.strTracksPathProp === "")
										{
											arTrackPaths[intListTrack] = this.arPlaylists[0][this.strTracksArrayName][intListTrack];
										}
										else
										{
											arTrackPaths[intListTrack] = this.arPlaylists[0][this.strTracksArrayName][intListTrack][this.strTracksPathProp];
										}
										
									}
									
									
									this.log("arTrackPaths: ", arTrackPaths);
									
									this.arSongs = [];
									
									
									for(intListTrack = 0; intListTrack < arTrackPaths.length ; intListTrack++)
									{
										
										if(this.objSongHash[arTrackPaths[intListTrack]])
										{
											
											var toCopy = this.objSongHash[arTrackPaths[intListTrack]];
											
											//filter for file playlists
											if(this.strTracksPathProp === "")
											{
												//the space in front is used as a simple way to tokenize
												if(enyo.g11n.Char.toUpper(" "+toCopy.artist).indexOf(this.searchQuery) === -1 && enyo.g11n.Char.toUpper(" "+toCopy.title).indexOf(this.searchQuery)  === -1)
												{
													continue;
												}	
											}
											//This creates a semi-deep copy of each song so we can change the track number	
											this.objSongHash[arTrackPaths[intListTrack]].track.position = intListTrack+1;
											var newObj = {};
											for(var prop in toCopy){
												if(typeof(toCopy[prop]) !== "undefined" && prop !== "track")
												{
													newObj[prop] = toCopy[prop];
												}
											}
											toCopy = toCopy.track;
											newObj.track = {};
											for(prop in toCopy){
												if(typeof(toCopy[prop]) !== "undefined"){
													newObj.track[prop] = toCopy[prop];
												}
											}
											this.arSongs.push(newObj);
											
									
										}
									}
									if(this.$.ctrlListViewHeader)
									{
										this.$.ctrlListViewHeader.setIntHeaderCount(this.arSongs.length);
									}
								}
							}
						}	
						
						this.log("this.arSongs: ", this.arSongs);
						
						//this.arSongs = this.objSongHash
						this.objSongHash = null;
						//this.arSongs = inResponse.results.slice(0);
					}
					else
					{
					this.arSongs = inResponse.results;
					
					}
				
					//--------------------
					
					//this.$.listMedia.resized();
					this.$.listMedia.punt();
									
				}
				else
				{
					this.$.content.hide();
					this.$.listMedia.punt();
					this.$.ctrlListViewHeader.setIntHeaderCount(this.arSongs.length);
					//this.$.listMedia.hide();

					//this.doEnableControls(false, false);
					
					
					if(inRequest.objGetMediaRequest)
					{

						if(inRequest.objGetMediaRequest.queryType)
						{
							if(inRequest.objGetMediaRequest.queryType === "search")
							{
								this.$.empty.hide();								
							}
							else
							{
								this.$.empty.show();
							}
						}
					}
					

				//this.$.txtResults.setContent("No Songs Found");
				}
			}
		
			
			
			
			this.boolListRendered = true;

		}
		catch (err)
		{
			this.log("**** gotSongs error: " + err);

		}
		
	},
	
	updateSearchQuery: function(newSearch)
	{
		if(this.searchQuery !== newSearch)
		{ 
			this.strCurrListID = Utilities.generateGuid();
			this.searchQuery = newSearch;
			this.parsePlaylistTracks();
		}
	},
	
	onSearchSongs: function (sender, strSearch)
	{
		
		this.intJumpRowOffset = 0;

		this.arSongs = [];
		
		if(Utilities.fastTrim(strSearch) === "")
		{
			this.updateSearchQuery("");
			return;
		}
		
		//we put a " " in front for easier tokenizing
		this.updateSearchQuery(enyo.g11n.Char.toUpper(" "+enyo.string.trim(strSearch)));
		return true;
	},
	
	
	listSetupRow: function (inSender, intIndex)
	{
		try
		{
		//	this.log("intIndex: " + intIndex);
			if(this.arSongs !== undefined)
			{				
				if(this.arSongs[intIndex] !== undefined)
				{
					var objSong = this.arSongs[intIndex];
					
					//this.log("title: " + objSong.title);
				
					this.$.songNumber.setContent(objSong.track.position);
					this.$.songTitle.setContent(objSong.title);
					this.$.songArtist.setContent(objSong.artist);
					//this.$.songAlbum.setContent(objSong.album);
					
					this.$.colDeleteItem.setShowing(this.boolEditMode);
					
					this.$.itemMedia.addRemoveClass("deleted", (objSong.boolDeleted));
					
					//sets playing class if needed
					this.handlePlayingSong(objSong, intIndex);
					
					this.$.itemMedia.addRemoveClass("odd", (intIndex % 2 === 0 ));
					
					return true;
					
					
					
				}
			//return true;
			}

	
		}
		catch(err)
		{
			this.log("error: " + err);
		}		
		
	

	},

	handlePlayingSong: function(objSong, intIndex){
		if(objSong._id === this.strCurrTrackID){
			//If we're on the same list
			if(this.strCurrTrackListQuery === this._strListQuery + this.strSortMode)
			{
				//if we left and came back, if we are in the original list, reset up the original indices
				if(objSong.origIndex === undefined){
					for(var i = 0; i<this.arSongs.length; i++){
						this.arSongs[i].origIndex = i;
					}
				}
				
				this.$.itemMedia.addRemoveClass("playing", (this.intCurrTrackOrigIndex === objSong.origIndex));
				this.$.itemMedia.addRemoveClass("playingHere", (this.intCurrTrackOrigIndex === objSong.origIndex));
				
			}
			else
			{
				//Check if we are the first one and if so highlight
				var first = false;
				for(var tempIndex = 0; tempIndex<=intIndex; tempIndex++){
					if(tempIndex === intIndex){
						first = true;
					}
					if(this.arSongs[tempIndex]._id === objSong._id){
						break;
					}
				}
				this.$.itemMedia.addRemoveClass("playing", first);
			}
		}
		else
		{
			this.$.itemMedia.removeClass("playingHere");
			this.$.itemMedia.removeClass("playing");
		}
	},
	
	onclick_Sort: function (objSender)
	{
		try
		{
			
			this.log("onclick_Sort: " + objSender.name + " - " + this.boolSortAsc);

			//this.$.txtResults.setContent("onclick_Sort<br/>" + objSender.name  + "<br/>");
			this.log("curr sort mode " + this.strSortMode);
			
			var boolNewSortAsc;


			switch (objSender.name)
			{
				
			case "lblSortArtist":
				
				boolNewSortAsc = this.strSortMode !== "artist" || (this.strSortMode === "artist" && !this.boolSortAsc);
				
				LibraryTools.sortbyArtist(this.arSongs, boolNewSortAsc);
				this.strSortMode = "artist";
				this.boolSortAsc = boolNewSortAsc;
				break;
			
			case "lblSortSong":
				
				boolNewSortAsc = this.strSortMode !== "title" || (this.strSortMode === "title" && !this.boolSortAsc);
				
				LibraryTools.sortbySong(this.arSongs, boolNewSortAsc);
				this.strSortMode = "title";
				this.boolSortAsc = boolNewSortAsc;
				break;
			
			case "lblSortPos":
				
				boolNewSortAsc = this.strSortMode !== "position" || (this.strSortMode === "position" && !this.boolSortAsc);
				
				LibraryTools.sortbyPos(this.arSongs, boolNewSortAsc);
				this.strSortMode = "position";
				this.boolSortAsc = boolNewSortAsc;
				break;
			
			/*case "lblSortGenre":
				
				boolNewSortAsc = this.strSortMode !== "genre" || (this.strSortMode === "genre" && !this.boolSortAsc);

				LibraryTools.sortbyGenre(this.arSongs, boolNewSortAsc);
				this.strSortMode = "genre";
				this.boolSortAsc = boolNewSortAsc;
				break;
						
			*/	
				
			}
			
			this.log("set to " + this.boolSortAsc);
				
			//this.renderContent();
			
			this.resetMusicListHeaders();
			objSender.addClass("sorted"); 
			objSender.addRemoveClass("asc", boolNewSortAsc);
			objSender.addRemoveClass("desc", !boolNewSortAsc);
			
			this.$.listMedia.refresh();

			this.strCurrListID = Utilities.generateGuid();

			//this.boolNotifyListChange = true;
			this.doListChanged(this.arSongs, this.intCurrTrackOrigIndex);
			
			//this.doListSorted(this.strSortMode, this.boolSortAsc);
			
		
		}
		catch (err)
		{
			this.log("**** onclick_Sort error: " + err);
		}
		
	},
	
	resetMusicListHeaders: function ()
	{
		for(var intChildIndex = 0; intChildIndex < this.$.header.children.length; intChildIndex++)
		{
			var header = this.$.header.children[intChildIndex];
			header.removeClass("desc");
			header.removeClass("asc");
			header.removeClass("sorted");
		}
	},
	
	clearPlaylist: function ()
	{
		this.strPlaylistName =  "";
		this.strPlaylistID = "";
		
		this.$.empty.hide();
		this.$.HeaderEdit.hide();
		this.$.ctrlListViewHeader.show();
		this.$.content.hide();
		
		this.$.footerEdit.hide();
		this.$.txtResults.hide();
		this.resetMusicListHeaders();
		
		this.$.ctrlListViewHeader.setStrHeaderTitle("");
		this.$.ctrlListViewHeader.setIntHeaderCount(-1);
		this.$.ctrlListViewHeader.setBoolShowEditPlaylist(false);
		this.$.ctrlListViewHeader.setBoolShowSearchBtn(false);
		
	}, 
	
	onclick_listMedia: function (sender, event)
	{
		
		this.log(event.rowIndex);
			
		var intClickIndex =  event.rowIndex;
		
		this.objPlaylist = this.arPlaylists[0];
		
		if(!this.boolEditMode)
		{
			var objSetPlaybackList =	{arThumbnails		:  this.arSongs[intClickIndex].thumbnails,
										 arSetPlaybackList	: this.arSongs,
										 intStartTrackIndex	: intClickIndex, 
										 intStartTrackTime	: 0, 
										 strOriginListID	: this.strCurrListID,
										 strListType		: "Playlist", 
										 strListText		: this.strPlaylistName,
										 strPlaylistKind	: this.strPlaylistKind, 
										 strListQuery		: this._strListQuery + this.strSortMode,
										 strPlaylistID		: this.strPlaylistID};
			this.doSetPlaybackList(objSetPlaybackList);
		
		
		}
	},
	
	
	onStartEdit: function ()
	{

		this.onEditMode(true);
		this.$.listMedia.refresh();

	},
	
	onCancelEdit: function ()
	{
		
		this.onEditMode(false);
		
		
		for(var intClearIndex=0; intClearIndex < this.arSongs.length; intClearIndex++)
		{
			this.arSongs[intClearIndex].boolDeleted = false;
		}
		
		this.$.listMedia.refresh();

	},
	
	onDoneEdit : function ()
	{
		this.log();
		
		
		if(this.processDeletedTracks(true))
		{
			this.$.popupConfirmDone.openAtCenter();			
		}
		else
		{
			this.closeEdit();			
		}
		
	},
	
	onConfirmDone: function ()
	{
		this.log();
		this.processDeletedTracks(false)
		this.closeEdit();
		
	},

	onCancelDone: function ()
	{
		this.log();
		this.$.popupConfirmDone.close();
	},
	
	closeEdit: function ()
	{
		this.$.popupConfirmDone.close();
		this.onEditMode(false);
		this.updatePlaylist();			
	},
	
	onEditMode: function (boolEditMode)
	{
		
		this.log();
		
		this.boolEditMode = boolEditMode;

		this.doEditPlaylist(this.boolEditMode ? this.strPlaylistID : "");
		this.$.listMedia.addRemoveClass("editing", this.boolEditMode);
		
		if(this.boolEditMode)
		{

			this.$.HeaderEdit.show();
			this.$.ctrlListViewHeader.hide();
			this.$.footerEdit.show();
			this.$.txtPlaylistName.setValue(this.strPlaylistName);
			
		}
		else
		{

			this.$.HeaderEdit.hide();
			this.$.ctrlListViewHeader.show();
			this.$.footerEdit.hide();
		
		}


	},
	
	onclick_btnDeleteItem: function (sender, event)
	{
	
		var intClickIndex =  this.$.listMedia.fetchRowIndex();
		//this.arSongs.splice(intClickIndex,1);
		
		if(this.arSongs[intClickIndex])
		{
			if(this.arSongs[intClickIndex].boolDeleted)
			{
				this.arSongs[intClickIndex].boolDeleted = false;
			}
			else
			{
				this.arSongs[intClickIndex].boolDeleted = true;
			}
		}
		
		this.$.listMedia.refresh();
	
	},
	
	processDeletedTracks: function (boolCheckOnly)
	{
		
		this.log();
		
		if(boolCheckOnly === undefined)
		{
			boolCheckOnly = false;
		}
		
		this.arUpdateTracks = [];
		
		var intOriginalTrackCount = this.arSongs.length;
		
		for(var intIndex = this.arSongs.length -1; intIndex >= 0; intIndex--)
		{
			if(boolCheckOnly)
			{
				if(this.arSongs[intIndex].boolDeleted)
				{
					return true;
				}
			}
			else
			{
				if(this.arSongs[intIndex].boolDeleted)
				{
					this.arSongs.splice(intIndex,1);
				}
				else
				{
					this.arUpdateTracks.unshift({title: this.arSongs[intIndex].title, artist : this.arSongs[intIndex].artist, path : this.arSongs[intIndex].path});
	
				}				
			}

			
		}
		
		return this.arUpdateTracks.length != intOriginalTrackCount;
		
	},
	
	updatePlaylist: function ()
	{
		var edited = false;
		if(this.arPlaylists[0].name !== this.$.txtPlaylistName.getValue()){
			this.arPlaylists[0].name = this.$.txtPlaylistName.getValue();
			this.$.ctrlListViewHeader.setStrHeaderTitle(this.arPlaylists[0].name);
			this.strPlaylistName = this.$.txtPlaylistName.getValue();
			edited = true;
		}
		
		if(this.arPlaylists[0][this.strTracksArrayName].length !== this.arUpdateTracks.length){
			this.$.ctrlListViewHeader.setIntHeaderCount(this.arUpdateTracks.length);
			this.arPlaylists[0][this.strTracksArrayName] = this.arUpdateTracks;
			edited = true;
		}
	
		if(edited){
			this.log("this.arPlaylists[0]: ", this.arPlaylists[0]);
			
			var objUpdatePlaylist = {objPlaylist: this.arPlaylists[0], callback: enyo.bind(this, "playlistUpdated")};
			
			this.doUpdatePlaylist(objUpdatePlaylist);
		}
	//	this.parsePlaylistTracks();

	},
	
	
	playlistUpdated: function (sender, response)
	{
		this.log();
		this.strCurrListID = Utilities.generateGuid();
		this.getPlaylist();
		
	},

	
	onclick_btnDeletePlaylist: function (sender, event)
	{
		this.$.popupConfirmDelete.openAtCenter();		
	},
	
		
	onConfirmDelete: function ()
	{
		this.log();
		this.deletePlaylist();	
		this.$.popupConfirmDelete.close();
	},
	
	onCancelDelete: function ()
	{
		this.log();
		this.$.popupConfirmDelete.close();
		
	},
	
	
	deletePlaylist: function ()
	{
		
		var objDeletePlaylist = {strPlaylistID: this.strPlaylistID};		
		this.doDeletePlaylist(objDeletePlaylist);		
		
	},
	
	playlistDeleted:  function ()
	{
		
		this.log();
		this.$.empty.hide();
	
		this.arSongs = [];
	
		this.$.listMedia.punt();	
		
		this.clearPlaylist();
		
		this.$.txtResults.setContent($L("Playlist Deleted"));
		this.$.txtResults.show();
		
		
	},
	


	itemDragFinish: function(sender, event)
	{
		
		this.log();
		this.boolDraggingList = false;		
		if (this.boolDragItem) {
			
			event.dragInfo.boolShowIcon = false;
			if(event.dragInfo.strPlaylistID)
			{
				
				var tempCallback = enyo.bind(this, function(){
					if(event.dragInfo.strPlaylistID == this.strPlaylistID)
					{
					
						//TODO: make not jump to the top
						this.renderList({strPlaylistName: this.strPlaylistName, strPlaylistID: this.strPlaylistID, strPlaylistKind: this.strPlaylistKind});
					}
				});
				
				
				this.callAddToPlaylist(event.dragInfo.dragItemIndex, event.dragInfo.strPlaylistID, "song", tempCallback);
			}
			
			this.$.listMedia.prepareRow(event.dragInfo.dragItemIndex);
			
			this.setItemHighlighted(false);
		
			this.doItemShowAvatarIcon(false);

			this.doItemShowAvatar(false);
		
			this.boolDragItem = false;
			
			this.dragObjType = null;
		}
		
		//return true;
		
		
	}
});
        