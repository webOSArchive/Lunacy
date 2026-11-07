/* Copyright 2009 Palm, Inc.  All rights reserved. */

var MainAssistant = Class.create({
//{label:$L('Turn On GPS'), command:'turnOnGps', disabled:false},
//{label:$L('Turn On Google'), command:'turnOnGoogle', disabled:false},
	initialize: function(prefData){
	this.isGoogleON = true,
		
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
   	 /* this.toggleAttr= {
		modelProperty: 'toggleOption',
		trueValue: "ON",
	   	falseValue: "OFF"		
	   }; 
	 
  	   this.toggleModel= {
        	toggleOption: "ON",
		disabled : false
   	   };*/
	
   	   this.geotagPhotosAttr= {
		modelProperty: 'geotagPhotosOption',
		trueValue: "ON",
	   	falseValue: "OFF"		
    	    }; 
	   this.geotagPhotosModel= {
	        geotagPhotosOption: "ON",
			disabled : false
	    };		
		
	   this.dataAttr= {
        	modelProperty: 'dataOption',
			trueValue: "ON",
        	falseValue: "OFF"		
    	    }; 
	   this.dataModel= {
	        dataOption: "ON",
			disabled : false
	    };
		
	   this.prefData = prefData;
	
	},
	
	gpsNotPresentGoogleOffState: [{
                    label: $L('Google Services'),
                    command: 'turnOffGoogle',
                    disabled: false,
		    chosen:true
                }],
                
    gpsNotPresentGoogleOnState: [{
            label: $L('Google Services'),
            command: 'turnOnGoogle',
            disabled: false,
    chosen:false
        }],
                
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
	
	setup: function() {
		//for app locate preferences
		var autoLocateSelections = {
			modelProperty: 'value',
			choices: [
				{label: $L('Always Ask'), value: "1"},
				{label: $L('Auto Locate'), value: "2"}
				
			]
		};
		
		this.autoLocateModel = {
			value:2 //for initial setup, setting it to auto locate always
		};
		
		this.controller.setupWidget('autoLocatePrefSelector',autoLocateSelections, this.autoLocateModel);
		this.controller.listen('autoLocatePrefSelector',Mojo.Event.propertyChange,this.setAutoLocatePreference.bind(this));
		
		//for site locate preferences
		var siteLocateSelections = {
			modelProperty: 'value',
			choices: [
				{label: $L('Never Share Location'), value: "1"},
				{label: $L('Always Ask'), value: "2"}
			]
		};
		
		this.siteLocateModel = {
			value:2 //for initial setup, setting it to always ask
		};
		
		this.controller.setupWidget('siteLocatePrefSelector',siteLocateSelections, this.siteLocateModel);
		this.controller.listen('siteLocatePrefSelector',Mojo.Event.propertyChange,this.setSiteLocatePreference.bind(this));
		
		//clearing site preference
		this.controller.get('clearSitePrefs').observe('mojo-tap', this.clearSiteLocatePreference.bind(this));
			
		//for other preferences

		this.controller.setupWidget('geotagPhotosPreference', this.geotagPhotosAttr, this.geotagPhotosModel);
		this.controller.setupWidget('dataPreference', this.dataAttr, this.dataModel);
		this.controller.setupWidget(Mojo.Menu.appMenu, {omitDefaultItems:true}, this.appMenuModel);
		
		//this.controller.get('autoLocatePreference').observe('mojo-property-change', this.setAutoLocatePreference.bind(this));
		this.controller.get('geotagPhotosPreference').observe('mojo-property-change', this.setGeotagPhotosPreference.bind(this));
		this.controller.get('dataPreference').observe('mojo-property-change', this.setDataPreference.bind(this));
		
		Mojo.Log.info("Response value ============== %j", $H(this.prefData));
		
		// This should not get called
		if(!this.prefData) {
			this.prefsRequest = LocationService.getLocationServicePrefs(this.controller,this.readLocationServicePrefs.bind(this), 
						this.errorLocationServicePrefs.bind(this));
			return;
		}
		this.readLocationServicePrefs(this.prefData);
	},
	
	toggleLocationMenu: function() {
		if (!this.prefData.gpsAvailable && !this.prefData.useGoogle) {
			Mojo.Log.info("****  GPS not avaiable and Google are OFF");
			this.appMenuModel.items[0].items = this.gpsNotPresentGoogleOnState;						
		} else if (!this.prefData.gpsAvailable && this.prefData.useGoogle) {
			Mojo.Log.info("****  GPS not avaiable and  Google are On");
			this.appMenuModel.items[0].items = this.gpsNotPresentGoogleOffState;						
		} else if (!this.prefData.useGps && !this.prefData.useGoogle) {
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
	
	successSetGpsPreference: function (response) {
		Mojo.Log.info("Success in setting location service preference %j", $H(response));
		this.prefsResquest = LocationService.getLocationServicePrefs(this.controller,this.handleSetGpsLocateSuccess.bind(this));
	},
	
	handleSetGpsLocateSuccess: function(result) {
		Mojo.Log.info("------------------- Set gps -----------------%j", $H(result));
		if (result && !result.useGps && !result.useGoogle && result.gpsAvailable) {
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
		} else if (result && !result.useGps && !result.useGoogle && result.gpsAvailable) {
			this.controller.stageController.swapScene("gpsoff", result);
		} else {
			if (result.useGoogle) {
							this.readUseBackgroundDataCollection(result);
							this.enableBackGroundDataCollection();
						}
						else {
							this.disableBackGroundDataCollection();
						}
		  this.prefData = result;
		  this.toggleLocationMenu();								
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
						
	handleCommand: function(event) {
		Mojo.Log.info("**** sssssssssss handleCommand: event.command %s", event.command);
		if(event.type == Mojo.Event.command) {		
			if (event.command == 'gpsNotPresent') {
				Mojo.Log.info("**** cmmmmm gpsNotPresent");
				Event.stop(event);
				//this.handleMonthView();
			} else if (event.command == 'turnOffGps') {
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

	/*
	setUpSimulator: function() {
		this.toggleModel.toggleOption="OFF";
		this.controller.modelChanged(this.toggleModel);
		$('locationOffMessage').show();
	},
	*/
	
	readLocationServicePrefs: function(response) {
		Mojo.Log.info("Preference to use location services: %j", $H(response));
		
		if(response != undefined && response.autoLocate != undefined) {
			Mojo.Log.info("Preference to autoLocate: ", response.autoLocate);
			//for app auto locate preferences
			
			if(!response.autoLocate) {
				this.disableAutoLocate();
				this.autoLocateModel.value=1;
			} else if(!response.cameraAvailable) {
				this.enableAutoLocate(false);
				this.autoLocateModel.value=2;
			}else {
				this.enableAutoLocate(true);
				this.autoLocateModel.value=2;
			}
			this.controller.modelChanged(this.autoLocateModel);
			//for website preferences
			if(!response.webSetting){
				this.siteLocateModel.value=1;
				this.controller.get('sitelocationOnMessage').hide();
				this.controller.get('sitelocationOffMessage').show();
				
			}
			else{
				this.siteLocateModel.value=2;
				this.controller.get('sitelocationOffMessage').hide();
				this.controller.get('sitelocationOnMessage').show();
				
			}
			this.controller.modelChanged(this.siteLocateModel);
			
			//for other preferences
			if (response != undefined && response.isTermsOfUseAccepted != undefined) {
				// [Lunacy] Always hidden: see enableBackGroundDataCollection.
				this.disableBackGroundDataCollection();
			}
			this.readUseGeotagPhotos(response);
		}
		this.toggleLocationMenu();
	},
	

	errorLocationServicePrefs: function(response) {
		Mojo.Log.error("Could not read location preferences %j", $H(response));
	},
	
	readUseGeotagPhotos: function(response) {
		Mojo.Log.info("Preference to use geotag photos: %j", $H(response));
		if(response && !response.geotagPhotos) {
			this.geotagPhotosModel.geotagPhotosOption="OFF";
			this.controller.modelChanged(this.geotagPhotosModel);
		} else {
			this.geotagPhotosModel.geotagPhotosOption="ON";
			this.controller.modelChanged(this.geotagPhotosModel);			
		}
	},	
	
	readUseBackgroundDataCollection: function(response) {
		Mojo.Log.info("Preference to use BackgroundDataCollection: %j", $H(response));
		if(response != undefined && response.useBackgroundDataCollection != undefined) {
			if(response.useBackgroundDataCollection == false) {
				this.dataModel.dataOption="OFF";
				this.controller.modelChanged(this.dataModel);
			} 
		} else {
			this.errorUseBackgroundDataCollection(response);
		}
	},
	
	errorUseBackgroundDataCollection: function(response) {
		Mojo.Log.error("Could not read background data collection preferences %j", $H(response));
	},
	
	setAutoLocatePreference: function (event) {
		Mojo.Log.info("------------------- Set preference -----------------", event.value);
		var autoLocate = false;
		if (event.value == 2) {
			autoLocate = true;
			if(this.prefData.cameraAvailable) {
				Mojo.Log.info("------------------- Set preference camera present");
				this.enableAutoLocate(true);
			} else {
				Mojo.Log.info("------------------- Set preference camera Not present");
				this.enableAutoLocate(false);
			}
				
		} else {
			this.disableAutoLocate();	
		}
		
		LocationService.setAutoLocate(
							autoLocate, 
							this.successSetUseLocationServices.bind(this,autoLocate), 
							this.errorSetUseLocationServices.bind(this), this.controller);
	},
	setSiteLocatePreference: function(event){
		Mojo.Log.info("******setSitelocatePreference:");
		var siteLocate = false;
		if (event.value == 2) {
			siteLocate = true;	
			this.controller.get('sitelocationOffMessage').hide();
			this.controller.get('sitelocationOnMessage').show();

		} else{
			this.controller.get('sitelocationOnMessage').hide();
			this.controller.get('sitelocationOffMessage').show();

		}
		LocationService.setWebSetting(
							siteLocate, 
							this.successSetWebSetting.bind(this,siteLocate), 
							this.errorSetWebSetting.bind(this), this.controller);
		
	},
	clearSiteLocatePreference:function(){
		
		this.controller.showDialog({template: 'main/siteClearPermission', assistant: new ClearDialogAssistant(this)});
	},
	disableBackGroundDataCollection: function() {
		this.controller.get('dataPrefs').hide();
	},

	enableBackGroundDataCollection: function() {
		// [Lunacy] "Background data collection" let Google gather location data through Palm's
		// Google location service, which is long gone; on Android, network location is Android's
		// own. The switch stays hidden.
		this.controller.get('dataPrefs').hide();
	},
	
	disableAutoLocate: function() {
		this.controller.get('locationOnMessage').hide();
		this.controller.get('locationOffMessage').show();
		this.controller.get('geotagPhotosPrefs').hide();
	},
	
	enableAutoLocate: function(isInit) {
		this.controller.get('locationOnMessage').show();
		this.controller.get('locationOffMessage').hide();
		if (isInit) {
			Mojo.Log.info("------------------- enableAutoLocate show");
			this.controller.get('geotagPhotosPrefs').show();
		} else {
			Mojo.Log.info("------------------- enableAutoLocate hide");
		  	this.controller.get('geotagPhotosPrefs').hide();
		}
	},
	
	handleGetGeotagPhotosPref: function(response) {
		this.readUseGeotagPhotos(response);
		this.controller.get('geotagPhotosPrefs').show();
	},
	
	successSetUseLocationServices: function (isEnabled,response) {
		Mojo.Log.info("55555555555555Success in setting location service preference %j", $H(response));
		if(!this.prefData.cameraAvailable) {
			Mojo.Log.info("------------------- enableAutoLocate hide");
		} else 	if (isEnabled) {
			LocationService.getUseGeotagPhotos(this.handleGetGeotagPhotosPref.bind(this), null, this.controller);
		}
	},
	successSetWebSetting:function(isEnabled,response){
		Mojo.Log.info("55555555555555Success in setting webSetting option %j", $H(response));
	},
	errorSetWebSetting:function(isEnabled,response){
		Mojo.Log.info("55555555555555Success in setting webSetting option %j", $H(response));
	},
	successClearWebSetting:function(response){
		Mojo.Log.info("55555555555555Success in clearing webSetting option %j", $H(response));
	},
	errorClearWebSetting:function(respone){
		Mojo.Log.info("55555555555555SFailure in clearing webSetting option %j", $H(response));
	},
	errorSetUseLocationServices: function (response) {
		Mojo.Log.info("Error in setting location service preference %j", $H(response));
	},
	
	
	setGeotagPhotosPreference: function (event) {
		Mojo.Log.info("------------------- Set geotag preference -----------------", event.value);
		var geotagPreference = false;
		if (event.value == "ON") {
			geotagPreference = true;
		}
		LocationService.setUseGeotagPhotos(
							geotagPreference,
							null, 
							null, this.controller);
	},	

	setDataPreference: function (event) {
		Mojo.Log.info("------------------- Set data collection preference -----------------", event.value);
		if (event.value == "ON") {
			this.dataModel.dataOption="ON";
			this.dataPreference = true;
		} else {
			this.dataModel.dataOption="OFF";
			this.dataPreference = false;
		}
		this.controller.modelChanged(this.dataModel);
		LocationService.setUseBackgroundDataCollection(
							this.dataPreference,
							this.successSetBackgroundDataCollection.bind(this), 
							this.errorSetBackgroundDataCollection.bind(this), this.controller);
	},
	
	successSetBackgroundDataCollection: function (response) {
		Mojo.Log.info("Success in setting location service preference %j", $H(response));
	},
	
	errorSetBackgroundDataCollection: function (response) {
		Mojo.Log.info("Error in setting location service preference %j", $H(response));
	}
	
});	

/*
	Small controller class used for the Clear dialog .
*/


var ClearDialogAssistant = Class.create({
	
	initialize: function(sceneAssistant,controller) {
		this.sceneAssistant = sceneAssistant;
	},
	
	setup : function(widget) {
		this.widget = widget;
	},
	
	activate: function() {
		this.sceneAssistant.controller.get('Clear').addEventListener(Mojo.Event.tap, this.clearTapHandler.bind(this));
    	this.sceneAssistant.controller.get('Cancel').addEventListener(Mojo.Event.tap,this.cancelTapHandler.bind(this));
	},
	
	deactivate: function() {
		this.sceneAssistant.controller.get('Cancel').removeEventListener(Mojo.Event.tap, this.cancelTapHandler.bind(this));
		this.sceneAssistant.controller.get('Clear').removeEventListener(Mojo.Event.tap, this.clearTapHandler.bind(this));
	},
	
	
	clearTapHandler:function(event){
		Mojo.Log.info("***** Clear");
	
		LocationService.clearWebSetting(
							this.sceneAssistant.successClearWebSetting.bind(this), 
							this.sceneAssistant.errorClearWebSetting.bind(this), this.sceneAssistant.controller);
		this.widget.mojo.close();
	},
	
	cancelTapHandler:function(event){
		Mojo.Log.info("***** Close");
		this.widget.mojo.close();
	}
});
