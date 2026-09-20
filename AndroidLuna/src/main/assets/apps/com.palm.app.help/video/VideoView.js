/**
 * VideoIconButton is a custom button that doesn't show
 * the button background. Used in the VideoView. 
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.VideoIconButton",
	kind: "CustomButton",
	published: {
		icon: ""
	},
	components: [
        {name: "icon"}
	],
	create: function() {
		this.inherited(arguments);
		this.iconChanged();
	},
	iconChanged: function() {
		this.$.icon.setClassName("video-icon " + this.icon);
	}
});

/**
 * VideoView is a component used to play a video.
 * The video is centered on a black background and has a control bar at the top
 * with a Back button and a control bar at the bottom with video controls. 
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.VideoView",
	kind: "VFlexBox",
	className: "video-view",
	align: "center",
	pack: "center",
	events: { 
		onBack: ""
	},
	components: [
	    {kind: "help.Video", name: "video", showControls: false, autoplay: false, loop: false, fitVideo: true,
			onloadedmetadata: "handleMetadata",
			onerror: "handleError",
			onended: "handleEnded",
			onprogress: "handleProgress",
			onplaying: "handlePlaying",
			onloadstart: "handleLoadStart",
			onloadeddata: "handleLoadedData",
			onstalled: "handleStalled",
			onwaiting: "handleWaiting",
			oncanplay: "handleCanPlay",
			oncanplaythrough: "handleCanPlay",
			onseeking: "handleSeeking",
			onseeked: "handleSeeked",
			ontimeupdate: "handleTimeUpdate",
			ondurationchange: "handleDurationChange",
			onplay: "handlePlay",
			onpause: "handlePause",
			onratechange: "handleRateChange"
		},
		{kind: "VFlexBox", name: "scrim", className: "enyo-view video-scrim", align: "center", pack: "center", onclick: "handleScrimClick", components: [
			{kind: "SpinnerLarge", name: "spinner", showing: false},
			{kind: "Control", name: "bigplay", className: "video-big-play", onclick: "resumePlayback", showing: false},
			{kind: "VFlexBox", name: "videofail", flex: 1,  pack: "center", align: "center", style: "color: grey;", showing: false, components: [
				{kind: "Image", src: "images/warning-large.png", onclick: "showFailDetails"},
				{kind: "Control", content: $L("Failed to load the requested video.")},
				{kind: "Control", name: "failDetails", showing: false}
			]}			
		]},
		{kind: "HFlexBox", name: "topBar", className: "video-controls-top", align: "center", components: [
			{kind: "Button", caption: $L("Clips"), className: "enyo-button-dark", onclick: "goBack"},
			{flex: 1},
			{kind: "Control", name: "videoTitle", className: "enyo-text-ellipsis"}
		]},		
		{kind: "HFlexBox", name: "bottomBar", className: "video-controls-bottom", align: "center", components: [
			{kind: "help.VideoIconButton", name: "play", icon: "video-play", onclick: "togglePlayback"},
			{kind: "Control", name: "time", className: "video-time"},
			{kind: "ProgressSlider", name: "seek", flex: 1, className: "video-slider", 
				minimum: 0, maximum: 100, snap: 1, lockBar: true, animatePosition: false, 
				onChanging: "handleSeekChanging", onChange: "handleSeekChange"},
			{kind: "Control", name: "remaining", className: "video-time"},
			{kind: "help.VideoIconButton", name: "zoom", icon: "video-normal", onclick: "toggleZoom"}
		]},
		{ kind: "PalmService", name: "headsetService", service: "palm://com.palm.keys/headset/", method: "status", subscribe: true, onSuccess: "handleHeadsetStatus"}
	],
	create: function() {
		this.inherited(arguments);
		this.state = "LOADING";
		this.zoomed = true;
		this.$.seek.$.button.hide();
		this.durationShortFormatter = new enyo.g11n.DurationFmt({ style: "short" });
		this.updateProgressTime();
		if (window.PalmSystem) {
			this.$.headsetService.call({});
		}
	},
	/**
	 * Resize the the video
	 */
	resize: function() {
		this.resizeHandler();
	},
	/**
	 * Resize Handler (when view is used as the main window)
	 */
	resizeHandler: function() {
		this.inherited(arguments);
		this.$.video.resize();
	},
	/**
	 * Handle mouse move
	 */
	mousemoveHandler: function() {
		if (this.toolBarsHidden) {
			this.showToolbars();
			this.setToolbarTimeout(4000);
		} else if (this.toolbarTimeout) {
			this.setToolbarTimeout(4000);
		}
	},
	/**
	 * Called when the window is minimized
	 */
	deactivate: function() {
		if (this.state === "PLAYING") {
			this.pauseVideo();
		}
	},
	/**
	 * Turn on/off full-screen and lock the window orientation
	 */
	setOrientation: function (fullScreen) {
		if (window.PalmSystem) {
			if (fullScreen) {
				this.lockWindowOrientation();
			} else {
				this.unlockWindowOrientation();
			}
		}
	},
	/**
	 * Lock the window orientation to the left (the way video plays)
	 */
	lockWindowOrientation: function () {
		if (!this.isOrientationLocked && window.PalmSystem) { 
			this.isOrientationLocked = true;
			enyo.setAllowedOrientation(window.PalmSystem.videoOrientation || "left");
		}
	},
	/**
	 * Unlock the window orientation
	 */
	unlockWindowOrientation: function () {
		if (this.isOrientationLocked && window.PalmSystem) {
			this.isOrientationLocked = false;
			enyo.setAllowedOrientation("free");
		}
	},
	/**
	 * Hide the toolbars (top and bottom)
	 */
	hideToolbars: function() {
		this.clearToolbarTimeout();
		this.$.topBar.addClass("hide");
		this.$.bottomBar.addClass("hide");
		this.toolBarsHidden = true;
	},
	/**
	 * Show the toolbars (top and bottom)
	 */
	showToolbars: function() {
		this.clearToolbarTimeout();
		this.$.topBar.removeClass("hide");
		this.$.bottomBar.removeClass("hide");
		this.toolBarsHidden = false;
	},
	/**
	 * Set the toolbar auto-hide timeout
	 */
	setToolbarTimeout: function(timeout) {
		this.clearToolbarTimeout();
		this.toolbarTimeout = setTimeout(enyo.bind(this, "hideToolbars"), timeout);
	},
	/**
	 * Stop the toolbar auto-hide timeout
	 */
	clearToolbarTimeout: function() {
		if (this.toolbarTimeout) {
			clearTimeout(this.toolbarTimeout);
			this.toolbarTimeout = null;
		}
	},
	/**
	 * Handle middle big play button
	 */
	resumePlayback: function(inSender, inEvent) {
		inEvent.stopPropagation();
		this.togglePlayback(inSender, inEvent);
	},
	/**
	 * Handle clicks on the scrim - toggle toolbar show/hide
	 */
	handleScrimClick: function(inSender, inEvent) {
		if (this.toolBarsHidden) {
			this.showToolbars();
		} else {
			this.hideToolbars();
		}
	},
	/**
	 * Handle seek changing events
	 */
	handleSeekChanging: function(inSender, inPosition) {
		var time = (this.$.video.getDuration() * inPosition) / 100;
		if (this.state === "PLAYING") {
			this.wasPlaying = true;
			this.pauseVideo();
		}
		this.requestVideoSeek(time, false);
	},
	/**
	 * Handle seek change events
	 */
	handleSeekChange: function(inSender, inPosition) {
		var time = (this.$.video.getDuration() * inPosition) / 100;
		if (this.wasPlaying || this.state === "PLAYING") {
			this.pauseVideo();
			this.requestVideoSeek(time, true);
			this.playVideo();
			this.startMonitor();
			this.wasPlaying = false;
		} else {
			this.requestVideoSeek(time, true);			
		}
		
	},
	/**
	 * Request a video seek 
	 */
	requestVideoSeek: function (time, isLast) {
		if (this.videoSeekingRequestPending && !isLast) {
            this.seekToTime = time;
        } else {
        	this.videoSeekingRequestPending = (isLast) ? undefined : true;
        	this.seekToTime = undefined;
        	this.$.video.setCurrentTime(time);
        	this.updateProgressTime(time);
        }
	},
	/**
	 * Handle headset events
	 */
	handleHeadsetStatus: function (inService, inResponse) {
		if (inResponse && inResponse.key === "headset" && inResponse.state === "up") {
			// Headset is unplugged
			this.pauseVideo();
		}
    },

	
	/*
	 * Handle video events
	 */
	handleMetadata: function(inSender, inEvent) {
		this.log("Metadata: width = " + this.$.video.nativeWidth + 
				" / height = " + this.$.video.nativeHeight +
				" / duration = " + this.$.video.duration);
		if (!this.$.video.streaming) {
			this.$.seek.$.button.show();
		}
	},
	handleError: function(inSender, inEvent) {
		this.log();
		this.stopMonitor();
		this.setState("ERROR");
		this.showToolbars();
	},
	handleEnded: function(inSender, inEvent) {
		this.log();
		this.stopMonitor();
		this.pauseVideo();
		this.$.video.rewind();
		this.updateProgressTime();
		this.updateSeeker();
	},
	handleCanPlay: function(inSender, inEvent) {
		this.setState("PLAYING");
		this.setToolbarTimeout(4000);
		if (!this.$.video.streaming) {
			this.log("CanPlay: " + this.$.video.bufferedPercent);
			this.$.seek.setAltBarPosition(this.$.video.bufferedPercent);
		}
	},
	handleProgress: function(inSender, inEvent) {
		if (!this.$.video.streaming) {
			this.log("Progress: " + this.$.video.bufferedPercent);
			this.$.seek.setAltBarPosition(this.$.video.bufferedPercent);
		}
	},
	handleLoadedData: function(inSender, inEvent) {
		if (!this.$.video.streaming) {
			this.log("LoadedData: " + this.$.video.bufferedPercent);
			this.$.seek.setAltBarPosition(this.$.video.bufferedPercent);
		}
	},	
	handlePlaying: function(inSender, inEvent) {
		this.log();
		this.startMonitor();
	},
	handleWaiting: function(inSender, inEvent) {
		this.log();
		this.stopMonitor();
		this.setState("LOADING");
		this.showToolbars();
	},
	handleLoadStart: function(inSender, inEvent) {
		this.log();
	},
	handleStalled: function(inSender, inEvent) {
		this.log();		
	},
	handleSeeking: function(inSender, inEvent) {
		this.log();
	},
	handleSeeked: function(inSender, inEvent) {
		this.log();
		this.videoSeekingRequestPending = undefined;
		var newTime = this.seekToTime;
		if (newTime) {
			this.seekToTime = undefined;
			this.requestVideoSeek(newTime, false);
		}
	},
	handleDurationChange: function(inSender, inEvent) {
		this.log("Duration: " + this.$.video.duration);
	},
	handlePlay: function(inSender, inEvent) {
		this.log();
	},
	handlePause: function(inSender, inEvent) {
		this.log();
	},
	handleRateChange: function(inSender, inEvent) {
		this.log();
	},
	
	/**
	 * Monitor the video progess and update the UI 
	 */
	startMonitor: function () {
		if (!this.playMonitorId) { 
			this.playMonitorId = setInterval(function () {
				this.updateProgressTime();
			}.bind(this), 1000);
			this.updateSeekerId = setInterval(function () {
				this.updateSeeker();
			}.bind(this), 80);
		}
	},
	/**
	 * Stop monitoring the video progress
	 */
	stopMonitor: function () {
		if (this.playMonitorId) {
			clearInterval(this.playMonitorId);
			this.playMonitorId = null;
			clearInterval(this.updateSeekerId);
			this.updateSeekerId = null;
		}
	},
	/**
	 * Update the progress time
	 */
	updateProgressTime: function (timeOverride) {
		var current = timeOverride || this.$.video.getCurrentTime(),
			duration = this.$.video.getDuration();
		
		this.$.time.setContent(this.secondsToTimeString(current));
		if (!this.$.video.streaming) {
			this.$.remaining.setContent("-" + this.secondsToTimeString(duration - current));
		} else {
			this.$.remaining.setContent("--:--");
		}
	},
	/**
	 * Update the seeker position
	 */
	updateSeeker: function () {
		if (!this.$.video.streaming) {
			var percentage = Math.floor((this.$.video.getCurrentTime() * 100) / this.$.video.getDuration());
			this.$.seek.setPosition(percentage);
			this.$.seek.setAltBarPosition(this.$.video.bufferedPercent);
		}
	},
	/**
	 * Convert seconds to a nice string (eg "0:12")
	 */
	secondsToTimeString: function (seconds) {
		var hours = Math.floor(seconds / 3600);
		seconds = seconds - (hours * 3600);
		var minutes = Math.floor(seconds / 60); 
		seconds = seconds - (minutes * 60);
		seconds = Math.round(seconds);
		
		return this.durationShortFormatter.format({'hours': hours, 'minutes': minutes, 'seconds': seconds});
	},
	
	/**
	 * Handle video pane closing
	 */
	goBack: function(inSender, inEvent) {
		this.stopVideoPlayback();
		this.doBack();
	},
	/**
	 * Toggle playback
	 */
	togglePlayback: function(inSender, inEvent) {
		if (this.state === "PLAYING") {
			this.pauseVideo();
		} else {
			this.playVideo();
		}
	},
	/**
	 * Toggle zoom
	 */
	toggleZoom: function(inSender, inEvent) {
		if (this.zoomed) {
			this.zoomed = false;
			this.$.zoom.setIcon("video-zoom");
			this.$.video.setFitVideo(false);
		} else {
			this.zoomed = true;
			this.$.zoom.setIcon("video-normal");
			this.$.video.setFitVideo(true);
		}
	},
	/**
	 * Show the video source
	 */
	showFailDetails: function(inSender, inEvent) {
		inEvent.stopPropagation();
		this.$.failDetails.setContent("URL :" + this.videoSource);
		this.$.failDetails.show();
	},
	
	/**
	 * Start video playback
	 */
	startVideoPlayback: function(inSrc, inTitle) {
		this.videoSource = inSrc;
		this.setOrientation(true);
		this.$.videoTitle.setContent(inTitle || "");
		this.$.video.setSrc(inSrc || "");
		this.zoomed = true;
		this.$.zoom.setIcon("video-normal");
		this.$.video.setFitVideo(true);
		this.playVideo();
	},
	/**
	 * Stop video playback
	 */
	stopVideoPlayback: function() {
		this.stopMonitor();
		this.$.video.stop();
		this.$.time.setContent(this.secondsToTimeString(0));
		this.$.remaining.setContent("-" + this.secondsToTimeString(0));
		this.$.seek.setPosition(0);
		this.$.seek.setAltBarPosition(0);
		this.$.seek.$.button.hide();
		this.$.failDetails.hide();
		this.setOrientation(false);
	},
	
	/**
	 * Play/Resume the video
	 */
	playVideo: function() {
		this.$.video.play();
		this.setState("PLAYING");
		this.setToolbarTimeout(4000);
		if (window.PalmSystem) {
			window.PalmSystem.setWindowProperties({ blockScreenTimeout: true });
		}
	},
	/**
	 * Pause the video
	 */
	pauseVideo: function() {
		this.stopMonitor();
		this.$.video.pause();
		this.setState(this.wasPlaying ? "PLAYING" : "PAUSED");
		this.showToolbars();
		if (window.PalmSystem) {
			window.PalmSystem.setWindowProperties({ blockScreenTimeout: false });
		}
	},
	/**
	 * State manager
	 */
	setState: function(inState) {
		this.state = inState;
		switch (inState) {
			case "LOADING":
				this.$.bigplay.hide();
				this.$.videofail.hide();
				this.$.spinner.setShowing(true);				
				break;
			case "PLAYING":
				this.$.bigplay.hide();
				this.$.videofail.hide();
				this.$.spinner.setShowing(false);
				this.$.play.setIcon("video-pause");
				break;
			case "PAUSED":
				this.$.bigplay.show();
				this.$.videofail.hide();
				this.$.spinner.setShowing(false);
				this.$.play.setIcon("video-play");
				break;
			case "ERROR":
				this.$.bigplay.hide();
				this.$.spinner.setShowing(false);
				this.$.videofail.show();
				break;
		}
	}
});


