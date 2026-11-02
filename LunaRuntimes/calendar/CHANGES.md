# Lunacy's changes to the calendar framework

Upstream: webOS CE's calendar framework, submission 42, from `system/usr/palm/frameworks/calendar/`
in [webos-doctor-ce](https://github.com/webOSArchive/webos-doctor-ce) at `3664f30`. That is Palm's
framework from the TouchPad with CE's fixes to repeats and southern daylight time. The copy
`fetch-assets.sh` starts from is `Workbench/vendor/webos-ce/`, whose `COMMIT` file says what it is;
`Workbench/vendor/touchpad/` stays as the TouchPad shipped it.

Each patch changes the same code in three places, because a MojoLoader reads either form:
`submission/42/javascript/`, `submission/42/concatenated.js` and the minified
`submission/42calendar.js`. `fetch-assets.sh` applies the patches in order, for the pages and for
the JS services, and fails the build if one no longer applies.

**Adding a patch:** change all three forms and diff against the tree before it. When CE's copy is
taken again, re-make these against it and drop what CE has taken.

## 0001: repeats on a daylight saving day

For CE to take as well; nothing here is about Lunacy. Palm holds a time in the event's zone as a
timestamp to be read back on the device's clock. A daylight saving change between the two, in either
zone, moves that reading by an hour, and the same goes for a zone's rule looked up at the clock
reading as if it were a real time. So:

- `findRepeatsInRangeHelper` put an occurrence on the day of a change an hour out.
- The start time of a repeat beginning on the device's own change day was an hour out, every time.
- CE's resume in `findRepeatsInRangeCountVersion` started from that wrong time, or round-tripped its
  date through the same conversion, landed on the same day, counted the occurrence twice, and the
  repeat ended early. A Sydney 12:30 AM repeat crossing 3 October 2027 lost its last three.

`wallClockOf` and `occurrenceTime` work from the date and time in the event's zone with the zone's
offset at the real time, and don't go through the device's clock. The resume starts from
`lastFound` and drops dates before `resumeDate`. With no rules loaded for the zone, as for a repeat
from a year outside the ones loaded, both fall back to Palm's reading, so such a repeat reads as
before.

Measured under node with Palm's real library against the time zone database, paging day by day
and in one wide range, with and without a cancelled date:

| Repeats | CE at `3664f30` | With this |
|---|---|---|
| Weekly COUNT, 8 event zones, 5 device zones (21,760 cases) | 726 wrong | 0 |
| Daily COUNT, device in New York (2,816) | 156 wrong | 0 |
| UNTIL through the real parser, 6 event zones, 3 device zones (7,632) | 110 wrong | 0 |

## 0002: Android's changes decide the offset

Lunacy only (codepoet, 2026-11-02: Lunacy always defers to Android on daylight saving). One daylight
saving span a year can't hold a permanent change or a third change, as in Almaty 2024 or Casablanca
2026. `getTimeZoneRules` sends Android's own list as `firstOffset` and `changes`
(`SystemService.kt`), and `TimezoneManager.getOffset` reads it when it is there. Without it,
`getOffset` is CE's.

The import parser keeps its own reading of the TouchPad's fields, because it has to stay identical
to webos-webcal-synergy, so an import in such a year can be an hour out.
