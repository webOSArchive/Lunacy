// Notification Probe: banners and dashboards on demand, for measuring where a webOS phone
// puts them. A noWindow app; every launch acts on its params:
//   {"banner": n}  n banners, one after another ("text" sets the words)
//   {"dash": n}    n dashboards, each its own stage ("title", "text", "count"); tapping one
//                  opens a card and closes it, as a mail or message dashboard did
//   {"clear": true} closes every dashboard this app has open
//   {"full": true}  a card in full-screen mode (black, a line of text), to see notifications over it
//   {"alert": h}    a popup alert h px high
function AppAssistant() { this.dashes = []; this.n = 0; }

AppAssistant.prototype.handleLaunch = function (params) {
	params = params || {};
	var app = Mojo.Controller.getAppController(), i;
	if (params.clear) {
		this.dashes.forEach(function (name) { app.closeStage(name); });
		this.dashes = [];
	}
	if (params.full) {
		app.createStageWithCallback({name: "full"}, function (stage) {
			stage.document.body.style.cssText = "background: #246; color: white; font-size: 24px; margin: 0;";
			stage.document.body.innerHTML = '<div style="position: absolute; top: 0; left: 0; right: 0; height: 100%; border: 4px solid #f80; box-sizing: border-box;">Full screen probe</div>';
			(stage.window || stage.document.defaultView).PalmSystem.enableFullScreenMode(true);
		}, "card");
	}
	if (params.alert) {
		app.createStageWithCallback({name: "alert" + (++this.n), lightweight: true, height: params.alert}, function (stage) {
			stage.document.body.style.cssText = "background: #333; color: white; margin: 0;";
			stage.document.body.innerHTML = '<div style="height: ' + params.alert + 'px; border: 2px solid #0f0; box-sizing: border-box;">Probe alert</div>';
		}, "popupalert");
	}
	for (i = 0; i < (params.banner || 0); i++) {
		app.showBanner({messageText: (params.text || "Notification Probe banner") + " " + (i + 1)}, {from: "banner"}, "probe" + i);
	}
	for (i = 0; i < (params.dash || 0); i++) {
		this.openDash(params, ++this.n);
	}
};

AppAssistant.prototype.openDash = function (params, n) {
	var name = "dash" + n, title = (params.title || "Probe dashboard") + " " + n, text = params.text || "Second line of dashboard " + n;
	this.dashes.push(name);
	Mojo.Controller.getAppController().createStageWithCallback({name: name, lightweight: true}, function (stage) {
		stage.document.body.innerHTML =
			'<div class="dashboard-notification-module">' +
				'<div class="palm-dashboard-icon-container">' +
					(params.count ? '<div class="dashboard-newitem"><span>' + params.count + '</span></div>' : '') +
					'<div class="palm-dashboard-icon" style="background: url(icon.png) center center no-repeat;"></div>' +
				'</div>' +
				'<div class="palm-dashboard-text-container">' +
					'<div class="dashboard-title">' + title + '</div>' +
					'<div class="palm-dashboard-text">' + text + '</div>' +
				'</div>' +
			'</div>';
		Mojo.Event.listen(stage.document.body, Mojo.Event.tap, function () {
			var app = Mojo.Controller.getAppController();
			app.createStageWithCallback({name: "tapped" + n}, function (card) {
				card.document.body.style.cssText = "background: #246; color: white; font-size: 24px; margin: 0; padding: 20px;";
				card.document.body.innerHTML = "Opened from " + title;
			}, "card");
			app.closeStage(name);
		});
	}, "dashboard");
};
