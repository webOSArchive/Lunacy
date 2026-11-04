/*globals enyo, $L, event, Utilities, window */
// UI for display of tracks retrieved from
enyo.kind({
	name: "kindListViewGenres",
	kind: "listViewDraggable",
	published: {boolViewActive: false, intColumns: 4},
	events: {onSetPlaybackList: "", onRequestMedia: "", onListChanged: "", onListSorted: "", onSelectGenre: "", onRequestPlaylists: "", onShowBanner: ""},
	height: "100%",
	components: [
		// the header
		{kind: "ctrlListViewHeader", onSearch_Header: "onSearchGenres"},
		// the content
		{name: "content", kind: "Control", layoutKind:"VFlexLayout", className: "list", flex: 1, components: [
		  // the list
		  {name: "listMedia", kind: "VirtualList", onSetupRow: "listSetupRow", flex: 1, height: "100%", components: [
			{name: "itemRow", style: "background:#F7F7F7;", kind: "Control", layoutKind: "HFlexLayout"}/*,
			{name: "itemGenre", added below*/
		  ]}
		]},
		// the empty view
		{name: "empty", kind: "Control", layoutKind: "VFlexLayout", align:"center", height:"100%", pack: "justify", className: "empty", showing: false, components: [
			{kind: "Spacer", flex: 1},
			{kind: "Control", className: "content", components: [
			  {kind: "Image", src: "images/bg_empty_genres.png"},
			  {content: $L("There are no songs on your HP TouchPad"), className: "heading"},
			  {content: $L("You can add music to your HP TouchPad with the HP Play music app, found at www.hpplay.com, or copy music to your HP TouchPad in USB mode.")}
			]},
			{kind: "Button", caption: $L("Add songs"), showing: false},
			{kind: "Spacer", flex: 3}		
		]}/*,
		{name: "txtResults"}*/

    ],
	intCurrTrack:  -1,
	intCurrTrackOrigIndex:  -1,
	strCurrListID: "",
	
	strSortMode: "name",
	boolSortAsc: true,
	
	boolNotifyListChange: false,
	
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
		//this.$.listMedia.setPageSize(200);
		
		this.local_tracks_Templ = new enyo.g11n.Template($L("#{num} songs"));
		this.local_track_Templ = new enyo.g11n.Template($L("#{num} song"));
		
	},
		
	ready: function () {

		try
		{

		this.$.ctrlListViewHeader.setStrHeaderTitle($L("Genres"));
		
		}
		catch (err)
		{
		
			this.log("**** musiclibrary ready error: " + err);
		}
			
	},
	
	
	rendered: function()
	{
		this.inherited(arguments);
		//this.buildCells();
	},
	
	
	renderList: function ()
	{
		this.$.ctrlListViewHeader.clearSearchInput(false);		
		this.buildCells();
		this.listQuery();
		
	},
	
	
	refreshList: function ()
	{
		
		this.$.ctrlListViewHeader.clearSearchInput(false);		
		//this.$.listMedia.refresh();
		//this.$.listMedia.update();
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
	
		var objGetMediaRequest = {where: [], mediaType: "genre", order: this.strSortMode, /*queryType: "search",*/ callback: enyo.bind(this, "gotGenres")};
		
		if(this.strDbNext !== "")
		{
			objGetMediaRequest.page = this.strDbNext;
		}		

		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);
		
		this.doRequestMedia(objGetMediaRequest);
		
	},


	gotGenres: function (inResponse, inRequest)
	{
		this.log();

		try
			
			{
			this.arGenres = [];
			this.log("**** gotGenres");
			this.log("count: " + inResponse.results.length);
			var objGetMediaRequest = inRequest.objGetMediaRequest;
			
			if(inResponse.results.length > 0)
			{
				
				this.log("--: " + inResponse.results[0].name);
				
				
				if(objGetMediaRequest.page)
				{
					this.arGenres = this.arGenres.concat(inResponse.results);
				}
				else
				{
					this.arGenres = inResponse.results.slice(0);
			
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
				
				this.$.ctrlListViewHeader.setIntHeaderCount(this.arGenres.length);
				
				
				if(this.arGenres.length > 0)
				{	
					
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
					

				//this.$.txtResults.setContent("No Genres Found");
				}
			}
		
			
			
			
			this.boolListRendered = true;

		}
		catch (err)
		{
			this.log("**** gotGenres error: " + err);

		}
		
	},
	

	onSearchGenres: function (sender, strSearch)
	{
		
		this.log(strSearch);

		this.arGenres = [];
		
		if(Utilities.fastTrim(strSearch) === "")
		{
			this.listQuery();
			return true;
		}
		
		var objGetMediaRequest = {where: [{"prop": "name", "op": "%", "val": strSearch, "collate": "primary"}], queryType: "search", mediaType: "genre", order: "name", callback: enyo.bind(this, "gotGenres")};
			
		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetMediaRequest);		
		
		this.doRequestMedia(objGetMediaRequest);
		return true;
		//this.arSearchResults = this.arSongs.filter(this.searchFilter);
		
		
	},
	
	intColumnsChanged: function (oldVal)
	{
		//this prevents unnessesary build and lists
		if(this.intColumns === oldVal)
		{
			return;
		}
		this.log(this.boolViewActive); 
		if(this.boolViewActive)
		{
			//this.boolNeedRendered = true;
			this.buildCells();
			this.listQuery();
		}else{
			this.boolNeedRendered = true;
		}
	},
	
	buildCells: function() {
	
		this.$.itemRow.destroyControls();
		
		this.cells = [];
		for (var i=0; i<this.intColumns; i++) {
			var ctrlItemGenre = this.createComponent({kind: "draggableGenre", onclick: "onclick_listMedia", intColumnIndex: i});

			ctrlItemGenre.setParent(this.$.itemRow);
			ctrlItemGenre.setContainer(this.$.itemRow);			

			this.cells.push(ctrlItemGenre);
		}
	},	
	
	listSetupRow: function (sender, intIndex)
	{
		
		
		try
		{
			this.log(intIndex);
			
			var intGenreIndex = intIndex * this.intColumns;
			
			if (intGenreIndex >= 0 && intGenreIndex < this.arGenres.length)
			{
				for (var i=0; i < this.intColumns; i++)
				{
					this.log("intGenreIndex: " + intGenreIndex);
					var itemGenre = this.cells[i];
					
					//Start draw cell
					if(this.arGenres !== undefined)
					{				
						if(this.arGenres[intGenreIndex] !== undefined)
						{
							
							var objGenre = this.arGenres[intGenreIndex];
							
							this.log(objGenre.name);
						
							itemGenre.setStrItemText(objGenre.name);
							
							itemGenre.setStrItemSubText(this.local_track_Templ.evaluate({num: objGenre.total.tracks}));
							
							if(objGenre.total.tracks !== 1)
							{
								itemGenre.setStrItemSubText(this.local_tracks_Templ.evaluate({num: objGenre.total.tracks}));
							}
							
							itemGenre.setIntThumbCount(objGenre.total.tracks);
							
							var objThumb = Utilities.getItemThumb(objGenre.thumbnails, 88);
						
							objGenre.strThumbURL = objThumb.strThumbURL;
							
							this.log("objGenre.strThumbURL: " + objGenre.strThumbURL);
							
							itemGenre.setStrImageURL(objGenre.strThumbURL);

							
							itemGenre.setBoolLandscape(this.intColumns === 4);
							itemGenre.show();
	
						}
						else
						{
							itemGenre.hide();
						}
					
					}					
					//End draw cell
					intGenreIndex++;
					
				}
				return true;
			}
			else
			{
				
				return false;
				
			}
			
		}
		catch(err)
		{
			this.log("error: " + err);
			return false;
		}		
		
	

	},

	
	onclick_listMedia: function (sender, event)
	{
		this.log();
		
		if(!this.boolSuspendClick)
		{
			var intClickIndex = (this.$.listMedia.fetchRowIndex() * this.intColumns) + sender.intColumnIndex;
			this.log("intClickIndex: " + intClickIndex);
		
			var objClickedGenre = this.arGenres[intClickIndex];
			
			this.doSelectGenre(objClickedGenre);
		}
		else
		{
			this.boolSuspendClick = false;
		}				
		

	},
		
	setItemHighlighted: function(inHighlight)
	{
		//should do nothing (overwrites kinds inheritance)
	}
});