/*globals enyo, $L, event, Utilities, window */
enyo.kind({
	name: "KindAlbumArtItem",
	kind: "Control",
	layoutKind: "VFlexLayout",
	className: "item focused",
	events: {onItemClick: ""},
	published: {intTrackOrigIndex: -99, intTrackIndex: -98, strArtistName: "", strSongTitle: "", strAlbumName: "", strAlbumArtFile: "", intCurrTrackIndex:-100, boolAudioPlaying: true, boolShow: true, boolVertical: true},
	components: [
		{ name: "itemHeader", className: "info", kind: "Control", pack: "end", showing: true, components:[
			{name: "lblSongTitle", content: "", className: "album"},
			{name: "lblArtistName", content: "", className: "artist"},
			{name: "imgMusicNote", kind: "Image", src: "images/Music_Note_white.png", className: "indicator"},
			{name: "lblAlbumName", content: "", className: "title"}			
		]},
		{name: "albumArtContainer", className: "cover", onclick: "onclick_vfbItem", components: [
			{name: "imgAlbumArt", kind: "Control", className: "img"},
			{name: "imgAlbumArtIcon", kind: "Image", src: "images/play_albumartitem.png", className: "albumarticon", showing: false}			
		]}
	],
	
	create: function ()
	{
		try
		{
			this.inherited(arguments);
			this.setupObj();				
			this.checkIfCurrentTrack();

		}
		catch(err2)
		{
			this.log("error: ",  err2);
		}
	},
	
	setupObj: function(){
		this.$.lblSongTitle.setContent(this.strSongTitle);
		this.$.lblArtistName.setContent(this.strArtistName);
		this.$.lblAlbumName.setContent(this.strAlbumName);
		
		this.log("**** ", this.strAlbumArtFile);
		//var strBlankImg = "images/imgAlbumArt.png";
		
		//this.strCenterClass = "imageCenter";
		
		/*
		var nodeArtImage = this.$.imgAlbumArt.hasNode();
		this.log("Getting Image Node");
		this.log("nodeArtImage: ", nodeArtImage);
		
		if(nodeArtImage)
		{
			
			this.log("Adding image error handler");
			
			nodeArtImage.addEventListener('error', enyo.bind(this, this.onImgError), false);
			
			
		}
		*/
		
		if (this.boolShow)
		{
			if(this.strAlbumArtFile !== "")
			{
				try
				{
					
					if (window.PalmSystem)
					{
						var intArtSize = 480;
						this.log("intArtSize: ", intArtSize);
						this.$.imgAlbumArt.setStyle("background-image: url(" + Utilities.getTrackImage(this.strAlbumArtFile, intArtSize) + ");");
						//this.$.imgAlbumArt.setSrc(strBlankImg);
						this.log("**** ", this.$.imgAlbumArt.src);
					}
					else
					{
						this.$.imgAlbumArt.hide();
					}
					
				}
				catch(err)
				{
					this.log("image error: ", err);
					this.$.imgAlbumArt.hide();

				}

				this.$.albumArtContainer.addClass("albumartbg");		
			}
			else
			{
				this.log("setting missing image");
			
				this.$.imgAlbumArt.hide();
			}
			
		}
		else
		{
			this.log("setting blank image");
			this.$.albumArtContainer.hide();
			
		}
		
	},
	onImgError: function (sender, event)
	{
		this.log();
		this.log(sender);
		//this.log(event);
		
	},
	
	
	checkIfCurrentTrack: function (boolAudioPlaying, intCurrTrackIndex)
	{
		this.log();
		this.log("intCurrTrackIndex: ", intCurrTrackIndex);
		this.log("boolAudioPlaying: ", boolAudioPlaying);
		
		if(boolAudioPlaying !== undefined)
		{
			this.boolAudioPlaying = boolAudioPlaying;
		}
		
		if(intCurrTrackIndex !== undefined)
		{
			this.intCurrTrackIndex = intCurrTrackIndex;
		}	
		
		this.$.imgMusicNote.setShowing (this.intTrackIndex === this.intCurrTrackIndex);

	},
	
	
	setPlayPauseIcon: function()
	{
		this.log();
		if(this.boolAudioPlaying)
		{
			this.$.imgAlbumArtIcon.setSrc("images/pause_albumartitem.png");
		}
		else
		{
			this.$.imgAlbumArtIcon.setSrc("images/play_albumartitem.png");
		}
	},	
	
	
	displayAsCurrent: function ()
	{
		try
		{
			this.log(this.strSongTitle);
         this.addRemoveClass("focused", true);
			
			//this.$.lblSongTitle.setContent(this.intTrackIndex + ":" + this.intTrackOrigIndex + " : " + this.strSongTitle);
			//this.$.imgMusicNote.show();
			/*
			this.$.lblSongTitle.removeClass("infoTextHide");
			this.$.lblArtistName.removeClass("infoTextHide");
			this.$.lblAlbumName.removeClass("infoTextHide");

			this.$.imgAlbumArt.addClass("imageCenter");
			
			this.$.lblSongTitle.addClass("infoTextShow");
			this.$.lblArtistName.addClass("infoTextShow");
			this.$.lblAlbumName.addClass("infoTextShow");
			*/

		}
		catch(err)
		{
			this.log(err);			
		}

	},
	
	displayAsPrev: function (boolMore)
	{
		this.log("");

      this.addRemoveClass("focused", false);
		
		//this.$.imgMusicNote.hide();
		/*
		this.$.lblSongTitle.removeClass("infoTextShow");
		this.$.lblArtistName.removeClass("infoTextShow");
		this.$.lblAlbumName.removeClass("infoTextShow");

		this.$.imgAlbumArt.removeClass("imageCenter");

		this.$.lblSongTitle.addClass("infoTextHide");
		this.$.lblArtistName.addClass("infoTextHide");
		this.$.lblAlbumName.addClass("infoTextHide");
		*/
	},
	
	displayAsNext: function (boolMore)
	{
		this.log("");
		
      this.addRemoveClass("focused", false);

		//this.$.imgMusicNote.hide();
		/*
		this.$.lblSongTitle.removeClass("infoTextShow");
		this.$.lblArtistName.removeClass("infoTextShow");
		this.$.lblAlbumName.removeClass("infoTextShow");

		this.$.imgAlbumArt.removeClass("imageCenter");

		this.$.lblSongTitle.addClass("infoTextHide");
		this.$.lblArtistName.addClass("infoTextHide");
		this.$.lblAlbumName.addClass("infoTextHide");
		*/
	},
	
	
	onclick_vfbItem: function (sender, event)
	{
		try
		{
			this.log();
			
			this.boolAudioPlaying = !this.boolAudioPlaying;
			
			this.setPlayPauseIcon();
			
			this.doItemClick(this.intTrackOrigIndex);
			
		}
		catch(err)
		{
			this.log("error: ", err);
		}

		
	}

	
	
	
	
});