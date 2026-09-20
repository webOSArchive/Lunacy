/**
 * HTML5 Video Wrapper
 */
enyo.kind({
	name: "help.Video",
	kind: enyo.Control,
	videoEvents: [ 
       "loadstart", "progress", "suspend", "abort", "error", "emptied",
       "stalled", "loadedmetadata", "loadeddata", "waiting", "playing", 
       "canplay", "canplaythrough", "seeking", "seeked", "timeupdate",
       "ended", "durationchange", "play", "pause", "ratechange"
	],
	nativeWidth: 0,
	nativeHeight: 0,
	aspectRatio: 1,
	duration: 0,
	buffered: 0,
	bufferedPercent: 0,
	streaming: false,
	published: {
		src: "",
		showControls: false,
		autoplay: false,
		loop: false,
		fitVideo: false,
		width: 0,
		height: 0
	},
	events: {
		// The user agent begins looking for media data, as part of the resource selection algorithm.
		onloadstart: "",
		// The user agent is fetching media data.
		onprogress: "",
		// The user agent is intentionally not currently fetching media data, but does not have the entire media resource downloaded.
		onsuspend: "",
		// The user agent stops fetching the media data before it is completely downloaded, but not due to an error.
		onabort: "",
		// An error occurs while fetching the media data.
		onerror: "",
		// A media element whose networkState was previously not in the NETWORK_EMPTY state has just switched to that state (either because of a fatal error during load that's about to be reported, or because the load() method was invoked while the resource selection algorithm was already running).
		onemptied: "",
		// The user agent is trying to fetch media data, but data is unexpectedly not forthcoming.
		onstalled: "",
		// The user agent has just determined the duration and dimensions of the media resource and the text tracks are ready.
		onloadedmetadata: "",
		// The user agent can render the media data at the current playback position for the first time.
		onloadeddata: "",
		// Playback has stopped because the next frame is not available, but the user agent expects that frame to become available in due course.
		onwaiting: "",
		// Playback has started.
		onplaying: "",
		// The user agent can resume playback of the media data, but estimates that if playback were to be started now, the media resource could not be rendered at the current playback rate up to its end without having to stop for further buffering of content.
		oncanplay: "",
		// The user agent estimates that if playback were to be started now, the media resource could be rendered at the current playback rate all the way to its end without having to stop for further buffering.
		oncanplaythrough: "",
		// The seeking IDL attribute changed to true and the seek operation is taking long enough that the user agent has time to fire the event.
		onseeking: "",
		// The seeking IDL attribute changed to false.
		onseeked: "",
		// The current playback position changed as part of normal playback or in an especially interesting way, for example discontinuously.
		ontimeupdate: "",				
		// Playback has stopped because the end of the media resource was reached.
		onended: "",
		// The duration attribute has just been updated.
		ondurationchange: "",	
		// Playback has begun. Fired after the play() method has returned, or when the autoplay attribute has caused playback to begin.
		onplay: "",
		// Playback has been paused. Fired after the pause() method has returned.
		onpause: "",
		// Either the defaultPlaybackRate or the playbackRate attribute has just been updated.
		onratechange: ""
	},
	components: [
		{ kind: "PalmService", name: "mediaserver" }
	],
	//* @protected
	nodeTag: "video",
	create: function() {
		this.inherited(arguments);
		this.widthChanged();
		this.heightChanged();
		this.showControlsChanged();
		this.autoplayChanged();
		this.loopChanged();
	},
	destroy: function() {
		var i;
		// Clean up the event listeners
		for (i = 0; i < this.videoEvents.length; ++i) {
			var event = this.videoEvents[i],
				bindHandler = event + "Bind";
			this.node.removeEventListener(event, this[bindHandler], true);
		}
		this.inherited(arguments);
	},
	rendered: function() {
		this.inherited(arguments);
		this.initVideoNode();
		this.srcChanged();
	},
	initVideoNode: function() {
		var i;
		if (this.hasNode()) {
			this.node.setAttribute("x-palm-media-audio-class", "media");
			// Install the event listeners
			for (i = 0; i < this.videoEvents.length; ++i) {
				var event = this.videoEvents[i],
					handler = event + "Handler",
					doHandler = "do" + enyo.cap(event),
					bindHandler = event + "Bind";
				
				if (!this[handler]) {
					this[handler] = this[doHandler];
				}				
				this[bindHandler] = enyo.bind(this, handler);
				this.node.addEventListener(event, this[bindHandler], true);
			}
		} else {
			this.log("Oops! Problem with the video node!");
		}
	},
	resize: function() {
		this.adjustSize();
	},
	/*
	 * Video Event Handler
	 */
	loadedmetadataHandler: function(inEvent) {
		this.nativeWidth = this.node.videoWidth;
		this.nativeHeight = this.node.videoHeight;
		this.aspectRatio = (this.nativeHeight > 0) ? this.nativeWidth / this.nativeHeight : 1;
		this.duration = this.node.duration;
		this.streaming = !isFinite(this.duration);
		this.mediadService = this.node.getAttribute("x-palm-media-control");
		this.fitVideoChanged();
		this.doLoadedmetadata(inEvent);
	},	
	progressHandler: function(inEvent) {
		if (!this.streaming) {
			this.buffered = (this.node.buffered.length) ? this.node.buffered.end(this.node.buffered.length - 1) : 0;
			this.bufferedPercent = this.duration ? Math.floor((this.buffered * 100) / this.duration) : 0;
		}
		this.doProgress(inEvent);
	},
	canplaythroughHandler: function(inEvent) {
		if (!this.streaming) {
			this.buffered = (this.node.buffered.length) ? this.node.buffered.end(this.node.buffered.length - 1) : 0;
			this.bufferedPercent = this.duration ? Math.floor((this.buffered * 100) / this.duration) : 0;
		}
		this.doCanplaythrough(inEvent);
	},
	timeupdateHandler: function(inEvent) {
		if (!this.streaming) {
			var oldPercent = this.bufferedPercent;
			this.buffered = (this.node.buffered.length) ? this.node.buffered.end(this.node.buffered.length - 1) : 0;
			this.bufferedPercent = this.duration ? Math.floor((this.buffered * 100) / this.duration) : 0;
			if (oldPercent !== this.bufferedPercent) {
				this.doProgress(inEvent);
			}
		}
		this.doTimeupdate(inEvent);
	},
	ondurationchange: function(inEvent) {
		this.duration = this.node.duration;
		this.streaming = !isFinite(this.duration);
		this.doDurationchange(inEvent);
	},
	/*
	 * Published Properties
	 */
	widthChanged: function() {
		this.applyStyle("width", this.width + "px");
	},
	heightChanged: function() {
		this.applyStyle("height", this.height + "px");
	},
	srcChanged: function() {
		var path = enyo.path.rewrite(this.src);
		this.setAttribute("src", path);
	},
	showControlsChanged: function() {
		this.setAttribute("controls", this.showControls ? "controls" : null);
	},
	autoplayChanged: function() {
		this.setAttribute("autoplay", this.autoplay ? "autoplay" : null);
	},
	loopChanged: function() {
		this.setAttribute("loop", this.loop ? "loop" : null);
	},
	fitVideoChanged: function() {
		this.adjustSize();	
	},
	//* @public
	play: function() {
		if (this.hasNode()) {
			try {
				if (!this.node.paused) {
					this.node.currentTime = 0;
				} else {
					this.node.play();
				}
			} catch (e) {
				this.doError();
			}			
		}
	},
	pause: function() {
		if (this.hasNode()) {
			try {
				this.node.pause();
			} catch (e) {
				this.doError();
			}			
		}
	},
	stop: function() {
		if (this.hasNode()) {
			this.pause();
			try {
				this.node.currentTime = 0;
				this.nativeWidth = 0;
				this.nativeHeight = 0;
				this.duration = 0;
				this.aspectRatio = 1;
				this.buffered = 0;
				this.bufferedPercent = 0;
				this.streaming = false;
				this.setWidth(0);
				this.setHeight(0);
			} catch (e) {
				this.doError();
			}
			this.streaming = false;
			this.setSrc("");
		}
	},
	rewind: function() {
		if (this.hasNode()) {
			try {
				this.node.currentTime = 0;
			} catch (e) {
				this.doError();
			}
		}
	},
	setCurrentTime: function(time) {
		if (this.hasNode()) {
			try {
				if (time < this.buffered) {
					this.node.currentTime = time;
				}
			} catch (e) {
				this.doError();
			}
		}
	},
	getCurrentSrc: function() {
		return this.hasNode() ? this.node.currentSrc : "";
	},
	getCurrentTime: function() {
		return this.hasNode() ? this.node.currentTime : 0;
	},
	getDuration: function() {
		return this.duration || 0;
	},
	getNativeWidth: function() {
		return this.nativeWidth || 0;
	},
	getNativeHeight: function() {
		return this.nativeHeight || 0;
	},
	getAspectRatio: function() {
		return this.aspectRatio || 1;
	},
	getBufferedPercent: function() {
		return this.bufferedPercent || 0;
	},
	adjustSize: function() {
		var containerWidth = this.node.parentElement.clientWidth,
			containerHeight = this.node.parentElement.clientHeight,
			width, height;
		
		if (this.duration === 0) {
			// We didn't get the metaloaded event
			return;
		}
		// FIXME: For now, the mediaserver doesn't set the native width/height 
		// on the video object so until it's fixed or something else changes 
		// fill the screen.
		if (window.PalmSystem) {
			this.log("Setting video size: " + width + "x" + height);
			this.setWidth(containerWidth);
			this.setHeight(containerHeight);
			
			// Call mediaserver's fit/fill interface
			if (this.mediadService) {
				var requestParam = this.fitVideo ? { args: [ "VIDEO_FILL" ] } : { args: [ "VIDEO_FIT" ] };
				this.$.mediaserver.call(requestParam, { service: this.mediadService, method: "setFitMode" });
			}
		} else {
			if (this.fitVideo || containerWidth < this.nativeWidth || containerHeight < this.nativeHeight) {
				width = containerWidth;
				height = Math.floor(width / this.aspectRatio);
				if (height > containerHeight) {
					height = containerHeight;
					width = Math.floor(height * this.aspectRatio);
				}
			} else {
				width = this.nativeWidth;
				height = this.nativeHeight;
			}
			this.log("Setting video size: " + width + "x" + height);
			this.setWidth(width);
			this.setHeight(height);
		}		
	}
});
