/**
 * Lunacy's file picker: the system UI that enyo.FilePicker opens.
 *
 * The contract is webOS's, and it is all that apps see:
 *   - parameters arrive as enyo.windowParams (fileType or fileTypes, extensions,
 *     allowMultiSelect, previewLabel), put there by Enyo from the query string;
 *   - the result goes back through enyo.CrossAppResult as
 *     {result: [{fullPath, iconPath, attachmentType, size}]}, or nothing when cancelled.
 *
 * What it lists comes from Lunacy's own service, because Lunacy has no media indexer:
 * palm://org.webosarchive.lunacy/files/list walks the webOS tree instead.
 */
enyo.kind({
	name: "FilePickerApp",
	kind: "VFlexBox",
	className: "lunacy-filepicker",
	components: [
		{name: "top", layoutKind: "HFlexLayout", className: "fp-top", components: [
			{flex: 1, name: "title", className: "enyo-text-header", content: $L("Select A File")},
			{name: "selection", className: "fp-selection", content: $L("No File Selected")}
		]},
		{kind: "Pane", name: "pane", flex: 1, className: "fp-body", components: [
			{name: "loading", kind: "HFlexBox", pack: "center", align: "center", components: [
				{kind: "Spinner"}, {content: $L("Looking...")}
			]},
			{name: "browser", kind: "Scroller", flex: 1, components: [
				{name: "albums", kind: "VFlexBox", className: "fp-albums"}
			]},
			{name: "empty", kind: "HFlexBox", pack: "center", align: "center", components: [
				{name: "emptyText", className: "fp-empty"}
			]}
		]},
		{name: "bottom", kind: "HFlexBox", className: "fp-bottom", components: [
			{flex: 1, kind: "Button", caption: $L("Cancel"), onclick: "cancel"},
			{flex: 1, kind: "Button", name: "ok", className: "enyo-button-affirmative", caption: $L("OK"), disabled: true, onclick: "choose"}
		]},
		{kind: "CrossAppResult", name: "result"},
		{kind: "PalmService", service: "palm://org.webosarchive.lunacy/", components: [
			{name: "list", method: "files/list", onResponse: "gotFiles"}
		]}
	],
	//* Where webOS kept the user's own files; the picker starts there, as Palm's did.
	root: "/media/internal",
	create: function() {
		this.inherited(arguments);
		this.params = enyo.windowParams || {};
		this.selected = [];
		this.multi = Boolean(this.params.allowMultiSelect);
		this.types = this.params.fileTypes || (this.params.fileType ? [this.params.fileType] : null);
		if (this.types && !(this.types instanceof Array)) { this.types = [this.types]; }
		this.$.title.setContent(this.params.previewLabel || this.titleFor());
		this.$.selection.setContent(this.multi ? $L("No Files Selected") : $L("No File Selected"));
		this.$.pane.selectViewByName("loading");
		this.$.list.call({path: this.root, types: this.types});
	},
	titleFor: function() {
		if (!this.types || this.types.length !== 1) { return this.multi ? $L("Select Files") : $L("Select A File"); }
		switch (this.types[0]) {
		case "image": return this.multi ? $L("Select Photos") : $L("Select A Photo");
		case "audio": return this.multi ? $L("Select Music") : $L("Select Music");
		case "video": return this.multi ? $L("Select Videos") : $L("Select A Video");
		case "document": return this.multi ? $L("Select Documents") : $L("Select A Document");
		}
		return this.multi ? $L("Select Files") : $L("Select A File");
	},
	gotFiles: function(inSender, r) {
		if (!r || !r.returnValue) {
			this.showEmpty(r && r.errorText || $L("Lunacy couldn't read the files."));
			return;
		}
		var albums = this.filtered(r.albums || []);
		if (!albums.length) {
			this.showEmpty($L("Nothing here to pick."));
			return;
		}
		for (var i = 0; i < albums.length; i++) { this.addAlbum(albums[i]); }
		this.$.pane.selectViewByName("browser");
	},
	//* The extensions parameter narrows what the service's type filter already gave us.
	filtered: function(albums) {
		var ext = this.params.extensions, out = [];
		for (var i = 0; i < albums.length; i++) {
			var files = albums[i].files || [];
			if (ext && ext.length) {
				files = files.filter(function (f) {
					var e = f.name.slice(f.name.lastIndexOf(".") + 1).toLowerCase();
					return enyo.indexOf(e, ext) >= 0 || enyo.indexOf("." + e, ext) >= 0;
				});
			}
			if (files.length) { out.push({name: albums[i].name, files: files}); }
		}
		return out;
	},
	addAlbum: function(album) {
		var images = this.types && this.types.length === 1 && this.types[0] === "image";
		var group = this.$.albums.createComponent({kind: "Control", className: "fp-album"}, {owner: this});
		group.createComponent({content: album.name, className: "fp-album-name"}, {owner: this});
		var body = group.createComponent({kind: "Control", className: images ? "fp-grid" : "fp-list"}, {owner: this});
		for (var i = 0; i < album.files.length; i++) {
			var f = album.files[i];
			if (images) {
				// A scaled copy: full-size photos would fill this device's memory.
				body.createComponent({
					kind: "Control", className: "fp-tile", file: f, onclick: "tapped",
					style: "background-image: url(" + this.thumbUrl(f.fullPath) + ")"
				}, {owner: this});
			} else {
				body.createComponent({
					kind: "Item", className: "fp-file", file: f, onclick: "tapped", layoutKind: "HFlexLayout",
					components: [{flex: 1, content: f.name}, {content: this.size(f.size), className: "fp-size"}]
				}, {owner: this});
			}
		}
		this.$.albums.render();
	},
	thumbUrl: function(path) {
		return encodeURI(path) + "?__lunacy_thumb=180";
	},
	size: function(n) {
		if (!n) { return ""; }
		if (n >= 1048576) { return (Math.round(n / 104857.6) / 10) + " MB"; }
		return Math.max(1, Math.round(n / 1024)) + " KB";
	},
	tapped: function(inSender) {
		var f = inSender.file;
		if (this.multi) {
			var at = -1;
			for (var i = 0; i < this.selected.length; i++) {
				if (this.selected[i].file.fullPath === f.fullPath) { at = i; break; }
			}
			if (at >= 0) { this.selected.splice(at, 1); inSender.removeClass("fp-chosen"); }
			else { this.selected.push({file: f, control: inSender}); inSender.addClass("fp-chosen"); }
		} else {
			for (var j = 0; j < this.selected.length; j++) { this.selected[j].control.removeClass("fp-chosen"); }
			this.selected = [{file: f, control: inSender}];
			inSender.addClass("fp-chosen");
		}
		this.updateSelection();
	},
	updateSelection: function() {
		var n = this.selected.length;
		this.$.ok.setDisabled(n === 0);
		if (n === 0) { this.$.selection.setContent(this.multi ? $L("No Files Selected") : $L("No File Selected")); }
		else if (n === 1) { this.$.selection.setContent(this.selected[0].file.name); }
		else { this.$.selection.setContent(n + " " + $L("Files Selected")); }
	},
	showEmpty: function(text) {
		this.$.emptyText.setContent(text);
		this.$.pane.selectViewByName("empty");
	},
	choose: function() {
		var files = [];
		for (var i = 0; i < this.selected.length; i++) {
			var f = this.selected[i].file;
			files.push({
				fullPath: f.fullPath,
				// webOS handed back a path the caller could show; here they are the same file.
				iconPath: f.fullPath,
				attachmentType: f.attachmentType,
				size: f.size
			});
		}
		this.$.result.sendResult({result: files});
	},
	cancel: function() {
		// No "result": enyo.FilePicker closes without firing onPickFile, as on webOS.
		this.$.result.sendResult({});
	}
});
