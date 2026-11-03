# Lunacy's changes to the calendar framework

Upstream: webOS CE's calendar framework, submission 42, from `system/usr/palm/frameworks/calendar/`
in [webos-doctor-ce](https://github.com/webOSArchive/webos-doctor-ce) at `8284a49`. That is Palm's
framework from the TouchPad with CE's rewrite of repeat expansion and southern daylight time. The copy
`fetch-assets.sh` starts from is `Workbench/vendor/webos-ce/`, whose `COMMIT` file says what it is;
`Workbench/vendor/touchpad/` stays as the TouchPad shipped it.

Each patch changes the same code in three places, because a MojoLoader reads either form:
`submission/42/javascript/`, `submission/42/concatenated.js` and
`submission/42calendar.js`. `fetch-assets.sh` applies the patches in order, for the pages and for
the JS services, and fails the build if one no longer applies.

**Adding a patch:** change all three forms and diff against the tree before it. When CE's copy is
taken again, re-make these against it and drop what CE has taken. Run the sweeps in
[Workbench/calendar-sweep](../../Workbench/calendar-sweep/README.md) and CE's `tools/calendar-sweep/`
on both file forms.

The patch that fixed repeats on a daylight saving day (`0001`, 2026-11-02) is gone: CE's rewrite
replaced it.

## 0002: Android's changes decide the offset

Lunacy only (codepoet, 2026-11-02: Lunacy always defers to Android on daylight saving). One daylight
saving span a year can't hold a permanent change or a third change, as in Almaty 2024 or Casablanca
2026. `getTimeZoneRules` sends Android's own list as `firstOffset` and `changes`
(`SystemService.kt`), and `TimezoneManager.getOffset` reads it when it is there. Without it,
`getOffset` is CE's.

The import parser keeps its own reading of the TouchPad's fields, because it has to stay identical
to webos-webcal-synergy, so an import in such a year can be an hour out.

## 0003: Windows zone names

For CE to take; [its copy of the notes](https://github.com/webOSArchive/webos-doctor-ce/blob/main/Docs/LUNACY-WINDOWS-ZONE-NAMES.md).
webcal 0.2.2 stored Outlook's `TZID` as it came ("Eastern Standard Time"). `getTimeZoneRules` leaves
a zone it doesn't know out of its reply, on a TouchPad too, so `getOffset` answered 0 and a repeat
kept its UTC time through a clock change: 09:45 Eastern became 08:45 after Nov 1. `windowsZones`
(CLDR, territory 001) and `zoneName` map the name where the framework asks for rules and reads an
offset. webcal 0.2.3 stores a real name, but events already stored keep the old one. Test:
`Workbench/calendar-sweep/windows-zone-sweep.js`.

CE took this as `b88ca03`, the same bytes. Drop this patch when CE's framework is next taken at that
commit or later.
