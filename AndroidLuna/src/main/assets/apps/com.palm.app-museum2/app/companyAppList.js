enyo.kind({
	name: "appMuseum.CompanyAppList",
	kind: "VFlexBox",
	events: {
		onAppSelected: "",
		onCloseCompanyPane: "",
	},
	published: {
		appList: [],
		selectedCompany: null,
	},
    _categories: {},
    touchpad: banneret.getGlobal("isTouchpad"),
	components: [
        {name:"companyHeader", showing: true, kind:"Toolbar", className:"enyo-toolbar enyo-toolbar-light searchHeader", components: [
            {kind: "Spacer"},
            {kind: "Button", content: "clear selection", onclick: "closeCompanyPane"}
        ]},
        {
            name: "appList",
            className: "appList",
            kind: "VirtualList",
            flex: 1,
            onSetupRow: "buildAppList",
            //onAcquirePage: "acquirePage",
            //onmousedown: "hideSearchBar",
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
        			content: "What else is there...",
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
        this.selectedCompanyChanged();
    },
    refresh: function (doPunt) {
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

    buildAppList: function(inSender, inIndex) {
        if (!this.appList || this.appList.length == 0) {
            // Just to bail out earlier if there are no apps in the data list
            return false;
        }

        if (inIndex >= 0) {   // checks whether the real index is greater than 0
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
                    this._categories[appItem.category] = "cat"+appItem.category.replace(/\s/g, "").replace("&","");
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
                return true;
            }
        }
    },

    /** scroller **/
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
    },
    /** app Item list **/

    handleAppSelection: function (inSender, inEvent) {
        var appListItem = this.appList[inEvent.rowIndex];
        this.doAppSelected(appListItem.id);
        inSender.tapHighlight = true;
    },

    /** company functions **/
    selectedCompanyChanged: function () {
        this.setAppList({});
        if (this.getSelectedCompany() !== null) {
            banneret.getMuseumList({
                    vendorId: this.getSelectedCompany(),
                },
                function (inData) {
                    this.setAppList(inData.data);
                    this.$.receivingDataBar.addClass("allDataReceived");
                    setTimeout(function () {
                        if(this.$.receivingDataBar.hasClass("allDataReceived")) {
                            // if all data is still received, we can make the bar non-showing.
                            // this also helps the spinner from not being rendered anymore than needed.
                            this.$.receivingDataBar.setShowing(false);
                        }
                    }.bind(this), 300);
                }.bind(this),
                function (inError) {

                }.bind(this))
            };
    },
    handleAddBookmark: function (inSender, inEvent) {
        console.log(inSender, inEvent);
    },
    closeCompanyPane: function() {
        this.doCloseCompanyPane();
    },
    appListChanged: function () {
        this.refresh(true);
    }
});