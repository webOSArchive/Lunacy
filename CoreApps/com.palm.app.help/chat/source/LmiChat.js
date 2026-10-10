/**
 * LogMeIn Chat Transport
 * 
 * Typical usage:
 * 
 * session = new LmiChatSession({
 *	    email: this.$.email.getValue(),
 *	    channelId: this.$.channel.getValue(),
 *      name: "Nicolas Test",
 *      problem: "A lot of problems",
 *      device: "Palm Pre Plus",
 *      carrier: "AT&T",
 *      category: "Using messaging",
 *      region: "NA",
 *      language: "en"
 * });
 * 
 * // Setup session event handlers
 * session.onError = this.onError.bind(this);
 * session.onConnected = this.onConnected.bind(this);
 * session.onEnded = this.onEnded.bind(this);
 * ...
 * 
 * // Start the session
 * session.start();
 * 
 * onConnected: function (sessionId, waitTime) {
 *      console.log("Connected! Yeah! Wait time is " + waitTime + " min");
 * }
 * 
 * // Stop the session
 * session.end();
 */

var LmiChatSession = function (owner, params) {
	enyo.log("LMI:constructor = ", params);
	this.debugTransport = true; // Set to true to see the transport messages

	/**
	 * Set the chat parameters
	 */
	this.owner = owner; // The object using us
	this.sessionId = params.sessionId; // The session ID (if any - usually unset)
	this.channelId = params.channelId; // The chat channel ID
	this.name = params.name; // The name of the user (set to Me - a.k.a. cfield0)
	this.email = params.email; // The user email address (a.k.a. cfield4)
	this.problem = params.problem; // The problem description (a.k.a. cfield1)
	this.product = params.device + "/" + params.carrier; // Device Name/Carrier - eg "Palm Pre Plus&#47;AT&#38;T" (a.k.a. cfield2)
	this.category = params.category + "|" + params.region + "|" + params.language; // Category|Region|language - eg "Using messaging (stage-help)|NA|en" (a.k.a. cfield3)
};

/**
 * Chat States
 */
LmiChatSession.NONE			= 0;
LmiChatSession.CONNECTING	= 1;
LmiChatSession.WAITING		= 2;
LmiChatSession.CONNECTED	= 3;
LmiChatSession.ONHOLD		= 4;
LmiChatSession.TRANSFERRING	= 5;
LmiChatSession.WAITFORAGENT	= 6;
LmiChatSession.DISCONNECTED	= 7;
LmiChatSession.ENDED		= 8;

LmiChatSession.prototype = {
	//@public
	/**
	 * Initialize the session data
	 */
	init: function () {
		enyo.log("LMI:init");
		
		this.chatItems = [ ]; // Messages displayed in the chat list
		this._localCache = [ ]; // Message cache (needed to re-sync with server)
		this._messageQueue = [ ]; // Messages to send
		this._technicians = { }; // Keep track of the technicians
		this._pollingInterval = 2000;  // 2s
		this._foregroundPollingInterval = 2000; //2s
		this._backgroundPollingInterval = 500; //.5s
		this._pollingWatchdogInterval = 30000; // 30s
		this._reconnectInterval = 15000; // 15s
		this._minimized = false; //window minimized state
		this._state = LmiChatSession.NONE;	
		this._lastMessage = -1;
		this._allocateXHRs();
	},
	/**
	 * Start the chat session.
	 * A connection request is initiated and onConnected will be called unless an error occurs.
	 * TODO: Fill the cfields correctly
	 */
	start: function () {
		enyo.log("LMI:start");
		this.init();
		
		var data = '<action source="client">start</action>\n';
		data += '<message type="control" source="client" text="' + this._encodeChatText(this._getUserAgent()) + '">CONNECTING</message>\n';
		data += '<channelid>' + this._encodeChatText(this.channelId) + '</channelid>\n';
		data += '<cfield0>' + this._encodeChatText(this.name) + '</cfield0>\n';
		data += '<cfield1>' + this._encodeChatText(this.problem) + '</cfield1>\n';
		data += '<cfield2>' + this._encodeChatText(this.product) + '</cfield2>\n';
		data += '<cfield3>' + this._encodeChatText(this.category) + '</cfield3>\n';
		data += '<cfield4>' + this._encodeChatText(this.email) + '</cfield4>\n';
		
		this._state = LmiChatSession.CONNECTING;
		this._sendRequest(data);
	},
	
	/**
	 * Terminate the chat session.
	 */
	end: function () {
		enyo.log("LMI:end", this.sessionId, this._state);
		if (this.sessionId && this._state !== LmiChatSession.ENDED) {
			this._state = LmiChatSession.ENDED;
			this._cancelReconnect();
			this._stopPolling();
			this._sendControl("END", null, true);
		}
	},
	
	/**
	 * Try to reconnect when a network issue o
	 * FIXME: After 10 times just give up
	 */
	reconnect: function () {
		enyo.log("LMI:reconnect", this.sessionId, this._state, this._reconnectingButServerChanged);
				
		if (this._state === LmiChatSession.DISCONNECTED) {
			this._stopPolling();
			this._state = LmiChatSession.CONNECTING;
			
			// Delete and re-create the XHRs
			this._deleteXHRs();
			this._allocateXHRs();
			this._cancelReconnect();
			
			if(this._minimized) {
				this._reconnectInterval = this.backgroundPollingRate;
			} else {
				this._reconnectInterval = 15000; // 15s
			}
			if (this.sessionId) {
				if (this._reconnectingButServerChanged) {
					this._reconnectingButServerChanged = false;
				} else {
					// Try to reconnect with a sessionId
					this._reconnectTimeout = setTimeout(function () {
						this._sendControl("CONNECTING", null, true);
					}.bind(this), this._reconnectInterval);
				}
			} else {
				// Full restart...
				this._reconnectTimeout = setTimeout(this.start.bind(this), this._reconnectInterval);
			}
		}
	},
	
	/**
	 * Inform the session that the device lost data connectivity (stop polling...)
	 */
	lostNetworkConnection: function () {
		this._errorHandler("UnknownConnectionError");
	},
	
	/**
	 * Inform the session that the window is deactivated (change polling rate...)
	 */
	windowDeactivated: function () {
		this._minimized = true;
		this._pollingInterval = this._backgroundPollingInterval;
		this._startPolling();		
	},
	
	/**
	 * Inform the session that the window is activated (change polling rate...)
	 */
	windowActivated: function () {
		this._minimized = false;
		this._pollingInterval = this._foregroundPollingInterval;
		this._startPolling();		
	},
	
	/**
	 * Erase the session ID
	 */
	clearSessionId: function () {
		this.sessionId = undefined;
	},
	
	/**
	 * Returns true if the session was terminated normally (user or technician initiated)
	 */
	hasEnded: function () {
		return this._state === LmiChatSession.ENDED;
	},
	
	/**
	 * Returns true if chat is disconnected
	 */
	isDisconnected: function () {
		return this._state === LmiChatSession.DISCONNECTED;
	},
	
	/**
	 * Returns true if we are waiting for an agent
	 */
	isWaitingForAgent: function () {
		return this._state === LmiChatSession.WAITFORAGENT;
	},
	
	/**
	 * Returns the current chat state
	 */
	getState: function () {
		return this._state;
	},
	
	/**
	 * Send a message to the connected technician.
	 * @param message	The message to send
	 */
	send: function (message) {
		enyo.log("LMI:send", this.sessionId, message);
		
		var data = '<action source="client">send</action>\n';
		data += '<message type="text" source="client">' + this._encodeChatText(message) + '</message>\n';
		
		this._sendRequest(data);
	},
	
	/**
	 * Notify the technician that you are typing a message.
	 * Call this function until done typing...
	 */
	typing: function () {
		enyo.log("LMI:typing", this.sessionId);
		
		var now, data = '<action source="client">send</action>\n';
		data += '<message type="control" source="client">TYPING</message>\n';
		
		now = new Date().getTime();
		if (!this._lastType || ((now - this._lastType) > (this._pollingInterval - 1000))) {
			this._lastType = now;
			this._sendTypingRequest(data);
		}
	},
	
	/**
	 * Get the current sessionId
	 */
	getSessionId: function () {
		return this._sessionId;
	},
	
	/**
	 * Get the name of the technician given its ID
	 */
	getTechnicianName: function (technicianId) {
		if (this._technicians[technicianId]) {
			return this._technicians[technicianId].name;
		} else {
			return null;
		}
	},
	
	/**
	 * Return true if the technician is the lead
	 */
	isTechnicianLead: function (technicianId) {
		return technicianId === this._technicianLead;
	},
	
	/**
	 * Get the technician priority
	 */
	getTechnicianLead: function () {
		return this._technicianLead;
	},
	
	/**
	 * Get the list of technicians
	 */
	getTechnicians: function () {
		return this._technicians;
	},
	
	/**
	 * Remove all error and info messages
	 */
	removeChatErrorItems: function () {
		var i, newItems = [];
		for (i = 0; i < this.chatItems.length; i++) {
			if (this.chatItems[i].source !== "error" && this.chatItems[i].source !== "info") {
				newItems.push(this.chatItems[i]);
			}
		}
		this.chatItems = newItems;
	},
	
	/**
	 * Add a message to the chat items list
	 */
	addChatItem: function (params) {
		this.removeChatErrorItems();
		this.chatItems.push(params);
	},
	
	// EVENT HANDLERS TO OVERRIDE
	
	nop: function () {
	},
	
	/**
	 * Handle errors
	 * @param sessionId The session ID
	 * @param error The error parameter (TDB)
	 */
	onError: function (sessionId, error) {
		if (this.owner && this.owner.onError) {
			this.owner.onError.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle server connection
	 * @param sessionId The session ID
	 * @param waitTime The average wait time for a technician to join
	 */
	onConnected: function (sessionId, waitTime) {
		if (this.owner && this.owner.onConnected) {
			this.owner.onConnected.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle a chat disconnect (END)
	 * @param sessionId The session ID
	 */
	onEnded: function (sessionId) {
		if (this.owner && this.owner.onEnded) {
			this.owner.onEnded.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle a chat session expired
	 * @param sessionId The session ID
	 */
	onExpired: function (sessionId) {
		if (this.owner && this.owner.onExpired) {
			this.owner.onExpired.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle client side timeout (waiting for too long)
	 */
	onTimeout: function (sessionId) {
		if (this.owner && this.owner.onTimeout) {
			this.owner.onTimeout.apply(this.owner, arguments);
		}
	},
	
	/**
	 * TODO: Called when a sent message is received
	 * @param sessionId The session ID
	 */
	onMessageReceived: function (sessionId) {
		if (this.owner && this.owner.onMessageReceived) {
			this.owner.onMessageReceived.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle messages sent by a technician
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 * @param message The message sent by the technician
	 */
	onTechnicianMessage: function (sessionId, technicianName, technicianId, message) {
		if (this.owner && this.owner.onTechnicianMessage) {
			this.owner.onTechnicianMessage.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle typing info from the technician
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 * @param isTyping True if the technician is typing
	 */
	onTechnicianTyping: function (sessionId, technicianName, technicianId, isTyping) {
		if (this.owner && this.owner.onTechnicianTyping) {
			this.owner.onTechnicianTyping.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle when a technician is joining the chat session
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 */
	onTechnicianConnected: function (tsessionId, echnicianName, technicianId) {
		if (this.owner && this.owner.onTechnicianConnected) {
			this.owner.onTechnicianConnected.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle when a technician has been disconnected from the chat session
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 */
	onTechnicianDisconnected: function (sessionId, technicianName, technicianId) {
		if (this.owner && this.owner.onTechnicianDisconnected) {
			this.owner.onTechnicianDisconnected.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle when a technician ends the chat session
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 */
	onTechnicianEnded: function (sessionId, technicianName, technicianId) {
		if (this.owner && this.owner.onTechnicianEnded) {
			this.owner.onTechnicianEnded.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle when a technician is leaving the chat session
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 */
	onTechnicianExit: function (sessionId, technicianName, technicianId) {
		if (this.owner && this.owner.onTechnicianExit) {
			this.owner.onTechnicianExit.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle when a technician is putting the chat session on hold
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 */
	onTechnicianHold: function (sessionId, technicianName, technicianId) {
		if (this.owner && this.owner.onTechnicianHold) {
			this.owner.onTechnicianHold.apply(this.owner, arguments);
		}
	},
	
	/**
	 * Handle when a technician is transferring the chat session
	 * @param sessionId The session ID
	 * @param technicianName The technician name
	 * @param technicianId The technician Id
	 */
	onTechnicianTransfer: function (sessionId, technicianName, technicianId) {
		if (this.owner && this.owner.onTechnicianTransfer) {
			this.owner.onTechnicianTransfer.apply(this.owner, arguments);
		}
	},
	
	//////////// INTERNAL //////////////
	
	/* Known error codes (old convertion):
		"NoTechAvail": "NOTECHAVAILABLE",
		"NoTechWorking": "NOTECHWORKING",
		"SessionDoesNotExist": "INVALID_PARAMETERS",
		"SessionAlreadyStarted1": "SESSIONALREADYSTARTED",
		"SessionAlreadyStarted2": "SESSIONALREADYSTARTED",
		"NoSuchSession": "ERRNOSUCHSSESSION",
		"NoSuchEntry": "ERRNOSUCHENTRY",
		"CodeDoesNotExist": "ERRCODEDOESNOTEXIST",
		"CodeExpired": "ERRCODEEXPIRED",
		"TechnicianOrCompanyDoesNotExist": "ERRNOTEXPIRED",
		"MissingTechLicense": "ERRMISSINGTECHLICENSE",
		"TrialIsOver": "ERRNOTEXPIRED",
		"CompanyBlocked": "ERRNOTEXPIRED",
		"InvalidParameters": "INVALID_PARAMETERS",
		"SessionCouldNotBeStarted": "INVALID_PARAMETERS",
		"UnknownConnectionError": "UCONNECTIONERROR"
	*/
	
	_chatUrl: "https://secure.logmeinrescue.com/InstantChat/Backend.aspx",
	
	/**
	 * Delete the XMLHttpRequest objects
	 */
	_deleteXHRs: function () {
		if (this._xhrSend) {
			this._xhrSend.abort();
			delete this._xhrSend;
		}
		if (this._xhrType) {
			this._xhrType.abort();
			delete this._xhrType;
		}
		if (this._xhrPoll) {
			this._xhrPoll.abort();
			delete this._xhrPoll;
		}
	},
	
	/**
	 * Allocate the XMLHttpRequest objects
	 */
	_allocateXHRs: function () {
		this._xhrSend = new XMLHttpRequest(); // One XMLHttpRequest for sending messages and other actions 
		this._xhrType = new XMLHttpRequest(); // One XMLHttpRequest for sending "typing" info
		this._xhrPoll = new XMLHttpRequest(); // One XMLHttpRequest for server polling
	},
	
	/**
	 * Cancel reconnection timeout
	 */
	_cancelReconnect: function () {
		if (this._reconnectTimeout) {
			clearTimeout(this._reconnectTimeout);
			this._reconnectTimeout = undefined;
		}
	},
	
	/**
	 * Pre-Process errors before notifying listener
	 */
	_errorHandler: function (error) {
		if (this._state !== LmiChatSession.ENDED) {
			this._state = LmiChatSession.DISCONNECTED;
			this._stopPolling();
			this.onError(this.sessionId, error);
		}
	},
	
	/**
	 * Wrap data into a full request.
	 */
	_wrapRequest: function (data) {
		var request = '<?xml version="1.0" encoding="utf-8" ?>\n';
		request += '<instantChat>\n';
		request += data;
		request += '<lastMessage>' + this._lastMessage + '</lastMessage>\n';		
		if (this.sessionId) {
			request += '<sessionid>' + this._encodeChatText(this.sessionId) + '</sessionid>\n';
		}
		request += '</instantChat>';
		
		if (this.debugTransport) {
			enyo.log("LMI:wrapRequest", this.sessionId, request);
		}		
		return request;
	},
	
	/**
	 * Send a control request ("SETLANGUAGE", "END", ...)
	 */
	_sendControl: function (control, param, sync) {
		var data = '<action source="client">send</action>\n';
		data += '<message type="control"';
		if (param) {
			data += ' param="' + param + '"';
		}
		data += ' source="client">' + control + '</message>\n';
        this._sendRequest(data, sync);
    },

	
	/**
	 * Send a request/action to the server
	 */
	_sendRequest: function (data, sync) {
		// Add message to queue (sync messages are high-priority!)		
		if (data) {
			if (sync) {
				this._messageQueue.unshift({ data: data, sync: true});
			} else {
				this._messageQueue.push({ data: data, sync: false});
			}
		} else if (this._messageQueue.length === 0) {
			enyo.warn("XHRSend Send - Emtpy message queue");
			return;			
		}
		
		// Check the current message status
		if (this._xhrSend.readyState === 0 || this._xhrSend.readyState === 4) {
			var message = this._messageQueue[0];
			// Sending the request
			this._xhrSend.open('POST', this._chatUrl, !message.sync);
			this._xhrSend.setRequestHeader('Content-Type', 'text/xml');
			this._xhrSend.onreadystatechange = this._handleSendResponse.bind(this);
			try {
				this._startSendWatchdog();
				this._xhrSend.send(this._wrapRequest(message.data));
				if (message.sync) {
					this._handleSendResponse();
				}
			} catch (e) {
				this._stopSendWatchdog();
				// Remove sync messages - they are automatically resent when needed
				if (message.sync) {
					this._messageQueue.shift();
				}
				enyo.error("XHRSend Send Exception", this.sessionId, e.message);
				this._errorHandler("UnknownConnectionError");
			}
		}
	},
	
	// Start send watchdog
	_startSendWatchdog: function () {
		this._stopSendWatchdog();
		this._sendWatchdog = setTimeout(this._handleSendWatchdog.bind(this), this._pollingWatchdogInterval);
	},
	
	// Stop send watchdog
	_stopSendWatchdog: function () {
		if (this._sendWatchdog) {
			clearTimeout(this._sendWatchdog);
			this._sendWatchdog = undefined;
		}
	},
	
	// Handle polling watchdog
	_handleSendWatchdog: function () {
		this._stopSendWatchdog();
		enyo.error("Send Watchdog triggered", this.sessionId);
		this._errorHandler("UnknownConnectionError");
		this._xhrSend.abort(); // Do we really need it? reconnect will also abort everything...
	},
	
	/**
	 * Handle Send response
	 */
	_handleSendResponse: function () {
		this._stopSendWatchdog();
		if (this._xhrSend && this._xhrSend.readyState === 4) {
			if (this._xhrSend.status === 200) {
				enyo.log("Handle Send Response");
				this._messageQueue.shift();
				this._processXMLResponse(this._xhrSend.responseXML, true);
				if (this._messageQueue.length > 0) {
					this._sendRequest(); // No params = process next message in queue
				}
			} else {
				enyo.error("XHRSend Response Error", this.sessionId, this._xhrSend.statusText);
				this._errorHandler("UnknownConnectionError");
			}
		}
	},
	
	// Send a typing request
	_sendTypingRequest: function (data) {
		this._xhrType.open('POST', this._chatUrl, true);
		this._xhrType.setRequestHeader('Content-Type', 'text/xml');
		this._xhrType.onreadystatechange = this.nop;
		try {
			this._xhrType.send(this._wrapRequest(data));
		} catch (e) {
			enyo.error("XHRType Send Exception", this.sessionId, e.message);
			//this._errorHandler("UnknownConnectionError"); // Don't worry too much about the typing issues
		}
	},
	
	/**
	 * Get messages from the server - better have a session ID at this point!
	 */
	_getMessages: function () {
		var data = '<action source="client">get</action>';
		this._xhrPoll.open('POST', this._chatUrl, true);
		this._xhrPoll.setRequestHeader('Content-Type', 'text/xml');
		this._xhrPoll.onreadystatechange = this._handlePollResponse.bind(this);
		try {
			this._startPollWatchdog();
			this._xhrPoll.send(this._wrapRequest(data));
		} catch (e) {
			this._stopPollWatchdog();
			enyo.error("XHRPoll Send Exception", this.sessionId, e.message);
			this._errorHandler("UnknownConnectionError");
		}
	},
	
	_handlePollResponse: function () {
		this._stopPollWatchdog();
		if (this._xhrPoll && this._xhrPoll.readyState === 4) {
			if (this._xhrPoll.status === 200) {
				enyo.log("Handle Poll Response");
				this._processXMLResponse(this._xhrPoll.responseXML, false);
			} else {
				enyo.error("XHRPoll Response Error", this.sessionId, this._xhrPoll.statusText);
				this._errorHandler("UnknownConnectionError");
			}
			
			// Restart polling unless it is stopped
			if (this._polling) {
				this._polling = setTimeout(this._getMessages.bind(this), this._pollingInterval);
			}
		}
	},

	// Start the response polling
	_startPolling: function () {
		this._stopPolling();
		if (this.sessionId) {
			this._polling = setTimeout(this._getMessages.bind(this), this._pollingInterval);
		}
	},
	
	// Stop polling
	_stopPolling: function () {
		this._stopPollWatchdog();
		if (this._polling) {
			clearTimeout(this._polling);
			//this._xhrPoll.abort();
			this._polling = undefined;
		}
	},
	
	// Start polling watchdog
	_startPollWatchdog: function () {
		this._stopPollWatchdog();
		if (this._state !== LmiChatSession.ENDED) { 
			this._pollingWatchdog = setTimeout(this._handlePollingWatchdog.bind(this), this._pollingWatchdogInterval);
		}
	},
	
	// Stop polling watchdog
	_stopPollWatchdog: function () {
		if (this._pollingWatchdog) {
			clearTimeout(this._pollingWatchdog);
			this._pollingWatchdog = undefined;
		}
	},
	
	// Handle polling watchdog
	_handlePollingWatchdog: function () {
		this._stopPollWatchdog();
		enyo.error("Poll Watchdog triggered", this.sessionId);
		this._errorHandler("UnknownConnectionError");
	},
	
	// End the session: stop polling, clean up sessionId, abort XHRs and send onEnded
	_endSession: function () {
		enyo.log("LMI:_endSession");
		this._state = LmiChatSession.ENDED;		
		this._stopPolling();
		this._deleteXHRs();
		this._cancelReconnect();
		this.onEnded(this.sessionId);
	},
		
	/**
	 * Handle the XML response from a request to the server (send/poll)
	 */
	_processXMLResponse: function (xmlResponse, checkEmptyResponse) {
		var i, messages, sessionId, avgPickupTime, lastMessage, techId;
		if (xmlResponse) {
			if (this.debugTransport) {
				enyo.log("LMI:processXMLResponse", this.sessionId, new XMLSerializer().serializeToString(xmlResponse));
			}
			
			// Check for <instantChat>
			messages = xmlResponse.getElementsByTagName("instantChat");
			if (messages.length !== 1) {
				this._errorHandler("InvalidParameters");
				return;
			}
			
			// Check empty send replies - try to see if a re-sync works
			if (checkEmptyResponse && messages[0].childNodes.length === 0) {
				this._refreshServerSideCache();
				return;
			}
			
			// Check for errors
			messages = xmlResponse.getElementsByTagName("error");
			if (messages.length > 0) {
				this._errorHandler(messages[0].firstChild.nodeValue || "InvalidParameters");
			} else {
				// Handle <action>
				messages = xmlResponse.getElementsByTagName("action");
				for (i = 0; i < messages.length; i++) {
					if (messages[i].firstChild.nodeValue === "get") {
						this._refreshServerSideCache();
						return; // Don't go any further for now
					}
				}
				
				// Handle <serverTime>
				messages = xmlResponse.getElementsByTagName("serverTime");
				if (messages.length > 0) {
					sessionId = messages[0].getAttribute("newwebsessionid");
					avgPickupTime = messages[0].getAttribute("avgpickuptime");					
					// Get the server time
					for (i = 0; i < messages.length; i++) {
						this._serverTime = new Date(messages[i].firstChild.nodeValue);
					}
					// Update the sessionId and pickup time
					if (sessionId) {
						this.sessionId = sessionId;
					}
					if (avgPickupTime) {
						this._avgPickupTime = avgPickupTime;
					}
					
					enyo.log("LMI:servertime", this.sessionId, this._avgPickupTime, this._serverTime);
				}
				
				// Handle <TCTyping> before <message>
				messages = xmlResponse.getElementsByTagName("TCTyping");
				if (messages.length > 0) {
					techId = messages[0].getAttribute("collab");
					this.onTechnicianTyping(this.sessionId, this.getTechnicianName(techId), techId, true);
				} else {
					this.onTechnicianTyping(this.sessionId, null, null, false);
				}
				
				// Handle <message>
				messages = xmlResponse.getElementsByTagName("message");
				for (i = 0; i < messages.length; i++) {
					this._copyToLocalCache(messages[i]);
					lastMessage = messages[i].getAttribute('id');
					if (lastMessage - this._lastMessage === 1) {
						this._lastMessage = lastMessage;
						
						if (messages[i].getAttribute("type") === "control") {
							this._processControlMessage(messages[i]);
						} else {
							this._processTextMessage(messages[i]);
						}
					}
				}
			}
		}
	},
	
	/**
	 * Copy message to the current cache
	 */
	_copyToLocalCache: function (message) {
		this._localCache.push({
			type: message.getAttribute("type"),
			time: message.getAttribute("time"),
			source: message.getAttribute("source"),
			sourceName: message.getAttribute("sourceName"),
			message: message.firstChild.nodeValue,
			text: message.getAttribute("text"),
			param: message.getAttribute("param"),
			collab: message.getAttribute("collab")
		});
	},
	
	/**
	 * Process control messages
	 */
	_processControlMessage: function (message) {
		var msgSource = message.getAttribute("source"),
			msgValue = message.firstChild.nodeValue;
		
		if (this.debugTransport) {
			enyo.log("LMI:processControlMessage", this.sessionId, new XMLSerializer().serializeToString(message));
		}
			
		switch (msgSource) {
		case "tc":
			var collabParams = message.getAttribute("collab") ? message.getAttribute("collab").split(":") : [ ],
				technicianName = message.getAttribute("sourceName"),
				technicianId = collabParams[0],
				technicianPriority = parseInt(collabParams[1] || "0", 10),
				msgParam = message.getAttribute("param");
			
			if (msgParam === "1") {
				return; // Drop private messages (why send them in the first place???)
			}
			
			switch (msgValue) {
			case "CONNECTED":
				//this._pollingInterval = 2 * 1000;				
				if(this._minimized) {
					this._pollingInterval = this._backgroundPollingInterval; //.5s
				} else {
					this._pollingInterval = this._foregroundPollingInterval; //2s
				}
				if (technicianPriority !== 0) {
					// We have a new leader!
					this._technicianLead = technicianId;
					this._state = LmiChatSession.CONNECTED;
					this._startPolling();
				}						
				this._technicians[technicianId] = { name: technicianName, id: technicianId };
				this.onTechnicianConnected(this.sessionId, technicianName, technicianId);
				break;
			case "DISCONNECTED":
				if (technicianId === this._technicianLead) {
					this._state = LmiChatSession.WAITFORAGENT;
					this._pollingInterval = 4 * 1000;
				}
				delete this._technicians[technicianId];
				this.onTechnicianDisconnected(this.sessionId, technicianName, technicianId);
				break;
			case "HOLD":
				if (technicianId === this._technicianLead) {
					this._state = LmiChatSession.ONHOLD;
					this.onTechnicianHold(this.sessionId, technicianName, technicianId);
				}
				break;
			case "TRANSFER":
				if (technicianId === this._technicianLead) {
					this._state = LmiChatSession.TRANSFERRING;
					this.onTechnicianTransfer(this.sessionId, technicianName, technicianId);
				}
				break;
			case "END":
				if (technicianId && technicianId === this._technicianLead) {
					this.onTechnicianEnded(this.sessionId, technicianName, technicianId);
					this._endSession();
				} else if (!technicianId) {
					this.onExpired(this.sessionId);
					this._endSession();
				}
				break;
			case "EXIT":
				if (technicianId === this._technicianLead) {
					this._state = LmiChatSession.WAITFORAGENT;
					this._pollingInterval = 4 * 1000;
				}
				delete this._technicians[technicianId];
				this.onTechnicianExit(this.sessionId, technicianName, technicianId);
				break;
			case "URLPUSH":
				if (msgParam) {
					this.onTechnicianMessage(this.sessionId, technicianName, technicianId, this._decodeParamText(msgParam));
				}
				break;
			// Actions below are not supported yet
			//case "START_APPLET":
			//case "SHOWSURVEY":
			default:
				enyo.warn("LMI:TechnicianControl", this.sessionId, msgValue, "not implemented"); 
				break;
			}
			break;
		case "client":
			//var params = message.getAttribute("param").split(":"); // Be careful split will fail on null!
				
			switch (msgValue) {
			case "CONNECTING":
				// param contains the current server  eg "www02-18.logmeinrescue.com"
				if (this._state === LmiChatSession.CONNECTING) {
					this._sendControl("CONNECTED");
				}
				break;
			case "CONNECTED":
				// param contains the company name + extra... eg " palm:388598:1:1:null:0:1:0:0"
				// 1: Company name
				// 2: companyId
				// 3: If "1", company logo is  -> /InstantChat/ResourceEngine.aspx?companyid=" + companyId + "&type=logo
				// 4: If "1", company icon is  -> /InstantChat/ResourceEngine.aspx?companyid=" + companyId + "&type=icon
				// 5: Survey URL
				// 6: If "1", trial account
				// 7: ???
				// 8: BackOffice click once flag
				//this._pollingInterval = 4 * 1000;
				if(this._minimized) {
					this._pollingInterval = this._backgroundPollingInterval; //.5s
				} else {
					this._pollingInterval = 4 * 1000;
				}
				this._startPolling();
				this._state = LmiChatSession.WAITING;
				this.onConnected(this.sessionId, this._avgPickupTime);
				break;
			case "CANNOTSTART":
			case "DISCONNECTED":
				this._errorHandler("UnknownConnectionError");
				break;
			case "END":
				this._endSession();
				break;
			case "TIMEOUT":
				this.onTimeout(this.sessionId);
				break;
			// Actions below are not supported yet
			//case "REQUESTSENTFILE":
			//case "REQUESTSENTCANCELFILE":
			//case "DISABLE_EAAB":
			default:
				enyo.warn("LMI:ClientControl", this.sessionId, msgValue, "not implemented"); 
				break;
			}
			break;
		}
	},
	
	/**
	 * Process text messages
	 */
	_processTextMessage: function (message) {
		var msgSource = message.getAttribute("source"),
			msgValue = message.firstChild.nodeValue;

		if (this.debugTransport) {
			enyo.log("LMI:processTextMessage", this.sessionId, new XMLSerializer().serializeToString(message));
		}
		
		if (msgSource === "tc") {
			var technicianId = message.getAttribute("collab").split(":")[0],
				technicianName = message.getAttribute("sourceName"),
				isPrivate = message.getAttribute("param") || "0";
			
			if (isPrivate === "0") {
				this.onTechnicianMessage(this.sessionId, technicianName, technicianId, msgValue);
			}
		} else if (msgSource === "client") {
			this.onMessageReceived(this.sessionId, msgValue);
		}
	},
	
	// Refresh server side cache
	_refreshServerSideCache: function () {
		var i;
		// Don't try to reconnect by sending a CONNECTING
		this._reconnectingButServerChanged = true;
        var data = '<action source="client" fromlocalcache="true">send</action>\n';
        // Send client cache to the server
		for (i = 0; i < this._localCache.length; i++) {
			var cachedMessage = this._localCache[i];
			if (!(cachedMessage.message === "DISCONNECTED" && !cachedMessage.time)) {
				data += '<message type="' + (cachedMessage.type || '');
				data += '" time="' + (cachedMessage.time || '');
				data += '" source="' + (cachedMessage.source || '');
				data += '" sourceName="' + this._encodeChatText(cachedMessage.sourceName);
				data += '" text="' + this._encodeChatText(cachedMessage.text);
				data += '" param="' + this._encodeChatText(cachedMessage.param);
				data += '" collab="' + (cachedMessage.collab || '');
				data += '">' + this._encodeChatText(cachedMessage.message) + '</message>\n';
			}
		}
		this._sendRequest(data);
	},
	
	// Replace special characters (from LMI ClientEngine.js)
	_encodeChatText: function (text) {
		if (typeof text === "string") {
			var i, truncatedChar, stext = "";
			for (i = 0; i < text.length; i++) {
				truncatedChar = text.charCodeAt(i);
				// Details: http://www.w3.org/TR/2000/REC-xml-20001006#NT-Char
				if ((truncatedChar === 0x9) || (truncatedChar === 0xA) || (truncatedChar === 0xD) ||
					   ((truncatedChar >= 0x20) && (truncatedChar <= 0xD7FF)) ||
					   ((truncatedChar >= 0xE000) && (truncatedChar <= 0xFFFD)) ||
					   ((truncatedChar >= 0x10000) && (truncatedChar <= 0x10FFFF))) {
					if ((truncatedChar === 60) || (truncatedChar === 62) || (truncatedChar === 47) || (truncatedChar === 38) || (truncatedChar === 34)) {
						// Add encoded character
						stext += "&#" + truncatedChar + ";";
					} else {
						// Add original character
						stext += String.fromCharCode(truncatedChar);
					}
				}
			}
			return stext;
		} else if (text) {
			return text.toString(); // Stringify....
		} else {
			return '';
		}
	},
	
	// Message parameter decoder
	_decodeParamText: function (text) {
		var iIndex, stext = text;
		stext = stext.replace(/%#59#%/g, ";");
		stext = stext.replace(/%#58#%/g, ":");
		stext = stext.replace(/%#38#%/g, "&");
		stext = stext.replace(/%#36#%/g, "$");
		stext = stext.replace(/%#60#%/g, "<");
		stext = stext.replace(/%#62#%/g, ">");
		stext = stext.replace(/%#34#%/g, '"');
		stext = stext.replace(/%#39#%/g, "'");
		stext = stext.replace(/%#13#%%#10#%/g, "<br />");
		stext = stext.replace(/%#13#%/g, "<br />");
		// Decode chars from 1 to 31
		for (iIndex = 1; iIndex < 32; iIndex++) {
			stext = stext.replace(new RegExp("%#" + iIndex + "#%", 'g'), "&#" + iIndex + ";");
		}
		return stext;
	},
	
	// Get the user agent
	_getUserAgent: function () {
		return navigator.userAgent || "";
	}
};


/**
 * Chat Status Manager
 * Handles the status messages (e.g. "Bob is typing...", "Chat disconnected")
 */

enyo.kind({
	name: "LmiChatStatusManager",
	kind: enyo.Component,
	g11nTyping: new enyo.g11n.Template($L("#{name} is typing...")),
	published: {
		status: "",
		typing: false
	},
	events: {
		onStatusChange: ""
	},
	create: function () {
		this.inherited(arguments);
	},
	statusChanged: function () {
		this.doStatusChange(this.status);
	},
	update: function (status) {
		this.oldStatus = status;
		this.setTyping(false);
		this.setStatus(status);
	},
	techTyping: function (technicianName, technicianId) {
		this.setTyping(true);
		this.setStatus(this.g11nTyping.evaluate({ "name": technicianName }));
	},
	stopTyping: function () {
		if (this.typing) {
			this.setTyping(false);
			this.setStatus(this.oldStatus);			
		}
	}
});
