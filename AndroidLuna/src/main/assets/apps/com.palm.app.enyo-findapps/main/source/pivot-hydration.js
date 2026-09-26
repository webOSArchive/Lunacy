// pivot-hydration.js
// Hydrates the Pivot magazine's full content from appcatalog.webosarchive.org into
// /media/internal/.pivot after install, so only a tiny placeholder edition needs to
// ship inside the IPK (main/source/magazine/defaultEdition/{lang}/).
// Must load AFTER build.js (listed last in main/depends.js), since it extends the
// compiled Magazine kind at runtime rather than editing main/build.js by hand --
// see main/source/magazine/app/Magazine.js for the original source it builds on.

(function () {
    "use strict";

    var PIVOT_BASE_URL = "https://appcatalog.webosarchive.org/pivot";
    var PIVOT_CACHE_ROOT = "/media/internal/.pivot";
    var SUPPORTED_LANGS = ["de", "en", "es", "fr", "it"];

    // Set by MagazineProto.loadDefaultEdition, read by checkAndHydrate --
    // lets the write path push a switch-to-placeholder directly onto a
    // currently-rendered Magazine the moment it decides a download is
    // starting, rather than the read side guessing. A per-launch singleton
    // is safe here: confirmed on-device that switching Magazine tabs away and
    // back reuses the same instance rather than recreating it, so at most one
    // Magazine instance ever exists per app session.
    var _pivotActiveMagazine = null;

    function resolveLang() {
        var lang = (enyo.g11n.currentLocale().toISOString()).substring(0, 2);
        if (SUPPORTED_LANGS.indexOf(lang) === -1) {
            lang = "en";
        }
        return lang;
    }

    // -----------------------------------------------------------------------
    // downloadFile -- wraps the platform's download manager via a raw
    // PalmServiceBridge (same low-level primitive archive-patch.js already
    // uses for the Preware-install call), not Enyo's PalmService kind.
    // Confirmed on-device (2026-08-19): Enyo's PalmService kind only ever
    // delivers the FIRST response of a subscribed call to onSuccess, silently
    // dropping the later push that actually carries completed:true -- the
    // download itself succeeds (visible in /var/log/messages and on disk),
    // but the queue never advances. PalmServiceBridge's onservicecallback
    // fires once per bus message, which is what a progress+completion
    // subscription actually needs.
    //
    // Actual response shapes seen on-device for palm://com.palm.downloadmanager/download:
    //   ack:      {"returnValue":true,"ticket":N,"url":...,"target":...,"subscribed":true}
    //   progress: {"ticket":N,"amountReceived":X,"amountTotal":Y,...}            (no "completed" key)
    //   final:    {"ticket":N,...,"httpStatus":200,"completed":true,"aborted":false,"target":"..."}
    // -----------------------------------------------------------------------
    function downloadFile(sourceUrl, targetDir, targetFilename, onProgress, onDone) {
        var done = false;
        var bridge = new PalmServiceBridge();
        function finish(ok) {
            if (done) { return; }
            done = true;
            // Drop the reference so nothing keeps this bridge (and its closure)
            // alive after we're done with it -- unclear whether the platform
            // caps concurrent/total PalmServiceBridge handles per app, but this
            // is cheap insurance either way.
            bridge.onservicecallback = null;
            onDone(ok);
        }
        try {
            bridge.onservicecallback = function (msg) {
                var response;
                try { response = JSON.parse(msg); } catch (e) { return; }
                if (!response) { return; }
                if (response.completed) {
                    if (response.aborted || (response.httpStatus && response.httpStatus !== 200)) {
                        console.log("PIVOT-HYDRATION downloadmanager reported failure for " + targetFilename +
                                 ": " + msg);
                    }
                    finish(!response.aborted && (!response.httpStatus || response.httpStatus === 200));
                } else if (response.returnValue === false) {
                    console.log("PIVOT-HYDRATION downloadmanager rejected request for " + targetFilename +
                             ": " + msg);
                    finish(false);
                } else if (onProgress && typeof response.amountReceived === "number") {
                    // Progress update, e.g. {"ticket":N,"amountReceived":X,"amountTotal":Y}.
                    onProgress(response.amountReceived);
                }
            };
            bridge.call("palm://com.palm.downloadmanager/download", JSON.stringify({
                target: sourceUrl,
                targetDir: targetDir,
                targetFilename: targetFilename,
                subscribe: true
            }));
        } catch (e) {
            console.log("PIVOT-HYDRATION downloadFile threw for " + targetFilename + ": " + e);
            finish(false);
        }
    }

    // -----------------------------------------------------------------------
    // Progress bar -- updates the placeholder page's enyo.ProgressBar directly
    // via the DOM rather than through Enyo's component tree. pivot-hydration.js
    // runs independently of whatever page happens to be rendered (see the
    // write-path note at the bottom of this file), and the placeholder's
    // portrait/landscape templates each instantiate their own ProgressBar
    // (BindableLayout creates one full component tree per orientation, only
    // one of which is visible at a time via Pane's CSS-based show/hide) -- so
    // both are queried and updated in lockstep rather than trying to track
    // which one Magazine currently considers "active".
    // -----------------------------------------------------------------------
    var _loggedProgressDomState = false;
    function updateProgress(percent) {
        try {
            var bars = document.querySelectorAll(".pivot-progress-bar .enyo-progress-bar-inner");
            var i;
            for (i = 0; i < bars.length; i++) {
                // Mirrors enyo.Progress.renderPosition(): the fill div is
                // created with visibility:hidden (position defaults to 0),
                // and normally only Enyo's own setPosition()/applyPosition()
                // clear it. Since this updates the DOM directly instead of
                // going through the component API, both properties have to
                // be set here or the fill stays invisible at 100% width.
                bars[i].style.visibility = percent <= 0 ? "hidden" : "visible";
                bars[i].style.width = percent + "%";
            }
            var labels = document.querySelectorAll(".pivot-progress-label");
            for (i = 0; i < labels.length; i++) {
                labels[i].textContent = percent + "%";
            }
            // Logged once per launch, not per call, so it's visible without
            // flooding the console across ~90 progress updates. bars.length
            // being 0 doesn't mean anything is broken by itself -- it also
            // happens whenever the Magazine tab isn't the foreground view --
            // but it's the first thing to check if the bar looks frozen while
            // the placeholder IS on screen.
            if (!_loggedProgressDomState) {
                _loggedProgressDomState = true;
                console.log("PIVOT-HYDRATION updateProgress: found " + bars.length +
                         " .pivot-progress-bar element(s), " + labels.length + " label(s) in DOM");
            }
        } catch (e) {}
    }

    // Blends byte-weighted and file-count-weighted progress, taking whichever
    // is higher. Pure byte-weighting (why this exists at all -- see below)
    // badly under-represents the first ~40% of the queue: an edition's first
    // ~40 files are near-empty common/apps/*/info.json entries that together
    // are under 1% of total bytes, but each still costs a full second of the
    // deliberate inter-file pacing (see downloadQueue) -- confirmed on-device
    // this reads as "nothing is happening" for the better part of a minute
    // even though the queue is advancing normally. File-count dominates the
    // max() during that phase so the bar visibly moves from the first
    // completed file; byte-weight takes back over once the queue reaches the
    // handful of large page background images, which is exactly the phase
    // pure file-counting would otherwise flatline at ~90% and stall through
    // (the reason this wasn't just file-count-weighted to begin with).
    function calcPercent(totalBytes, doneBytes, totalFiles, doneFiles) {
        var byBytes = totalBytes ? (100 * doneBytes / totalBytes) : 0;
        var byFiles = totalFiles ? (100 * doneFiles / totalFiles) : 0;
        var pct = Math.floor(Math.max(byBytes, byFiles));
        // Reserve 100% for the actual completion callback -- manifest.json
        // (the completion marker itself, downloaded last) has no known size
        // to weigh in, so byte-math alone can't account for it.
        return Math.max(0, Math.min(99, pct));
    }

    // -----------------------------------------------------------------------
    // Wake lock -- confirmed on-device 2026-08-21 to hold the screen awake
    // (blockScreenTimeout) using enyo.windows.setWindowProperties(window,
    // {...}), a direct native window call and the documented public API for
    // this property -- the same call every first-party HP app in the SDK
    // sample tree (com.palm.app.photos, com.palm.app.messaging) uses
    // directly. A Luna Bus route via a "DimService" PalmService component
    // against palm://com.palm.display/control/ (matching papyrus's own
    // DimService) was tried first and ruled out: unset `method` resolves to
    // the component's name ("DimService"), which the bus rejects outright as
    // an unknown method; the real registered method ("setProperty") ACKs
    // with {"returnValue":true} but has no observable effect on this device.
    // Called unconditionally, no window.PalmSystem branch -- this app has no
    // non-webOS codepath (see index.html).
    //
    // Triggered the same way papyrus triggers it: tied to a VIEW being on
    // screen (papyrus: disableDim() while the reading view is up, enableDim()
    // once the user leaves it), not to the background download process's own
    // start/end. See _loadBundledPlaceholderEdition (acquire) and
    // _pivotHydratedCacheFound (release) below -- those are the single choke
    // points for "the Fetching placeholder just became visible/stopped being
    // visible", covering every way that can happen (initial load,
    // checkAndHydrate's mid-session switch, the layout-error fallback).
    // -----------------------------------------------------------------------
    function acquireWakeLock() {
        try {
            enyo.windows.setWindowProperties(window, {blockScreenTimeout: true});
            console.log("PIVOT-HYDRATION acquireWakeLock: setWindowProperties({blockScreenTimeout:true}) sent");
        } catch (e) {
            console.log("PIVOT-HYDRATION error disabling dim: " + e);
        }
    }

    function releaseWakeLock() {
        try {
            enyo.windows.setWindowProperties(window, {blockScreenTimeout: false});
            console.log("PIVOT-HYDRATION releaseWakeLock: setWindowProperties({blockScreenTimeout:false}) sent");
        } catch (e) {
            console.log("PIVOT-HYDRATION error enabling dim: " + e);
        }
    }

    // -----------------------------------------------------------------------
    // checkAndHydrate -- checks the server's magazine version against whatever
    // is (or isn't -- e.g. the user deleted the folder) cached on disk, and
    // if the server is ahead, downloads the full edition into place. Runs
    // standalone, independent of whether the Magazine tab is ever opened.
    // -----------------------------------------------------------------------
    function checkAndHydrate(lang) {
        console.log("PIVOT-HYDRATION checkAndHydrate lang=" + lang);
        if (isHydratingElsewhere()) {
            console.log("PIVOT-HYDRATION skipped: another launch is already hydrating (localStorage lock)");
            return;
        }
        readLocalVersion(lang, function (localVersion) {
            console.log("PIVOT-HYDRATION local manifestVersion=" + localVersion);
            fetchJson(PIVOT_BASE_URL + "/" + lang + "/version.json", function (remote) {
                if (!remote) {
                    console.log("PIVOT-HYDRATION version.json fetch FAILED (network/parse error) -- treating as no update available this launch");
                    return;
                }
                var remoteVersion = remote.magazineVersion || 0;
                console.log("PIVOT-HYDRATION remote magazineVersion=" + remoteVersion);
                if (remoteVersion <= localVersion) {
                    console.log("PIVOT-HYDRATION already current -- nothing to do");
                    return;
                }
                markHydrating();
                // Push the switch directly onto whatever's already rendered, right
                // as the decision to download is made -- this is the actual fix,
                // not the isHydratingElsewhere() check in loadDefaultEdition (that
                // one runs too early to ever see this same download start; see its
                // comment). Guarded on _pivotShowingPlaceholder so this doesn't
                // fire redundantly if the Magazine tab happens to already be
                // showing the placeholder for some other reason (e.g. the very
                // first hydration ever, before any edition exists to switch away
                // from).
                if (_pivotActiveMagazine && !_pivotActiveMagazine._pivotShowingPlaceholder) {
                    console.log("PIVOT-HYDRATION pushing placeholder switch to active Magazine instance");
                    _pivotActiveMagazine._loadBundledPlaceholderEdition();
                }
                fetchJson(PIVOT_BASE_URL + "/" + lang + "/manifest.device.json", function (devManifest) {
                    if (!devManifest || !devManifest.assets || !devManifest.self) {
                        console.log("PIVOT-HYDRATION manifest.device.json fetch FAILED or malformed");
                        return;
                    }
                    var totalBytes = 0;
                    var i;
                    for (i = 0; i < devManifest.assets.length; i++) {
                        totalBytes += devManifest.assets[i].size || 0;
                    }
                    console.log("PIVOT-HYDRATION starting download: " + devManifest.assets.length +
                             " assets, " + totalBytes + " bytes total, into v" + remoteVersion);
                    updateProgress(0);
                    // Assets land in a version-namespaced directory -- see the comment on
                    // downloadQueue below for why this isn't just PIVOT_CACHE_ROOT/lang.
                    var versionedDir = PIVOT_CACHE_ROOT + "/" + lang + "/v" + remoteVersion;
                    downloadQueue(lang, versionedDir, devManifest.assets.slice(), devManifest.self,
                        totalBytes, 0, devManifest.assets.length, 0, function () {
                        console.log("PIVOT-HYDRATION hydration complete for lang=" + lang);
                        updateProgress(100);
                    });
                });
            });
        });
    }

    // onDone fires exactly once, on every exit path (full completion or an
    // early abort on a failed download), so the wake lock is always released.
    // completedBytes/completedFiles accumulate what's fully written so far
    // (from the manifest's own size field and the queue's own shift(), not a
    // filesystem stat) so progress only moves forward -- an in-flight file's
    // amountReceived is added to completedBytes for display but never folded
    // back into the running total itself.
    //
    // versionedDir (PIVOT_CACHE_ROOT/lang/v{N}) is where every PAGE asset
    // lands -- never PIVOT_CACHE_ROOT/lang directly. Confirmed on-device
    // 2026-08-21: an app killed mid-hydration left the flat, unversioned
    // cache directory (the pre-existing design, page filenames identical
    // across editions/versions) in a state mixing files from two different
    // editions -- e.g. a page's bindings.json already overwritten by the new
    // download while its portrait.lo.js hadn't been reached yet -- and the
    // OLD manifest.json (not yet overwritten, since it downloads last) was
    // still on disk describing the mix as if it were self-consistent. Magazine
    // rendering doesn't just show stale content in that state, it breaks
    // outright (BindableLayout can't resolve a template macro whose value
    // came from a binding file that's already the new edition's). Every
    // version's assets going into their own directory makes that impossible:
    // a killed download just leaves an incomplete v{N} sitting there unused,
    // and whatever v{N-1} (or earlier) manifest.json is still active on the
    // fixed top-level path keeps pointing at ITS OWN directory, untouched.
    // manifest.json itself still lives at the fixed top-level path (not
    // versioned) -- it's the one thing every launch's read-path needs to find
    // without already knowing the current version, and it's the only
    // still-flat-named file this scheme relies on downloadmanager overwriting
    // cleanly, same as it already reliably did for the v1->v2 bump.
    function downloadQueue(lang, versionedDir, remainingAssets, selfEntry, totalBytes, completedBytes, totalFiles, completedFiles, onDone) {
        var pointerDir = PIVOT_CACHE_ROOT + "/" + lang;
        if (remainingAssets.length === 0) {
            // All page assets are down -- write the local manifest last, at the
            // fixed (unversioned) pointer location, so its presence there is
            // itself the "hydration complete AND this is the active version"
            // signal every launch's read-path checks.
            downloadFile(selfEntry.sourceUrl, pointerDir, selfEntry.targetFilename, null, function (ok) {
                // best-effort either way -- a failure here just means we retry
                // (and re-download everything) on the next version check.
                onDone();
            });
            return;
        }
        var asset = remainingAssets.shift();
        downloadFile(asset.sourceUrl, versionedDir, asset.targetFilename, function (received) {
            updateProgress(calcPercent(totalBytes, completedBytes + received, totalFiles, completedFiles));
        }, function (ok) {
            if (!ok) {
                console.log("PIVOT-HYDRATION download FAILED for " + asset.targetFilename + " -- aborting queue, " +
                         remainingAssets.length + " assets left unfetched this launch");
                onDone(); // abort the queue; next launch's version check retries from scratch
                return;
            }
            completedBytes += asset.size || 0;
            completedFiles += 1;
            markHydrating(); // heartbeat -- see isHydratingElsewhere's comment
            updateProgress(calcPercent(totalBytes, completedBytes, totalFiles, completedFiles));
            // Paced rather than chained straight through, for two independent reasons
            // both confirmed on-device: (1) rapid-fire calls silently stopped producing
            // responses after a couple dozen in a row, with no error anywhere -- looks
            // like a hidden rate limit or handle cap on PalmServiceBridge/downloadmanager;
            // (2) even below that ceiling, this JS thread is the SAME thread the app's
            // own UI renders on -- back-to-back native bridge calls visibly stalled the
            // app's splash/launch rendering. Nothing is waiting on this to finish quickly,
            // so pace it gently rather than racing the UI for the thread.
            setTimeout(function () {
                downloadQueue(lang, versionedDir, remainingAssets, selfEntry, totalBytes, completedBytes, totalFiles, completedFiles, onDone);
            }, 1000);
        });
    }

    function readLocalVersion(lang, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", PIVOT_CACHE_ROOT + "/" + lang + "/manifest.json?_=" + Date.now(), true);
        xhr.onload = function () {
            if (xhr.status !== 200) { callback(0); return; }
            try {
                var manifest = JSON.parse(xhr.responseText);
                callback((manifest && manifest.magazineVersion) || 0);
            } catch (e) {
                callback(0);
            }
        };
        xhr.onerror = function () { callback(0); };
        try { xhr.send(); } catch (e) { callback(0); }
    }

    function fetchJson(url, callback) {
        var xhr = new XMLHttpRequest();
        xhr.open("GET", url, true);
        xhr.onload = function () {
            if (xhr.status !== 200) { callback(null); return; }
            try { callback(JSON.parse(xhr.responseText)); } catch (e) { callback(null); }
        };
        xhr.onerror = function () { callback(null); };
        try { xhr.send(); } catch (e) { callback(null); }
    }

    // Heartbeat, not a one-shot lock: markHydrating() is called again after
    // every file downloadQueue completes (see below), so this timestamp keeps
    // sliding forward for as long as a hydration run is genuinely still
    // active. That's load-bearing, not defensive -- a fixed lock set once at
    // the start (the original design) stays "fresh" from a killed app's point
    // of view for its whole window regardless of what actually happened, so
    // swiping the app away mid-download left the next launch's
    // checkAndHydrate seeing a stale-but-not-yet-expired lock and silently
    // bailing out with no retry until a THIRD launch, after the fixed window
    // finally ran out -- confirmed on-device 2026-08-21 (killed at 22%,
    // relaunch immediately stuck at 0% for the rest of that session). With a
    // heartbeat, the threshold only needs to outlast the gap between two
    // consecutive file completions (1s pacing + actual transfer time), not
    // the whole multi-minute hydration run, so a killed app's lock goes stale
    // within seconds of the next launch instead of up to 2 minutes.
    function isHydratingElsewhere() {
        try {
            var raw = localStorage.getItem("com.palm.app.findapps.pivotHydrating");
            if (!raw) { return false; }
            return (Date.now() - parseInt(raw, 10)) < (30 * 1000);
        } catch (e) {
            return false;
        }
    }
    function markHydrating() {
        try { localStorage.setItem("com.palm.app.findapps.pivotHydrating", "" + Date.now()); } catch (e) {}
    }

    // -----------------------------------------------------------------------
    // Read path: prefer the hydrated cache over the bundled placeholder.
    // Only touches Magazine's fallback tier -- the live/db8 edition attempt in
    // create() is untouched. Full-body override (not saving/calling the
    // original) so the pre-existing usingDefaultEdition re-entrancy guard
    // still behaves exactly as before.
    // -----------------------------------------------------------------------
    var MagazineProto = enyo.FindApps.Magazine.Magazine.prototype;

    MagazineProto.loadDefaultEdition = function () {
        if (this.usingDefaultEdition) {
            return this.unableToInitMagazine();
        }
        this.usingDefaultEdition = true;
        this._pivotLang = resolveLang();
        _pivotActiveMagazine = this;
        // Checked BEFORE ever looking at the cache, launch-time only --
        // deliberately not re-checked on tab-return (see
        // findApps.MagazineView.prototype.reset below), not something that
        // runs during active use. isHydratingElsewhere() is true exactly
        // while a newer edition is actively downloading (markHydrating()'s
        // heartbeat -- see its own comment), so a user who opens the app
        // while a download happens to already be in progress sees "fetching
        // your next issue" instead of the old edition. In practice this
        // rarely fires on its own: the Magazine tab is this app's default
        // view, so loadDefaultEdition typically runs within ~2s of launch --
        // long before checkAndHydrate's 20s delay even elapses, let alone
        // starts a download. checkAndHydrate pushing directly onto
        // _pivotActiveMagazine (below) is what actually handles the common
        // case; this check is the fallback for whenever the tab is opened
        // later, after a download has already been running for a while.
        if (isHydratingElsewhere()) {
            this._loadBundledPlaceholderEdition();
            return;
        }
        this._pivotTryHydratedCache(true);
    };

    // Shared by every check site (initial load, background poll while the
    // placeholder is showing, and the tab-return check) so there's exactly
    // one success/failure path to reason about. isInitial controls what
    // happens on failure: the very first check falls back to the bundled
    // placeholder (nothing has rendered yet); every later check just leaves
    // whatever's already on screen alone and waits for the next attempt.
    //
    // The cache-busting query param is load-bearing, not defensive: this is
    // the SAME literal URL re-requested every 15s by _startPivotRecheck (and
    // again on every tab-return) for as long as the placeholder is showing.
    // The first attempt, made before hydration has written anything, gets a
    // failure response for a URL WebKit's XHR layer has no reason to treat as
    // anything but a normal cacheable GET (enyo.xhr.request sets no
    // Cache-Control/no-cache headers -- see source/dom/xhr.js). Once that
    // failure is cached, every later poll to the identical URL can keep
    // being served the same stale failure instead of re-checking disk, even
    // long after hydration finishes and the file genuinely exists -- which
    // would explain a magazine that never swaps over without a full app
    // restart (a fresh process has no cache yet). Same class of bug
    // archive-patch.js already works around via makeKey() for the museum
    // API.
    MagazineProto._pivotTryHydratedCache = function (isInitial) {
        this.$.webService.call(null, {
            url: PIVOT_CACHE_ROOT + "/" + this._pivotLang + "/manifest.json?_=" + Date.now(),
            handleAs: "json",
            onSuccess: "_pivotHydratedCacheFound",
            onFailure: isInitial ? "_loadBundledPlaceholderEdition" : "_pivotHydratedCacheStillPending"
        });
    };

    MagazineProto._pivotHydratedCacheFound = function (inSender, inResponse, inRequest) {
        this._pivotShowingPlaceholder = false;
        releaseWakeLock();
        if (this._pivotRecheckTimer) {
            clearInterval(this._pivotRecheckTimer);
            this._pivotRecheckTimer = null;
        }
        // Rebuilds the file set and re-renders the current page -- when this fires
        // from a background/tab-return recheck rather than the initial load, this is
        // what actually swaps the still-showing placeholder for the real content.
        this.gotDefaultEditionFileSet(inSender, inResponse, inRequest);
    };

    MagazineProto._pivotHydratedCacheStillPending = function () {
        // Not ready yet -- the interval (or the next tab visit) just tries again.
    };

    // A successful manifest.json fetch only proves the POINTER file is
    // readable, not that everything it points at will actually render --
    // confirmed on-device 2026-08-21: a cache left inconsistent by an
    // interrupted hydration (fixed going forward by the versioned-directory
    // write path above, but this covers ANY other way a hydrated cache could
    // end up broken) fetched fine, so _pivotHydratedCacheFound declared
    // victory and cancelled the recheck loop, but the page itself then failed
    // to render -- and with the recheck already stopped and
    // _pivotShowingPlaceholder already false, there was no automatic recovery
    // left at all short of a full app restart, even long after a clean
    // hydration completed in the background. Falling back to the placeholder
    // here (only while we currently believe we're showing hydrated content --
    // the placeholder's own template failing is a separate, unrelated bug,
    // not something to loop on) re-arms _startPivotRecheck via the normal
    // path, so a later successful hydration still gets picked up live.
    var _origHandleDispatchLayoutError = MagazineProto.handleDispatchLayoutError;
    MagazineProto.handleDispatchLayoutError = function () {
        if (!this._pivotShowingPlaceholder) {
            console.log("PIVOT-HYDRATION hydrated cache failed to render -- falling back to placeholder, resuming recheck");
            this._loadBundledPlaceholderEdition();
        }
        return _origHandleDispatchLayoutError.apply(this, arguments);
    };

    MagazineProto._loadBundledPlaceholderEdition = function () {
        this.warn(MagazineErrors.getErrorString(MagazineErrors.LOADING_DEFAULT_EDITION));
        this._pivotShowingPlaceholder = true;
        acquireWakeLock();
        var lang = this._pivotLang;
        this.$.webService.call(null, {
            url: "source/magazine/defaultEdition/" + lang + "/manifest.json",
            handleAs: "json",
            onSuccess: "gotDefaultEditionFileSet",
            onFailure: "unableToInitMagazine"
        });
        // The placeholder just rendered because hydration hadn't finished (or hadn't
        // even started) at the moment this Magazine instance was created -- either
        // it's genuinely the first hydration ever, or loadDefaultEdition's
        // isHydratingElsewhere() check found an update actively in progress.
        // Either way, hydration runs independently in the background and has no way
        // to reach back into an already-rendered view to say "done now" -- without
        // this, a user who leaves the tab open (never switching away and back, the
        // other trigger -- see findApps.MagazineView.prototype.reset below) would
        // sit on the placeholder forever, even after the edition finishes caching.
        // So: poll for it in the background too, capped well above the ~6-7 minutes
        // a full hydration has taken end to end on-device.
        this._startPivotRecheck();
    };

    MagazineProto._startPivotRecheck = function () {
        if (this._pivotRecheckTimer) {
            return;
        }
        var self = this;
        var attemptsLeft = 60; // 15min at 15s apart
        this._pivotRecheckTimer = setInterval(function () {
            attemptsLeft -= 1;
            if (attemptsLeft <= 0) {
                clearInterval(self._pivotRecheckTimer);
                self._pivotRecheckTimer = null;
                return;
            }
            self._pivotTryHydratedCache(false);
        }, 15000);
    };

    // -----------------------------------------------------------------------
    // "View Recent Apps" button on the placeholder page: while the real
    // edition hydrates, the placeholder (main/source/magazine/defaultEdition)
    // offers a way out to the rest of the app instead of just sitting there.
    // Wires a new "recentapps" magazine target to the Categories/Browser view,
    // pre-selecting the "New" stored query (RadioGroup index 1 -- see
    // main/source/main/AppCatalog.js, "Top"/"New"; "Paid"/"Free" were trimmed).
    // -----------------------------------------------------------------------
    if (enyo.FindApps && enyo.FindApps.Magazine && enyo.FindApps.Magazine.Magazine) {
        enyo.FindApps.Magazine.Magazine.prototype.magazineDefinedTarget.recentapps = {type: "external"};
    }

    if (typeof findApps !== "undefined" && findApps.AppCatalog && findApps.BrowserView && findApps.MagazineView) {
        findApps.AppCatalog.prototype.showNewest = function () {
            this.$.stored_queries.setValue(1);
            this.storedQuerySelected();
        };
        findApps.BrowserView.prototype.showNewest = function () {
            this.$.browseCategories.showNewest();
        };

        var _origGoToTarget = findApps.MagazineView.prototype.goToTarget;
        findApps.MagazineView.prototype.goToTarget = function (inSender, targetName, params) {
            if (targetName === "recentapps") {
                var browserView = findApps.ViewLibrary.getView("BROWSER");
                browserView.reset();
                browserView.showNewest();
                return;
            }
            return _origGoToTarget.apply(this, arguments);
        };

        // Confirmed on-device: switching to another tab and back does NOT recreate
        // the underlying Magazine component (create()/loadDefaultEdition() only run
        // once, on the view's first construction) -- it just calls reset() on the
        // already-alive instance. Without this, a user who switches away and back
        // after hydration finished kept seeing the placeholder indefinitely; only a
        // full app close+relaunch actually picked up the real content, which is a
        // broken experience. This makes tab-return the primary, instant way hydration
        // gets noticed -- the interval in _startPivotRecheck above is just the backup
        // for someone who leaves the Magazine tab open and never switches away at all.
        //
        // Deliberately only fires while the placeholder is already showing --
        // checking for a newer edition is launch-time only, not something that
        // runs while the app is in active use.
        var _origMagazineViewReset = findApps.MagazineView.prototype.reset;
        findApps.MagazineView.prototype.reset = function () {
            var result = _origMagazineViewReset.apply(this, arguments);
            var magazine = this.$.magazine;
            if (magazine && magazine._pivotShowingPlaceholder) {
                magazine._pivotTryHydratedCache(false);
            }
            return result;
        };
    }

    // -----------------------------------------------------------------------
    // Write-path: best-effort background hydration on every launch, whether or
    // not the user ever opens the Magazine tab. A no-op once the on-device
    // manifest's magazineVersion is already current with the server.
    //
    // Delayed well past launch (confirmed on-device: starting this at the same
    // 3s mark archive-patch.js uses for its lightweight one-shot update check
    // was too early for a ~200-call download sequence on this single-threaded
    // JS environment -- it visibly stalled the app's own splash/launch
    // rendering, since native bridge calls and UI paint share one thread).
    // 20s gives the app room to fully launch and settle first.
    // -----------------------------------------------------------------------
    console.log("PIVOT-HYDRATION module loaded, scheduling checkAndHydrate in 20s");
    setTimeout(function () {
        try {
            checkAndHydrate(resolveLang());
        } catch (e) {
            console.log("PIVOT-HYDRATION checkAndHydrate threw: " + e);
        }
    }, 20000);
}());
