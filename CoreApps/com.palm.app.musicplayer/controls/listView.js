/*globals enyo, $L, event, Utilities, window, LibraryTools */
enyo.kind({
	name: "listView",
	kind: "VFlexBox",
	events: {},
			
	create: function () {
		this.inherited(arguments);
	},
	
	highlightTrack: function (objTrackInfo)
	{
		this.log();	
		try
		{
			if(objTrackInfo)
			{
			this.log(objTrackInfo.intTrackIndex);
				this.intCurrTrack = objTrackInfo.intTrackIndex;
				this.intCurrTrackOrigIndex = objTrackInfo.intTrackOrigIndex;
				this.strCurrTrackID = objTrackInfo.strTrackID;
				this.strCurrTrackListQuery = objTrackInfo.strListQuery;
				
				this.log(this.intCurrTrack);
				
			}
			
			if(this.boolViewActive)
			{
				this.$.listMedia.refresh();
			}
		}
		catch(err)
		{
			this.log("error:" + err);
		}
		
	}
});