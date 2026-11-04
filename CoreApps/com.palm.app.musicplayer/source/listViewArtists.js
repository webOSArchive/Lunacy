/*globals enyo, $L, event, Utilities, window, console*/
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewArtists",
	kind: "listViewDraggable",
	className: "playlist",
	published: {boolViewActive: false},
	events: {onSetPlaybackList: "", onRequestMedia: "", onListChanged: "", onListSorted: "", onSelectArtist: "", onRequestPlaylists: "", onShowBanner: ""},
	height: "100%",
	components: [
		// the header
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchArtists", onSetMultiSelect: "onMultiSelect", boolShowMultiSelect: false},
		// the content
		{name: "content", kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
			// the list
			{kind: "Control", layoutKind: "HFlexLayout", flex: 1, components: [
				{name: "listMedia", kind: "VirtualList", onSetupRow: "listSetupRow", className: "listMedia", flex: 1,	 components: [
					// the divider
					{name: "header", kind: "Control", className: "divider", style: "margin-top: -1px;", components: [{name: "divider", kind: "Control", className: "column"}]},
					// the item
					{name: "itemMedia", kind: "draggableArtist", className: "artist", layoutKind: "HLayout", width: "100%", onclick: "onclick_listMedia", components: [ 
						{className: "column play", onclick: "onclick_playArtist", components: [
							{name: "imgPLay", kind: "ToolButton", icon: "images/bg_item_play.png"}
						]},
						{name: "covers", className: "column cover"/*, onclick: "onclick_imgMenu"*/, components: [{name: "imgContainer", className: "img", showing: false, components: [{name: "imgThumb", kind: "Image", onerror: "onError_imgThumb"}]}]},
						{kind: "Control", style: "vertical-align: middle;", components: [ //temp hack for HLayout
							{kind: "Control", layoutKind: "HLayout", components: [
								{name: "artistName", className: "column name", content: ""},
								{name: "songCount", className: "column count", content: ""}
							]},
							{name: "albumCount", className: "column", content: "2 albums"}
						]}
					]}
				]}
			]}
		]},
		// the empty view
		{name: "empty", kind: "Control", layoutKind: "VFlexLayout", align:"center", height:"100%", pack: "justify", className: "empty", showing: false, components: [
			{kind: "Spacer", flex: 1},
			{kind: "Control", className: "content", components: [
			  {kind: "Image", src: "images/bg_empty_artists.png"},
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
	
	intClickIndex: -1,
	
	strCurrListID: "",
	
	strSortMode: "name",
	boolSortAsc: true,
	
	boolMultiSelect: false,
	
	boolNotifyListChange: false,
	boolNeedRendered: true,
	
	objAlphaJumps: {},
	
		
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
		//this.$.listMedia.setPageSize(20);

		this.local_tracks_Templ = new enyo.g11n.Template($L("(#{num} songs)"));
		this.local_track_Templ = new enyo.g11n.Template($L("(#{num} song)"));
		
		this.local_albums_Templ = new enyo.g11n.Template($L("#{num} Albums"));
		this.local_album_Templ = new enyo.g11n.Template($L("#{num} Album"));
		
	},
		
	ready: function () {

		try
		{
			this.inherited(arguments);

			this.$.ctrlListViewHeader.setStrHeaderTitle($L("Artists"));
			
			
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
		this.log();
		
		this.$.ctrlListViewHeader.clearSearchInput(false);		

		this.intJumpRowOffset = 0;
		
		this.listQuery();
		
	},
	
	
	refreshList: function ()
	{
		this.log();
		
		this.$.ctrlListViewHeader.clearSearchInput(false);	
		
		this.intJumpRowOffset = 0;
		//this.getPlaylists();
		if(this.boolNeedRendered){
			this.renderList();	
		}//else{
			//Not neccessary, only causes performance issues
			//this.$.listMedia.refresh();
		//}
		
		
	},

	resizeList: function ()
	{
		this.log();
		this.$.listMedia.resized();
	},
	
	listQuery: function (sender, inQuery)
	{
		this.log();
	
		var objGetMediaRequest = {where: [], mediaType: "artist", /*queryType: "search",*/ order: this.strSortMode, callback: enyo.bind(this, "gotArtists")};

		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);
		
		this.doRequestMedia(objGetMediaRequest);
		
	},
	
	

	gotArtists: function (inResponse, inRequest)
	{
		this.log();
		
		this.log(inResponse);

		try
			
		{
			
			this.arArtists = [];
			
			var objGetMediaRequest = inRequest.objGetMediaRequest;
			if(inResponse.results.length > 0)
			{
				
				
				
				this.log("**** gotArtists");
				this.log("count: ", inResponse.results.length);					
				
				this.log("--: ", inResponse.results[0].name);
				
				
						
				if(objGetMediaRequest.page)
				{
					this.arArtists = this.arArtists.concat(inResponse.results);
				}
				else
				{
					this.arArtists = inResponse.results.slice(0);
			
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
				if(objGetMediaRequest.queryType === "search" && objGetMediaRequest.where[0] && objGetMediaRequest.where[0].val.length > 0)
				{
					this.boolNeedRendered = true;
				}
				
				if(this.arArtists)
				{
					this.$.ctrlListViewHeader.setIntHeaderCount(this.arArtists.length);
								
					if (this.arArtists.length > 0) {
						this.$.content.show();
						this.$.empty.hide();
						
						
						for (var intTrackIndex = 0; intTrackIndex < this.arArtists.length;  intTrackIndex++)
						{
							
							
							this.log(this.arArtists[intTrackIndex].name);
							//console.log("**--  " + enyo.json.to(this.arArtists[intTrackIndex]));
							this.arArtists[intTrackIndex].origIndex = intTrackIndex;
							
							
							var charPrevLabelAlpha = null;
							
							if(this.arArtists[intTrackIndex-1])
							{
								charPrevLabelAlpha = this.arArtists[intTrackIndex-1].name.charAt(0).toUpperCase();
								
							}
						
						}
						
						
						
						this.$.listMedia.resized() ;
						this.$.listMedia.punt();			
					}
					else
					{
						this.$.content.hide();
						this.$.listMedia.punt();
	
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
	
					}
								
				}
				
			}
			
			
		}
		catch (err)
		{
			this.log("**** gotArtists error: " + err);

		}
		
	},
	
	
	
	

	onSearchArtists: function (sender, strSearch)
	{
		
		this.log(strSearch);
		this.intJumpRowOffset = 0;
		
		this.arArtists = [];
		
		if(Utilities.fastTrim(strSearch) === "")
		{
			this.listQuery();
			return true;
		}
		
		var objGetMediaRequest = {where: [{"prop": "name", "op": "%", "val": strSearch, "collate": "primary"}], queryType: "search", mediaType: "artist", order: "name", callback: enyo.bind(this, "gotArtists")};
			
		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);		
		
		this.doRequestMedia(objGetMediaRequest);
		return true;
		//this.arSearchResults = this.arArtists.filter(this.searchFilter);
		
	},
	
	
	listSetupRow: function (sender, intIndex)
	{
		try
		{
			this.log(intIndex);
			this.log(intIndex + this.intJumpRowOffset);
			
			if(this.arArtists !== undefined)
			{
				if(intIndex + this.intJumpRowOffset < this.arArtists.length)
				{
					if(this.arArtists[intIndex + this.intJumpRowOffset] !== undefined)
					{
						var objArtist = this.arArtists[intIndex + this.intJumpRowOffset];
						
						this.log(objArtist.name);
					
						this.$.artistName.content = objArtist.name;
						
						if(objArtist.name.length > 0)
						{
							var charPrevLabelAlpha = null;
							
							if(this.arArtists[intIndex + this.intJumpRowOffset-1])
							{
								charPrevLabelAlpha = Utilities.getBaseLabelChar(this.arArtists[intIndex + this.intJumpRowOffset -1].name);
							}
							
							var strCurrLabelAlpha = Utilities.getBaseLabelChar(objArtist.name);
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
								this.$.header.hide();
							}						
							
							
						}
						
						if(objArtist.total.albums !== 1)
						{
							this.$.albumCount.content =  this.local_albums_Templ.evaluate({num: objArtist.total.albums});
						}
						else
						{
							this.$.albumCount.content = this.local_album_Templ.evaluate({num: objArtist.total.albums});

						}
						
						
						
						if(objArtist.total.tracks !== 1)
						{
							this.$.songCount.content = this.local_tracks_Templ.evaluate({num: objArtist.total.tracks});
						}
						else
						{
							this.$.songCount.content = this.local_track_Templ.evaluate({num: objArtist.total.tracks});
						}
						
						var objThumb = Utilities.getItemThumb(objArtist.thumbnails);
						
						
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
							
							objArtist.strThumbURL = objThumb.strThumbURL;
							
							this.$.covers.addRemoveClass("many", (objThumb.intThumbsTotalCount > 1));
							
							this.$.imgContainer.setShowing(objThumb.intThumbsTotalCount > 0);
							
						}
						else
						{
							this.$.imgContainer.hide();
						}
							
						this.$.itemMedia.addRemoveClass("odd", ((intIndex + this.intJumpRowOffset) % 2 === 0 ));
						
						return true;
					}
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
			this.callSelectArtist(event.rowIndex);			
		}
		else
		{
			this.boolSuspendClick = false;
		}		
		
		
		
		
	},
	
	callSelectArtist: function (intIndex, boolForcePlay)
	{
		var objClickedArtist = this.arArtists[intIndex + this.intJumpRowOffset];
		objClickedArtist.boolForcePlay = boolForcePlay;
		this.doSelectArtist(objClickedArtist);		
	},	

	onclick_playArtist: function (sender, event)
	{
		
		this.log();
		//this.callPlayArtist(this.$.listMedia.fetchRowIndex());
		this.callSelectArtist(this.$.listMedia.fetchRowIndex(), true);
		return true;
	},
	
	callPlayArtist: function (intIndex)
	{
		
		this.log();
		
		var objClickedArtist = this.arArtists[intIndex + this.intJumpRowOffset];
						
		var objThumb = Utilities.getItemThumb(objClickedArtist.thumbnails, 54);

		var objGetMediaRequest = {where: [{"prop": "isRingtone", "op": "=", "val": false},{"prop": "artist", "op": "=", "val": objClickedArtist.name}], order: "sortKey.trackAndDisc", mediaType: "song",  boolForcePlay: true, playFirst: false, strListType: "Artist", strListText: objClickedArtist.name};
		
		this.log("**** raising doRequestMedia for clicke artist");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);
		
		this.doRequestMedia(objGetMediaRequest);			
	},
	

	
	onclick_imgMenu: function (sender,event)
	{
		this.log();
		//this.log(sender);
		////this.log(event);
		this.intClickIndex = event.rowIndex;
		
		//this.$.mitemAddToPlaylist.setOpen(false);
		//this.$.popMenuAction.$.client.setScrollTop(0);
		//this.$.popMenuAction.openAtEvent(event, {left: 0});
		
		
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
				this.callPlayArtist(this.intClickIndex);
				break;
			
			case "mitemView":
				this.callSelectArtist(this.intClickIndex);
				break;
			
			default:
				this.callAddToPlaylist(this.intClickIndex, origSender.plid);
				break;
		}
		
	},

	
	onMultiSelect: function ()
	{
		this.log();
		
		this.boolMultiSelect = !this.boolMultiSelect;
		
		this.$.listMedia.setMultiSelect(this.boolMultiSelect);
		
	},
	
	
	onError_imgThumb: function (sender, event)
	{
		this.error();
		this.log(sender.getSrc());
		this.$.imgContainer.setShowing(false);
	}
	
	
});