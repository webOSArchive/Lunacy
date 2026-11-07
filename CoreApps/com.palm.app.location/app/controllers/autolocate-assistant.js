/* Copyright 2009 Palm, Inc.  All rights reserved. */

var AutolocateAssistant = Class.create({
	initialize: function(appId){
		this.appId = appId;
		this.dataPreference = true;
	},
	
	setup: function(){		
		this.controller.get('al_enable').addEventListener(Mojo.Event.tap, this.enableDisableAutoLocate.bind(this,true));
		this.controller.get('al_disable').addEventListener(Mojo.Event.tap, this.enableDisableAutoLocate.bind(this,false));
	},	
	
	enableDisableAutoLocate: function(autoLocate,event) {
		LocationService.setAutoLocate(
							autoLocate, 
							this.handleSetAutoLocate.bind(this), 
							this.handleSetAutoLocate.bind(this), 
							this.controller);
	},
	
	handleSetAutoLocate: function() {
		// get the new preference settings
		this.prefsResquest = LocationService.getLocationServicePrefs(this.controller,this.handleSetAutoLocateSuccess.bind(this));
	},
	
	handleSetAutoLocateSuccess: function(result) {
		this.controller.stageController.popScenesTo();
		this.controller.stageController.pushScene('main',result);
	}
});	