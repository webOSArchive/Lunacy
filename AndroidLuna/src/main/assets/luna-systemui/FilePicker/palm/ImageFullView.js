enyo.kind({
	name: "ImageFullView",
	kind: enyo.Control,
	published: {
		imageIndex:0,
		allowMultiSelect: false,
		images:[],
		imageData: null
	},
	events: {
		onImageSelect:""
	},
	imageViewArr: [],
	components: [
		{kind: "ImageView", flex: 1, onGetLeft: "snapLeft", onGetRight:"snapRight"}
	],
	
	create: function() {
		this.inherited(arguments);
		//this.resizeHandler = enyo.bind(this, "resize");
		//window.addEventListener("resize", this.resizeHandler, false);
	},
	
	resize: function() {
        this.$.imageView.resize();
	},
	
	imagesChanged: function() {
		this.convertToImageViewArry(this.images);
		this.$.imageView.setCenterSrc(this.imageViewArr[this.imageIndex].path);
		//this.owner.doLabelChange({showOKButton:true, topLeftLabel:FPLabels.topLeftPreview + " "+ 1 + " of "+ this.imageViewArr.length});
	},
	
	
	convertToImageViewArry: function() {
		this.imageViewArr = [];
		for(var i=0, image; image=this.images[i]; i++) {
			if(image.path) {
				if(image.path == this.imageData.path)
					this.imageIndex = i;
				this.imageViewArr.push(image);
			}
		}
	},
	
	getImageUrl: function(inIndex) {
		var n = this.imageViewArr[inIndex];
		if (n) {
			return n.path;
		}
	},
	
	snapLeft: function(inSender, inValue) {
		inValue && --this.imageIndex;
		if(inValue && this.allowMultiSelect) {
			var msg = new enyo.g11n.Template($L("Preview #{num} of #{total}")).evaluate({num:this.imageIndex+1, total:this.imageViewArr.length});
			this.owner.doLabelChange({showOKButton:true, topLeftLabel:msg});
		}
		return this.getImageUrl(this.imageIndex-1);
	},
	
	snapRight: function(inSender, inValue) {	
		inValue && ++this.imageIndex;
		if(inValue && this.allowMultiSelect) {
			var msg = new enyo.g11n.Template($L("Preview #{num} of #{total}")).evaluate({num:this.imageIndex+1, total:this.imageViewArr.length});
			this.owner.doLabelChange({showOKButton:true, topLeftLabel:msg});
		}
		return this.getImageUrl(this.imageIndex+1);
	},
	
	selectButtonHandler: function() {
		if(this.allowMultiSelect)
			this.doImageSelect(this.imageViewArr);
		else {
			this.imageViewArr[this.imageIndex].dbId = this.images[this.imageIndex]._id;
			this.doImageSelect(this.imageViewArr[this.imageIndex]);
		}
	}
});