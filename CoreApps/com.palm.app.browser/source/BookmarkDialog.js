enyo.kind({
	name: "BookmarkDialog",
	kind: "ModalDialog",
	published: {
		title: "",
		url: "",
		icons: {},
		id: "",
		acceptCaption: ""
	},
	events: {
		onAccept: ""
	},
	components: [
		{components: [
			{className: "item-thumb-container", style: "margin: 0 auto 8px", components: [
				{name: "icon", className: "item-image", kind: "Image"},
				{className: "item-image-frame"}
			]},
		]},
		{name: "titleInput", kind: "Input", hint: " ", insetClass: "enyo-flat-shadow", onkeydown: "inputKeydown", selectAllOnFocus: true},
	        {name: "urlInput", kind: "Input", inputType: "url", hint: " ", insetClass: "enyo-flat-shadow", selectAllOnFocus: true, autoCapitalize:"lowercase"},
		{kind: enyo.VFlexBox, components: [
			{name: "acceptButton", flex: 1, kind: "NoFocusButton", className: "enyo-button-dark", onclick: "acceptClick"},
			{kind: "NoFocusButton", flex: 1, caption: $L("Cancel"), onclick: "cancelClick"}
		]}
	],
	componentsReady: function() {
		this.inherited(arguments);
		this.titleChanged();
		this.iconsChanged();
		this.urlChanged();
		this.acceptCaptionChanged();
	},
	inputKeydown: function(inSender, inEvent) {
		if (inEvent.keyCode == 13) {
			this.acceptClick();
			return true;
		}
	},
	titleChanged: function() {
		if (!this.lazy) {
			this.$.titleInput.setValue(this.title);
		}
	},
	urlChanged: function() {
		if (!this.lazy) {
			this.$.urlInput.setValue(this.url);
		}
	},
	iconsChanged: function() {
		if (!this.lazy) {
			if (this.icons.iconFile64) {
				this.$.icon.setSrc(this.icons.iconFile64);
			} else {
				this.$.icon.setSrc("images/bookmark-icon-default.png");
			}
		}
	},
	acceptCaptionChanged: function() {
		if (!this.lazy) {
			this.$.acceptButton.setCaption(this.acceptCaption);
		}
	},
	getTitle: function() {
		return this.$.titleInput.getValue();
	},
	getUrl: function() {
		return this.$.urlInput.getValue();
	},
	acceptClick: function() {
		var t = enyo.string.escapeHtml(this.getTitle());
		var u = enyo.string.escapeHtml(this.getUrl());
		this.doAccept(t, u, this.icons, this.getId());
		this.close();
	},
	cancelClick: function() {
		this.close();
	},
	close: function() {
		this.inherited(arguments);
		this.$.titleInput.forceBlur();
		this.$.urlInput.forceBlur();
	}
});
