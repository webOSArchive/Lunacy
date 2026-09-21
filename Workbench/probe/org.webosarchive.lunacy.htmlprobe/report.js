// Does this host's parser self-close <script src="..." />, as XHTML does, or swallow the
// rest of the document, as HTML does? Everything after the first such tag in index.html is
// what the answer decides: on the HTML reading, after.js, probe.css and the body's own
// markup all become that script's (ignored) text content.
//
// Reports at both levels, because webOS 2.2.4 carries only console.error to palm-log while
// 3.x carries console.log. Read with: palm-log -f org.webosarchive.lunacy.htmlprobe
function htmlprobeSay(s) {
	try { console.log("HTMLPROBE " + s); } catch (e) {}
	try { console.error("HTMLPROBE " + s); } catch (e) {}
}

function htmlprobeAsk(name, fn) {
	var v;
	try { v = String(fn()); } catch (e) { v = "threw: " + e; }
	htmlprobeSay(name + "=" + v);
	return v;
}

function htmlprobeReport() {
	htmlprobeSay("--- report ---");
	htmlprobeAsk("selfClosed", function () { return !!window.__after; });
	htmlprobeAsk("marker", function () { return !!document.getElementById("marker"); });
	htmlprobeAsk("bodyKids", function () { return document.body ? document.body.childNodes.length : "(no body)"; });
	htmlprobeAsk("links", function () { return document.getElementsByTagName("link").length; });
	htmlprobeAsk("scriptTags", function () { return document.getElementsByTagName("script").length; });
	htmlprobeAsk("bodyBackground", function () { return window.getComputedStyle(document.body).backgroundColor; });
	htmlprobeAsk("compatMode", function () { return document.compatMode; });
	htmlprobeAsk("contentType", function () { return document.contentType; });
	htmlprobeAsk("xmlVersion", function () { return document.xmlVersion; });
	htmlprobeAsk("docEl", function () { return document.documentElement.nodeName; });
	htmlprobeAsk("ns", function () { return document.documentElement.namespaceURI; });
	htmlprobeAsk("bodyHtmlHead", function () { return document.body.innerHTML.substring(0, 120); });
	htmlprobeSay("--- end ---");
	if (document.body) {
		var pre = document.createElement("pre");
		pre.style.cssText = "font:20px monospace;color:#000;background:#fff;white-space:pre-wrap";
		pre.appendChild(document.createTextNode(
			"selfClosed=" + !!window.__after + "\nmarker=" + !!document.getElementById("marker") +
			"\nbodyKids=" + document.body.childNodes.length));
		document.body.appendChild(pre);
	}
}

htmlprobeSay("report.js ran");
window.setTimeout(htmlprobeReport, 1500);
