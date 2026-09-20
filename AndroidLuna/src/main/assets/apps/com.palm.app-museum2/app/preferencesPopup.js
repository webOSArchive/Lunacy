enyo.kind({
    name: "appMuseum.PreferencesPopup",
    className: "enyo-popup",
    kind: "Toaster",
    modal: "true",
    flyInFrom: "bottom",
    width: "100%",
    height: "100%",
    layoutKind: "VFlexLayout",
    published: {
        
    },
    events: {
        onRequestRerenderList: ""
    },
    components: [
        {
            kind: "Scroller",
            name: "settingsArea",
            autoHorizontal: false,
            horizontal: false,
            vertical: false,
            autoVertical: true,
            width: "100%",
            flex: 1,
            components: [
                {
                    allowHTML: true,
                    content: "Settings marked with<span class='resetAppIcon'></span>will require a restart of the appliction. The webOS App Museum will restart automatically when the preferences-tab closes.",
                    className: "appFooter appFooterNote"
                },
                {
                    kind: "RowGroup",
                    name: "backendSourceSettingsGroup",
                    caption: "Backend Source Settings",
                    components: [
                        {
                            layoutKind: "HFlexLayout",
                            components: [
                                {
                                    content: "Backend Source",
                                    className: "enyo-label",
                                },
                                {name: "resetIconBS",className: "resetAppIcon"},
                            ]
                        },
                        {
                            kind: "RadioGroup",
                            value: 0,
                            name: "backendSourceSelector",
                            onChange: "backendSourceSelected",
                            components: [
                                {label: "Release"},
                                {label: "Beta"},
                                {label: "Alpha"},
                            ]
                        },
                        {
                            content: "You like living on the edge, or simply prefer apps not working as they should? - You could try our beta (or even alpha) data-sources. These may change and break daily without further notice, as they are development feeds. Beta means it's currently in test-phase, while Alpha is really purely for development.",
                            className: "appFooter",
                            name: "backendSourceNote"
                        },
                        {
                            content: "<b>ADVICE:</b> Don't touch these unless you're me...or are part of the test-team.",
                            allowHTML: true,
                            className: "appFooter",
                            name: "backendSourceNote2"
                        }
                    ]
                },
                {
                    kind: "RowGroup",
                    caption: "General Settings",
                    components: [
                        {
                            layoutKind: "VFlexLayout",
                            components: [
                                {
                                    layoutKind: "HFlexLayout",
                                    components: [
                                        {
                                            content: "Reset to Factory Settings",
                                            className: "enyo-label",
                                        },
                                        {name: "resetIconR",className: "resetAppIcon"},
                                    ]
                                },
                                {
                                    layoutKind: "HFlexLayout",
                                    components: [
                                        {content: "NOTE: This will not affect favourites.", flex:1},
                                        {kind: "Button", caption: "Reset", style:"height: 24px;", onclick: "resetPreferences"},
                                    ]
                                },
                            ]
                        },
                        {
                            layoutKind: "VFlexLayout",
                            components: [
                                {
                                 content: "Category Icon Size",
                                 className: "enyo-label",
                                },
                                {kind: "RadioGroup",
                                 name: "categoryIconSizeSelector",
                                 style: "margin: 4px;",
                                 components: [
                                    {label: "32px"},
                                    {label: "48px"}
                                 ],
                                 onChange: "selectHandler"
                                }
                            ]
                        },
                        {
                            layoutKind: "VFlexLayout",
                            components: [
                                {
                                 content: "App Icon Size",
                                 className: "enyo-label",
                                },
                                {kind: "RadioGroup",
                                 name: "appIconSizeSelector",
                                 style: "margin: 4px;",
                                 components: [
                                    {label: "48px"},
                                    {label: "64px"}
                                 ],
                                 onChange: "selectHandler"
                                }
                            ]
                        },
                        {
                            kind: "HFlexBox",
                            align: "center",
                            tapHighlight: false,
                            components: [
                                {
                                    content: "Show original price",
                                    className: "enyo-label",
                                    flex: 1
                                },
                                {
                                    kind: "ToggleButton",
                                    name: "showPriceToggle",
                                    state: false,
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "togglePrice"
                                },
                            ]
                        },
                        
                        {
                            kind: "HFlexBox",
                            align: "center",
                            tapHighlight: false,
                            components: [
                                {
                                    layoutKind: "HFlexLayout",
                                    flex:1,
                                    components: [
                                        {
                                            content: "Show Adult Apps",
                                            className: "enyo-label",
                                        },
                                        {name: "resetIconBS",className: "resetAppIcon"},
                                    ]
                                },
                                {
                                    kind: "ToggleButton",
                                    name: "showAdultToggle",
                                    state: false,
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "toggleAdult"
                                },
                            ]
                        },
                        {
                            content: "The App Catalog never allowed actual adult content, but did include a number of suggestive apps that are filtered out by default (they're sexist, and don't work any more anyway.)",
                            className: "appFooter"
                        },
                        {
                            kind: "HFlexBox",
                            align: "center",
                            name: "randomAppButtonArea",
                            tapHighlight: false,
                            components: [
                                {
                                    content: "Show random app button",
                                    className: "enyo-label",
                                    flex: 1
                                },
                                {
                                    kind: "ToggleButton",
                                    name: "showRandomAppButtonToggle",
                                    state: false,
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "showRandomAppButton"
                                }
                            ]
                        },
                        {
                            content: "Add a button to the footer of the detail page to go to a random app in the museum.",
                            className: "appFooter",
                            name: "randomAppButtonNote"
                        },
                        {
                            kind: "HFlexBox",
                            align: "center",
                            name: "randomAppArea",
                            tapHighlight: false,
                            components: [
                                {
                                    content: "Show random app",
                                    className: "enyo-label",
                                    flex: 1
                                },
                                {
                                    kind: "ToggleButton",
                                    name: "showRandomAppToggle",
                                    state: false,
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "showRandomApp"
                                }
                            ]
                        },
                        {
                            content: "Show a random app whenever the museum opens or when a new category is selected (when turned off you will always open the first item in the list).",
                            className: "appFooter",
                            name: "randomAppNote"
                        }
                    ]
                },
                {
                    height: "8px"
                },
                {
                    kind: "RowGroup",
                    caption: "Archive Location",
                    name: "archiveDetails",
                    components: [
                        {
                            layoutKind: "HFlexLayout",
                            name: "archiveHeader",
                            align: "center",
                            tapHighlight: false,
                            components: [
                                {
                                    kind: "HFlexBox",
                                    align: "center",
                                    components: [
                                        {
                                            kind: "CheckBox",
                                            name: "archiveUseCheckbox",
                                            checked: false,
                                            style: "margin-right: 16px;",
                                            onChange: "handleArchiveUse"
                                        },
                                        {
                                            content: "Use external archive",
                                            className: "enyo-label"
                                        }
                                    ]
                                },
                                {
                                    kind: "Control",
                                    layoutKind: "HFlexLayout",
                                    name: "archiveToggleArea",
                                    flex: 1,
                                    components: [
                                        {
                                            kind: "Spacer",
                                            style: "height: 8px;"
                                        },
                                        {
                                            kind: "ToggleButton",
                                            name: "archiveFTPToggle",
                                            state: "true",
                                            onLabel: "FTP",
                                            offLabel: "HTTP",
                                            onChange: "handleProtocolChange"
                                        }
                                    ]
                                }
                            ]
                        },
                        {kind: "Input", name: "archiveLocation",      hint: "Archive URI", changeOnInput: true, onchange: "inputChange"},
                        {kind: "Input", name: "archiveLoginName",     hint: "Username",    changeOnInput: true, onchange: "inputChange"},
                        {kind: "Input", name: "archiveLoginPassword", hint: "Password",    changeOnInput: true, onchange: "inputChange"},
                        {
                            kind: "RadioGroup",
                            name: "ipkFormatting",
                            onChange: "handleFormattingChange",
                            components: [
                                {
                                    label: "{id}--{ipk}"
                                },
                                {
                                    label: "{ipk}"
                                }
                            ]
                        },
                        {
                            kind: "Item", className: "appFooter", components: [
                                {kind: "BasicRichText", name: "archivePreview"},
                            ]
                        },
                        {
                            content: "NOTE: Password and Username are sent as part of the (public) URL. For security-reasons you should only do this for public or unprotected archives (when security is a requirement, use a dedicated FTP application instead)",
                            className: "appFooter"},
                        {
                            kind: "HFlexBox",
                            name: "indicateArchiveLocationArea",
                            tapHighlight: false,
                            components: [
                                {
                                    content: "Indicate app acrhive location",
                                    className: "enyo-label",
                                    flex: 1
                                },
                                {kind: "Spacer"},
                                {
                                    kind: "ToggleButton",
                                    name: "indicateArchiveLocationToggle",
                                    state: false,
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "indicateArchiveLocation"
                                }
                            ]
                        },
                        {
                            kind: "VFlexBox",
                            name: "onlyArchivedArea",
                            tapHighlight: false,
                            components: [
                                {
                                    kind: "HFlexBox",
                                    width: "100%",
                                    components: [
                                        {
                                            content: "Show only (known) archived apps",
                                            className: "enyo-label",
                                        },
                                        {name: "resetIconOA",className: "resetAppIcon", flex: 1, style: "width: 24px"}, 
                                    ]
                                },
                                {
                                    kind: "HFlexBox",
                                    components: [
                                        {kind: "Spacer"},
                                        {
                                            kind: "ToggleButton",
                                            name: "onlyArchivedToggle",
                                            state: false,
                                            onLabel: "yes",
                                            offLabel: "no",
                                            onChange: "onlyArchived"
                                        }
                                    ]
                                }
                            ]
                        },
                        {
                            content: "Apps that we don't know are archived will be hidden. We may, however, not know all apps that have been archived.",
                            className: "appFooter",
                            name: "onlyArchivedNote"
                        }
                    ],
                },
                {
                    height: "8px"
                },
                {
                    kind: "RowGroup",
                    caption: "Hidden Vendors",
                    name: "blacklist",
                    components: [
                       {
                            kind: "Control",
                            layoutKind: "HFlexLayout",
                            align: "center",
                            tapHighlight: false,
                            name: "blacklistGroup",
                            components: [
                                {
                                    kind: "HFlexBox",
                                    components: [
                                        {
                                            content: "Add Blacklist Button",
                                            className: "enyo-label",
                                            flex: 1
                                        },
                                        {name: "resetIconBL",className: "resetAppIcon"}, 
                                    ]
                                },
                                {
                                    kind: "HFlexBox",
                                    flex: 1,
                                    name: "blacklistToggleArea",
                                    components: [
                                        {kind: "Spacer"},
                                        {
                                            kind: "ToggleButton",
                                            name: "addBlacklistToggle",
                                            state: false,
                                            onLabel: "yes",
                                            offLabel: "no",
                                            onChange: "addBlacklistButton"
                                        }
                                    ]
                                }
                            ]
                        },
                        {flex: 1, name: "blacklistList", kind: "VirtualRepeater", onSetupRow: "blackListSetupRow", components: [
                            {kind: "SwipeableItem", name: "blacklistCreator", layoutKind: "HFlexLayout", onConfirm: "removeBlacklist", components: [
                                {kind: "Image", name: "vendorLogo", className: "vendorLogo noLogo", src: ""},
                                {layoutKind: "VFlexLayout", components: [
                                    {name: "vendorNames"},
                                    {name: "appCount", className: "enyo-label"}
                                ]}
                            ]}
                        ]}
                    ],
                },
                {
                    name: "blacklistSpacer",
                    height: "8px"
                },
                {
                    kind: "RowGroup",
                    caption: "Advanced features",
                    name: "advanced",
                    components: [
                        {
                            kind: "Control",
                            layoutKind: "HFlexLayout",
                            align: "center",
                            tapHighlight: false,
                            name: "accelerationGroup",
                            components: [
                                {
                                    kind: "HFlexBox",
                                    components: [
                                        {
                                            content: "Use CSS accelerated scrolling",
                                            className: "enyo-label",
                                            flex: 1
                                        },
                                        {name: "resetIconA",className: "resetAppIcon"}, 
                                    ]
                                },
                                {
                                    kind: "HFlexBox",
                                    flex: 1,
                                    name: "accelerationToggleArea",
                                    components: [
                                        {kind: "Spacer"},
                                        {
                                            kind: "ToggleButton",
                                            name: "useAcceleratedScrollingToggle",
                                            state: false,
                                            onLabel: "yes",
                                            offLabel: "no",
                                            onChange: "toggleAcceleratedScrolling"
                                        },
                                    ]
                                }
                            ]
                        },
                        {
                            content: "CSS acceleration can greatly affect scrolling performance. In general webOS phones should have this settings turned off, while the TouchPad should have it turned on.",
                            className: "appFooter",
                            name: "accelerationNote"

                        },
                        {
                            kind: "HFlexBox",
                            align: "center",
                            tapHighlight: false,
                            components: [
                                {
                                    content: "Load app-icons while scrolling",
                                    className: "enyo-label",
                                    flex: 1
                                },
                                {
                                    kind: "ToggleButton",
                                    name: "loadIconWhileScrollingToggle",
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "toggleScrollIconsLoad"
                                },
                            ]
                        },
                        {
                            content: "Loading images requires a lot of processing that can severely impact scrolling/rendering. When turned off, the application will request app-icons only when the user doesn't scroll",
                            className: "appFooter"
                        },
                        {
                            kind: "HFlexBox",
                            align: "center",
                            tapHighlight: false,
                            components: [
                                {
                                    content: "Show icons while scrolling",
                                    className: "enyo-label",
                                    flex: 1
                                },
                                {
                                    kind: "ToggleButton",
                                    name: "showIconWhileScrollingToggle",
                                    state: false,
                                    onLabel: "yes",
                                    offLabel: "no",
                                    onChange: "toggleScrollIconsShow"
                                },
                            ]
                        },
                        {
                            content: "Showing/rendering icons/images can be a very labour-intensive task. This option lets you hide these in the app-list when scrolling, to enhance performance on slower devices. NOTE: This will load the app-icons in the background when the above option is turned on.",
                            className: "appFooter"
                        },
                        {
                            layoutKind: "VFlexLayout",
                            components: [
                                {
                                    content: "List Prefetch",
                                    className: "enyo-label"
                                },
                                {
                                    kind: "Slider",
                                    name: "prefetchSlider",
                                    position: 2,
                                    minimum: 1,
                                    maximum: 10,
                                    snap: 1,
                                    onChange: "prefetchSliderChanged"
                                }
                            ]
                        },
                        {
                            content: "List prefetch determines how many apps are requested from the backend in a single call. The more apps are requested, the fewer calls will interupt scrolling, but the longer each call will take.",
                            className: "appFooter"
                        },
                        {
                            kind: "VFlexBox",
                            name: "liveSearchArea",
                            components: [
                                {
                                    kind: "HFlexBox",
                                    align: "center",
                                    tapHighlight: false,
                                    components: [
                                        {
                                            content: "Use live-search",
                                            className: "enyo-label",
                                            flex: 1
                                        },
                                        {
                                            kind: "ToggleButton",
                                            name: "useLiveSearchToggle",
                                            state: false,
                                            onLabel: "yes",
                                            offLabel: "no",
                                            onChange: "useLiveSearch"
                                        }
                                    ]
                                },
                                {
                                    kind: "HFlexBox",
                                    align: "center",
                                    tapHighlight: false,
                                    components: [
                                        {
                                            content: "Character threshold",
                                            className: "enyo-label",
                                            flex: 1
                                        },
                                        {
                                            kind: "Picker",
                                            value: "6",
                                            name: "characterThresholdPicker",
                                            onChange: "characterThesholdChanged",
                                            items: [
                                                "3",
                                                "4",
                                                "5",
                                                "6",
                                            ]
                                        }
                                    ]
                                }
                            ]
                        },
                        {
                            content: "How many character do you have to type, because making a search-call to the backend (please keep this high to make sure the server is not taxed needlessley). When turned off, the search will occur after you pressed enter.",
                            className: "appFooter",
                            name: "liveSearchNote"
                        }
                    ],
                },
            ]
        },
        {
            layoutKind: "HFlexLayout",
            components: [
                {kind: "Spacer", flex: 1},
                {kind: "Button", caption: "Close", onclick: "closePopup"},
                {kind: "Spacer", flex: 1}
            ]
        }
    ],
    create:function() {
        this.inherited(arguments);
        var isTouchpad = banneret.getGlobal('isTouchpad');
        if (!isTouchpad) {
            this.setFlyInFrom('left');
        }
    },
    rendered: function () {
        this.inherited(arguments);
        var isTouchpad = banneret.getGlobal('isTouchpad');

        if (!isTouchpad) {
            this.$.archiveHeader.setLayoutKind("VFlexLayout");
            this.$.archiveToggleArea.applyStyle("width", "inherit");
            this.$.accelerationGroup.setLayoutKind("VFlexLayout");
            this.$.accelerationToggleArea.applyStyle("width", "inherit");
        }

        var prefs = banneret.getPrefs();

        var value;
        switch(prefs.backendVersion) {
            case "_beta": {
                value = 1;
                break;
            }
            case "_alpha": {
                value = 2;
                break;
            }
            default: {
                value = 0;
                break;
            }
        }
        this.$.backendSourceSelector.setValue(value);

        this.$.appIconSizeSelector.setValue(prefs.appIconSize);
        this.$.categoryIconSizeSelector.setValue(prefs.catIconSize);
        this.$.showPriceToggle.setState(prefs.showPrice);
        this.$.showAdultToggle.setState(prefs.showAdult);
        this.$.showRandomAppToggle.setState(prefs.showRandomApp);
        this.$.showRandomAppButtonToggle.setState(prefs.showRandomAppButton);
        this.$.loadIconWhileScrollingToggle.setState(!!prefs.loadIconsWhileScrolling);
        this.$.showIconWhileScrollingToggle.setState(!!prefs.showIconsWhileScrolling);
        this.$.useAcceleratedScrollingToggle.setState(!!prefs.useAcceleratedScrolling);

        this.$.archiveUseCheckbox.setChecked(prefs.archiveUsage);
        this.$.archiveFTPToggle.setState(prefs.archiveFTP);
        this.$.archiveLocation.setValue(prefs.archiveLocation);
        this.$.archiveLoginName.setValue(prefs.archiveLoginName);
        this.$.archiveLoginPassword.setValue(prefs.archiveLoginPassword);

        this.$.indicateArchiveLocationToggle.setState(prefs.indicateArchive);

        this.$.useLiveSearchToggle.setState(prefs.liveSearch);
        this.$.characterThresholdPicker.setValue(prefs.liveSearchThreshold);

        this.$.prefetchSlider.setPosition(prefs.prefetchSize);

        this.$.ipkFormatting.setValue(prefs.archiveFileFormatting);

        this.showArchivePreview();

        this.renderByDevMode();

    /*archiveUsage: false,
    archiveFTP: true,
    archiveLocation: "webosftp.qualitis.hu/webOS Apps/Catalog Pull",
    archiveLoginName: "preanon",
    archiveLoginPassword: "",
    archiveFileFormatting: 0,*/
    },

    renderByDevMode: function () {
        var devMode = banneret.getGlobal("devMode");
        var isTouchpad = banneret.getGlobal('isTouchpad');

        if (this.$.settingsArea) {
            this.$.backendSourceSettingsGroup.setShowing(!!devMode);
            this.$.randomAppArea.parent.setShowing(!!devMode);
            this.$.randomAppNote.setShowing(!!devMode);
            this.$.randomAppButtonArea.parent.setShowing(!!devMode);            
            this.$.randomAppButtonNote.setShowing(!!devMode);
            this.$.liveSearchArea.parent.setShowing(!!devMode);
            this.$.liveSearchNote.setShowing(!!devMode);
            this.$.indicateArchiveLocationArea.parent.setShowing(!!devMode);
            this.$.onlyArchivedArea.parent.setShowing(!!devMode);
            this.$.onlyArchivedNote.setShowing(!!devMode);       
            this.$.blacklist.setShowing(!!devMode);
            this.$.blacklistSpacer.setShowing(!!devMode);

            if (isTouchpad) {
                this.$.accelerationGroup.parent.setShowing(!!devMode);
                this.$.accelerationNote.setShowing(!!devMode);
            }
        }
    },

    selectHandler: function (inSender, inEvent) {
        switch (inSender.name) {
            case "categoryIconSizeSelector":
                banneret.setPref("catIconSize", inEvent);
                enyo.$.museumApp.$.categories.$.catList.render();
            break;
            case "appIconSizeSelector":
                banneret.setPref("appIconSize", inEvent);
                enyo.$.museumApp.$.appList.$.appItem.setIconSize();
                enyo.$.museumApp.$.appList.$.appList.render();
                enyo.$.museumApp.$.appList.refresh(true);
            break;

        }
        this.refreshAfterChange(true);  // we don't need to update both lists as that may be an expensive operation.
    },
    togglePrice: function (inSender, inEvent) {
        banneret.setPref("showPrice", inEvent);
        if (enyo.$.museumApp.$.details && enyo.$.museumApp.$.details.getAppId()) {
            enyo.$.museumApp.$.details.getAppBySelectedId();
        }
    },
    toggleAdult: function (inSender, inEvent) {
        banneret.setPref("showAdult", inEvent);
        if (enyo.$.museumApp.$.details && enyo.$.museumApp.$.details.getAppId()) {
            enyo.$.museumApp.$.details.getAppBySelectedId();
        }
    },
    showRandomApp: function (inSender, inEvent) {
        banneret.setPref("showRandomApp", inEvent);
    },
    showRandomAppButton: function (inSender, inEvent) {
        banneret.setPref("showRandomAppButton", inEvent);
        this.owner.handlePrefsChange();
    },
    toggleScrollIconsLoad: function (inSender, inEvent) {
        banneret.setPref("loadIconsWhileScrolling", inEvent);
    },
    toggleScrollIconsShow: function (inSender, inEvent) {
        banneret.setPref("showIconsWhileScrolling", inEvent);
    },
    toggleAcceleratedScrolling: function (inSender, inEvent) {
        this.restart = true;
        banneret.setPref("useAcceleratedScrolling", inEvent);
        enyo.$.museumApp.$.appList.updateAcceleration();
        this.showResetRequired();
    },
    closePopup: function () {
        if (this.restart) {
            banneret.restartApp();
        } else if (this.refreshOnClose) {
            this.refreshOnClose = false;
            this.refreshAfterChange();
            this.close();
        } else {
            this.close();
        }
    },
    showPrefs: function (){
        this.openAtCenter();
        this.$.blacklist.render();
    },
    resetPreferences: function () {
        this.restart = true;
        banneret.resetPrefs();
        this.refreshAfterChange();
        this.showResetRequired();
    },
    showResetRequired: function () {
        this.$.resetIconA.addClass("resetting");
        this.$.resetIconR.addClass("resetting");
        this.$.resetIconOA.addClass("resetting");
        this.$.resetIconBS.addClass("resetting");
        this.$.resetIconBL.addClass("resetting");
    },
    refreshAfterChange: function(noListUpdates) {
        // refresh our lists
        if (!noListUpdates) {
            enyo.$.museumApp.$.categories.$.catList.render();
            enyo.$.museumApp.$.appList.$.appItem.setIconSize();
            enyo.$.museumApp.$.appList.$.appList.render();
            enyo.$.museumApp.$.appList.refresh(true);
        }

        enyo.$.museumApp.setPeekWidth();
    },
    blackListSetupRow: function (inSender, inIndex) {
        var blacklist = banneret.getBlacklist();
            blacklist = blacklist.sort(function(a, b){
                var keyA = a.names,
                    keyB = b.names;
                if(keyA < keyB) return -1;
                if(keyA > keyB) return 1;
                return 0;
            });
        if (inIndex < blacklist.length) {
            this.$.vendorLogo.setAttribute("onload", "showVendorIcon(this)");
            this.$.vendorLogo.setAttribute("vendorletter", blacklist[inIndex].names[0]);
            this.$.vendorNames.setContent(blacklist[inIndex].names);
            this.$.appCount.setContent ("(" + blacklist[inIndex].count + " apps)");

            setTimeout(function() {
                this.$.blacklistList.prepareRow(inIndex);
                this.$.vendorLogo.setSrc(banneret.getPrefs("detailLocation") + "/getVendorIcon.php?url=" + blacklist[inIndex].homepage);
            }.bind(this), 4000 + (inIndex*500));

            return true;
        }
    },
    removeBlacklist: function(inSender, inIndex) {
        // remove data
        banneret.removeBlacklist({idx: inIndex});
        this.$.blacklistList.render();
        this.restart = true;
        this.showResetRequired();
    },
    showArchivePreview: function () {
        var zeroWidthWhiteSpace = "&#x200B;";
        var ipk      = "com.palm.app.findapps_2.0.22300_all.ipk";
        var id       = "8846";

        var protocol = banneret.getPrefs("archiveFTP") ? "FTP://" : "HTTP://";
        var URI      = banneret.getPrefs("archiveLocation");
        var username = banneret.getPrefs("archiveFTP")   ? banneret.getPrefs("archiveLoginName") : "";
        var password = banneret.getPrefs("archiveFTP")   ? banneret.getPrefs("archiveLoginPassword") : "";
        var filename = banneret.getPrefs("archiveFileFormatting") ? ipk : id + "--" + ipk;

        var login    = [username, password].join(":&shy;") + "@"+zeroWidthWhiteSpace;

        var preview  = protocol + (banneret.getPrefs("archiveFTP") ? login : "") + URI + "/" + filename;
            preview  = preview.split(".").join("."+zeroWidthWhiteSpace).split("/").join("/"+zeroWidthWhiteSpace);
        this.$.archivePreview.setContent(preview);
    },
    handleProtocolChange: function (inSender, inEvent) {
        banneret.setPrefs({archiveFTP: inEvent});
        this.$.archiveLoginName.setDisabled(!inEvent);
        this.$.archiveLoginPassword.setDisabled(!inEvent);
        this.showArchivePreview();
    },
    handleFormattingChange: function (inSender, inEvent) {
        banneret.setPrefs({archiveFileFormatting: inEvent});
        this.showArchivePreview();
    },
    handleArchiveUse: function (inSender) {
        banneret.setPrefs({archiveUsage: inSender.checked});
        if (enyo.$.museumApp.$.details && enyo.$.museumApp.$.details.getAppId()) {
            enyo.$.museumApp.$.details.getAppBySelectedId();
        }
    },
    inputChange: function (inSender, inEvent) {
        var pref    = {};
            pref[inSender.name] = inSender.getValue();
        banneret.setPrefs(pref);
        this.showArchivePreview();
    },
    prefetchSliderChanged: function (inSender, inEvent) {
        banneret.setPrefs({prefetchSize: inEvent});
    },
    useLiveSearch: function (inSender, inEvent) {
        banneret.setPrefs({liveSearch: inEvent});
    },
    characterThesholdChanged: function (inSender, inEvent) {
        banneret.setPrefs({liveSearchThreshold: inEvent});
    },
    indicateArchiveLocation: function (inSender, inEvent) {
        banneret.setPrefs({indicateArchive: inEvent});
        this.refreshOnClose = true;
    },
    onlyArchived: function (inSender, inEvent) {
        banneret.setPrefs({onlyArchived: inEvent});
        this.restart = true;
        this.showResetRequired();
    },
    backendSourceSelected: function (inSender, inEvent) {
        var value;
        switch(inEvent) {
            case 1: {
                value = "_beta";
                break;
            }
            case 2: {
                value = "_alpha";
                break;
            }
            default: {
                value = "";
                break;
            }
        }
        banneret.setPrefs({backendVersion: value});
        this.restart = true;
        this.showResetRequired();
    },
    addBlacklistButton: function (inSender, inEvent) {
        banneret.setPrefs({blacklistButton: inEvent});
        this.restart = true;
        this.showResetRequired();
    }
});

// NOTE: showVendorIcon is a global function in /init/museum.js