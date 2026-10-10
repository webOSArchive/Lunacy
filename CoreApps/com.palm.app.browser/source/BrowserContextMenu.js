enyo.kind({
	name: "BrowserContextMenu",
	kind: "PopupSelect",
	published: {
		view: ""
	},
	events: {
		onItemClick: ""
	},
	tapInfo: {link: true, image: true},
	linkItems: [
		{caption: $L("Open In New Card"), value:"newCardClick"},
		{caption: $L("Share Link"), value:"shareLinkClick"},
		{caption: $L("Copy URL"), value:"copyLinkClick"}
	],
	imageItems: [
		{caption: $L("Copy To Photos"), value: "copyToPhotosClick"},
		{caption: $L("Share Image"), value: "shareImageClick"},
		{caption: $L("Set Wallpaper"), value: "setWallpaperClick"}
	],
	openAtTap: function(inEvent, inTapInfo) {
		this.tapPosition = {left: inEvent.pageX, top: inEvent.pageY};
		this.tapInfo = inTapInfo;
		if (!this.view) {
			return;
		}
		var items = this.makeItems();
		if (items) {
			this.setItems(items);
			this.openNear(this.tapPosition);
		}
	},
	makeItems: function() {
		var items;
		if (this.tapInfo.isLink) {
			var uri = enyo.uri.parseUri(this.tapInfo.linkUrl);
			if (uri.scheme && enyo.uri.isValidScheme(uri)) {
				items = [].concat(this.linkItems);
			}
		}
		if (this.tapInfo.isImage) {
			items = (items || []).concat(this.imageItems);
		}
		return items;
	},
	menuItemClick: function(inSender) {
		this.doItemClick(inSender.getValue(), this.tapInfo, this.tapPosition);
		this.close();
	}
});
