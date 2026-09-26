// Runs App Catalog 6.2's direct install on the device under a com.palm.* id, as the catalog
// would: a package without install scripts (installed, then left for the remover), then one
// with scripts (which should be given back, not installed). Read with palm-log -f <this id>.
var out = [];
function say(s) { out.push(s); console.log("INSTALLPROBE " + s); var o = document.getElementById("out"); if (o) { o.textContent = out.join("\n"); } }
var cases = [
    {id: "ca.canuckcoding.compass", version: "1.0.1", ipkUrl: "http://appstorage.webosarchive.org/packages/ca.canuckcoding.compass_1.0.1_all.ipk"},
    {id: "org.webosarchive.otaready", version: "1.2.0", ipkUrl: "http://appstorage.webosarchive.org/packages/org.webosarchive.otaready_1.2.0_all.ipk"}
];
function next(i) {
    if (i >= cases.length) { say("done"); return; }
    var c = cases[i], last = "";
    say("== " + c.id);
    DirectInstall.run(c, function (state, progress, extra) {
        var line = state + " " + progress + (extra ? " " + JSON.stringify(extra) : "");
        if (state !== "ipk download current" || progress === 0 || progress === 100) { say(line); }
        if (/^(installed|install failed|download failed)$/.test(state)) { next(i + 1); }
    }, function (scripts) {
        say("has scripts: " + JSON.stringify(scripts));
        next(i + 1);
    });
}
window.onload = function () { next(0); };
