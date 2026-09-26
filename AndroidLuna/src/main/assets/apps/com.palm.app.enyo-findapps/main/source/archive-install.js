/*
 * archive-install.js -- how App Catalog installs a package, on each system it runs on.
 *
 * HP's catalog installed through com.palm.appInstallService and followed the install through
 * its "status" subscription: the catalog's own progress pill ("Downloading", "Installing",
 * "Open"). That service still exists on webOS, but checks every package against HP's signing
 * servers, gone since 2015, so every install ends "install failed", FAILED_VERIFY.
 *
 *   Lunacy  -- its com.palm.appInstallService installs packages as Preware did (running their
 *              scripts), so the catalog uses its own install path as HP wrote it.
 *   webOS   -- the catalog installs a package itself: the download manager fetches it, and
 *              com.palm.appinstaller/installNoVerify (open to a com.palm.* app) installs it,
 *              reporting to the same progress pill as the appInstallService would have. The
 *              stock installer runs no install scripts, so a package that has any
 *              (IpkInspect) goes to Preware instead - and so does every install that fails
 *              or stalls at any step, from the tap on: webOS's users install with Preware,
 *              and the catalog must never be where an install fails (see webosInstall).
 *              The catalog never registers as an .ipk handler itself.
 *   other   -- LuneOS, or anywhere the above aren't there: the .ipk handler.
 *
 * The .ipk handler: whichever apps the system lists for application/vnd.webos.ipk, the active
 * one first, then the original Preware by id; each is opened with {type: "install", file,
 * target} until one opens. From a patch by a Preware 2 developer, with its LuneOS route.
 *
 * Loaded after archive-patch.js; replaces its Preware-only install.
 */
(function () {
    "use strict";

    var IPK_MIME = "application/vnd.webos.ipk";
    var FALLBACK_IPK_HANDLER = "org.webosinternals.preware";
    // Legacy webOS user agents say "webOS/1.x-2.x" or "hpwOS/3.x"; LuneOS doesn't.
    var IS_LEGACY_WEBOS = /hpwOS\/|webOS\/[1-3]\./.test(navigator.userAgent);

    // ---- the bus ----

    // An unreferenced PalmServiceBridge can be collected before it answers.
    var pending = [];

    function lunaCall(url, params, callback, keepOpen) {
        var bridge = new PalmServiceBridge();
        pending.push(bridge);
        function done() {
            var i = pending.indexOf(bridge);
            if (i >= 0) { pending.splice(i, 1); }
        }
        bridge.onservicecallback = function (msg) {
            if (!keepOpen) { done(); }
            var response;
            try { response = JSON.parse(msg); } catch (e) { response = {returnValue: false, errorText: String(msg)}; }
            callback(response, function () { done(); try { bridge.cancel(); } catch (e) {} });
        };
        bridge.call(url, JSON.stringify(params || {}));
        return bridge;
    }

    // ---- where are we? ----

    // "lunacy", "webos" or "other"; decided at load, long before anyone taps Install.
    var platform = IS_LEGACY_WEBOS ? "webos" : "other";
    if (window.PalmServiceBridge) {
        lunaCall("palm://com.palm.bus/signal/registerServerStatus", {serviceName: "org.webosarchive.lunacy"}, function (r) {
            if (r && r.connected) { platform = "lunacy"; }
            console.log("ARCHIVE-INSTALL platform: " + platform);
        });
    } else {
        platform = "other";
    }

    // ---- the .ipk handler ----

    function launchApp(id, params, callback) {
        if (IS_LEGACY_WEBOS) {
            lunaCall("palm://com.palm.applicationManager/open", {id: id, params: params}, callback);
        } else {
            // LuneOS: LunaAppManager passes launches on to SAM and then answers '"<id>" was
            // not found' even for an app it launched, so ask SAM itself.
            lunaCall("luna://com.webos.service.applicationmanager/launch", {id: id, params: params}, callback);
        }
    }

    // callback(appIds): the active handler, the alternates, then the original Preware.
    function ipkHandlerCandidates(callback) {
        lunaCall("palm://com.palm.applicationManager/listAllHandlersForMime", {mime: IPK_MIME}, function (r) {
            var ids = [], h = r && r.returnValue && r.resourceHandlers, i;
            function add(id) { if (id && ids.indexOf(id) < 0) { ids.push(id); } }
            if (h) {
                add(h.activeHandler && h.activeHandler.appId);
                for (i = 0; h.alternates && i < h.alternates.length; i++) { add(h.alternates[i].appId); }
            }
            add(FALLBACK_IPK_HANDLER);
            callback(ids);
        });
    }

    // Opens the package in the first handler that launches. onSuccess(appId), onFailure(lastResponse).
    // A handler removed since it registered fails to launch, and the next is tried.
    function openIpkWithHandler(ipkUrl, onSuccess, onFailure) {
        ipkHandlerCandidates(function (ids) {
            var n = 0;
            function next(last) {
                if (n >= ids.length) { onFailure(last); return; }
                var id = ids[n++];
                launchApp(id, {type: "install", file: ipkUrl, target: ipkUrl}, function (r) {
                    if (r && r.returnValue) {
                        console.log("ARCHIVE-INSTALL package handed to " + id);
                        onSuccess(id);
                    } else {
                        console.log("ARCHIVE-INSTALL handler " + id + " not available: " + JSON.stringify(r));
                        next(r);
                    }
                });
            }
            next({returnValue: false, errorText: "No application for .ipk files"});
        });
    }

    // The install handed to a handler app: the button resets when the catalog comes back
    // to the front (the handler's card closing), or reports failure if none would open.
    function installWithHandler(app, ipkUrl) {
        openIpkWithHandler(ipkUrl, function (handlerId) {
            console.log("ARCHIVE-INSTALL " + handlerId + " opened; waiting for onWindowActivated");
            window._archivePatchOnActivated = function () {
                var numId = app._archiveNumericId ||
                            (/^\d+$/.test(String(app.id || "")) ? String(app.id) : null);
                var palmId = app.publicApplicationId;
                // _myApps is keyed by the numeric catalog id; see archive-patch.js.
                if (numId && palmId !== numId) { app.publicApplicationId = numId; }
                app.setState("findApps.AppState.Download");
            };
        }, function (r) {
            console.log("ARCHIVE-INSTALL no .ipk handler could be opened: " + JSON.stringify(r));
            app.errorCode = "PREWARE_NOT_FOUND";
            app.setState("findApps.AppState.InstallFailed");
        });
    }

    // ---- the parameters HP's service takes ----

    // The appInstallService refuses an install with any empty string among its parameters
    // ("Bad parameter", measured on the TouchPad), so empty and missing ones are left out.
    function installParams(app) {
        // The catalog's session is archive-patch.js's DummyConfig; the service wants its token.
        var dummy = (AppCatalog.Config && AppCatalog.Config.DummyConfig) || {};
        var p = {
            catalogId: app._archiveNumericId || app.id,
            id: app.publicApplicationId,
            title: app.title,
            version: app.serverVersion,
            vendor: app.vendor,
            vendorUrl: app.vendorUrl,
            iconUrl: app.iconUrl || app.icon,
            ipkUrl: app.packageUrl,
            authToken: findApps.UserSession._token || dummy._token,
            deviceId: findApps.UserSession._deviceId || dummy._deviceId,
            email: findApps.UserSession._email || dummy._email,
            noApp: app.appType === "app" ? false : true,
            services: app.services,
            accounts: app.accounts,
            dockMode: app.dockMode,
            universalSearch: app.universalSearch,
            loc_name: app.title,
            transactionId: "" + new Date().getTime()
        };
        for (var k in p) {
            if (p.hasOwnProperty(k) && (p[k] === undefined || p[k] === null || p[k] === "")) { delete p[k]; }
        }
        if (typeof p.catalogId === "number") { p.catalogId = String(p.catalogId); }
        return p;
    }

    // ---- webOS: the catalog installs it itself, and anything that goes wrong goes to Preware ----
    //
    // webOS users have installed with Preware for fourteen years, so on webOS the catalog may
    // only ever *add* a way to install: anything that goes wrong in its own install ends in
    // Preware, never in a catalog error. Every install reaches webosInstall - the Install,
    // Update and Retry taps through AppDownload.install, and the catalog's own install calls
    // through AppInstallService.install - and there:
    //   - HP's checks before an install (embargo, location, space, connection, payment) are
    //     skipped: they were HP's store's, and any that would fail now is Preware's to report;
    //   - the package's URL comes from the app's details if the catalog hasn't got it,
    //     asked for up to DETAILS_TRIES times;
    //   - the install runs (DirectInstall), watched: no progress for STALL_MS ends it;
    //   - a throw anywhere, a download or install that fails, install scripts, no details, a
    //     stall: the install stops and the package goes to Preware, once (handToPreware).
    // The only failure the catalog shows is Preware not being there at all.

    var DETAILS_TRIES = 3;
    var STALL_MS = 90000;          // a download that stops moving
    var INSTALL_STALL_MS = 300000; // the installer, which can't be stopped once it has it

    // Reports to the catalog as appInstallService's status subscription would:
    // {id, details: {…, state, progress}}, through the download manager's own callback.
    function reporter(adm, params) {
        var details = {};
        for (var k in params) { if (params.hasOwnProperty(k)) { details[k] = params[k]; } }
        details.icon = params.iconUrl;
        return function (state, progress, extra) {
            details.state = state;
            details.progress = progress;
            if (extra) { for (var e in extra) { if (extra.hasOwnProperty(e)) { details[e] = extra[e]; } } }
            var copy = {};
            for (var c in details) { if (details.hasOwnProperty(c)) { copy[c] = details[c]; } }
            adm._appInstallServiceStatusCB(null, {id: params.id, details: copy});
        };
    }

    // The package's id, if the catalog knows it: before its details arrive an app is known by
    // its numeric catalog id only.
    function packageId(app) {
        var id = String(app.publicApplicationId || "");
        return (id && !/^\d+$/.test(id)) ? id : null;
    }

    // Preware, with the most it can be told: the package to install; else the package's page
    // from its feeds ({type: "view", id}); else Preware itself (its own launch for that is
    // {source: "updateNotification"} - an empty launch opens nothing). Preware 1.9's
    // AppAssistant.handleLaunch, read on the reference TouchPad.
    function handToPreware(app, why) {
        console.log("ARCHIVE-INSTALL handing " + (app.publicApplicationId || "?") + " to Preware: " + why);
        if (app.packageUrl) { installWithHandler(app, app.packageUrl); return; }
        var id = packageId(app);
        var params = id ? {type: "view", id: id} : {source: "updateNotification"};
        ipkHandlerCandidates(function (ids) {
            var n = 0;
            function next() {
                if (n >= ids.length) {
                    console.log("ARCHIVE-INSTALL no .ipk handler could be opened");
                    app.errorCode = "PREWARE_NOT_FOUND";
                    try { app.setState("findApps.AppState.InstallFailed"); } catch (e) {}
                    return;
                }
                var handler = ids[n++];
                launchApp(handler, params, function (r) {
                    if (!(r && r.returnValue)) { next(); return; }
                    console.log("ARCHIVE-INSTALL " + handler + " opened with " + JSON.stringify(params));
                    window._archivePatchOnActivated = function () {
                        try { app.setState("findApps.AppState.Download"); } catch (e) {}
                    };
                });
            }
            next();
        });
    }

    // The package's URL, from the app's details if the catalog hasn't got it yet.
    // go() once it is known; fail(why) if it can't be.
    function withPackage(app, go, fail) {
        if (app.packageUrl) { go(); return; }
        var numeric = app._archiveNumericId ||
                      (/^\d+$/.test(String(app.publicApplicationId || "")) ? String(app.publicApplicationId) :
                       /^\d+$/.test(String(app.id || "")) ? String(app.id) : String(app.publicApplicationId || ""));
        app._archiveNumericId = numeric;
        var attempt = 0;
        function ask() {
            attempt++;
            var scope = {got: function (s, response, req, props, errors) {
                try {
                    var detail = response && response.OutGetAppDetailV2 && response.OutGetAppDetailV2.appDetail;
                    if ((errors && errors.length) || !detail) {
                        if (attempt < DETAILS_TRIES) { setTimeout(ask, 1000 * attempt); return; }
                        fail("no details for " + numeric + " (" + errors + ")");
                        return;
                    }
                    app.updateFromServer(detail);
                    if (!app.packageUrl) { fail("details for " + numeric + " name no package"); return; }
                    go();
                } catch (e) {
                    fail(String(e));
                }
            }};
            findApps.BaseServer.getACServer().getApplicationDetails(null, numeric,
                enyo.g11n.currentLocale().toISOString(), "GDBAppDetailsSvc", true,
                {onResponse: "got", scope: scope});
        }
        ask();
    }

    function webosInstall(adm, app) {
        if (app._archiveInstalling) { return; }
        app._archiveInstalling = true;
        var over = false, job = null, stall = null;
        function end() { over = true; clearTimeout(stall); app._archiveInstalling = false; }
        function handOff(why) {
            if (over) { return; }
            end();
            if (job) { try { job.abort(); } catch (e) {} }
            try { handToPreware(app, why); } catch (e) { console.log("ARCHIVE-INSTALL handing to Preware threw: " + e); }
        }
        function watch(ms) {
            clearTimeout(stall);
            stall = setTimeout(function () { handOff("no progress for " + (ms / 1000) + " s"); }, ms);
        }
        function start() {
            var params = installParams(app);
            var report = reporter(adm, params);
            console.log("ARCHIVE-INSTALL installing " + params.id + " directly: " + params.ipkUrl);
            watch(STALL_MS);
            job = DirectInstall.run(params, function (state, progress, extra) {
                if (over) { return; }
                if (state === "download failed" || state === "install failed") {
                    handOff(state + (extra && extra.reason ? ", " + extra.reason : ""));
                    return;
                }
                if (state === "installed") {
                    // Installed: whatever the catalog's own display does now, it's done.
                    end();
                    try { report(state, progress, extra); } catch (e) { console.log("ARCHIVE-INSTALL showing installed threw: " + e); }
                    return;
                }
                watch(state === "installing" ? INSTALL_STALL_MS : STALL_MS);
                try { report(state, progress, extra); } catch (e) { handOff(String(e)); }
            }, function (scripts) {
                // Only Preware's root helper runs install scripts.
                handOff(scripts ? "install scripts: " + scripts : "package couldn't be read");
            }, function (e) {
                handOff(String(e));
            });
        }
        try {
            try { app.setState("findApps.AppState.InitiatingDownload"); } catch (e) {}
            withPackage(app, function () {
                if (over) { return; }
                try { start(); } catch (e) { handOff(String(e)); }
            }, handOff);
        } catch (e) {
            handOff(String(e));
        }
    }

    // ---- the catalog's install ----

    // The AppDownloadManager that follows installs (_appInstallServiceStatusCB). The service
    // installing is DownloadStateManager's own, whose owner is the state manager, so the
    // download manager is further up; the one the window made is the fallback.
    function downloadManager(service) {
        for (var o = service && service.owner; o; o = o.owner) {
            if (typeof o._appInstallServiceStatusCB === "function") { return o; }
        }
        return enyo.application.appdownloadManager;
    }

    // Every install tap - Install, Update, Retry - is AppDownload.install, which hands the app to
    // its state; on webOS it goes to webosInstall instead, before any of HP's checks.
    var _origAppInstall = findApps.AppDownload.prototype.install;
    findApps.AppDownload.prototype.install = function () {
        if (platform !== "webos" || !this._state || !this._state.install) {
            return _origAppInstall.apply(this, arguments);
        }
        console.log("ARCHIVE-INSTALL install tapped for " + this.publicApplicationId + " on webos");
        webosInstall(downloadManager(null), this);
    };

    // An update installed from the updates list: the same.
    var _origInstallUpdate = findApps.AppDownload.prototype.installUpdate;
    findApps.AppDownload.prototype.installUpdate = function () {
        if (platform !== "webos" || !this._state || !this._state.installUpdate) {
            return _origInstallUpdate.apply(this, arguments);
        }
        webosInstall(downloadManager(null), this);
    };

    // The catalog's own install calls (DownloadStateManager._install, the retry states) come
    // through AppInstallService.install.
    findApps.AppInstallService.prototype.install = function (app, successCb, failureCb) {
        var ipkUrl = app.packageUrl;
        console.log("ARCHIVE-INSTALL install " + app.publicApplicationId + " on " + platform + ": " + ipkUrl);
        if (platform === "webos") {
            webosInstall(downloadManager(this), app);
        } else if (!ipkUrl) {
            return;
        } else if (platform === "lunacy") {
            this.subscribe = false;
            this.call(installParams(app), {method: "install", onSuccess: successCb, onFailure: failureCb});
        } else {
            installWithHandler(app, ipkUrl);
        }
    };

    // A list row is found by its app's id to redraw it as an install moves. The row was
    // listed under the catalog's numeric id ("130"), and fetching the app's details turns the
    // same AppDownload's id into the package's ("com.example.app") before the install starts,
    // so the row was never found and stayed at "Downloading..." after the app had installed.
    // The row keeps that AppDownload, so it is matched by it as well.
    var _origSerialSearch = findApps.AppList.prototype.serialSearch;
    findApps.AppList.prototype.serialSearch = function (arr, appPublicApplicationId) {
        var i = _origSerialSearch.call(this, arr, appPublicApplicationId);
        if (i >= 0 || !arr) { return i; }
        for (i = 0; i < arr.length; i++) {
            if (arr[i] && arr[i]._appDownload && arr[i]._appDownload.publicApplicationId === appPublicApplicationId) { return i; }
        }
        return -1;
    };

    // For the catalog's own self-update prompt (archive-patch.js).
    window.archiveInstallWithHandler = function (ipkUrl) {
        openIpkWithHandler(ipkUrl, function (id) {
            console.log("ARCHIVE-INSTALL update handed to " + id);
        }, function (r) {
            console.log("ARCHIVE-INSTALL update: no .ipk handler could be opened: " + JSON.stringify(r));
        });
    };
    window.archiveInstallPlatform = function () { return platform; };
}());
