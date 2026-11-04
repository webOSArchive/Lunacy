enyo.kind({
	name: "VideoPicker",
	kind: enyo.VFlexBox,
	published:{
		allowMultiSelect: false
	},
	events: {
		onVideoSelect: "",
		onLabelChange: ""
	},
	components: [
		{name: "pane", kind: "Pane", flex: 1, onSelectView: "processViewSelected", 
			components: [
		                    {name: "albumList", kind: "ImageAlbumList", mediaType:"video", onAlbumClick: "albumClick"},
		                    {name: "imageAlbum", kind:"VideoAlbumList", onFileSelect: "videoSelect"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.showAlbums();
	},
	showAlbums: function() {
		this.$.albumList.getAlbums();
		if (this.$.pane.getViewIndex() != 0) {
			this.$.pane.selectViewByIndex(0);
		}
	},
	albumClick: function(inSender, inAlbum) {
		if(this.allowMultiSelect)
			this.doLabelChange({showOKButton:true, topLeftLabel:FPLabels.multiSelectLeftLabel});
		this.$.imageAlbum.setAlbumId(inAlbum._id);
		this.$.imageAlbum.setAllowMultiSelect(this.allowMultiSelect);
		this.$.pane.selectViewByIndex(1);
	},
	
	videoSelect: function(inSender, selectedImages) {
		this.doVideoSelect(selectedImages);
	},
	
	processMultiImageClick: function(inSender,selectedImages) {
		this.doVideoSelect(selectedImages);
	},
	
	processViewSelected: function(inSender, inView, inPreviousView) {
		inView.activateView && inView.activateView(true);
		inPreviousView && inPreviousView.cleanupView && inPreviousView.cleanupView(true);
	},
	
	selectButtonHandler: function() {
		var currentView = this.$.pane.getViewName();
		if(currentView == "albumList")
			enyo.log("FP - Error - Select Button Tapped on AlbumList View!");
		else
			this.$.pane.viewByName(currentView).selectButtonHandler();
	},
	
	goToDefaultView: function() {
		this.$.pane.selectViewByIndex(0);
	},
	
	backHandler: function(inEvent) {
		var currentView = this.$.pane.getViewName();
		if(currentView == "albumList")
			this.owner.backToMainView();
		else {
			this.$.pane.back();
		}	
	}
});