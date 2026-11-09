# Roadmap

Priority order: Enyo 1 → Mojo → JS services → PDK native. Each phase has an exit criterion,
and a phase is finished when its criterion is met, not when its task list runs out.

## Before 1.0 (codepoet, 2026-11-08)

The only items before 1.0. Everything else on this page, the open items below included, comes
after 1.0.

- **Exchange (Microsoft Outlook over EAS):**
  - ~~recurring events with exceptions~~ done 2026-11-08: both ways, on the Fold3
    ([outlook-eas.md](outlook-eas.md), "Recurring exceptions");
  - ~~a time zone other than the device's~~ done 2026-11-08 ([outlook-eas.md](outlook-eas.md),
    "Time zones");
  - ~~the validator reporting a throttle (a 503) as a bad login~~ done 2026-11-08
    ([outlook-eas.md](outlook-eas.md), "Sign-in messages");
  - ~~telling a person plainly when their organisation's admin has to approve the app~~ done
    2026-11-08 ([outlook-eas.md](outlook-eas.md), "Sign-in messages"); to be seen against a
    tenant that forbids user consent.
- ~~**Email's "Change login settings"** for Google and Microsoft accounts~~ done 2026-11-08:
  Email opens the account type's own sign-in page there ([oauth.md](oauth.md), Open); a change
  to Email, which codepoet approved as a rule 5 exception.
- ~~**The code review's "before 1.0"**~~ done 2026-11-08 ([code-review-2026-11-03.md](code-review-2026-11-03.md),
  "Left by judgment": `animateValue` at its last sites; `ScrollMasks` and the drawer were left
  on 2026-11-04 for reasons recorded there):
  the TouchPad measurements (B4, B5, B6, S5, S7/S8, S14, §4.3) were made on 2026-11-03 and acted
  on 2026-11-04, and `compat.js` was split the same day. Left: the rest of §3.1 (`animateValue`
  at 6 of its 24 sites; the scroll masks, drawers and dashboard rows, each against its own
  measured numbers).
- ~~**Exhibition:** a button in its settings that opens Android's Screen Saver settings~~ done
  2026-11-08 (luna-deltas B24).

Before shipping 1.0, as for every release: the latest App Catalog bundled (6.2.2931 as of
2026-11-08, the same as the Museum's).

## Where things stand (2026-11-06)

**Tests (2026-11-09):** the release test plan ([test-plan.md](test-plan.md)), after webOS CE's:
`./test.sh` runs everything that needs no device (and `build.sh release` stops on a failure),
`Workbench/device-test.sh` everything on a device a script can decide (42 checks on the Fold3,
each seen failing once), and the rest is a list worked by hand. Next: the release candidate,
with the plan's first full round.

**OAuth for mail, for 0.9.5 (started 2026-11-05, pushed through 25d82d7; b64149a local):**
Google and Microsoft account types whose sign-in page uses OAuth on the device or through the
broker, with Palm's mail services logging in with XOAUTH2 under them, unchanged
([oauth.md](oauth.md)). Where it stands:
- **Gmail:** signs in (Android's browser), syncs and sends, on the Fold3. Google's client is in
  testing (test users only, at most 100); public use needs Google's restricted-scope
  verification, which codepoet started on 2026-11-05. A security assessment (CASA, by a paid
  lab) is tied to reaching the data through a server; signing in on the device keeps Gmail off
  any server of ours, but the broker would put Google's tokens through one ([oauth.md](oauth.md)).
- **Microsoft 365:** IMAP and SMTP work (Fold3). Exchange ActiveSync through Palm's own
  mojomail-eas reaches Exchange Online through five adapters in the preload (16.1 version,
  status 142 as 449, DeviceInformation, newer code pages dropped from replies, sends as
  ComposeMail): mail arrives and sends (new, reply, forward) on the A7 (2026-11-06). Since
  2026-11-08 an account type of its own, "Microsoft 365", works on the Fold3: mail, push through
  Ping, sending, contacts and calendar from the device (a sixth adapter rewrites calendar items
  for 16.x), with Microsoft's back-off kept and no-token failures retried
  ([outlook-eas.md](outlook-eas.md)). Earlier:
  contacts and calendar items and the Fold3 are still to see. Since 2026-11-08 Add Account has
  one "Microsoft Outlook" entry (codepoet): Exchange, with personal accounts falling back to IMAP.
- **Outlook.com (personal):** Microsoft's own bug: its IMAP refuses OAuth logins for personal
  accounts ("authenticated but not connected"), reproduced outside Lunacy; the EAS scope is
  refused for them too.
- **Next:** the "Before 1.0" list above. After 1.0: invitations; the broker's
  `google` and `microsoft` apps on oauth.wosa.link; codepoet's call on Email's "Change login
  settings"; luna-deltas B23. Herrie's mojomail-legacy-eas rewrite was looked at and isn't usable
  yet (a skeleton: no password sent, blocking network calls, stubbed sends); it builds here with
  the PDK's GCC 4.3.3 and HP's CE build-support headers.

**0.9.5** (2026-11-09), modern mail accounts: Microsoft and Google sign in with OAuth, and work
and school Microsoft accounts get mail, contacts and calendar over Exchange. Everything since 0.9.4:

- **One "Microsoft Outlook" entry** in Add Account, "Email (Calendar and Contacts, if
  available)". A work or school account (Microsoft 365) gets Exchange ActiveSync: mail with
  push, contacts and calendar both ways, sending, replies and forwards. A personal account,
  which Microsoft keeps off Exchange, signs in for IMAP mail in the same sign-in. Palm's own
  mail service runs it unchanged, carried to Exchange Online's ActiveSync 16.1 by Lunacy's
  adapters ([oauth.md](oauth.md), [outlook-eas.md](outlook-eas.md)). The legacy "Microsoft
  Exchange" type is as it was.
- **Calendars over Exchange:** a repeating event with one occurrence moved or deleted goes both
  ways; an event in another time zone keeps its time, for the legacy Exchange type too (webOS
  sent its zones an hour off in the week before a change).
- **Plain words when a sign-in fails:** a busy or throttled server is "temporarily
  unavailable", not a wrong password; Exchange's back-off is honoured; an organisation whose
  administrator has to approve the app says so.
- **Email's "Change login settings"** opens a Google or Microsoft account's own sign-in page.
- **Email no longer flashes** as it opens with several accounts (db8 woke every watcher on an
  update that changed nothing; 16 reloads became 1).
- **Messaging:** a chat's avatar is never a broken image.
- **Exhibition:** a button to Android's screen saver settings; its app list no longer doubled.
- **Fixes:** a photo shared from Photos is attached to the mail; Home from an Android app comes
  back to Lunacy as it was; Email's signature; Device Info's dialog titles.
- **Under the hood:** a release test plan, after webOS CE's ([test-plan.md](test-plan.md)):
  `./test.sh` before every release build, `Workbench/device-test.sh` on every device, and the
  PDK sweep as scenarios and a random sample.

**0.9.5 test round (2026-11-09), build 578 (c922796)**, the first under [test-plan.md](test-plan.md):
`./test.sh` 8 passed, 0 failed (in `build.sh release`); six APKs in `out/SHA256SUMS`. Automated
results in [test-results/578](test-results/578):

| Device | Device pass | PDK sweep |
|---|---|---|
| Galaxy Z Fold3 (Android 15, arm64) | 43 passed, 0 failed | 16 of 16 started |
| Pixel Tablet (Android 17, arm64 only, qemu) | 32 passed, 0 failed | 16 of 16 started |
| HP 10 G2 (Android 5.0.1, arm32) | 32 passed, 0 failed | 15 of 15 started |

Every sweep screenshot showed its expected screen. Caught by the round, in the tools: the HP's
log buffer turned over before the sweep read it (now streamed), and it logs no native-service
list (now counted from the starts). Open: ExZeus's first-run message sits left of centre on both
tablets, not compared with a TouchPad. Not run: the A7 Lite, Nexus 5 and
Nexus 7 (off the network that day); the [Human] sections 7 to 14.

**0.9.3** (2026-11-06), a point release for testers, for the problems 0.9.2's testers met
running Lunacy as an ordinary app rather than the home screen. Everything since 0.9.2:

- **Accounts no longer hang after a start.** The photo, video and music index was read on
  db8's own thread at every cold start, holding every account list, Email's folders and the
  Android account's copy behind it: 48 s for the first account list on the Pixel Tablet with
  3,000 photos, now 6.6 s. The index is read in the background, as webOS's filenotifyd did.
- **Lunacy keeps running in the background** when it isn't the home screen and has a mail
  account: one silent notification, "Keeping your mail up to date", at Android's lowest
  priority (Android froze it about a minute after it was left, which stopped mail and the
  Android account's copy). None as the home screen.
- **First Use asks to let Lunacy run in the background** (Android's battery optimisation), and
  says what may misbehave without it; an install that has done First Use gets that page once.
- **Save a Log**, in Device Info's App Menu: a file in Android's Download folder to send with a
  bug report, with a Share button. Passwords, contacts and calendar entries are left out.
- **No "built for an older version of Android" warning** on Android 14 and later with the
  32-bit build, which now targets API 28 as the 64-bit one does.
- **Signing in to Google and Microsoft mail** (work in progress for 0.9.5, [oauth.md](oauth.md)):
  Google and Microsoft in Email's and Accounts' lists. Google lets in only the accounts added to
  its test list while its review is pending; Microsoft 365 work and school accounts sign in and
  sync over IMAP; Outlook.com personal accounts are refused by Microsoft's own servers.
- **Fixes:** after an update on Android 17, a second start Android makes no longer leaves a
  half-built copy of Lunacy's system running beside the real one.

**0.9.2 reports from testers (2026-11-06), open.** Three testers, all running Lunacy as an
ordinary app rather than the home screen, reported problems:
- user 1: after the update Email wanted a new login, then showed no folders, then a blank card;
- user 2 (Galaxy S21): Photos showed no or empty albums, account lists hang, and the Android
  account came late without data;
- user 3: the Android account reached Contacts and Calendar late, with no data.

None of it was reproduced here. Tried on the Pixel Tablet (stand-alone):
- published 0.9.1 with IMAP and Android accounts and 3,000 photos, then 0.9.2 installed over it;
- the phone layout;
- limited photo access;
- the Android account with about 650 contacts and 300 events.

On the A7 Lite, Lunacy was hard-killed mid-sync (as a recents swipe kills a stand-alone
Lunacy) and recovered fully.

Added to find out more: Device Info's **Save a Log** ([dev-workflow.md](dev-workflow.md),
"A tester's log"). Two findings on the way, neither the testers' cause:
- **Two shells after an update:** after an update, Android 17's System UI relaunches the app
  with CLEAR_TASK beside the user's own launch. Both shells build a system, and the one
  Android finished tears its system down about 0.4 s later, its services cut off as in a
  hard kill. A check of `isFinishing` in `onCreate` doesn't catch it: it is still false
  there.
- **Nexus 7:** Device Info shows "Node: not available" though the JS services run (the
  version probe fails).

**Found and fixed (2026-11-06), after the reasoning above was turned on the code:** db8's
media mirrors read every photo's EXIF on db8's own thread at each cold start, holding every db8
call: after a cold start the accounts list took 48 s, then 7-19 s a call for two minutes, on the
Pixel Tablet with 3,000 photos. Now read on a thread of their own: 6.6 s, then about 1 s
([fix-log.md](fix-log.md), 2026-11-06). A stand-alone Lunacy starts cold far more often, which is
why only testers who don't use it as the home screen saw it. Also measured: a stand-alone Lunacy
is cached about a minute after the user leaves it, and Android 17 freezes it (Pixel), which
stops the whole webOS system; One UI 6 (A7 Lite) caches it but didn't freeze it in five minutes.
Photos' All Photos grid seemed to leave out an album in portrait: it doesn't. Palm's library
grid (`LibraryMode`) always has 4 columns of 184 px, and beside the 280 px album list in an
800 px portrait card it has 520 px, so each row's fourth album sits past the card's edge until
the grid is slid over the list by its drag handle (Palm adjusts columns only inside an album,
`AlbumGridView.adjustColumnCount`). A TouchPad in portrait (768 px) should do the same; to
confirm on the reference TouchPad before calling it the device's behaviour.

Still to do: an uninstall followed by a restore from Android's backup, which brings the
accounts back without their passwords by design and is still untried.

**0.9.2** (2026-11-05), a point release for testers: the code review's clean-up and webOS CE's
emoji key. Everything since 0.9.1:

- **LunaKeyboard:** webOS CE 3.2's emoji key, beside the symbol key: four pages of emoji over
  the top four rows, in CE's order and OpenMoji's art, shift to turn the page, ABC to go back.
- **Links:** a `mailto:` in a webOS app opens webOS Email, as the TouchPad's handler table
  says (and `sms:`/`im:` Messaging, `contact:` Contacts), when the app is installed.
- **The bus answers as the TouchPad does** (measured there): db8's errors for a parameter of
  the wrong type, `pauseDownload` and `resumeDownload`, `com.palm.systemmanager` private,
  `removeLaunchPoint`'s errors, the activity manager's `enable`/`disable`.
- **Security:** a JS service calls the bus as itself, never as an app it names; the Web app
  opens only webOS paths for `file:` URLs, not Lunacy's own files; an app's origin is served
  to the app, not to a website; WebSQL's migration takes the app from its origin.
- **Links:** a card an app opens as it is relaunched comes up on top (a mailto: brought up
  Email's compose card behind the inbox, a target=_blank link in the Web app a card nobody
  saw); a target=_blank link in an email, or a new window in any app's web view, opens in the
  Web app, as on the TouchPad.
- **Just Type:** the empty panel has its magnifier and "Start typing to search or create".
- **Fixes:** every activity is saved at start (five or six weren't: a revision race); a fold
  or unfold no longer leaves the system menu's radio watchers and the shortcut watch running
  twice; `curl`'s headers in any case and a connect timeout that times the connection; PDK
  2D frames never drawn half written, and the screen never waits for one to be copied; a debug
  build no longer stops at db8's daily purge; a database kept by an older WebView is found
  whatever the app id's case; a launcher group's panel shows current titles; Treo New has the
  other ringtones' tags.
- **Caught in testing, before release:** a PDK lock shared by every socket halved games' frame
  rates and kept Fruit Ninja's first frame off the screen on slow tablets (the A7 Lite); now a
  lock per socket, with a test the PDK build runs.
- **Under the hood:** the code review's clean-up (its §2 to §10): `ShellActivity` is five
  classes (the card layer's owner, the apps' lifecycle, the shell's chrome, notifications,
  Exhibition), the Launcher, the activity manager, the browser views and `compat.js` split by
  what they do, one way for services to answer, check callers and keep subscribers, the bus on
  one thread, and 32 unit tests, db8's queries on a real SQLite among them.

**0.9.1** (2026-11-04, build 437), a point release. Everything since 0.9.0:

- **Music:** Palm's Music app (HP's Apache 2.0 core app), its library from Android's media
  library with albums, artists, genres and covers as the TouchPad filed them, playing on with
  the screen off behind Android's media notification, its volume Android's media volume, and
  a phone layout. An Android app that is playing (Plexamp, say) gets Music's dashboard, with
  previous, play-pause and next.
- **Ringtones:** the TouchPad's ten short ringtones, shared with Android as notification
  sounds, and Palm's ringtone picker with preview for webOS apps (Messaging's). The system
  ringtone stays Android's setting.
- **Notifications:** an Android notification dismissed in Lunacy goes from Android too (it
  came back at every start); Blink Notifications pulses a phone's notification light; Show
  When Locked is gone; the open menu's tab meets the drop-down.
- **Messaging:** the chat slides over the list on a phone; a buddy no longer shows another
  conversation's unread count; the message hint stays on one line.
- **Synergy connectors:** the BlueBubbles connector works: services using `require.paths`
  run, a column with only a max-width fills the card, year-month-day dates read as the
  TouchPad read them, and an interval schedule waits for its first tick (it synced 102 times
  a minute).
- **Look:** Just Type's tabs fit a phone, launcher labels clear the edit frame, Just Type
  settings' App Catalog icon, and the device's own camera and phone wear webOS's icons.
- **Wallpapers:** the TouchPad's wallpapers are shared with Android (Pictures/webOS
  Wallpapers), so Android's gallery and wallpaper chooser have them too.
- **File picker:** Palm's own, from the TouchPad: albums, the photo grid, preview and crop,
  videos, music and documents, reading the media index.
- **Launcher:** the first page starts in webOS's order, and a phone's dock is Web, Email,
  Calendar and App Catalog.
- **Enyo:** the spinner turns as a ring on a high-density screen (patch 0013), and lists coast
  and spring back on a 120 Hz screen (patch 0014).
- **Fixes:** a window closed twice no longer crashes Lunacy, and a page's unload handlers run
  when Lunacy closes its window, on every Android's engine.
- **LunaKeyboard:** webOS's Text Assist: auto-correction with the TouchPad's plop, dotted
  underline and bubble of alternates (Alan Morford's Pre Keyboard engine and word list),
  auto-capitalization, double space for period and shortcuts; Return says Enter, and Done in a
  single-line field; a phone's number row is readable (superscripts); built for Android 15.

**0.9.0, a preview release** (2026-11-03, build 394), the last before the OAuth work for
Exchange and Google mail. Everything since 0.8.4:

- **System menu and settings:** the TouchPad's whole system menu, its brightness slider
  coming down as well as up and dimming at once, the tab fading with the menu. Bluetooth,
  Date & Time, Location Services and Regional Settings open Android's screens under the
  TouchPad's names and icons. The Settings page starts in alphabetical order, as on webOS;
  Help shows its tablet layout on a tablet; First Use's last page says where the Android
  account is.
- **Just Type:** what apps add to it (searches, Quick Actions, content such as Memos,
  Calendar events, bookmarks and history), web search engines to choose and switch, Palm's
  Just Type settings app, and icons drawn smoothly.
- **Contacts and Calendar:**
  - An "Android" account copies Android's contacts and calendars into webOS, one way: Lunacy
    never writes to Android. Off until added; each calendar can be chosen, and copying
    reminders is the owner's switch.
  - Import vCards and Import Calendar (.ics), from webOS CE, into the on-device account.
  - On a phone or a folded Fold, Calendar draws the Pre3's layout and Contacts a list or a
    contact, both instead of Phone Zoom. Calendar's header keeps its round ends at any width,
    and its view menu holds its size on older WebViews.
  - CE's rewrite of repeat expansion: repeats across daylight-saving changes are right, and
    Android's zone rules decide daylight saving.
  - Exchange time zones chosen as a TouchPad chose them (Sydney isn't Brisbane; US Eastern
    keeps daylight saving), and Outlook's Windows zone names understood, so webcal events
    no longer shift an hour after a clock change.
  - People's details and events stay out of Android's log; the App Menu opens in an app
    started cold (Calendar).
- **PDK games:** Guitar Hero 5, Family Guy, The Oregon Trail and H.A.W.X. play ([pdk.md](pdk.md)
  §6i): taps land where touched in phone games, two games at once no longer crash the
  shell, and a game's intro movie ends at once instead of hanging or crashing (Lunacy's
  stand-in for libSDL_cinema, the PDK's movie library, plays nothing yet).
- **Web:** on a narrow card the progress bar runs along the bottom of the chrome.
- **Installs:** an older installed copy of a bundled app no longer hides the newer bundled
  one (App Catalog stops asking to update itself).
- **From the code review** ([code-review-2026-11-03.md](code-review-2026-11-03.md), its triage):
  bus replies with a line separator no longer vanish; WebSQL refuses ATTACH; credentials
  stay off another site after a redirect; names apps give can't climb out of their folders
  (wallpapers, packages, sounds, images saved from Web); a malformed request or a crafted
  email no longer crashes Lunacy; music files with "+" in their names play; a fold no longer
  loses pending shell actions; Android's backup leaves out saved passwords; the first unit
  tests.
- **Known issues:** NesEm HD quits on the Fold3's cover screen. (PDK games' intro movies,
  skipped until then, play from 2026-11-07: [pdk.md](pdk.md) §6j.) (Video Player's volume keys, listed here at
  first, work: while a video plays Android points them at media, measured 2026-11-07 on the
  Fold3, Android 15, and the HP 10 G2, Android 5; Lunacy's `media/lockVolumeKeys` came later.)

**Next (codepoet, 2026-11-05):** 0.9.2 for others to bang on, then OAuth for Exchange and
Google mail as 0.9.5, then preparing an F-Droid launch. Earlier plan (2026-11-03): 0.9.x point
releases until 0.9 is stable, then OAuth for Exchange and Google mail, then preparing an
F-Droid launch. First up: the TouchPad
measurements from the code review are done
([code-review-2026-11-03.md](code-review-2026-11-03.md), "Measured on the TouchPad"), and
three changes they decided (db8's typed-parameter errors, `pauseDownload` and
`resumeDownload` as the device answers them, `com.palm.systemmanager` private-bus only) are
done and tested (2026-11-04), for the release after 0.9.1, with the review's other open
security items codepoet decided that day: B5 (the activity manager's `enable`/`disable`), S7
and S8 (the Web app's app origins and `file:` URLs) and two of S14's. Then the review's cleanup before OAuth (its triage: §5, §4.2, §4.1 with
§3.2 and S2), since OAuth works in the accounts and services.

**0.8.4, a mini release** of the fixes since 0.8.3 (2026-11-01, build 358 replacing build 352):

- **PDK games:** the six emulators play (2600.emu, EMU7800, MD.emu, NesEm HD, Snes9x EX,
  SuperNES): launchers that exec another binary, `uname`'s machine and the PDK version as the
  TouchPad answers them (`armv7l`, 303), `scandir`, `getcwd`, file modes, a first frame before
  the surface ([pdk.md](pdk.md) §6h, with what is still open). Start scripts work with them.
- **Web's dialogs:** a page's alert, confirm and prompt, which stopped every card, are drawn
  natively (luna-deltas B18). A form that asked a question on Send looked like a dead button.
- **Web on a narrow card:** a layout of its own instead of Phone Zoom: the address on top, the
  tools along the bottom. A foldable's cover shows it at the Pre3's 320 x 532.
- **Trust Always** lasts across restarts, and holds for that certificate only.
- **Hung cards:** the keyboard closing while a card came up left the card view deaf to touch.
  Send from Web's Share was the way in.
- **JS services** that call the bus while they load start, as on webOS (Topaz Explorer), and a
  margin inside a clipped box stays (its tree's arrows).
- **`/media/internal`** lists its mapped folders (downloads, music and the rest) everywhere:
  Node's `fs.readdir`, and PDK apps by `scandir` and relative paths, so a file browser or a
  game's picker finds what Web downloaded.
- **The bus and the SDK:** a shell going no longer takes the luna-send socket and the novacom
  relay from the one replacing it (`palm-install`'s "no response").

**0.8.3, a mini release** of the fixes since 0.8.2, for the community to try:

- **Notifications:** swiping a dashboard away no longer opens its app. The row takes the
  whole touch, as LunaSysMgr's did, and the app hears only a real tap. On a phone's bottom
  area and the tablet's drop-down.
- **A touch the shell takes away no longer taps the app** under the finger: a cancelled
  touch is released off the page, as webOS did.
- **Exhibition:** an app opened from a dashboard, webOS or Android, ends Exhibition, as a
  card arriving did on the TouchPad.
- **Photos' slideshow** slides instead of slamming on fast devices: timer animations run at
  the TouchPad's measured pace (luna-deltas B17).
- **App Catalog 6.2.2931:** download counts, Find More by what an app provides.
- Release builds no longer open their cards to `chrome://inspect`.

**0.8.2, a mini release** of the fixes since 0.8.0, at codepoet's call, so people can try them:

- **Duplicate calendar events and email:** a recreated shell left the old one's bus, db8 and
  activity manager running, so syncs ran several times over; a destroyed shell now stops all
  of it, and a process keeps one shell. Email, Calendar and Messaging run headless from boot,
  as on the TouchPad, so new mail notifies.
- **Phone notifications:** a swipe up from the bottom edge cards the app again; an alert
  clears the scrim; rows dismiss once.
- **Edge swipe for home** (Screen & Lock, on by default): a swipe in from a side edge cards
  the app; greyed out when Android's gesture navigation already does it.
- **Exhibition:** the Time faces fit a phone or a cover screen; Photos' slideshow shows,
  scaled to fit at the TouchPad's dock size, with its controls fading as on the TouchPad
  (an app's named viewport scale now counts TouchPad px, as on webOS, so they stay hidden).
- **Apps:** a link out of an app opens in the browser instead of replacing the app (SimpleChat's
  images); `timeout/set` ignores `$` keys as the TouchPad does; Messaging's carrier settings.
- **PDK:** webOS 3's portability, read in libnapp's own device numbers (ExZeus fills the
  screen; Worms upright); Worms and Atlantis Sky Patrol HD play (OpenSSL 0.9.8, Palm's SDL
  calls, a TouchPad-sized `/proc/meminfo`); MP3 music; Cut the Rope's levels start.
- The status bar's ▾ no longer shows while the group fades after a banner with no icons.

**0.8.0 release, build 296:** 0.7.4 RC build 289 renamed a minor release at codepoet's
call, with one fix on top: the status bar's notification icons fade out under a banner and
the group's separator rides out at the banner's edge, as LunaCE's do (the banner text had
been drawn over them). The keyboard now carries the same version and build number.
codepoet approved it and releases it to the community for testing and feedback. Since 0.7.2 (build
249):

- **Foldables:** the shell rebuilds in the phone or tablet layout when a foldable folds or
  opens, keeping every card ([phone.md](phone.md) §1, "Foldables"); Email's message and IAmA
  reddit's right pane fit the opened screen.
- **Phone mode is the Pre3's:** notifications along the bottom (rule 0 now covers the Pre3 in
  phone mode), a centred launcher grid with the first row closer under the tabs.
- **Android's notifications as webOS dashboards**, kept calm (no lock-screen work, no per-app
  choice).
- **Launcher and Software Manager:** hide any app, webOS or Android; pin a webOS app to
  Android's home screen, and other apps can pin shortcuts to Lunacy's launcher.
- **PDK:** native on 64-bit devices that run 32-bit code; Temple Run 2 and Fruit Ninja play;
  ScummVM runs; every finger's motion; tilt in webOS's own frame (measured on the TouchPad
  with `Workbench/probe/accelprobe`); the picture keeps its shape when the screen turns for
  a game ([pdk.md](pdk.md)).
- **Apps:** Photos & Videos 3.2.0 from webOS CE (rotate, crop; videos play in Android's
  player); Memos to the card's bottom (A22); an `.apk` opens in Android's installer; Device
  Info's long service ids.
- **Enyo 0011 and 0012** (a native web view's holes, following a moving box), also in the SDK
  (webos-sdk-redux 5d77edc).

Tested on the HP 10 G2, the Nexus 5, the Galaxy Z Fold3 and the Pixel Tablet; Temple Run 2's
tilt confirmed right on all four by codepoet; on the Fold3 the picture took its new place at its old size after the turn, fixed in 289 (build 287 had it).

Open from this round: the qemu path on the Pixel Tablet (to fix the emulator); Fruit Ninja is
a little slow; `glGetAttachedShaders`; the A12 margin question for the TouchPad (does a
`-webkit-box` child's own margin collapse through its box); Device Info on the Fold3's outer
screen with an installed build.

## How it got here

One line per milestone; the detail is in [fix-log.md](fix-log.md), the topic docs and git.

- **2026-09-20, ready to show:** one Gradle root, two APKs (shell and the optional keyboard);
  Settings as three cases (Lunacy's own Device Info, Palm's Screen & Lock, Android shortcuts);
  Exhibition, also as Android's screen saver; the file picker; Mojo's first app.
- **2026-09-21:** the Mojo suite codepoet named runs, with Palm's Video Player (Mojo 2); the
  Pre3 and a stock 3.0.5 TouchPad measured ([pre3.md](pre3.md); HP's `/etc/palm` is
  byte-identical to CE's).
- **2026-09-22, 0.2.0:** [luna-deltas.md](luna-deltas.md)'s A and B lists measured and done;
  the viewport declared rather than inferred (the rules in [architecture.md](architecture.md)).
- **2026-09-23, 0.2.5:** the first community reports: other screen scales, dashboards'
  viewports, the tap radius, Enyo patch 0003, target API 24.
- **2026-09-26, 0.3.0:** a webOS root filesystem, install scripts, webOS Accounts, App Catalog
  installs through `appInstallService`; the APK from 82 to 48 MB; the startup card first.
- **2026-10-01, 0.4.0:** arm64 and Android 17, one APK per ABI, First Use, the Nexus 7.
- **2026-10-02:** the phone layout ([phone.md](phone.md)); WebSQL over SQLite where the WebView
  has none; the first PDK apps (Keen, Transformers G1; [pdk.md](pdk.md)).
- **2026-10-03, 0.5.0:** Mojo flicks, the launcher's over-scroll, busybox built against bionic,
  the layout choice in Screen & Lock (B11), the keyboard at whole-number scales.
- **2026-10-04, 0.5.5:** PVRTC decoding, Android's wallpaper following webOS's, Palm's Web app
  on a native WebView, Calculator 3.2.0, Paste, webOS text selection.
- **2026-10-05:** the SDK's tools over adb (webos-sdk-redux 0.4's novacomd); the Enyo fork
  rebased on the TouchPad's own framework; apkenv's Android ports run as PDK apps.
- **2026-10-06, 0.6.0 and 0.6.1:** native PDK on Android 10+ (`libenosys`), GPU driver fixes
  (Tegra 3, Adreno 620 and 330); foldables move between screens without a restart prompt.
- **2026-10-07, 0.7.0 built:** Email on IMAP/SMTP with Palm's native mail services, Calendar and
  Contacts (webOS CE's, local), hybrid apps (Quick Office, Adobe Reader), documents' index.
- **2026-10-08:** sleep and wake mapped to Android; Photos & Videos with the media indexer.
- **2026-10-09, 0.7.0 released** (b2362ec, build 216): local-network proxy, WebView 44 fixes,
  Enyo 0010, content handlers, A12; then A19 and time-zone rules.
- **2026-10-10:** Exchange ActiveSync with its security policies ([eas.md](eas.md)); CoreApps/;
  Messaging 3.2.0 with webOS CE's IM stack ([messaging.md](messaging.md)).
- **2026-10-12/13, 0.7.2 RC:** webOS Chat live over `oscar.webosarchive.org`; the IM transport
  under qemu; chat threader 1.1.0.3; Db8 null and `incDel` fixes.
- **2026-10-16, 0.7.4 RC:** foldables rebuild in place; Pre3 phone mode; Android's
  notifications as dashboards; PDK native on 64-bit with Temple Run 2 and Fruit Ninja.
- **2026-10-17, 0.8.0** (build 296; the keyboard now carries the same version): 0.7.4 as a minor release, with the banner over the
  notification icons fixed.
- **2026-10-19, 0.8.2:** a mini release of the fixes since: shell teardown (duplicate syncs),
  headless mail from boot, edge swipe for home, Exhibition, links out, Worms and Atlantis.
- **2026-10-29, 0.8.3:** a mini release: dashboard swipes no longer open apps, Exhibition
  ends when an app opens, Photos' slideshow at the TouchPad's pace, App Catalog 6.2.2931.
- **2026-11-01, 0.8.4:** a mini release: the six PDK emulators, Web's dialogs and narrow
  layout, Trust Always that lasts, hung cards, the bus socket.
- **2026-11-03, 0.9.0** (build 394): a preview release: the system menu, settings shortcuts,
  Just Type's app integration, the Android account, Contacts' and Calendar's imports and phone
  layouts, four more PDK games, and the code review's fixes before release.
- **2026-11-04, 0.9.1** (build 437): a point release: Music, ringtones, Android notifications
  that stay dismissed, Blink Notifications, Messaging on a phone, the BlueBubbles connector,
  shared wallpapers, Palm's file picker, smooth scrolling at 120 Hz, the keyboard's Text Assist.

## Open, carried forward (after 1.0)

Gathered from the dated notes this section replaced (git has them in full). Check each before
starting; some may have been settled since without a note. All of it comes after 1.0
(codepoet, 2026-11-08), except what "Before 1.0" at the top lists.

- **Chat:** longevity on the devices; webOS CE's startup hang (PmLogLib's semaphore; take CE's
  fix if it lands in the transport); the Add Buddy invitation message, which the transport drops.
- **Exchange:** a real server's policy, tasks, attachments.
- **PDK intro movies:** done 2026-11-07, Android's player behind Palm's API ([pdk.md](pdk.md)
  §6j). Still to see: Oregon Trail, Asphalt 5 and Nova 2's movies, the end event's path on
  each, and a movie on the 32-bit devices.
- **Launching an app as the shell starts (0.9.0 sweep):** an `--es launch` of Calendar, Clock
  or Email in the intent that starts Lunacy can come up with no card (Calendar: "no card
  opened"; the Nexus 5 showed the launcher), seemingly racing the same app's own start at
  boot. A tap once the shell is up opens them (codepoet, HP 10 G2). Not compared with 0.8.4.
- **Video Player:** `com.palm.filenotifyd`'s `state` (whether `/media/internal` is mounted and
  scanned) isn't there. (`com.palm.audio`'s `media/lockVolumeKeys` is, since Music's port, and
  while a video plays Android points the volume keys at media itself: measured 2026-11-07.)
- **Email:** attachments; the Android 5 devices untried; PmLogLib's levels need PmLogDaemon's
  shared memory (only errors are logged unless a service is started with `files/pdk/args`).
- **Photos & Videos 3.2.0:** the editor dragged with a finger, and on the 32-bit build's
  devices (Chromium 37); on a 320-px-wide card (the Fold3's cover screen) its toolbars and
  the single-photo toolbar run off the right edge, the app's own layout; whether the
  TouchPad's thumbnails of EXIF-rotated photos were upright (architecture, "Photos and
  videos"); Replace Original on an Android 11+ photo another app owns.
- **Android packages (luna-deltas A24):** an install carried through past Android's "unknown
  sources" prompt, and on Android 5-6 (file URI); a Play build would have to drop or justify
  `REQUEST_INSTALL_PACKAGES`.
- **Calendar:** repeats on the screen, snooze. **Contacts:** vCard export, a `.vcf` opened from outside the
  app (the stock importer), favorites. Both untried beyond the A7 Lite, except Import vCards,
  tested on the Fold3 (2026-11-02).
- **The Android account:** a real account's contacts and calendars (the Fold3's Android stores
  were empty, so it ran on device-only test data); "keep data" on Remove Account; attendees.
  Calendar still has Phone Zoom.
- **Hybrid apps:** a visible plugin's picture; callable collections on Chromium 37.
- **PDK:** OpenSSL 0.9.8 and curl for the apps that link them; the other ten apkenv ports; on
  the A7 Lite's 64-bit build Where's My Water levels take 20 to 65 s to load under qemu;
  `palm-install` of a 24 MB package was reset by the relay.
- **SDK:** compare `palm-log` output for the Web app's plugin calls with the TouchPad's; Mojo
  samples don't open in a desktop browser (webos-sdk-redux FRAMEWORKS.md).
- **Web app:** the download manager's history; the TouchPad's default bookmarks.
- **Mojo:** the `WebView` widget (`BrowserAdapter`), so Quick Tip Calculator's first scene is
  an empty panel.
- **WebSQL:** the copy-in of a WebView's own WebSQL files is untested; not yet run on the
  32-bit build's devices.
- **Phones:** whether a focused field should raise the keyboard when an app focuses it itself
  (a Pre3 had a hardware keyboard). First Use not yet run on the HP 10 G2.
- **Installs and accounts:** App Catalog 6.2 as Lunacy's pack-in; the phones catalog on a phone;
  cookies are shared between apps' origins (`.media.cryptofs.apps`); an app a package script put
  in the rootfs can't be removed from the launcher; Enyo's libraries other than `networkproxy`
  are upstream's, not the TouchPad's newer ones.
- **PDK (2026-11-07, [pdk.md](pdk.md) §6k):** N.O.V.A. 2 crashes as a mission starts, on every
  device: a menu sprite or font the game looks up by name isn't there (a null from its lookup
  at 0xc0f70); next is gdb through qemu on the desk. Why Asphalt 5's own PVRTC decoder gives
  zeros here and not on the TouchPad (Lunacy now offers PVRTC, so it goes unused). GLES 2
  draws still send their arrays from element 0. `Workbench/pdk-sweep.sh` reports `started=0`
  on the HP 10 G2: Android 5's small log buffer has dropped the launch line by the time it reads.
- **Shell (the 2026-09-22 candidates list):** the emulated card's chrome turning with the
  screen; the rest of the settings apps (Backup, Updates, and others codepoet keeps for Lunacy's own
  or hybrid apps; Bluetooth, Date & Time, Location Services and Regional Settings became
  shortcuts to Android's screens on 2026-11-03); the system menu's VPN drawer (left out: Android keeps VPNs from apps); the rest of Just Type (contacts and Address Lookup, Recent Searches, the default engine's Suggest, OpenSearch engines from pages, and `searchApps`/`listLaunchPoints`; the filter tabs, Search Using…, Quick Actions, content and Palm's Just Type settings app came on 2026-11-03, luna-deltas B6); the border-image seams in Enyo dialogs
  and the Sampler's radio button; a test suite from the apps in fix-log.md, on WebView 37 and
  a current one; luna-deltas A9 and A10.

## Before phase 0: two checks

Both are cheap, and both can change the plan. **Spike 1 is done, on Chromium 37 and 64, plus a
reference probe on a real TouchPad: see [spike-1.md](spike-1.md).**

- **Device spike.** Test on the first target, an HP 10 G2 tablet running Android 5.0.1: a
  bare WebView, stock Enyo 1.0 and three real apps. So far the evidence is desktop Safari
  with a mouse; the target is Android Chromium with touch, which is where wcl struggled.
  Run the same apps on the factory WebView 37 and on the updated WebView (about Chromium
  95), and pick the baseline. The spike also checks:
  - whether WebView 37 can reach current HTTPS sites, including the App Museum;
  - how many live cards 1 GB of RAM can hold.

  A day or two of work shows how narrow phase 1 really is.
- **Corpus survey.** A static scan of the App Museum's packages: framework used (Enyo 1,
  Enyo 2 or bundled, Mojo), framework script paths, `palm://` URIs by frequency,
  `openDatabase` use, bundled JS services and PDK plugins. It sets the service build order,
  and sizes each phase with data instead of guesses.

**Exit:** a written result for each, and the roadmap adjusted to match.

## 0: Skeleton

- Android project: Kotlin with Android Views, `minSdk 21`, and AndroidX pinned to releases
  that still support API 21. One full-screen activity hosts the shell scaffold, and it is
  installed over `adb` on the HP 10 G2.
- The card host serves one bundled Enyo app from its own origin, at its webOS filesystem
  path, so relative framework paths resolve.
- Framework route: `/usr/palm/frameworks/enyo/*` serves an Enyo fork, forked from
  [enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0) and vendored or added as a submodule.
- The bridge: a JavaScript interface, with the bridge script added to each app's HTML at
  serve time. It provides `PalmSystem` and `PalmServiceBridge`, plus the bus router with
  honest errors and the coverage log. All of Lunacy's JS runs on Chromium 37.

**Exit:** a bundled Enyo sample launches from an icon into a card and renders.

## 1: Enyo runs

Start from stock Enyo 1.0. It already runs many popular apps in modern Safari, so the work
is expected to be narrow:
- differences between WebKit and Chromium: border-image, `-webkit-box`, scrollers, touch and
  mouse events;
- making the `PalmSystem` calls that apps expect actually work.

Other work:
- The services apps call while starting (system preferences, locale and time, connection
  status, device info), since apps rarely handle errors from them.
- App lifecycle: headless root windows, `window.open` for extra cards, launch parameters
  and relaunch, `stageReady`, activate and deactivate, back as Escape, the keyboard.
- Global compat layer: the webOS input model (touch delivered as mouse events), injected
  polyfills, serve-time CSS transforms (starting with the border-image rule from spike 1), and the network shim for cross-origin and
  plain-`http` requests.
- `PalmSystem` complete enough for Enyo's own code path (`palmGetResource` with JSON hints,
  `simulateMouseClick`), the `enyo.WebView` control, and the system files apps read.
- Start the Enyo fork from stock, and import the differences the TouchPad's shipped copy
  has where they matter: small code changes, the `resources/` localization folders and
  `lib/networkproxy`. See [spike-1.md](spike-1.md).
- A change log in the Enyo fork, and a fix log recording where each fix landed (framework,
  compat layer, or nowhere general): [fix-log.md](fix-log.md).
- A compatibility test suite: the Enyo samples, codepoet's open apps, and at least one each
  of: a multi-window app, a `noWindow` app, an app that bundles its own Enyo, an Enyo 2 app.
  The suite runs on both the factory and the updated WebView until the spike settles the
  baseline.

**Exit:** the test suite renders and responds correctly on the HP 10 G2 in both orientations,
with no per-app code, and the fix log shows fixes landing in general layers. Phone form
factor is covered too (on a small Android 5 phone, or an emulator at phone size).

## 2: Bus core

- Router (from phase 0) gains cancellation and subscriptions that deliver updates.
- Services in the order the corpus survey and coverage log give. Expected: applicationManager,
  systemservice, connectionmanager, power, preferences.
- db8 on SQLite: kinds, queries, put/merge/del, watch. Its depth follows the survey; third-
  party apps may lean on WebSQL and localStorage far more than on db8.

**Exit:** the test-suite apps that store data keep it across restarts, and no call returns
a faked success.

## 3: Shell

**Brought forward (2026-09-19).** The App Museum already runs and looks right, so shell
work started ahead of phases 1 and 2. The shell is the strongest signal that this is webOS.

- **Reference sources:**
  - [LunaCE](https://github.com/webOSArchive/LunaCE) (Apache 2.0): the source of the build
    running on the reference TouchPad, and of the shell images Lunacy uses.
  - [Docs/luna-shell-reference.md](luna-shell-reference.md): a spec drawn from that source.
  - Screenshots from the TouchPad.
- **Done in the first pass:**
  - status bar (title, wifi, battery, clock);
  - wallpaper;
  - live cards in a card view, with LunaCE's shadow and rounded corners;
  - tap to maximize;
  - swipe up from the bottom edge to minimize, with the card following the finger;
  - horizontal card scrolling;
  - flick up to throw a card away;
  - the "Just type" pill;
  - the quick-launch dock;
  - app launch and relaunch through `applicationManager/launch` and `open`;
  - `noWindow` apps with headless roots;
  - `window.open` opening new cards;
  - `Mojo.stageActivated`/`stageDeactivated`.
- **Second pass:**
  - geometry and timings tuned to the reference TouchPad's configuration (card ratios
    0.55/0.50, easing curves, throw-away thresholds);
  - the launcher (tabs, icon grid, paging, open/close animation);
  - the dock and "Just type" pill to spec;
  - immersive full screen;
  - TouchPad proportions;
  - back button as home;
  - "Lunacy" as the carrier text;
  - app content in TouchPad-sized CSS pixels;
  - dock icons level with the launcher button (54 px below the bar top, measured on the
    TouchPad).
- **Third pass:**
  - whole-number shell and content scale (no seams);
  - Prelude served to apps;
  - flick inertia;
  - status bar backgrounds, separators and ▾ per LunaCE;
  - live battery and wifi;
  - notifications: banners, dashboards with the drop-down, popup alerts;
  - the Lunacy launcher icon.
- **Needs fine tuning:** scroll inertia in apps. Flicks now coast (codepoet: "not perfect,
  but feels better"). Candidates:
  - the order of the flick relative to `mouseup`;
  - Android touch sample rate versus the TouchPad's;
  - Enyo's fixed 20 ms simulation step per animation frame.
- **Next:**
  - card groups/stacks;
  - banners and the dashboard;
  - landscape and portrait layouts;
  - status bar menus;

Planned scope:

- Card view with several live cards; throw-away and minimize gestures; launcher.
- Status bar, banners and the notification dashboard, fed from the bus. Dashboard and popup
  alert windows opened by apps.
- The gesture bar, with immersive mode on Android 5.
- The card memory policy, tuned to 1 GB of RAM.
- Rotation and resizing; phone and tablet layouts.

**Exit:** using several apps at once feels like a TouchPad. codepoet is the judge.

## 4: App Museum

- Bundle the App Museum's own Enyo app (it already runs in modern Safari).
- The `.ipk` reader, ported from wcl, and the package manager.
- Answer the Museum's install requests: `applicationManager/open` for Preware's id with
  `{type: "install", file}`, handled by the package manager.
- At install, note what an app needs that Lunacy lacks (a JS service, a PDK plugin), so the
  compatibility score can say why an app falls short.
- A per-app compatibility score from the coverage log, fed back through the catalog service
  (which webOS Archive owns).

**Exit:** a user can find, install and run popular Enyo apps from the App Museum, all
inside Lunacy.

## 5: Mojo

**Started 2026-09-20**, ahead of its phase: an Exhibition app from the Museum (Flying Toasters)
turned out to be Mojo, and it runs. Palm's own Mojo is served from the reference TouchPad with
its own patch series and NOTICE, as Enyo is; see "Mojo" in the architecture doc for how it is
packaged, and [mojo.md](mojo.md) for everything measured.

**2026-09-21:** the suite codepoet named as the apps that must work all run - drPodder Redux,
IAmA reddit, MeTube, Check Mate and SimpleChat - and with them **Mojo 2**, because MeTube
hands every video to Palm's own Video Player, which is a Mojo 2 app. Every fix landed in a
general layer; none names an app. See [fix-log.md](fix-log.md) for the nine, and for what
each app still asks for that nobody answers.

- ~~Modernized Mojo at `/usr/palm/frameworks/mojo/…`, handled like Enyo, with its own change
  log.~~ Done in outline.
- ~~Mojo 2 (`mojo2`, submission 205), MojoLoader and the frameworks tree.~~ Done; the
  frameworks copied into the APK are only the ones something has needed.
**2026-09-21, later:** a fidelity pass over the same suite. The faults it turned up were all
Lunacy's, and all general: no card's page held Android's focus, so a WebView dispatched no
focus events at all and nothing an app focused itself raised the keyboard; the keyboard hid
without taking the field's focus, which is the only signal Mojo gives an app that it has gone;
and the framework's widget art was asked for only once a widget wanted it. See
[fix-log.md](fix-log.md).

- Multi-stage windows mapped to cards; scene transitions
  (`PalmSystem.prepareSceneTransition` and `runSceneTransition` are logged no-ops).
- Full-screen cards (`PalmSystem.enableFullScreenMode`), which a video player wants.
- The services Mojo apps lean on that Lunacy hasn't got yet: the download manager,
  `com.palm.power`'s timeouts, `com.palm.audio`, `com.palm.accountservices`.

**Exit:** a Mojo test suite of popular App Museum apps runs without per-app code. The suite
exists as a list in [fix-log.md](fix-log.md); what it still needs is to be *run* rather than
driven by hand, which is the same missing test suite phase 1 is waiting on.

## 6: JS services (brought forward, running; see the architecture doc)

- An embedded Node runtime (e.g. nodejs-mobile); app services register on the bus.

**Exit:** popular App Museum apps that ship a JS service work, service included, without
per-app code.

## 7: PDK native

- ~~A glibc/SDL 1.2/PDL loader over Android (apkenv in reverse).~~ Started 2026-10-02: the
  binaries run as they are on 32-bit ARM, through the glibc loader with SDL 1.2 rebuilt to
  talk to the shell and Lunacy's own libpdl; Commander Keen plays on the Nexus 5
  ([pdk.md](pdk.md)). Since done, the same day: SDL_mixer, SDL_image, SDL_ttf, SDL_net and
  the image and font libraries apps link by name; GLES 1.1 and GLES 2 streamed to the shell
  and replayed into a framebuffer of the app's own size; the TouchPad's way of showing a
  buffer, measured on it; the accelerometer as SDL joystick 0; qemu for 64-bit-only devices,
  inside the arm64 APK. Codepoet's first tests passed.
- **Next:**
  - ~~**Hybrid apps**: web apps that embed a PDK plugin (`<object type="application/x-palm-remote">`),
    called from JavaScript.~~ Done 2026-10-07 for plugins that draw nothing on the page (Quick
    Office, Adobe Reader; [pdk.md](pdk.md) §6c). Still to do: a plugin drawing into a region of
    the page, as the chess app tried on 2026-10-02 may.
  - **OpenSSL 0.9.8 and curl**: about 50 apps link `libcrypto.so.0.9.8`, `libssl.so.0.9.8`
    or `libcurl.so.4`, which the runtime doesn't provide yet.
  - **`PDL_ServiceCall` to the bus**, as JS services have it.
  - **A keyboard** for apps that ask for one (`PDL_SetKeyboardState`).
  - **The launcher's runnable state** for a `pdk` app where the runtime is absent.

**Exit:** a handful of well-known PDK apps run at full speed on a current device. The
corpus survey says how many apps this phase can reach, and whether it is worth doing.

## Later

- **Newer Android targets, tightening security at each step.**
  - Move the bridge to `WebMessageListener` restricted to Lunacy's origins, with
    document-start scripts.
  - Recover through `onRenderProcessGone` (Android 8 and later).
  - Handle the conflict between the gesture bar and Android's gesture navigation (Android 10
    and later).
  - ~~Add the WebSQL polyfill when a target's WebView drops WebSQL.~~ Done 2026-10-02
    (`websql.js` and `WebSql.kt`), for the Pixel Tablet's WebView 153. Its migration of a
    WebView's own WebSQL files is written but untested: it needs a device whose WebView was
    updated across the removal.
  - Move to current AndroidX once `minSdk` rises.
  - **Developer verification** (Google, not an Android version: it arrives through Google
    System Updates). From 2026-09-30 in Brazil, Indonesia, Singapore and Thailand, and
    worldwide in 2027, an APK from an unregistered developer installs only over `adb` or
    through Android's "advanced flow" (a developer option, a reboot and a 24-hour wait).
    Either codepoet registers (a limited-distribution account opened in August 2026) or the
    install guide documents the advanced flow. Both APKs are affected. codepoet decides.
  - **16 KB memory pages: test on a device that has them.** The 64-bit build's libraries are
    all 16 KB-aligned now (2026-10-01, below), and Android 17's dialog is gone on the 4 KB
    Pixel Tablet, but no 16 KB-page device has run it yet.
  - **Target 28 on the 64-bit build: what is left to check.** Android's autofill reaches
    WebView forms from a target of 26; see what it does in a card against the TouchPad.
    Android 8.0 alone refuses an orientation lock on a translucent activity (the shell's
    theme is opaque, so this should not bite). The 32-bit build is at 28 too since 2026-11-06; the HP
    (Android 5) is unaffected, as the changes need Android 8.
  - **Android 17's memory limiter** kills any app's process past a RAM-derived limit (exit
    reason `MemoryLimiter:AnonSwap`; the figures aren't published). On the Pixel Tablet's
    Android 17 it reports itself disabled, so there was nothing to measure; check again on
    another 17 device or a later build.
  - **If the target ever reaches 37:** `ACCESS_LOCAL_NETWORK` for the loopback app server and
    LAN services such as Plex; orientation locks ignored on tablets; the shorter WebView
    user agent. None of it applies at target 24.
  - On 17 the keyboard no longer comes back by itself after a rotation; check what the
    TouchPad did. Add WebView 140 to the versions the suite runs on.
- **First Use: what is left** (the app is in, 2026-10-01). No rule 0 pass against the
  TouchPad's own First Use (codepoet): that app is platform-specific, and Lunacy's asks for
  different things; Palm's look is kept, not its measurements. Pages still to come as the
  targets rise:
  Bluetooth from a target of 31, notification access for launcher mode, the local network at
  37. A way to run it again from Device Info.
- Launcher mode: Lunacy as the Android home screen, with Android apps shown alongside webOS
  cards. A proof of concept is in (`shell/AndroidApps.kt`): Android's apps in the launcher,
  the dock and Just Type, launched and uninstalled through Android. An Android app opened
  from the launcher leaves it open behind it, so Home comes back to the launcher as it was
  (`ShellChrome.launchFromLauncher`, codepoet 2026-11-08). Reviewed 2026-09-26; what
  it still lacks, none of it started:
  - **Running Android apps have no cards.** Switching between them is Android's Recents,
    so "alongside webOS cards" isn't met. This is the largest gap.
  - **Android apps are listed whether or not Lunacy is the home screen**, and the favorites
    page is renamed "android" for everyone. Decide whether both should follow being the
    default home screen.
  - **Starting as the home screen hasn't been timed.** Android starts Lunacy at boot and
    after every low-memory kill, and on a 1 GB device each start first readies the webOS
    root (`WebosRoot`).
  - Not planned (codepoet, 2026-09-26): Android widgets. webOS had none.
  - Android's own wallpaper, opt-in (codepoet, 2026-10-04): Screen & Lock's "Use on Android"
    sets Android's home screen wallpaper (and lock screen, from Android 7) to webOS's, and
    keeps it following (`HostWallpaper`). Off by default. Works on the Pixel Tablet (Android
    17, home and lock) and the HP 10 G2 (Android 5.0.1, whose lock screen shows the one
    wallpaper).
- Just Type (universal search).

## Open questions


- **Apps that check `location.protocol`.** A TouchPad reports `file:`, and Lunacy can't. The
  corpus survey should count apps that check it.


## Decided

- **The card animations stay as they are, and card view keeps live pages** (codepoet,
  2026-09-21): a card's minimize drops its first four frames because Chromium re-rasters a live
  WebView when the card's transform changes. Measured, with everything else ruled out, in
  [fix-log.md](fix-log.md). **Snapshotting the cards - what LunaSysMgr did - is declined**: it
  would rebuild card view around frozen pages to work around one device's speed, and faster
  hardware will simply fit the frames. Don't propose it again; if it ever comes back it should
  be because a *newer* target still can't draw a transformed WebView in 16 ms.
- **`deviceInfo` reports this screen, accurately** (codepoet, 2026-09-21): 1280 x 800 on the
  HP 10 G2, not the TouchPad's 1024 x 768, because more devices are coming and the number has
  to mean the screen. An app that subtracts a TouchPad-sized constant from it therefore gets a
  short layout (SimpleChat's chat log; see [fix-log.md](fix-log.md)) - that is the accepted
  cost. **Android's navigation bar** is accepted too: it takes 48 px no webOS device gave up,
  and it goes away on the later Android versions that have gesture navigation.
- **Lunacy answers as a webOS device, fully** (codepoet, 2026-09-20): a TouchPad on a
  tablet-sized screen, a Pre3 on a phone. The environment is most like a TouchPad, and with
  that hardware dying off this is how it lives on. One place decides it (`DeviceProfile.kt`)
  and every app-visible surface follows: `deviceInfo`, the user agent in the page and on the
  wire, the system properties, and `X-Palm-Carrier`. The serial and `nduid` are generated per
  install rather than copied from a real device. Lunacy's own Device Info, the shell and the
  bus's service names stay truthful - the spoof is for apps, not a claim to be webOS. The
  Pre3's values were **measured on hardware on 2026-09-21** ([pre3.md](pre3.md)) and several
  of the community's are wrong - `DeviceProfile.PRE3` has not been corrected yet.
- **The device id is derived and can be carried over** (codepoet, 2026-09-20): `nduid` and the
  serial come from this device's own hardware ids, so a reinstall gives the same id back and
  the services' analytics don't see a new device; and Device Info lets its owner type in a
  TouchPad's id, so a migration off dying hardware keeps that device's history. No random
  regeneration, for the same reason.
- **webOS version:** Lunacy reports webOS CE 3.1.0, the community-supported version, as the
  reference TouchPad does (codepoet, 2026-09-19); a phone would report the Pre3's 2.2.4.
- **Palm's code ships** (codepoet, 2026-09-20): Palm's own settings apps go in the APK with a
  NOTICE, as abandonware, like Mojo, the Prelude fonts and the TouchPad wallpapers. Nobody has
  asserted rights in nearly a decade of webOS Archive doing the same.
- **No service workers:** `navigator.serviceWorker` is hidden, as the TouchPad's WebKit had
  none (codepoet, 2026-09-19).
- **App Museum:** bundle the real Museum app rather than write a catalog client. It installs
  through Preware's `applicationManager/open` contract (found in spike 1).
- **Shell styling:** LunaCE, with TouchPad 3.0.5 as the fallback reference.
- **HP fonts and wallpapers:** ship them as abandonware, like Mojo (codepoet, 2026-09-19).
- **Android back button = TouchPad home button:** webOS tablets had no back button. Back
  follows LunaCE's home-button order: close what's open on top, else minimize the
  maximized card, else toggle the launcher. A hardware keyboard's Escape still reaches apps
  as webOS back (codepoet, 2026-09-19).
- **First platform:** Android 5.0.1 on the HP 10 G2. Security is tightened as later Android
  versions are targeted.
- **Serve-time transforms:** global, mechanical transforms of app files are allowed; per-app
  edits are not.
- **Origin:** `https://<appid>.media.cryptofs.apps`, serving
  `/media/cryptofs/apps/usr/palm/applications/<appid>/`. It matches the TouchPad's path, and
  the hostname check apps use (TouchPad hostname:
  `.media.cryptofs.apps.usr.palm.applications.<appid>`).
- **WebView range:** Lunacy supports every WebView Android 5 can run. It is tested on
  Chromium 37 and 64, with global transforms for the differences. Chromium 95 is added
  when it can be obtained.
