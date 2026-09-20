enyo.kind({
	name: "appMuseum.AppItem",
	kind: "Item",
	tapHighlight: true,
	layoutKind: "HFlexLayout",
	events: {
		onAddBookmark: ""
	},
	published: {
		appId: null
	},
	components: [
		{
			layoutKind: "VFlexLayout",
			components: [
				{
					kind: "Spacer",
					flex: 1
				},
				{
					name: "appIcon",
					kind: "AnimatedImage",
					height: "64px",
					width: "64px",
					className: "itemIcon hideOnScrollArea"
				},
				{
					kind: "Spacer",
					flex: 1
				}
			]
		},
		{
			kind: "Control",
			flex: 1,
			style: "padding-left: 10px",
			components: [
				{
					name: "appTitle",
					className: "itemTitle"
				},
				{
					name: "appAuthor",
					className: "itemAuthor"
				},
				{
					height: "24px",
					layoutKind: "HFlexLayout",
					name: "infoIcons",
					className: "hideOnScrollArea",
					showing: false,
					components: [
						{className: "categoryIndicator"},
						{className: "appListIcon"}
					]
				}
			]
		},
		{
			layoutKind: "VFlexLayout",
			width: "42px",
			components: [
				{kind: "Spacer", flex: 1},
				{
					name: "bookmarkButton",
					kind: "CustomButton",
					className: "enyo-button enyo-button-light bookmarkButton hideOnScrollArea",
					onclick: "handleBookmark",
					toggling: true,
					showing: false,
					height: "20px",
					width: "12px",
					style: "margin-right: 3px;",
					components: [
						{className: "bookmarkInner"}
					]
				},
				{kind: "Spacer", flex: 1}
			]
		}
	],
	/** general **/
	create: function () {
		this.inherited(arguments);
	},
	/** setup item **/
	setupItem: function (appItem) {
		var favourites  = banneret.getPrefs("favorites") || {};
		var isFavourite = !!favourites[appItem.id];

        this.setAppId(appItem.id);

		this.$.bookmarkButton.setDepressed(isFavourite);

		var portrait = banneret.getGlobal("orientation") === "portrait";
		this.$.appTitle.applyStyle("width", portrait ? "325px" : "575px");
		this.$.appTitle.setContent(banneret.cleanText(appItem.title));
		this.$.appAuthor.setContent(banneret.cleanText(appItem.author));

		if (appItem.appIcon.indexOf("://") == -1 && appItem.appIconBig.indexOf("://") == -1) {
			var appIconUri = banneret.getPrefs().baseImageURL + "/" + (banneret.getPrefs().appIconSize ? appItem.appIconBig : appItem.appIcon).toLowerCase();
		}
		else {
			var appIconUri = appItem.appIcon;
		}
		this.$.appIcon.setAttribute("icon-src", appIconUri);
        if (banneret.getPrefs("loadIconsWhileScrolling") === true) {
            this.$.appIcon.addStyles("background-image: url('"+appIconUri+"');");
        }
        this.setIconSize();
	},

	/** render item **/
	setIconSize: function() {
		var iconSize = banneret.getPrefs().appIconSize ? 64 : 48;
		this.$.appIcon.addStyles("width: " + iconSize + "px; height: " + iconSize + "px;");
	},
	showIcon: function (itmIdx) {
		var icon    = this.$.appIcon;
		var iconUri = icon.domAttributes["icon-src"];
		var style   = "visibility: visible";
		if (!this.domStyles['background-image'] &&
			!(this.getOwner().isScrolling &&
			banneret.getPrefs("loadIconsWhileScrolling") !== true)
		   ) {
			style = "background-image: url('"+iconUri+"');" + style;  // NOTE: This order is relevant!
		}
		icon.addStyles(style);

        if (this.$.bookmarkButton.getShowing() !== true) {
            this.$.bookmarkButton.setShowing(true);
            this.$.infoIcons.setShowing(true);
            setTimeout(function() {
                this.getOwner().$.appList.prepareRow(itmIdx);
                this.removeClass("hiddenOnScroll");
            }.bind(this), 20);
        }
   	},
   	hideIcon: function () {
        this.$.appIcon.addStyles("visibility: hidden");;
        this.$.bookmarkButton.setShowing(false);
        this.$.infoIcons.setShowing(false);
        this.addClass("hiddenOnScroll");
   	},

	/** bookmarkButton **/
	handleBookmark: function (inSender, inEvent) {
		var appId = this.getAppId();
		var toggleBookmark = inSender.depressed ? banneret.addFavourite : banneret.removeFavourite;
			toggleBookmark(appId);
		this.doAddBookmark({
			appId: appId,
			bookmarked: inSender.depressed
		})
	}
})