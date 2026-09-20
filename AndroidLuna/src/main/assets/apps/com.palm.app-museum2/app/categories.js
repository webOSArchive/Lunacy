enyo.kind({
	name: "appMuseum.Categories",
	published: {
		selectedRow: 0,
		categoryCount: {}
	},
	events: {
		onCategorySelected: ""
	},
	kind: "VFlexBox",
	flex: 1,
	horizontal: false,
	autoHorizontal: false,
	components: [
		{
			name: "catHeader",
			kind: "Toolbar",
			className: "enyo-toolbar-light",
			components:[
				{
					kind:"Control",
					content: "Categories"
				}
			]
		},
		{
			name: "categoryScroller",
			kind: "Scroller",
			flex: 1,
			components: [
				{
					name: "catList",
					kind: "VirtualRepeater",
					onSetupRow: "showCategories",
					components: [{
						kind: "Item",
						name: "catItem",
						layoutKind: "HFlexLayout",
						tapHighlight: "true",
						style: "padding: 0px;padding-left: 6px;",
						onclick: "handleItemClick",
						components: [
							{
								name: "catIcon",
								kind: "Image"
							}, {
								name: "catName",
								style: "line-height:48px;margin-left:8px;overflow:hidden;text-overflow:ellipsis;"
							}, {
								kind: "Spacer"
							}, {
								name: "countBullet",
								className: "appCountBullet",
								components: [
									{
										name: "appCountCounter",
										content: 0
									}
								]
							}
						]
					}]
				}
			]
		},
		{
			kind: "Toolbar",
			name: "catFooter",
			className:"enyo-toolbar-light"
		}
	],
	create: function() {
		this.inherited(arguments);
		var touchpad = banneret.getGlobal("isTouchpad");
		this.$.catHeader.setShowing(touchpad);
		this.$.catFooter.setShowing(touchpad);
	},
	refresh: function () {
		this.$.catList.render();
	},
	showCategories: function (inSender, index) {
		var category = banneret.getGlobal("categories")[index];
		if (category) {
			var iconLocationPlus = "1.5/";
            if (!banneret.getPrefs().catIconSize) {
                iconLocationPlus = "";
            }
            this.$.catIcon.setSrc("images/" + category.iconLocation + iconLocationPlus + "category-selector.png");
            this.$.catName.setContent(category.label);
            if (index === this.selectedRow) {
            	this.$.catItem.addClass("enyo-held");
            }
            if (!banneret.getPrefs().catIconSize) {
                this.$.catName.addStyles("line-height:32px;")
                this.$.catItem.addStyles("padding: 4px;")
            }
            if (this.categoryCount[category.label]) {
            	this.$.appCountCounter.setContent(this.categoryCount[category.label]);
            } else {
            	this.$.appCountCounter.setContent(0);
            }
            return true;
		}
		return false;
	},
	handleItemClick: function (inSender, inEvent) {
		var row = inEvent.rowIndex;

		this.$.catList.prepareRow(this.selectedRow);
		this.$.catItem.removeClass("enyo-held");
		this.selectedRow = row;

		this.$.catList.prepareRow(this.selectedRow);
		this.$.catItem.addClass("enyo-held");

		this.doCategorySelected(row);
	},
	categoryCountChanged: function () {
		this.refresh();
	}
})

