/*globals enyo, $L, event, Utilities, window, PalmSystem, MediaIndex */
enyo.kind(
{
	name: "kindPlaylistManager",
	kind: "Component",
	events: {onSetPlaybackList: "", onRequestMedia: "", onRefreshPlaylists: "", onPlaylistDeleted: ""},
	components: [

		{kind: "DbService", dbKind: "com.palm.music.staticplaylists:1" , onFailure: "onFailure_dbsStaticPlaylists", components: [
			{ name: "dbsGetStaticPlaylists", method: "find", onSuccess: "gotPlaylists"},
			{ name: "dbsPutStaticPlaylists", method: "put", onSuccess: "onSuccess_PutStaticPlaylists"},
			{ name: "dbsMergeStaticPlaylists", method: "merge", onSuccess: "onSuccess_MergeStaticPlaylists" , onFailure: "onFailure_MergeStaticPlaylists"},
			{ name: "dbsDelStaticPlaylists", method: "del", onSuccess: "onSuccess_DeleStaticPlaylists"}
		]},
		{name: "dbsFilePlaylists", kind: "DbService", dbKind: "com.palm.media.playlist.object:1", onSuccess: "gotPlaylists", onFailure: "onFailure_dbsStaticPlaylists", components: [
		]}

	],
	
	arAddQueue: [],	// queue for added elements waiting to be merged into the playlist
	boolMerging: false, //set to true while waiting on merge operation. If false, process next queued item (if any)
		
	create: function ()
	{
		this.inherited(arguments);
	},
	
	
	requestPlaylists: function (objGetPlaylistsRequest)
	{
		this.log();

		this.getPlaylists(objGetPlaylistsRequest);
		
	},
   

    getPlaylists: function (objGetPlaylistsRequest)
    {
		this.log("****");

		try
		{

			var q = {
				orderBy: objGetPlaylistsRequest.order || "",
				where: objGetPlaylistsRequest.where || []
			};
			
			if(objGetPlaylistsRequest.queryType === undefined)
			{
				objGetPlaylistsRequest.queryType = "find";
			}
			
			this.$.dbsGetStaticPlaylists.method = objGetPlaylistsRequest.queryType;
				
			if(objGetPlaylistsRequest.playlistKind === undefined)
			{
				objGetPlaylistsRequest.playlistKind = "com.palm.music.staticplaylists:1";
			}
			
			this.$.dbsGetStaticPlaylists.setDbKind(objGetPlaylistsRequest.playlistKind);
				
			var req = this.$.dbsGetStaticPlaylists.call({watch: false, query: q, subscribe: false},{});
			
			req.objGetPlaylistsRequest = objGetPlaylistsRequest;
	
			this.log("**** called StaticPlaylists query");
		}
		catch(err)
		{
			this.log(err);
		}
    },
	
	gotPlaylists: function (inSender, inResponse, inRequest)
	{
		this.log("****");
				
		if(inResponse.results)
		{
			this.log(inResponse.results.length);

			if(inRequest.objGetPlaylistsRequest)
			{
				if(inRequest.objGetPlaylistsRequest.callback)
				{
					inRequest.objGetPlaylistsRequest.callback(inResponse, inRequest);
				}
			}
		}
	},
		
	insertStaticPlaylist: function (objPutPlaylistsRequest)
	{
		this.log(objPutPlaylistsRequest.strPlaylistName);
		this.putStaticPlaylistsCallback = objPutPlaylistsRequest.callback;
		this.$.dbsPutStaticPlaylists.call({objects: [{"_kind":"com.palm.music.staticplaylists:1","name": objPutPlaylistsRequest.strPlaylistName, "tracks":[]}]});

	},
	
	onSuccess_PutStaticPlaylists: function (sender, response)
	{
		this.log(response);
		if(response.results.length > 0){
			this.putStaticPlaylistsCallback(response.results[0].id);
		}
		delete this.putStaticPlaylistCallback;
	},

	
	onFailure_dbsStaticPlaylists: function (inSender, inResponse)
	{
		
		this.log("****");
		this.log(inSender);
		this.log(inResponse);
		
	},

	
	
	addToPlaylist: function (objAddToPlaylist)
	{
		this.log();
		this.arAddQueue.push(objAddToPlaylist);
		this.processAddQueue();
	},
	
	
	processAddQueue: function ()
	{
		this.log(this.arAddQueue.length);
		
		if(this.arAddQueue.length > 0 && !this.boolMerging)
		{
			
			var objAddToPlaylist = this.arAddQueue.shift();
	
			var objGetMediaRequest;
			switch (objAddToPlaylist.strMediaType)
			{
				
				case "song":
					this.addLooseSongs(objAddToPlaylist);
					return true;
				case "artist":
					objGetMediaRequest = {where: [{"prop": "isRingtone", "op": "=", "val": false},{"prop": "artist", "op": "=", "val": objAddToPlaylist.strMediaFilter}], mediaType: "song", order: "sortKey.albumDiscAndTrack", callback: enyo.bind(this, "gotAddTracks"), plid: objAddToPlaylist.strPlaylistID};
					break;
					
				case "album":	
					objGetMediaRequest = {where: [{"prop": "isRingtone", "op": "=", "val": false},{"prop": "album", "op": "=", "val": objAddToPlaylist.strMediaFilter},{"prop": "albumArtist", "op": "=", "val": objAddToPlaylist.strMediaFilterSub}], mediaType: "song", order: "sortKey.trackAndDisc", callback: enyo.bind(this, "gotAddTracks"), plid: objAddToPlaylist.strPlaylistID};
					break;
				
				case "genre":	
					objGetMediaRequest = {where: [{"prop": "isRingtone", "op": "=", "val": false},{"prop": "genre", "op": "=", "val": objAddToPlaylist.strMediaFilter}], mediaType: "song", order: "title", callback: enyo.bind(this, "gotAddTracks"), plid: objAddToPlaylist.strPlaylistID};
					break;
			}
	
			this.log("**** raising doRequestMedia");
			this.log("**** objGetMediaRequest: ", objGetMediaRequest);
			
			this.doRequestMedia(objGetMediaRequest);
			
			
		}


		
	},
	
	addLooseSongs: function (objAddToPlaylist)
	{
		var tempFunc = enyo.bind(this, function(inResponse, inRequest){
			this.gotAddPlaylist(inResponse, inRequest);
			if(typeof(objAddToPlaylist.callback)  === "function"){
				objAddToPlaylist.callback();
			}
		});
		var objGetPlaylistsRequest = {where: [{"prop": "_id", "op": "=", "val": objAddToPlaylist.strPlaylistID }], playlistType: "static", callback: tempFunc, arAddTracks: objAddToPlaylist.arTracks};

		this.log("**** raising doRequestMedia");
		this.log("**** objGetMediaRequest: ", objGetPlaylistsRequest);
		
		this.getPlaylists(objGetPlaylistsRequest);		
	},
	
	
	gotAddTracks: function (inResponse, inRequest)
	{
		
		this.log();
		if(inResponse)
		{
			if(inResponse.results)
			{
				if(inResponse.results.length > 0)
				{
					
					var arAddTracks = [];
					
					for(var intIndex = 0; intIndex < inResponse.results.length; intIndex++)
					{
						
						this.log(inResponse.results[intIndex].title);
						arAddTracks[intIndex] = Utilities.getPlaylistSongData(inResponse.results[intIndex]);
						
					}

					
					if(inRequest)
					{
						if(inRequest.objGetMediaRequest.plid)
						{
							this.log(arAddTracks);
							
							
								
							var objGetPlaylistsRequest = {where: [{"prop": "_id", "op": "=", "val": inRequest.objGetMediaRequest.plid }], playlistType: "static", callback: enyo.bind(this, "gotAddPlaylist"), arAddTracks: arAddTracks};
					
							this.log("**** raising doRequestMedia");
							this.log("**** objGetMediaRequest: ", objGetPlaylistsRequest);
							
							this.getPlaylists(objGetPlaylistsRequest);
							
						}
					}					
				}
			}
		}
		

	},
	
	gotAddPlaylist: function (inResponse, inRequest)
	{
		this.log(inResponse);
		
		
		if(inRequest)
		{
			if(inRequest.objGetPlaylistsRequest.arAddTracks)
			{
				
				var arAddTracks = inRequest.objGetPlaylistsRequest.arAddTracks;
				
				this.log("arAddTracks.length: ", arAddTracks.length);
			
				if(inResponse.results)
				{
			
					var arAddPlaylists = inResponse.results.slice(0);
					
					this.log("this.arPlaylists.length: ", arAddPlaylists.length);
					
					if(arAddPlaylists.length > 0)
					{
						if(arAddPlaylists[0])
						{
							if(arAddPlaylists[0].tracks)
							{
								this.log("arAddPlaylists[0].tracks: ", arAddPlaylists[0].tracks);
							
								arAddPlaylists[0].tracks = arAddPlaylists[0].tracks.concat(arAddTracks);								
								
								this.log("arAddPlaylists[0].tracks.length: ", arAddPlaylists[0].tracks.length);
								
								this.$.dbsMergeStaticPlaylists.call({objects: [arAddPlaylists[0]]});
								this.boolMerging = true;
							
								
							}
							
						}
					}
				}
			}				
		}
		
	},
	
	
	updateStaticPlaylist:  function (objUpdatePlaylist)
	{
		try
		{
			var req = this.$.dbsMergeStaticPlaylists.call({objects: [objUpdatePlaylist.objPlaylist]});
			req.objUpdatePlaylist = objUpdatePlaylist;
		}
		catch (err)
		{
			this.log(err);
		}
	},
	
	onSuccess_MergeStaticPlaylists: function (inSender, inResponse, inRequest)
	{

		this.log();
		
		
		this.boolMerging = false;
		if(inRequest.objUpdatePlaylist)
		{
			this.doRefreshPlaylists();
			if(inRequest.objUpdatePlaylist.callback)
			{
				enyo.windows.addBannerMessage($L("Playlist updated"), "{}", "images/notification-small.png");
				inRequest.objUpdatePlaylist.callback();

			}
		}
		else
		{
			if(this.arAddQueue.length > 0)
			{
				this.processAddQueue();
			}
			else
			{
				enyo.windows.addBannerMessage($L("Added tracks to playlist"), "{}", "images/notification-small.png");
			}
		}
		
		
	},
	
	onFailure_MergeStaticPlaylists: function (inSender, inResponse, inRequest)
	{
		
		this.log();
		
		this.log(inResponse);
		this.boolMerging = false;
		
		
	
		if(inRequest.objUpdatePlaylist)
		{
			if(inRequest.objUpdatePlaylist.callback)
			{
				enyo.windows.addBannerMessage($L("Error updating playlist"), "{}", "images/notification-small.png");
				inRequest.objUpdatePlaylist.callback();
			}	
		}
		else
		{
			if(this.arAddQueue.length > 0)
			{
				this.processAddQueue();
			}
			else
			{
				enyo.windows.addBannerMessage($L("Error adding tracks to playlist"), "{}", "images/notification-small.png");			
			}
		}
		
	},
	
	
	deletePlaylist: function (objDeletePlaylist)
	{
		
		if(objDeletePlaylist.strPlaylistID !== undefined)
		{
					
			var req = this.$.dbsDelStaticPlaylists.call({ids: [objDeletePlaylist.strPlaylistID]});
			req.objDeletePlaylist = objDeletePlaylist;
		}
		

		
	},
	
	
	onSuccess_DeleStaticPlaylists: function (inSender, inResponse, inRequest)
	{
		this.log();
		this.doRefreshPlaylists();
		this.doPlaylistDeleted();
		if(inRequest.objDeletePlaylist.callback)
		{
			inRequest.objDeletePlaylist.callback();
		}
		
	}
	
	
       
});