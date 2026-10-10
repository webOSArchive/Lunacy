/**
 * Chat Application
 * 
 * This is the main application for the LogMeIn chat client.
 * It uses the LmiChatEnyo component and has a few panes displayed
 * in this order: login -> connecting -> waiting -> chat
 * If an error occurs in any pane except chat, the error pane is shown
 * and the user can try again to connect via the login pane.
 * If the network is down before the chat pane, the noConnection pane 
 * is displayed. It is removed and the login pane is shown when a network 
 * connection is available.
 * Any errors that occur while the chat pane is up will be shown in the chat
 * as an error item. 
 * 
 * Copyright 2011 HP, Inc.  All rights reserved.
 */
enyo.kind({
	name: "ChatApp",
	kind: "VFlexBox",
	minimized: false,
	statics: {
		State: { 
			LOGIN:			0, 
			CONNECTING:		1,
			WAITING:		2,
			CHATTING:		3,
			UNAVAILABLE:	4,
			NOCONNECTION:	5
		},
		MAX_RECONNECT: 6
	},	
	components: [
		{kind: "Toolbar", className: "enyo-toolbar-light", align: "center", pack: "center", components: [
			{kind: "Image", src: "images/chat-icon-48.png", style: "margin: -12px 10px"},
			{kind: "Control", content: $L("webOS Mobile Chat")},
			{kind: "Button", name: "endChat", style: "position: absolute; right: 8px; top: 8px; margin: 0px", caption: $L("End Chat"), onclick: "handleEndSessionButton", showing: false},
		]},
		{kind: "Pane", name: "pane", flex: 1, components: [
			{kind: "Scroller", name: "noConnection", flex: 1, components: [
				{kind: "VFlexBox", flex: 1, pack: "center", align: "center", style: "color: grey;", components: [
					{kind: "Image", src: "../images/no-internet.png"},
					{kind: "Control", content: $L("No Network Connection")},
					{kind: "Button", content: $L("Help"), onclick: "showHelp", style: "margin-top: 20px; width: 240px"}
				]}
			]},
			{kind: "Scroller", name: "login", flex: 1, components: [
				{kind: "VFlexBox", flex: 1, className: "chat-login", components: [
					{kind: "Control", className: "chat-login-text top", content: $L("Quick, simple, secure.")},
					{kind: "Control", className: "chat-login-text bottom", content: $L("Communicate directly with a support agent in real time.")},
					{kind: "RowGroup", caption: $L("webOS Account Email"), components: [
						{kind: "Input", name: "email", hint: $L("Tap here to enter your email"), inputType: "email", selectAllOnFocus: true, onchange: "handleEmailChange", onkeydown: "handleEmailKey", changeOnInput: true, autoCapitalize: "lowercase", autocorrect: false, spellcheck: false}
					]},
					{kind: "ActivityButton", className: "chat-button", name: "chatButton", caption: $L("Connect"), onclick: "handleChatButton", disabled: true, align: "center", pack: "center"},
					{kind: "Control", allowHtml: true, className: "chat-terms-text", name: "terms"},
					{kind: "Control", allowHtml: true, className: "chat-debug-info", name: "chatDebugInfo", showing: false}
				]}
			]},
			{kind: "Scroller", name: "waiting", flex: 1, components: [
				{kind: "VFlexBox", flex: 1, pack: "center", align: "center", className: "chat-waiting", components: [
					{kind: "Image", src: "images/chat-icon-256.png"},
					{kind: "Control", name: "text1", allowHtml: true, content: $L("All agents are helping other customers.")},
					{kind: "Control", name: "text2", allowHtml: true},
					{kind: "Control", name: "text3", allowHtml: true, content: $L("To keep your place in line, please do not dismiss this card.")}
				]}
		    ]},
		    {kind: "Scroller", name: "hoursOfOperation", flex: 1, components: [
				{kind: "VFlexBox", flex: 1, pack: "center", align: "center", components: [
					{kind: "Image", src: "images/chat-icon-256.png"},
					{kind: "Control", name: "hoursText", allowHtml: true, content: $L("All agents are busy."), className: "chat-hours-text"}					
				]}
		    ]},
			{kind: "VFlexBox", name: "chat", flex: 1, components: [
				{kind: "PopupSelect", name: "chatItemPopup", onSelect: "handlePopupMenuSelect", components: [
					{caption: $L("Copy Text"), value: "copy-cmd"}
				]},
				{kind: "Scroller", name: "chatScroller", flex: 1, style: "background-color: white", components: [
					{kind: "VirtualRepeater", name: "chatList", className: "chat-list", flex: 1, onSetupRow: "chatGetRow", components: [
						{kind: "help.ChatItem", name: "chatItem", onclick: "handleChatItemClick"}
					]}
				]},
				{kind: "VFlexBox", components: [
					{kind: "Control", domStyles: {"border-top": "1px solid lightgrey", "border-bottom": "1px solid white"}},
					{kind: "Control", name: "chatStatus", className: "chat-status", content: $L("Waiting for an agent...")},
					{kind: "Toolbar", name: "chatToolbar", showing: false, className:"enyo-toolbar-light conversation-bottom", components: [
						{kind: "RichText", name: "chatField", hint: $L("Enter message here..."), onkeydown: "handleChatKeyDown",  alwaysLooksFocused:true, flex: 1}
					]}
				]}
			]}
		]},
		{kind: "ModalDialog", name: "errorDialog", caption: $L("Chat Connection Error"), components: [
  			{kind: "Control", domStyles: {"border-top": "1px solid silver", "border-bottom": "1px solid white"}},
  			{kind: "HFlexBox", flex: 1, align: "center", components: [
  				{kind: "Image", src: "images/warning-icon.png", style: "margin-right: 15px; margin-left: 5px"},
  				{kind: "Control", flex: 1, content: $L("Unable to start chat session. Please try again later."), className: "enyo-paragraph"}
  			]},	
  			{kind: "Button", caption: $L("OK"), flex: 1, onclick: "onErrorOK"}
  		]},
		{kind: "ModalDialog", name: "newSessionDialog", caption: $L("New Chat Session"), components: [
			{kind: "Control", domStyles: {"border-top": "1px solid silver", "border-bottom": "1px solid white"}},
			{kind: "Control", content: $L("Starting a new chat session will erase the current session data."), className: "enyo-paragraph"},
			{kind: "Control", content: $L("Are you sure you want to start a new chat session?"), className: "enyo-paragraph"},
			{kind: "HFlexBox", components: [
				{kind: "Button", caption: $L("OK"), flex: 1, onclick: "onNewSessionOK"},
				{kind: "Button", caption: $L("Cancel"), flex: 1, onclick: "onNewSessionCancel"}
			]}
		]},
		{kind: "ModalDialog", name: "endSessionDialog", caption: $L("End Chat Session"), components: [
			{kind: "Control", domStyles: {"border-top": "1px solid silver", "border-bottom": "1px solid white"}},
			{kind: "Control", content: $L("Are you sure you want to end the chat session?"), className: "enyo-paragraph"},
			{kind: "HFlexBox", components: [
				{kind: "Button", caption: $L("OK"), flex: 1, onclick: "onEndSessionOK"},
				{kind: "Button", caption: $L("Cancel"), flex: 1, onclick: "onEndSessionCancel"}
			]}
		]},
		{kind: "help.ConnectionManager", name: "connectionManager", onConnectionChange: "onConnectionChange"},
		{kind: "help.Preferences", name: "preferences", cookieName: "HelpChatPreferences"},
		{kind: "LmiChatStatusManager", name: "statusManager", onStatusChange: "handleStatusChange"},
		{kind: "WebService", name: "getJson", onResponse: "onJsonResponse"},
		{kind: "WebService", name: "getHead", method: "HEAD", onResponse: "onHeadResponse"},
		{kind: "ApplicationEvents", onWindowActivated: "windowActivatedHandler", onWindowDeactivated: "windowDeactivatedHandler",
			onWindowParamsChange: "windowParamsChangeHandler", onResize: "resizeHandler", onUnload: "unloadHandler"
		}
	],
	/**
	 * g11n Templates
	 */
	g11nWaitTime: new enyo.g11n.Template($L("Estimated wait: <strong>#{wait} min</strong>")),
	g11nTechReplyTmp: new enyo.g11n.Template($L("#{techName} says #{message}")),
	g11nTechConnect: new enyo.g11n.Template($L("You are chatting with #{techName}.")),
	g11nTechDisconnect: new enyo.g11n.Template($L("#{techName} has left the chat session.")),
	g11nTechHold: new enyo.g11n.Template($L("#{techName} has put you on hold.")),
	g11nTechTransfer: new enyo.g11n.Template($L("#{techName} is transferring you to another agent.")),
	g11nTechEnded: new enyo.g11n.Template($L("#{techName} has ended the session.")),
	g11nTerms: new enyo.g11n.Template($L("By entering chat, you agree to the <a href='#{terms}'>Terms of Use</a>.")),	
	/**
	 * Setup the application
	 */
	create: function() {
		this.inherited(arguments);
		this.online = enyo.application.online;		
		this.notificationManager = enyo.application.notificationManager;
		// Go to the login view
		this.switchToLoginView();
	},
	/**
	 * On ready, set the email address field
	 */
	ready: function() {
		this.inherited(arguments);
		// Set the email - first check the cookie
		var email = this.$.preferences.get("email");
		if (email && email.length > 0) {
			this.$.email.setValue(email);
			this.handleEmailChange();			
		} else if (enyo.application.profileAlias && enyo.application.profileAlias.length > 0) {
			this.$.email.setValue(enyo.application.profileAlias);
			this.handleEmailChange();
		}
	},
	/**
	 * Handle window parameters update
	 *  - Chat Parameters
	 */
	windowParamsChangeHandler: function(inSender, inEvent) {
		this.log("windowParamsChangeHandler: " + enyo.json.stringify(inEvent.params));
		
		if (inEvent.params.chatParams) {
			// Store the new chat parameters and set the terms & conditions
			this.chatParams = enyo.clone(inEvent.params.chatParams);
			this.$.terms.setContent(this.g11nTerms.evaluate({ "terms": this.chatParams.terms }));
			// Show debug info on the same page
			if (this.chatParams.debugInfo || !window.PalmSystem) {
				this.$.chatDebugInfo.show();
				this.$.chatDebugInfo.setContent(enyo.json.stringify(this.chatParams, undefined, 2).replace(/\n/g, "<br>"));
			}
			
			if (this.state === ChatApp.State.CHATTING && this.chatSession.hasEnded()) {
				this.$.newSessionDialog.openAtCenter();
			} else if (this.state === ChatApp.State.UNAVAILABLE || this.state === ChatApp.State.NOCONNECTION) {
				this.switchToLoginView();
			}
			return true;
		}
		return false; // Important
	},
	/**
	 * Handle window unload
	 */
	unloadHandler: function(inSender, inEvent) {
		this.endChatSession();
	},
	/**
	 * Handle window minimized/deactivated
	 */
	windowDeactivatedHandler: function(inSender) {
		this.minimized = true;
		if (this.chatSession) {
			this.chatSession.windowDeactivated();
		}
	},
	/**
	 * Handle window maximized/activated
	 */
	windowActivatedHandler: function(inSender) {
		this.minimized = false;
		enyo.application.notificationManager.hideDashboard();
		if (this.chatSession) {
			this.chatSession.windowActivated();
		}
	},
	/**
	 * Handle window resize
	 */
	resizeHandler: function() {
		this.inherited(arguments);
		this.$.chatScroller.scrollToBottom();
	},
	/**
	 * Handle connection changes 
	 */
	onConnectionChange: function(sender, online) {
		this.log(online);
		// Only check for changes
		if (this.online !== online) {
			this.online = online;
			switch (this.state) {
			case ChatApp.State.CONNECTING:
			case ChatApp.State.WAITING:
				if (!online) {
					this.switchToNoConnectionView();
				} 
				break;
			case ChatApp.State.CHATTING:
				if (!online) {
					this.chatSession.lostNetworkConnection();
				} else if (this.chatSession.isDisconnected()) {
					this.$.statusManager.update($L("Attempting reconnection..."));
					this.reconnectChatSession();
				}
				break;
			}
		}
	},
	/**
	 * Handle error dialog OK
	 */
	onErrorOK: function () {
		this.$.errorDialog.close();
		this.switchToLoginView();
	},
	/**
	 * Handle end chat button
	 */
	handleEndSessionButton: function () {
		this.$.endSessionDialog.openAtCenter();
	},
	/**
	 * Handle end session dialog OK
	 */
	onEndSessionOK: function () {
		this.$.endSessionDialog.close();
		this.endChatSession();
	},
	/**
	 * Handle end session dialog Cancel
	 */
	onEndSessionCancel: function () {
		this.$.endSessionDialog.close();
	},
	/**
	 * Handle new session dialog OK
	 */
	onNewSessionOK: function () {
		this.$.newSessionDialog.close();
		this.switchToLoginView();
	},	
	/**
	 * Handle new session dialog Cancel
	 */
	onNewSessionCancel: function () {
		this.$.newSessionDialog.close();
	},
	
	// NO CONNECTION VIEW
	
	switchToNoConnectionView: function() {
		this.endChatSession();
		this.state = ChatApp.State.NOCONNECTION;
		this.$.pane.setTransitionKind("enyo.transitions.Simple");
		this.$.pane.selectView(this.$.noConnection);
	},
	
	/**
	 * Launch the Help app with network help
	 */
	showHelp: function() {
		enyo.application.openHelpApp({ target: "no-network" });
	},
	
	// LOGIN VIEW
	
	/**
	 * Switch to the login view (fresh start)
	 */
	switchToLoginView: function () {
		this.log();
		this.reconnectCount = 0;
		// FIXME: CLEAN ME!!!!
		if (this.chatSession) {
			this.chatSession.end();
			delete this.chatSession;
		}
		// enable the chat button and the text field
		this.$.chatButton.setCaption($L("Connect"));
		this.$.chatButton.setDisabled(false);
		this.$.chatButton.setActive(false);
		this.handleEmailChange();
		// Clean the chat pane
		this.$.chatList.render();
		this.handleStatusChange("");
		// Show login pane
		this.state = ChatApp.State.LOGIN;
		this.$.pane.setTransitionKind("enyo.transitions.Simple");
		this.$.pane.selectView(this.$.login);
		// Keyboard manual OFF
		//enyo.keyboard.setManualMode(false);
	},
	/**
	 * Handle email field keys
	 */
	handleEmailKey: function(inSender, inEvent) {
		if (!this.$.chatButton.getDisabled() && inEvent.keyCode === 13) {
			inEvent.preventDefault();
			this.doLogin();
			return true;
		}
	},
	/**
	 * Handle email field changes
	 */
	handleEmailChange: function() {
		if ((/^[a-zA-z0-9\-\!\#\$\%\&\'\*\+\/\=\?\^\_\`\{\|\}\~]+([\.]?[a-zA-z0-9\-\!\#\$\%\&\'\*\+\/\=\?\^\_\`\{\|\}\~]+)*@\w+([\.\-]?\w+)*(\.\w{2,4})+$/).test(this.$.email.getValue())) {
			this.$.chatButton.setDisabled(false); // Valid email
		} else {
			this.$.chatButton.setDisabled(true); // Invalid email
		}	
		// Special 
		if (this.$.email.getValue() === "clear my cookies") {
			this.handleClearCookies();
		}
	},
	/**
	 * Start chat session
	 */
	handleChatButton: function() {
		this.chatParams.email = this.$.email.getValue();
		this.$.preferences.set({"email": this.chatParams.email});
		if (this.online) {
			this.state = ChatApp.State.CONNECTING;
			// Disable the chat button and the text field
			this.$.chatButton.setCaption($L("Connecting..."));
			this.$.chatButton.setDisabled(true);
			this.$.chatButton.setActive(true);
			this.$.email.setDisabled(true);
			// Check if we need to load some extra parameters
			if (this.chatParams.extraInfo) {
				if (this.chatParams.extraInfo.jsonUrl) {
					this.getJsonExtraInfo(this.chatParams.extraInfo.jsonUrl);
				} else if (this.chatParams.extraInfo.articleUrl) {
					this.getArticleExtraInfo(this.chatParams.extraInfo.articleUrl);
				} else {
					this.log("Humm, shouldn't be here...");
					this.startChatSession();
				}
			} else {
				this.startChatSession();
			}
		} else {
			this.switchToNoConnectionView();
		}
	},
	/**
	 * Get some extra given an application name
	 */
	getJsonExtraInfo: function(url) {
		var customRequestHeaders = {
			"X-Palm-Carrier": enyo.application.xPalmCarrier,
			"X-Palm-Locale": enyo.application.locale,
			"X-Palm-Device": enyo.application.deviceId,
			"X-Palm-Device-Model": enyo.application.deviceModel,
			"X-Palm-Carrier-Name": enyo.application.deviceInfo.carrierName,
			"X-Palm-Device-Name": escape(enyo.application.deviceInfo.modelName)
		};
		
		this.$.getJson.setUsername(enyo.application.username);
		this.$.getJson.setPassword(enyo.application.password);		
		this.$.getJson.setHeaders(customRequestHeaders);
		this.$.getJson.setUrl(url);
		this.$.getJson.call();
	},
	/**
	 * Handle JSON response
	 */
	onJsonResponse: function(inSender, inResponse, inRequest) {
		if (inRequest && inRequest.isSuccess() && typeof inResponse === "object") {
			this.log(inResponse);
			this.chatParams = enyo.mixin(this.chatParams, inResponse);
		}
		this.startChatSession();
	},
	/**
	 * Get some extra info given the article URL
	 */
	getArticleExtraInfo: function(url) {
		var customRequestHeaders = {
			"X-Palm-Carrier": enyo.application.xPalmCarrier,
			"X-Palm-Locale": enyo.application.locale,
			"X-Palm-Device": enyo.application.deviceId,
			"X-Palm-Device-Model": enyo.application.deviceModel,
			"X-Palm-Carrier-Name": enyo.application.deviceInfo.carrierName,
			"X-Palm-Device-Name": escape(enyo.application.deviceInfo.modelName)
		};
		
		this.$.getHead.setUsername(enyo.application.username);
		this.$.getHead.setPassword(enyo.application.password);		
		this.$.getHead.setHeaders(customRequestHeaders);
		this.$.getHead.setUrl(url);
		this.$.getHead.call();
	},
	/**
	 * Handle HEAD response
	 */
	onHeadResponse: function(inSender, inResponse, inRequest) {
		if (inRequest && inRequest.isSuccess()) {
			var info = {
				problem: inRequest.xhr.getResponseHeader("chat_problem"),
				channelId: inRequest.xhr.getResponseHeader("chat_channelid"),
				category: inRequest.xhr.getResponseHeader("chat_category")
			};
			this.log(info);
			this.chatParams = enyo.mixin(this.chatParams, info);
		}
		this.startChatSession();
	},
	/**
	 * Clear the chat cookies
	 */
	handleClearCookies: function() {
		this.$.preferences.clear();
		this.$.email.setValue(enyo.application.profileAlias);
		this.handleEmailChange();
	},
			
	// WAITING VIEW
	
	/**
	 * Switch to the waiting view
	 */
	switchToWaitingView: function() {
		this.updateWaitTime();		
		this.state = ChatApp.State.WAITING;
		this.$.pane.setTransitionKind("enyo.transitions.Fade");
		this.$.pane.selectView(this.$.waiting);
	},
	/**
	 * Return the estimated time text
	 */
	getWaitTimeString: function() {
		if (this.waitTime > (59 * 60)) {
			return $L("Estimated wait: <strong>1 hour or more</strong>");
		} else if (this.waitTime < (2 * 60)) {
			return $L("Estimated wait: <strong>1 minute or less</strong>");
		} else {
			var waitInMin = Math.floor(this.waitTime / 60);
			return this.g11nWaitTime.evaluate({ "wait": waitInMin });
		}
	},
	/**
	 * Update the estimated wait time info 
	 */
	updateWaitTime: function() {
		this.$.text2.setContent(this.getWaitTimeString());
		// Start wait time count down...
		this.cancelWaitTimeout();
		this.waitTimeout = setTimeout(function() {
			try {
				this.waitTime = this.waitTime - 1;
				if (this.waitTime > 0) {
					this.updateWaitTime();
				} else {
					this.waitExpired();
				}
			} catch (e) {
				this.log(e.message);
			}
		}.bind(this), 1000); // Refresh every second
	},
	/**
	 * Cancel wait timeout
	 */
	cancelWaitTimeout: function() {
		if (this.waitTimeout) {
			clearTimeout(this.waitTimeout);
			this.waitTimeout = undefined;
		}
	},
	/**
	 * Wait time has expired
	 */
	waitExpired: function() {
		this.log();
		this.cancelWaitTimeout();
		// TODO ....
	},
	
	// HOURS OF OPERATIONS VIEW
	
	/**
	 * Retrieve the hours of operation
	 */
	switchToHoursOfOperationView: function () {
		this.log();
		var customRequestHeaders = {
			"X-Palm-Carrier": enyo.application.xPalmCarrier,
			"X-Palm-Locale": enyo.application.locale,
			"X-Palm-Device": enyo.application.deviceId,
			"X-Palm-Device-Model": enyo.application.deviceModel,
			"X-Palm-Carrier-Name": enyo.application.deviceInfo.carrierName,
			"X-Palm-Device-Name": escape(enyo.application.deviceInfo.modelName)
		};
		
		this.$.getJson.setUsername(enyo.application.username);
		this.$.getJson.setPassword(enyo.application.password);		
		this.$.getJson.setHeaders(customRequestHeaders);
		this.$.getJson.setUrl(this.chatParams.hoursOfOperationUrl);
		this.$.getJson.call({}, {onResponse: "onHoursResponse"});
	},
	/**
	 * Handle hours of operation response
	 */
	onHoursResponse: function (inSender, inResponse, inRequest) {
		var text = $L("Chat is temporarily unavailable.<br>Please use the Help application to learn about your device, or try Chat again later.");		
		if (inRequest && inRequest.isSuccess() && typeof inResponse === "object") {
			text = inResponse.chatAvailabeText || text;
		}
		this.$.hoursText.setContent(text);
		this.state = ChatApp.State.UNAVAILABLE;
		this.$.pane.setTransitionKind("enyo.transitions.Fade");
		this.$.pane.selectView(this.$.hoursOfOperation);
	},
	
	// CHAT VIEW

	/**
	 * Switch to Chat View
	 */
	switchToChatView: function() {
		this.state = ChatApp.State.CHATTING;
		this.$.endChat.show();		
		this.cancelWaitTimeout();
		this.$.pane.selectView(this.$.chat);
		// Keyboard manual ON
		//enyo.keyboard.setManualMode(true);
	},
	/**
	 * Add a message item to the current chat session
	 */
	addChatMessageItem: function(message, source) {
		if (this.chatSession) {
			this.chatSession.addChatItem({ text: this.prepareHtmlText(message), source: source});
			this.$.chatList.render();
			this.$.chatScroller.scrollToBottom();
		}
	},
	/**
	 * Remove error/info items from the list
	 */
	removeChatErrorItems: function() {
		if (this.chatSession) {
			this.chatSession.removeChatErrorItems();
			this.$.chatList.render();
			this.$.chatScroller.scrollToBottom();
		}
	},
	/**
	 * Handle item popup selection 
	 */
	handlePopupMenuSelect: function(inSender, inSelected) {
		var value = inSelected.getValue();
		if (value === "copy-cmd") {
			enyo.dom.setClipboard(this.selectedMessage.text);
			this.notificationManager.showBanner($L("Selection Copied"), { status: "clipboard", source: "chat" });
		}
	},
	/**
	 * Handle item clicks
	 */
	handleChatItemClick: function(inSender, inEvent) {
		if (inEvent.target.nodeName.toUpperCase() !== "A") {
			this.selectedMessage = this.chatSession.chatItems[inEvent.rowIndex];
			this.$.chatItemPopup.openAtEvent(inEvent);
		}
	},
	/**
	 * Handle key down events from the chatField field
	 */
	handleChatKeyDown: function(inSender, inEvent) {
		if (inEvent.keyCode === 13) {
			inEvent.preventDefault();
			this.handleSendMessage();
		} else {
			this.chatSession.typing();
		}
	},
	/**
	 * Populate chat list
	 */
	chatGetRow: function (inSender, inIndex) {
		if (this.chatSession) {
			var item = this.chatSession.chatItems[inIndex];
			if (item) {
				this.$.chatItem.setMessage(item);
				return true;
			}
		}
	},	
	/**
	 * Send a text message
	 */
	handleSendMessage: function (inSender) {
		var message = this.$.chatField.getText().trim();
		if (message.length > 0) {
			this.chatSession.send(message);
			this.addChatMessageItem(message, "me");
			this.$.chatField.setValue("");
		}
	},	
	/**
	 * Called when the status bar needs to be refreshed
	 */
	handleStatusChange: function (inSender, inText) {
		this.log("Status changed: ", inText);
		this.$.chatStatus.setContent(inText);
	},	
	/**
	 * Clean up a text and format extra stuff
	 */
	prepareHtmlText: function (text) {
		// FIXME: Not sure we really need this…..Escape HTML
		text = (text && enyo.string.escapeHtml(text)) || '';
		// Replace newlines with <br>
		text =  text.replace(/\n/g, '<br>');
		// Replace nbsp; with ' '
		text = text.replace(new RegExp(String.fromCharCode(160), 'g'), ' ');
		// Format some good stuff (email, phone number, links...)
		text = enyo.string.runTextIndexer(text);
		
		return text;
	},
	/**
	 * Show chat text field
	 */
	showChatField: function() {
		this.$.chatToolbar.show();
		this.$.chatScroller.scrollToBottom();
		this.$.chatField.setValue("");
		//enyo.keyboard.show(enyo.keyboard.typeText);
		enyo.asyncMethod(this, function() {
			this.$.chatField.forceFocus();
		});
	},
	/**
	 * Hide chat text field
	 */
	hideChatField: function() {
		//enyo.keyboard.hide();
		this.$.chatToolbar.hide();
		this.$.chatField.forceBlur();
		this.$.chatScroller.scrollToBottom();
	},
	
	// Chat Event Handlers
	
	/**
	 * Start a chat session
	 */
	startChatSession: function () {
		this.log();	
		this.chatParams.name = "Me"; // Do not translate!
		this.chatSession = new LmiChatSession(this, this.chatParams);
		this.chatSession.start();
	},	
	/**
	 * Terminate the current chat session
	 */
	endChatSession: function () {
		this.log();
		this.$.endChat.hide();
		this.cancelWaitTimeout();
		this.removeChatErrorItems();
		
		if (this.chatSession && !this.chatSession.hasEnded()) {
			this.chatSession.end();
			this.onEnded();
		}
	},
	/**
	 * Try to reconnect to the chat session
	 */
	reconnectChatSession: function () {
		this.log(this.reconnectCount);
		this.reconnectCount++;
		if (this.reconnectCount > ChatApp.MAX_RECONNECT) {
			this.onError(null, "CannotReconnect");
		} else {
			this.chatSession.reconnect();
		}
	},
	/**
	 * Handle error codes
	 */
	onError: function (sessionId, err) {
		this.log(sessionId, err);
		
		switch (this.state) {
		case ChatApp.State.LOGIN:
		case ChatApp.State.CONNECTING:
		case ChatApp.State.WAITING:
			switch (err) {
			case "NoTechAvail":
			case "NoTechWorking":
			case "TrialIsOver":
			case "CompanyBlocked":
			case "MissingTechLicense":
			case "TechnicianOrCompanyDoesNotExist":
			case "InvalidParameters":
			case "SessionCouldNotBeStarted":
				this.endChatSession();
				this.switchToHoursOfOperationView();
				break;
			case "NoSuchSession":			
				this.endChatSession();
				this.chatSession.clearSessionId();
				this.reconnectChatSession();
				break;
			case "UnknownConnectionError":
				if (this.online) {
					this.reconnectChatSession();
				}
				break;
			case "CannotReconnect":
			case "SessionDoesNotExist":
				this.endChatSession();
				this.$.errorDialog.openAtCenter();
				break;
			default:
				this.warn("Unhandled Error", err);	
			}
			break;
			
		case ChatApp.State.CHATTING:
			switch (err) {
			case "NoTechAvail":
			case "NoTechWorking":
			case "TrialIsOver":
			case "CompanyBlocked":
			case "MissingTechLicense":
			case "TechnicianOrCompanyDoesNotExist":
			case "InvalidParameters":
			case "SessionCouldNotBeStarted":
				this.hideChatField();
				this.endChatSession();
				this.addChatMessageItem($L("Chat is temporarily unavailable. Please try again later."), "error");
				break;
			case "NoSuchSession":
//			case "SessionDoesNotExist":
				this.hideChatField();
				this.endChatSession();
				this.addChatMessageItem($L("Chat session has expired."), "error");
				break;	
			case "UnknownConnectionError":
			case "SessionDoesNotExist":
				this.hideChatField();
				if (this.online) {
					this.$.statusManager.update($L("Connection lost. Attempting reconnection..."));
					this.reconnectChatSession();
				} else {
					this.reconnectCount = 0;
					this.$.statusManager.update($L("Internet connection not available."));
					this.addChatMessageItem($L("To resume chat, please restore your internet connection."), "error");
				}
				break;
			case "CannotReconnect":
				this.hideChatField();
				this.endChatSession();
				this.addChatMessageItem($L("Unable to reconnect chat session."), "error");
				break;
			default:
				// Unhandled errors: should be ok - we are handling the main errors
				this.warn("Unhandled Error", err);
			}
			break;
		}
	},
	/**
	 * Handle chat connection
	 */
	onConnected: function (sessionId, waitTime) {
		this.log(sessionId, waitTime);
		var wait = parseInt(waitTime, 10);	
		// Only switch view and update wait time if we are in connecting or waiting 
		if (this.state === ChatApp.State.CONNECTING || this.state === ChatApp.State.WAITING) {
			this.state = ChatApp.State.WAITING;
			this.waitTime = wait;
			this.switchToWaitingView();
			if (this.minimized) {
				this.notificationManager.showBanner(this.getWaitTimeString(), { status: "connected", source: "chat" });
			}
		}
	},
	/**
	 * Handle chat disconnection
	 */
	onEnded: function (sessionId) {
		this.log(sessionId);
		if (this.state === ChatApp.State.CHATTING) {
			var text = $L("Chat session has ended.");
			this.hideChatField();			
			this.$.endChat.hide(); // End Chat button
			this.$.statusManager.update(text); // Update status
			if (this.minimized) {
				this.notificationManager.showBanner(text, { status: "disconnected", source: "chat" });
			}
		}		
	},
	/**
	 * Handle expired session
	 */
	onExpired: function (sessionId) {
		this.log(sessionId);
		if (this.state === ChatApp.State.CHATTING && this.chatSession.hasEnded()) {
			this.addChatMessageItem($L("Your session has expired."), "error");
		} else {
			this.switchToHoursOfOperationView();
		}
	},
	/**
	 * Handle session timeout
	 */
	onTimeout: function (sessionId) {
		this.log(sessionId);
		this.onError(sessionId, "NOTECHAVAILABLE");
	},
	/**
	 * Handle chat ended by a technician 
	 */
	onTechnicianEnded: function (sessionId, technicianName, technicianId) {
		var text = this.g11nTechEnded.evaluate({ "techName": technicianName });
		this.log(text, technicianId);
		this.addChatMessageItem(text, "info");
	},
	/**
	 * Handle messages from a technician
	 */
	onTechnicianMessage: function (sessionId, technicianName, technicianId, message) {
		var text = this.g11nTechReplyTmp.evaluate({ "techName": technicianName, "message": message });
		this.log(text, technicianId);
		this.addChatMessageItem(message, "technician");
		this.$.statusManager.stopTyping();
		
		if (this.minimized) {
			this.notificationManager.showDashboard(text);
			this.notificationManager.showBanner(text, { status: "technicianMessage", source: "chat" });
		}
	},
	/**
	 * Handle the typing status from a technician
	 */
	onTechnicianTyping: function (sessionId, technicianName, technicianId, isTyping) {
		if (isTyping) {
			this.log("** onTechnicianTyping = " + technicianName + " (" + technicianId + ") is typing");			
			this.$.statusManager.techTyping(technicianName, technicianId);
		} else {
			this.$.statusManager.stopTyping();
		}
	},
	/**
	 * Handle a technician connection
	 */
	onTechnicianConnected: function (sessionId, technicianName, technicianId) {
		var text = this.g11nTechConnect.evaluate({techName: technicianName});
		this.log(text, technicianId);
		this.reconnectCount = 0;
		if (this.state === ChatApp.State.WAITING) {
			if (this.chatSession.isTechnicianLead(technicianId)) {
				this.switchToChatView();
				this.$.statusManager.update(text);
				this.showChatField();
				if (this.minimized) {
					this.notificationManager.showBanner(text, { status: "technicianConnected", source: "chat" });
				}
			}
		} else if (this.state === ChatApp.State.CHATTING) {
			this.$.statusManager.update(text);
			this.removeChatErrorItems();
			this.showChatField();
			if (this.minimized) {
				this.notificationManager.showBanner(text, { status: "technicianConnected", source: "chat" });
			}
		}
	},
	/**
	 * Handle a technician disconnection
	 */
	onTechnicianDisconnected: function (sessionId, technicianName, technicianId) {
		var text = this.g11nTechDisconnect.evaluate({techName: technicianName});
		this.log(text, technicianId);
		if (this.chatSession.isWaitingForAgent()) {
			this.addChatMessageItem(text, "info");
			this.$.statusManager.update($L("Waiting for technician..."));
			this.hideChatField();
		} else {
			this.$.statusManager.update(text);
		}
		if (this.minimized) {
			this.notificationManager.showBanner(text, { status: "technicianDisconnected", source: "chat" });
		}
	},
	/**
	 * Handle a technician leaving the chat
	 */
	onTechnicianExit: function (sessionId, technicianName, technicianId) {
		var text = this.g11nTechDisconnect.evaluate({techName: technicianName});
		this.log(text, technicianId);
		if (this.chatSession.isWaitingForAgent()) {
			this.addChatMessageItem(text, "info");
			this.$.statusManager.update($L("Waiting for technician..."));
			this.hideChatField();
		} else {
			this.$.statusManager.update(text);
		}		
		if (this.minimized) {
			this.notificationManager.showBanner(text, { status: "technicianExit", source: "chat" });
		}
	},
	/**
	 * Handle a technician putting us on hold
	 */
	onTechnicianHold: function (sessionId, technicianName, technicianId) {
		var text = this.g11nTechHold.evaluate({techName: technicianName});
		this.log(text, technicianId);
		this.$.statusManager.update(text);
		this.hideChatField();
		if (this.minimized) {
			this.notificationManager.showBanner(text, { status: "technicianHold", source: "chat" });
		}
	},
	/**
	 * Handle a technician transferring us to another technician
	 */
	onTechnicianTransfer: function (sessionId, technicianName, technicianId) {
		var text = this.g11nTechTransfer.evaluate({techName: technicianName});
		this.log(text, technicianId);
		this.$.statusManager.update(text);
		this.hideChatField();
		if (this.minimized) {
			this.notificationManager.showBanner(text, { status: "technicianTransfer", source: "chat" });
		}
	}
});
