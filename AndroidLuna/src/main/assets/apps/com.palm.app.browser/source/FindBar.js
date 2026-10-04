enyo.kind({
	name: "FindBar",
	kind: enyo.HFlexBox,
	events: {
		onFind: "",
		onGoToPrevious: "",
		onGoToNext: ""
	},
	components: [
		{kind: "Input", name: "input", flex: 2, autoCapitalize: "lowercase", changeOnKeypress: true, onchange: "inputChange"},
		{flex: 1},
		{kind: "NoFocusButton", name: "prev", caption: $L("prev"), disabled: true, onclick: "findPrevious"},
		{kind: "NoFocusButton", name: "next", caption: $L("next"), disabled: true, onclick: "findNext"},
		{kind: "NoFocusButton", caption: "done", onclick: "close"}
	],
	//* @protected
	showingChanged: function() {
		this.inherited(arguments);
		if (this.showing) {
			this.$.input.forceFocus();
		}
	},
	inputChange: function() {
		var value = this.$.input.getValue();
		var disabled = value.length < 2;
		this.$.prev.setDisabled(disabled);
		this.$.next.setDisabled(disabled);
		if (!disabled) {
			this.doFind(value);
		}
	},
	findPrevious: function() {
		this.doGoToPrevious();
	},
	findNext: function() {
		this.doGoToNext();
	},
	close: function() {
		this.log();
		this.$.input.forceBlur();
		this.hide();
	}
});
