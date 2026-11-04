/*globals enyo, $L, event, Utilities, window */
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewAlbums",
	kind: "listViewDraggable",
	published: {boolViewActive: false},
	className: "artists",
	events: {onSetPlaybackList: "", onRequestMedia: "", onListChanged: "", onListSorted: "", onSelectAlbum: "", onRequestPlaylists: ""},
	height: "100%",
	components: [
		// the header
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchAlbums"},
		// the content
		{name: "content", kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			// the list
			{name: "listMedia", kind: "VirtualList", onQuery: "listQuery", onSetupRow: "listSetupRow", className: "listMedia", flex: 1, components: [
				// the divider
				{name: "header", kind: "Control", className: "divider", style: "margin-top: -1px;", components: [{name: "divider", kind: "Control", className: "column"}]},
				// the item
				{name: "itemMedia", kind: "draggableAlbumGroup", className: "album", layoutKind: "HFlexLayout", align: "center", onclick: "onclick_listMedia", components: [
					{className: "column play", onclick: "onclick_playAlbum", components: [
						{name: "imgPLay", kind: "ToolButton", icon: "images/bg_item_play.png"}
					]},
					{name: "covers", className: "column cover", components: [{name: "imgContainer", className: "img", showing: false, components: [{name: "imgThumb", kind: "Image", onerror: "onError_imgThumb"}]}]},
					{kind: "Control", components: [
						{kind: "Control", layoutKind: "HFlexLayout", components: [
							{name: "albumName", className: "column name",  content: ""},
							{name: "albumCounts", className: "column count", content: ""} 
						]},
						{name: "albumArtist", className: "column", content: ""}
					]}
				]}	
			]}
		]},
		// the empty view
		{name: "empty", kind: "Control", layoutKind: "VFlexLayout", align:"center", height:"100%", pack: "justify", className: "empty", showing: false, components: [
			{kind: "Spacer", flex: 1},
			{kind: "Control", className: "content", components: [
			  {kind: "Image", src: "images/bg_empty_albums.png"},
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
	
	strCurrListID: "",
	
	strSortMode: "name",
	boolSortAsc: true,
	
	boolNotifyListChange: false,
	
	boolDragHold: false,
	boolDragItem: false,
	boolDraggingList: false,
	boolNeedRendered: true,
	
	create: function () {
		//test
		
		try
		{
			this.inherited(arguments);
			this.strSortMode = "name";
			this.boolSortAsc = true;
			this.strCurrListID = Utilities.generateGuid();
			
		}
		catch (err)
		{
			this.log("**** create error: " + err);
		}

		//this.$.listMedia.update();
		//this.$.listMedia.setPageSize(30);
		
		this.local_tracks_Templ = new enyo.g11n.Template($L("(#{num} songs)"));
		this.local_track_Templ = new enyo.g11n.Template($L("(#{num} song)"));
		
	},
		
	ready: function () {

		try
		{
			this.inherited(arguments);
	
			this.$.ctrlListViewHeader.setStrHeaderTitle($L("Albums"));
			
		}
		catch (err)
		{
			this.log("**** musiclibrary ready error: " + err);
		}
			
	},
	
	
	rendered: function()
	{
		this.inherited(arguments);
		
	},
	
	
	renderList: function ()
	{
		this.$.ctrlListViewHeader.clearSearchInput(false);
		
		this.listQuery();
		
	},
	
	resizeList: function ()
	{
		this.log();
		this.$.listMedia.resized();
	},
	
	
	refreshList: function ()
	{
		this.$.ctrlListViewHeader.clearSearchInput(false);
		
		//this.listQuery();
		//this.$.listMedia.update();
		if(this.boolNeedRendered){
			this.renderList();	
		}//else{
			//Not neccessary, only causes performance issues
			//this.$.listMedia.refresh();
		//}
	},

	listQuery: function (sender, inQuery)
	{ 
		this.log();
	
		var objGetMediaRequest = {where: [], mediaType: "album", /*queryType: "search",*/ order: this.strSortMode, callback: enyo.bind(this, "gotAlbums")};
		
		if(this.strDbNext !== "")
		{
			objGetMediaRequest.page = this.strDbNext;
		}	

		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);
		
		this.doRequestMedia(objGetMediaRequest);
		
	},


	gotAlbums: function (inResponse, inRequest)
	{
		this.log();

		try
			
			{
			this.arAlbums = [];
			this.log("**** gotAlbums");
			this.log("count: " + inResponse.results.length);
			var objGetMediaRequest = inRequest.objGetMediaRequest;
			
			if(inResponse.results.length > 0)
			{
				
				this.log("--: " + inResponse.results[0].title);
				
				
				if(objGetMediaRequest.page)
				{
					this.arAlbums = this.arAlbums.concat(inResponse.results);
				}
				else
				{
					this.arAlbums = inResponse.results.slice(0);
			
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
				
				this.boolNeedRendered = false;
				if(objGetMediaRequest.queryType === "search"  && objGetMediaRequest.where[0] && objGetMediaRequest.where[0].val.length > 0)
				{
					this.boolNeedRendered = true;
				}
				
				this.$.ctrlListViewHeader.setIntHeaderCount(this.arAlbums.length);
				
				
				if(this.arAlbums.length > 0)
				{	
					//this.$.lblNumber.setStyle("width: " + (this.arAlbums.length +'').length * 0.8 + "em");
					this.$.content.show();
			
					this.$.listMedia.resized();
					this.$.listMedia.punt();
					
					this.$.empty.hide();				
				}
				else
				{
					this.$.content.hide();
					this.$.listMedia.punt();
					//this.$.listMedia.hide();

					//this.doEnableControls(false, false);
					
					
					if(objGetMediaRequest)
					{

						
						if(objGetMediaRequest.queryType)
						{
							if(objGetMediaRequest.queryType === "search")
							{
								this.$.empty.hide();								
							}
							else
							{
								this.$.empty.show();
							}
						}
						else
						{
							this.$.empty.show();								
						}
					}
					

				//this.$.txtResults.setContent("No Albums Found");
				}
			}
		
			
			
			
			this.boolListRendered = true;

		}
		catch (err)
		{
			this.log("**** gotAlbums error: " + err);

		}
		
	},



	onSearchAlbums: function (sender, strSearch)
	{
		
		this.log(strSearch);
		
		this.arAlbums = [];
		
		if(Utilities.fastTrim(strSearch) === "")
		{
			this.listQuery();
			return true;
		}
		
		var objGetMediaRequest = {where: [{"prop": "name", "op": "%", "val": strSearch, "collate": "primary"}], queryType: "search", mediaType: "album", order: "name", callback: enyo.bind(this, "gotAlbums")};
			
		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);	
		
		this.doRequestMedia(objGetMediaRequest);
		return true;
		//this.arSearchResults = this.arSongs.filter(this.searchFilter);
		
		
	},
	
	
	listSetupRow: function (sender, intIndex)
	{
		try
		{
			this.log(intIndex);
			
			if(this.arAlbums !== undefined)
			{				
				if(this.arAlbums[intIndex] !== undefined)
				{
					var objAlbum = this.arAlbums[intIndex];
					
					this.log(objAlbum.name);
				
					this.$.albumName.content = objAlbum.name;
					this.$.albumArtist.content = objAlbum.artist;					
					
					if(objAlbum.name.length > 0)
					{
						var charPrevLabelAlpha = null;
						
							if(this.arAlbums[intIndex-1])
							{
								charPrevLabelAlpha = Utilities.getBaseLabelChar(this.arAlbums[intIndex-1].name);
							}
							
							var strCurrLabelAlpha = Utilities.getBaseLabelChar(objAlbum.name);
							if(strCurrLabelAlpha !== charPrevLabelAlpha || charPrevLabelAlpha === null)
							{
								
								
								this.log("Divider: " + strCurrLabelAlpha);							
								this.$.divider.setContent(strCurrLabelAlpha);
								
								//if(intIndex === 0)
								//{
								//	this.$.header.addClass("first");
								//}
								
								this.$.header.show();
								
								
							}
							else
							{
								//this.$.labelAlpha.content = "";
								this.$.header.hide();
				
							}										
						
						
					}
					this.$.albumCounts.content = this.local_track_Templ.evaluate({num:objAlbum.total.tracks});
					
					if(objAlbum.total.tracks !== 1)
					{
						this.$.albumCounts.content = this.local_tracks_Templ.evaluate({num:objAlbum.total.tracks});
					}

					var objThumb = Utilities.getItemThumb(objAlbum.thumbnails);
					
					
					if(objThumb)
					{

						if(objThumb.intThumbsTotalCount > 0)
						{
							this.$.imgThumb.setSrc(objThumb.strThumbURL);
							this.$.imgContainer.show();
						}
						else
						{
							this.$.imgContainer.hide();
						}
						
						objAlbum.strThumbURL = objThumb.strThumbURL;
						
						this.$.covers.addRemoveClass("many", (objThumb.intThumbsTotalCount > 1));
						
						this.$.imgContainer.setShowing(objThumb.intThumbsTotalCount > 0);
						
					}
					else
					{
						this.$.imgContainer.hide();
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
	
	onclick_listMedia: function (sender, event)
	{
		
		this.log();
		if(!this.boolSuspendClick)
		{		
			this.callSelectAlbum(event.rowIndex);
		}
		else
		{
			this.boolSuspendClick = false;
		}
		
	},
	

	callSelectAlbum: function (intIndex, boolForcePlay)
	{
		var objClickedAlbum = this.arAlbums[intIndex];
		objClickedAlbum.boolForcePlay = boolForcePlay;
		this.doSelectAlbum(objClickedAlbum);		
	},	

	onclick_playAlbum: function (sender, event)
	{
		this.log();
		//this.callPlayAlbum(this.$.listMedia.fetchRowIndex());
		this.callSelectAlbum(this.$.listMedia.fetchRowIndex(), true);
		return true;
	},
	
	callPlayAlbum: function (intIndex)
	{
		
		this.log();
		
		var objClickedAlbum = this.arAlbums[intIndex];
		var objThumb = Utilities.getItemThumb(objClickedAlbum.thumbnails, 54);
		var objGetMediaRequest = {where: [{"prop": "isRingtone", "op": "=", "val": false},{"prop": "album", "op": "=", "val": objClickedAlbum.name},{"prop": "albumArtist", "op": "=", "val": objClickedAlbum.artist}],
								order: "sortKey.trackAndDisc", mediaType: "song",  boolForcePlay: true, strListType: "Album", strListText: objClickedAlbum.name, /*strListSubText: objClickedAlbum.artist,*/
								strArtist: objClickedAlbum.artist, playFirst: false};

		
		this.log("**** raising doRequestMedia for clicke album");
		this.log("**** objGetMediaRequest: " + enyo.json.stringify(objGetMediaRequest));
		
		this.doRequestMedia(objGetMediaRequest);			
	},

	/*
	onclick_imgMenu: function (sender,event)
	{
		this.log();
		this.intClickIndex = event.rowIndex;
		this.$.MediaItemMenu.openMenu();		
		return true;
		
	},
		
	

	
	onMenuClick: function (sender, origSender)
	{
		
		this.log();
		this.log(sender);
		this.log(origSender);
		
		this.log(origSender.name);
		this.log(origSender.plindex);
		
		switch(origSender.name)
		{
			case "mitemPlay":
				this.callPlayAlbum(this.intClickIndex);
				break;
			
			case "mitemView":
				this.callSelectAlbum(this.intClickIndex);
				break;
			
			default:
				this.callAddToPlaylist(this.intClickIndex, origSender.plid);
				break;
			
				
			
		}
		
	
	},	
	*/
	
	onError_imgThumb: function (sender, event)
	{
		this.error();
		this.log(sender.getSrc());
		//this.$.imgContainer.setShowing(false);
	}

	
});