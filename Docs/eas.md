# Exchange ActiveSync

How Lunacy runs webOS's Exchange support, what was measured to get there, and what is open.
The short version is in [architecture.md](architecture.md), "Exchange (EAS)".

## What runs

Palm's `mojomail-eas` (package 1.1-122.2, `svn://subversion.palm.com/main/nova/palm/mojomail/eas`),
exactly as the reference TouchPad (webOS CE 3.1.0) ships it, under the PDK runtime's glibc
loader with Lunacy's liblunaservice, like mojomail-imap ([architecture.md](architecture.md),
"Native services"). Its command line is the device's D-Bus service file:
`LD_LIBRARY_PATH=/usr/lib/ssl11mail LD_PRELOAD=…/libssl_compat.so … mojomail-eas -c '{log…}' Palm Palm/1.0.1`
(the last two arguments are the device type and user agent it reports to the server).
It links the same libraries as the IMAP service (libemail-common, libmojo*, Boost 1.39 regex,
jemalloc, GLib), reaches HTTPS through libemail-common's curl and webOS CE's `ssl11mail`
OpenSSL 1.1 shim, and adds libxml2 2.7.2 (autodiscover). All of it comes from
`Workbench/vendor/touchpad/eas`, which `AndroidLuna/fetch-assets.sh` lays into the ROM:

| File | From the TouchPad |
|---|---|
| `/usr/bin/mojomail-eas` | the package's binary |
| `/usr/lib/libxml2.so.2` | libxml2 2.7.2 |
| `/usr/share/dbus-1/system-services/com.palm.eas.service` | the service file |
| `/etc/palm/db/kinds/com.palm.*.eas` and `permissions/com.palm.securitypolicy.eas` | the package's ten kinds |
| `/etc/palm/db_kinds/com.palm.securitypolicy`, `.device`; `/etc/palm/db/kinds/com.palm.task`, `.tasklist*` | the bases the EAS kinds extend that no bundled app brings |
| `/usr/palm/public/accounts/com.palm.eas` | the Microsoft Exchange account template |
| `/usr/palm/ext-timezones.json` | the system service's zone list (below) |
| `/usr/palm/services/com.palm.service.calendar.meetingupdate`, `/usr/palm/frameworks/calendar.io` | Calendar's meeting update service and its framework |

`mojomail-eas-debug`, the package's debug launcher, isn't shipped; its logging is had the
Lunacy way: `files/pdk/args/com.palm.eas` (with `run-as`) holding
`-c '{"log":{"appender":{"type":"stdout"},"levels":{"com.palm.eas":"debug"}}}' Palm Palm/1.0.1`
sends the service's debug log, with every WBXML request and response decoded, to logcat as
`svc [com.palm.eas]`. Mind the quotes: without them the JSON is split into words and the
service prints its usage and exits.

## Setting up an account

Email → Preferences & Accounts → Add Account → Microsoft Exchange. For a server autodiscover
can't find, Manual Setup: the server as `https://host` (the wizard puts `https://` in the
field), the domain blank unless the server wants one, and the username as the server wants it
(for Zoho, the whole address; the Sign In page fills in only the part before the `@`, as the
TouchPad's does). The Account Settings page then offers Email, Contacts, Address Lookup and
Calendar. Contacts and Calendar show the account as their own calendar and contact set.

## What was measured and fixed (2026-10-10)

Each is general: nothing here knows about EAS.

- **db8 `reserveIds`.** Lunacy answered at least one id and at most 1000 whatever the count.
  The TouchPad (measured): `count` 0 is `{"ids":[]}`, a negative count error 22 "value out of
  range for property 'count'", more than 1000 error -3967 "cannot reserve more than 1000 ids".
  mojomail-eas keeps a pool of reserved ids and asks for 0, then for what it is short; the
  extra id Lunacy gave sent its count below zero and it asked for 4294967295, …94, …93.
  ([db8.md](db8.md).)
- **`com.palm.systemservice/timezone/getTimeZoneFromEasData`** was missing, and every
  calendar event the server sent failed its collection's sync (`EasEventSync::GetTimezoneFromObjectResponse`).
  Now in SystemService.kt, after luna-sysservice's TimeZoneService: the candidates are
  ext-timezones.json's `timeZone` entries whose `offsetFromUTC` is -bias, in file order; with
  both dates whole (all seven fields) the first whose daylight saving starts and ends at
  those instants this year, otherwise the first. mojomail-eas sends its dates without
  `dayOfWeek`, so on a device it always gets the first. Measured on the TouchPad and matched:
  bias 300 → America/Toronto, 480 → America/Los_Angeles, 0 → Europe/London, -60 →
  Europe/Tirane (whole EU dates too), -330 → Asia/Kolkata, -600 → Australia/Brisbane; `{}`,
  `"300"` and `300.5` → "bias value missing"; 720 → "Failed to find any timezones with
  specified bias value"; whole US pre-2007 dates → "…with specified parametes" (sic).
  **One difference:** whole dates with Sydney's rules (bias -600) find nothing on the TouchPad,
  where Lunacy finds Australia/Melbourne, which they describe. Its 2011 build differs from the
  Open webOS source in some detail that wasn't found; nothing on a device sends such dates.
- **`com.palm.service.calendar.meetingupdate`** wasn't shipped; mojomail-eas calls it for every
  event it sends up ("Service does not exist" was a critical error in its log). Palm's service
  now runs, with `calendar.io`; with no one else to tell it answers "No emails to send".
- **Kind files with json-c's trailing commas.** The TouchPad's `com.palm.tasklist` kind has a
  comma before a closing brace inside an object, which json-c reads and Android's org.json
  refuses, so the kind (which `com.palm.tasklist.eas` extends) wasn't registered. The
  configurator now drops a comma before `}` or `]` outside strings.
- **Two windows opened back to back** (shell, AppWindow.kt). WebView binds one new window at a
  time; one opened before the last is bound is dropped ("Blocking popup window creation as an
  outstanding popup window is still pending"). Email lost its mail card that way once, opened
  just after its login-error dashboard, and the card sat on its splash. `nextWindow`, which
  the page's `window.open` wrapper calls first, now waits (at most 1 s) until the previous
  window is bound. Chromium binds the window only after `onCreateWindow` returns, so handing
  it over sooner isn't possible. The race was seen once and couldn't be forced; a test of two
  opens in one script passed before and after.

## HP's own behaviour, seen on both machines

Not Lunacy's to change (rule 0, the bus tells the truth); recorded for the webOS CE project.

- **A contact first heard of as a server `Change` is stored with `"_id": null`.** Zoho's first
  contacts sync sent nothing, and its own "Test User" contact came later as a Change, not an
  Add. mojomail-eas looks the server id up, finds nothing ("failed to find database ID for
  item") and merges an object with `_id: null`, which db8 stores under that id. The TouchPad
  has exactly that record (`get {"ids":[null]}` returns it), and a later `put` with a null id
  there says "_rev must be specified for existing objects". The contacts linker then throws
  `Cannot read property 'getDBObject' of null` over it; on the TouchPad no person was made for
  it. Lunacy's db8 does the same, but after Lunacy restarted, its Contacts did list Test User,
  where the TouchPad's doesn't: a difference not yet looked into.
- **The TouchPad doubled its own event.** An event put into the EAS calendar on the TouchPad
  went up and came back from Zoho as an Add with another server id, so the TouchPad shows it
  twice (America/New_York and America/Toronto). Lunacy, which only received it, has it once.
- **Compose's address field.** `enyo.AddressingPopup.inputFilterCleared` calls
  `this.$.list.cancelSearch()` before the popup has ever opened and made its list: an uncaught
  TypeError at `AddressingPopup.js:222`. Text typed into the field while its popup shows was
  invisible on the Galaxy Tab; whether the TouchPad does the same wasn't checked.

## Herrie's mojomail-legacy-eas

[Herrie82/mojomail-legacy-eas](https://github.com/Herrie82/mojomail-legacy-eas) (2026-01) adds an
`eas/` folder to LG's mojomail: a new EAS client written from the protocol specifications and
the original binary's symbols. Its own README lists SendMail, MoveItems, MeetingResponse,
Search (GAL), attachments and smart reply/forward as still to do. Palm's binary does all of
those and runs unchanged, so Lunacy uses it, and nothing from the rewrite was needed. The
rewrite would matter where the original can't go: EAS 16.x (the original stops at 14.1), or
an arm64 build without qemu.

## For the webOS CE project

What CE could take from this:

- The three HP behaviours above are fixable only in mojomail-eas (the null id: treat a
  Change for an unknown server id as an Add) and in Enyo's addressing library (guard
  `this.$.list`). Lunacy will take either if CE changes it.
- `/etc/palm/disable_eas_security_policies` is a file the binary checks (its strings name
  it), presumably turning policy enforcement off. Not tried.
- The measured `reserveIds`, `getTimeZoneFromEasData` and null-id answers above are the
  TouchPad's; anyone reimplementing db8 or the system service (LuneOS, Herrie's rewrite) needs
  the same.

## Open

- **Security policies.** A server that sends a policy (passcode length, lock timeout, wipe)
  has Email ask `com.palm.systemmanager` (`getSecurityPolicy`, `setDevicePasscode`,
  `getDeviceLockMode`), which LunaSysMgr's EASPolicyManager answered and enforced with webOS's
  lock screen. Lunacy registers none of them (fix-log: Android's lock screen is the real one),
  so such an account can't be added. Possible answer, for codepoet to decide: report the
  policy as met when Android has a secure lock screen (`KeyguardManager.isKeyguardSecure`), and
  ask the user to set one in Android's settings otherwise. Not testable with Zoho.
- Tasks: the template's TASKS capability is there, and mojomail-eas syncs task lists, but
  Lunacy has no Tasks app and Zoho's account showed none.
- Untried: attachments, meeting invitations from someone else (`respondToMeetingInvitations`),
  moving mail between folders, autodiscover, a real Exchange or Office 365 server, Android 5,
  64-bit-only devices (qemu), deleting the account.
