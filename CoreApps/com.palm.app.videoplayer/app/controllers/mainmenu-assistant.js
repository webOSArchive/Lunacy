/* Copyright 2009 Palm, Inc.  All rights reserved. */

/*jslint browser:true*/
/*globals Mojo AppAssistant*/

var MainmenuAssistant = function(query){
	this.query = query;
};

MainmenuAssistant.prototype = {  
	setup: function() {
		AppAssistant.VideoLibrary.Util.addActiveScene(this);
		this.watchState();
		this.subscribeForVideoCount();

		this.spinnerModel = {
			spinning: false
		};
		
		// spins while indexing
		this.controller.setupWidget('scan-spinner',
				{spinnerSize: 'large'}, this.spinnerModel);

		this.controller.get("videoroll").addEventListener(Mojo.Event.tap, this.handleVideoRoll.bind(this), false);
		this.controller.get("loaded").addEventListener(Mojo.Event.tap, this.handleLoaded.bind(this), false);
		AppAssistant.VideoLibrary.Util.setupAppMenu(this);
	},
	
	handleVideoRoll: function(){
		AppAssistant.VideoLibrary.Push(
			this.controller.stageController, 
			AppAssistant.VideoLibrary.Browser, 
			{capturedOnDevice: true});
	},
	
	handleLoaded: function(){
		AppAssistant.VideoLibrary.Push(
			this.controller.stageController, 
			AppAssistant.VideoLibrary.Browser, 
			{capturedOnDevice: false});
	},
	
	cleanup: function() {
		AppAssistant.VideoLibrary.Util.removeActiveScene(this);
	},
	
	activate: function() {
		this.controller.stageController.setWindowOrientation("free");
	},
	
	showNoVideosDiv: function(){
		this.controller.get('empty-library-message').style.display = 'block';	
		this.controller.get('option-list').style.display = 'none';
	},
	
	showVideosDiv: function(){
		this.controller.get('empty-library-message').style.display = 'none';	
		this.controller.get('option-list').style.display = 'block';
	},
	
	watchState : function() {
		if (this.stateRequest) {
			this.stateRequest.cancel();
			this.stateRequest = undefined;
		}

		this.stateRequest = new Mojo.Service.Request(
			'palm://com.palm.filenotifyd', {
				method: 'state',
				parameters: {watch: true, subscribe: true},
				onComplete: this.handleServiceState.bind(this)
		}, {resubscribe: true});
	},
	
	handleServiceState : function(response) {
		Mojo.Log.info("filenotifyd state:" + JSON.stringify(response));

		if (!response.returnValue) {
			Mojo.Log.error("Unexpected state response!" +
				JSON.stringify(response));
			//this.displayScanState(true);
			return;
		}

		if (response.fired) {
			this.watchState();
			return;
		}
		
		if (response.mounted["/media/internal"] === false) {
			this.displayScanState(true);
			return;
		}

		switch (response.state) {
			case "idle":
				this.displayScanState(false);
				break;
			case "processing":
				//todo: comment back in when we know what to do with processingProgress
				//this.updateScanProgress(response.processingProgress);
				break;
		}
	},

	updateScanProgress : function(progress) {
		this.controller.get('indexing-div').style.opacity =
			"" + (1.0 - progress);
	},

	displayScanState : function(show) {
		if (this.scanStateShowing === show) {
			return;
		}
		if (show) {
			this.updateScanProgress(0);
			this.controller.stageController.popScenesTo('mainmenu');
		}
		this.scanStateShowing = show;
		this.spinnerModel.spinning = this.scanStateShowing;
		this.controller.modelChanged(this.spinnerModel);
		this.controller.get('indexing-div').style.display = 
						(show ? "block" : "none");
	},
	
	showIndexingDiv: function(){
		Mojo.Log.info ("show indexing div");
		if (!this.isIndexing){
			this.controller.get('indexing-div').style.display = 'block';	
			this.controller.get('video-library-container').style.display = 'block';
			this.controller.get('empty-library-message').style.display = 'none';	
			this.spinnerModel.spinning = true;
			this.controller.modelChanged(this.spinnerModel);
			this.isIndexing = true;
		}
	},
	
	hideIndexingDiv: function(){
		Mojo.Log.info ("hide indexing div");
		if (this.isIndexing){
			this.spinnerModel.spinning = false;
			this.controller.modelChanged(this.spinnerModel);
			this.isIndexing = false;
			this.subscribeForVideoCount();
		}
	},

	subscribeForVideoCount: function(){	
		var options = {
			limit: 0,
			watch: true,
			count: true
		};

		if (this.videoCountRequest){
			this.videoCountRequest.cancel();
		}
		this.videoCountRequest = 
			AppAssistant.VideoLibrary.MojoDBService.listVideos(this.controller, options, [], this.handleCountChange.bind(this));
	},
	
	handleCountChange: function(response){
		Mojo.Log.info("count change:", JSON.stringify(response));
		if(response.fired){
			this.subscribeForVideoCount();
		} else {
			var newCount = response.count;
			if (newCount !== this.videoCount) {
				this.videoCount = newCount;
				this.refreshMainMenu();
			}
		}
	},
	
	refreshMainMenu: function(){
		Mojo.Log.info ("calling refresh");
		if (AppAssistant.VideoLibrary.Util.transcoding){
			this.showIndexingDiv();

			// periodically retry until no longer transcoding
			this.controller.window.setTimeout(this.refreshMainMenu.bind(this), 1000);
		} else {
			// make sure the indexing div is not up
			this.controller.get('indexing-div').style.display = 'none'; 

			if (this.videoCount > 0) {
				this.showVideosDiv();
				this.populateAllThumbEntries();
			} else {
				this.showNoVideosDiv();
			}
		}	
	},
	
	populateAllThumbEntries: function(){
		this.populateThumbEntries('videoroll', true);	
		this.populateThumbEntries('loaded', false);	
	},
	
	populateThumbEntries: function(baseId, captured){
		var mainDiv = this.controller.get(baseId);
		
		var addClassName = function(count){
			if (count > 0){
				//purging the div of previous class assignments
				var classNameArray = mainDiv.className.split(" ");
				for(var i=0; i<classNameArray.length; i++){
					if(classNameArray[i] === "one" || classNameArray[i] === "two" || classNameArray[i] === "three"){
						delete classNameArray[i];
					}
				}
				mainDiv.style.display = "block";

				if (count == 1) { classNameArray.push("one"); }
				else if (count == 2) { classNameArray.push("two"); } 
				else { classNameArray.push("three"); }

				mainDiv.className = classNameArray.join(" ");
			} else {
				mainDiv.style.display = "none";
			}
		};
		
		this.fetchVideoThumbs(baseId, captured, 3, addClassName);
	},
	
	fetchVideoThumbs: function(baseId, captured, limit, callback){
		//todo: should this art be random?
		var options = {limit:limit, count:true};

		var where = AppAssistant.VideoLibrary.MojoDBService.capturedWhere(captured);
		
		AppAssistant.VideoLibrary.MojoDBService.listVideos(this.controller, options, where, function(response){
			var artArray = [];
			var i;
			for (i = 0; i < response.results.length; i++){
				var thumb = response.results[i].thumbnails[0];
				artArray[i] = AppAssistant.VideoLibrary.Util.videoThumbUrlFormatter(thumb);
			}
			this.showVideoThumbs(baseId, artArray, callback);
		}.bind(this));
	},
	
	firstDescendant: function(element) {
	    element = element.firstChild;
	    while (element && element.nodeType != 1){ element = element.nextSibling; }
	    return element;
	  },
	
	showVideoThumbs: function(baseId, artArray, callback) {
		var leftDiv = this.controller.get(baseId +'-thumb-left');
		var centerDiv = this.controller.get(baseId + '-thumb-center');
		var rightDiv = this.controller.get(baseId + '-thumb-right');

		var numItems = artArray.length;
		var image;

		var renderDiv = function(div, imgSrc) {
			if(imgSrc) {
				this.firstDescendant(div).src = imgSrc;
				div.style.display = 'block';
			} else {
				div.style.display = 'none';
			}
		}.bind(this);
		
		var renderListenerDiv = function(div, imgSrc) {
			if(imgSrc) {
				var doneFunc = function () {
					image.onload = null;
					image.onerror = null;
					image.onabort = null;
					image.onchange = null;

					if (callback) {
						callback(artArray.length);
					}
				};

				image = this.firstDescendant(div);
				image.src = imgSrc;

				image.onload = doneFunc;
				image.onerror = doneFunc;
				image.onabort = doneFunc;
				image.onchange = doneFunc;								

				if(image.complete){
					doneFunc();
				}

				div.style.display = 'block';
			} else {
				div.style.display = 'none';
				if(callback) {
					callback(artArray.length);
				}
			}
		}.bind(this);

		if(numItems >= 3) {
			renderDiv(rightDiv, artArray[2]);
			renderDiv(centerDiv, artArray[1]);
			renderListenerDiv(leftDiv, artArray[0]);
		} else if(numItems === 2) {
			renderDiv(leftDiv, null);
			renderDiv(rightDiv, artArray[1]);
			renderListenerDiv(centerDiv, artArray[0]);
		} else if(numItems === 1) {
			renderDiv(leftDiv, null);
			renderDiv(centerDiv, null);
			renderListenerDiv(rightDiv, artArray[0]);
		} else if(numItems === 0){
			renderDiv(leftDiv, null);
			renderDiv(centerDiv, null);
			renderListenerDiv(rightDiv, null);
		}
	},
	
	THUMB_WIDTH: 85,
	THUMB_HEIGHT: 65
};

