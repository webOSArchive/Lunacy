enyo.kind({
	name: "VerticalAcceptCancelPopup",
	kind: "AcceptCancelPopup",
	chrome: [
		{className: "enyo-modaldialog-container", components: [
			{name: "modalDialogTitle", className: "enyo-modaldialog-title"},
			{name: "client"},
			{kind: enyo.VFlexBox, components: [
				{name: "accept", kind: "NoFocusButton", className: "enyo-button-dark", flex: 1, onclick: "acceptClick"},
				{name: "cancel", kind: "NoFocusButton", flex: 1, onclick: "cancelClick"}
			]}
		]}
	]
});
