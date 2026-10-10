enyo.kind({
	name: "ProgressItem",
	kind: enyo.ProgressBar,
	className: "",
	layoutKind: "VFlexLayout",
	create: function() {
		this.inherited(arguments);
		this.$.bar.setClassName("download-progress-item-inner");
		this.$.client.setLayoutKind("HFlexLayout");
		this.$.client.addClass("enyo-progress-pill-client");
		this.$.client.addClass("download-progress-item-client");
		this.$.client.flex = 1;
		this.$.client.align = "center";
	}
});
