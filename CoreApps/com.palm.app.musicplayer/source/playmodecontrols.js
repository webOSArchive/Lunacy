/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "PlayModeControls",
	kind: "HFlexBox",
	className: "playmodecontrols",
	events: {onShuffleClick: "", onRepeatClick: "", onSetVolume: "", onRequestVolume: ""},
	components: [
		{name: "btnRepeat", kind: "Image", src: "images/btnRepeat_off.png", className: "playmodeButton", onclick: "doRepeatClick"},
            {name: "btnShuffle", kind: "Image", src: "images/btnShuffle.png", className: "playmodeButton", onclick: "doShuffleClick"},
			{kind: "Control", className: "volSliderContainer", components: [
				{name: "sliderVolume", className: "volSlider", kind: "Slider", width: "120px",position: "100", onChange: "onChange_sliderVolume", onChanging: "onChanging_sliderVolume"}						  
			]}
            
		],
	
	
	create: function ()
	{
		this.inherited(arguments);
		
		this.doRequestVolume(enyo.bind(this,this.onGetVolume));
	},
	
	setShuffleButton: function (boolShuffleOn)
	{
		this.log(boolShuffleOn);
		
		if(boolShuffleOn)
		{
			this.$.btnShuffle.src = "images/btnShuffle_on.png";
		}
		else
		{
			this.$.btnShuffle.src = "images/btnShuffle.png";
		}
		this.$.btnShuffle.srcChanged();		

	},
	
	setRepeatButton: function (strRepeatMode)
	{
		this.$.btnRepeat.src = "images/btnRepeat_" + strRepeatMode + ".png";
		this.$.btnRepeat.srcChanged();		
	
	},
	
	onGetVolume: function(intVolume)
	{
		this.$.sliderVolume.setPositionImmediate(intVolume);

	},
	
	onChange_sliderVolume: function(sender,intPos)
	{
		this.log("onChange_sliderVolume");
		this.log(sender);
		this.log(intPos);
		this.doSetVolume(intPos);
	},
	
	onChanging_sliderVolume: function (sender, intPos)
	{
		this.log("onChanging_sliderVolume");
		this.log(sender);
		//this.log(event);
		this.log(intPos);
		this.doSetVolume(intPos);

	}
	
	
	});
