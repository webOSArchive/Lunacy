/* Copyright 2011 Palm, Inc.  All rights reserved. */
enyo.kind({
    name: "UpdateWebView",
//    kind: enyo.WebView,
    kind: "Control",
    published:{
        currentVersion:"",
        payload: {status: "Checking"},
    },

    components: [
        {name: "releaseNotes", allowHtml: true},

        {name: "releaseNotesService", kind: "WebService", handleAs: "text", onSuccess: "handleReleaseNotes", onFailure: "handleReleaseNotesFailure"},
        {kind: "SystemProperty", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [{name: "getProperty", method: "systemProperties/Get"}]
        },
    ],

    constructor: function() {
        this.inherited(arguments);
    },

    create: function() {
        this.inherited(arguments);
        this.$.getProperty.call({"key": "com.palm.properties.version"}, {onSuccess: "handleGetVersionProperty"});
    },

    currentVersionChanged:function(){
        this.log("currentVersionChanged " + this.currentVersion);

        if (this.payload.status == 'UpToDate') {
           this.setReleaseNotes(this.currentVersion, "installed");
           this.showReleaseNotes();
        }
    },

    setChecking: function(){
        this.hide();
    },

    showReleaseNotes: function() {

        switch (this.payload.status) {
        case "InstallSuccessful":
        case "UpToDate":
        case "Waiting":
        case "Downloading":
        case "Validating": 
        case "Available": 
        case "Countdown": 
        case "PreparingDownload":
            this.show();
            break;

        case "InstallFailed":
        case "Checking":
        case "UpToDate":
        case "SpaceNeeded": 
        case "InvalidUpdate": 
        case "InsufficientCharge":
        case "NetworkFailed": 
            this.hide(); 
            break;

        case "InstallBegun": 
        default : 
            break;
        }
    },

    payloadChanged:function(){
        this.log('payloadChanged ' +  JSON.stringify(this.payload));

        if(this.payload.status == this.status)
        {
            this.log("status did not change");
            return;
        }

        switch (this.payload.status) {
        case "InstallSuccessful":
            this.setReleaseNotes(this.payload.version, "installed");
            break;

        case "UpToDate":
//            this.setReleaseNotes(this.currentVersion, "installed");
            break;

        case "Waiting":
        case "Downloading":
        case "Validating": 
        case "Available": 
        case "Countdown":
        case "PreparingDownload":        
            if ((this.status != "Waiting" && this.status != "Downloading" &&
                this.status != "Validating" && this.status != "Available" && 
                this.status != "Countdown") || 
                (this.networkAvailable == false && this.payload.networkAvailable == true) ) { 
                this.setReleaseNotes(this.payload.version, "available");
            }
            break;

        case "InstallFailed":
        case "Checking":
        case "SpaceNeeded": 
        case "InvalidUpdate": 
        case "InsufficientCharge": 
        case "NetworkFailed":
            this.showReleaseNotes(); 
            break;

        case "InstallBegun": 
        default : 
            break;
        }

        this.networkAvailable = this.payload.networkAvailable;

        if(this.payload.status == "NetworkFailed") {
        } else {
            this.status = this.payload.status;
        }

    },

    getReleaseNotesUrl: function(version, type){
        if(version && version != "") {
            var versionlist = version.split(" ");
            var urlVersion = versionlist[2].replace(/\./g, "-");
            if(type == "installed") {                
                return "http://help.palm.com/release_notes/webOS_#{version}_installed.html".replace("#{version}", urlVersion);
            } else {    
                return "http://help.palm.com/release_notes/webOS_#{version}_available.html".replace("#{version}", urlVersion);
            }
        } else {
            return "";
        }
    },

    setReleaseNotes: function(version, type) {
        // [Lunacy] The notes come with the release (its GitHub release notes, as plain text), not
        // from help.palm.com, which is gone; this build's own have nowhere to come from.
        if (type == "available" && this.payload.releaseNotes) {
            this.$.releaseNotes.setContent(enyo.string.escapeHtml(this.payload.releaseNotes).replace(/\n/g, "<br>"));
            this.showReleaseNotes();
        }
        return;
        var url = this.getReleaseNotesUrl(version, type);
        this.log(url);
        if (url.length > 0) {
            this.$.releaseNotesService.setUrl(url);
            this.$.releaseNotesService.call();
        }
    },

    handleGetVersionProperty: function(inSender, inResponse) {
        this.log(JSON.stringify(inResponse));
        if ("com.palm.properties.version" in inResponse) {
            this.setCurrentVersion(inResponse["com.palm.properties.version"]);
        }
    },

    handleReleaseNotes: function(inSender, inResponse, inRequest) {
        this.results = inResponse;
        this.log(inRequest.xhr.status, "length=", this.results.length);

        if (inRequest.xhr.readyState == 4) {
            if ( (inRequest.xhr.status==200) && (this.results.length > 0)) {
                this.log("responseText = " + this.results);

                // Do not show the coming soon content.
                // case-insensitive search
                if ((this.results.search(/<!-- Content Coming Soon! -->/i) == -1) &&
                    (this.results.search(/<!-- hp webos update release notes -->/i) != -1)) {
//                    this.results = "....<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.<p>.....<p>";
                    this.$.releaseNotes.setContent(this.results);
                    this.showReleaseNotes();
                } else {
                    this.log("block content");
                }
            }
        }
    },

    handleReleaseNotesFailure: function(inSender, inResponse, inRequest) {
        this.log("got failure");
    },

    setUrl: function(url){

    }

});
