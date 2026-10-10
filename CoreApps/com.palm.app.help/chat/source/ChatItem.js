/*globals enyo */

enyo.kind({
	name: "help.ChatItem",
	kind: "Item",
	layoutKind: "HFlexLayout",
	align: "start",
	pack: "center",
	className: "chat-balloon",
	published: {
		message: null
	},
	components: [
		{kind: "Image", name: "techIcon", className: "chat-tech-icon", src: "images/chat-icon-32.png"},
		{kind: "HFlexBox", name: "message", flex: 1, components:[
			{kind: "Control", name: "messageText", allowHtml: true, flex: 1},
			{kind: "Image", name: "warningIcon", src: "images/warning-icon.png", showing: false}
		]}
	],
	create: function() {
		this.inherited(arguments);
	},
	messageChanged: function() {
		this.$.messageText.setContent(this.message.text);
		switch (this.message.source) {
		case "me":
			this.$.message.setClassName("enyo-item chat-balloon-sent");
			this.$.techIcon.hide();
			break;
		case "technician":
			this.$.message.setClassName("enyo-item chat-balloon-received");
			break;
		case "info":
			this.$.message.setClassName("enyo-item chat-balloon-error");
			this.$.techIcon.hide();
			break;
		case "error":
			this.$.message.setClassName("enyo-item chat-balloon-error");
			this.$.techIcon.hide();
			this.$.warningIcon.show();
			break;
		default:
			this.warn("Unknown message source", this.message.source);
		}
	}
});
