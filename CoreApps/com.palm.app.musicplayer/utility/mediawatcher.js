/*globals enyo, $L, event, Utilities, window, PalmSystem, MediaIndex */
enyo.kind({
	kind:enyo.Component,
	name:"MediaWatcher",
	events: {
		onSongChange:"",
		onPlaylistChange:"",
		onIndexingStart:"",
		onIndexingStop:""
	},
	components: [
		//Check for when Audio Files are added or destroyed
		{kind: "enyo.DbService", dbKind: "com.palm.media.audio.genre:1", method: "find", name: "watchSongsAdded", subscribe:true, onWatch:"gotSongs", onSuccess: "gotMedia" , onFailure: "watchFailure", reCallWatches: true},
		{kind: "enyo.DbService", dbKind: "com.palm.media.audio.genre:1", method: "find", name: "watchSongsDeleted", subscribe:true, onWatch:"deleteSongs", onSuccess: "gotMedia" , onFailure: "watchFailure", reCallWatches: true},
		//Check for when playlists are added or destroyed
		{kind: "enyo.DbService", dbKind: "com.palm.media.playlist.file:1", method: "find", name: "watchPlaylistsDeleted", subscribe: true, onWatch: "gotPlaylists", onSuccess: "gotMedia" , onFailure: "watchFailure", reCallWatches: true},
		{kind: "enyo.DbService", dbKind: "com.palm.media.playlist.file:1", method: "find", name: "watchPlaylistsAdded", subscribe: true, onWatch: "gotPlaylists", onSuccess: "gotMedia" , onFailure: "watchFailure", reCallWatches: true},
		//Monitor Filenotifyd for Changes
		{kind: "enyo.PalmService", service:"palm://com.palm.filenotifyd/", method:"state", name: "filenotifydMonitor", subscribe:true, onSuccess: "gotFilenotifydResponse", onFailure: "fileNotifydFailure"}
		
	],

	create:function (){
		this.inherited(arguments);
		this.filenotifyd_is_busy = false;
	},

	//Failure subscribing to Filenotifyd Service
	fileNotifydFailure: function(inSender, inResponse){
		this.log();
		this.log(inResponse);
	},

	//Failure subscribing to Filenotifyd Service
	gotFilenotifydResponse: function(inSender, inResponse){
		this.log();
		this.log(inResponse);
		//If we are not already spinning and not in idle (are processing(starting app in middle of index) or queued)
		if (!this.filenotifyd_is_busy && inResponse.state && inResponse.state !== "idle")
		{
			this.filenotifyd_is_busy = true;
			this.doIndexingStart();
			
		}
		else if (this.filenotifyd_is_busy && inResponse.state && inResponse.state === "idle")
		{
			this.filenotifyd_is_busy = false;
			this.doIndexingStop();
		}
	},
	

	//Generic Failure Callback for all WATCH calls
	watchFailure: function(inSender, inResponse)
	{
		this.log();
		this.log(inResponse);
	},

	//Generic Callback for DB Find Method Results
	gotMedia: function(inSender, inResponse, inRequest)
	{
		this.log();
	},

	//Watch Triggered For Songs Added
	gotSongs: function(inSender, inResponse, inRequest)
	{
		this.log();
		this.doSongChange(inResponse);
		// this.$.watchSongsAdded.cancelCall(this.songAddedWatcher);
		// this.songAddedWatcher = this.$.watchSongsAdded.call({ query: { where: [{"prop": "isRingtone","op": "=", "val": false}] } });
	},

	//Watch Triggered For Songs Deleted
	deleteSongs: function(inSender, inResponse, inRequest)
	{
		this.log();
		this.doSongChange(inResponse);
		// this.$.watchSongsDeleted.cancelCall(this.songDeletedWatcher);
		// this.songDeletedWatcher = this.$.watchSongsDeleted.call({ query: { where: [{"prop": "_del","op": "=", "val": true}] } });
	},

	//Watch Triggered For Playlists Added
	gotPlaylists: function(inSender, inResponse, inRequest)
	{
		this.log();
		this.doPlaylistChange(inResponse);
		// this.$.watchPlaylistsAdded.cancelCall(this.playlistAddedWatcher);
		// this.playlistAddedWatcher = this.$.watchPlaylistsAdded.call({ query: { where: [{"prop": "_del","op": "=", "val": false}]} });

	},

	//Watch Triggered For Playlists Deleted
	deletePlaylists: function(inSender, inResponse, inRequest)
	{
		this.log();
		this.doPlaylistChange(inResponse);
		// this.$.watchPlaylistsDeleted.cancelCall(this.playlistDeletedWatcher);
		// this.playlistDeletedWatcher = this.$.watchPlaylistsDeleted.call({ query: { where: [{"prop": "_del","op": "=", "val": true}]} });

	},

	//Cancels all ongoing calls
	cancelAllCalls : function(){
		this.filenotifyd_is_busy = false;
		
		if (this.songAddedWatcher)
		{
			this.$.watchSongsAdded.cancelCall(this.songAddedWatcher);
		}
		if (this.songDeletedWatcher)
		{
			this.$.watchSongsDeleted.cancelCall(this.songDeletedWatcher);
		}
		if (this.playlistAddedWatcher){
			this.$.watchPlaylistsAdded.cancelCall(this.playlistAddedWatcher);
		}
		if (this.playlistDeletedWatcher){
			this.$.watchPlaylistsDeleted.cancelCall(this.playlistDeletedWatcher);
		}
		if (this.filenotifydWatcher){
			this.$.filenotifydMonitor.cancelCall(this.filenotifydWatcher);
		}
	},

	//Trigger All Watches Note: Some Watches may be triggered twice for each change
	startWatch: function()
	{		
		this.cancelAllCalls();
		
		this.filenotifydWatcher = this.$.filenotifydMonitor.call({});

		//this.songAddedWatcher = this.$.watchSongsAdded.call({ query: { where: [{"prop": "isRingtone","op": "=", "val": false}]} });
		this.songAddedWatcher = this.$.watchSongsAdded.call({ query: { from: "com.palm.media.audio.genre:1", where: [{"prop": "_del","op": "=", "val": false}]} });
		//this.songDeletedWatcher = this.$.watchSongsDeleted.call({ query: { where: [{"prop": "_del","op": "=", "val": true}]} });
		this.songDeletedWatcher = this.$.watchSongsDeleted.call({ query: { from: "com.palm.media.audio.genre:1", where: [{"prop": "_del","op": "=", "val": true}]} });

		//this.playlistAddedWatcher = this.$.watchPlaylistsAdded.call({ query: { where: [{"prop": "_del","op": "=", "val": false}]} });
		this.playlistAddedWatcher = this.$.watchPlaylistsAdded.call({ query: {from: "com.palm.media.playlist.file:1",  where: [{"prop": "_del","op": "=", "val": false}]} });
		//this.playlistDeletedWatcher = this.$.watchPlaylistsDeleted.call({ query: { where: [{"prop": "_del","op": "=", "val": true}]} });
		this.playlistDeletedWatcher = this.$.watchPlaylistsDeleted.call({ query: {from: "com.palm.media.playlist.file:1", where: [{"prop": "_del","op": "=", "val": true}]} });
	},

	//Stop All Watches:
	stopWatch: function()
	{
		this.cancelAllCalls();
		
		this.filenotifydWatcher = undefined;
		
		this.playlistAddedWatcher = undefined;
		this.playlistDeletedWatcher = undefined;

		this.songAddedWatcher = undefined;
		this.songDeletedWatcher = undefined;
	}
});