/*globals enyo, $L, event, Utilities, window, PalmSystem, MediaIndex, Audio */
enyo.kind(
{
	name: "kindAudioManager",
	kind: "Component",
	
	
	//boolAudioSetup: false, May not end up using
	
	published: {boolAudioPaused: true, boolAudioPlaying: false},
	
	events: {onPlaying: "", onEnded: "", onPausePlay: "", onSrcChanged: "", onAudioError: "", onAudioStall: "", onAudioConnected:""},
	
	create: function ()
	{
		this.inherited(arguments);
		this.setupAudio();
		
	},
	
	_boolAudioLoaded: false,
	_boolPlayingBeforeError: false,
	_intQueuedPlayTime: null,
	
	setupAudio: function ()
	{
		this.log();			
		if (this.objAudio === undefined)
		{
			this.objAudio = new Audio();
			this.objAudio.setAttribute("x-palm-media-audio-class", "media");
			
			this.objAudio.addEventListener('load', enyo.bind(this, this.onAudioLoaded), false);
			this.objAudio.addEventListener('play', enyo.bind(this, this.onAudioPlayed), false);
			this.objAudio.addEventListener('playing', enyo.bind(this, this.onAudioPlaying), false);
			this.objAudio.addEventListener('ended', enyo.bind(this, this.onAudioEnded), false);
			this.objAudio.addEventListener('pause', enyo.bind(this, this.onAudioPaused), false);
			this.objAudio.addEventListener('connected', enyo.bind(this, this.doAudioConnect), false);
			
			this.objAudio.addEventListener('error', enyo.bind(this, this.onError_Play), false);
			this.objAudio.addEventListener('stalled', enyo.bind(this, this.onError_Stall), false);
			
		}		
		
	},
	
	killAudio: function ()
	{
		
		
		
	},
	
	
	resetAudio: function ()
	{
		
	},
	
		

	
	playAudio: function (strAudioFile, intStartTime, boolForced)
	{
		try
		{
			
			this.log("playing: ", strAudioFile);
			this._boolAudioLoaded = false;
			this.objAudio.src = strAudioFile;
			
			this.doSrcChanged(boolForced);
			
			this.objAudio.load();
			this.objAudio.play();
			
		}
		catch (err)
		{
			this.log("playAudio error: ", err);
		}
		
	},

	pauseAudio: function (boolPlayPause)
	{
	
		if(boolPlayPause === undefined)
		{
			boolPlayPause = !this.boolAudioPlaying;
		}

		this.log(boolPlayPause);

		if (!boolPlayPause)
		{
			this.objAudio.pause();
			this.boolAudioPlaying = false;
		}
		else
		{
			this.objAudio.play();
			this.boolAudioPlaying = true;
		}
		
		return this.boolAudioPlaying;
		
	},
	
	onAudioConnected: function (event)
	{
		
	},
	
	onAudioLoaded: function (event)
	{
		this.log();
	},
	
	onAudioPlayed: function (event)
	{
		this.log();
	},
	
	onAudioPlaying: function (event)
	{
		this.log();
		this._boolAudioLoaded = true;
		this.boolAudioPlaying = true;
		this.boolAudioPaused = false;
		this._boolPlayingBeforeError = true;
		this.doPausePlay(this.boolAudioPlaying);
		this.doPlaying();
		//this.doSrcChanged(); //moved back to playAudio as a test. May move it back
		
	},
	
	onAudioPaused: function (event)
	{
		this.log();
		this._boolPlayingBeforeError = false;
		this.boolAudioPlaying = false;
		this.boolAudioPaused = true;		
		this.doPausePlay(this.boolAudioPlaying);
	},
	
	onAudioEnded: function (event)
	{
		this.log();
		this._boolPlayingBeforeError = false;
		this.doEnded();
	
	},
	
	setAudioTime: function(intPos)	// Takes a value from 0 to 100 that represents the percentage of the song duration.
	{
		this.log("intPos: ", intPos);
		this.log("src: ", this.objAudio.src);
		//must be past the point where it begins playing before changing duration
		if (this.objAudio.src && this._boolAudioLoaded)
		{
			this.objAudio.currentTime = this.getAudioDuration() * intPos / 100;
		}
		
	},
	
	setAudioVolume: function(intPos)	//  Takes a value from 0 to 100.
	{
		this.objAudio.volume = intPos/100;
	},
	
	getAudioVolume: function()
	{
		return this.objAudio.volume;
	},		
	
	/*
	onAudioSrcChanged: function (event)
	{
		
		
	},
	*/
	
	
	onError_Play: function (event)
	{
		this.log();
		if(this._boolPlayingBeforeError){
			this.onAudioPaused(event);
			this._boolPlayingBeforeError = true;
			event.forcePlay = true;
		}else{
			event.forcePlay = false;
		}
		this.doAudioError(event);
	},
	
	onError_Stall: function (event)
	{
		this.log();
		this.log();
		if(this._boolPlayingBeforeError){
			this.onAudioPaused(event);
			this._boolPlayingBeforeError = true;
			event.forcePlay = true;
		}else{
			event.forcePlay = false;
		}
		this.doAudioError(event);
	},
	
	onError_Disconnect: function (event)
	{
		this.doAudioError(event);
	},
	
	onError_Watchdog: function (event)
	{
		this.doAudioError(event);		
	},
	
	getAudioCurrentTime: function ()
	{
		return this.objAudio.currentTime;
	},
	
	getAudioDuration: function()
	{
		
		return this.objAudio.duration;
	}
	
	
	
	
	
	
	
	
	
	
	
});