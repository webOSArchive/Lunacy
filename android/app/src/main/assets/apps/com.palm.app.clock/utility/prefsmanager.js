enyo.kind(
{
	name: "PrefsManager",
	kind: "Component",
	events: {onPrefInserted: "", onPrefUpdated: "", onPrefDeleted: "", onGotPrefs: ""},
	components: [

		{kind: "DbService", dbKind: "com.palm.clock.prefs:1" , onFailure: "onFailure_dbsPrefs", components: [
			{ name: "dbsGetPrefs", method: "find", onSuccess: "gotPrefs"},
			{ name: "dbsPutPrefs", method: "put", onSuccess: "onSuccess_PutPrefs"},
			{ name: "dbsUpdatePrefs", method: "merge", onSuccess: "onSuccess_updatePref"},
			{ name: "dbsDelPrefs", method: "del", onSuccess: "onSuccess_deletePref"}
		]}
		
	],
		
	create: function ()
	{
		this.inherited(arguments);
	},	
	
	requestPrefs: function (objGetPrefsRequest)
	{
		this.log();

		this.getPrefs(objGetPrefsRequest);
		
	},
	
  getPrefs: function (objGetPrefsRequest)
    {
		this.log("****");

		try
		{

			var req = this.$.dbsGetPrefs.call({watch: false, query: {}, subscribe: false},{});
			
			this.log("**** called Prefs query");
		}
		catch(err)
		{
			this.log(err);
		}
    },
	 
	 
	gotPrefs: function (inSender, inResponse, inRequest)
	{
		this.log();
				
		if(inResponse.results)
		{
			if(inResponse.results.length > 0)
			{	
				this.log(inResponse.results.length);
				enyo.application.prefs = inResponse.results[0];
				this.doGotPrefs();
				return true;
			}	
		}
		
		enyo.application.prefs = this.createBlankPref();
		this.insertPref();
		return true;
	},
	
	
	objBlankPref: {
		_kind:"com.palm.clock.prefs:1",
		RingerSwitchOff: "play",
		AscendingVolume: false,
		SnoozeDuration: 10,
		ClockTheme: "default"	
	},
	
	
	createBlankPref: function ()
	{
		var objNewPref = enyo.clone(this.objBlankPref);
		objNewPref.key = this.kStrKeyBase + (new Date().valueOf());
		return objNewPref;
		
	},
	
	
	insertPref: function ()
	{
		this.log();
		if(enyo.application.prefs)
		{
			this.$.dbsPutPrefs.call({objects: [enyo.application.prefs]});
		}
	},
	
	
	onSuccess_PutPrefs: function (sender, response)
	{
		this.log(response);
		this.doPrefInserted();
		this.doGotPrefs();
	},
	
	
	updatePref: function ()
	{
		this.log();
		if(enyo.application.prefs)
		{
			this.$.dbsUpdatePrefs.call({objects: [enyo.application.prefs]});
		}		
	
	},
	
	onSuccess_updatePref: function (sender, response)
	{
		this.log(response);
		this.doPrefUpdated();
	},
	
	/*
	deletePref: function (idDeletePref)
	{
		if(idDeletePref !== undefined)
		{
					
			var req = this.$.dbsDelPrefs.call({ids: [idDeletePref]});
		}		
		
	},
	
	onSuccess_deletePref: function (sender, response)
	{
		this.log(response);
		this.doPrefDeleted();
	},	
	*/	
	onFailure_dbsPrefs: function (sender, response)
	{
		
		this.log("****");
		this.log(sender);
		this.log(response);
		
	},
	

}
);
