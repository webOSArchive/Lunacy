/*globals enyo, $L, event, Utilities, window, setTimeout, clearTimeout */
enyo.kind({
	name: "kindListViewSongs",
	kind: "listViewDraggable",
//	className: "playlist",
	published: {boolViewActive: false, boolRefreshData: true},
	events: {onSetPlaybackList: "", onRequestMedia: "", onRequestCurrTrackInfo: "", onListChanged: "", onListSorted: "", onEnableControls: ""},
	height: "100%",
	components: [
		// the header
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchSongs"},
		// the content
		{name: "content", kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			// the divider
			{name: "header", kind: "Control", layoutKind: "HFlexLayout", className: "divider first", align:'center', components: [
				//{name: "lblNumber", content: $L("#"), className: "column number"},
				{name: "lblSortSong", content: $L("Song"), flex: 2, className: "column", onclick: "onclick_Sort"},
				{name: "lblSortArtist", content: $L("Artist"), flex: 1, className: "column sorted", onclick: "onclick_Sort"},
				{name: "lblSortAlbum", content: $L("Album"), flex: 1, className: "column", onclick: "onclick_Sort"}
			]},
			// the list
			{kind: "DbService", method: "find", name: "SongsDB", onSuccess: "gotSongs", dbKind:"com.palm.media.audio.file:1"},
			//{kind: "Control", layoutKind: "HFlexLayout", flex: 1, components: [
				{name: "listMedia", kind: "VirtualList", onSetupRow: "listSetupRow", flex: 1, components: [
					{name:"itemMedia", kind: "draggableSong", layoutKind:"HFlexLayout", className:'song', onclick: "onclick_listMedia", components: [
						//{name: "songNumber", content: '1', className: "column number"},
						{kind: "Control", name: "songPlaying", className: "column song_genre_Blueicon", showing: true},
						{name: "songTitle", flex: 2, className: "column title"}, 
						{name: "songArtist", flex: 1, className: "column artist"},
						{name: "songAlbum", flex: 1, className: "column album"}
					]}
				]}
				
			//]},

		]},
		// the empty view
		{name: "empty", kind: "Control", layoutKind: "VFlexLayout", align:"center", height:"100%", pack: "justify", className: "empty", showing: false, components: [
			{kind: "Spacer", flex: 1},
			{kind: "Control", className: "content", components: [
			  {kind: "Image", src: "images/bg_empty_songs.png"},
			  {content: $L("There are no songs on your HP TouchPad"), className: "heading"},
			  {content: $L("You can add music to your HP TouchPad with the HP Play music app, found at www.hpplay.com, or copy music to your HP TouchPad in USB mode.")}
			]},
			{kind: "Button", caption: $L("Add songs"), showing: false},
			{kind: "Spacer", flex: 3}		
		]},
		{name: "txtResults"}

    ],
	intCurrTrack:  -1,
	intCurrTrackOrigIndex:  -1,
	strCurrTrackID: "",
	strCurrTrackListQuery: "",
	
	searchQuery: "",
	
	strCurrListID: "",
	
	strSortMode: "artist",
	boolSortAsc: true,

	strDbNext: "",
	
	arSongs: [],
	
	boolNotifyListChange: false,
	
	strShowSongTitle: "",
	strShowSongArtist: "",
	strShowSongFile: "",
	
	_arrIntToSongIndex: [],
	_boolReloading : false,
	
	_boolWaitToJump: false, 

	_strListQuery : "",
	//intJumpRowOffset: 0, inherrited from listViewDraggable

	create: function () {
		//test
		
		try
		{
			this.inherited(arguments);
			this.strSortMode = "sortKey.artistAlbumDiscAndTrack";
			this.boolSortAsc = true;
			this.strCurrListID = Utilities.generateGuid();
		}
		catch (err)
		{
			this.log("**** create error: " + err);
		}

	},
		
	ready: function () {

		this.inherited(arguments);
		this.$.ctrlListViewHeader.setStrHeaderTitle($L("Songs"));
	
	},
	
	rendered: function()
	{
		this.inherited(arguments);
		
	},
	
	
	renderList: function (objParams)
	{
		this.log();
		this.log("objParams: ", objParams);
		this.$.ctrlListViewHeader.clearSearchInput(false);	
		this.searchQuery = "";
		this.strDbNext = "";
		this.strShowSongTitle = "";
		this.strShowSongArtist = "";
		this.strShowSongFile = "";
		this.intJumpRowOffset = 0;
		
		if(objParams)
		{

			if(objParams.strSongTitle)
			{
				this.strShowSongTitle = objParams.strSongTitle;
			}
			if(objParams.strSongArtist)
			{
				this.strShowSongArtist = objParams.strSongArtist;
			}
			if(objParams.strSongFile)
			{
				this.strShowSongFile = objParams.strSongFile;
			}
			
			
			
		}
		this.listQuery(undefined, undefined, objParams);
		
	},

	
	refreshList: function (objParams)
	{
		this.log();

		this.$.ctrlListViewHeader.clearSearchInput(false);		
		this.doRequestCurrTrackInfo(objParams);
		
		
		if(this.boolRefreshData || this.searchQuery !== "")
		{
			this.searchQuery = "";
			this.listQuery(undefined, undefined, objParams);
			this.boolRefreshData = false;
		}
	
	},
	
	resizeList: function ()
	{
		this.$.listMedia.resized();
	},

	//overriding function in listViewDraggable
	setScrolling: function(bool){
		this.scrolling = bool;
		this._boolWaitToJump = true;
	},
	
	onTrackEnd: function(){
		
	},
	
	onTrackPlaying: function(){
		this._boolWaitToJump = false;
	},
	
	listQuery: function (inQuery, nextPage, objParams)
	{
		/* Clear the array if we've got a brand new query */
		if(inQuery === undefined || nextPage === undefined) 
		{
			this.arSongs = [];
			this._boolReloading = true;
			this._arrIntToSongIndex = [];
			this.intJumpRowOffset = 0;
			this._boolWaitToJump = false;
		}
		else
		{
			if(this.lastQuery !== inQuery)
			{
				return;
			}
		} 

		var query = inQuery || {};
		/* Skip generating the query again if we're on a subsequent page */
		if(this.lastQuery !== query)
		{
			this.arSongs = [];
			this.intJumpRowOffset = 0;
			if(query.where === undefined)
			{
				query.where = [{"prop": "isRingtone", "op": "=", "val": false}];
			}
			query.orderBy = this.strSortMode || "";
			query.desc = !this.boolSortAsc || false;
			
			//strShowSongTitle: undefined,
			//strShowSongArtist: undefined,
			//strShowSongFile: undefined,
			
			if(this.strShowSongTitle !== "" && this.strShowSongArtist !== "")
			{
				query.where = [{"prop": "isRingtone", "op": "=", "val": false}, {"prop": "title", "op": "=", "val": this.strShowSongTitle}, {"prop": "artist", "op": "=", "val": this.strShowSongArtist}];
				query.orderBy = "";
			}
			else if(this.strShowSongFile !== "")
			{
				query.where = [{"prop": "path", "op": "=", "val": this.strShowSongFile}];
				query.orderBy = "";
			}
			else if(this.searchQuery !== "")
			{
				query.where.push({"prop": "searchKey", "op": "?", "val": this.searchQuery, "collate": "primary"});
			}
			
			//query.where.push({"prop": "isRingtone", "op": "=", "val": false});


			//limit set to 300 to increase perforance when scrolling fast to the bottom, slightly longer delay to come up
			query.limit = 500;
		}
		query.page = nextPage || "";
		this.lastQuery = query;

		/* Got some bizarre errors when trying to use search on find, FIXME? */
		if(this.searchQuery !== "")
		{
			this.$.SongsDB.method = "search";
		}
		else
		{
			this.$.SongsDB.method = "find";
		}
		
		this.log("query: ", query);
		
		this.$.SongsDB.call({count: true, watch: false, subscribe: false, query: query}, {"userdata": query, "objParams": objParams});
		//webosEvent.event("", "musicPlayer.songs.listQuery", enyo.json.stringify(query));
	},


	gotSongs: function (inSender, inResponse, inRequest)
	{
		//inRequest.startTime
		
		//inRequest.json
		//inResponse.count
		
		//webosEvent.event("", "musicPlayer.songs.gotSongs", this.arSongs.length);
		/* Do not parse results if this is not the immediately previous query made */
		if(this.lastQuery !== inRequest.userdata)
		{
			return;
		}
		this.log();

		try
			
			{
			
			this.log("**** gotSongs");
			this.log("count: ", inResponse.results.length);
			this.log("count: ", inResponse.count);
			
			
			if(inResponse.results.length > 0)
			{
				this.log("--: " + inResponse.results[0].title);
				this.arSongs = this.arSongs.concat(inResponse.results);
			
			}
			//add id's to lookup table
			for(var i = this.arSongs.length-inResponse.results.length; i<this.arSongs.length; i++){
				this._arrIntToSongIndex[this.arSongs[i]._id] = i;
			}
			
			//need to set unique list variables before jump
			if(inResponse.results.length === this.arSongs.length)
			{
				/* We're in the first page of results, set the count, generate a unique playlist ID */

				this.$.ctrlListViewHeader.setIntHeaderCount(inResponse.count);
				this.strCurrListID = Utilities.generateGuid();
				//create a unique query for this list of songs
				this._strListQuery = inRequest.json + inResponse.count;
			
			}
			
			var punted = this._jumpIfNeeded(inRequest.objParams);
			
			
			if(inResponse.results.length === this.arSongs.length)
			{
				/* We're in the first page of results, set the count, generate a unique playlist ID */

				if(this.arSongs.length > 0)
				{	

					//this.$.lblNumber.setStyle("width: " + (inResponse.count +'').length * 0.8 + "em");
					this.$.content.show();
					
					if(punted === false)
					{
						if(this.strCurrTrackListQuery === this._strListQuery)
						{
							this._updateJumpRowOffset();
						}
						else
						{
							this.intJumpRowOffset = 0;
						}
						
						this.$.listMedia.punt();
					}
						//this.doRequestCurrTrackInfo();
					
					this.$.empty.hide();

				}
				else
				{
					this.$.content.hide();
					
					this.$.listMedia.punt();
					
					//this.doEnableControls(false, false);
					
					if(this.searchQuery !== "") 
					{
						this.$.empty.hide();								
					}
					else
					{
						this.$.empty.show();
					}
				}
				this._boolReloading = false;
			}
			
			if(inResponse.next)
			{
				this.log("inResponse.next: " + inResponse.next);
				/* Query the next page, using the passed in query */
				//enyo.asyncMethod(this, "listQuery", inRequest.userdata, inResponse.next);
				this.listTimeout = setTimeout(enyo.bind(this, "listQuery"), 50 , inRequest.userdata, inResponse.next, inRequest.objParams);
			}
		

		}
		catch (err)
		{
			this.log("**** gotSongs error: " + err);

		}
		
	},
	onSearchSongs: function (sender, strSearch)
	{
		
		this.log(strSearch);
		this.intJumpRowOffset = 0;

		this.arSongs = [];
		
		if(Utilities.fastTrim(strSearch) === "")
		{
			this.searchQuery = "";
			this.listQuery();
			return;
		}
		this.searchQuery = strSearch;
		this.listQuery();
		return true;
	},
	
	listSetupRow: function (sender, intIndex)
	{
		try
		{
			//this.log("intIndex: " + intIndex);
			
			intIndex = intIndex  + this.intJumpRowOffset;

			if(this.arSongs !== undefined)
			{				
				if(this.arSongs[intIndex] !== undefined)
				{
					//this.log("title: " + this.arSongs[intIndex].title);
					//this.log(this.arSongs[intIndex].title);
					
					//this.$.songNumber.content = intIndex + 1;
					//this.$.songNumber.setStyle("width: " + (this.arSongs.length+'').length * 0.8 + "em");
					this.$.songArtist.content = this.arSongs[intIndex].artist;
					this.$.songTitle.content = this.arSongs[intIndex].title;
					this.$.songAlbum.content = this.arSongs[intIndex].album;
					
					//if (this.arSongs[intIndex].origIndex === this.intCurrTrackOrigIndex) Discontinued use of origIndex to match for highlight
					this.setPlayingStyles(this.arSongs[intIndex]._id, this.$.itemMedia, this._strListQuery);
					
					this.$.itemMedia.addRemoveClass("odd", (intIndex % 2 === 0 ));
					//this.log("finshed");
					//this.$.itemMedia.setContent("-----");
					return true;
				}
			//return true;
			}

	
		}
		catch(err)
		{
			this.log("error: " + err);
		}		
		
		return false;	

	},
	
	
	onclick_Sort: function (objSender)
	{
		if (this.listTimeout){
			clearTimeout(this.listTimeout);
			delete this.listTimeout;
		}
		var boolNewSortAsc;
		
		switch (objSender.name)
		{
				
			case "lblSortArtist":
				
				boolNewSortAsc = this.strSortMode !== "sortKey.artistAlbumDiscAndTrack" || (this.strSortMode === "sortKey.artistAlbumDiscAndTrack" && !this.boolSortAsc);
				this.strSortMode = "sortKey.artistAlbumDiscAndTrack";
				this.boolSortAsc = boolNewSortAsc;
				break;
			
			case "lblSortSong":
				
				boolNewSortAsc = this.strSortMode !== "title" || (this.strSortMode === "title" && !this.boolSortAsc);
				this.strSortMode = "title";
				this.boolSortAsc = boolNewSortAsc;
				break;
			
			case "lblSortAlbum":
				
				boolNewSortAsc = this.strSortMode !== "sortKey.albumDiscAndTrack" || (this.strSortMode === "sortKey.albumDiscAndTrack" && !this.boolSortAsc);
//				turning off until in filenotifyd
				this.strSortMode = "sortKey.albumDiscAndTrack";//  make sure to switch above too
				//this.strSortMode = "album";
				this.boolSortAsc = boolNewSortAsc;
				break;
			
			/*case "lblSortGenre":
				
				boolNewSortAsc = this.strSortMode !== "genre" || (this.strSortMode === "genre" && !this.boolSortAsc);
				this.strSortMode = "genre";
				this.boolSortAsc = boolNewSortAsc;
				break;*/
		}

		this.resetMusicListHeaders();
	
		objSender.addClass("sorted"); 
		objSender.addRemoveClass("asc", boolNewSortAsc ? true : false);
		objSender.addRemoveClass("desc", !boolNewSortAsc ? true : false);

		this.listQuery();

		this.doListChanged(this.arSongs, this.intCurrTrackOrigIndex);
		
	},
	
	resetMusicListHeaders: function ()
	{
		for(var intChildIndex = 0; intChildIndex < this.$.header.children.length; intChildIndex++)
		{
			this.$.header.children[intChildIndex].removeClass("desc");
			this.$.header.children[intChildIndex].removeClass("asc");
			this.$.header.children[intChildIndex].removeClass("sorted");
		}
	},
	
	onclick_listMedia: function (sender, event)
	{
		
		this.log("this.boolSuspendClick: " + this.boolSuspendClick);
		
		if(!this.boolSuspendClick)
		{
			
			var intClickIndex =  event.rowIndex + this.intJumpRowOffset;

			this.log(event.rowIndex);
			this.log(intClickIndex);		
		
			var objSong = this.arSongs[intClickIndex];			
			
			var objSetPlaybackList = {arThumbnails: objSong.thumbnails, 
										arSetPlaybackList: this.arSongs,
										intStartTrackIndex: intClickIndex,
										intStartTrackTime: 0, 
										strOriginListID: this.strCurrListID, 
										strListType: "Song", 
										strListText: objSong.title, 
										strListSubText: objSong.artist,
										strListQuery:	this._strListQuery
									};
			this.doSetPlaybackList(objSetPlaybackList);
			
		}
		else
		{
			this.boolSuspendClick = false;
		}
		
	},
	
	//jumps and punts if forcing or in original list, and song exists
	_jumpIfNeeded: function(objParams){
		//if we are in the original list or are forcing jump
		if((objParams && objParams.forceJump === true) || this.strCurrTrackListQuery === this._strListQuery)
		{
			//if we haven't scrolled yet
			if(this._boolWaitToJump === false)
			{
				if(this._updateJumpRowOffset())
				{
					this.$.listMedia.punt();
					return true;
				}
			}
		}
		return false;
	},
	
	_updateJumpRowOffset: function(){
		if(this._arrIntToSongIndex[this.strCurrTrackID]!==undefined){
			//Don't jump to the bottom 11 songs to prevent empty view
			this.intJumpRowOffset = Math.max(Math.min(this._arrIntToSongIndex[this.strCurrTrackID] - 1, this.arSongs.length - 12), 0);
			return true;
		}
		return false;
	},
	
	highlightTrack: function (objTrackInfo, forceJump)
	{
		try
		{
			if(objTrackInfo)
			{
				this.log(objTrackInfo.intTrackIndex);
				this.intCurrTrack = objTrackInfo.intTrackIndex;
				this.intCurrTrackOrigIndex = objTrackInfo.intTrackOrigIndex;
				this.strCurrTrackID = objTrackInfo.strTrackID;
				this.strCurrTrackListQuery = objTrackInfo.strListQuery;
				
				this.log(this.intCurrTrack);
				if(forceJump || (!this._boolWaitToJump  && this.strCurrTrackListQuery === this._strListQuery)){
					
					//if we updateJumpRowOffset, we're in the active view, and we aren't rendering
					if(this._updateJumpRowOffset(objTrackInfo.strTrackID) && this.boolViewActive && !this._boolRendering)
					{
						this.$.listMedia.punt();
					}
				}
				else
				{
					if(this.boolViewActive && !this._boolRendering)
					{
						this.$.listMedia.refresh();
					}
				}
				this.log("this.intJumpRowOffset", this.intJumpRowOffset);				
			
			}
		

		}
		catch(err)
		{
			this.log("error:" + err);
		}
		
	}
	
	
});
        
