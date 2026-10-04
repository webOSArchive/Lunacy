enyo.kind({
	name: "NoFocusButton",
	kind: "Button",
	requiresDomMousedown: true,
	//* @protected
	mousedownHandler: function(inSender, inEvent) {
		this.inherited(arguments);
		inEvent.preventDefault();
	}
});
