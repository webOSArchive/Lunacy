/*globals enyo, $L, event, Utilities, window, PalmSystem, MediaIndex */
enyo.kind(
{
	name: "kindDashboardManager",
	kind: "Component",
	components: [
		
	],
	
	
	objTrackInfoCache: undefined,
	boolAudioPlayingCache: false,
	
	
	openControlDashboard: function()
	{
		this.log();
		try
		{
		
		
			//this.dashControls = enyo.windows.openDashboard("dashboard.html","dash");
			
			if(this.objTrackInfoCache)
			{
				this.dashControls = enyo.windows.openDashboard("dashboard.html","dash", {objTrackInfo: this.objTrackInfoCache, boolAudioPlaying : this.boolAudioPlayingCache},{clickableWhenLocked: true});
				//0enyo.windows.openDashboard("testpopup.html", "testPopup", {}, {clickableWhenLocked:true}); 
			}
			
		}
		catch(err)
		{
			this.log("error: " + err);
		}
	},
	
	
	closeControlDashboard: function()
	{
		
		if(this.dashControls)
		{
			this.dashControls.close();
			this.dashControls = null;
		}		
		
	},
	
	
	updateControlDashboardInfo: function (objTrackInfo, boolWindowActive, boolShowBanner)
	{
		this.log();
		this.objTrackInfoCache = objTrackInfo;
		this.applyDashParams({objTrackInfo: objTrackInfo});
			
		this.log("objTrackInfo.boolForced: ", objTrackInfo.boolForced);
		this.log("boolWindowActive: ", boolWindowActive);
		
		if(objTrackInfo.boolForced === undefined)
		{
			objTrackInfo.boolForced = false;
		}
		
		if(boolWindowActive === undefined)
		{
			boolWindowActive = true;
		}
		
		if(!objTrackInfo.boolForced && !boolWindowActive && boolShowBanner)
		{
			var template = new enyo.g11n.Template($L("Playing #{trackTitle} by #{trackArtist}"));
			var strBannerMsg = template.evaluate({trackTitle: objTrackInfo.strTrackTitle, trackArtist: objTrackInfo.strTrackArtist});
			this.log("strBannerMsg: ", strBannerMsg);
			enyo.windows.addBannerMessage(strBannerMsg, "{}", "images/notification-small.png");				
		}
		
		
	},
	
	setPlayPause: function (boolAudioPlaying)
	{
		this.boolAudioPlayingCache = boolAudioPlaying;
		this.applyDashParams({boolAudioPlaying: boolAudioPlaying});
	},
	
	applyDashParams: function(obj){
		if(this.dashControls)
		{
			var winDash = enyo.windows.fetchWindow("dash");
			if(winDash)
			{
				enyo.windows.setWindowParams(winDash, obj);
			}
		}		
	}
});