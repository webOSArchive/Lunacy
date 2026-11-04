/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "kindSongInfoControls",
	kind: "HFlexBox",
	components: [
		{name: "imgAlbumArt", kind: "Image", src: "images/imgAlbumArt.png", className: "imgAlbumArt", showing: false},
		{kind: "VFlexBox", className: "songInfoContainter", components: [
			{kind: "Control", className: "sliderContainer", components: [
				{name: "sliderSongTime", kind: "ProgressSlider", className: "sliderSongTime", onChange: "onChange_sliderSongTime", onChanging: "onChanging_sliderSongTime", tapPosition: true},
				
					{name: "lblArtistName", content: "&nbsp;", className: "songInfoLabel", showing: true},
			

				{name: "lblSongTime", content: $L("--:--"), className: "timeDisplay",  style: "position: fixed; bottom: 41px; width: 40px; left: 230px; background-color: none;"},
				{ content: "/", className: "timeDisplay", style: "z-index: 105;", showing: false},
				{kind: "Control", style: "position: fixed; bottom: 41px; width: 40px; right: 270px; background-color: none; text-align: right;", components: [
					{name: "lblSongDuration", content: $L("--:--"), className: "timeDisplay"}				
				]}

			]}
		]}
	],
	
	events: {onSetPlaybackTime: ""},
	
	_boolUpdateSlider: true,
	_boolUpdateTimeDisplay: true,	
	
	_intCurrentDuration: 0,
	
	create: function ()
	{
		this.inherited(arguments);
		
		
	},
	
	updateTrackInfoDisplay: function (objTrackInfo)
	{
		try
		{
			
			if(this.intervalCheckTrackTime !== undefined)
			{
				window.clearInterval(this.intervalCheckTrackTime);
			}
			
			var strSongInfo = "<b>" + objTrackInfo.strTrackTitle  + "</b> - " + objTrackInfo.strTrackArtist + ", "+ objTrackInfo.strTrackAlbum;
			
			this.$.lblArtistName.setContent(strSongInfo);

			
			this._boolUpdateSlider = true;
			this._boolUpdateTimeDisplay = true;
			
			
		}
		catch(err)
		{
			this.log("**** updateTrackDisplay error: " + err);
		}
		
	},
	
	
	updateTrackTimeDisplay: function (objTrackTimes)
	{
		//this.log("updateTrackTimeDisplay objTrackTimes: " + objTrackTimes);

		if(this._boolUpdateTimeDisplay)
		{
			this.$.lblSongTime.setContent(Utilities.formatTime(objTrackTimes.floatTrackCurrentTime));
			this._intCurrentDuration = objTrackTimes.floatTrackDuration;
			this.$.lblSongDuration.setContent(Utilities.formatTime(this._intCurrentDuration));
		}


		
		if(this._boolUpdateSlider)
		{
			var intBarPos = Math.floor((objTrackTimes.floatTrackCurrentTime / objTrackTimes.floatTrackDuration) * 100) ;

			this.$.sliderSongTime.setLockBar(true);
			this.$.sliderSongTime.setPositionImmediate(intBarPos);
			
		}
		
	},
	
	
	// Need to fix some issues with inner bar delay when dragging.
	onChange_sliderSongTime: function(sender,intPos)
	{
		
		this._boolUpdateSlider = false;
		this._boolUpdateTimeDisplay = true;
		this.log("onChange_sliderSongTime");
		this.log(sender);
		this.log(intPos);

		this.$.sliderSongTime.setPosition(intPos);
		
		this.updateSliderSongTime(intPos);
		
	},
	
	
	updateSliderSongTime: function (intPos)
	{
		this.doSetPlaybackTime(intPos);
		
		this._boolUpdateSlider = true;
		this._boolUpdateTimeDisplay = true;
		
	},
	
	
	
	onChanging_sliderSongTime: function (sender, intPos)
	{
		this.log("onChanging_sliderSongTime");
		this.log(sender);
		//this.log(event);
		this.log(intPos);
		this._boolUpdateSlider = false;
		this._boolUpdateTimeDisplay = false;
		
		this.$.lblSongTime.setContent(Utilities.formatTime(this._intCurrentDuration * intPos / 100));

		
		
		this.$.sliderSongTime.setPosition(intPos);
		
		this.doSetPlaybackTime(intPos);

	}
	

	});
