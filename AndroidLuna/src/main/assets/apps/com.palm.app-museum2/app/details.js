enyo.kind({
    name: "appMuseum.Details",
    kind: "Control",
    layoutKind: "VFlexLayout",
    published: {
        appId: null,
        imageList: [],
    },
    _index: undefined,
    events: {
        onShowMoreByCompany: "",
        onShowImages: "",
        onRequestRerenderList: ""
    },
    components: [{
            name: "serviceRequest",
            kind: "PalmService",
            service: "palm://com.palm.applicationManager",
            method: "open"
        },
        {
            name: "emailRequest",
            kind: "PalmService",
            service: "palm://com.palm.applicationManager",
            method: "open"
        },
        {
            name: "countDownloadRequest",
            kind: "WebService",
        },
        {
            name: "headerLabel",
            layoutKind: "HFlexLayout",
            kind: "Toolbar",
            className: "enyo-toolbar enyo-toolbar-light",
            components: [
                { kind: "Image", name: "category", className: "categoryIconDetails" },
                { kind: "Spacer" },
                {
                    kind: "Control",
                    layoutKind: "HFlexLayout",
                    name: "devices",
                    domStyles: { "padding-right": "6px"},
                    components: [
                        { kind: "Control", name: "Pre", className: "preIcon" },
                        { kind: "Control", name: "Pixi", className: "pixiIcon" },
                        { kind: "Control", name: "Pre2", className: "pre2Icon" },
                        { kind: "Control", name: "Veer", className: "veerIcon" },
                        { kind: "Control", name: "Pre3", className: "pre3Icon" },
                        { kind: "Control", name: "TouchPad", className: "touchpadIcon", components: [
                            { kind: "Image", name:"LuneOS", className: "luneOSIcon", src: "images/devices/LuneOS.png" }
                        ] },
                    ]
                }
            ]
        },
        {
            className: "top-shadow",
            style: "position: relative;"
        },
        {
            kind: "Scroller",
            flex: 1,
            autoHorizontal: false,
            horizontal: false,
            components: [{
                layoutKind: "VFlexLayout",
                components: [{
                        name: "AppHeader",
                        className: "appHeaderDetail:",
                        layoutKind: "HFlexLayout",
                        style: "padding: 8px;",
                        components: [
                            { kind: "Image", name: "appIcon", height: "64px", width: "64px" },
                            {
                                layoutKind: "VFlexLayout",
                                width: "100%",
                                components: [
                                    { name: "appName", className: "appNameDetail" },
                                    {
                                        layoutKind: "HFlexLayout",
                                        components: [
                                            { kind: "Image", name: "vendorLogo", showing: false, className: "vendorLogo noLogo detailLogo", onclick: "openVendorWebPage" },
                                            { width: "6px" },
                                            { name: "appMaker", className: "appMakerDetail", flex: 1, onclick: "openVendorWebPage" },
                                            { kind: "Image", name: "ratingImage", showing: false, className: "starRatings" },
                                            { width: "6px" },
                                            {
                                                kind: "IconButton",
                                                name: "menuButton",
                                                style: "background-color: rgb(86,68,195); color: white;",
                                                icon: "images/button_share.png",
                                                onclick: "showShareMenu"
                                            },
                                            {
                                                kind: "IconButton",
                                                name: "fetchButton",
                                                style: "background-color: rgb(86,68,195); color: white;",
                                                icon: "images/fetchFromArchive.png",
                                                onclick: "handleFetch"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    },
                    {kind: "PopupSelect", name: "menuShare", onSelect: "shareMenuSelect", components: [
                        {caption: "Copy Share Link", value: "do-copyshare"}
                    ]},
                    { kind: "ModalDialog", name: "copyDialog", /*contentHeight: "100px",*/ layoutKind: "VFlexLayout",  
                        caption: "Share", components: [
                            { w: "fill", domStyles: {"text-align": "center", margin: "12px"}, content: "The share link has been copied!" },
                        { layoutKind: "HFlexLayout", components: [
                            { kind: "Button", caption: "OK", flex: 1, popupHandler: "OK"},
                        ]}
                    ]},
                    {
                        name: 'imageList',
                        className: "imageList",
                        layoutKind: "VFlexLayout",
                        components: [{
                            name: "screenshotArea",
                            kind: "Control",
                            flex: 1,
                            className: "screenshotArea",
                            onclick: "handleShowImages",
                            components: [
                                { name: "screenshot1", className: 'screenshot screenshot1' },
                                { name: "screenshot2", className: 'screenshot screenshot2' },
                                { name: "screenshot3", className: 'screenshot screenshot3' }
                            ]
                        }]
                    },
                    {
                        content: "Description",
                        className: "descriptionTitle"
                    },
                    {
                        name: "description",
                        kind: "BasicRichText",
                        className: 'description'
                    },
                    {
                        height: "16px"
                    },
                    {
                        content: "Version Notes",
                        className: "descriptionTitle",
                        name: "verstionNoteTitle"
                    },
                    {
                        name: "versionNotes",
                        kind: "BasicRichText",
                        className: 'description',
                        style: "margin-bottom: 16px;"
                    },
                    {
                        kind: "Spacer",
                        flex: 1
                    },
                    {
                        name: "factsArea",
                        className: "screenshotArea factsArea",
                        layoutKind: "VFlexLayout",
                        components: [
                            { name: "homepage", className: "linkString", kind: "HtmlContent", content: "<a href=''>Homepage</a>", address: "", onLinkClick: "handleRedirection" },
                            { name: "email", className: "linkString", kind: "HtmlContent", content: "<a href=''>Support eMail</a>", address: "", onLinkClick: "handleRedirection" },
                            { name: "copyright", className: "copyright" },
                            { height: "16px;" },
                            { name: "version" },
                            { name: "appid" },
                            { name: "app" },
                            { height: "16px;" },
                            { name: "downloadSize" },
                            {
                                layoutKind: "HFlexLayout",
                                components: [
                                    { name: "showMedia", kind: "HtmlContent", className: "media", onclick: "handleRedirection" },
                                    {
                                        layoutKind: "VFlexLayout",
                                        components: [{
                                                layoutKind: "HFlexLayout",
                                                components: [
                                                    { name: "price", className: "price" },
                                                    { name: "adds", className: "adds" }
                                                ]
                                            },
                                            {
                                                layoutKind: "HFlexLayout",
                                                components: [{
                                                        kind: "Button",
                                                        caption: "more apps",
                                                        onclick: "showMoreCompany"
                                                    },
                                                    {
                                                        kind: "Button",
                                                        name: "blacklist",
                                                        caption: "hide vendor",
                                                        toggling: "true",
                                                        onclick: "addToBlacklist"
                                                    }
                                                ]
                                            },
                                        ]
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        layoutKind: "HFlexLayout",
                        className: "appFooter",
                        components: [{
                                layoutKind: "HFlexLayout",
                                flex: 1,
                                components: [{
                                        layoutKind: "VFlexLayout",
                                        components: [
                                            { content: "no app-mode:" },
                                            { content: "dock-mode:" },
                                            { content: "universal search:" },
                                        ]
                                    },
                                    {
                                        layoutKind: "VFlexLayout",
                                        components: [
                                            { content: "", name: "noAppMode" },
                                            { content: "", name: "dockMode" },
                                            { content: "", name: "universalSearch" },
                                        ]
                                    }
                                ]
                            },
                            {
                                layoutKind: "VFlexLayout",
                                flex: 1,
                                components: [{
                                        content: "connectors:"
                                    },
                                    {
                                        content: "",
                                        name: "connectors"
                                    }
                                ]
                            },
                            {
                                layoutKind: "VFlexLayout",
                                flex: 1,
                                components: [{
                                        content: "services:"
                                    },
                                    {
                                        content: "",
                                        name: "services"
                                    }
                                ]
                            }
                        ]
                    }
                ]
            }]
        }
    ],
    create: function() {
        this.inherited(arguments);
        if (window.PalmSystem) {
            var newItems = this.$.menuShare.items;
            newItems.push({caption: "Email Link", value: "do-emailshare"})
            this.$.menuShare.setItems(newItems);
            
        }
        if (banneret.getGlobal('isTouchpad')) {
            this.$.ratingImage.setShowing(true);
            this.$.vendorLogo.setAttribute("onload", "showVendorIcon(this)");
            this.$.vendorLogo.setAttribute("onerror", "hideVendorIcon(this)");
            this.$.vendorLogo.setShowing(true);
        }
        var getRandomApp = function() {
            if (enyo.$.museumApp) {
                this.getAppBySelectedId();
            } else {
                setTimeout(function() {
                    getRandomApp();
                }.bind(this), 200)
            }
        }.bind(this);
        getRandomApp();
    },
    rendered: function() {
        this.$.blacklist.setShowing(banneret.getPrefs("blacklistButton"));
    },
    appIdChanged: function() {
        this.getAppBySelectedId(this.appId);
    },
    getAppBySelectedId: function(appId) {
        appId = appId >= 0 ? appId : this.appId;
        this.appId = appId;
        if (appId != undefined) { // checks for both undefined and null
            var myApp;
            var apps = banneret.getGlobal("appList");
            apps.some(function(app, idx, arr) {
                if (app.id === appId) {
                    this._index = idx;
                    myApp = app;
                    return true;
                }
                return false;
            });
            this.showAppDetails(myApp);
        } else {
            this.owner.handleRandomButton();
        }
    },
    showAppDetails: function(myApp) {
        var detailsFound = function() {
            if (banneret.isTouchpad) {
                hideVendorIcon(this.$.vendorLogo.hasNode());
                this.$.vendorLogo.setSrc("");
            }

            if (!myApp.archived && !myApp._archived) {
                this.$.devices.addStyles("opacity: 0.5");
            } else {
                this.$.devices.addStyles("opacity: 1");
            }
            var setClass = function(element, className, marker) {
                if (marker) {
                    element.addClass(className);
                } else {
                    element.removeClass(className);
                }
            }
            this.$.fetchButton.setShowing(banneret.getPrefs("archiveUsage"));

            setClass(this.$.Pre, 'activeDeviceIcon', myApp.Pre);
            setClass(this.$.Pixi, 'activeDeviceIcon', myApp.Pixi);
            setClass(this.$.Pre2, 'activeDeviceIcon', myApp.Pre2);
            setClass(this.$.Veer, 'activeDeviceIcon', myApp.Veer);
            setClass(this.$.Pre3, 'activeDeviceIcon', myApp.Pre3);
            setClass(this.$.TouchPad, 'activeDeviceIcon', myApp.TouchPad);
            setClass(this.$.LuneOS, 'activeLuneIcon', myApp.LuneOS);

            var categories = banneret.getGlobal("categories");
            categories.forEach(function(cat, idx, arr) {
                if (cat.label === myApp.category) {
                    this.$.category.setSrc("./images/" + cat.iconLocation + "1.5/category-selector.png");
                }
            }.bind(this));

            if (myApp.appIconBig && myApp.appIconBig.indexOf("://") == -1) {
                this.$.appIcon.setSrc(banneret.getPrefs("baseImageURL") + "/" + myApp.appIconBig.toLowerCase());
            }
            else {
                this.$.appIcon.setSrc(myApp.appIconBig);
            }
            this.$.appName.setContent(banneret.cleanText(myApp.title));
            this.$.appMaker.setContent(banneret.cleanText(myApp.author));
            this.$.vendorLogo.setAttribute("vendorletter", myApp.author[0]);
            if (!myApp.vendorId || myApp.vendorId == "")
                this.$.vendorLogo.setSrc(banneret.getPrefs("detailLocation") + "/getVendorIcon.php?url=" + (myApp.detail.homeURL || myApp.detail.supportURL));
            else
                this.$.vendorLogo.setSrc(banneret.getPrefs("detailLocation") + "/getVendorIcon.php?url=" + this.makeVendorUrl());

            if (myApp.detail.starRating != null && myApp.detail.starRating > 0) {
                var r = myApp.detail.starRating;
                this.$.ratingImage.setSrc("images/star-" + r + ".png");
                //console.log("adding star rating " + r);
            } else {
                this.$.ratingImage.setSrc("images/star-0.png");
                //console.log("removing star rating");
            }

            this.$.description.setContent(banneret.cleanText(myApp.detail.description).replace("\n", "<br>"));
            this.$.versionNotes.setContent(banneret.cleanText(myApp.detail.versionNote).replace("\n", "<br>"));
            if (myApp.detail.versionNote === "") {
                this.$.verstionNoteTitle.setShowing(false);
                this.$.versionNotes.setShowing(false);
            } else {
                this.$.verstionNoteTitle.setShowing(true);
                this.$.versionNotes.setShowing(true);
            }

            this.setImageList(myApp.detail.images);
            var isTouchpad = banneret.getGlobal("isTouchpad");
            if (isTouchpad) {
                this.$.imageList.removeClass("phone");
            } else {
                this.$.imageList.addClass("phone");
            }
            var screenshots = [];
            var sC = 0;
            screenshots[0] = myApp.detail.images[sC] || myApp.detail.images[++sC];
            sC++;
            screenshots[1] = myApp.detail.images[sC] ? myApp.detail.images[sC++] : screenshots[0];
            screenshots[2] = myApp.detail.images[sC] ? myApp.detail.images[sC] : screenshots[0];
            var style = [];
            var baseURL = banneret.getPrefs("baseImageURL") + "/";
            screenshots.forEach(function(screenS, idx, arr) {
                if (arr[idx].screenshot.indexOf("://") == -1)
                    var bURL = baseURL;
                else
                    var bURL = "";
                if (arr[idx].screenshot.toLowerCase().indexOf("http") === 0) {
                    bURL = "";
                }
                if (isTouchpad && screenS.device === "P") {
                    style.push("background-image: url('" + bURL + arr[idx].screenshot.toLowerCase() + "');");
                } else {
                    style.push("background-image: url('" + bURL + arr[idx].thumbnail.toLowerCase() + "');");
                }
            });
            this.$.screenshot1.addStyles("background-image: none");
            this.$.screenshot2.addStyles("background-image: none");
            this.$.screenshot3.addStyles("background-image: none");
            setTimeout(function() {
                this.$.screenshot1.addStyles(style[0]);
                setTimeout(function() {
                    this.$.screenshot2.addStyles(style[1]);
                    setTimeout(function() {
                        this.$.screenshot3.addStyles(style[2]);
                    }.bind(this), 250);
                }.bind(this), 250);
            }.bind(this), 100);
            this.$.screenshot1.addClass(screenshots[0].orientation === 'P' ? 'portrait' : 'landscape');
            this.$.screenshot2.addClass(screenshots[1].orientation === 'P' ? 'portrait' : 'landscape');
            this.$.screenshot3.addClass(screenshots[2].orientation === 'P' ? 'portrait' : 'landscape');
            this.$.screenshot1.removeClass(screenshots[0].orientation === 'P' ? 'landscape' : 'portrait');
            this.$.screenshot2.removeClass(screenshots[1].orientation === 'P' ? 'landscape' : 'portrait');
            this.$.screenshot3.removeClass(screenshots[2].orientation === 'P' ? 'landscape' : 'portrait');

            this.$.homepage.address = myApp.detail.homeURL;
            this.$.homepage.setContent("<a href='" + myApp.detail.homeURL + "'>" + myApp.detail.homeURL + "</a>");
            this.$.email.address = "mailto:" + myApp.detail.custsupportemail;
            this.$.email.setContent("<a href='mailto:" + myApp.detail.custsupportemail + "'>" + myApp.detail.custsupportemail + "</a>");
            this.$.copyright.setContent(myApp.detail.copyright);

            this.$.version.setContent("version: " + myApp.detail.version);
            this.$.appid.setContent("appId: " + myApp.id);
            this.$.app.setContent("app: " + myApp.detail.publicApplicationId);

            this.$.downloadSize.setContent("filesize: ~" + (myApp.detail.appSize / 1048576).toFixed(1) + "MB");

            if (banneret.getPrefs("showPrice") === true) {
                this.$.price.setContent(myApp.detail.price === 0 ? "FREE" : myApp.detail.price + myApp.detail.currency);
            } else {
                this.$.price.setContent(myApp.detail.price === 0 ? "FREE" : "paid");
            }
            this.$.adds.setShowing(myApp.detail.isAdvertized);

            this.$.showMedia.address = myApp.detail.mediaLink;
            if (myApp.detail.mediaIcon !== null) {
                this.$.showMedia.addStyles("background-image: url(" + myApp.detail.mediaIcon + ")");
            }
            this.$.showMedia.setShowing(myApp.detail.mediaIcon === null ? false : true);

            banneret.getGlobal("appList")[this._index] = myApp;


            if (myApp.detail.attributes &&
                myApp.detail.attributes.provides) {
                if (myApp.detail.attributes.provides.optimizedUserExperience) {
                    this.$.imageList.addClass("optimizedUserExperience");
                } else {
                    this.$.imageList.removeClass("optimizedUserExperience");
                }

                this.$.noAppMode.setContent(myApp.detail.attributes.provides.noApp ? "yes" : "no");
                this.$.dockMode.setContent(myApp.detail.attributes.provides.dockMode ? "yes" : "no");
                this.$.universalSearch.setContent(myApp.detail.attributes.provides.universalSearch ? "yes" : "no");

                var connectors = myApp.detail.attributes.provides.connectors.map(
                    function(conn, idx, arr) {
                        return conn === "null" ? "" : conn;
                    }).join("\n\r").trim();
                this.$.connectors.setContent(
                    connectors === "" ? "none" : connectors
                );
                var services = myApp.detail.attributes.provides.services.map(
                    function(srv, idx, arr) {
                        return srv === "null" ? "" : srv;
                    }).join("\n\r").trim();
                this.$.services.setContent(
                    services === "" ? "none" : services
                );
            }

            this.$.blacklist.vendorId = myApp.vendorId;

            if (!banneret.getBlacklist(myApp.vendorId)) {
                this.$.blacklist.setDepressed(false);
                this.$.blacklist.addStyles("text-decoration: none;");
            } else {
                this.$.blacklist.setDepressed(true);
                this.$.blacklist.addStyles("text-decoration: line-through;");
            }

        }.bind(this);

        if (myApp) {
            this.appId = myApp.id;
            if (myApp.detail === undefined) {
                banneret.loadJSON(
                    banneret.getPrefs("detailLocation") + "/" + banneret.getPrefs("detailPage") + banneret.getPrefs("backendVersion") + ".php?id=" + String(this.appId),
                    function(inResponse) {
                        myApp.detail = inResponse;
                        detailsFound();
                    }.bind(this),
                    function(inError) {
                        console.log(inError);
                    }
                )
            } else {
                detailsFound();
            }
        }
    },
    openVendorWebPage: function() {
        enyo.log("clicked vendor!");
        this.handleRedirection( { address: this.makeVendorUrl()} );
    },
    makeVendorUrl: function() {
        var vendorURL = banneret.getPrefs("detailLocation").replace("WebService", "author");
        vendorURL += this.$.appMaker.content.replace(/ /g, "");
        return vendorURL;
    },
    showShareMenu: function() {
        this.$.menuShare.openAroundControl(this.$.menuButton);
    },
    shareMenuSelect: function(inSender, inSelected) {
        var value = inSelected.getValue();
        enyo.log("Share option selected: " + value);

        var shareURL = banneret.getPrefs("detailLocation").replace("WebService", "app");
        shareURL += this.$.appName.content.replace(/ /g, "");

        switch(value) {
            case "do-copyshare":
                enyo.log("sharing url: " + shareURL);
                enyo.dom.setClipboard(shareURL);
                this.$.copyDialog.openAtCenter();
                break;
            case "do-emailshare":
                this.$.emailRequest.call({ id: "com.palm.app.email", params: { summary: "Check out this great webOS app", text: shareURL } });
                break;
        }
    },
    showMoreCompany: function() {
        var myApp = banneret.getAppById(this.appId);
        this.doShowMoreByCompany(myApp.vendorId);

        /*banneret.getMuseumList({
            category: "All",
            vendorId: myApp.vendorId
        },
        function (inData) {

        }.bind(this),
        function (inError) {

        }.bind(this))*/
    },
    handleFetch: function() {
        var myApp = banneret.getGlobal("appList")[this._index];
        //Support absolute download paths (files hosted elsewhere)
        if (myApp.detail.filename.indexOf("://") == -1) {
            var protocol = banneret.getPrefs("archiveFTP") ? "FTP://" : "HTTP://";
            var URI = banneret.getPrefs("archiveLocation");
            var username = banneret.getPrefs("archiveFTP") ? banneret.getPrefs("archiveLoginName") : "";
            var password = banneret.getPrefs("archiveFTP") ? banneret.getPrefs("archiveLoginPassword") : "";
            var filename = banneret.getPrefs("archiveFileFormatting") ? myApp.detail.filename : String(myApp.id) + "--" + myApp.detail.filename;
            
            var login = [username, password].join(":") + "@";
            var app = protocol + (banneret.getPrefs("archiveFTP") ? login : "") + URI + "/" + filename;
        } else {
            var app = myApp.detail.filename;
        }
        
        //Do the right kind of download for environment
        enyo.log("Window location is " + JSON.stringify(window.location));

        if (window.location.hostname && window.location.hostname.indexOf(".media.cryptofs.apps") != -1) {   // Running on webOS
            this.$.serviceRequest.call({ id: "org.webosinternals.preware", params: { type: "install", file: app } });
            this.$.countDownloadRequest.setUrl(banneret.getPrefs("detailLocation") + "/countAppDownload.php?appid=" + myApp.id + "&source=webos");
        } else if(window.location.href.indexOf("file:///media/cryptofs") != -1) { // Running on LuneOS
            this.$.serviceRequest.call({ id: "org.webosports.app.preware", params: { type: "install", file: app } });
            this.$.countDownloadRequest.setUrl(banneret.getPrefs("detailLocation") + "/countAppDownload.php?appid=" + myApp.id + "&source=luneos");
        } else {    // Running in a web browser
            window.open(app);
            this.$.countDownloadRequest.setUrl(banneret.getPrefs("detailLocation") + "/countAppDownload.php?appid=" + myApp.id);
        }
        
        //Count download
        this.$.countDownloadRequest.call();
    },
    handleShowImages: function() {
        this.doShowImages(this.getImageList());
    },
    handleResize: function(inEvent) {
        var layout = "HFlexLayout"
        if (parseFloat(inEvent) < 640) {
            layout = "VFlexLayout";
        }
        this.$.imageList.setLayoutKind(layout);
        this.$.imageList.render();
    },
    handleRedirection: function(inSender, inEvent) {
        if (inSender.address === null || inSender.address === undefined) {
            return;
        }
        if (window.PalmSystem) {
            this.$.serviceRequest.call({ target: inSender.address });
        } else {
            window.open(inSender.address);
        }
    },
    addToBlacklist: function(inSender, inEvent) {
        if (inSender.depressed) {
            banneret.addBlacklist(inSender.vendorId);
            inSender.addStyles("text-decoration: line-through;");
        } else {
            banneret.removeBlacklist({ id: inSender.vendorId });
            inSender.addStyles("text-decoration: none;");
        }
        this.doRequestRerenderList();
    }
});

// NOTE: showVendorIcon is a global function in /init/museum.js