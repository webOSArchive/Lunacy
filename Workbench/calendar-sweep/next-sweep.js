// Reminders: findNextOccurrence for every repeat type, against gen-cases.py's answers.
//   TZ=<device> node next-sweep.js <framework file> <parser folder> <cases.json> [examples]
// Compares a build of the calendar framework with gen-cases.py's expected occurrences:
//   TZ=Australia/Sydney node oracle-sweep.js <concatenated.js or 42calendar.js> <parser folder> <cases.json> [examples]
// TZ is the device's zone. Zone rules are real TouchPad getTimeZoneRules replies (touchpad-rules.json).
// .ics text goes through the real parser, and every case is paged by day, by week and in one range.
const fs = require("fs"), path = require("path"), H = require("./lib.js");
const file = process.argv[2], utils = process.argv[3].replace(/\/?$/, "/"), all = JSON.parse(fs.readFileSync(process.argv[4])), show = +process.argv[5] || 0, DEV = process.env.TZ;
const lib = H.load(file), tm = new lib.__T(), u = new lib.__U(), em = new lib.EventManager();
tm.timezone = DEV;
tm.timezones = JSON.parse(fs.readFileSync(path.join(__dirname, process.env.RULES||"touchpad-rules.json")));
function Future(r) { if (arguments.length) this.result = r; }
Future.prototype.then = function (a, b) { (b || a).call(this, this); return this; };
Future.prototype.getResult = function () { return this.result; };
tm.hasTimezoneInfo = tm.hasTimezoneInfo || ((t, y) => !!(tm.timezones[y] && tm.timezones[y][t]));
tm.loadTimezones = () => new Future({returnValue: true});
tm.setup = () => new Future({});
function L(f) {
    const m = {exports: {}};
    new Function("require", "module", "exports", "libPath", "Log", "Future", "Calendar", "checkResult", fs.readFileSync(utils + f, "utf8"))(n => L(path.basename(n)), m, m.exports, "", {log() {}, log_icalDebug() {}, debug() {}}, Future, {TimezoneManager: () => tm}, x => x.result || {returnValue: true});
    return m.exports;
}
const iCal = L("iCal.js");
function parse(lines) {
    let o;
    iCal.parseWebCalICal(["BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VEVENT", "UID:u", "SUMMARY:x"].concat(lines, ["END:VEVENT", "END:VCALENDAR", ""]).join("\r\n")).then(f => { o = f.result; });
    return o.events[0].result;
}

const tally = {}, ex = []; let checks = 0, wrong = 0, secs = Date.now();
const SAMPLE = +process.env.SAMPLE || 1;
for (let i = 0; i < all.length; i += SAMPLE) {
    const c = all[i], ev = parse(c.lines), exp = [...c.expect].sort((a, b) => a - b);
    const bounded = /COUNT|UNTIL/.test(c.lines.join(" "));
    const qs = [exp[0] - 864e5];
    exp.forEach(e => qs.push(e - 60000, e, e + 3600000));
    for (const q of qs) {
        const want = exp.find(e => e > q);
        if (want === undefined && !bounded) continue;
        const e = JSON.parse(JSON.stringify(ev));
        const got = em.findNextOccurrence(e, q);
        // Past the window the reference lists, nothing can be said.
        if (want === undefined && got !== false && got >= c.last) continue;
        checks++;
        if ((want === undefined ? false : want) !== got) {
            wrong++;
            const k = c.kind + (c.cancel ? "+cancel" : "");
            const how = want === undefined ? "ran-on" : got === false ? "none" : got > want ? "skipped" : "early";
            const t = tally[k] = tally[k] || {}; t[how] = (t[how] || 0) + 1;
            if (ex.length < show) ex.push({tz: c.tz, kind: c.kind, how, q: new Date(q).toISOString(), want: want && new Date(want).toISOString(), got: got && new Date(got).toISOString(), l: c.lines.slice(0, 1).concat(c.lines.slice(2)).join(" ").slice(0, 170)});
        }
    }
}
console.log(DEV.padEnd(20), "next checks", checks, "wrong", wrong, ((Date.now() - secs) / 1000 | 0) + "s", JSON.stringify(tally));
ex.forEach(x => console.log(JSON.stringify(x)));
