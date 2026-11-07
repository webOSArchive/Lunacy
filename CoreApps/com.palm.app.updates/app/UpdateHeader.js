/* Copyright 2011 Palm, Inc.  All rights reserved. */

enyo.kind({
    name: "UpdateHeader",
    kind: "Header",
    pack: "center",

    label_header_updates: $L('System Updates'),
    label_header_installFailure: $L('Update Failed to Install'),
    label_header_installSuccess: $L('Update Installed'),

    published:{
        labelValue:"",
        payload: {status: "Checking"},
    },
    
    components: [
        {className: "header-icon"},
        {name: "headerLabel", style: "padding-left:10px;", content: this.label_header_updates, className:""},        
    ],
    
    create: function() {
        this.inherited(arguments);
        this.setLabelValue(this.label_header_updates);
    },

    labelValueChanged:function(){
        this.$.headerLabel.setContent(this.labelValue);     
    },
    
    setChecking: function () {
        this.setLabelValue(this.label_header_updates);
    },
    
    payloadChanged:function(){
    	if(this.payload.status == "InstallSuccessful") {
    		this.setLabelValue(this.label_header_installSuccess);
    	} else if (this.payload.status == "InstallFailed"){
            this.setLabelValue(this.label_header_installFailure);
    	} 
    },
});

enyo.kind({
    name: "UpdateHeaderFirstUse",
    kind: "VFlexBox",

    label_header_updates: $L('Update Available'),
    label_header_lowbattery: $L('Low Battery'),

    published:{
        payload: {status: "Checking"}, 
    },

    components: [
        {name: "headerLabel", content: this.label_header_updates, className: "title"},        
    ],
    
    create: function() {
        this.inherited(arguments);
        this.$.headerLabel.setContent(this.label_header_updates);
    },

    payloadChanged: function(){
        if (this.payload.status == "InsufficientCharge") {
            this.$.headerLabel.setContent(this.label_header_lowbattery);
        } else {
            this.$.headerLabel.setContent(this.label_header_updates);
        }
    },

    setChecking: function(){
        
    }
});
