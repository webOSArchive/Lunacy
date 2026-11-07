/* Copyright 2011 Palm, Inc.  All rights reserved. */

enyo.kind({
    name: "UpdateProgressButton",
    kind: "ProgressButton",
    cancelable: false,
    published:{
        content:"",        
    },

    components: [
        {kind: enyo.HFlexBox, pack: 'justify', components: [
            {name: "label", className:"dlPillLabel", style:"line-height:34px"},
            {kind: "Spacer"}
        ]}
    ],

    contentChanged: function() {
        this.log(this.content);
        this.$.label.setContent(this.content);
    }
});

enyo.kind({
    name: "UpdateProgressBar",
    kind: "ProgressBar",    
    published:{
        content:"",        
    },
    
    components: [
        {kind: enyo.HFlexBox, className:"box-center", pack: 'justify', components: []}
    ],

    contentChanged: function() {
        this.log(this.content);
    }
});


enyo.kind({
    name: "UpdateProgress",
    kind: "Control",
    
    label_progress_downloading: $L('Downloading...'),
    label_progress_validating: $L('Validating...'),
    label_progress_waitingForNetwork: $L('Waiting for the network...'),
    label_progress_unpacking: $L('Unpacking...'),
    label_progress_preparedownload: $L('Preparing Download...'),
    
    label_button_installnow: $L('Install Now'),
    label_button_download: $L('Download Now'),
    
    published:{
        labelValue:"",
        payload: {status: "Checking"},
    },
    events: {
        onCancelDownload: "",
    },
    components: [
        {name: "progress", kind: "UpdateProgressButton"},
        
        {kind: "UpdateValidationService", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "getValidationSubscription", method: "Progress", subscribe: true, onSuccess: "handleValidationStatus"},
            ]
        },
        {kind: "SignalBusService", onFailure: "handleGenericFailure", onSuccess: "handleGenericSuccess",
            components: [
                {name: "registerServerStatus", method: "registerServerStatus", subscribe: true, onSuccess: "handleRegistrationStatusValidation"}
            ]
        }
    ],
    
    handleCancelClick: function() {
        this.$.progress.setPosition(0);
        this.doCancelDownload();
        this.setLabelValue(this.label_button_download);
    },
    constructor: function() {
        this.inherited(arguments);
    },
    create: function() {
        this.inherited(arguments);
        this.$.registerServerStatus.call({'serviceName': 'com.palm.validation'});
    },
    
    labelValueChanged:function(){
        this.$.progress.setContent(this.labelValue);
    },
    
    handleValidationStatus: function(inSender, inResponse){
        this.log(JSON.stringify(inResponse));
        if ("percent" in inResponse) {
            if (this.payload.status == 'Validating') {                
                this.$.progress.setPosition(inResponse.percent);
            }
        }
    },
    
    handleGenericSuccess: function(inSender, inResponse) {
        this.log(JSON.stringify(inResponse));
    },
    
    handleGenericFailure: function(inSender, inResponse) {
        this.log(JSON.stringify(inResponse));
    },
    
    handleRegistrationStatusValidation: function(inSender, inResponse) {
        this.log(JSON.stringify(inResponse));
        if (inResponse.connected == true) {
            this.validateRequest = this.$.getValidationSubscription.call();
        } else {
            if (this.validateRequest) {
                this.validateRequest.finish();
                this.validateRequest = undefined;
            }
        }
    },
    
    payloadChanged: function() {
        // [Lunacy] A Lunacy release isn't downloaded here: the button opens its release page,
        // so it says so (codepoet, 2026-11-07).
        if (this.payload.releasePage) {
            this.label_button_download = $L("Learn More");
        }
        if(this.payload.status == "Downloading") {

            this.disabled = false;
            var value = 0;
            if ("percent" in this.payload)
                value = this.payload.percent;

            if (this.payload.networkAvailable == false) {
                this.setLabelValue(this.label_progress_waitingForNetwork);
            } else {
                this.setLabelValue((value >= 45) ? this.label_progress_unpacking : this.label_progress_downloading);
            }
            this.log('update progress: ' + value);
            this.$.progress.setPosition(value);

        } else if (this.payload.status == "PreparingDownload") {
            this.$.progress.setPosition(0);
            this.disabled = true;
            this.setLabelValue(this.label_progress_preparedownload);
            
        } else if (this.payload.status == "Waiting") {
            if ((this.payload.networkAvailable == false) || (this.payload.roaming == true)) {
                this.disabled = true;
                this.setLabelValue(this.label_progress_waitingForNetwork);                
            } else {
                this.disabled = false;
                this.$.progress.setPosition(0);
                this.setLabelValue(this.label_button_download);
            }
            
        } else if (this.payload.status == "Validating") {
            this.disabled = false;         
            this.setLabelValue(this.label_progress_validating);
            if(!this.validateRequest) {
                this.$.progress.setPosition(0);
            }
            
        } else if (this.payload.status == "Available" || this.payload.status == "Countdown") {            
            this.$.progress.setPosition(0);
            this.setLabelValue(this.label_button_installnow);
            this.disabled = false;
            
        } else if (this.payload.status == "NetworkFailed"){
            if(this.oldState != "Checking") {
                if ((this.payload.networkAvailable == false) || (this.payload.roaming == true)) {
                    this.disabled = true;
                    this.setLabelValue(this.label_progress_waitingForNetwork);
                } else {
                    this.disabled = false;
                    this.$.progress.setPosition(0);
                    this.setLabelValue(this.label_button_download);
                }
            }
        } else {
            
        }
        
        if(this.payload.status == "NetworkFailed") {            
        } else {
            this.oldState = this.payload.status;
        }
    }
    
});


enyo.kind({
    name: "UpdateProgressFirstUse",
    kind: "UpdateProgress",
    className: "box",
    label_button_installnow: $L(''),
    label_button_download: $L(''),

    firstUsePill: {name: "progress", kind: "UpdateProgressBar"},
    
    create: function() {
        this.inherited(arguments);
        this.$.progress.destroy();
        this.createComponent(this.firstUsePill);
    },

});