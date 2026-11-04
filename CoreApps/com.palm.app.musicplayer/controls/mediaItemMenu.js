/*globals enyo, $L, event */
enyo.kind({
	name: "ctrMediaItemMenu",
	kind: "Menu",
	events: {onMenuClick: "", onRequestPlaylists: ""},
	published: {strMediaType: "" },
	components: [
		{name: "mitemPlay", caption: $L("Play"), onclick: "onMenuItemClick"},
		{name: "mitemView", caption: $L("View"), onclick: "onMenuItemClick"},
		{name: "mitemAddToPlaylist", caption: $L("Add to Playlist"), components: [
			
			
			
		]}		
	],
	
	create: function ()
	{
		this.inherited(arguments);
		
		
		this.local_play_Templ = new enyo.g11n.Template($L("Play #{strMediaType}"));
		this.local_view_Templ = new enyo.g11n.Template($L("View #{strMediaType} Tracks"));
		this.local_addToPlayList_Templ = new enyo.g11n.Template($L("Add #{strMediaType} to Playlist"));
	},
	
	ready: function ()
	{
		this.inherited(arguments);
		
	},
	
	drawMenu: function ()
	{
		this.log();
		this.$.mitemPlay.setCaption(this.local_play_Templ.evaluate({strMediaType: this.strMediaType}));
		this.$.mitemView.setCaption(this.local_view_Templ.evaluate({strMediaType: this.strMediaType}));
		this.$.mitemAddToPlaylist.setCaption(this.local_addToPlayList_Templ.evaluate({strMediaType: this.strMediaType}));
		this.getPlaylists();
		
	},
	
	openMenu: function ()
	{
		
		this.intClickIndex = event.rowIndex;
		this.$.mitemAddToPlaylist.setOpen(false);
		this.$.client.setScrollTop(0);
		this.openAtEvent(event, {left: 0});
		return true;
		
		
	},
	
	getPlaylists: function () // gets list of playlists for menu
	{
		
		this.log();
	
		var objGetPlaylistsRequest = {where: [], playlistType: "static", callback: enyo.bind(this, "gotPlaylists")};

		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest:", objGetPlaylistsRequest);
		
		this.doRequestPlaylists(objGetPlaylistsRequest);
		
	},
	
	
	gotPlaylists: function (inResponse)
	{
		this.log();
		try
			{
			
			if(inResponse.results)
			{
		
				this.arPlaylists = inResponse.results.slice(0);
								
				this.$.mitemAddToPlaylist.destroyControls();
				
				if(this.arPlaylists.length > 0)
				{
					for(var intPlaylist = 0 ; intPlaylist < this.arPlaylists.length; intPlaylist++)
					{
						this.log("Checking item ", intPlaylist);
						
						var mitemPlaylist;

						if(this.arPlaylists[intPlaylist] !== undefined)
						{
							this.log("Adding item ", intPlaylist);
							
							mitemPlaylist = this.$.mitemAddToPlaylist.createComponent({kind: "MenuItem", caption: this.arPlaylists[intPlaylist].name, onclick: "onMenuItemClick", plid: this.arPlaylists[intPlaylist]._id });
						}
						else
						{
							mitemPlaylist = this.createComponent({caption: ""});
						}
						
						mitemPlaylist.setOwner(this);
						
						//mitemPlaylist.setParent(this.$.mitemAddToPlaylist);
						//mitemPlaylist.setContainer(this.$.mitemAddToPlaylist);
					}

				}
				
				this.$.mitemAddToPlaylist.contentChanged();
			
			}
			

		}
		catch (err)
		{
			this.log("error: ", err);

		}
		
	},
	
	
	onMenuItemClick: function (sender, event)
	{
		
		this.doMenuClick(sender);
		
	}
	
});