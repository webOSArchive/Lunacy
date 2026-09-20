enyo.kind({
	name: "appMuseum.MuseumApp",
	kind: "Control",
	className: "enyo-bg",
	layoutKind: "VFlexLayout",
	_position: {},
	_touchpad: banneret.getGlobal("isTouchpad"),
	components: [
		{kind: "Helpers.Updater", name: "SelfUpdater" },
		{
			name: "ConfigService",
			kind: "WebService",
            url: banneret.getPrefs("detailLocation")+"/getConfig.php",
            method: "GET",
            handleAs: "json",
            onSuccess: "GetConfigSuccessFn",
			onFailure: "GetConfigFailureFn"
		},
		{
			name: "MasterWebService",
			kind: "WebService",
            url: banneret.getPrefs("detailLocation")+"/"+banneret.getPrefs("masterPage")+banneret.getPrefs("backendVersion")+".php",
            method: "POST",
            handleAs: "json",
            onSuccess: "MasterWebServiceSuccessFn",
            onFailure: "MasterWebServiceFailureFn"
		},
		{
			kind: "ApplicationEvents",
			onBack: "selectPreviousView"
		},
/*		{
			kind: "ApplicationEvents",
			onKeypress: "showSearchBar"
		},*/
		{
			kind: "appMuseum.AppMenu",
			name: "appMenu",
			onItemSelected: 'handleItemSelected'
		},
		{
			layoutKind: "VFlexLayout",
			flex: 1,
			components: [
				{
					layoutKind: "HFlexLayout",
					className: "museumHeader",
/*					components: [
						{
							height: "48px",
							width:  "48px",
							style: "position: absolute; z-index: 2;",
							onclick: "handleOpenMenu"
						}
					]*/
				},
				{
					kind: "appMuseum.PreferencesPopup",
					onRequestRerenderList: "rerenderAppListP"
				},
				{
					kind: "appMuseum.AboutPopup"
				},
				{kind: "Toaster", name: "devModeToaster", flyInFrom: "bottom", onclick: "confirmClick", className: "enyo-popup smallToaster", components: [
			        {layoutKind: "VFlexLayout", pack: "center", components: [
			        	{
			        		layoutKind: "HFlexLayout", pack: "center", components: [
			        			{width: "16px"},
			        			{kind: "Image", src: "./images/devMode.png"},
			        			{width: "16px"},
	        					{content: "DevMode", className: "enyo-label"},			        			
			        			{width: "16px"}
			        		]
			        	},
	        			{layoutKind: "VFlexLayout", components: [
	        				{content: "NOTE: these features are experimental and not fully tested! - So be cautious. There are also no guarantees they will ever see the light of day. And while they are persistant and will continue to be active (their settings are stored), to toggle them off again you will need to start DevMode again.", className: "appFooter appFooterNote"}
	        			]},
			            {kind: "Button", caption: "OK", onclick: "confirmClick"},
			        ]}
			    ]},
				{
					kind: "SlidingPane",
					name: "slidingPane",
					flex: 1,
					components: [
						{
							width: "240px",
							name: "catSliding",
							dragAnywhere: false,
							fixedWidth: true,
							components: [
								{
									name: "categories",
									kind: "appMuseum.Categories",
									flex: 1,
									onCategorySelected: "handleCategorySelected"
								}
							]
						},
						{
							width: "320px",
							name: "appSliding",
							dragAnywhere: false,
							fixedWidth: true,
							layoutKind: "VFlexLayout",
							peekWidth: 60,
							components: [
								{
									name: "appPane",
									kind: "Pane",
									flex: 1,
									components: [
										{
											kind: "appMuseum.AppList",
											className: "appListBackground",
											onAppSelected: "handleAppSelected",
											onApplistChanged: "handleApplistChanged",
											onCallSearchQuery: "callSearchQuery",
										},
										{
											name: "companyAppList",
											className: "appListBackground",
											kind: "appMuseum.CompanyAppList",
											onAppSelected: "handleAppSelected",
											onCloseCompanyPane: "closeCompanyPane",
										}
									]
								},
								{
									kind: "Toolbar",
									style: "z-index: 1;",
									className:"enyo-toolbar-light",
									name: "appToolbar",
									components: [
										{
											slidingHandler: true,
											kind: "GrabButton"
										},
										{
											kind: "HFlexBox",
											flex: 1,
											style: "margin-left: 48px;",
											components: [
												{
													kind: "ListSelector",
													className: "appFooterSelectorOnLight",
													name: "selectFirstLetter",
													onChange: "firstLetterSelected"
												},
												{
													kind: "Spacer",
													flex: 1
												},
												{
													// App filter selector for touchpad only/phone only/all feature
													kind: "ListSelector",
													name: "deviceFilterSelector",
													onChange: "deviceFilterChanged",
													className: "appFooterSelectorOnLight",
													items: [
														{caption: $L("All"), value: "All"},
														{caption: $L("LuneOS"), value: "LuneOS"},
														{caption: $L("TouchPad Exclusive"), value: "touchpad_exclusive"},
														{caption: $L("TouchPad"), value: "TouchPad"},
														{caption: $L("HP Pre3"), value: "Pre3"},
														{caption: $L("HP Veer"), value: "Veer"},
														{caption: $L("Palm Pre 2"), value: "Pre 2"},
														{caption: $L("Palm Pre"), value: "Pre"},
														{caption: $L("Palm Pixi"), value: "Pixi"},
													]
												}
											]
										}
									]
								}
							]
						},
						{
							name: "detailSliding",
							dragAnywhere: false,
							onResize: "resizeDetails",
							peekWidth: 144,
							components: [
								{
									kind: "appMuseum.Details",
									flex: 1,
									onShowMoreByCompany: "showCompanyApps",
									onShowImages: "handleShowImages",
									onRequestRerenderList: "rerenderAppListD"
								},
								{
									kind: "Toolbar",
									style: "z-index:1;",
									name: "detailToolbar",
									className: "enyo-toolbar-light",
									components: [
										{
											slidingHandler: true,
											kind: "GrabButton"
										},
										{
											kind: "Spacer"
										},
                                        {
                                        	kind: "IconButton",
                                        	name: "randomButton",
                                        	style: "background-color: rgb(62,11,249); color: white;",
                                        	icon: "images/randomCard.png",
                                        	onclick: "handleRandomButton"
                                        }
									]
								}

							]
						}
					]
				}
			]
		},
		{
			name:"screenshotsArea",
			style: "position: fixed; left: 0; top: 0; right: 0; bottom: 0;",
			showing: false,
			components: [
				{
					name: "screenshots",
					kind: "ImageView",
					showing: false,
					onclick: "closeScreenshots",
					className: "screenshotCarousel",
					imageLoaded: function (inEvent) {
						this.owner.screenshotLoaded(inEvent);
					}
				}
			]
		},
		{
			kind: "SpinnerLarge", name: "screenshotSpinner", showing: false, className: "screenshotSpinner"
		}
	],


	/** general **/
	create: function (lauchParams) {
		// NOTE: in the future we want to use the launchParams to go directly to
		//       a specific application; this should 'overwrite' the launch of the
		//       (defunct) App Catalog

		this.inherited(arguments);

		this.$.SelfUpdater.CheckForUpdate(this, "app museum 2", this.updateResponseCallBack);

		document.addEventListener("keydown", this.showSearchBar);

		this.setPeekWidth();
		this.$.ConfigService.call();

		if (this._touchpad) {
			this.addClass("touchpad");
		} else {
			this.addClass("");
		}
	},
	updateResponseCallBack: function(self, message) {
		//Included as an example of controlling the update flow, but usually its safe just to let the Update helper to handle everything
		enyo.log("Got an updater response: " + message);
		self.$.SelfUpdater.PromptUserForUpdate(message);
	},
	rendered: function () {
		this.inherited(arguments);
		if (banneret.getPrefs('firstUse') !== false) {
			this.$.aboutPopup.openAtCenter();
		}
	},
	setPeekWidth: function () {
		var peekA, peekB;
		peekA = banneret.getPrefs().catIconSize ? 60 : 44;
		peekB = banneret.getPrefs().appIconSize ? peekA + 84 : peekA + 64;

    	this.$.appSliding.setPeekWidth(peekA);
    	//this.$.detailSliding.setPeekWidth(peekB);
	},
	handleOpenMenu: function () {
		this.$.appMenu.open();
	},
	GetConfigSuccessFn: function (inSender, inData, inResponse) {
		enyo.log("Got config response");
		if (inData) {
			enyo.log("Using config response");
			if (inData.package_host) {
				var newPackageHost = inData.package_host;
				if (newPackageHost.indexOf("://") != -1) {
					newPackageHost = newPackageHost.replace("ftp://", "");
					newPackageHost = newPackageHost.replace("http://", "");
					newPackageHost = newPackageHost.replace("https://", "");
				}	
				if (banneret.getPrefs().serviceArchiveLocation != newPackageHost) {
					enyo.warn("Got new configuration setting from server, using package_host: " + newPackageHost);
					banneret.setPref("serviceArchiveLocation", newPackageHost);
					banneret.setPref("archiveLocation", newPackageHost);
				}
			}
			if (inData.image_host) {
				var newImageHost = inData.image_host;
				if (newImageHost.indexOf("://") == -1)
					newImageHost = "http://" + newImageHost;
				if (banneret.getPrefs().serviceBaseImageURL != newImageHost) {
					enyo.warn("Got new configuration setting from server, over-writing prefs and using image_host: " + newImageHost);
					banneret.setPref("serviceBaseImageURL", newImageHost);
					banneret.setPref("baseImageURL", newImageHost);
				}
			}
        }
		else {
			enyo.warn("Failure parsing Configuration from server, defaults wil be used.");
		}
		this.handlePrefsChange();
	},
	GetConfigFailureFn: function() {
		enyo.warn("Faiure fetching Configuration from server, defaults will be used.");
		this.handlePrefsChange();
	},
	MasterWebServiceSuccessFn: function (inSender, inData, inResponse) {
        if (inResponse === "end call") {
        	var databar = this.$.appList.$.receivingDataBar;
            databar.addClass("allDataReceived");
            setTimeout(function () {
                if(databar.hasClass("allDataReceived")) {
                    // if all data is still received, we can make the bar non-showing.
                    // this also helps the spinner from not being rendered anymore than needed.
                    databar.setShowing(this.$.appList.$.appSearch.getValue() > 0);    // we will punt whenever we're dealing with a query.
                }
            }.bind(this), 300);
            return;
        }
		if (inData && inData.appCount) {
			inData.appCount.Favourites = Object.keys(banneret.getFavourites()).length;
			this.$.categories.setCategoryCount(inData.appCount);
		}
		banneret.getMuseumList.successFn(inSender, inData, inResponse);
	},
	MasterWebServiceFailureFn: function (inSender, inError, inResponse) {
		var appListObj = this.$.appList;
        	appListObj.$.receivingDataBar.addClass("allDataReceived");
        setTimeout(function () {
            if(appListObj.$.receivingDataBar.hasClass("allDataReceived")) {
                // if all data is still received, we can make the bar non-showing.
                // this also helps the spinner from not being rendered anymore than needed.
                appListObj.$.receivingDataBar.setShowing(false);
            }
        }.bind(this), 300);
		banneret.getMuseumList.failureFn(inSender, inError, inResponse);
	},
	selectNextView: function () {
		var pane    = this.$.slidingPane;
		var viewIdx = pane.getViewIndex();
		if (viewIdx < pane.views.length - 1) {
			viewIdx = viewIdx + 1;
		} else {
			return;	// we've selected the last available view.
		}
		pane.selectViewByIndex(viewIdx);
	},
	selectPreviousView: function (inSender, inEvent) {
		var pane    = this.$.slidingPane;
		var viewIdx = pane.getViewIndex();
		if (viewIdx > 0 || this.$.preferencesPopup.getShowing() || this.$.aboutPopup.getShowing() || this.$.screenshots.getShowing()) {
			inEvent.preventDefault();
			inEvent.stopPropagation();	// back gestures are handled 'manually' in-app not automatically.
		}
		if (this.$.preferencesPopup.getShowing() || this.$.aboutPopup.getShowing()) {
			this.$.preferencesPopup.closePopup();
			this.$.aboutPopup.close();
		} else {
			if (this.$.screenshots.getShowing()) {
				this.closeScreenshots();
			} else {
				if (viewIdx  === 0) {
					// we will go into card view.
					// (the below line isn't needed since the 'return' prevents the preventDefault and stopPropagation code)
					// enyo.windows.deactivate(enyo.windows.getActiveWindow());
					return;
				} else {
					viewIdx = viewIdx - 1;
				}
				pane.selectViewByIndex(viewIdx);
			}
		}
	},
	confirmClick: function () {
		this.$.devModeToaster.close();
	},

	/** app menu **/
	handleItemSelected: function (inSender, inEvent) {
		switch (inEvent) {
			case 'Preferences':
				this.$.preferencesPopup.showPrefs();
				break;
			case "About the App Museum":
				this.$.aboutPopup.openAtCenter();
				break;
		}
	},
	/** preferences **/
	rerenderAppListP: function () {
		this.$.appList.calcPageSize();
		this.$.appList.refresh();
	},

	/** categories **/
	handleCategorySelected: function (inSender, inEvent) {
		/*var firstPosition = Object.keys(this._positions)[0];
		this.$.selectFirstLetter.setValue(firstPosition);
		this.$.appList.listOffset = 0;*/
		this.$.appPane.selectViewByIndex(0);
		if (!this._touchpad) {
			this.selectNextView();
			this.$.slidingPane.reallySelectView(this.$.appSliding)
		}
		//this.$.appList.setQuery("");

		this.$.appList.refresh(true);
		var label = banneret.global.categories[inEvent].label;
		this.$.appList.setCategory(label);
		this.handleRandomButton();
	},

	/** app list **/
	firstLetterSelected: function (inSender, inEvent) {
		var topItemIdx = this._positions[inEvent];
		this.$.appList._forcePunt = true;
		this.$.appList.setListOffset(topItemIdx);
	},
	deviceFilterChanged : function (inSender, inEvent) {
		this.handleRandomButton();
	},
	handleAppSelected: function (inSender, inEvent) {
		if (!this._touchpad) {
			this.selectNextView();
			this.$.slidingPane.reallySelectView(this.$.appSliding)
		}
		this.$.details.setAppId(inEvent);
	},

	/** company app list **/
	closeCompanyPane: function() {
		this.$.appPane.selectViewByIndex(0);
		this.$.deviceFilterSelector.setDisabled(false);
		this.$.selectFirstLetter.setDisabled(false);
	},
	showSearchBar: function (inEvent) {
		var appList = enyo.$.museumApp.$.appList;
		appList.showSearchBar(inEvent);
        if (inEvent.keyCode === 13) {
            // enter pressed in the searchbar.
            appList._filterApps(undefined, inEvent);
        }
		// NOTE: can't use 'this' here because it's called from the DOM's eventhandler.
		//       the true Enyo-approach, however, didn't work on the Pre3.
	},

	/** detail screen **/
	showCompanyApps: function (inSender, inEvent) {
		this.$.companyAppList.setSelectedCompany(inEvent);
		this.$.appPane.selectViewByIndex(1);
		this.$.deviceFilterSelector.setDisabled(true);
		this.$.selectFirstLetter.setDisabled(true);
	},
	rerenderAppListD: function (inSender, inEvent) {
		this.$.appList.refresh();
	},
	handleShowImages: function (inSender, imageList) {
		var keys = Object.keys(imageList);
		var imageArr = keys.map(function (im, idx, arr) {
            var bURL = banneret.getPrefs("baseImageURL") + "/";
            if (imageList[im].screenshot.toLowerCase().indexOf("://") != -1) {
                bURL = "";
            }
			return bURL + imageList[im].screenshot;
		});
		this.$.screenshots.setImages(imageArr);
		this.$.screenshots.setScrollLeft(0);
		enyo.setFullScreen(true);
		this.$.screenshots.setShowing(true);
		this.$.screenshotSpinner.setShowing(true);
		this.$.screenshotsArea.setShowing(true);
	},
	/** screenshots **/
	screenshotLoaded: function (inEvent) {
		this.loadedScreenshots = this.loadedScreenshots || 0;
		this.loadedScreenshots = this.loadedScreenshots + 1;
		if (this.$.screenshots.images.length === this.loadedScreenshots) {
			this.$.screenshotSpinner.setShowing(false);
		}
	},
	closeScreenshots: function () {
		this.loadedScreenshots = 0;
		enyo.setFullScreen(false);
		this.$.screenshotSpinner.setShowing(false);
		this.$.screenshots.setShowing(false);
		this.$.screenshotsArea.setShowing(false);
	},
	handleRandomButton: function () {
		var appListObj = this.$.appList;
        if (!appListObj.calcPageSize.calculated) {
            appListObj.calcPageSize();
        }
        var callObj = {
            count: appListObj.$.appList.pageSize,
            device: this.$.deviceFilterSelector.getValue(),
            category: appListObj.getCategory(),
            query: appListObj.$.appSearch.getValue()
        }
        if (banneret.getPrefs("showRandomApp") || arguments.length > 0) {
        	callObj.appIds = "random";
        } else {
        	callObj.page = 0;        	
        }

	    banneret.getMuseumList(
	    	callObj,
	        function (inData) {
	        	try {
	        		appListObj.setListLength(inData.appCount[appListObj.getCategory()]);
            	} catch(e) {
		        	try {
		        		appListObj.setListLength(inData.extraData.listCount);
		        	} catch(e) {
		        		appListObj.setListLength(Infinity);
		        	}
            	}
	            var appList = []; //appListObj.getAppList();
	            inData.indices.forEach(function (idxs, idx, arr) {
	                var listPos = inData.return_indices[idx];
	                appList[listPos] = inData.data[idx] !== null ? inData.data[idx] : banneret.getAppByIndex(idxs);
	            });
	            var firstLetterSelector = enyo.$.museumApp.$.selectFirstLetter;
	            	firstLetterSelector.setItems(Object.keys(inData['first_position']));
	            enyo.$.museumApp._positions = inData['first_position']
	            if (inData.extraData && inData.extraData.randomOffset) {
	            	appListObj.listOffset = inData.extraData.randomOffset;	            	
	            } else {
	            	appListObj.listOffset = inData.return_indices[0];
	            }

	            appListObj.appList = appList;
        		//appListObj._forcePunt = true;
	            appListObj.appListUpdated(inData);

	            var selectedFirstLetter = "";
	            var firstLetter = Object.keys(inData['first_position']);
	            	firstLetter.some(function(letter, idx, arr) {
	            		if (inData['first_position'][letter] < inData.return_indices[0]) {
							selectedFirstLetter = letter;
	            		} else {
							return true;
	            		}
	            	});
                if (firstLetterSelector.value !== selectedFirstLetter) {
                    firstLetterSelector.setValue(selectedFirstLetter);
                }

	            this.$.details.showAppDetails(appList[inData.return_indices[0]]);

	            appListObj.$.receivingDataBar.addClass("allDataReceived");
	            setTimeout(function () {
	                if(appListObj.$.receivingDataBar.hasClass("allDataReceived")) {
	                    // if all data is still received, we can make the bar non-showing.
	                    // this also helps the spinner from not being rendered anymore than needed.
	                    appListObj.$.receivingDataBar.setShowing(false);
	                }
	            }.bind(this), 300);
	        }.bind(this),
	        function (inError) {

	        }.bind(this)
        );
	},
	handlePrefsChange: function () {
		this.$.randomButton.setShowing(banneret.getPrefs("showRandomAppButton"));
	},
	callSearchQuery: function () {
		this.handleRandomButton();
	}
})