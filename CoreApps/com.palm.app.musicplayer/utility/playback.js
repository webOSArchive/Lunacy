/*globals enyo, setTimeout, $L, event, Utilities, window, PalmSystem, MediaIndex */
enyo.kind(
{
	name: "kindPlayback",
	kind: "Component",
	components: [
		{name: "AudioManager", kind: "kindAudioManager", onPlaying: "onAudioPlaying", onEnded: "onEnded", onSrcChanged: "onAudioSrcChanged", onPausePlay: "doTrackPausePlay", onAudioError: "onAudioError"},
		{name: "PlaybackList", kind: "kindPlaybackList", onShuffleChanged: "doPlaybackShuffleChanged", onRepeatChanged: "doPlaybackRepeatChanged", onStrOriginListIDChanged: "doPlaybackListIDChanged"}
	],
	events: {onTrackPlaying: "", onSongEnd: "", onTrackEnded: "", onTrackSrcChanged: "", onUpdateTrackInfo: "", onUpdateTrackTime: "", onPlaybackShuffleChanged: "", onPlaybackRepeatChanged: "", onTrackPausePlay: "", onPlaybackListSet: "", onPlaybackListIDChanged: ""},
	published: {boolPlaybackListSet: false, boolSuspendUpdates: false, boolSuspendPlayback: false, intUpdateRate: 333},

	/*
	arPlaybackList: [], // array of songs in current Playback list
	intCurrTrackIndex: 0, // index of currently playing track within Playback list
	intCurrTrackTime: 0, // current time of currently playing track
	*/
	intErrorCount: 0,
	INT_ERROR_MAX: 10,
	create: function ()
	{
		this.inherited(arguments);
	},
	
	destroy: function ()
	{
		this.stopTrackTimeUpdate();
	},	
	
	boolSuspendUpdatesChanged: function ()
	{
		
		if(this.boolSuspendUpdates)
		{
			this.stopTrackTimeUpdate();
		}
		else
		{
			if(this.$.AudioManager.boolAudioPlaying)
			{
				this.startTrackTimeUpdate();
				
				this.onAudioSrcChanged(null, true);
			}
			else
			{
				//This allows to get an update if screen just came back on
				this.updateTrackTime();
			}
		}
		
	},
	
	boolSuspendPlaybackChanged: function ()
	{
		this.log(this.boolSuspendPlayback);
	},
	
	/**
	* @param objSetPlaybackList
	*			.arSetPlaybackList	.intStartTrackTime
	*/
	setPlaybackList: function (objSetPlaybackList)
	{
		this.log();
		
		if (typeof(objSetPlaybackList.arSetPlaybackList) === 'object')
		{
			this.boolPlaybackListSet  = this.$.PlaybackList.setPlaybackList(objSetPlaybackList);
			
			if (this.boolPlaybackListSet)
			{
				
				if (objSetPlaybackList.intStartTrackTime === undefined)
				{
					objSetPlaybackList.intStartTrackTime = 0;
				}			
	
	
				this.startPlayback(objSetPlaybackList.intStartTrackTime);
				
				this.log("Raising onPlaybackListSet");
				this.doPlaybackListSet(this.boolPlaybackListSet, objSetPlaybackList.arSetPlaybackList.length, this.$.PlaybackList.getShuffleState(true), this.$.PlaybackList.getRepeatMode(true));
				
				return true;				
				
			}
		
		}
		
		return false;
		
	},
	
	/**
	 * @brief starts playback
	 * @param intStartTrackTime Time you want to start from
	 */
	startPlayback: function (intStartTrackTime)
	{
		this.log();
		this.playTrack(intStartTrackTime);
		
	},
	
	/**
	 * @brief stops updating time
	 * @param boolForcePause (optional) true forces pause, false forces play
	 */
	pausePlayback: function (boolForcePause) 
	{
		//if playback was stopped because of errors, play the current track again
		if(this.intErrorCount === this.INT_ERROR_MAX && !boolForcePause)
		{
			this.$.AudioManager.playAudio(this.$.PlaybackList.getCurrentPlayBackItem().path, 0, true);
		}
		
		if(!this.boolSuspendPlayback)
		{
			if(this.$.AudioManager.pauseAudio(boolForcePause))
			{
				this.startTrackTimeUpdate();
			}
			else
			{
				this.stopTrackTimeUpdate();
			}
		}
		
	},
	
	
	playTrack: function (intStartTrackTime, boolForced, forcePlay)
	{
		this.log(this.$.PlaybackList.intCurrTrackIndex);
		
		if(forcePlay === undefined)
		{
			forcePlay = true;
		}
		if(intStartTrackTime === undefined)
		{
			intStartTrackTime = 0;
		}
		//this.log(this.$.PlaybackList.getCurrentPlayBackItem().path);
		var paused = this.$.AudioManager.boolAudioPaused;
		this.$.AudioManager.playAudio(this.$.PlaybackList.getCurrentPlayBackItem().path, intStartTrackTime, boolForced);			
		if(paused && !forcePlay)
		{
			this.$.AudioManager.pauseAudio(false);
		}
	},
	
	onAudioPlaying: function ()
	{
		this.log("Playback onAudioPlaying");
		this.startTrackTimeUpdate();
		this.doTrackPlaying();
		//Succesfully started playing so mediaServer isn't erroring out
		this.intErrorCount = 0;
	},
	

	setTrackTime: function (intPos)
	{
		this.log();
		this.$.AudioManager.setAudioTime(intPos);
	},
	
	intUpdateRateChanged: function ()
	{
		
		if(this.intervalCheckTrackTime !== undefined && this.$.AudioManager.boolAudioPlaying)
		{
			this.startTrackTimeUpdate();
	
		}
		
	},
	
	startTrackTimeUpdate: function ()
	{
	
		this.stopTrackTimeUpdate();
		if(!this.boolSuspendUpdates)
		{
			this.updateTrackTime();
			this.intervalCheckTrackTime = window.setInterval(enyo.bind(this, "updateTrackTime"), this.intUpdateRate);
		}
	},
		
	updateTrackTime: function ()
	{
		//this.log("updateTrackTime");

		if(!this.boolSuspendUpdates)
		{
			var objTrackTimes = {floatTrackCurrentTime: this.getTrackCurrentTime(), floatTrackDuration: this.getTrackDuration()};
			this.doUpdateTrackTime(objTrackTimes);
		}
	},
	
	stopTrackTimeUpdate: function ()
	{
		if(this.intervalCheckTrackTime !== undefined)
		{
			window.clearInterval(this.intervalCheckTrackTime);
		}
	},
	
	onAudioSrcChanged: function (sender, boolForced)
	{
		
		try
		{
			
			
			this.log();
			
			if(this.$.PlaybackList.getCurrentPlayBackItem())
			{
				var objTrackInfo = {strTrackArtist: this.getTrackArtist(), 
									strTrackTitle: this.getTrackTitle() ,
									strTrackAlbum: this.getTrackAlbum(), 
									strTrackGenre: this.getTrackGenre(), 
									strTrackImage: this.getTrackImage(), 
									intTrackIndex: this.$.PlaybackList.intCurrTrackIndex, 
									intTrackOrigIndex:  this.$.PlaybackList.getCurrentPlayBackItem().origIndex, 
									strTrackID: this.getTrackID(), 
									intTrackTime: this.getTrackCurrentTime(), 
									intTrackDuration: this.getTrackDuration(), 
									strTrackDuration: this.getTrackDuration(true), 
									strListQuery:			this.$.PlaybackList.getListQuery(),
									boolForced: boolForced};
				
				this.doTrackSrcChanged(objTrackInfo);

			}
		}
		catch(err)
		{
			
			this.log(err);
				
		}
		
	},
	
		
	onAudioError: function (sender, event)
	{
		this.log();
		
		this.stopTrackTimeUpdate();
		if(event === undefined || event.forcePlay === undefined)
		{
		    event = {forcePlay: false};	
		}
		if(this.intErrorCount < this.INT_ERROR_MAX)
		{
			this.intErrorCount++;
			if(this.$.playbackList.getRepeatMode() === 2){
				//if we are repeating the same song, stay on it
				this.$.AudioManager.playAudio(this.$.PlaybackList.getCurrentPlayBackItem().path, 0, event.forcePlay);
			}else{
				this.nextTrack(true, event.forcePlay);
			}
		}
		else
		{
			this.log("Max Error Count");
		}
		
	},
	
	clickTrack: function(intClickedTrackIndex)
	{
	 if(intClickedTrackIndex === this.$.PlaybackList.getCurrSongOrigIndex())
	 {
		this.pausePlayback();
		
	 }
	 else
	 {
		this.switchTrack(intClickedTrackIndex);
	 }
	},
	
	switchTrack: function (intSetTrackIndex)
	{
		this.$.PlaybackList.switchTrack(intSetTrackIndex);
		this.playTrack();			
	},
	
	nextTrack: function (boolForce, forcePlay)
	{
		this.log("nextTrack");
		if(this.$.PlaybackList.nextTrack(boolForce))
		{
			this.playTrack(0, boolForce, forcePlay);			
		}else
		{
			this.pausePlayback(false);
		}
	},
	
	prevTrack: function (boolForce)
	{
		this.log("prevTrack");
		//this.log(this.$.AudioManager.getAudioCurrentTime());
		
		if(this.$.PlaybackList.prevTrack(this.$.AudioManager.getAudioCurrentTime(), boolForce))
		{
			this.playTrack(0, boolForce);
		}
		
		
		
	},
	
	shufflePlaylist: function (boolForceShuffle)
	{
		this.log();
		this.$.PlaybackList.shufflePlaylist(boolForceShuffle);
	},
	
	songListChanged: function (/*arChangedUList, */intCurrTrackOrigIndex)
	{
		
		this.log();
		this.$.PlaybackList.songListChanged(/*arChangedUList, */intCurrTrackOrigIndex);
	
	},
	
	sortSongList: function (strSortMode, boolSortAsc)
	{
		
		
		
	},
	
	
	setRepeatMode: function (intForceMode) // intForceMode is optional. Only use to override the default repeat mode cycle (off, all, one).
	{
		this.$.PlaybackList.setRepeatMode(intForceMode);
	},

	setVolume: function (intPos)
	{
		this.$.AudioManager.setAudioVolume(intPos);
		
	},
	
	getVolume: function ()
	{
		this.$.AudioManager.getAudioVolume(); 
	},
	
    onEnded: function()
    {
		if(!this.boolSuspendUpdates)
		{
			//Send time as duration, so the user sees the scrubber at the end
			var objTrackTimes = {floatTrackCurrentTime: this.getTrackDuration(), floatTrackDuration: this.getTrackDuration()};
			this.doUpdateTrackTime(objTrackTimes);
		}
		this.doSongEnd();
    },
	
	//Info methods
	
	getTrackTitle: function ()
	{
		return this.$.PlaybackList.getCurrentPlayBackItem().title;
	},
	
	getTrackArtist: function ()
	{
		return this.$.PlaybackList.getCurrentPlayBackItem().artist;
	},
	
	getTrackAlbum: function ()
	{
		return this.$.PlaybackList.getCurrentPlayBackItem().album;
	},
	getPlaybackListID: function ()
	{
		return this.$.PlaybackList.strOriginListID;
	},
	getTrackGenre: function ()
	{
		return this.$.PlaybackList.getCurrentPlayBackItem().genre;		
	},
	
	getTrackCurrentTime: function (boolFormatted)
	{
		if(boolFormatted === undefined)
		{
			boolFormatted = false;
		}
		
		var intCurrentTime = this.$.AudioManager.getAudioCurrentTime();
		if(!( Utilities.isNumeric(intCurrentTime) || intCurrentTime < 0))
		{
			intCurrentTime = 0;
			
		}
		
		
		if(boolFormatted)
		{
			return Utilities.formatTime(intCurrentTime);
		}
		else
		{
			return intCurrentTime;
		}
		
	},
	
	getTrackDuration: function (boolFormatted)
	{
		if(boolFormatted === undefined)
		{
			boolFormatted = false;
		}
		
		var intDuration = this.$.AudioManager.getAudioDuration();
		if(!( Utilities.isNumeric(intDuration)))
		{
			intDuration = 0;
			
		}
		
		if(boolFormatted)
		{
			return Utilities.formatTime(intDuration);
		}
		else
		{
			return intDuration;
		}
		
	},
	
	getTrackPlaying: function ()
	{	
		return this.$.AudioManager.getBoolAudioPlaying();
	},
	
	getTrackImage: function (intImageSize)
	{
		return this.$.PlaybackList.getCurrentPlayBackImage();
	},
	
	getTrackIndex: function ()
	{
		return this.$.PlaybackList.getIntCurrTrackIndex();
	},
	
	getTrackID: function()
	{
		return this.$.PlaybackList.getCurrSongID();		
	},
	
	getTrackList: function()
	{
		return this.$.PlaybackList.getPlaybackList();
	}
	
	
	
});





















