/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "ScrollTracks",
	kind: enyo.SnapScroller,
	dragstartHandler: function(inSender, e) {
		if (this.dragging || this.snapping) {
			return true;
		}
		return this.inherited(arguments);
	},
	dragHandler: function(inSender, e) {
		if (this.snapping) {
			return true;
		}
		return this.inherited(arguments);
	},
	flickHandler: function(inSender, e) {
		//this.log(e.xVel);
		if (this.snapping) {
			return true;
		}
		return this.inherited(arguments);
	}
});



enyo.kind({
	name: "KindAlbumArtView",
	kind: "Control",
	className: "flow",
	events: {onRequestTracks: "", onClickAlbumArtView: ""},
	components: [
		{name:"scrollTracks", kind: "ScrollTracks", className: "scrollH", layoutKind: "HFlexLayout", autohorizontal: true, horizontal: true, autovertical: false, vertical: false, onSnap: "onSnap_scrollTracks", onSnapFinish: "onSnapFinish_scrollTracks", onScrollStart: "onScrollStart_scrollTracks", onScrollStop: "onScrollStop_scrollTracks", showing: true},
		{kind: "Control", name: "shadowMask", className: "mask", style: "-webkit-palm-mouse-target:ignore;"}
	],
	
	_arFullTrackList: [],
	
	intCurrSnapPos: 0,
	intCurrListMin: 0,
	intCurrListMax: 0,
	intCurrListIndex: 0,
	
	boolGotTracks: false,
	
	boolScrollerDrawn: false,
	
	intCurrTrackIndex: -1,
	
	boolAudioPlaying: false,
	
	boolUpdateScroller: false,
	
	boolLandscape: true,
	
	create: function()
	{
		this.inherited(arguments);
		this.index = 1;
		
		//this.$.scrollTracks.$.scroll.kFrictionEpsilon = 1.0;
		
		//this.$.scrollTracks.$.scroll.kSpringDamping = 0.1;  // lower makes snapping faster
		//this.$.scrollTracks.$.scroll.kFrictionDamping = 1.0;
	},
	
	orientationChanged: function(boolLandscape)
	{	
		this.log(boolLandscape);
		
		
		if(this.boolLandscape !== boolLandscape)
		{
			
			if(boolLandscape)
			{
				this.$.scrollTracks.setClassName("scrollH");
			}
			else
			{
				this.$.scrollTracks.setClassName("scrollV");
			}

			this.boolLandscape = boolLandscape;

		}
		

	},
	
	populateScroller: function (intCurrTrackIndex)
	{
		
		//return true;
	
		this.log(intCurrTrackIndex);
		this.intCurrListIndex = intCurrTrackIndex;
		this.intCurrListMin = Math.max(intCurrTrackIndex - 6, -1);
		
		var intInitialSnapIndex = intCurrTrackIndex - this.intCurrListMin - 1;
		
		this.clearScroller();
		var ctrlAlbumArtItem;
		for(var intCheckPos = this.intCurrListMin; intCheckPos <= intCurrTrackIndex + 6;  intCheckPos++)
		//for(var intCheckPos = -2; intCheckPos < this._arFullTrackList.length;  intCheckPos++)
		{
			//this.log("intCheckPos", intCheckPos);
			
			if(intCheckPos < 0 || intCheckPos > this._arFullTrackList.length)
			{
				if(intCheckPos <= intCurrTrackIndex + 3 &&  intCheckPos <= this._arFullTrackList.length + 1)
				{
					ctrlAlbumArtItem = this.$.scrollTracks.createComponent({kind: "KindAlbumArtItem", boolShow: false});					
				}
			}
			else
			{
				var objTrack = this._arFullTrackList[intCheckPos];
				
				if(objTrack !== undefined)
				{
					this.log("Adding Album Art: ", objTrack.strTrackImage);
					ctrlAlbumArtItem = this.createComponent({kind: "KindAlbumArtItem", intTrackOrigIndex: objTrack.origIndex, strSongTitle: objTrack.title, strAlbumArtFile: objTrack.strTrackImage, strArtistName: objTrack.artist, strAlbumName: objTrack.album, onItemClick: "doClickAlbumArtView", intCurrTrackIndex: this.intCurrTrackIndex, boolAudioPlaying: this.boolAudioPlaying, intTrackIndex: intCheckPos, boolVertical: this.boolLandscape});
					//this.$.scrollTracks.addChild(ctrlAlbumArtItem);
					
					//ctrlAlbumArtItem.onItemClick = "onclickAlbumArtItemTest";
					//this.intCurrListMax = intCurrTrackIndex - 6;
				}

			}

			if(ctrlAlbumArtItem)
			{
				ctrlAlbumArtItem.setParent(this.$.scrollTracks);
				ctrlAlbumArtItem.setContainer(this.$.scrollTracks);
			}
		}
		
		
		try
		{
			
		
		this.$.scrollTracks.contentChanged();
		//this.log("Calling onSnap_scrollTracks with : ", intCurrTrackIndex);
		this.boolUpdateScroller = false;
		
		//setTimeout(enyo.bind(this, "setScrollerIndex"), 300);
		//this.boolUpdateScroller = true;
		this.intCurrSnapPos = intInitialSnapIndex;
		//this.$.scrollTracks.snapTo(5);
		this.$.scrollTracks.setIndex(this.intCurrSnapPos);
		
		//this.setItemsDisplay(6);
		//this.transformItems();
		this.boolScrollerDrawn = true;
		
		}
		catch(err)
		{
			this.log("err: ", err);
		}
		
	},
	
	
	setScrollerIndex: function ()
	{
		this.$.scrollTracks.setIndex(5);
	},
	
	onclickAlbumArtItemTest: function ()
	{
		this.log();
		
		this.doClickAlbumArtView();
	},
	
	
	clearScroller: function ()
	{
		this.log();
		this.$.scrollTracks.destroyControls();
		//this.$.scrollTracks.contentChanged();
	},
		
	
	requestTrackList: function ()
	{
		this.log();
		this.doRequestTracks(enyo.bind(this, this.gotTracks));
	},

	gotTracks: function (arTrackResponse, intCurrTrackIndex, boolTrackPlaying)
	{
		this.log();
		this._arFullTrackList = arTrackResponse.slice(0);
		
//		try
//		{
//			//LibraryTools.sortOrigOrder(this._arFullTrackList);
//		}
//		catch(err)
//		{
//			this.log("sort error: ", err);
//		}
		this.boolGotTracks = true;
		this.intCurrTrackIndex = intCurrTrackIndex;
		
		this.boolAudioPlaying = boolTrackPlaying;
		
		this.populateScroller(intCurrTrackIndex);
		//this.$.scrollTracks.setIndex(5);
		//this.$.scrollTracks.snapTo(5);
		//this.setItemsDisplay(6, true);
		

	},
	
	onSnap_scrollTracks: function (sender)
	{
		this.log();
		//this.setItemsDisplay(this.intCurrSnapPos + 1);
	
		
		
	},
	
	onSnapFinish_scrollTracks: function (sender)
	{
		try
		{
			this.log();
			
			if(this.intervalAnimate !== undefined)
			{
				window.clearInterval(this.intervalAnimate);
			}
			
			//return true;
			
			this.intCurrSnapPos = sender.index;
			this.log("this.intCurrSnapPos: ", this.intCurrSnapPos);
			//this.log("this.boolUpdateScroller: ", this.boolUpdateScroller);
	
			//this.setItemsDisplay(this.intCurrSnapPos + 3);
			if(this.boolUpdateScroller && this.$.scrollTracks.children[0].children[this.intCurrSnapPos + 1].intTrackIndex >=0)
			{
				
				//this.populateScroller(this.intCurrSnapPos + 1);
				
				this.populateScroller(this.$.scrollTracks.children[0].children[this.intCurrSnapPos + 1].intTrackIndex);
				
				
				this.setItemsDisplay(this.intCurrSnapPos);
	
			}
		
		}
		catch(err)
		{
			this.log("Error: ", err);
		}

		this.boolUpdateScroller = true;		
	},
	
	
	setItemsDisplay: function (intCurrIndex, boolShowCurrIcon)
	{
		this.log("intCurrIndex: ", intCurrIndex);
		
		return true;

		/*if(intCurrIndex >= 0 && intCurrIndex - 1 < this.$.scrollTracks.children[0].children.length)
		{
			this.$.scrollTracks.children[0].children[intCurrIndex ].displayAsPrev(intCurrIndex > 1);
		}
		
		if(intCurrIndex >= 0 && intCurrIndex + 0 < this.$.scrollTracks.children[0].children.length)
		{
			this.$.scrollTracks.children[0].children[intCurrIndex + 1].displayAsCurrent(boolShowCurrIcon);
		}
		if(intCurrIndex >= 0 && intCurrIndex + 1 < this.$.scrollTracks.children[0].children.length)
		{
			this.$.scrollTracks.children[0].children[intCurrIndex + 2].displayAsNext(intCurrIndex + 1 < this.$.scrollTracks.children[0].children.length  );
		}*/	
	
	},


	changeTrack: function (objTrackInfo)
	{
		this.log(objTrackInfo.intTrackIndex);
		
		var intIndexDiff = objTrackInfo.intTrackIndex - this.$.scrollTracks.children[0].children[this.intCurrSnapPos + 1].intTrackIndex;

		this.log("this.intCurrSnapPos: ", this.intCurrSnapPos);
		this.log("objTrackInfo.intTrackIndex: ", objTrackInfo.intTrackIndex);
		this.log("children[", this.intCurrSnapPos + 1, "].intTrackIndex: ", this.$.scrollTracks.children[0].children[this.intCurrSnapPos + 1].intTrackIndex);
		this.log("intIndexDiff: ", intIndexDiff);
		
		this.intCurrTrackIndex = objTrackInfo.intTrackIndex;		
	
		if(this.boolGotTracks)
		{
			if(intIndexDiff >= -2 && intIndexDiff <= 2)
			{
				this.boolUpdateScroller = true;
				
				this.log("snapTo: ", (this.intCurrSnapPos + intIndexDiff));
				
				this.$.scrollTracks.snapTo(this.intCurrSnapPos + intIndexDiff);
				this.updatePlayPauseItems(undefined, objTrackInfo.intTrackIndex);
			}
			else
			{
			
			this.populateScroller(this.intCurrTrackIndex);
			//this.$.scrollTracks.setIndex(5);
			//this.$.scrollTracks.snapTo(5);
			}
		}
	},
	
	
	setPlayPause: function (boolAudioPlaying)
	{
		this.log();
		this.log(boolAudioPlaying);
		this.boolAudioPlaying = boolAudioPlaying;
		//this.populateScroller(this.intCurrListIndex);
		this.updatePlayPauseItems();
		//this.$.scrollTracks.contentChanged();
	},
	
	
	updatePlayPauseItems: function (boolAudioPlaying, intCurrTrackIndex)
	{
		
		
		if(boolAudioPlaying !== undefined)
		{
			this.boolAudioPlaying = boolAudioPlaying;
		}		
		
		
		for(var intCheckIndex = 0; intCheckIndex < this.$.scrollTracks.children[0].children.length; intCheckIndex++)
		{
			this.log();
			this.log(this.$.scrollTracks.children[0].children[intCheckIndex]);
			if(this.$.scrollTracks.children[0].children[intCheckIndex].checkIfCurrentTrack || 0 === 0)
			{
				
				this.$.scrollTracks.children[0].children[intCheckIndex].checkIfCurrentTrack(this.boolAudioPlaying, intCurrTrackIndex);
			}
		}
		
		//checkIfCurrentTrack
	},
	
	
	onScrollStart_scrollTracks: function (sender, event)
	{
		//return true;
//		if(this.boolScrollerDrawn)
//		{
//			//this.intervalAnimate = window.setInterval(enyo.bind(this, "transformItems"), 100)
//		}
		
	},
	onScrollStop_scrollTracks: function (sender, event)
	{
		
		//return true;
		
		if(this.intervalAnimate !== undefined)
		{
			window.clearInterval(this.intervalAnimate);
		}		
		
		
	}
	
	
});
	