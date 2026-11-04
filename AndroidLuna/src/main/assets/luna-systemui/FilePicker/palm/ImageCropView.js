enyo.kind({
	name: "ImageCropView",
	kind: enyo.VFlexBox,
	published: {
		cropParams:''
	},
	events: {
		onImageSelect:""
	},
	components: [
		
	],
	
	create: function() {
		this.inherited(arguments);
	},
	
	cropParamsChanged: function() {
		//this.$.croppableImage.applyStyle("width:"+this.cropParams.width+"px;"+" height:"+this.cropParams.height+"px;");
		//this.$.croppableImage.setSrc(this.cropParams.imageData.path);
		this.destroyControls();
		this.createComponent({flex:1, name: "croppableImage", kind: "enyo.CroppableImage", onCrop: "processCropImage",owner:this,
							src:this.cropParams.imageData.path});
		//style:"width:"+this.cropParams.cropWidth+"px;"+" height:"+this.cropParams.cropHeight+"px;"
		this.render();
	},
	
	crop: function() { 
		this.$.croppableImage.getCropParams(); 
	},
	
	processCropImage: function(inSender, inParams) {
		this.cropParams.imageData.cropInfo = inParams;
		this.doImageSelect(this.cropParams.imageData);
	},
	
	selectButtonHandler: function() {
		this.$.croppableImage.getCropParams(); 
	},
	
});