	/*globals PalmSystem, enyo, $L, event, Utilities, window */
	enyo.kind({
	name: "MusicAppLauncher",
	kind: "enyo.Object",
	faces: {
		 musicplayer:
		 {
			  windowName: "com.palm.app.musicplayer",
			  path: "main.html",
			  state: "unknown"
		 }
	},
	
	/**
	 * This method starts the app.
	 */
	startup: function()
	{
		this.log();
		var paramString = window.PalmSystem && PalmSystem.launchParams || "{}";
		this.log("paramString: ", paramString);
		this.startParams = JSON.parse(paramString);
		this.log("this.startParams: ", this.startParams);
		this.activateApp(this.appSelect());
	},
	
	/**
	 * This is the callback handler responding to the enyo's application relaunch event.
	 */
	applicationRelaunchHandler: function (relaunchParams)
	{
		this.log();
		this.log("relaunchParams:", relaunchParams);
		this.startParams = relaunchParams;
		this.log("this.startParams: ", this.startParams);
		this.activateApp(this.appSelect());
	},
	
	/**
	 * It determines a colleciton of the parameters required to activate a window by examining
	 * the environment.
	 *
	 * @retrun It returns an object containing a collection of the parameters required to activate
	 *         a window.
	 */
	appSelect: function ()
	{
	  this.log();
		 var launchParams = null, app = this.faces.musicplayer;
	
		 return app;
	},
	
	/**
	 * Activate a window identified by name.  If the identified window already existed, then the enyo
	 * window manager will bring it to focus, otherwise it will be created.
	 *
	 * @param app is an object containing the relevant parameters to activate a window.  This app
	 *            is one of the parameters objects defined by the faces property of this kind.
	 */
	activateApp: function (app)
	{
		 this.log();
		 if (!app) { return; }
		 if (!window.PalmSystem)
		 {           // (desktop only): add this extra artifact so that
			  window.name = app.windowName;   //          enyo.windows.browserAgent will not hide the window
		 }                                   //          hosting our app  
	  var allwindows = enyo.windows.getWindows();
	  for (var wins in allwindows)
	  {
		  if(typeof(wins) !== "undefined"){
			  this.log("Windows Before Activation, NAME: ", wins);
		  }
	  }
	  var path = enyo.fetchAppRootPath() + app.path;
	  
	  this.log("PATH IS ", path);
	  
		this.log("this.startParams: ", this.startParams);
	  enyo.windows.activate(app.path,app.windowName,this.startParams);
	  
	  allwindows = enyo.windows.getWindows();
	  for (var win in allwindows)
	  {
		  if(typeof(win) !== "undefined")
		  {
			  this.log("Windows After Activation, NAME: ", win);
		  }
	  }
	},
	
	/**
	 * A debugging helper method.
	 *
	 * @return It returns a debugging message.
	 */
	verboseAppContext: function ()
	{
		 var desc = "no PalmSystem";
		 if (window.PalmSystem)
		 {
			  if (window.PalmSystem.launchParams)
			  {
					// available only at relaunch
					// expect { windowType: "dockModeWindow", dockMode: true }
					desc = "PalmSystem.launchParams = "+window.PalmSystem.launchParams;
			  } else {
					desc = "no PalmSystem.launchParams";
			  }
		 }
		 return desc;
	}
	});
