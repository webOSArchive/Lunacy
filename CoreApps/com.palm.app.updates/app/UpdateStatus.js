/* Copyright 2011 Palm, Inc.  All rights reserved. */

enyo.kind({
    name: "UpdateStatus",
    kind: "Control",

    label_status_installFailure: $L('The system update #{version} failed to install. Please try again later.'),
    label_status_installSuccess: $L('The system update #{version} was successfully installed. Your new software is available immediately.'),

    label_status_checking: $L('Checking for updates...'),
    label_status_upToDate: $L('Your device is up to date.'),
    label_status_noNetwork: $L("Waiting for the network..."),
    label_status_failedNetwork: $L("Unable to connect.  Try again later."),

    info_failure: $L("The update failed to properly validate. This problem can usually be fixed if you wait a little while, then try again. We'll try again for you in about a day."),
    info_download: $L("#{version} is now available. Approx size: #{size}MB."),
     info_failedconnection: $L("Unable to connect. Try again later."),
     info_nohighspeedblockedvoice: $L("No high speed data connection is available. We'll continue to wait for a high speed connection to download or you can start the download now. This download will take much longer over a low speed connection and will block incoming calls. "),
     info_nohighspeed: $L("No high speed data connection is available. We'll continue to wait for a high speed connection to download or you can start the download now. This download will take much longer over a low speed connection."),
     info_highspeed: $L("We'll download it over the next couple of days when your device is idle and a high speed network is available.  You'll be notified when the update is ready to install."),
     info_nonetwork: $L("No data connection is available. We'll download it over the next couple of days when your device is idle and a high speed network is available. You'll be notified when the update is ready to install."),
     info_roaming: $L("The data connection is currently roaming. We'll download it over the next couple of days when your device is idle, a high speed network is available and not roaming. You'll be notified when the update is ready to install."),
    info_downloading: $L("#{version} is currently downloading. You'll be notified when the update is ready to install."),
    info_validating: $L("#{version} is currently validating. You'll be notified when the update is ready to install."),
    info_freeSpace:  $L("A system update is now available, but there's not enough free memory on your device to store it.  Free up at least #{spaceNeeded}MB of memory to download the update."),
    info_lowResources: $L("System resources are critically low. Please tap \"Get Instructions\" for suggestions on how to free space."),
    info_install: $L("Installation of #{version} will take about #{installTime} minutes. Your device cannot be used during the updates."),
    info_battery:  $L("Your battery must be at least #{minBattery} full before you install the update.  Please charge your battery before installing the update."),
    info_batteryDownload:  $L("Your battery must be at least #{minBattery} full before you download the update.  Please charge your battery before downloading the update."),

    published:{
        available:"",
        info:"",
        payload: {status: "Checking"},
        blockVoice: false,
    },

    uiComponents:[
        {name: "infoBox", kind: enyo.VFlexBox, align:'center', pack: 'justify', components: [
            {kind: enyo.HFlexBox, components: [
                {name: "availableLabel", content: this.label_status_checking, style: "line-height: 32px;"},
                {name: "headerSpinner", kind: "Spinner", showing: true, style: "margin:0 12px;", spinning: true},
            ]},
            {kind: "Spacer"},
            {name: "infoLabel", content: "", style: "margin:12px 0px;" },
        ]},
    ],

    components: [

        {name: "migrationError", style: "color: red;", showing: false, content: ""},

        {kind: "SystemPrefsService", onFailure: "handleGenericFailure", onSuccess: "handleGetSystemPrefsSuccess",
            components: [
                {name: "GetPreferences", method: "getPreferences", parameters: {'keys': ['allowIncomingCallsOver2G']} }
        ]},

        {name: "checkForMigrationError", kind: "WebService", url: "/var/lib/software/migrationError.json",
            onSuccess: "handleMigrationError"
        },

        {name: "signalAddMatch", kind: enyo.PalmService, 
            service: "palm://com.palm.bus/signal/", method: "addmatch", 
            subscribe: true, onSuccess: "handleBatteryLevel", onFailure: "handleBatteryLevel"},
        {name: "batteryStatus", kind: enyo.PalmService, 
            service: "palm://com.palm.power/com/palm/power/", method: "batteryStatusQuery"}

    ],

    create: function() {
        this.inherited(arguments);
        this.createComponents(this.uiComponents);
    },

    handleBatteryLevel: function(inSender, inResponse){
        this.log(JSON.stringify(inResponse));
    },
 
    handleGenericFailure: function(inSender, inResponse){
        this.log(JSON.stringify(inResponse));
    },
    handleGetSystemPrefsSuccess: function(inSender, inResponse){
        this.log(JSON.stringify(inResponse));
        if ( inResponse && !Object.isUndefined(inResponse['allowIncomingCallsOver2G']) ) {
            this.setBlockVoice(!inResponse['allowIncomingCallsOver2G']);
        }
    },
        
    availableChanged:function(){
        this.$.availableLabel.setContent(this.available);
    },
    infoChanged: function() {
        this.$.infoLabel.setContent(this.info);
    },
    
    setChecking: function () {
        this.setSpinning(true);
        this.setAvailable(this.label_status_checking);
    },
    
    payloadChanged: function() {

        this.log(this.payload.status, this.oldStatus);
        // This check will allow us to leave the network failure text on the screen
        // if the state moves from NetworkFailed to Waiting.
        if (this.payload.status != 'Waiting') {
           this.networkFailed = (this.payload.status == 'NetworkFailed');
        }

        this.setInfo("");
        if (this.payload.status == "Checking") {
            this.setSpinning(this.payload.networkAvailable);
        } else {
            this.setSpinning(false);
        }

        if (this.payload.status == "Checking" || this.payload.status == "UpToDate") {
            if (this.$.infoBox) {
                this.$.infoBox.align = "center";
                this.$.infoBox.render();
            }
        } else {
            this.log("not checking or uptodate");
            if (this.$.infoBox) {
                this.log("set left");
                this.$.infoBox.align = "left";
                this.$.infoBox.render();
            }
        }

        if(this.payload.status == "InstallSuccessful") {
            var tmp = this.label_status_installSuccess;
            tmp = tmp.replace("#{version}", this.payload.version);
            this.setAvailable(tmp);
            this.$.checkForMigrationError.call();
        } else if (this.payload.status == "InstallFailed"){
            var tmp = this.label_status_installFailure;
            tmp = tmp.replace("#{version}", this.payload.version);
            this.setAvailable(tmp);
            this.$.checkForMigrationError.call();
        } else if (this.payload.status == "Checking") {
            if(this.payload.networkAvailable == false) {
                this.setAvailable(this.label_status_noNetwork);
            } else {
                this.setAvailable(this.label_status_checking);
            }

        } else if (this.payload.status == "UpToDate") {
            this.setAvailable(this.label_status_upToDate);

        } else if ( (this.payload.status == "Waiting") || 
                    (this.payload.status == "NetworkFailed") ||
                    (this.payload.status == "PreparingDownload") ) {

            if ( (this.payload.status == "NetworkFailed") && (this.oldStatus == "Checking") ) {
                this.setAvailable(this.label_status_failedNetwork);
            } else {
                var tmp = "";

                if ((this.payload.status == 'NetworkFailed') || (this.payload.status == 'Waiting' && this.networkFailed)) {
//                    this.setAvailable(this.info_failedconnection);
                }

                tmp += this.info_download;
                tmp = tmp.replace("#{version}", this.payload.version);
                tmp = tmp.replace("#{size}", this.payload.size);

                if (this.payload.releasePage) {
                    // [Lunacy] Lunacy's releases come from GitHub, and the person installs them:
                    // Learn More opens the release page. None of Palm's download notes apply.
                    this.setInfo($L("Press Learn More to open the Release page, for details and downloads."));
                } else if (this.payload.roaming === true) {
                   this.setInfo(this.info_roaming);
                } else if (this.payload.networkAvailable === false) {
                    this.setInfo(this.info_nonetwork);
                } else if (this.payload.lowSpeed === false) {
                    this.setInfo(this.info_highspeed);
                } else if(this.payload.blockVoice === true) {
                    this.setInfo(this.info_nohighspeedblockedvoice);
                } else if(this.payload.lowSpeed === true){
                    this.setInfo(this.info_nohighspeed);
                } else {
                    this.setInfo(this.info_failedconnection);

                }
                this.setAvailable(tmp);
            }

        } else if (this.payload.status == "Downloading") {
            var tmp = this.info_downloading;
            tmp = tmp.replace("#{version}", this.payload.version);
            this.setAvailable(tmp);
            
        } else if (this.payload.status == "Validating") {
            var tmp = this.info_validating;
            tmp = tmp.replace("#{version}", this.payload.version);
            this.setAvailable(tmp);
            
        } else if ((this.payload.status == "Available") || (this.payload.status == "InstallBegun") ||
                    (this.payload.status == "Countdown")) {
                        
            if (this.payload.insufficientCharge == true) {
                var tmp = this.info_battery;
                tmp = tmp.replace("#{minBattery}", Util.formatPercent(this.payload.minBattery));
                this.setAvailable(tmp);
            } else {
                var tmp = this.info_install;
                tmp = tmp.replace("#{version}", this.payload.version);
                tmp = tmp.replace("#{installTime}", this.payload.installTime);
                this.setAvailable(tmp);
            }

        } else if (this.payload.status == "SpaceNeeded") {
            var tmp = this.info_freeSpace;
            tmp = tmp.replace("#{spaceNeeded}", this.payload.spaceNeeded);
            this.setAvailable(tmp);

        } else if (this.payload.status == "InsufficientCharge") {
            if(this.payload.resourcesLow && this.payload.resourcesLow == true) {
                this.setAvailable(this.info_lowResources);
            } else {
                var tmp = this.info_battery;
                tmp = tmp.replace("#{minBattery}", Util.formatPercent(this.payload.minBattery));
                this.setAvailable(tmp);
            }

        } else if (this.payload.status == "InvalidUpdate") {
            this.setAvailable(this.info_failure);
        } else {
            console.log('Details: text unknown status: ' + this.payload.status);
        }
        
        if(this.payload.status == "NetworkFailed") {
        } else {            
            this.oldStatus = this.payload.status;
        }

    },

    handleMigrationError: function(inSender, inResponse){
        this.log(enyo.json.to(inResponse));

        if (inResponse.code && inResponse.text) {
            var contentStr = $L("<b>There was and error in Data Migration</b> Please collect logs and file a bug <br> #{errorCode}: #{errorText}<br>");
            var contentTemplate = new enyo.g11n.Template(contentStr);
            this.$.migrationError.setContent(contentTemplate.evaluate({
                errorCode: inResponse.code,
                errorText: inResponse.text
            }));
            this.$.migrationError.show();
        }
    },

    setSpinning:function(value){
        if (this.$.headerSpinner) {
            this.$.headerSpinner.setShowing(value);
        }
    }  

});


enyo.kind({
    name: "UpdateStatusFirstUse",
    kind: "UpdateStatus",

    label_status_failedNetwork: $L("Unable to connect."),
    label_status_checking: $L('Checking for updates...'),
    info_failure: $L("The update failed to properly validate. This problem can usually be fixed if you wait a little while, then try again.  Alternatively, on your desktop computer, visit palm.com/rom to install the update using webOS Doctor."),
    info_roaming: $L("No data connection is available. We'll download it over the next couple of days when your device is idle and a high speed network is available. You'll be notified when the update is ready to install."),
    info_failedconnection: $L("Unable to connect.  We will try again in a few minutes."),

    info_downloading: $L("#{version} is currently downloading. When the download is complete, we'll restart your device."),
    info_validating: $L("#{version} is currently validating. When the validation is complete, we'll restart your device."),
    info_battery:  $L("Your battery must be at least #{minBattery} full before you install the update. To charge your device, use the charger that came with it and plug it into an outlet.  When the battery is charged enough, your update will be installed."),

    uiComponents:[
        {kind: "Control", className:"box-center", align:'center', pack: 'justify', components: [
            {name: "availableLabel", content: this.label_status_checking},
            {name: "infoLabel", content: ""},
            {kind: "Image", showing: false, src: "images/battery.png", style: "margin:30px 0;"},
            {name: "percent", content: "", showing: false, style: "font-size: 38px;"}
        ]},
    ],

    create: function() {
        this.inherited(arguments);
        this.$.signalAddMatch.call({"category":"/com/palm/power","method":"batteryStatus"});
        this.$.batteryStatus.call({});
        this.currentBatteryPercent = 0;
    },

    payloadChanged: function(){
        
        if (this.payload.status == "InsufficientCharge") {
            var tmp = this.info_battery;
            tmp = tmp.replace("#{minBattery}", Util.formatPercent(this.payload.minBattery));
            this.setAvailable(tmp);

            this.$.image.show();
            this.$.percent.show();
        } else  if (((this.payload.status == "Waiting") || 
                     (this.payload.status == 'NetworkFailed') ||
                     (this.payload.status == "PreparingDownload")) && 
                    (this.payload.insufficientCharge == true) ) {
            var tmp = this.info_batteryDownload;
            tmp = tmp.replace("#{minBattery}", Util.formatPercent(this.payload.minDownloadBattery));
            this.setAvailable(tmp);

            this.$.image.show();
            this.$.percent.show();
        } else if (((this.payload.status == "Available") || 
                    (this.payload.status == "InstallBegun") ||
                    (this.payload.status == "Countdown")) && 
                    (this.payload.insufficientCharge == true)) {
            var tmp = this.info_battery;
            tmp = tmp.replace("#{minBattery}", Util.formatPercent(this.payload.minBattery));
            this.setAvailable(tmp);

            this.$.image.show();
            this.$.percent.show();

        } else {
            this.$.image.hide();
            this.$.percent.hide();

            this.inherited(arguments);
        }

    },
    
    handleBatteryLevel: function(inSender, inResponse) {
        this.log(JSON.stringify(inResponse));

        if(!inResponse.percent_ui) {
            return;
        }

        this.currentBatteryPercent = inResponse.percent_ui;
        this.$.percent.setContent(Util.formatPercet(this.currentBatteryPercent));
    }

});
