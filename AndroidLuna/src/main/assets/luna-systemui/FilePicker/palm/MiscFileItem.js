enyo.kind({
	name: "MiscFileItem",
	kind: enyo.Item,
	className: "enyo-item",
	layoutKind: "VFlexLayout",
	tapHighlight:true,
	published: {
		file: {},
	},
	create: function() {
		this.inherited(arguments);
	},
	components: [
		{layoutKind: "HFlexLayout", components:[
		                                        { name: "fileicon", className: "filepicker-file-icon", kind: "Image" },
		                                        { name: "filename", className: "enyo-text-ellipsis files-file-name"}
		]},
		{layoutKind: "HFlexLayout", components:[                                       
		                                        { name: "filedate", className:"files-file-date"},
		                                        {flex:1},
		                                        { name: "filesize", className:"files-file-size"}
		]}
	],
	processClick: function() {
		this.inherited(arguments);
	},
	fileChanged: function() {
		
		this.$.filename.setContent(this.file.name);
        
		if (this.file.extension && this.file.name) {
            this.file.fileExtensionFormatted = this.file.extension.substr(0, 4);
            this.$.filename.setContent(this.file.name + '.' + this.file.fileExtensionFormatted);
        }
		
        if (this.file.modifiedTime) {
            // Date stored in UTC time in seconds. Covert into MilliSeconds.
            var fileDate = new Date(parseInt(this.file.modifiedTime) * 1000);
            this.file.fileDateFormatted = Formatter.formatTimestamp(new Date(), fileDate);
			this.$.filedate.setContent(this.file.fileDateFormatted);
        }
        
       	if (this.file.size) {
            this.file.fileSizeFormatted = Formatter.formatSize(this.file.size);
			this.$.filesize.setContent(this.file.fileSizeFormatted);
        }
       	
       	this.file.imageSrcFormatted = "images/" + Formatter.getImageSrc(this.file.extension ? this.file.extension.toLowerCase() : 'generic') + '.png';
		this.$.fileicon.setSrc(this.file.imageSrcFormatted);
		this.addRemoveClass("file-selected", this.file.selectedFile);
	}
});