/* Copyright 2009 Palm, Inc.  All rights reserved. */

/*globals Mojo MojoLoader $L*/

function AppAssistant(appController, params) { 
	AppAssistant.appController = appController; 
	AppAssistant.instance = this;
	this.appController = appController;
	
	// register for msm notifications
	this.storageNotificationSession = new Mojo.Service.Request(
		'palm://com.palm.bus/signal/', {
			method: 'addmatch',
			parameters: {
				category: "/storaged",
				method: "MSMProgress"
			},
			onSuccess: AppAssistant.storagedProgress.bind(this)
		}
	);
}

AppAssistant.prototype.handleLaunch = function(params) {
	if (Mojo.appName === AppAssistant.NO_WINDOW_APP_NAME) {
		// no window launch
		Mojo.Log.info ("no window launch");
		this.openChildWindow(this.controller, params);
	}
};

AppAssistant.prototype.cleanup = function cleanup() {
	if (this.storageNotificationSession){
		this.storageNotificationSession.cancel();
	}
};


function StageAssistant(stageController) {
	AppAssistant.stageController = stageController;
	this.stageController = stageController;
	this.stageController.pushScene("mainmenu", {});
}

AppAssistant.prototype.openChildWindow = function(appController, launchParams) {
	this.launchParams = launchParams;	
	var videoController = appController.getStageController('videoplayer');
	var launchedAsHeadless = false;
	
	if (launchParams && (launchParams.target || launchParams.video || launchParams._id)){
		launchedAsHeadless = true;
	}

	if (videoController) {
		// the stage already exists
		
		if(launchedAsHeadless) {
			videoController.popScenesTo();
			this.pushLaunchParamsFile(videoController); // launch the specified file
		} else if (this.lastLaunchHeadless) {
			videoController.popScenesTo();
			this.pushBrowser(videoController); // reset the card to the browser
		}
		
		videoController.activate(); // just bring it into focus
	} else {
		// the stage does not exist
		var f;
		if (launchedAsHeadless) {
			f = this.pushLaunchParamsFile;
		} else { 
			f = this.pushBrowser;  
		} 

		appController.createStageWithCallback({
			name: 'videoplayer',
			lightweight: true
		}, f.bind(this));		
	}
};

AppAssistant.prototype.pushLaunchParamsFile = function(stageController) {
	AppAssistant.stageController = stageController;
	//todo: this is messy
	var params = {
		target: this.launchParams.target,
		title: this.launchParams.videoTitle,
		initialPos: 0,
		videoID: undefined,
		thumbUrl: this.launchParams.thumbUrl,
		isNewCard: true,
		captured: this.launchParams.captured,
		item: {
			videoDuration: this.launchParams.videoDuration
		}
	};
	
	// we were launched by another app to play a particular video
	this.lastLaunchHeadless = true;
	
	if (this.launchParams.source === "upload-popup") {
		stageController.pushScene("mainmenu");
		AppAssistant.VideoLibrary.Push(
			stageController, AppAssistant.VideoLibrary.Browser, {capturedOnDevice: true});
		params.autoplay = true;
		AppAssistant.VideoLibrary.Push(
			stageController, AppAssistant.VideoLibrary.Videoeditor, this.launchParams);
	} else if(this.launchParams.source == "upload-dashboard") {
		stageController.pushScene("mainmenu");
		AppAssistant.VideoLibrary.Push(
			stageController, AppAssistant.VideoLibrary.Browser, {capturedOnDevice: true});
		AppAssistant.VideoLibrary.Push(
			stageController, AppAssistant.VideoLibrary.Details, this.launchParams);
	} else {
		AppAssistant.VideoLibrary.Push(
			stageController, AppAssistant.VideoLibrary.Nowplaying, params);
	}
};

AppAssistant.prototype.pushBrowser = function(stageController) {
	AppAssistant.stageController = stageController;
	stageController.pushScene("mainmenu", {});
};

AppAssistant.storagedProgress = function(response) {
	if (!response) {
		return;
	}

	if (response.stage == 'attempting') {
		Mojo.Log.info ("entering MSM");
		AppAssistant.resetApp();
	}
};

AppAssistant.resetApp = function() {
	// reset to the first scene
	AppAssistant.stageController.popScenesTo('mainmenu');
};

AppAssistant.VideoLibrary = MojoLoader.require({
	name: "metascene.videos",
	version: "1.0"
})["metascene.videos"];

AppAssistant.NO_WINDOW_APP_NAME = "com.palm.app.videoplayer";

