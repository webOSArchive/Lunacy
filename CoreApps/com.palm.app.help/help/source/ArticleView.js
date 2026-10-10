/**
 * ArticalPane is a SwipeableStackView with a WebView.
 * Links are handled so we can open them in new panes.
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "help.ArticleView",
	kind: "help.SwipeableStackView",
	published: {
		fileUrl: ""
	},
	events: {
		onLoadUrl: ""
	},
	WebKitErrors: {
		ERR_SYS_FILE_DOESNT_EXIST: 14,
		ERR_WK_FLOADER_CANCELLED: 1000,
		ERR_WK_NOINTERNET:1005,
		ERR_CURL_FAILURE: 2000,
		ERR_CURL_COULDNT_RESOLVE_HOST: 2006,
		ERR_CURL_SSL_CACERT: 2060
	},
	components: [
		{kind: "Pane", name: "outerPane", flex: 1, components: [
		    {kind: "WebView", name: "article", flex: 1,
				onUrlRedirected: "urlRedirected",
				onLoadStarted: "loadStarted",
				onLoadProgress: "loadProgress",
				onLoadStopped: "loadStopped",
				onLoadComplete: "loadCompleted",
				onError: "browserError",
				onFileLoad: "fileLoad"
			},
			{kind: "help.FailView", name: "articleFail", flex: 1},
			{kind: "help.HelpScrim", name: "paneScrim"}
		]},				
		{kind: "ProgressBar", name: "progressBar", className: "url-progress invisible", animatePosition: false}
	],
	cleanup: function() {
		if (this._timeoutHandle !== null) {
		   clearTimeout(this._timeoutHandle);
		   this._timeoutHandle = null;
	   }
	},
	create: function() {
		this.inherited(arguments);
		this.$.paneScrim[window.PalmSystem ? "show" : "hide"]();
		this.fileUrlChanged();
	},
	handleException: function(inException) {
		this.inherited(arguments);
		this.log("browserserver workaround...");
		this._timeoutHandle = setTimeout(enyo.bind(this, "fileUrlChanged"), 2000);
	},
	resize: function() {
		this.log();
		if (window.PalmSystem) {
			this.$.article.resize();
		}
	},
	fileUrlChanged: function() {
		this.log();
		if (this.fileUrl.length > 0) {
			this.$.article.setUrl(this.fileUrl);
		}
	},
	fileLoad: function(inSender, inMimeType, inUrl) {
		this.log("Handle File Load " + inUrl + " with Mime " + inMimeType);
		this.doLoadUrl(inUrl, inMimeType);
	},
	urlRedirected: function(inSender, inUrl, inCookie) {
		this.log("Handle URL redirect to " + inUrl + " with Cookie " + inCookie);
		this.doLoadUrl(inUrl);
	},
	loadStarted: function() {
		this.log("Load Started");		
		this.progress = 0;
		if (this._timeoutHandle !== null) {
			clearTimeout(this._timeoutHandle);
			this._timeoutHandle = null;
		}
	},
	loadProgress: function(inSender, inProgress) {
		this.log("Load Progress " + inProgress);
		if (this.progress < inProgress) {			
			this.progress = inProgress;
			this.updateProgressBar(inProgress);
			
			if (inProgress === 100) {
				this._timeoutHandle = setTimeout(enyo.bind(this, "clearProgress"), 1000);
			}
		}
	},
	loadStopped: function() {
		this.log("Load Stopped");
	},
	loadCompleted: function() {
		this.log("Load Completed");
		this.clearProgress();
		this.$.paneScrim.hide();
		if (window.PalmSystem) {
			this.$.article.setRedirects([
				{ regex: '.*', enable: true, cookie: 'article' }
			]);
		}
	},
	clearProgress: function() {
		this.log();
		this.updateProgressBar(0);
		this._timeoutHandle = null;		
	},
	browserError: function(inSender, inErrorCode, inErrorMessage) {
		this.log("Error " + inErrorCode + " " + inErrorMessage);
		if (inErrorCode !==  this.WebKitErrors.ERR_WK_FLOADER_CANCELLED) {
			this.loadCompleted();
			this.$.articleFail.setDetails(inErrorMessage + "(" + inErrorCode +")");
			this.$.articleFail.setUrl(this.fileUrl);
			this.$.outerPane.selectView(this.$.articleFail);
		}
		this.clearProgress();
	},
	updateProgressBar: function(progress) {
		if (progress === 0) {
			if(!this.$.progressBar.hasClass("invisible")) {
				this.$.progressBar.addClass("invisible"); 
			}
		} else {
			if(this.$.progressBar.hasClass("invisible")) {
				this.$.progressBar.removeClass("invisible");
			}
			this.$.progressBar.setPosition(progress);
		}
	},
	getChatInfo: function() {
		var info = { };
		if (this.fileUrl.length) {
			info.extraInfo = { 
				articleUrl: this.fileUrl
			};
		}
		return info;
	}
});

