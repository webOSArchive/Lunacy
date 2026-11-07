/* Copyright 2009 Palm, Inc.  All rights reserved. */

var TermsAssistant = Class.create({
	initialize: function(appId){
		this.appId = appId;
		this.dataPreference = true;
	},
	
	setup: function(){
		
		// to set the is anonymous flag with user comment
		this.dataAttr = {
			property: "setDataCollection",
			trueValue: "ON",
			falseValue: "OFF",
			fieldName: 'checkboxstuff'
		};
		this.dataModel = {
			value: "ON",
			disabled: false
		};

		this.controller.setupWidget('setBackgroundDataCollection', this.dataAttr, this.dataModel);
		$('setBackgroundDataCollection').observe(Mojo.Event.propertyChange, this.setBackgroundDataCollectionPrefs.bind(this));
				
		this.controller.setupWidget('terms-box', "");
		this.controller.listen('terms-box',Mojo.Event.tap, this.expandTerms.bind(this));
		this.controller.listen('acceptTerms',Mojo.Event.tap, this.acceptTerms.bindAsEventListener(this));
		this.controller.listen('declineTerms',Mojo.Event.tap, this.declineTerms.bindAsEventListener(this));			
	},	
	
	expandTerms: function (event) {
		this.controller.get('terms-expand-div').hide();
		this.controller.get('terms-box').addClassName('expanded');	  
	},
		
	/*
	handleCommand: function(event) {
		if (event.type == Mojo.Event.back) {
			event.preventDefault();
			event.stopPropagation();
		}	
	},
	*/
	setBackgroundDataCollectionPrefs: function(event) {
		if ("ON" == event.value) {
			this.dataPreference = true;
		} else {
			this.dataPreference = false;
		}	
		Mojo.Log.info("Set background data collection ==== ", this.dataPreference);
	},
	
	successSetBackgroundDataCollection: function (response) {
		Mojo.Log.info("Success in setting location service preference %o", $H(response));
	},
	
	errorSetBackgroundDataCollection: function (response) {
		Mojo.Log.error("Error in setting location service preference %o", $H(response));
	},
		
	declineTerms: function(event){
		this.locationPreference = false;
		//this.successRejectTerms();
		//Doing this as need to set GPS on only when rejected from the First Use.
		// Otherwise GPS should have same value as before.
		LocationService.rejectTermsOfUse(
							this.appId,
							this.successRejectTerms.bind(this), 
							this.errorRejectTerms.bind(this), this.controller);
		
	},
	
	successRejectTerms: function (response) {
		Mojo.Log.info("eeeeeeee Success in setting google preference %j", $H(response));
		this.prefsResquest = LocationService.getLocationServicePrefs(this.controller,this.handlsuccessRejectTerms.bind(this));
	},
		
	handlsuccessRejectTerms: function(response) {
		var message = $L("Your device will not be able to use Google's location service, which provides location information for your device.");
		var title = $L("Google Location Services Off");
		this.controller.showAlertDialog({
			onChoose: function(choice){
				if(choice == 'next') {
					this.controller.stageController.popScenesTo();
					Mojo.Controller.stageController.swapScene("main", response);
				}
			}.bind(this),
			title: title,
			message: message,
			preventCancel: false,
			choices: [{label: $L('Back'), value: 'back'},{label: $L('Next'), value: 'next'}]
		});			
	},
	
	errorRejectTerms: function(response) {
		Mojo.Log.error("Error in setting rejecting terms %j", $H(response));
	},
	
	
	acceptTerms: function(event){
		this.locationPreference = true;
		LocationService.acceptTermsOfUse(	this.dataPreference,
							this.appId,
							this.successAcceptTerms.bind(this), 
							this.errorAcceptTerms.bind(this), this.controller);
	},
	
	handlsuccessAcceptTerms: function (response) {
		this.controller.stageController.popScenesTo();
		Mojo.Controller.stageController.swapScene("main", response);		
	},
	
	errorAcceptTerms: function(response) {
		Mojo.Log.error("Error in setting terms acceptance %o", $H(response));
	},
	successAcceptTerms: function (response) {
		Mojo.Log.info("eeeeeeeeeeee Success in setting google preference %j", $H(response));
		this.prefsResquest = LocationService.getLocationServicePrefs(this.controller,this.handlsuccessAcceptTerms.bind(this));
	},
	
	successSetUseLocationServices: function (response) {
		Mojo.Log.info("Success in setting location service preference %j", $H(response));
		if(this.appId == "-1") {
			Mojo.Controller.stageController.swapScene("main", "");
			return;
		}
		this.controller.window.close();	
	},
	
	errorSetUseLocationServices: function (response) {
		Mojo.Log.error("Error in setting location service preference %o", $H(response));
		this.controller.window.close();	
	}
	
});	