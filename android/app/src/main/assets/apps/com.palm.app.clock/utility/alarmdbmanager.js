enyo.kind(
{
	name: "AlarmDbManager",
	kind: "Component",
	events: {onAlarmInserted: "", onAlarmUpdated: "", onAlarmDeleted: "", onGotAlarms: ""},
	components: [

		{kind: "DbService", dbKind: "com.palm.clock.alarm:1" , onFailure: "onFailure_dbsAlarms", components: [
			{ name: "dbsGetAlarms", method: "find", onSuccess: "gotAlarms"},
			{ name: "dbsPutAlarms", method: "put", onSuccess: "onSuccess_PutAlarms"},
			{ name: "dbsUpdateAlarms", method: "merge", onSuccess: "onSuccess_updateAlarm"},
			{ name: "dbsDelAlarms", method: "del", onSuccess: "onSuccess_deleteAlarm"}
		]}
		
	],
		
	create: function ()
	{
		this.inherited(arguments);
	},	
	
	requestAlarms: function (objGetAlarmsRequest)
	{
		this.log();

		this.getAlarms(objGetAlarmsRequest);
		
	},
	
  getAlarms: function (objGetAlarmsRequest)
    {
		this.log("****");

		try
		{

			var q = {
				//orderBy: objGetAlarmsRequest.order || "",
				where: objGetAlarmsRequest.where || []
			};
			
			if(objGetAlarmsRequest.queryType === undefined)
			{
				objGetAlarmsRequest.queryType = "find"
			}
				
			var req = this.$.dbsGetAlarms.call({watch: false, query: q, subscribe: false},{});
			
			req.objGetAlarmsRequest = objGetAlarmsRequest;
	
			this.log("**** called Alarms query");
		}
		catch(err)
		{
			this.log(err);
		}
    },
	 
	 
	gotAlarms: function (inSender, inResponse, inRequest)
	{
		this.log();
				
		if(inResponse.results)
		{
			this.log(inResponse.results.length);
			this.doGotAlarms(inResponse.results);
			
		}
	},
	
	objBlankAlarm: {
		_kind:"com.palm.clock.alarm:1",
		title: $L("Alarm"),
		occurs: "daily",
		hour: 8,
		minute: 0,
		timezoneOffset: -6,
		niceTime: "8:00 AM",
		niceDay: "--",
		enabled: true,
		alarmSoundFile: "/media/internal/ringtones/Flurry.mp3", 
		alarmSoundTitle: "Flurry",
		snoozed: false,
		hideSnoozeTime: false
	},
	
	kIntBaseID: 1,
	kStrKeyBase: "clockAlarm",	
	
	createBlankAlarm: function ()
	{
		var objNewAlarm = enyo.clone(this.objBlankAlarm);
		objNewAlarm.key = this.kStrKeyBase + (new Date().valueOf());
		return objNewAlarm;
		
	},
	
	
	insertAlarm: function (objInsertAlarm)
	{
		this.log();
		this.$.dbsPutAlarms.call({objects: [objInsertAlarm]});
	},
	
	
	onSuccess_PutAlarms: function (sender, response)
	{
		this.log(response);
		this.doAlarmInserted();
	},
	
	
	updateAlarm: function (objUpdateAlarm)
	{
		this.log();
		
		this.$.dbsUpdateAlarms.call({objects: [objUpdateAlarm]});
	
	},
	
	onSuccess_updateAlarm: function (sender, response)
	{
		this.log(response);
		this.doAlarmUpdated();
	},
	
	
	deleteAlarm: function (idDeleteAlarm)
	{
		if(idDeleteAlarm !== undefined)
		{
					
			var req = this.$.dbsDelAlarms.call({ids: [idDeleteAlarm]});
		}		
		
	},
	
	onSuccess_deleteAlarm: function (sender, response)
	{
		this.log(response);
		this.doAlarmDeleted();
	},	
	
	onFailure_dbsAlarms: function (sender, response)
	{
		
		this.log("****");
		this.log(sender);
		this.log(response);
		
	},
	

}
);
