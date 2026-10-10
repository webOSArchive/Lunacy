/*
 * direct-install.js -- App Catalog installing a package itself on webOS, with no Preware.
 *
 * The download manager fetches the package into /media/internal/downloads; IpkInspect reads
 * its control.tar.gz; a package without install scripts goes to the stock installer,
 * com.palm.appinstaller/installNoVerify, which a com.palm.* app may call (measured on the
 * reference TouchPad, as App Catalog). A package with scripts is given back (onScripts): the
 * stock installer would install it without running them, and only Preware's root helper ran
 * those.
 *
 *   DirectInstall.run(params, report, onScripts, onError)
 *     params: appInstallService's install parameters (id, version, ipkUrl, ...)
 *     report(state, progress, extra): appInstallService's states - "ipk download current",
 *       "ipk download complete", "installing", "installed", "download failed" and
 *       "install failed" (with extra.reason, the installer's FAILED_... status)
 *     onScripts(list): the package has install scripts (list), or couldn't be read (null)
 *     onError(e): something threw in one of the install's callbacks (optional); the install
 *       stops there and reports nothing more
 *   returns {abort()}: stops the install where it is and reports nothing more - a download is
 *     cancelled and its file deleted. An install already handed to the installer runs on.
 *
 * No framework of its own, so a probe app can run it as it is (Workbench/probe in Lunacy).
 */
var DirectInstall = (function () {
    var DOWNLOAD_DIR = "/media/internal/downloads";
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

    // The downloaded package's install scripts: callback(list), or callback(null) if it
    // can't be read, which is treated as "has scripts" - the handler is the safe side.
    function readScripts(path, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", "file://" + path, true);
        xhr.overrideMimeType("text/plain; charset=x-user-defined");
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) { return; }
            var list = null;
            try {
                if (xhr.responseText) { list = IpkInspect.scripts(IpkInspect.fromBinaryString(xhr.responseText)); }
            } catch (e) {
                console.log("DIRECT-INSTALL can't read " + path + ": " + e);
            }
            callback(list);
        };
        xhr.send();
    }

    function run(params, report, onScripts, onError) {
        var ticket = null, lastPct = -1, broken = false, downloaded = false;
        var bridges = [];
        console.log("DIRECT-INSTALL installing " + params.id + " from " + params.ipkUrl);

        // The bus and XHR callbacks run outside any caller's try, so each is wrapped here.
        function guard(fn) {
            return function () {
                if (broken) { return; }
                try {
                    return fn.apply(this, arguments);
                } catch (e) {
                    broken = true;
                    console.log("DIRECT-INSTALL " + params.id + " failed: " + e);
                    // A bus callback's second argument closes its subscription.
                    if (typeof arguments[1] === "function") { try { arguments[1](); } catch (e2) {} }
                    try { forget(); } catch (e3) {}
                    if (onError) { onError(e); }
                }
            };
        }

        function forget() {
            if (ticket !== null) {
                lunaCall("palm://com.palm.downloadmanager/deleteDownloadedFile", {ticket: ticket}, function () {});
            }
        }

        report("ipk download current", 0);
        bridges.push(lunaCall("palm://com.palm.downloadmanager/download", {
            target: params.ipkUrl, targetDir: DOWNLOAD_DIR, subscribe: true
        }, guard(function (r, stop) {
            if (r.returnValue === false) {
                stop();
                report("download failed", 0, {errorCode: -5, reason: "Http error"});
                return;
            }
            if (r.ticket !== undefined && ticket === null) { ticket = r.ticket; }
            if (r.amountTotal > 0 && !r.completed) {
                var pct = Math.floor(r.amountReceived * 100 / r.amountTotal);
                if (pct !== lastPct) { lastPct = pct; report("ipk download current", pct); }
            }
            if (r.completed === false && (r.aborted || r.interrupted || r.completionStatusCode !== undefined)) {
                stop();
                report("download failed", 0, {errorCode: -5, reason: "Http error"});
                return;
            }
            if (r.completed) {
                stop();
                downloaded = true;
                if (r.completionStatusCode !== 200 && r.httpStatus !== 200) {
                    forget();
                    report("download failed", 0, {errorCode: -5, reason: "Http error"});
                    return;
                }
                var path = r.target || ((r.destPath || "") + (r.destFile || ""));
                report("ipk download complete", 100);
                readScripts(path, guard(function (scripts) {
                    if (scripts === null || scripts.length) {
                        forget();
                        onScripts(scripts);
                        return;
                    }
                    bridges.push(installFile(path, report, forget, params, guard));
                }));
            }
        }), true));

        return {
            abort: function () {
                if (broken) { return; }
                broken = true;
                console.log("DIRECT-INSTALL " + params.id + " aborted");
                for (var i = 0; i < bridges.length; i++) { try { bridges[i].cancel(); } catch (e) {} }
                if (ticket !== null && !downloaded) {
                    lunaCall("palm://com.palm.downloadmanager/cancelDownload", {ticket: ticket}, function () {});
                }
                try { forget(); } catch (e) {}
            }
        };
    }

    // com.palm.appinstaller/installNoVerify, measured on the reference TouchPad as App Catalog:
    // {returnValue, ticket, subscribed}, then status STARTING, CREATE_TMP, VERIFYING,
    // IPKG_INSTALL and SUCCESS (or a FAILED_… status).
    function installFile(path, report, forget, params, guard) {
        report("installing", 0);
        return lunaCall("palm://com.palm.appinstaller/installNoVerify", {target: path, subscribe: true}, guard(function (r, stop) {
            if (r.returnValue === false) {
                stop(); forget();
                report("install failed", 0, {reason: r.errorText || "FAILED_IPKG_INSTALL"});
                return;
            }
            if (r.status === "SUCCESS") {
                stop(); forget();
                report("installed", 100, {version: params.version});
            } else if (r.status && /^FAILED/.test(r.status)) {
                stop(); forget();
                report("install failed", 0, {reason: r.status});
            }
        }), true);
    }

    return { run: run };
}());
