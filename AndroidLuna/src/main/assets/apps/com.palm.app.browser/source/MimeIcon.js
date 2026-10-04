enyo.kind({
	name: "MimeIcon",
	kind: enyo.Image,
	published: {
		mimeType: ""
	},
	//* @protected
	create: function() {
		this.inherited(arguments);
		this.mimeTypeChanged();
	},
	iconsByMimeType: {
		"audio": "audio",
		"image": "image",
		"video": "video",
		"text": {
			"x-vcard": "vcard"
		},
		"application": {
			"msword": "word",
			"pdf": "pdf",
			"vnd.openxmlformats-officedocument.wordprocessingml.document": "word",
			"excel": "xls",
			"x-excel": "xls",
			"x-msexcel": "xls",
			"vnd.ms-excel": "xls",
			"vnd.openxmlformats-officedocument.spreadsheetml.sheet": "xls",
			"vnd.openxmlformats": "xls",
			"powerpoint": "ppt",
			"mspowerpoint": "ppt",
			"x-mspowerpoint": "ppt",
			"vnd.ms-powerpoint": "ppt",
			"vnd.openxmlformats-officedocument.presentationml.presentation": "ppt"
		}
	},
	mimeTypeChanged: function() {
		if (this.mimeType) {
			var m = this.mimeType.split("/", 2);
			var r = this.iconsByMimeType[m[0]];
			if (r && typeof r !== "string") {
				r = r[m[1]];
			}
			if (r) {
				this.setSrc("images/mime-icon-" + r + ".png");
			} else {
				this.setSrc("images/mime-icon-download.png");
			}
		} else {
			this.setSrc("images/mime-icon-download.png");
		}
	}
});
