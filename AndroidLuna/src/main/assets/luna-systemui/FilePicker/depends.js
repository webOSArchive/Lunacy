// Palm's FilePicker as the reference TouchPad loads it (its depends.js, in its order), from
// palm/ unchanged; then Lunacy's ringtone picker (source/RingtonePicker.js), which
// filepicker.html opens for the ringtone type. See NOTICE.
enyo.depends(
	"css/RingtoneItem.css",
	"css/ImageAlbumList.css",
	"css/ImageAlbum.css",
	"css/MiscFilePicker.css",
	"css/AudioPicker.css",
	"palm/FPMainView.js",
	"palm/AlbumGridView.js",
	"palm/MiscFilePicker.js",
	"palm/MiscFileItem.js",
	"palm/AudioPicker.js",
	"palm/AudioItem.js",
	"palm/RingtonePicker.js",
	"palm/ImageAlbumList.js",
	"palm/ImageFullView.js",
	"palm/ImageCropView.js",
	"palm/ImagePicker.js",
	"palm/VideoPicker.js",
	"palm/VideoAlbumList.js",
	"palm/FilePickerApp.js",
	"../../utils/utils.js",
	"../../utils/FileFormatter.js",
	"source/RingtonePicker.js"
);
