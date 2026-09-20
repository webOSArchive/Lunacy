enyo.kind({
	name: "appMuseum.AppList",
	kind: "VFlexBox",
	events: {
		onAquirePage: "",
		onAppSelected: "",
		onCloseCompanyPane: "",
		onApplistChanged: "",
        onCallSearchQuery: ""
	},
	published: {
		category: 0,
		appList: [],
		listOffset: 0,
        listLength: Infinity,
        query: ""
	},
    _categories: {},
	_nextPageRun: false,
	_currentPage: null,
    touchpad: banneret.getGlobal("isTouchpad"),
	components: [
		{
			name: "searchHeader",
			kind: "Toolbar",
			layoutKind: "HFlexLayout",
			className: "enyo-toolbar enyo-toolbar-light searchHeader",
			components:[
	            {
	            	name: "appSearch",
	            	kind: "enyo.RoundedSearchInput",
	            	flex:1,
	            	onblur: "hideSearchBar",
	            	autocorrect: false,
	            	autoCapitalize:"lowercase",
	            	autoWordComplete: false,
	            	spellcheck: false,
	            	onchange: "filterApps",
                    onkeypress: "_filterApps",
	            	oldValue: "",
	            	onCancel: "clearFilter"
	            }
			]
		},
        {
            name: "appList",
            className: "appList",
            kind: "VirtualList",
            flex: 1,
            onSetupRow: "buildAppList",
            onAcquirePage: "acquirePage",
            onmousedown: "hideSearchBar",
            components: [{
                name: "appItem",
                kind: "appMuseum.AppItem",
                onclick: "handleAppSelection",
                onAddBookmark: "handleAddBookmark"
            }]
        },
        {
        	name: "receivingDataBar",
        	className: "receivingDataBar",
        	kind: "HFlexBox",
        	style: "margin-top: -18px;",
        	components: [
        		{
        			kind: "Spacer"
        		},
        		{
        			content: "Wait, there's more...",
        			style: "padding-top: 6px;"
        		},
        		{
        			style: "width: 8px"
        		},
        		{
        			kind: "Spinner",
        			showing: true,
        			name: "appDataSpinner"
        		},
        		{
        			kind: "Spacer"
        		},
        	]
        }
	],

    /** general **/
    create: function () {
        this.inherited(arguments);
        if (!this.touchpad) {
            this.hideSearchBar();
        }

        var adjustTopOriginal = this.$.appList.$.buffer.adjustTop;
        this.$.appList.$.buffer.adjustTop = function (a) {
            var firstItem = this.topItemOnPage();
            if (this.firstItem !== firstItem) {
                this.firstItem = firstItem;

                var selectedKey = "";
                var first_position = this.owner._positions || {};
                var fpKeys = Object.keys(first_position);
                    fpKeys.some(function (key, idx, arr) {
                        if (first_position[key] <= firstItem) {
                            selectedKey = key;
                        } else {
                            return true;
                        }
                    }.bind(this));
                var firstLetterSelector = this.owner.$.selectFirstLetter;
                if (firstLetterSelector.value !== selectedKey) {
                    firstLetterSelector.setValue(selectedKey);
                }
            }
            return adjustTopOriginal.call(this.$.appList.$.buffer, a);
        }.bind(this);

    },
    appListUpdated: function(inData) {
        if (!isNaN(parseFloat(this.category))) {
            this.category = banneret.getGlobal("categories")[this.category].label;
        }
        try {
            this.setListLength(inData.appCount[this.getCategory()]);
        } catch(e) {
            try {
                this.setListLength(inData.extraData.listCount);
            } catch(e) {
                this.setListLength(Infinity);
            }
        }
        if (this.category === "Favourites") {
            this.setAppList(inData.data);
            this.refresh(true); 
        }
        var receivedPage = Math.floor(inData.return_indices[0]/this.$.appList.pageSize)
        if (this._currentPage === null ||
            this._currentPage === receivedPage ||
            Math.abs(this._currentPage-receivedPage) > 1 ||
            receivedPage === Math.floor(this.listLength/inData.request.count)) {
            
            this.refresh(false);    // we don't want to punt here!
        }
    },
    refresh: function (doPunt, forceRerender) {
        var _refresh = function () {
            if (doPunt || this._forcePunt) {
                // we only want to call resized() when it's really needed. Punt will refresh the
                // list-state so we can do without resized()
                this.$.appList.punt();
                this._forcePunt = false;
            } else {
                this.$.appList.resized();
            }
        }.bind(this)
        // improve user-experience by slightly delaying resized() so it isn't blocking
        setTimeout(function () {
            _refresh();
        }.bind(this), 50);
    },
    getListOffset: function () {
        return Math.floor(this.listOffset/this.$.appList.pageSize);
    },
    /** handle data **/
    acquirePage: function(inSender, inPage, callback) {
        if (this.category === "Favourites" || this.owner.$.details.getAppId() == undefined) { // either undefined or null
            // we will not aquire a page unless the details page has a selected app.
            return;
        }
        var listOffsetPage = this.getListOffset();
            inPage = listOffsetPage + inPage;
        var index = inPage * inSender.pageSize;
        if (index > this.listLength || index < 0) {
            // we reached the end of the list (requested from the backend)
            return;
        }
        var allAppsKnown = true;
        var i;
        var inData = [];
        for (i=index; i<index+inSender.pageSize; i++) {
            if (!this.appList[i]) {
                allAppsKnown = false;
                break;
            }
            inData.push(this.appList[i]);
        }
        if (allAppsKnown) {
            this.fetchSurroundingPages(inPage);
        }
        if (index >= 0 && !allAppsKnown) {
            this.$.receivingDataBar.setShowing(true);
            this.$.receivingDataBar.removeClass("allDataReceived");
            if (!this.calcPageSize.calculated) {
                this.calcPageSize();
            }
            var callObj = {
                    page: inPage,
                    count: inSender.pageSize,
                    device: enyo.$.museumApp.$.deviceFilterSelector.getValue(),
                    query: this.$.appSearch.getValue(),
                    category: this.getCategory()
                }
            banneret.getMuseumList(
                callObj,
                function (inData, inResponse) {
                    var r = inData.request;                
                    var appList = this.getAppList();
                    inData.indices.forEach(function (idxs, idx, arr) {
                        var listPos = inData.return_indices[idx];
                        appList[listPos] = inData.data[idx] !== null ? inData.data[idx] : banneret.getAppByIndex(idxs);
                    });
                    // we need to make sure that items beyond our listCount are excluded
                    appList.length = inData.appCount[this.getCategory()] || inData.extraData.listCount;

                    enyo.$.museumApp.$.selectFirstLetter.setItems(Object.keys(inData['first_position']));
                    enyo.$.museumApp._positions = inData['first_position']
                    this.appList = appList;
                    this.appListUpdated(inData);
                    if (typeof callback === "function") {
                        callback(inData);
                    }
                    this.$.receivingDataBar.addClass("allDataReceived");
                    setTimeout(function () {
                        if(this.$.receivingDataBar.hasClass("allDataReceived")) {
                            // if all data is still received, we can make the bar non-showing.
                            // this also helps the spinner from not being rendered anymore than needed.
                            this.$.receivingDataBar.setShowing(this.$.appSearch.getValue() > 0);    // we will punt whenever we're dealing with a query.
                        }
                    }.bind(this), 300);
                }.bind(this),
                function (inError) {

                }.bind(this)
            )
        } else {
            if (typeof callback === "function") {
                callback(inData);
            }
        }
    },
    fetchSurroundingPages: function (inPage) {
        if (this.category === "Favourites") {
            return;
        }
        var listObj = this.$.appList;
        var listOffsetPage = this.getListOffset();

            inPage = inPage+listOffsetPage || this._currentPage;
        var cPage = this._currentPage;
        var i,j;
        var index;
        var allAppsKnown;

        [-1,1].forEach(function(j, idx, arr) {
            allAppsKnown = true;
            index = (cPage+j) * listObj.pageSize;
            if (index >= 0) {
                for (i=index; i<index+listObj.pageSize; i++) {
                    if (!this.appList[i]) {allAppsKnown = false; break;}
                }
                if (!allAppsKnown && i < this.listLength) {
                    if (Math.abs((inPage+j)-(this._currentPage+(3*j))) <= 3) {
                        this.acquirePage(listObj, inPage+j-listOffsetPage)
                    }
                    //this.acquirePage(listObj, (inPage+j-listOffsetPage));
                }
            }
        }.bind(this));
    },

    /** render list **/
    rendered: function () {
        this.inherited(arguments);
        // we want our icons to be loaded only when we're not scrolling
        var appList = this; // note: one of the few cases where .bind is less useful
                            //       than a that=this variant.
        this.$.appList.$.scroller.$.scroll.start = function () {
            this.job || (this.animate(), this.doScrollStart(), appList.scrollStart());
        }
        this.$.appList.$.scroller.$.scroll.stop = function (a) {
            this.job = enyo.cancelRequestAnimationFrame(this.job), a && this.doScrollStop(); appList.scrollStop();
        }
        this.updateAcceleration();
    },

    getPageFromIndex: function (index) {
        return Math.floor(index/this.$.appList.pageSize);
    },
    getFirstIndexFromPage: function (page) {
        return page * this.$.appList.pageSize;
    },
    calcItemHeight: function () {
        // NOTE: we know each item in our list has a height of 86 pixels. As long as that isn't dynamic there is no
        //       reason to dynamically calculate it.

        // if (this.$.appItem.hasNode()) {
        //     return this.$.appItem.node.offsetHeight;
            this.calcPageSize.calculated = true;
        // } else {
            return 86;
        //}
    },
    calcPageSize: function () {
        var calc;

        // NOTE: we know each item in our list has a height of 86 pixels. As long as that isn't dynamic there is no
        //       reason to dynamically calculate it.

        if (this.hasNode()) {
            calc = Math.ceil(Math.ceil(this.node.offsetHeight/this.calcItemHeight()));
        }
        calc = calc < 10 ? 10 : calc;
        calc = calc * (banneret.getPrefs("prefetchSize") || 1);

        this.$.appList.setPageSize(calc < 10 ? 10 : calc);
    },

    buildAppList: function(inSender, index) {
        if (!this.appList || this.appList.length == 0) {
            // Just to bail out earlier if there are no apps in the data list
            return false;
        }
        var offset = this.listOffset;
        var inIndex = index + offset;

        if (inIndex >= -offset) {   // checks whether the real index is greater than 0
            var appItem = this.appList[inIndex];
            if (appItem) {
                var className = ["enyo-item", "enyo-hflexbox", "appListItem"];
                if (inIndex % 2 === 0) {
                    className.push("listItemEven");
                }
                if (appItem.archived) {
                    className.push("foundApp");
                } else {
                    if (appItem._archived){
                        if (banneret.getPrefs("indicateArchive")) {
                            className.push("foundAppLocally");
                        } else {
                            className.push("foundApp");
                        }
                    }
                }

                if (this._categories[appItem.category] === undefined) {
                    this._categories[appItem.category] = "cat"+appItem.category.replace(/\s/g, "").replace("&","").replace("'", "");
                }
                className.push(this._categories[appItem.category]);

                this.$.appItem.setClassName(className.join(" "));

                this.$.appItem.setupItem(appItem);

                if (banneret.getFavourites()[appItem.id] === true) {
                    this.$.appItem.$.bookmarkButton.setDepressed(true);
                }
                if (!this.isScrolling || banneret.getPrefs("showIconsWhileScrolling") === true) {
                    this.$.appItem.showIcon();
                } else {
                    this.$.appItem.hideIcon();
                }

                this._currentPage = Math.floor(inIndex/this.$.appList.pageSize);
                return true;
            } else {
                // we don't have our appData for this app yet. So we need to make a call.
                this.acquirePage(this.$.appList, this.getPageFromIndex(inIndex) - this.getListOffset());
            }
        }
    },

    /** scroller **/
    topItemOnPage: function (){
        var scrollOffset = Math.round(this.$.appList.$.scroller.$.scroll.y);
        var itemHeight   = this.calcItemHeight();
        return this.listOffset - Math.ceil(scrollOffset / itemHeight);
    },
    updateAcceleration: function () {
        this.$.appList.$.scroller.setAccelerated(banneret.getPrefs('useAcceleratedScrolling'));
        this.$.appList.$.scroller.punt();
    },
    scrollStart: function () {
        this.isScrolling = true;
        if (banneret.getPrefs("showIconsWhileScrolling") !== true) {
            var items = this.$.appList.$.scroller.$.content.hasNode().children;
            var i = 0, l = items.length;
            for (i; i<l; i++) {
                if (items[i].style.display !== "none") {
                    var itmIdx = items[i].children[0].getAttribute('rowindex');
                    this.$.appList.prepareRow(itmIdx);
                    this.$.appItem.hideIcon();
               }
            }
        }
    },
    scrollStop: function () {
        if (!this.isScrolling) {
            return;
        }
        this.isScrolling = false;

        // we're going to do a little DOM hack here. This is not really a
        // logical approach on Enyo (where you want the engine to do all DOM
        // actions), but it will help us greatly today.
        if (banneret.getPrefs("showIconsWhileScrolling") !== true || banneret.getPrefs("loadIconsWhileScrolling") !== true) {
            var items = this.$.appList.$.scroller.$.content.hasNode().children;
            var itemsOnScreen = [];
            var i = 0, l = items.length;
            for (i; i<l; i++) {
                if (items[i].style.display !== "none") {
                    itemsOnScreen.push(parseFloat(items[i].children[0].getAttribute('rowindex')))
                }
            }
            itemsOnScreen.forEach(function(itmIdx, idx, arr) {
                this.$.appList.prepareRow(itmIdx);
                this.$.appItem.showIcon(itmIdx);
            }.bind(this));
        }

        // The following is needed on the Pre3 for some reason. Sometimes the onAquirePage
        // isn't working the way I want it...and this is especially noticable on the phone.
        this.fetchSurroundingPages();
    },
    listOffsetChanged: function() {
        if (this.isScrolling) {return}  // ofsetChanges when scrolling are defined by the top-item and don't have to be handled as they are already onscreen.
        if (this.listOffset < 0) {this.listOffset = 0;}

        var appList = this.$.appList;
        this.acquirePage(appList, 0, function () {  // NOTE: We say we want page 0, because we want the first page with this listOffset (I hope this makes sense)
            this.refresh(true);
        }.bind(this));
    },

    /** app Item list **/

    handleAppSelection: function (inSender, inEvent) {
        var appListItem = this.appList[inEvent.rowIndex+this.listOffset];
        this.doAppSelected(appListItem.id);
        inSender.tapHighlight = true;
    },

    /** searchbar **/
    showSearchBar: function (inEvent) {
        if (this.touchpad || inEvent.charCode == 27 || this.owner.$.slidingPane.getViewIndex() !== 1) {
            return;
        }
/*        if (this.owner.$.slidingPane.getViewIndex() === 1) {
            this.$.appSearch.forceFocus();
        }*/

        var searchHeader = this.$.searchHeader;
        if (searchHeader.hasClass("hidden")) {
            searchHeader.removeClass("hidden");
            this.$.appSearch.setValue(String.fromCharCode(inEvent.keyCode));
        } else if (!this.$.appSearch.hasFocus()) {
            this.$.appSearch.setValue(this.$.appSearch.getValue() + String.fromCharCode(inEvent.keyCode));
        }

/*        if (this.owner.$.slidingPane.getViewIndex() < 1) {
            this.owner.selectNextView();
            this.owner.$.slidingPane.reallySelectView(this.owner.$.appSliding);
        }*/
    },
    hideSearchBar: function () {
        if (!this.touchpad) {
            var searchHeader = this.$.searchHeader;
            if (!searchHeader.hasClass('hidden')) {
                this.$.searchHeader.addClass("hidden");
                this.$.appSearch.forceBlur();
                this.$.appSearch.oldValue = "";
                this.$.appSearch.setValue("");
            }
        }
    },

    filterApps: function (inSender, inEvent) {
        if (inSender.getValue() === "Palm webOS 2018") {
            // we go into developer mode...which enables some 'experimental
            // features'...some of which may never see the light of day.
            // NOTE: these features as - as the name implies - not fully
            //       tested! - So be cautious...
            banneret.setGlobal("devMode", true);
            if (this.owner.$.preferencesPopup) {
                this.owner.$.preferencesPopup.renderByDevMode();
            }
            this.owner.$.devModeToaster.open();
            inSender.setValue("");
        }
        this.query = this.$.appSearch.getValue();
        if (banneret.getPrefs("liveSearch") && this.$.appSearch.getValue().length >= banneret.getPrefs("liveSearchThreshold")) {
            setTimeout(function() {
                if (this.$.appSearch.getValue() === this.query && this.$.appSearch.getValue().length !== 0) {
                    this.doCallSearchQuery(this.$.appSearch.getValue());
                }
            }.bind(this), 200);
        }
    },
    _filterApps: function (inSender, inEvent) {
        if (inEvent.keyCode === 13 && this.$.appSearch.getValue().length > 0) {
            // enter pressed in the searchbar.
            this.doCallSearchQuery(this.$.appSearch.getValue());
        }
    },

    handleAddBookmark: function (inSender, inEvent) {
        console.log(inSender, inEvent);
    },

    queryChanged: function () {
        this.$.appSearch.setValue(this.getQuery());
    },
    clearFilter: function () {
        this.setQuery("");
        this.doCallSearchQuery("");
    }
});