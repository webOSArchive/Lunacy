// How long Photos' slideshow transition takes (SwipeTransition.doScene): the next photo's
// div slides in from the right in fixed 90 px steps on setTimeout(..., 1) while the one
// leaving fades. Nothing in it reads the clock, so its speed is the engine's. Run on the
// TouchPad and on Lunacy and compare; it goes on sliding until closed, to watch side by side. Also: a bare setTimeout(1) chain (the timer clamp),
// and whether the page has requestAnimationFrame. Read with: palm-log -f
// org.webosarchive.lunacy.slideprobe (webOS), or logcat.
var lines = [];
function say(s) {
	lines.push(s);
	document.getElementById("out").textContent = lines.join("\n");
	try { console.log("SLIDEPROBE " + s); } catch (e) {}
}
function now() { return new Date().getTime(); }

function photo(src) {
	var div = document.createElement("div"), img = document.createElement("img");
	div.style.cssText = "position:absolute;top:0;left:0;width:100%;height:100%";
	img.src = src;
	img.style.cssText = "width:100%;height:100%";
	div.appendChild(img);
	return div;
}

// One transition, as doScene runs it.
// When frames are drawn: requestAnimationFrame fires once a frame, so its times are when the
// screen changed, which can lag what the script asked for.
var raf = window.requestAnimationFrame || window.webkitRequestAnimationFrame;
function frames(t0) {
	var list = [], on = true;
	function tick() { if (!on) { return; } list.push(now() - t0); raf(tick); }
	if (raf) { raf(tick); }
	return { stop: function () { on = false; return list; } };
}

function transition(vp, inDiv, outDiv, done) {
	var w = vp.clientWidth, delta = w > 500 ? 90 : 18;
	var marginLeft = w - (w % delta) - delta, t0 = now(), stamps = [];
	var drawn = frames(t0);
	inDiv.style.marginLeft = marginLeft + "px";
	outDiv.style.opacity = marginLeft / w + 0.05;
	vp.appendChild(inDiv);
	function scene() {
		stamps.push(now() - t0);
		if (marginLeft > 0) {
			marginLeft = Math.max(0, marginLeft - delta);
			inDiv.style.marginLeft = marginLeft + "px";
			outDiv.style.opacity = marginLeft / w + 0.05;
			setTimeout(scene, 1);
		} else {
			inDiv.style.marginLeft = "0px";
			if (outDiv.parentNode) { outDiv.parentNode.removeChild(outDiv); }
			outDiv.style.opacity = 1;
			var end = now() - t0;
			// A few frames more, to see when the last step reached the screen.
			setTimeout(function () { done(end, stamps, w, drawn.stop()); }, 300);
		}
	}
	setTimeout(scene, 1);
}

function bareChain(n, done) {
	var t0 = now(), i = 0;
	(function step() { if (++i < n) { setTimeout(step, 1); } else { done(now() - t0); } })();
}

// Timers alone, once the page has settled: a setTimeout(1) chain, setTimeout(0), and setInterval(1).
function settled() {
	bareChain(100, function (ms) {
		say("settled setTimeout(1) x100: " + ms + " ms");
		var t0 = now(), i = 0;
		(function z() { if (++i < 100) { setTimeout(z, 0); } else {
			say("settled setTimeout(0) x100: " + (now() - t0) + " ms");
			var t1 = now(), k = 0, id = setInterval(function () {
				if (++k >= 100) { clearInterval(id); say("settled setInterval(1) x100: " + (now() - t1) + " ms"); say("done"); }
			}, 1);
		} })();
	});
}

window.onload = function () {
	// A plain page has to say it is ready, or the TouchPad keeps the startup splash over it.
	if (window.PalmSystem && PalmSystem.stageReady) { PalmSystem.stageReady(); }
	var vp = document.getElementById("vp");
	var a = photo("a.jpg"), b = photo("b.jpg");
	vp.appendChild(a);
	say("innerWidth=" + window.innerWidth + " innerHeight=" + window.innerHeight + " devicePixelRatio=" + window.devicePixelRatio);
	say("raf=" + typeof window.requestAnimationFrame + " webkitRaf=" + typeof window.webkitRequestAnimationFrame);
	bareChain(20, function (ms) {
		say("bare setTimeout(1) x20: " + ms + " ms");
		var runs = 0, from = a, to = b;
		function next() {
			// Six timed, then the timers alone; after that it goes on sliding until closed,
			// for comparing by eye with another device.
			if (runs === 6) { settled(); }
			runs++;
			transition(vp, to, from, function (ms, stamps, w, drawn) {
				if (runs <= 6) say("transition " + runs + ": " + ms + " ms, " + stamps.length + " scenes, width " + w + ", stamps " + stamps.join(",") + "; frames " + drawn.join(","));
				var t = from; from = to; to = t;
				setTimeout(next, 1500);
			});
		}
		setTimeout(next, 2000);
	});
};
