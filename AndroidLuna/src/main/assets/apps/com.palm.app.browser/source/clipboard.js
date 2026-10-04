enyo.setClipboard = function(inText) {
	var n = document.createElement("textarea");
	n.style.cssText = "position: absolute; height: 0px; width: 0px;";
	n.value = inText;
	document.body.appendChild(n);
	n.select();
	document.execCommand("cut");
	document.body.removeChild(n);
}