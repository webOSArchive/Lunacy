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
 *   DirectInstall.run(params, report, onScripts)
 *     params: appInstallService's install parameters (id, version, ipkUrl, ...)
 *     report(state, progress, extra): appInstallService's states - "ipk download current",
 *       "ipk download complete", "installing", "installed", "download failed" and
 *       "install failed" (with extra.reason, the installer's FAILED_... status)
 *     onScripts(list): the package has install scripts (list), or couldn't be read (null)
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

    function run(params, report, onScripts) {
        var ticket = null, lastPct = -1;
        console.log("DIRECT-INSTALL installing " + params.id + " from " + params.ipkUrl);

        function forget() {
            if (ticket !== null) {
                lunaCall("palm://com.palm.downloadmanager/deleteDownloadedFile", {ticket: ticket}, function () {});
            }
        }

        report("ipk download current", 0);
        lunaCall("palm://com.palm.downloadmanager/download", {
            target: params.ipkUrl, targetDir: DOWNLOAD_DIR, subscribe: true
        }, function (r, stop) {
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
                if (r.completionStatusCode !== 200 && r.httpStatus !== 200) {
                    forget();
                    report("download failed", 0, {errorCode: -5, reason: "Http error"});
                    return;
                }
                var path = r.target || ((r.destPath || "") + (r.destFile || ""));
                report("ipk download complete", 100);
                readScripts(path, function (scripts) {
                    if (scripts === null || scripts.length) {
                        forget();
                        onScripts(scripts);
                        return;
                    }
                    installFile(path, report, forget, params);
                });
            }
        }, true);
    }

    // com.palm.appinstaller/installNoVerify, measured on the reference TouchPad as App Catalog:
    // {returnValue, ticket, subscribed}, then status STARTING, CREATE_TMP, VERIFYING,
    // IPKG_INSTALL and SUCCESS (or a FAILED_… status).
    function installFile(path, report, forget, params) {
        report("installing", 0);
        lunaCall("palm://com.palm.appinstaller/installNoVerify", {target: path, subscribe: true}, function (r, stop) {
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
        }, true);
    }

    return { run: run };
}());
