/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "KindPlaycontrols",
	kind: "HFlexBox",
	className: "playcontrols",
	events: {onClickNext: "", onClickPrev: "", onClickPlayPause: ""},
	components: [
            {name: "btnPrev", kind: "Image", src: "images/btnPrev.png", className: "playButton", onclick: "onclick_prev"},
            {name: "btnPlay", kind: "Image", src: "images/btnPlay.png", className: "playButton", onclick: "onclick_playpause"},
            {name: "btnNext", kind: "Image", src: "images/btnNext.png", className: "playButton", onclick: "onclick_next"}
	],
	
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
	
	setPlayPause: function (boolAudioPlaying)
	{
		if(boolAudioPlaying)
		{
			this.$.btnPlay.src = "images/btnPause.png";
		}
		else
		{
			this.$.btnPlay.src = "images/btnPlay.png";
		}
		this.$.btnPlay.srcChanged();
		
	}
	
	
	});

