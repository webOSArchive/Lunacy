// What this host's engine does with the layout webOS apps were written against. Read against
// the reference TouchPad with:
//   Workbench/tp.sh 'luna-send -n 1 palm://com.palm.applicationManager/launch
//                    "{\"id\":\"org.webosarchive.lunacy.cssprobe\"}"; sleep 6;
//                    grep CSSPROBE /var/log/messages'
// and in Lunacy with Workbench/cdp.sh eval 'window.__cssprobe' org.webosarchive.lunacy.cssprobe.
//
// Reports at both levels: webOS 2.2.4 carries only console.error to palm-log, 3.x carries
// console.log as well.
(function () {
	var out = document.getElementById("out"), results = {};

	function say(s) {
		try { console.log("CSSPROBE " + s); } catch (e) {}
		try { console.error("CSSPROBE " + s); } catch (e) {}
	}
	function rec(k, v) {
		var s;
		try { s = JSON.stringify(v); } catch (e) { s = String(v); }
		results[k] = v;
		out.appendChild(document.createTextNode(k + " = " + s + "\n"));
		say(k + " = " + s);
	}
	function safe(k, f) { try { rec(k, f()); } catch (e) { rec(k, "THROWS " + e); } }

	// A hidden stage each test builds into, so nothing a test does disturbs the next one.
	function stage(html, css) {
		var d = document.createElement("div");
		d.className = "probe";
		d.style.cssText = (css || "") + ";width:1000px";
		d.innerHTML = html;
		document.body.appendChild(d);
		return d;
	}
	function drop(d) { d.parentNode.removeChild(d); }
	function w(e) { return Math.round(e.getBoundingClientRect().width * 100) / 100; }
	function h(e) { return Math.round(e.getBoundingClientRect().height * 100) / 100; }

	// --- 1. What the window says it is. An app sizes its own layout from these, and on a
	// device every one of them is the card's own pixels.
	safe("win.inner", function () { return [window.innerWidth, window.innerHeight]; });
	safe("win.outer", function () { return [window.outerWidth, window.outerHeight]; });
	safe("win.screen", function () { return [screen.width, screen.height]; });
	safe("win.avail", function () { return [screen.availWidth, screen.availHeight]; });
	safe("win.dpr", function () { return window.devicePixelRatio; });
	safe("win.client", function () { return [document.documentElement.clientWidth, document.documentElement.clientHeight]; });
	safe("win.htmlRect", function () { return [w(document.documentElement), h(document.documentElement)]; });
	safe("win.innerMatchesClient", function () { return window.innerWidth === document.documentElement.clientWidth; });

	// --- 2. Mojo's body rule: min-height 480, height 100%. Does 100% reach the viewport when
	// html's own height is auto? The card's background is painted into whatever this is.
	safe("body.offsetHeight", function () { return document.body.offsetHeight; });
	safe("body.computedHeight", function () { return getComputedStyle(document.body).height; });
	safe("html.computedHeight", function () { return getComputedStyle(document.documentElement).height; });
	safe("html.offsetHeight", function () { return document.documentElement.offsetHeight; });
	safe("body.fillsViewport", function () { return document.body.offsetHeight >= document.documentElement.clientHeight; });

	// --- 3. Anonymous table boxes. Mojo's own widget CSS puts display:table-cell children in
	// a plain block (global-dev.css: .stream-buffer-bar is a 60% table-cell), which only lays
	// out as intended if the anonymous table fills its container rather than shrinking to fit.
	safe("anonTable.pct", function () {
		var d = stage('<i style="display:table-cell;width:20%">a</i>' +
			'<i style="display:table-cell;width:60%">b</i>' +
			'<i style="display:table-cell;width:20%">c</i>');
		var k = d.children, r = [w(k[0]), w(k[1]), w(k[2])];
		drop(d);
		return r;
	});
	safe("anonTable.fillsParent", function () {
		var d = stage('<i style="display:table-cell;width:60%">b</i>');
		var r = w(d.children[0]);
		drop(d);
		return r; // 600 if the anonymous table is 1000 wide, ~10 if it shrank to the text
	});
	safe("anonTable.auto", function () {
		var d = stage('<i style="display:table-cell">a</i><i style="display:table-cell">b</i>');
		var k = d.children, r = [w(k[0]), w(k[1])];
		drop(d);
		return r;
	});


	// --- 3b. The same thing as drPodder actually writes it: Mojo's own .stream-buffer-bar
	// (a 60% table-cell, from global-dev.css) between the app's two 20% table-cells, inside a
	// plain block. On the reference TouchPad the row spans the card; the question is what
	// makes the anonymous table around those three cells take its container's width.
	safe("drPodderRow", function () {
		var d = stage('<div class="stream-info" id="pi">' +
			'<div class="elapsed">00:05</div>' +
			'<div class="stream-buffer-bar" id="sbb">' +
			'<div class="progress-content progress-widget-width"><div class="file-icon"></div><div></div></div>' +
			'<div class="stream-background progress-widget-width"></div>' +
			'<div class="stream-buffered progress-widget-width"></div>' +
			'<div class="palm-slider-button"></div></div>' +
			'<div class="duration">89:26</div></div>');
		var pi = d.firstChild, k = pi.children;
		var r = { parent: w(pi), cells: [w(k[0]), w(k[1]), w(k[2])],
			trackLeft: Math.round(k[1].getBoundingClientRect().left),
			streamBufferBarIsCell: getComputedStyle(k[1]).display };
		drop(d);
		return r;
	});
	// Whether Mojo's stylesheet reached the page at all, so the row above can be read.
	safe("mojoCssLoaded", function () {
		var d = stage('<div class="stream-buffer-bar"></div>');
		var v = getComputedStyle(d.firstChild).display;
		drop(d);
		return v === "table-cell";
	});
	// The same row with the middle cell given a wide fixed child, to see whether a cell whose
	// content wants more than the container is what pushes the table out to full width.
	safe("anonTable.wideContent", function () {
		var d = stage('<i style="display:table-cell;width:20%">a</i>' +
			'<i style="display:table-cell;width:60%"><i style="display:block;width:900px">x</i></i>' +
			'<i style="display:table-cell;width:20%">c</i>');
		var k = d.children, r = [w(k[0]), w(k[1]), w(k[2])];
		drop(d);
		return r;
	});

	// --- 4. The old flexbox, which Mojo's chrome is built on.
	safe("webkitBox.flex", function () {
		var d = stage('<i style="display:-webkit-box;width:1000px">' +
			'<i style="-webkit-box-flex:1">a</i><i style="-webkit-box-flex:3">b</i></i>');
		var k = d.children[0].children, r = [w(k[0]), w(k[1])];
		drop(d);
		return r;
	});

	// --- 5. A percentage height inside a parent whose own height is auto.
	safe("pctHeight.inAuto", function () {
		var d = stage('<i style="display:block"><i style="display:block;height:100%">x</i></i>');
		var r = h(d.children[0].children[0]);
		drop(d);
		return r;
	});

	// --- 6. Two floats that together come to a fraction of a pixel more than their container.
	// Chromium drops the second onto the next line; an app that sizes a pane from innerWidth
	// lands here whenever innerWidth is a pixel wider than the layout viewport.
	safe("floatDrop.overByAFraction", function () {
		var d = stage('<i style="display:block;float:left;width:400.4px;height:10px"></i>' +
			'<i style="display:block;float:right;width:600px;height:10px"></i>');
		var k = d.children, r = [k[0].getBoundingClientRect().top === k[1].getBoundingClientRect().top];
		drop(d);
		return r[0] ? "sameLine" : "dropped";
	});
	safe("floatDrop.exact", function () {
		var d = stage('<i style="display:block;float:left;width:400px;height:10px"></i>' +
			'<i style="display:block;float:right;width:600px;height:10px"></i>');
		var k = d.children, same = k[0].getBoundingClientRect().top === k[1].getBoundingClientRect().top;
		drop(d);
		return same ? "sameLine" : "dropped";
	});

	// --- 7. Odds and ends an app can trip over.
	safe("boxSizing.default", function () { return getComputedStyle(document.body).webkitBoxSizing || getComputedStyle(document.body).boxSizing; });
	safe("doc.compatMode", function () { return document.compatMode; });
	safe("nav.userAgent", function () { return navigator.userAgent; });


	// --- 8. background-size against what, when the background is fixed to the viewport.
	// The spec says a fixed background's positioning area is the viewport, so 100% is the
	// viewport's; this is the one place Lunacy corrects the renderer, so what the device does
	// decides whether that correction is right. Some engines report a used pixel value here,
	// which answers it without a camera; the swatches in index.html answer it either way.
	safe("bgSize.fixed", function () {
		var e = document.getElementById("sw-fixed"), cs = getComputedStyle(e);
		return [cs.backgroundSize, cs.webkitBackgroundSize, Math.round(e.getBoundingClientRect().width)];
	});
	safe("bgSize.scroll", function () {
		var e = document.getElementById("sw-scroll"), cs = getComputedStyle(e);
		return [cs.backgroundSize, cs.webkitBackgroundSize, Math.round(e.getBoundingClientRect().width)];
	});
	// A 2 x 2 image, drawn here rather than shipped so both machines get the same bytes:
	// red, green / blue, white.
	safe("bgSize.swatches", function () {
		var c = document.createElement("canvas");
		c.width = 2; c.height = 2;
		var x = c.getContext("2d");
		x.fillStyle = "#ff0000"; x.fillRect(0, 0, 1, 1);
		x.fillStyle = "#00c000"; x.fillRect(1, 0, 1, 1);
		x.fillStyle = "#0000ff"; x.fillRect(0, 1, 1, 1);
		x.fillStyle = "#ffffff"; x.fillRect(1, 1, 1, 1);
		var url = "url(" + c.toDataURL("image/png") + ")";
		document.getElementById("sw-fixed").style.backgroundImage = url;
		document.getElementById("sw-scroll").style.backgroundImage = url;
		return "drawn";
	});

	window.__cssprobe = results;
	say("--- end ---");
})();
