enyo.kind(
{
	name: "DisplayManager",
	kind: "Component",
	events: {},
	components: [
		
		{name: "psDisplayLock", kind: "PalmService", service: "palm://com.palm.display/control", method: "setProperty", onSuccess: "onSuccess_psDisplayLock", onFailure: "onFailure_psDisplayLock", subscribe: true},
		
	],
	
	
	lockDisplay: function (boolDisplayOn)
	{
		
		this.log();
		
		if(boolDisplayOn)
		{
			this.$.psDisplayLock.call({requestBlock: true, client: "com.palm.app.clock"});
		}
		else
		{
			this.$.psDisplayLock.cancel();
		}
		
	},
	
	
	onSuccess_psDisplayLock: function (sender, response)
	{
		this.log(response);
	},
	
	
	onFailure_psDisplayLock: function (sender, response)
	{
		this.log(response);		
	}
	
});