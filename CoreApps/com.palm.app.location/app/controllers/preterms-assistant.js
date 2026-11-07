/* Copyright 2009 Palm, Inc.  All rights reserved. */

var PretermsAssistant = Class.create({
	initialize: function(appId){
		this.appId = appId;
		this.dataPreference = true;
	},
	
	setup: function(){		
		this.controller.get('pt_next').addEventListener(Mojo.Event.tap, this.pushTerms.bind(this));
	},	
	
	pushTerms: function(event){
		this.controller.stageController.pushScene('terms',this.appId);	
	}
});	