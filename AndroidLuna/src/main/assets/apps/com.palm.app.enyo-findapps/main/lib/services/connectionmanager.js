enyo.kind({
    name: "findApps.ConnectionManager",
    kind: enyo.Component,
    events: {
        onSuccess: "",
        onError: ""
    },
    components: [{
        name: "connMan",
        kind: "PalmService",
        service: "palm://com.palm.connectionmanager/",
        subscribe: true
    }, {
        name: "massStorageMan",
        kind: "PalmService",
        service: "palm://com.palm.bus/signal/",
        method: "addmatch",
        onSuccess: "msmSuccess",
        onFailure: "msmFailure"
    }],
    _online: undefined,
    _1x: undefined,
    _waiting: [],
    _started: false,
    gotStatus: function(inSender, inResponse) {
        this.log("connectionmanager: gotStatus: ", inResponse);
        this._1x = inResponse.wan.network == "1x" && inResponse.wifi.state != "connected";
        
		this.wanInterface = inResponse && inResponse.wan && inResponse.wan.interfaceName;
        this._stateChanged((inResponse.isInternetConnectionAvailable == true ? true : false));
        //Allow a hook into the component
        this.doSuccess(inResponse);
    },
    handleError: function(inSender, inResponse) {
        this.error("connection manager error ", inResponse);
        // In case the browser flag is enabled, don't flag an error
        if (enyo.application.onBrowser) return;
        this._stateChanged(false);
        this.doError();
    },
    getStatus: function() {
        var inProps = {
            method: "getstatus",
            onSuccess: "gotStatus",
            onFailure: "handleError"
        }
        var inParams = {
            subscribe: true
        }
        this.$.connMan.call(inParams, inProps);
    },
    _startup: function(callback) {
        this._started = true;
        var params = {
            "category": "/storaged",
            "method": "MSMProgress",
            "subscribe": true
        };
        this.$.massStorageMan.call(params);
        this.getStatus();
    },
    msmSuccess: function(inSender, inResponse) {
        this.log("connectionmanager: msmSuccess: ", inResponse);
        this._MSMnotification(inResponse);
    },
    msmError: function(inSender, inResponse) {
        this.error("msm error ", inResponse);
        this._MSMnotification(inResponse);
    },
    _MSMnotification: function(response) {
        if (!response) return;
        if (response.stage == 'attempting') {
            this._MSMmodeActive = true;
            findApps.ViewLibrary._container.$.error.cancelOfflineDialog();
        }
    },
    _stateChanged: function(online) {
        if (this._online != online) {
            this._online = online;
            var waiting = this._waiting;
            this._waiting = [];
            for (var i = 0; i < waiting.length; i++) {
                var wait = waiting[i];
                if (wait.online === undefined || wait.online === online) {
                    waiting[i].callback(online);
                } else {
                    this._waiting.push(wait);
                }
            }
            if (this._online) {
                // if internet is available we are certainly not in MSM mode
                this._MSMmodeActive = false;
            }
        }
    },
    /*
	 * Enable monitoring of network status.
	 */
    monitor: function() {
        if (!this._started) {
            this._startup();
        }
    },
    isOnline: function() {
        if (enyo.application.onBrowser) {
            return true;
        } else {
            if (this._online === undefined) {
                throw new Error("ConnectionManager is not monitoring status");
            } else {
                return this._online;
            }
        }
    },
    isOn1x: function() {
        return this._1x;
    },
    cleanup: function() {},
    destroy: function() {
        this.inherited(arguments);
    }
});
