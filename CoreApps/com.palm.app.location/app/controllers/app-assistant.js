/* Copyright 2009 Palm, Inc.  All rights reserved. */

var LOCATION_HELP_URL = "http://help.palm.com/location/index.html";
var LOCATION_MAIN_STAGE = "location_stage";

function AppAssistant(appController) {
  this.appController = appController; 
}

function StageAssistant(stageController) {
	var launchParams = {};
	try {
		launchParams = PalmSystem.launchParams.evalJSON(true);
	} catch (err) {
		Mojo.Log.info("Location app: No launch params");
	}
	var appAssistant = Mojo.Controller.getAppController().assistant;
	appAssistant.mainController = stageController;
	appAssistant.handleLaunchType(stageController, launchParams, false);
}

AppAssistant.prototype.handleLaunch = function(params){
	Mojo.Log.info("handle launch params %j ", $H(params));
	if (this.mainController) {
		//this.handleLaunchType(stageController, params, true);
		this.mainController.activate();		
	}
	/*
	var stageController = this.controller.getStageController(LOCATION_MAIN_STAGE);
	
	if (stageController) {
		//this.handleLaunchType(stageController, params, true);
		stageController.activate();
	} else {
		var f = function(stageController){
			this.handleLaunchType(stageController, params, false);
		}.bind(this)
		this.controller.createStageWithCallback(LOCATION_MAIN_STAGE, f);
	}
	*/	
}

AppAssistant.prototype.handleLaunchType = function(stageController, params, appExists){
	Mojo.Log.info("launch params ==== %j", $H(params));
    
    if (params && params.launchTerms) {
		stageController.swapScene("terms", params.appId);
	} else {
		AppAssistant.prefsResquest = LocationService.getLocationServicePrefs(null,this.readLocationServicePrefs.bind(this,stageController), this.errorLocationServicePrefs.bind(this));
	}
}

AppAssistant.prototype.readLocationServicePrefs = function(stageController, response) {
		if (response && !response.useGoogle && !response.useGps && response.gpsAvailable) {
			stageController.swapScene("gpsoff", response);
		} else {
			stageController.swapScene("main", response);
		}
	},
	
AppAssistant.prototype.errorLocationServicePrefs = function(response) {
	Mojo.Log.error("Could not read location preferences %j", $H(response));
}

// Enable the help menu item across the whole app
/*AppAssistant.prototype.handleCommand = function(event) {
	if (event.type == Mojo.Event.commandEnable) {
		if (event.command == Mojo.Menu.helpCmd) {
			event.stopPropagation();
		}
    	}
	if (event.type == Mojo.Event.command) {
		if (event.command === Mojo.Menu.helpCmd) {
			LocationService.launchHelp(LOCATION_HELP_URL);
			event.stop();
		}
	}
}*/