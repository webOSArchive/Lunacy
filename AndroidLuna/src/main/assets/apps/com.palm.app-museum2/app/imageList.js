enyo.kind({
	name: "museumApp.imageList",
	kind: "Control",
	className: "imageList",
	layoutKind: "HFlexLayout",
	published: {
		images: [],
		index: 0
	},
	components: [

	],
	create: function () {
		this.inherited(arguments);
		var touchpad = banneret.getGlobal("isTouchpad");
		if (!touchpad) {
			this.addClass('phone');
		} else {
			this.removeClass('phone');
		}
	},
	imagesChanged: function() {
		var touchpad = banneret.getGlobal("isTouchpad");
		var imageNums = Object.keys(this.images);
		var imageList = imageNums.map(function(num, idx, arr) {
			if (this.images[num].device === "P" && touchpad) {
				return this.images[num].screenshot;
			}
			return this.images[num].thumbnail;
		}.bind(this));
    	var s = this.$.innerClient;
    		s.destroyControls()

		imageList.forEach(function (img, idx, arr) {
			if (img.indexOf("://") == -1)
				var bURL = baseURL;
			else
				var bURL = "";
			s.createContainedComponent({
				name: "screenshot"+idx,
				className: "screenshot",
				index: idx,
				style: "background-image: url(" + bURL + img +")"
			}, {owner: this})
		}.bind(this));
		s.render();
	},
	getImageUrl: function (idx) {
		idx = idx === undefined ? this.index : idx;
		var urlSmall = this.thumbnails[idx];
		var urlBig   = this.images[idx];

		return {
			url: urlBig,
			thumb: urlSmall
		};
	},
	getImageView: function (idx) {
		idx = idx === undefined ? this.index : idx;
		if (idx >= 0 && idx < this.getImages().length) {
			var src = this.getImageUrl(idx);
			if (src) {
				return {
					kind: "ViewImage",
					src: src.thumb,
					autoSize: true,
					onImageFailed: "handleFail"
				};
			}
		}
	},
    getLeft: function(inSender) {
    	this.setIndex(this.getIndex()-1);
        This.imagesChanged();
    },
    getRight: function(inSender) {
    	this.setIndex(this.getIndex()+1);
        this.imagesChanged();
    },
    handleFail: function() {
    	this.getRight();
    }
});
