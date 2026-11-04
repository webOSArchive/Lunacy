/*globals enyo, setTimeout, $L, event, Utilities, window */
enyo.kind({
	kind: "Toolbar",
	name: "kindPlayerControl",
	height: "80px",
	className: "enyo-toolbar enyo-hflexbox controls",
	events: {
		onClickPrev: "",
		onClickPlayPause: "",
		onClickNext: "",
		onShuffleClick: "",
		onRepeatClick: "",
		onSetVolume: "",
		onRequestVolume: "",
		onSetPlaybackTime: "",
		onClickFullScreen: ""
	},
	published: {boolDraggingSlider: false, playEnabled: false, prevNextEnabled: false},
	components: [
			{kind: "Control", className: "playback", layoutKind: "HFlexLayout", pack: "start", align: "center", components: [
				{name: "btnPrev", kind: "IconButton", className: "prev", icon:"images/btn_controls_prev.png", onclick: "onclick_prev", disabled: true}, // This needs to be changed to switch icons like btnPlay
				{name: "btnPlay", kind: "IconButton", className: "play paused", icon:"images/btn_controls_play.png", label: " ", onclick: "onclick_playpause", disabled: true},
				{name: "btnNext", kind: "IconButton", className: "next", icon:"images/btn_controls_next.png", onclick: "onclick_next", disabled: true} // This needs to be changed to switch icons like btnPlay
			]},
			{kind: "Spacer", style:"max-width:25px;"},
			/*
			{kind: "Control", flex:1, layoutKind: "VFlexLayout", align: "center", pack:"justify", components: [
				{kind: "HFlexBox", align: "center", className: "current", pack: "justify", components: [
					{name: "btnRepeat", kind: "Control", className: "toggleMode repeat", onclick: "doRepeatClick"}, //possible class values are off/on/one - let me know if switching classes is harder than say... set styles
					{kind: "Control", pack:"justify", align: "center", flex:1, className: "info", components: [
						{name: "lblArtistName", content: "", className: "artist"},
						{name: "lblSongTitle", content: "", className: "title"}
			*/
			{kind: "Control", name: "centerBox", flex:1, align: "center", pack:"justify", components: [
				{kind: "Control", name: "innerBox", style: "width: 250px; height: 68px; overflow: hidden;", align: "center", components: [
					{kind: "HFlexBox", align: "center", className: "current", pack: "justify", components: [
						{name: "btnRepeat", kind: "Control", className: "toggleMode repeat", onclick: "doRepeatClick"}, //possible class values are off/on/one - let me know if switching classes is harder than say... set styles
						{kind: "Control", pack:"justify", align: "center", flex:1, className: "info", components: [
							{name: "lblArtistName", content: "", className: "artist"},
							{name: "lblSongTitle", content: "", className: "title"}
						]},
						{name: "btnShuffle", kind: "Control", className: "toggleMode shuffle", onclick:"doShuffleClick"} //possible class values are off/on - let me know if switching classes is harder than say... set styles
					]},
					/*
					{name: "btnShuffle", kind: "Control", className: "toggleMode shuffle", onclick:"doShuffleClick"} //possible class values are off/on - let me know if switching classes is harder than say... set styles
					
				]},
				{kind: "Control", layoutKind: "HFlexLayout", className: "progress", pack: "center", components: [//JC_UI
					{name: "lblSongTime", kind: "Control", className: "label elapsed", content: $L("--:--")},
					{name: "sliderSongTime", kind: "ProgressSlider", onChange: "onChange_sliderSongTime", onChanging: "onChanging_sliderSongTime", onclick: "onClick_sliderSongTime", tapPosition: true, lockBar: true, position:0, flex:1, disabled: true},
					{name: "lblSongDuration", kind: "Control", className: "label duration", content: $L("--:--")},
					*/
						{kind: "Control", layoutKind: "HFlexLayout", className: "progress", pack: "center", components: [
						{name: "lblSongTime", style: "width: 67px; height: 14px; overflow: hidden;", kind: "Control", className: "label elapsed", content: $L("--:--")},
						{name: "sliderSongTime", kind: "ProgressSlider", onChange: "onChange_sliderSongTime", onChanging: "onChanging_sliderSongTime", onmousedown: "onClick_sliderSongTime", animatePosition: false, tapPosition: true, lockBar: true, position: 0, flex:1},
						{name: "lblSongDuration", kind: "Control", className: "label duration", content: $L("--:--")}
					]}
				]}
			]},
			{kind: "Spacer", style:"max-width:15px;"},
			/*
			{kind: "Control", layoutKind: "VFlexLayout", pack: "justify", width:"190px", align: "end", components:[						//JC_UI
				{name: "btnFullscreen", kind: "Control", className: "toggleMode fullscreen", onclick: "onclick_FullScreen"},
			*/
			{kind: "Control", width:"190px", height: "64px", style: "overflow: hidden;", align: "end", components:[
				{name: "btnFullscreen", kind: "Control", style: "position: relative; left: 140px;", className: "toggleMode fullscreen", onclick: "onclick_FullScreen", onmousedown: "onmousedown_FullScreen"},				
				{name: "sliderVolume", kind: "ProgressSlider", lockBar: true, position:0, width:"142px", className: "volume", onChange: "onChange_sliderVolume", onChanging: "onChanging_sliderVolume"}
			]}
	],
	
	
	
	_boolUpdateSlider: true,
	_boolUpdateTimeDisplay: true,	
	_boolUpdateVolumeSlider: true,
	
	_intCurrentDuration: 0,
	
	resized: function()
	{
		this.$.innerBox.addStyles("width:" + this.$.centerBox.getBounds().width + "px;");
	},
	
	
	create: function ()
	{
		this.inherited(arguments);
		
		this.doRequestVolume(enyo.bind(this,this.onGetVolume));
		
		this.resized();
	},
	
	
	
	onclick_next: function()
	{
		this.log("onclick_next");
		this.doClickNext();
	},
		
	onclick_prev: function()
	{
		this.log("onclick_prev");
		this.doClickPrev();
	},
		
	onclick_playpause: function()
	{
		this.log("onclick_playpause");
		this.doClickPlayPause();
		
	},
	
	
	onclick_FullScreen: function ()
	{
		this.log();
		this.doClickFullScreen();
		
	},
	
	onmousedown_FullScreen: function ()
	{
		this.log();
		this.$.btnFullscreen.addClass("blue");		
	},
	
	
	playEnabledChanged: function ()
	{
		this.log();
		this.$.btnPlay.setDisabled(!this.playEnabled);
	},
	
	
	 prevNextEnabledChanged: function ()
	 {
		this.log();
		//This all needs to be redone to switch classes instead of src.
		this.$.btnPrev.setDisabled(!this.prevNextEnabled);
		this.$.btnNext.setDisabled(!this.prevNextEnabled);
	 },

	setPlayPause: function (boolAudioPlaying)
	{
		
		this.log(boolAudioPlaying);
		this.$.btnPlay.addRemoveClass("paused", !boolAudioPlaying);
		//this.$.btnPlay.srcChanged();
		
	},
	
		
	setFullscreen: function(boolFullscreen)
	{
		this.$.btnFullscreen.addRemoveClass("on", boolFullscreen);
		if(this.$.btnFullscreen.hasClass("blue")){
			this.$.btnFullscreen.removeClass("blue");
		}
		else
		{
			this.$.btnFullscreen.addClass("blue");
			//ack fix for DFISH-22435
			setTimeout(enyo.bind(this, function(){this.$.btnFullscreen.removeClass("blue");}) , 500);
		}
	
	},
	
	
	updateTrackInfoDisplay: function (objTrackInfo)
	{
		//this.log();
		try
		{
			
			if(this.intervalCheckTrackTime !== undefined)
			{
				window.clearInterval(this.intervalCheckTrackTime);
			}
			
			this.$.lblSongTitle.setContent(objTrackInfo.strTrackTitle);
//			this.$.lblArtistName.setContent(objTrackInfo.strTrackArtist + " - "+ objTrackInfo.strTrackAlbum); design says no album name :/
			this.$.lblArtistName.setContent(objTrackInfo.strTrackArtist);

			
			this._boolUpdateSlider = true;
			this._boolUpdateTimeDisplay = true;
			//this._boolUpdateVolumeSlider = true;
			
			
		}
		catch(err)
		{
			this.log("**** updateTrackDisplay error: " + err);
		}
		
	},	
		
	updateTrackTimeDisplay: function (objTrackTimes)
	{
		//this.log(objTrackTimes);
		//this.log("updateTrackTimeDisplay objTrackTimes: " + objTrackTimes);
		//this.log("objTrackTimes: " + objTrackTimes);
		//this.log(this._boolUpdateTimeDisplay);

		if(this._boolUpdateTimeDisplay)
		{
			this.$.lblSongTime.setContent(Utilities.formatTime(objTrackTimes.floatTrackCurrentTime));
			//this.$.lblSongTime.setContent(Math.floor(objTrackTimes.floatTrackCurrentTime));
			this._intCurrentDuration = objTrackTimes.floatTrackDuration;
			this.$.lblSongDuration.setContent(Utilities.formatTime(this._intCurrentDuration));
			//this.$.lblSongDuration.setContent(Math.floor(this._intCurrentDuration));
		}

		if(this._boolUpdateSlider)
		{
			//set to 0 if fix for jumping to end before we get the duration
			var intBarPos = 0;
			if(this._intCurrentDuration>0)
			{
				intBarPos = Math.floor((objTrackTimes.floatTrackCurrentTime / objTrackTimes.floatTrackDuration) * 100);
			}
			this.$.sliderSongTime.setPositionImmediate(intBarPos);
			this.$.sliderSongTime.render();
		}
		
	},
	
	
	onClick_sliderSongTime: function (sender, event)
	{
		
		this.log();
		
		this._boolUpdateSlider = false;
		this._boolUpdateTimeDisplay = true;

		this.setBoolDraggingSlider(true);
	},
	
	
	// Need to fix some issues with inner bar delay when dragging.
	onChange_sliderSongTime: function(sender,intPos)
	{
		//this Allows music to send another end event because we stopped it while dragging, arbitrary number ;D
		if(intPos === 100){
			intPos = 99.999;			
		}
		this.log();		
		this._boolUpdateSlider = true;
		this._boolUpdateTimeDisplay = true;
		

		this.log(sender);
		this.log(intPos);

		this.$.lblSongTime.setContent(Utilities.formatTime(this._intCurrentDuration * intPos / 100));

		//this.$.sliderSongTime.setPositionImmediate(intPos);
		//this.$.sliderSongTime.setBarPosition(intPos);

		this.updateSliderSongTime(intPos);

		this.setBoolDraggingSlider(false);
		
	},
	
	
	updateSliderSongTime: function (intPos)
	{
		
		this.log();
		this.doSetPlaybackTime(intPos);
		
		//this._boolUpdateSlider = true;
		this._boolUpdateTimeDisplay = true;
		
	},
	
		
	onChanging_sliderSongTime: function (sender, intPos)
	{
		if(this.boolDraggingSlider){
			this.log();
			this._boolUpdateSlider = false;
	
			this.log(sender);
			//this.log(event);
			this.log(intPos);
			this._boolUpdateTimeDisplay = false;
			
			this.$.lblSongTime.setContent(Utilities.formatTime(this._intCurrentDuration * intPos / 100));
		
			//this.$.sliderSongTime.setPositionImmediate(intPos);
			this.doSetPlaybackTime(intPos);
		}
	},
	
	
	setShuffleButton: function (boolShuffleOn)
	{
		this.log(boolShuffleOn);
		this.$.btnShuffle.addRemoveClass("on", boolShuffleOn);
		

	},
	
	setRepeatButton: function (strRepeatMode)
	{
		this.log(strRepeatMode);
		this.$.btnRepeat.setClassName("toggleMode repeat " + strRepeatMode);
	
	},
	
	onGetVolume: function(intVolume)
	{
		
			
			this.log("**: ", intVolume);
			
			
			if(this._boolUpdateVolumeSlider)
			{
				this.$.sliderVolume.setPositionImmediate(intVolume);
				this.$.sliderVolume.render();
			}
		
	},
	
	onChange_sliderVolume: function(sender,intPos)
	{
		this.log();
		//this.log(sender);
		//this.log(intPos);
		this.doSetVolume(intPos);
		this._boolUpdateVolumeSlider = true;

	},
	
	onChanging_sliderVolume: function (sender, intPos)
	{
		this.log(intPos);
		this._boolUpdateVolumeSlider = false;
		this.doSetVolume(intPos);

	}
	
	
	
});