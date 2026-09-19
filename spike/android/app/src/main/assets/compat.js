// Lunacy spike compat layer. ES5 only: must run on Chromium 37.
// webOS WebKit delivered touches to web content as mouse events (mousedown/mousemove/mouseup
// + click). Enyo 1 and Mojo only listen for mouse events. Chromium only synthesizes them for
// taps, never drags, so recreate the webOS input model for every page.
(function () {
	var THRESHOLD = 10, st = null;
	function fire(type, t, target) {
		var e = document.createEvent("MouseEvents");
		e.initMouseEvent(type, true, true, window, 1, t.screenX, t.screenY, t.clientX, t.clientY,
			false, false, false, false, 0, null);
		target.dispatchEvent(e);
	}
	function under(t) { return document.elementFromPoint(t.clientX, t.clientY) || (st && st.target) || document.body; }
	function focusable(el) {
		for (; el && el.nodeType === 1; el = el.parentNode) {
			if (/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName) || el.isContentEditable) { return el; }
		}
		return null;
	}
	var opts = { capture: true, passive: false };  // Chromium 37 reads this as capture=true
	document.addEventListener("touchstart", function (ev) {
		if (ev.touches.length > 1) { st = null; return; }  // leave multi-touch (pinch) alone
		var t = ev.changedTouches[0];
		st = { x: t.clientX, y: t.clientY, target: t.target, moved: false };
		fire("mouseover", t, t.target);
		fire("mousedown", t, t.target);
		ev.preventDefault();
	}, opts);
	document.addEventListener("touchmove", function (ev) {
		if (!st) { return; }
		var t = ev.changedTouches[0];
		if (Math.abs(t.clientX - st.x) > THRESHOLD || Math.abs(t.clientY - st.y) > THRESHOLD) { st.moved = true; }
		fire("mousemove", t, under(t));
		ev.preventDefault();
	}, opts);
	function end(ev) {
		if (!st) { return; }
		var t = ev.changedTouches[0], target = under(t);
		fire("mouseup", t, target);
		if (!st.moved && ev.type === "touchend") {
			var f = focusable(st.target);
			if (f) { f.focus(); } else if (document.activeElement && document.activeElement !== document.body && focusable(document.activeElement)) { document.activeElement.blur(); }
			fire("click", t, st.target);
		}
		fire("mouseout", t, target);
		st = null;
		ev.preventDefault();
	}
	document.addEventListener("touchend", end, opts);
	document.addEventListener("touchcancel", end, opts);
})();
