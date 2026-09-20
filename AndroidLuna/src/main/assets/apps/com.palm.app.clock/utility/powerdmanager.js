enyo.kind(
{
	name: "PowerDManager",
	kind: "Component",
	events: {onSetAlarmTimeOut: "", onFailure: ""},
	components: [
		
		{name: "psAlarmSet", kind: "PalmService", service: "palm://com.palm.power/", method: "timeout/set", onSuccess: "onSuccess_AlarmSet", onFailure: "onFailure_AlarmSet"},
		
		{name: "psAlarmClear", kind: "PalmService", service: "palm://com.palm.power/", method: "timeout/clear", onSuccess: "onSuccess_AlarmClear", onFailure: "onFailure_AlarmClear"},
	],
		
	create: function ()
	{
		this.inherited(arguments);
	},
	
	
	kStrSchedulerKeyBase: "clockAlarm",

	
	setAlarmTimeout: function (objAlarmRecord, dateTimeout)
	{
		this.log(objAlarmRecord);
		
		//var dateNext = enyo.application.utilities.dateGetNext(objAlarmRecord.hour, objAlarmRecord.minute);
		
			
		var objSetAlarmPayload = {
			
			key: objAlarmRecord.key,
			at: enyo.application.utilities.dateFormatForScheduler(dateTimeout),
			uri: "luna://com.palm.applicationManager/launch",
			params: {"id":"com.palm.app.clock","params":{"action":"ring", "key": objAlarmRecord.key}},
			wakeup: true,
			keep_existing: false
			
		}
		
		this.log("objSetAlarmPayload: ", objSetAlarmPayload);
		
		this.$.psAlarmSet.call(objSetAlarmPayload);
		
	},
	
	
	onSuccess_AlarmSet: function (sender, response)
	{
		this.log(response);
	},
	
	
	onFailure_AlarmSet: function (sender, response)
	{
		this.log(response);		
	},



	clearAlarmTimeout: function (objAlarmRecord)
	{
				
		this.$.psAlarmClear.call({key: objAlarmRecord.key});
		
	},


	onSuccess_AlarmClear: function (sender, response)
	{
		this.log(response);
	},
	
	
	onFailure_AlarmClear: function (sender, response)
	{
		this.log(response);		
	},






});
