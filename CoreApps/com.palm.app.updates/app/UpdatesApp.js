/* Copyright 2011 Palm, Inc.  All rights reserved. */﻿
enyo.kind({
    name: "UpdatesApp",
    kind: "VFlexBox",    
    published:{
        payload:{status: "Checking"},
        currentVersion: "",
        isInternetConnectionAvailable: true,
        systemCheckInProgress: false,
        firstUse: false
    },
    
    label_button_ok: $L('OK'),
    label_button_done: $L('Done'),
    label_button_now: $L('Update Now'),
    label_button_later: $L('Update Later'),
    label_button_getInstructions: $L('Get Instructions'),
    
    defaultDisplay: [
        {name: "header", kind: "UpdateHeader", className: "page-header"},
        {kind: "VFlexBox", flex: 1, pack: "top", align: "center", components: [

            {kind: "VFlexBox", className:"box-center box", flex: 1, style: "max-height: 338px; margin: 32px 0 12px 0;", components: [
                {kind: "FadeScroller", autoVertical: true, horizontal: false, autoHorizontal: false, flex: 1, components: [
                    {name: "info", kind: "UpdateStatus"},
                    {name: "releaseNotes", kind: "UpdateWebView", showing: false}
                ]}
            ]},

			{name: "dlPill", kind: "UpdateProgress", showing: false, width: "500px", style: "max-width: calc(100% - 24px);" /* [Lunacy] no wider than a phone's card, less the column's margins (layout.js) */, onclick: "downloadNowClick", onCancelDownload: "cancelDownloadClick"},
            {name: "doneButton", kind: "Button", caption: this.label_button_ok, showing: false, width: "500px", style: "max-width: calc(100% - 24px);" /* [Lunacy] no wider than a phone's card, less the column's margins (layout.js) */, onclick: "doneButtonClick"},

        ]},
        {kind: "Toolbar", showing: false, className: "enyo-toolbar-light", align:"center", pack:"center", components: [
			{kind: "Button", className: "enyo-button-dark", caption: $L("Check for Updates"), width: "18.75em", onclick: "refreshClick"}
        ]},
       
        {kind: "AppMenu", components: [            
            {kind: "HelpMenu", target: "http://help.palm.com/updates/index.html"}           
        ]}
    ],
    
    firstUseDisplay: [
        
        {kind: enyo.VFlexBox, align:'center', pack: 'justify', components: [
            {name: "header", kind: "UpdateHeaderFirstUse", className: "title"},            
            {name: "info", kind: "UpdateStatusFirstUse", className: "subtitle"},
            
            {name: "updateNowButton", kind: "Button", caption: this.label_button_now, 
                showing: false, onclick: "nowButtonClick"},
            {name: "updateLaterButton", kind: "Button", caption: this.label_button_later, 
                showing: false, onclick: "laterButtonClick"},
            {name: "dlPill", kind: "UpdateProgressFirstUse", showing: false,
                onclick: "downloadNowClick", onCancelDownload: "cancelDownloadClick"}
        ]}
    ],
    
    components: [

        {kind: "Scrim", layoutKind: "VFlexLayout", align: "center", pack: "center", components: [
            {kind: "SpinnerLarge", showing: true}
        ]},
        
        {kind: "NetworkAlerts", onTap: "handleNetworkAlertResponse"},

        {kind: "UpdateDaemonService", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "checkForUpdate", method: "CheckForUpdate", onSuccess: "handleCheckForUpdate"},
                {name: "installNow", method: "InstallNow"},//, onSuccess: "handleInstallNow"},
                {name: "downloadNow", method: "DownloadNow"},//, onSuccess: "handleDownloadNow"},
                {name: "dismissedUpdate", method: "DismissedUpdate"},//, onSuccess: "handleDismissedUpdate"},                
                {name: "cancelDownload", method: "CancelDownload", onSuccess: "handleCancelDownload"},
                {name: "getSubscription", method: "GetStatusApp", subscribe: true, onSuccess: "handleGetSubscription"},
                {name: "getStatus", method: "GetStatusApp"}//, onSuccess: "handleGetStatus"}
        ]},
        {kind: "ConnectionManagerService", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "cmGetStatus", method: "getstatus", subscribe: true, onSuccess: "handleCMStatus"}
        ]},
        {kind: "SignalBusService", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "registerServerStatus", method: "registerServerStatus", subscribe: true}
        ]},
        {kind: "SystemProperty", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "getProperty", method: "systemProperties/Get"}
        ]},
        {kind: "ApplicationManagerService", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "openDbHelp", method: "open", params: {id: "com.palm.app.help", params: {target: 'http://help.palm.com/basics/basics_db_full.html'}}}
        ]},
        
        {name: "signalAddMatch", kind: enyo.PalmService, 
            service: "palm://com.palm.bus/signal/", method: "addmatch", 
            subscribe: true, onSuccess: "handleBatteryLevel", onFailure: "handleBatteryLevel"},        
        {name: "batteryStatus", kind: enyo.PalmService, 
            service: "palm://com.palm.power/com/palm/power/", method: "batteryStatusQuery"}
    ],

    create: function() {
        this.inherited(arguments);
        console.log("create params= " + JSON.stringify(enyo.windowParams));
        this.userSelected = false;
        this.systemCheckInProgress = false;
        
        this.params = enyo.windowParams;
///////////////////////////////////////////////////////        
//        this.params.firstUse = true;
//        this.params.backupRestore = true;
///////////////////////////////////////////////////////
        if ("firstUse" in this.params) {
            this.firstUse = this.params.firstUse;
        }
        
        if("backupRestore" in this.params) {
            this.backupRestore = true;
            this.firstUse = true;
        }
            
        this.log("create info component");        
        if (this.firstUse) {
            this.currentBatteryPercent = undefined;
            this.$.signalAddMatch.call({"category":"/com/palm/power","method":"batteryStatus"});
            this.$.batteryStatus.call({});

            this.createComponents(this.firstUseDisplay);
            this.$.updateNowButton.setCaption(this.label_button_now);
            this.$.updateLaterButton.setCaption(this.label_button_later);

        } else {
            this.createComponents(this.defaultDisplay);

            if ("installNow" in this.params) {
                this.setPayload({status: "Available"});
            } else if ("diskSpaceNeeded" in this.params) {
                this.setPayload({status: "SpaceNeeded"});
            } else if ("installFailed" in this.params) {
                this.setPayload({status: "InstallFailed", 'version':enyo.string.escapeHtml(this.params.version)});
            } else if ("updateSuccessful" in this.params) {
                this.setPayload({status: "InstallSuccessful", 'version':enyo.string.escapeHtml(this.params.version)}); 
            } else {
    
            }
        }

        if(this.payload.status != "InstallSuccessful" && this.payload.status != "InstallFailed"){
            this.$.cmGetStatus.call();
            this.$.registerServerStatus.call({'serviceName': 'com.palm.update'}, {onSuccess: "handleRegistrationStatusUpdate"});        
            this.checkForUpdate();
        }
    },

    checkForUpdate: function() {
        this.log('checkForUpdate');
        if(this.systemCheckInProgress == false){
            this.systemCheckInProgress = true;
            this.$.checkForUpdate.call({"firstUse": this.firstUse});
            if (this.$.toolbar) {
                this.$.toolbar.hide();
            }
            this.$.header.setChecking();
            this.$.info.setChecking();
            if (this.$.releaseNotes) {
                this.$.releaseNotes.setChecking();
            }
        } else{
            this.log("Check already in progress");
        }
    },

    payloadChanged: function(){
        this.log('payloadChanged ' +  JSON.stringify(this.payload));

        if(this.cancelDownload == true) {
            return;
        }

        if (this.oldState == "InvalidUpdate") {
            return;
        }

        if( this.payload.spaceNeeded > 0) {
            this.payload.status = "SpaceNeeded";
        }

        if( "insufficientCharge" in this.payload) {
            this.insufficientCharge = this.payload.insufficientCharge;
        }

        if( "resourcesLow" in this.payload) {
            this.resourcesLow = this.payload.resourcesLow;  
        } else {
            this.resourcesLow = false;
        }

        if (this.payload.status == 'Checking') {
            if (this.payload.networkAvailable == false) {
                this.$.networkAlerts.push({type: "Data"});
            }
        }
        
        if ((this.payload.status == "Download2G") || (this.payload.status == "DownloadRetry")) {
            this.payload.status = "Waiting";
        }
////////////////////////////////////////////////////////////////////////
//        this.payload.priority = "optional"
////////////////////////////////////////////////////////////////////////
        if (this.firstUse === true && (this.payload.status != "Checking")) {
            // if backupRestore is set to true override anything the server tells us.
            if(this.backupRestore == true){
                this.log("force the update");
                this.payload.priority = "forced";
            }

            if (this.payload.priority && this.payload.priority == "optional") {
                // if optional do not go past the waiting status unless the 
                // user has selected an option.
                if(this.userSelected == false){
                    this.payload.status = "Waiting";
                }
            }
        }

        if(this.payload.status == "InstallSuccessful" || this.payload.status == "InstallFailed"){
            this.$.doneButton.show();
            this.$.doneButton.setCaption(this.label_button_done);

        } else if(this.payload.status == "Checking"){
            if (this.$.toolbar) {
                this.$.toolbar.hide();
            }

        } else if (this.payload.status == 'UpToDate') {
            // if this is an firstUse then we know that there is an update and we should retry after 5 second wait.
//            if (this.firstUse === true){  
//                setTimeout(function(){    
//                    this.updatesModel.checkForUpdates(this.callback.bind(this));
//                }.bind(this), 5000);
//                return;
//            }

            this.$.dlPill.hide();

            if (this.firstUse != true) {
                if (this.$.toolbar) {
                    this.$.toolbar.show();
                }
            } else {
                this.sendResult({"status": "uptodate"});
            }

            if (this.oldState == 'NetworkFailed' && this.payload.networkAvailable == true && this.failCount < 5) {
                console.log('Network is available again, recheck for updates.');
                this.checkForUpdate();
            } else {
                this.failCount = 0;
            }

        } else if (this.payload.status == "Waiting") {
            this.log("firstuse: " + this.firstUse);            
            if (this.firstUse === true) {
                
                if(this.userSelected == true){
                    this.$.updateNowButton.hide();
                    this.$.updateLaterButton.hide();
                    if(this.payload.networkAvailable == true){
                        this.$.downloadNow.call({});
                    }
                } else if(this.insufficientCharge == true) {
                    this.$.updateNowButton.hide();
                    if (this.payload.priority == "optional") {
                        this.$.updateLaterButton.show();
                    } else {
                        this.$.updateLaterButton.hide();
                    }
                    this.$.dlPill.hide();
                } else {                
                    if (this.payload.priority && this.payload.priority == "optional") {
                        this.$.updateNowButton.show();
                        this.$.updateLaterButton.show();
                    // if firstuse has a background update it is likely that update app will not be called.
                    // however this is here just in case.
                    } else if (this.payload.priority && this.payload.priority == "default") {
                        this.laterButtonClick();
                    } else if (this.payload.priority && this.payload.priority == "forced") {
                        // this is considered a force update.  Start the download right away.
                        this.$.downloadNow.call({});
                        this.$.dlPill.show();
                    }
                }
            } else {
                this.$.dlPill.show();    
            }            

        } else if (this.payload.status == "Downloading") {
            if(this.firstuse == true) {
                this.$.updateNowButton.hide();
                this.$.updateLaterButton.hide();
                if (this.insufficientCharge == true && this.userSelected == false){
                    this.$.dlPill.hide();
                }
            } else {
                this.networkAvailable = this.isInternetConnectionAvailable;
                this.$.dlPill.show();
            }
            if (this.$.doneButton) {
                this.$.doneButton.hide();
            }
            
        } else if (this.payload.status == "Validating") {
            this.$.dlPill.show();
            
        } else if ((this.payload.status == "Available") || (this.payload.status == "Countdown")) {
            if (this.insufficientCharge == true) {
                this.$.dlPill.hide();
            } else {
                if (this.firstUse === true){
                    this.sendResult({"status": "complete"});
                } else {
                    this.$.dlPill.show();
                }        
            }
            if (this.$.doneButton) {
                this.$.doneButton.hide();
            }

        } else if (this.payload.status == "InstallBegun") {

        } else if (this.payload.status == "SpaceNeeded") {
            this.$.dlPill.hide();
            this.$.doneButton.setCaption(this.label_button_ok);
            this.$.doneButton.show();

        } else if (this.payload.status == "InvalidUpdate") {
            if (this.firstUse === true){

            } else {
                this.$.dlPill.hide();
                this.$.doneButton.setCaption(this.label_button_ok);
                this.$.doneButton.show();
            }

        } else if (this.payload.status == "InsufficientCharge") {
            
            if (this.resourcesLow == true) {
                this.$.dlPill.hide();
                this.$.doneButton.setCaption(this.label_button_getInstructions);
                this.$.doneButton.show();
            } else {
                this.$.dlPill.hide();
            }
        } else if (this.payload.status == 'NetworkFailed') {

            if(this.oldState == "Checking") {
                this.failCount = this.failCount + 1;
                if (this.$.toolbar) {
                    this.$.toolbar.show();
                }
                this.$.dlPill.hide();
            } else {
                if (this.firstUse === true) {
                    setTimeout(function(){    
                        this.$.downloadNow.call({});
                    }.bind(this), 2000);
                } else {
                    this.$.dlPill.show();
                }
            }
            
        } else {
            this.$.dlPill.hide();
        }

        if (this.$.info) {
            this.$.info.setPayload(this.payload);
        }
        if (this.$.header) {
            this.$.header.setPayload(this.payload);
        }
        if (this.$.dlPill) {
            this.$.dlPill.setPayload(this.payload);
        }
        if (this.$.releaseNotes) {
            this.$.releaseNotes.setPayload(this.payload);
        }

        if(this.payload.status == "NetworkFailed") {
            this.log("do not record");
        } else {            
            this.log("record change");
            this.oldState = this.payload.status;
        }

    },

    refreshClick: function() {
        
        if (this.networkAvailable == false) {
            this.$.networkAlerts.push({type: "Data"});
        } else {
            this.checkForUpdate();    
        }
        
    },

    sendResult: function(params) {
        this.log(JSON.stringify(params));
        if(window.parent) {
            this.log("post message to firstuse");
            window.parent.postMessage("enyoCrossAppResult=" + JSON.stringify(params), "*");
        } else {
            this.log("no parent");
        }
    },

    nowButtonClick: function() {
        this.log();
        if (this.firstUse === true) {
            this.userSelected = true;
            this.$.updateNowButton.hide();
            this.$.updateLaterButton.hide();
            this.$.dlPill.show();
            this.doneButtonClick();
        }
    },
    
    laterButtonClick: function() {
        this.log();
        if (this.firstUse === true) {
            this.userSelected = true;
            this.$.updateNowButton.hide();
            this.$.updateLaterButton.hide();

            this.log("user choose to delay update");
            this.sendResult({"status": "background"});
            this.$.dismissedUpdate.call({});
            window.close();
        }
    },

    doneButtonClick: function() {
        this.downloadNowClick();
    },

    cancelDownloadClick: function(){
        this.log();
        this.cancelDownload = true;
        this.$.cancelDownload.call();        
    },
    
    downloadNowClick: function() {
        this.log(this.payload.status);

        if(this.cancelDownload == true) {
            return;
        }

        if(this.payload.status == "InstallSuccessful" || this.payload.status == "InstallFailed"){
            window.close();
        } else if ( (this.payload.status == 'Downloading') || 
                    (this.payload.status == 'Waiting') || 
                    (this.payload.status == 'NetworkFailed')) {
            this.$.downloadNow.call({});
        } else if (this.payload.status == 'Available' || this.payload.status == 'Countdown') {
            this.$.scrim.show();
            this.$.installNow.call({});
        } else if (this.payload.status == "SpaceNeeded") {
            window.close();
        } else if (this.payload.status == "InvalidUpdate" || this.oldState == "InvalidUpdate") {
            if (this.firstUse === true) {
                this.checkForUpdate();
            } else {
                window.close(); 
            }
        } else if (this.payload.status == "InsufficientCharge") {
            this.$.openDbHelp.call();
        } else {
            this.log("Button pressed with unknown state" + JSON.stringify(this.payload) );
        }
    },

    handleGetSubscription: function(inSender, inResponse){
        this.log(JSON.stringify(inResponse));
        if (this.systemCheckInProgress == false) {
            this.setPayload(inResponse);
        }
    },
    
    handleCheckForUpdate: function(inSender, inResponse){
        this.log(JSON.stringify(inResponse));
        this.systemCheckInProgress = false;
        this.setPayload(inResponse);        
    },

    handleCancelDownload: function(inSender, inResponse){
        this.cancelDownload = false;    
    },

    handleGenericSuccess: function(inSender, inResponse) {
        this.log("generic success: " + JSON.stringify(inResponse));
    },

    handleGenericFailure: function(inSender, inResponse) {
        this.log("generic failure: " + JSON.stringify(inResponse));
    },

    handleCMStatus: function(inSender, inResponse) {
        this.log("CM " + JSON.stringify(inResponse));
        
        this.setIsInternetConnectionAvailable(inResponse.isInternetConnectionAvailable);
        if(this.serviceRequest){
            console.log("current payload: " + JSON.stringify(this.payload));
            this.setPayload(this.payload);
        }        
    },

    handleRegistrationStatusUpdate: function(inSender, inResponse) {
        this.log("UpdateDaemon Service state: " + inResponse.connected);
        this.updateDaemonRunning = inResponse.connected;
        if (inResponse.connected == true) {
            if(!this.serviceRequest) {
               this.serviceRequest = this.$.getSubscription.call();
            }
        } else {
            if(this.serviceRequest) {
                this.serviceRequest.finish();
                this.serviceRequest = undefined;
            }
            if(this.payload.status == "Checking") {
                this.$.checkForUpdate.call({"firstUse": this.firstUse});
            }
        }
    },
    
    handleNetworkAlertResponse: function(inSender, inResponse) {
        this.log(JSON.stringify(inResponse));
    },
    
    handleBatteryLevel: function(inSender, inResponse) {        
        this.log(JSON.stringify(inResponse));
        
        if(!inResponse.percent_ui) {
            return;
        }
        
        this.currentBatteryPercent = inResponse.percent_ui;        
//        this.$.percent.setContent(this.currentBatteryPercent + "%");
    }
});

