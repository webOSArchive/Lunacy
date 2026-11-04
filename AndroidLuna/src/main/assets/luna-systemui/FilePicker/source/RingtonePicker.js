/**
 * The picker for webOS's "ringtone" type, as the TouchPad showed it (codepoet, 2026-11-04):
 * Palm's FilePickerApp with its RingtonePicker, which is two of Palm's AudioPickers - the
 * ringtones, and the music a ringtone can be added from.
 *
 *   - "Select A File" over a search field; each tone a row of Palm's AudioItem (palm/), with
 *     its title, its artist ("Unknown Artist" without one), and the preview button, which
 *     plays it and shows its progress across the row (Palm's FPAudioPlayer, palm/).
 *   - A tap picks the tone at once, with no OK, and the result is Palm's:
 *     [{fullPath, size, name: the title, attachmentType: "audio"}].
 *   - A ringtone can be swiped away (systemservice's ringtone/deleteRingtone), and the
 *     add button (menu-icon-add-ringtone) lists the music to make one from
 *     (ringtone/addRingtone), with Cancel turning into Back.
 *
 * Palm's AudioPicker read the media index with a DbList. The picker runs as the app that
 * opened it, which may not read that kind, so the rows come from Lunacy's service instead
 * (files/list), in a VirtualList as DbList's were; the logic below is AudioPicker's,
 * RingtonePicker's and FilePickerApp's.
 */
window.rb = window.rb || {$L: function (s) { return $L(s); }};

enyo.kind({
	name: "RingtonePickerApp",
	kind: "VFlexBox",
	className: "lunacy-ringtone-picker",
	components: [
		{name: "title", className: "enyo-text-header", style: "text-align: center;", content: $L("Select A File")},
		{kind: "Pane", name: "pane", flex: 1, components: [
			{name: "ringtones", kind: "RingtoneAudioList", isRingtone: true, onAudioFileSelect: "ringtonePicked", onDeleteRingtone: "deleteRingtone"},
			{name: "music", kind: "RingtoneAudioList", isRingtone: false, onAudioFileSelect: "musicPicked"}
		]},
		{kind: "HFlexBox", components: [
			{flex: 1, kind: "Button", name: "cancelButton", caption: $L("Cancel"), onclick: "cancel"},
			{flex: 1, kind: "IconButton", name: "addRingtoneButton", icon: "images/menu-icon-add-ringtone.png", onclick: "showMusic"}
		]},
		{kind: "CrossAppResult", name: "result"},
		{kind: "PalmService", service: "palm://com.palm.systemservice/ringtone/", onResponse: "ringtonesChanged", components: [
			{method: "addRingtone", name: "addRing"},
			{method: "deleteRingtone", name: "deleteRing"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.params = enyo.windowParams || {};
	},
	rendered: function() {
		this.inherited(arguments);
		this.$.ringtones.load();
	},
	ringtonePicked: function(inSender, inFiles) {
		this.$.result.sendResult({result: inFiles});
	},
	// RingtonePicker.processAudioFileSelect: back to the ringtones, and the music made one.
	musicPicked: function(inSender, inFiles) {
		this.showRingtones();
		this.$.addRing.call({filePath: inFiles[0].fullPath});
	},
	deleteRingtone: function(inSender, inFile) {
		this.$.deleteRing.call({filePath: inFile.path});
	},
	ringtonesChanged: function() {
		this.$.ringtones.load();
	},
	showMusic: function() {
		this.$.cancelButton.setCaption($L("Back"));
		this.$.addRingtoneButton.setShowing(false);
		this.$.ringtones.cleanupView();
		this.$.pane.selectViewByName("music");
		this.$.music.load();
	},
	showRingtones: function() {
		this.$.music.cleanupView();
		this.$.cancelButton.setCaption($L("Cancel"));
		this.$.addRingtoneButton.setShowing(true);
		this.$.pane.selectViewByName("ringtones");
	},
	cancel: function() {
		if (this.$.pane.getViewName() === "music") {
			this.showRingtones();
			return;
		}
		this.$.ringtones.cleanupView();
		// No "result": enyo.FilePicker closes without firing onPickFile, as on webOS.
		this.$.result.sendResult({});
	}
});

enyo.kind({
	name: "RingtoneAudioList",
	kind: enyo.VFlexBox,
	className: "enyo-filepicker-mainview",
	published: {
		isRingtone: false
	},
	events: {
		onAudioFileSelect: "",
		onDeleteRingtone: ""
	},
	components: [
		{kind: "SearchInput", className: "enyo-tool-input", name: "searchField", autoCapitalize: "lowercase",
			autocorrect: false, spellcheck: false, autoEmoticons: false, autoWordComplete: false,
			onchange: "processOnSearch", onCancel: "processOnCancel", keypressInputDelay: 500},
		{flex: 1, name: "list", className: "custom-rowgroup", kind: "VirtualList", onSetupRow: "listSetupRow", components: [
			{name: "audioItem", kind: "AudioItem", onclick: "fileClick", onPlayPause: "handlePlayPause", onDelete: "handleDelete"}
		]},
		{flex: 1, name: "empty", align: "center", pack: "center", showing: false, layoutKind: "VFlexLayout", components: [
			{className: "enyo-audio-list-empty"},
			{content: $L("Your music library is empty.")}
		]},
		{kind: "FPAudioPlayer", name: "audioPlayer", onProgressUpdate: "handleAudioProgressUpdate"},
		{kind: "PalmService", service: "palm://org.webosarchive.lunacy/", components: [
			{name: "lister", method: "files/list", onResponse: "gotFiles"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.all = [];
		this.files = [];
		this.filterString = undefined;
		this.playingRowIndex = undefined;
	},
	load: function() {
		this.abortAudio();
		this.$.lister.call({path: "/media/internal", types: [this.isRingtone ? "ringtone" : "audio"]});
	},
	// The service's albums as the media index's records: path, size, title, artist, searchKey.
	gotFiles: function(inSender, r) {
		var all = [], albums = (r && r.albums) || [];
		for (var i = 0; i < albums.length; i++) {
			var files = albums[i].files || [];
			for (var j = 0; j < files.length; j++) {
				var f = files[j];
				// The music to add from: what isn't a ringtone already, as isRingtone false was.
				if (!this.isRingtone && f.fullPath.indexOf("/media/internal/ringtones/") === 0) { continue; }
				var title = f.title || f.name.replace(/\.[^.]*$/, "");
				all.push({path: f.fullPath, size: f.size, title: title, artist: f.artist || "",
					searchKey: f.searchKey || ((f.artist || "") + "\t\t\t\t" + title)});
			}
		}
		// AudioPicker's orderBy "title".
		all.sort(function (a, b) { var x = a.title.toLowerCase(), y = b.title.toLowerCase(); return x < y ? -1 : x > y ? 1 : 0; });
		this.all = all;
		this.filter();
	},
	// db8's search on the searchKey: a word of it starting with what was typed.
	filter: function() {
		var s = this.filterString && this.filterString.toLowerCase(), out = [];
		for (var i = 0; i < this.all.length; i++) {
			var r = this.all[i];
			if (s) {
				var words = r.searchKey.toLowerCase().split(/\s+/), hit = false;
				for (var w = 0; w < words.length && !hit; w++) { hit = words[w].indexOf(s) === 0; }
				if (!hit && r.searchKey.toLowerCase().indexOf(s) !== 0) { continue; }
			}
			out.push(r);
		}
		this.files = out;
		var empty = this.all.length === 0 && !this.filterString;
		this.$.empty.setShowing(empty);
		this.$.searchField.setShowing(!empty);
		this.$.list.setShowing(!empty);
		this.$.list.punt();
	},
	listSetupRow: function(inSender, inIndex) {
		var r = this.files[inIndex];
		if (!r) { return false; }
		this.$.audioItem.setAudioFile(r);
		this.$.audioItem.addRemoveClass("enyo-first", inIndex === 0);
		return true;
	},
	processOnSearch: function(inSender, inEvent, inValue) {
		this.filterString = inValue && inValue.length ? inValue : undefined;
		this.abortAudio();
		this.filter();
	},
	processOnCancel: function() {
		this.filterString = undefined;
		this.filter();
	},
	fileClick: function(inSender, inEvent) {
		this.abortAudio();
		var f = this.files[inEvent.rowIndex];
		if (!f) { return; }
		this.doAudioFileSelect([{fullPath: f.path, size: f.size, name: f.title, attachmentType: "audio"}]);
	},
	handlePlayPause: function(inSender, rowIndex) {
		var wasPlaying = this.playingRowIndex;
		if (wasPlaying != undefined && this.$.audioPlayer.getPlaying()) {
			// Audio is playing: stop it first before playing the next one.
			this.$.audioPlayer.stopAudio();
			delete this.files[wasPlaying].audioPlay;
			this.$.list.updateRow(wasPlaying);
		}
		// The same row again: that was a stop.
		if (wasPlaying === rowIndex) {
			this.playingRowIndex = undefined;
			return;
		}
		this.playingRowIndex = rowIndex;
		if (this.files[rowIndex]) {
			this.$.audioPlayer.playAudio(this.files[rowIndex].path, 0);
			this.files[rowIndex].audioPlay = {status: "play"};
			this.$.list.updateRow(rowIndex);
		}
	},
	handleAudioProgressUpdate: function(inSender, status, currentTime, duration) {
		var f = this.files[this.playingRowIndex];
		if (!f) { return; }
		f.audioPlay = {status: status, progressPercentage: duration ? (currentTime / duration) * 100 : 0};
		this.$.list.updateRow(this.playingRowIndex);
		if (status === "stop") { this.playingRowIndex = undefined; }
	},
	handleDelete: function(inSender, inIndex) {
		if (this.files[inIndex]) { this.doDeleteRingtone(this.files[inIndex]); }
	},
	abortAudio: function() {
		if (this.$.audioPlayer.getPlaying()) {
			this.$.audioPlayer.stopAudio();
		}
		if (this.playingRowIndex != undefined && this.files[this.playingRowIndex]) {
			delete this.files[this.playingRowIndex].audioPlay;
			this.$.list.updateRow(this.playingRowIndex);
		}
		this.playingRowIndex = undefined;
	},
	cleanupView: function() {
		this.abortAudio();
		this.filterString = undefined;
		this.$.searchField.setValue("");
	}
});
