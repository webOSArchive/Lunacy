enyo.kind({
	name:"ScreenLockPrefApp",
	kind:"VFlexBox",
	components:[
		{kind:"Control", className:"enyo-toolbar-light header-welcome", components: [
			{kind: "Image", src: "images/header-icon-screen.png"},
			{name: "headerTitle", content: $L("Screen & Lock"), style: "padding-left: 10px;"}
		]},
		{className:"header-shadow"},
    	{kind:"Scroller", flex:1, components:[
    	                                      {kind:"Pane", flex:1, onSelectView: "processViewSelected", 
    	                                    	  components:[
                                                               {kind: "HFlexBox", flex:1, pack:"center", align:"center", 
                                                            	   components: [
                                                                               	{kind:"Spinner", name: "getPreferencesSpinner"},
                                                                               	{content: $L("Loading Preferences...")}
                                                                ]},
    		{kind:"Control",  name:"prefView", className:"box-center", components:[                               
				{kind: "RowGroup", style:"margin-top:24px", components: [
					{kind: "Item", tapHighlight: false, layoutKind: "HFlexLayout", components: [
						{flex:1, content: $L("Auto Dim")},
						{kind: "ToggleButton", name:"autodim", state:true, onChange:"toggleAutoDim"}
					]},
					{kind: "Item", layoutKind:"VFlexLayout", tapHighlight: false, components: [
					                                                   						{content: $L("Brightness")},
					                                                   						{kind: "Slider", name:"brightnessSlider", position: 0, minimum:10, maximum:100, onChange:"brightnessChange"}
					                                                   					]},
					{kind: "Item", layoutKind:"HFlexLayout", tapHighlight: false, 
					        components: [
					                     {flex:1, content:$L("Turn off After")},
					                     {kind: "ListSelector", value: 30, name:"displayTimer", onChange:"handleDisplayTimerChange", items: [
					                                            					{caption: $L('1 minute'), value:60},
					                                            					{caption: $L('2 minutes'), value:120},
					                                            					{caption: $L('5 minutes'), value:300},
					                                            					{caption: $L('10 minutes'), value:600}
					                                            				]}
					]}
	        	]},
	        	{kind: "RowGroup", caption:$L("Wallpaper"),
	        		components: [
	        		             {kind: "Item", tapHighlight: true, layoutKind: "HFlexLayout", onclick:"launchFilePicker", components: [{content: $L("Change Wallpaper")}]},
	        	]},
	        	{kind: "RowGroup", caption:$L("Advanced Gestures"), components: [
	        	                                        					{kind: "Item", tapHighlight: false, layoutKind: "HFlexLayout", components: [
	        	                                        						{flex:1, content: $L("Enable Gestures")},
	        	                                        						{kind: "ToggleButton", name:"enablegesture", state:true, onChange:"toggleEnableGesture"}
	        	                                        					]}
	        	]},
				{content: $L("Swipe up from the bottom of the screen to card an app. Swipe up again to see the Launcher."), className:"accounts-gestures-text"},
	        	{kind: "RowGroup", caption:$L("Secure Unlock"), className:"accounts-group", 
	        		components: [
	        		             {kind: "Item", name:"securityOptionsItem", tapHighlight:false, components:[
	        		             {kind: "ListSelector", name:"securityOptions", onChange:"changeSecurityOption", value: 'none', items: [
					                                               					{caption: $L('Off'), value:'none'},
					                                            					{caption: $L('Simple PIN'), value:'pin'},
					                                            					{caption: $L('Password'), value:'password'}
					                                            				]},
					             ]},
					             {kind: "Item", tapHighlight:true, name:"updateSecurityOption", showing:false, className:"enyo-middle", onclick:"updateSecuritySetting"},
					             {kind: "Item", layoutKind:"HFlexLayout", name:"lockTimerOptionsItem", showing:false, className:"enyo-last", tapHighlight: false, 
								        components: [
								                     {flex:1, content:$L("Lock After")},
								                     {kind: "ListSelector", name:"lockTimerOptions", onChange:"changeLockTimerOption", items: []}
								 ]},
								                     
	        	]},
				{kind: "RowGroup", caption:$L("Notifications"), components: [
					{kind: "Item", tapHighlight: false, layoutKind: "HFlexLayout", components: [
						{flex:1, content: $L("Show When Locked")},
						{kind: "ToggleButton", name:"locknotification", state:false, onChange:"toggleLockNotification"}
					]},
					{kind: "Item", tapHighlight: false, layoutKind: "HFlexLayout", components: [
					                                                    						{flex:1, content: $L("Blink Notifications")},
					                                                    						{kind: "ToggleButton", name:"blinknotification", state:false, onChange:"toggleBlinkNotification"}
					                                                    					]}
				]},
				{content: $L("The center button blinks when new notifications arrive."), className:"accounts-body-text"},
			]},
		]}
		]},
	    			{kind: "Dialog", lazy:false, components: [
						{name:"errorMsg"},
						{layoutKind: "HFlexLayout", pack: "center", components: [
							{kind: "Button", caption: $L("Close"), onclick: "closeDialog"}
						]}
				    ]},
					    {kind: "AppMenu", components: [
							{kind: "HelpMenu", target: "http://help.palm.com/screen/index.html"}
					  	]},
	   				 {kind:"PalmService", service:"palm://com.palm.systemservice/", 
					    	components:[
							{name:"getSystemPreferences", method:"getPreferences", onResponse:"handleGetSystemPref"},
							{name:"setSystemPreferences", method:"setPreferences", onResponse:"handleSetSystemPref"},
							{name:"getSystemLockoutTimer", method:"getPreferences", onResponse:"handleSystemlockTimeout"}
					    ]},
	    			{kind:"PalmService", service:"palm://com.palm.display/control/", 
						   	components:[
						   	            {name:"setProperty", method:"setProperty", onResponse:"handleSetProperty"},
						   	            {name:"getProperty", method:"getProperty", onResponse:"handleGetProperty"}
						   ]},
	    			{kind:"PalmService", service:"palm://com.palm.systemmanager/", 
				   	components:[
				   	            {name:"getSecurityPolicy", method:"getSecurityPolicy", onResponse:"handleSecurityPolicy"},
				   	            {name:"getDeviceLockMode", method:"getDeviceLockMode", onResponse:"handleGetDeviceLockMode"},
				   	            {name:"setDevicePasscode", method:"setDevicePasscode", onResponse:"handleSetDevicePasscode"}
   	            
				   ]},
					{kind:"PalmService", service:"palm://com.palm.systemservice/wallpaper/", name:"importWallpaper", method:"importWallpaper", onResponse:"handleImportWallpaper"},         	
						{name:'imagePicker', kind: "FilePicker", fileType:["image"], onPickFile: "selectedImageFile"},                  
	    				{kind:"SetPasswordDialog", lazy:false, onCancel:"handleSetPasswordCancel", onDone:"handleSetPasswordDone"},                 
	    				{kind:"PasswordUnlock", lazy:false, onCancelClick:"handleSetPasswordCancel", onPasswordVerified:"handleVerifyPasswordDone"},
	
	    {kind:"Popup", dismissWithClick:false, lazy:false, name: "secPops", width:"360px", height:"360px", className: "cust-enyo-popup",
	    	components: [
	    	             {kind: enyo.VFlexBox, width: "320px", height: "325px", components: [     
	{kind: "Pane", height: "100%", flex: 1, components:[
	{name:"pinUnlock", kind: "PinUnlock", onCancelClick: "handleSetPinCancel", onSetPinSuccess: "handleSetPinSuccess", onPinVerified:"handlePinVerified"},
								]}                                     
						]}
	    ]},
	    {kind: "AppMenu", components: [
	                       			{kind: "HelpMenu", target: "http://help.palm.com/screen/index.html"}
	                       	  	]},
	],
	availableLockTimers: [
	              		{caption: $L('Screen turns off'), value:0}, 
	              		{caption: $L('30 seconds'), value:30}, 
	              		{caption: $L('1 minute'), value:60},
	              		{caption: $L('2 minutes'), value:120},
	              		{caption: $L('3 minutes'), value:180},
	              		{caption: $L('5 minutes'), value:300},
	              		{caption: $L('10 minutes'), value:600},
	              		{caption: $L('30 minutes'), value:1800}
	              	],
	create: function() {
		this.inherited(arguments);
		this.$.getPreferencesSpinner.show();
		this.$.getSecurityPolicy.call();
		this.$.lockTimerOptions.setItems(this.availableLockTimers);
		
		this.currentSecurity = 'none';
		this.requiredLockMode == "password"
	},
	showPrefView: function() {
		//All prefs query done. Remove Spinner.
		this.$.pane.selectViewByName("prefView");
		setTimeout(enyo.bind(this.$.getPreferencesSpinner, "hide"), 1000);
	},
	handleGetSystemPref: function(inSender, inResponse) {
		
		if (inResponse.showAlertsWhenLocked != undefined) {
			this.$.locknotification.setState(inResponse.showAlertsWhenLocked);
		}
		
		if (inResponse.sysUiEnableNextPrevGestures != undefined) {
			this.$.enablegesture.setState(inResponse.sysUiEnableNextPrevGestures);
		}
		
		if (inResponse.enableALS != undefined) {
			this.$.autodim.setState(inResponse.enableALS);
		}
		
		if (inResponse.BlinkNotifications != undefined) {
			this.$.blinknotification.setState(inResponse.BlinkNotifications);
		}
		
		//Demo Device -- Hide the PIN / Password options.
		if(inResponse.onDeviceDemoRunning != undefined && inResponse.onDeviceDemoRunning === true) {
			this.$.securityOptions.setValue('none');
			this.$.securityOptions.setItems([{caption: $L('Off'), value:'none'}]);
		}
		setTimeout(enyo.bind(this, "showPrefView"),500);
	},
	handleGetProperty: function(inSender, inResponse) {
		this.$.displayTimer.setValue(inResponse.timeout);
		this.$.brightnessSlider.setPosition(inResponse.maximumBrightness);	
		this.$.getSystemPreferences.call({keys:["showAlertsWhenLocked","sysUiEnableNextPrevGestures","BlinkNotifications","onDeviceDemoRunning", "enableALS"]});
	},
	launchFilePicker: function(inSender, inEvent) {	
		this.$.imagePicker.pickFile();
	},
	toggleAutoDim: function(inSender, inEvent) {
		var state = inSender.getState();
		this.$.setSystemPreferences.call({enableALS:state});
	},
	toggleEnableGesture: function(inSender, inEvent) {
		var state = inSender.getState();
		this.$.setSystemPreferences.call({sysUiEnableNextPrevGestures:state});
	},
	toggleLockNotification: function(inSender, inEvent) {
		var state = inSender.getState();
		this.$.setSystemPreferences.call({showAlertsWhenLocked:state});
	},
	toggleBlinkNotification: function(inSender, inEvent) {
		var state = inSender.getState();
		this.$.setSystemPreferences.call({BlinkNotifications:state});
	},
	handleSetSystemPref:function(inSender, inResponse) {
		if(!inResponse.returnValue)
			this.showDialog($L("Unable to set preference"));
	},
	brightnessChange:function(inSender, inPosition) {
		this.$.setProperty.call({maximumBrightness:parseInt(inPosition)});
	},
	handleDisplayTimerChange: function(inSender, inValue) {
		this.$.setProperty.call({timeout:parseInt(inValue)});
	},
	handleImportWallpaper: function(inSender, inResponse) {
		if(inResponse.wallpaper)
			this.$.setSystemPreferences.call({"wallpaper":inResponse.wallpaper});	
	},
	selectedImageFile: function(inSender, response) {
		if(response && response.length == 0)
			return;
		var params = {"target": encodeURIComponent(response[0].fullPath)};
		
		/*var cropInfoWindow = response[0].cropInfo;
		
		if(cropInfoWindow) {
			if(cropInfoWindow.scale)
				params["scale"] = cropInfoWindow.scale;
			
			if(cropInfoWindow.focusX)
				params["focusX"] = cropInfoWindow.focusX;
			
			if(cropInfoWindow.focusY)
				params["focusY"] = cropInfoWindow.focusY;
		}*/			
		this.$.importWallpaper.call(params);
	},
	handleSecurityPolicy: function(inSender, inResponse) {
		
		if(inResponse.returnValue && inResponse.policy) {
			this.parseSecurityPolicy(inResponse.policy);
		}
		else {
			this.securityPolicy = false;
		}
		this.$.getSystemLockoutTimer.call({keys:["lockTimeout"]});
		this.$.getDeviceLockMode.call();
	},
	
	parseSecurityPolicy: function(policy) {
		
		//save the policy
		this.securityPolicyObj = policy;
		
		//Find out if the Lock Timer is set.
		if(policy.inactivityInSeconds !== undefined) {
			this.securityPolicyInactivityTimer = policy.inactivityInSeconds;
		}
		else {
			this.securityPolicyInactivityTimer = 0;
		}
		
		//Find out the password policies and get the password message.
		if(policy.password && policy.password.enabled) {
			this.securityPolicy = true;		
			//Remove the 'Off' from the list option, if the policy requires PIN or Password.
			if(policy.password.alphaNumeric === false) {
				this.requiredLockMode = "PinOrPass";
			//	this.$.securityOptions.setItems([{caption: $L('Simple PIN'), value:'pin'},
              //               					{caption: $L('Password'), value:'password'}]);
			}
			
			this.$.passwordUnlock.setSecurityPolicyState("active");
			this.$.setPasswordDialog.setPolicy(policy.password);
			this.$.pinUnlock.setSecurityPolicyState("active");
		}
		else {
			//Password not enforced.
			this.securityPolicy = false;
		}
		//Find out the Policy Status
		this.policyEnforced = policy.status.enforced;
	},
	
	handleSystemlockTimeout: function(inSender, inResponse) {

		if (inResponse.lockTimeout !== undefined) {
			this.currentLockTimer = inResponse.lockTimeout;

			if (this.securityPolicyInactivityTimer !== undefined && this.securityPolicy) {
				for (var i=0; i<this.availableLockTimers.length; i++) {
					if (this.availableLockTimers[i].value > this.securityPolicyInactivityTimer) {
						this.availableLockTimers.splice(i, this.availableLockTimers.length-i);
						break;
					}
				}
				//The last value in the list should be same as the policy inactivity timer value. If not, add it to the list.
				if(this.availableLockTimers[this.availableLockTimers.length-1].value !== this.securityPolicyInactivityTimer) {
					var obj = {};
					obj.caption = new enyo.g11n.Template($L('#{policyInactivityTimer} minutes')).evaluate({policyInactivityTimer: this.securityPolicyInactivityTimer/60});
					obj.value = this.securityPolicyInactivityTimer;
					this.availableLockTimers.push(obj);
				}	
			}
			this.$.lockTimerOptions.setItems(this.availableLockTimers);
			this.$.lockTimerOptions.setValue(this.currentLockTimer);
		}
	},	
	handleGetDeviceLockMode: function(inSender, inResponse) {
		this.currentSecurity = inResponse.lockMode || 'none';
		this.showModifyRow();
		this.revertChanges();
		this.$.getProperty.call({properties:['timeout','maximumBrightness']});
	},
	showModifyRow: function() {
		if (this.currentSecurity == 'pin' || this.currentSecurity == 'password') {

			if (this.currentSecurity == 'pin') {
				this.$.updateSecurityOption.setContent($L("Change PIN"));
			}
			else {
				this.$.updateSecurityOption.setContent($L("Change Password"));
			}
		
			if(this.securityPolicy) {
				if(!this.policyEnforced) {
					if (this.requiredLockMode == "password") {
						this.$.securityOptions.setItems([{caption: $L('Simple PIN'), value:'pin'},
			                             					{caption: $L('Password'), value:'password'}]);
						if(this.currentSecurity == 'pin')
							this.$.updateSecurityOption.setShowing(false);
					}
				}
				else {
					if(this.requiredLockMode == "PinOrPass") {
						this.$.securityOptions.setItems([{caption: $L('Simple PIN'), value:'pin'},
			                             					{caption: $L('Password'), value:'password'}]);
						
					}
					if (this.securityPolicyObj.password.alphaNumeric) {
						this.$.securityOptions.setShowing(false);
					}
				}
			}
			this.$.securityOptionsItem.removeClass("enyo-single");
			this.$.securityOptionsItem.addClass("enyo-first");
			this.$.updateSecurityOption.setShowing(true);
			this.$.lockTimerOptionsItem.setShowing(true);
		}
		else {
			this.$.updateSecurityOption.setShowing(false);
			this.$.lockTimerOptionsItem.setShowing(false);
			this.$.securityOptionsItem.removeClass("enyo-first");
			this.$.securityOptionsItem.addClass("enyo-single");
			if(this.securityPolicy) {
				if(this.requiredLockMode == "password") {
					this.$.securityOptions.setItems([{caption: $L('Off'), value:'none'},,
		                             					{caption: $L('Password'), value:'password'}]);
				}
			}
		}	
		this.$.securityOptions.setValue(this.currentSecurity);
	},
	revertChanges: function() {
		this.$.securityOptions.setValue(this.currentSecurity);
	},
	changeSecurityOption: function(inSender, inValue) {
		this.newSecurity = inValue;	
		
		if(this.currentSecurity === 'none') {
			this.changeSecureLock(this.newSecurity);
		}
		else if(this.currentSecurity === 'pin') {
			this.$.pinUnlock.setPinSet(false);
			this.$.secPops.openAtCenter();
		}
		else if(this.currentSecurity === 'password') {			
			this.$.passwordUnlock.openAtCenter();
		}		
	},
	changeSecureLock: function(newSetting) {
		if(newSetting == 'pin') {
			this.$.pinUnlock.setPinSet(true);
			this.$.secPops.openAtCenter();			
		}
		else if (newSetting == 'password') {
			this.$.setPasswordDialog.openAtCenter();
		}
		else {
			this.currentSecurity = 'none';
			this.$.setDevicePasscode.call({passCode:'', lockMode: 'none'});
			this.showModifyRow();
		}	
	},
	updateSecuritySetting: function(inSender, inEvent) {
		if (this.currentSecurity == 'pin') {
			this.$.pinUnlock.setPinSet(false);
			this.$.secPops.openAtCenter();
			this.newSecurity = "pin";
		}
		else if(this.currentSecurity == 'password') {
			this.$.passwordUnlock.openAtCenter();
			this.newSecurity = "password";
		}
	},
	changeLockTimerOption: function(inSender, inValue) {
		this.$.setSystemPreferences.call({lockTimeout:inValue});
	},
	handleSetPinCancel: function() {
		this.$.secPops.close();
		this.revertChanges();
	},
	handleSetPinSuccess: function() {
		this.currentSecurity = 'pin';
		this.$.secPops.close();
		this.showModifyRow();
	},
	handlePinVerified: function() {
		this.$.secPops.close();
		this.changeSecureLock(this.newSecurity);
	},
	handleSetPasswordCancel: function() {
		//revert security options
		this.$.passwordUnlock.close();
		this.revertChanges();
	},
	handleSetPasswordDone: function() {
		this.$.setPasswordDialog.close();
		this.currentSecurity = 'password';
		this.showModifyRow();
	},
	handleVerifyPasswordDone:function(inSender, inEvent) {
		this.$.passwordUnlock.close();
		this.changeSecureLock(this.newSecurity);
	},
	cancelPin: function() {
		//revert security options
		this.$.secPops.close();
	},
	showDialog: function(errMsg) {
		this.$.errorMsg.setContent(errMsg);
		this.$.dialog.open();
	},
	closeDialog: function(inSender, inEvent) {
		this.$.dialog.close();
	}
});
