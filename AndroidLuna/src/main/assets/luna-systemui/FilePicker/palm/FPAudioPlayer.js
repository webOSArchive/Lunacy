enyo.kind({
    name: "FPAudioPlayer",
    kind: "Component",
    published: {
            src: null,
            playing: false
    },
    events: {
            onProgressUpdate: ''
    },
    
    create: function() {
        this.inherited(arguments);
        this.status = "stop";
        this.timer = undefined;
        this.setupAudio();
        this.updateProgress = enyo.bind(this, this.updateProgress);
    },
    
    setupAudio: function() {		
		if (this.objAudio === undefined)
		{
			this.objAudio = new Audio();
			this.objAudio.setAttribute("x-palm-media-audio-class", "media");
			
			this.objAudio.addEventListener('play', enyo.bind(this, this.onAudioPlayed), false);
			this.objAudio.addEventListener('playing', enyo.bind(this, this.onAudioPlaying), false);
			this.objAudio.addEventListener('ended', enyo.bind(this, this.onAudioEnded), false);
			this.objAudio.addEventListener('pause', enyo.bind(this, this.onAudioPaused), false);
			
			this.objAudio.addEventListener('error', enyo.bind(this, this.onError_Play), false);
			this.objAudio.addEventListener('stalled', enyo.bind(this, this.onError_Stall), false);
		}		
    },
    
    
	playAudio: function (strAudioFile, intStartTime)
	{
		try
		{
			this.objAudio.src = strAudioFile;
			
			this.objAudio.load();
			this.objAudio.play();			
		}
		catch (err)
		{
			console.log("playAudio error: " + err);
		}
	},
	
	stopAudio: function() {
		this.objAudio.pause();
	},
   
    playPause: function() {
        if (!this.playing) {
        	this.$.objAudio.play();
        } else {
            this.$.objAudio.pause();
        }
    },
   
    updateProgress: function() {
    	
        this.doProgressUpdate(this.status, this.objAudio.currentTime, this.objAudio.duration);
    },
   
	onAudioPlayed: function (event)
	{
		this.status = "play";
		this.doProgressUpdate(this.status, this.objAudio.currentTime, this.objAudio.duration);
		try {
			if(this.timer)
				clearInterval(this.timer);
			 this.timer = undefined;
		}catch(err) {}
		this.timer = window.setInterval(this.updateProgress, 1000);
	},
	
	onAudioPlaying: function (event)
	{
		this.playing = true;
		this.status = "playing";
	},
	
	onAudioPaused: function (event)
	{
		this.playing = false;
		this.status = "stop";
		try {
			if(this.timer)
				clearInterval(this.timer);
			 this.timer = undefined;
		}catch(err) {}
	},
	
	onAudioEnded: function (event)
	{
		this.playing = false;
		this.status = "stop";
		this.doProgressUpdate(this.status, this.objAudio.currentTime, this.objAudio.duration);
		try{
			if(this.timer)
				clearInterval(this.timer);
			 this.timer = undefined;
		}catch(err) {}
	},
	
	onError_Play: function (event)
	{
		this.playing = false;
		this.status = "stop";
		this.doProgressUpdate(this.status, this.objAudio.currentTime, this.objAudio.duration);
	},
	
	onError_Stall: function (event)
	{
		this.playing = false;
		this.status = "stop";
		this.doProgressUpdate(this.status, this.objAudio.currentTime, this.objAudio.duration);
	},
   

});
