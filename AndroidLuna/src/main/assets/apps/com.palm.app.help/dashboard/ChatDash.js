/*globals enyo */

enyo.kind({
	name: "help.ChatDash",
	kind: enyo.Control,	
	className: "chat-dashboard",
	published: {
		text: "",
		title: ""
	},
	components: [
        {kind: "HFlexBox", onclick: "clickHandler", components: [
		    {kind: "Image", src: "images/chat-icon-48.png", className: "chat-dashboard-icon"},
		    {kind: "VFlexBox", flex: 1, pack: "center", align: "start", style: "padding-left: 10px", components: [
                {kind: "Control", name: "title", className: "chat-dashboard-title"},
                {kind: "Control", name: "text", className: "chat-dashboard-text"}
            ]},
            {kind: "ApplicationEvents", onWindowParamsChange: "windowParamsChangeHandler"}
		]}
	],
	create: function() {
		this.inherited(arguments);
		this.titleChanged();
		this.textChanged();
	},
	titleChanged: function() {
		this.$.title.setContent(this.title);
	},
	textChanged: function() {
		this.$.text.setContent(this.text);
	},
	windowParamsChangeHandler: function(inSender, inEvent) {
		this.log("windowParamsChangeHandler: " + enyo.json.stringify(inEvent.params));
		if (inEvent.params.text) {
			this.setText(inEvent.params.text);
			return true;
		}
		return false;
	},
	clickHandler: function(inSender, inEvent) {
		enyo.application.openChatApp({ source: "chat" });
	}
});
