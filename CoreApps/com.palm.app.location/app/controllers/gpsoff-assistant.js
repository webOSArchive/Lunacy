/* Copyright 2009 Palm, Inc.  All rights reserved. */
var GpsoffAssistant = Class.create({
    	initialize: function(prefData){
	this.appMenuModel = {
            visible: true,
            label: $L('Location'),
            items: [{
                label: $L('Locate Me Using...'),
                items: [{
                    label: $L('GPS'),
                    command: 'turnOffGps',
                    disabled: false,
		    chosen:true
                }, {
                    label: $L('Google Services'),
                    command: 'turnOffGoogle',
                    disabled: false,
		    chosen:true
                }]
            }, {
                label: $L('Help'),
                command: Mojo.Menu.helpCmd,
                disabled: false
            }]
        };
	this.prefData = prefData;	
    },
    gpsOffGoogleOffState: [{
                    label: $L('GPS'),
                    command: 'turnOffGps',
                    disabled: false,
		    chosen:true
                }, {
                    label: $L('Google Services'),
                    command: 'turnOffGoogle',
                    disabled: false,
		    chosen:true
    }],
		
   gpsOnGoogleOnState: [{
            label: $L('Use GPS'),
            command: 'turnOnGps',
            disabled: false,
	    chosen:false
        }, {
            label: $L('Use Google Services'),
            command: 'turnOnGoogle',
            disabled: false,
	    chosen:false
    }],
	
   gpsOnGoogleOffState: [{
            label: $L('Use GPS'),
            command: 'turnOnGps',
            disabled: false,
	    chosen:false
        }, {
            label: $L('Google Services'),
            command: 'turnOffGoogle',
            disabled: false,
	    chosen:true
   }],
	
   gpsOffGoogleOnState: [{
            label: $L('GPS'),
            command: 'turnOffGps',
            disabled: false,
	    chosen:true
        }, {
            label: $L('Use Google Services'),
            command: 'turnOnGoogle',
            disabled: false,
	    chosen:false
    }],
		
    activate: function() {
    	 this.isTapped = false;
    },
    
    setup: function(){
    	this.controller.setupWidget(Mojo.Menu.appMenu, {omitDefaultItems:true}, this.appMenuModel);
        this.controller.listen('gps_turnon', Mojo.Event.tap, this.turnonGPS.bindAsEventListener(this));
	this.toggleLocationMenu();
    },
      
    toggleLocationMenu: function(){
		if (!this.prefData.useGps && !this.prefData.useGoogle) {
			Mojo.Log.info("**** sssssssssss both GPS and Google are OFF");
			// both GPS and Google is OFF
			this.appMenuModel.items[0].items = this.gpsOnGoogleOnState;						
		} else if (this.prefData.useGps && this.prefData.useGoogle) {
			Mojo.Log.info("**** sssssssssss both GPS and Google are ON");
			// both GPS and Google is On
			this.appMenuModel.items[0].items = this.gpsOffGoogleOffState;						
		} else if (this.prefData.useGps && !this.prefData.useGoogle) {
			// GPS in ON and Google is OFF
			Mojo.Log.info("**** sssssssssss GPS in ON and Google is OFF");
			this.appMenuModel.items[0].items = this.gpsOffGoogleOnState;						
		} else if (!this.prefData.useGps && this.prefData.useGoogle) {
			// GPS is OFF  and Google is ON
			Mojo.Log.info("**** sssssssssss GPS is OFF  and Google is ON");
			this.appMenuModel.items[0].items = this.gpsOnGoogleOffState;						
		}	
    },
    
    handleCommand: function(event) {
		Mojo.Log.info("**** handleCommand: event.command %s", event.command);
		if(event.type == Mojo.Event.command) {
			if (event.command == 'turnOffGps') {
				this.gpsPreference = false;	
				LocationService.setUseGps(
							this.gpsPreference,
							this.successSetGpsPreference.bind(this), 
							this.errorSetGpsPreference.bind(this), this.controller);
				Event.stop(event);
				//this.handleMonthView();
			} else if (event.command == 'turnOffGoogle') {
				this.useGoogle = false;
				LocationService.setUseGoogle(
							this.useGoogle,
							this.successSetGooglePreference.bind(this), 
							this.errorSetGooglePreference.bind(this), this.controller);
				Event.stop(event);
				//goToToday();
			} else if (event.command == 'turnOnGps') {
				this.gpsPreference = true;
				LocationService.setUseGps(
							this.gpsPreference,
							this.successSetGpsPreference.bind(this), 
							this.errorSetGpsPreference.bind(this), this.controller);
				Event.stop(event);
				//goToToday();
			} else if (event.command == 'turnOnGoogle') {
				this.useGoogle = true;
				LocationService.setUseGoogle(
							this.useGoogle,
							this.successSetGooglePreference.bind(this), 
							this.errorSetGooglePreference.bind(this), this.controller);
				Event.stop(event);
			} else if (event.command === Mojo.Menu.helpCmd) {
				LocationService.launchHelp("http://help.palm.com/location/index.html");
				event.stop();		
			}		
		}		
    },
    successSetGpsPreference: function (response) {
		Mojo.Log.info("Success in setting location service preference %j", $H(response));
		this.prefsResquest = LocationService.getLocationServicePrefs(this.controller,this.handleSetGpsLocateSuccess.bind(this));
    },
	
    handleSetGpsLocateSuccess: function(result) {
		Mojo.Log.info("------------------- Set gps -----------------%j", $H(result));
		if (result && !result.useGps && !result.useGoogle) {
			this.controller.stageController.swapScene("gpsoff", result);
		} else {
			this.controller.stageController.swapScene("main", result);
		}	
    },
	
    errorSetGpsPreference: function (response) {
		Mojo.Log.error("Error in setting errorSetGpsPreference %j", $H(response));
    },
	
    successSetGooglePreference: function (response) {
		Mojo.Log.info("Success in setting google preference %j", $H(response));
		this.prefsResquest = LocationService.getLocationServicePrefs(this.controller,this.handleSetGoogleSuccess.bind(this));
    },
	
    handleSetGoogleSuccess: function(result) {
		Mojo.Log.info("------------------- Set handleSetGoogleSuccess -----------------%j", $H(result));
		if (result && result.useGoogle && !result.isTermsOfUseAccepted) {
			this.controller.stageController.swapScene("terms", "com.palm.app.location");	
		} else if (result && !result.useGps && !result.useGoogle) {
			this.controller.stageController.swapScene("gpsoff", result);
		} else {
			this.controller.stageController.swapScene("main", result);
		}	
    },
	
    errorSetGooglePreference: function (response) {
		Mojo.Log.error("Error in  errorSetGooglePreference %j", $H(response));
		if (response.errorCode == '6') {
			Mojo.Log.error("Error in  errorCode 6");
			if (this.useGoogle == true) {
				this.controller.stageController.swapScene("terms", "com.palm.app.location");
			}
		}
    },
	
    turnonGPS: function(event){
    	//Mojo.Log.error("aaaaaaaaaaa turnonGPS");
        this.gpsPreference = true;
	if(this.isTapped == false) {
		   	Mojo.Log.error("aaaaaaaaaaa isTapped");
	       	 LocationService.setUseGps(this.gpsPreference, this.successSetGpsPreference.bind(this), this.errorSetGpsPreference.bind(this), this.controller);
		 this.isTapped = true;
	 }
    },
    
    successSetGpsPreference: function(response){
       // Mojo.Log.error("Success in setting location service preference %j", $H(response));
        this.prefsResquest = LocationService.getLocationServicePrefs(this.controller, this.handleSetGpsLocateSuccess.bind(this));
    },
    
    handleSetGpsLocateSuccess: function(result){
       // Mojo.Log.error("------------------- handleSetGpsLocateSuccess -----------------%j", $H(result));
        
        this.controller.stageController.popScenesTo();
        this.controller.stageController.pushScene('main', result);
    },
    
    errorSetGpsPreference: function(response){
        Mojo.Log.error("Error in setting location service preference %j", $H(response));
    },
});
