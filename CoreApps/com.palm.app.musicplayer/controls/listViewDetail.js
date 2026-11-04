/*globals enyo, $L, event, Utilities, window, LibraryTools */
enyo.kind({
	name: "listViewDetail",
	kind: "listViewDraggable",

	published: {boolViewActive: false, boolListRendered: false},
	events: {onSetPlaybackList: "", onRequestMedia: "", onRequestCurrTrack: "", onListChanged: "", onListSorted: ""},
	
	
	objCurr: {},
	
	
	intCurrTrack:  -1,
	intCurrTrackOrigIndex:  -1,
	strCurrTrackID: "",
	
	strSortMode: "album",
	
	strCurrListID: "",
	
	boolSortAsc: true,
	
	listViewDetailType: "",//should be set by inheritor
	
	_strListQuery: "",
	
	//Drag & Drop related events	
	create: function () {
		this.inherited(arguments);
		this.intCurrTrack = -1;
		this.intCurrTrackOrigIndex = -1;
		this.boolSortAsc = true;
		this.strSortMode = "album";
		this.strCurrListID = Utilities.generateGuid();
	
	},

	renderList: function (obj)
	{
		this.log();
		this.arSongs = [];
		
		if(obj === undefined)
		{
			obj = this.objCurr;
		}
		if(obj === undefined)
		{
			return;
		}
		
		this.$.ctrlListViewHeader.setIntHeaderCount("");
		//in case exiting msm mode
		//if(this.objCurr !=  obj)
		//{
			this.strCurrListID = Utilities.generateGuid();

		//}
		this.objCurr = obj;
		
		this.setStrHeaderTitle(obj.name);
		
		this.$.listMedia.hide();
		this.listQuery();
			
		
	},
	
	listQuery: function(inSender, inQuery){
		this.log();
		var objGetMediaRequest = {where: [{"prop": "isRingtone", "op": "=", "val": false},{"prop": this.listViewDetailType , "op": "=", "val": this.objCurr.name}], mediaType: "song", callback: enyo.bind(this, "gotSongs")};
		switch(this.listViewDetailType)
		{
			case "genre":
				objGetMediaRequest.desc = !this.boolSortAsc;
				objGetMediaRequest.order = this.strSortMode;
				break;
			case "album":
				objGetMediaRequest.where.push({"prop": "albumArtist", "op": "=", "val": this.objCurr.artist});
				objGetMediaRequest.order = "sortKey.trackAndDisc";
				this.strSortMode = "pos";
				break;
			case "artist":
				objGetMediaRequest.order = "sortKey.albumDiscAndTrack";
				this.strSortMode = "pos";
				break;
			default:
				return;
		}
		
		if(this.strDbNext !== "")
		{
			objGetMediaRequest.page = this.strDbNext;
		}
		
		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);
		
		this.doRequestMedia(objGetMediaRequest);
		
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
	
	
	setStrHeaderTitle: function(name){
		this.$.ctrlListViewHeader.setStrHeaderTitle(name);
	},
	
	gotSongs: function (inResponse, inRequest)
	{
		/*TODO: set strSortMode to whatever its original value is for each list*/
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
			
			this.log("inResponse.count: " + inResponse.count);

			
			if(inResponse.next)
			{
				this.log("inResponse.next: " + inResponse.next);
				this.strDbNext = inResponse.next;
				this.listQuery();
			}
			else
			{
				this.strDbNext = "";
			
				this.$.ctrlListViewHeader.setIntHeaderCount(this.arSongs.length);
				
				this._strListQuery = inRequest.json + inResponse.count;
				
				if(this.arSongs.length > 0)
				{	
					//this.$.lblNumber.setStyle("width: " + (this.arSongs.length +'').length * 0.8 + "em");
					//this.$.content.show();
					this.strCurrAlbum = "";
					this.$.listMedia.show();		
					this.$.listMedia.resized();
					this.$.listMedia.punt();
					this.doRequestCurrTrack();
					
					if(this.objCurr.boolForcePlay)
					{
						this.onclick_listMedia(null, 0);
						this.objCurr.boolForcePlay = false;
					}
					
					//this.$.empty.hide();				
				}
				else
				{
					//this.$.content.hide();
					this.$.listMedia.punt();
					//this.$.listMedia.hide();

					//this.doEnableControls(false, false);
					
					if(this.$.txtResults && this.objCurr.boolShowContent)
					{
						this.$.txtResults.setContent($L("No " + this.listViewDetailType + " found"));
					}
					
				}
			}
		
			
			
			
			this.boolListRendered = true;

		}
		catch (err)
		{
			this.log("**** gotSongs error: " + err);

		}
		
	},
	
	onclick_Sort: function(objSender, event){
		try
		{
			
			var objFirstSong = this.arSongs[event.rowIndex];
			
			this.log("onclick_Sort: " + objSender.name + " - " + this.boolSortAsc);

			//this.$.txtResults.setContent("onclick_Sort<br/>" + objSender.name  + "<br/>");
			this.log("curr sort mode " + this.strSortMode);
			
			var boolNewSortAsc;

			var arAlbumSongs = this.arSongs;
				

			//pull out the specific album if in artist mode
			var endIndex;
			if(this.listViewDetailType === "artist"){
				for(endIndex = event.rowIndex; endIndex<this.arSongs.length; endIndex++){
					this.log(endIndex, this.arSongs[endIndex]);
					if(this.arSongs[endIndex].album !== objFirstSong.album){
						break;
					}
				}
				arAlbumSongs = this.arSongs.slice(event.rowIndex, endIndex);
			}
			
			//pull out sortType from name ***MUST BE lblSortSong lblSortPos lblSortArtist or lblSortAlbum
			var sortType = objSender.name.substr(7).toLowerCase();
			if(sortType === "song")
			{
				sortType = "title";
			}
			if(sortType !== "title" && sortType !== "pos" && sortType !== "artist" && sortType !== "album")
			{
				return;
			}
		
			boolNewSortAsc = this.strSortMode !== sortType || (this.strSortMode === sortType && !this.boolSortAsc);
			this.strSortMode = sortType;
			this.boolSortAsc = boolNewSortAsc;
			
			
			this.log("set to " + this.boolSortAsc);
				
			this.resetMusicListHeaders();
			objSender.addClass("sorted"); 
			objSender.addRemoveClass("asc", boolNewSortAsc);
			objSender.addRemoveClass("desc", !boolNewSortAsc);			
			
			if(this.listViewDetailType === "genre")
			{
				this.listQuery();
			}
			else
			{
				LibraryTools.sort(this.strSortMode, arAlbumSongs, boolNewSortAsc);
				//putted sorted list back into arSongs
				
				if(this.listViewDetailType === "artist")
				{
					var args = [event.rowIndex, endIndex-event.rowIndex].concat(arAlbumSongs);
					Array.prototype.splice.apply(this.arSongs, args);
				}
				else
				{
					this.arSongs = arAlbumSongs;
				}
				
				this.$.listMedia.refresh();
				
			}
			this.strCurrListID = Utilities.generateGuid();
			
			this.doListChanged(this.arSongs, this.intCurrTrackOrigIndex);
			
		}
		catch (err)
		{
			this.log("**** onclick_Sort error: " + err);
		}
	},
	
	updateUISongInfo: function(objSong){
		if(typeof objSong === "undefined"){
			return;
		}
		if (Utilities.isNumeric(objSong.track.position) && objSong.track.position > 0)
		{
			this.$.songTrack.setContent(objSong.track.position);
		}
		else
		{
			this.$.songTrack.setContent("-");					
		}
		
		this.$.songTitle.setContent(objSong.title);
	},
	
	
	resetMusicListHeaders: function ()
	{
		for(var intChildIndex = 0; intChildIndex < this.$.header.children.length; intChildIndex++)
		{
			this.$.header.children[intChildIndex].addClass("column");
			this.$.header.children[intChildIndex].removeClass("sorted");
			this.$.header.children[intChildIndex].removeClass("asc");
			this.$.header.children[intChildIndex].removeClass("desc");
		}
	},
	
	onclick_listMedia: function (sender, event)
	{
		
		this.log(event.rowIndex);
			
		var intClickIndex =  event.rowIndex;
		
		var objSetPlaybackList =	{arSetPlaybackList	: this.arSongs, 
									intStartTrackIndex	: intClickIndex, 
									intStartTrackTime	: 0, 
									strOriginListID		: this.strCurrListID, 
									strListType			: Utilities.capitalize(this.listViewDetailType), 
									strListText			: this.objCurr.name, 
									strListSubText		: "", 
									strArtist			: this.objCurr.artist,
									strListQuery		: (this._strListQuery + this.strSortMode),
									arThumbnails		: this.objCurr.thumbnails};
		this.doSetPlaybackList(objSetPlaybackList);
		
	}
});