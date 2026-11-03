# Calendar repeat sweeps

Run Palm's real calendar framework under Node and compare every occurrence with independent zone
data, paging a day at a time and in one wide range, with and without a cancelled date. Nothing here
runs on a device except `fold-rules.mjs`.

    export UNDERSCORE=<a TouchPad's frameworks>/underscore/submission/7/javascript/underscore.js
    STEP=11 TZ=America/New_York node sweep.js <framework>/submission/42/concatenated.js WEEKLY 5
    STEP=7 TZ=Australia/Sydney node until-sweep.js <framework>/submission/42calendar.js <parser folder>

- **Needs:** Node 22 or later (its `Intl` supplies the expected answers and the zone rules), and a
  copy of Palm's underscore. The file under test is given on the command line, so stock, CE or
  Lunacy's copy all work. Nothing else is outside this folder.
- **`next-sweep.js`:** `findNextOccurrence`, which reminders use, for every repeat type. It runs from CE's
  `tools/calendar-sweep/` beside `gen-cases.py`'s cases and needs `lib.js` there.
- **What it covers:** 8 event zones at 4 start times, COUNT repeats, weekly on one day and daily;
  UNTIL through the parser in 6 zones. It doesn't cover monthly, yearly, weekly on several days,
  intervals above 1 or multi-day events.
- **Weak spots:** the rule answers are `SystemService.yearRules` redone in JS, with "daylight" meaning
  the larger offset, so they're a stand-in for a device's. `Intl` is both the rule source and the
  expected answer here. The `Class` and `ObjectUtils.clone` stubs in `lib.js` are minimal.
- **`windows-zone-sweep.js`:** a repeat stored with each Windows zone name against the same repeat with
  its IANA name; a build without the names shows one hour out after a clock change.
- **`fold-rules.mjs`, `score-rules.py`:** read `getTimeZoneRules` from a Lunacy card over DevTools for
  every zone in `zones.json`, then score the replies against Python's zone data.
