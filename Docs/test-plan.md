# Release test plan

How a Lunacy build is shown to be ready, before every release and every test round. Modelled on
webOS CE's flash test plan (its `Docs/TEST-PLAN.md` in webos-doctor-ce): one script decides
everything a script can, its output is kept with the release, and what needs eyes or hands is
a list worked by hand, marked as it goes.

Copy the block below into the release's notes in [roadmap.md](roadmap.md), fill it in, and
keep each device's results file in `Docs/test-results/<build>/`.

```
Build under test:  0.9.x, build NNN, commit abcdef0
                   out/SHA256SUMS sha256 ...           (six APKs: release + debug, both
                                                        ABIs and the keyboard)
Tier 1:            ./test.sh             N passed / 0 failed     (build.sh release ran it)
                   apk-inventory         every APK: N files by content, N patches, 0 problems (build.sh ran it)
Tier 2:            fresh-install.sh      one device, debug: N passed / 0 failed  test-results/NNN/fresh-<device>.txt
                   device-test.sh        per device, below; every device on build NNN
                   HP 10 G2   (debug)    N passed / 0 failed     test-results/NNN/hp10g2.txt
                   Fold3      (debug)    ...
                   ...
Tier 3:            the [Human] items below, marked [Pass] / [Fail] / [Skipped]

What this run does NOT decide, despite being green:
                   - anything only a person sees: rule 0 is tier 3, never tier 2
                   - a release build's in-page checks (they run on the debug build)
                   - an account the round didn't sign in to
```

Legend: `[ ]` not yet run · `[Pass]` · `[Fail]` · `[Human]` needs eyes or hands · `[Skipped]` ·
`[n/a]`. An automated item is marked from the script's output, never from memory.

## The rules of a round

- **One build everywhere.** Commit, build the candidate (`./build.sh release`, then
  `./build.sh debug`), install that one build on every device, and record each device's
  versionCode before testing anything (`device-test.sh` checks it). Never fix something
  mid-round on some devices only: a fix means a new build, on all of them, and the automated
  pass again.
- **Test devices run the debug build** (they can't take a release over a debug build without
  a wipe), which carries the same code. The release APKs get section 13 on a device that can be
  wiped.
- **A check has to be able to fail.** Every automated check was seen failing once, on purpose,
  before it was trusted (how is under each). A check that can't fail is worse than none: it
  reads as cover. When a check's signal turns out to be wrong, say so in this file.
- **A bug fixed is a check added.** When something reaches a device that a check could have
  caught, the fix adds the check: to `test.sh` or a unit test if it can run on the build
  machine, to `device-test.sh` if it needs a device, and as a `[Human]` line here if it needs
  eyes. Section 5 is where they collect.
- **Don't edit a script while a pass is running it.** Bash reads a script as it goes: a change
  to `device-test.sh` mid-round cut one device's pass short without an error (the A7 Lite's
  fresh install, 2026-11-18). Finish the passes, then change the script, then run them again.
- **Don't trust a green log over the screen** where they disagree; and don't trust the
  screen's colours from a TouchPad screenshot ([dev-workflow.md](dev-workflow.md)).

## Devices

| Device | Android | ABI | Build | What it stands for |
|---|---|---|---|---|
| HP 10 G2 | 5.0.1 | arm32 | debug | the first target: 1 GB, Chromium 37, the reference scale (1:1 with the TouchPad) |
| Nexus 7 2012 | 5.1.1 | arm32 | debug | a slow old tablet |
| Nexus 5 | 6 | arm32 | debug | phone mode, measured against the Pre3 |
| Galaxy Tab A7 Lite | 14 | arm64 | debug | a current cheap tablet; mail, PDK and hybrid apps |
| Galaxy Z Fold3 | 15 | arm64 | debug | a foldable: both screens, folding mid-use |
| Pixel Tablet | 17 | arm64 only | debug | the newest Android; read-only unless codepoet opens it |
| Surface Duo | 12 | arm64 | debug | two screens, each the other shape from the display: PDK games as a TouchPad's ([pdk.md](pdk.md)); never `wm size` it |
| Chromecast with Google TV | 14 | arm32 | debug | Android TV, a remote and no touch ([android-tv.md](android-tv.md)); wireless debugging only |
| Boox Go Color 7 | | | release | codepoet installs it and reports back; nobody else touches it |

Addresses are in [dev-workflow.md](dev-workflow.md), and every change to a device goes in
[android5-setup.md](android5-setup.md). The reference is the TouchPad (webOS CE 3.1.0), and in
phone mode the Pre3.

## How to run

```sh
# Tier 1, on the build machine (build.sh release runs it and stops on a failure)
./test.sh                 # all of it; `./test.sh quick` leaves out the JVM tests

# Tier 1, after each build: what each APK holds against the tree (build.sh runs it, and
# stops on a gap; by hand: Workbench/apk-inventory.py out/AndroidLuna-*.apk)
#   Every file by content, and the APK's own list of applied framework patches
#   (assets/patches.applied) against LunaRuntimes/: a patch committed after the last
#   fetch-assets run is in no build, and nothing else showed it (Mojo 0005, 2026-11-16).
#   Seen failing: an APK without the list; a patch added to the tree after the build.

# Tier 2, first: a fresh install, on the Galaxy Tab A7 Lite (never the Kyocera, which is
# codepoet's F-Droid update test device and is not touched: codepoet, 2026-11-16). ERASES
# Lunacy's data there; installs the candidate's debug build, finishes First Use over the bus,
# adds the Android account, and runs device-test in that same run of Lunacy, unrestarted
Workbench/fresh-install.sh 192.168.10.27:5555 out/AndroidLuna-arm64-debug.apk Docs/test-results/NNN/fresh-a7lite.txt

# Tier 2, on each device, the candidate installed and First Use done
Workbench/device-test.sh 192.168.10.36:5555 NNN Docs/test-results/NNN/hp10g2.txt

# For a release that moves code: the bus's answers before and after, on one device (section 16)
Workbench/bus-snapshot.sh 192.168.10.27:5555 before.json    # then install the candidate; after.json; diff

# PDK and hybrid apps: the scenarios and a random sample (section 12)
Workbench/pdk-sweep.sh 192.168.10.175:5555 scenarios Workbench/results/pdk-NNN-fold3 16
cp Workbench/results/pdk-NNN-fold3/summary.txt Docs/test-results/NNN/pdk-fold3.txt
#   (only the summary is kept: the folder's logcats and screenshots may show mail)
```

**Why a fresh install, every round.** 1.0 shipped two bugs that lived only in the first run
after an install, and every device in its round had been set up long before and restarted
since (2026-11-15). The activity files the configurator holds back until First Use (the
contacts linker's watch among them) were sent only at the next start, so a new install's
Android contacts sat in db8 behind an empty Contacts list; and adding the Android account from
Contacts' or Calendar's first screen made no account. `device-test.sh` restarts Lunacy first,
which hid the first; `fresh-install.sh` runs it with `NORESTART=1`. The second is checked in
the source by `test.sh`; adding the account by hand from Contacts' first screen on the fresh
install (section 9) is the person's half.

`device-test.sh` restarts Lunacy twice and opens every bundled app; it reads no mail or
messages, only counts, ids and error codes, so its results file can be committed. Bus calls go
through the SDK's novacomd relay when it runs here (it works with a release build), or through
DevTools on a debug build. On the relay, `luna-send` needs `</dev/null` or it exits silently
(learnt from CE's plan; it cost a morning on the TouchPad too).

Keep the screen awake and unlocked for the whole run. A card opened behind a lock screen or a
sleeping display never lays out, so every app fails "no card" (the HP 10 G2, 2026-11-14).
`svc power stayon true` holds only while the device is plugged in, and an `am start` from adb
isn't user activity, so on an unplugged device dismiss the lock screen and send
`input keyevent KEYCODE_WAKEUP` every minute or so during the run. On a phone (the Nexus 5),
an app late in the run may miss the 25 s limit once some twenty cards are open: open it by hand
before calling it a failure.

Learnt in the 1.2.0 round (2026-11-26):
- **A fresh install has no PDK games.** The sweep skips a scenario a device lacks, so put the
  scenarios back after `fresh-install.sh` (each app id's newest package from the Museum, through
  Lunacy's own installer: serve them, `adb reverse tcp:8123 tcp:8123`, then
  `--es install http://localhost:8123/<file>`). `adb reverse` didn't reach the HP 10 G2 over
  Wi-Fi; serve on this machine's LAN address for it instead. `palm-install` over the relay
  drops packages of a few hundred MB (Asphalt 6).
- **Android 5.1's lock screen** (the Nexus 7) has no `wm dismiss-keyguard`, and a swipe lock
  sleeps the screen ten seconds after each wake: keep it on with `svc power stayon true` while it
  charges and send the Menu key (`input keyevent 82`) every few seconds, or cards open behind the
  lock and fail "no card".
- **The Nexus 5 is slow:** a card past the 25 s limit, or Email's third accounts reload, is the
  phone's slowness and not a failure (codepoet). A crash or an uncaught error still is. With some
  twenty cards open it once ran out of GPU memory for a layer and Lunacy restarted; the test's
  load, not a person's, and section 6's crash check missed it. For after 1.2.0: close each card
  as section 4 goes, and a crash check that sees a crash Lunacy recovered from.
- **On a TV** drive the remote with `input dpad keyevent`; a plain `input keyevent` is a virtual
  keyboard's, which a TV leaves alone.

**Tier 1** (`test.sh`) is:
- every bundled JSON file parses (a stray comma silently stops a kind, a template or an app);
- the account templates and the OAuth providers agree (ids, folders, fallbacks);
- the preload's host tests, in C (mail auth, the Exchange adapters, back-off, PDK sends);
- the bundled apps' own Node tests (Calendar's iCal import, Contacts' vCards);
- the service host's child processes (`host.js`) with a root as a desktop has it;
- the JVM unit tests (db8 on a real SQLite, the transforms, the page hooks, EAS dates, ...), and
  LunaCore's own (the launcher's pages, Just Type, the app lifecycle, the thumbnailer's sizes, the
  notification sounds' rules, novacom's protocol), with its API 21 gate.

Palm's Jasmine spec for Contacts is listed as skipped: it needs a browser harness.

## 1. Identity — *automated*

- [ ] The installed versionCode is the candidate's; debug or release and the ABI are recorded.

## 2. Start and the bus — *automated*

- [ ] The shell comes up and the activity manager runs, from cold.
- [ ] Every native service that starts becomes ready (one that waits for demand is INFO).
- [ ] Email loads headless at boot.
- [ ] The bus answers: the time, `listApps` (20 apps or more), a real error for a service that
      doesn't exist (rule 2), and db8 refuses a caller without rights.
- [ ] The private bus's services refuse a public caller as the TouchPad did (`luna-send -P`:
      `com.palm.wifi`, `com.palm.btmonitor`, the ringtone class) and answer a `com.palm` page
      (those, and `com.palm.filenotifyd/state`). *Seen failing:* luna-send without `-P` (the
      relay's private bus) answered all three; that was the check's mistake, fixed to `-P`.
      The in-page half first waited on a Promise, which Chromium 37's DevTools hands back
      unresolved, as `{}`: all four failed on the Nexus 7 though the services answered there
      (1.0 round, 2026-11-15). In-page checks now send, then read back a few seconds later.
- [ ] Every activity file that waits (a trigger or a schedule: the system's, its services',
      the bundled apps') is live: in the fresh-install pass, in the run First Use finished in.
      *Seen failing:* 1.0's fresh installs had none of the 17 until Lunacy restarted.
- [ ] *(debug)* Contacts in db8 reach the Contacts list: where there are contacts, there are
      people (`com.palm.person`), so the linker runs. *Seen failing:* 455 Android contacts and
      no people on 1.0's fresh install (the Kyocera).
- [ ] In an XML document `getElementsByTagName` finds elements by local name, as the
      TouchPad's WebKit did (a WebDAV reply's `<d:response>` for `"response"`). The check
      finds the element itself by namespace: a prefixed name finds nothing on Chromium 37,
      as on the TouchPad, and the first version of the check threw there.

## 3. Mail accounts — *automated, debug build*

- [ ] No mail account has an error. An account the person removed may leave its record behind
      until the delete finishes; that is INFO, not a failure.
      *Wrong signal, found 2026-11-15:* the check waited on a Promise, so on Android 5
      (Chromium 37's DevTools) it read `{}` and reported "no mail accounts" whatever there was.
      It now sends, then reads back, and anything but a list is a failure.

## 4. Every bundled app opens a card — *automated*

- [ ] Each visible bundled app opens a card within 25 s (a card's page logs a size; a headless
      page logs 0x0), with no uncaught error, and on a debug build no broken image in it.
      *Seen failing:* images planted in Email's card (`src="undefined"`, and a file that
      doesn't exist) were both reported, then removed.

## 5. Regression watch — *automated where a script can see it*

Each line is a bug that reached a device, with the check that would see it again.

- [ ] **Email flashing as it opens** (22fa049): db8 woke watches on merges that changed nothing,
      and Email reloaded its accounts once per account. *Check:* watched queries of
      `com.palm.mail.account` from start to card, at most 2. *Seen failing:* a build with the
      old `Db8.kt` gave 16 on the Fold3 (7 accounts), the fixed one 1. A first version of this
      check counted `_accountsChanged`, which the old build also logged once: a check that
      couldn't fail, found by trying.
- [Human] **A message that won't stay zoomed** (2026-11-19): in Email, pinch a message larger,
      and a message wider than its pane smaller. It stays at the size the fingers left it,
      the header keeps its size and scrolls away with the message, and a fling back to the
      top stops with the header whole and the message straight under it.
- [Human] **Messaging's broken avatar** (6071f3c): a chat with someone new shows their avatar,
      or the generic one, from the first message; never a broken image. Section 4's image
      check covers an open chat only if one is open.
      *Wrong signal, found 2026-11-18:* on the HP 10 G2 a fresh install's pass read "not
      seen": Android 5's log is 256 KB and can't be enlarged, and the query, made as Email
      loads at boot, had scrolled out before its card opened. The check keeps the log as it
      streams as well.
- [ ] **An app launched as Lunacy starts** (970b21c): Clock, launched by the intent that
      starts Lunacy, opens its card after the boot settles. *Seen failing:* 617 on the Fold3,
      every time ("no card opened; the placeholder goes").
- [ ] **WebDAV Client's curl** (18c28a8): `test.sh` runs `curl.js` with the app's PROPFIND
      (`--noproxy`, credentials in the URL and `-u`). *Seen failing:* the old `curl.js` gave
      "option --noproxy: is unknown".
- [Human] **Exhibition from Android's screen saver stays lit** (dad14ba): with Lunacy chosen as
      the screen saver, dock or charge the device and leave it past the screen timeout; the
      screen saver's Exhibition stays on until the device comes off the charger.
- [Human] **Schedules that switch radios and volumes** (fd57d48, 222a819): Night Moves' night
      and morning schedules turn Wi-Fi and Bluetooth off and on and set the ringer volume.
- [Human] **WebDAV Client** (18c28a8): a configured server lists its folders, and a file
      downloads and uploads.
- [Human] **Video Player** (8575ef4): launched by id with a video (MeTube, or
      `alaunch.sh com.palm.app.videoplayer '{"target":...}'`), webOS CE's player plays it
      across the card.
- [Human] **Duplicates from leaked shells** (e384a82, 2d64e46): each leaked shell ran its own bus,
      db8 and activity manager, and webCal's events came in twice or more. After a fold, an
      unfold, a rotation and Android sending Lunacy home and back, a synced calendar has no
      duplicate events ([fix-log.md](fix-log.md)).

## 6. Crashes — *automated*

- [ ] No Java crash, native signal or ANR, and no service died, over the whole pass.

## 7. Look and feel against the TouchPad — [Human] (rule 0)

On the HP 10 G2, beside the TouchPad at the same scale; codepoet judges the feel.
- [ ] Launcher, card view, a card's open and close, swipe-to-close, the gesture area's back.
- [ ] Notifications and dashboards: arrive, stack, swipe away.
- [ ] The system menu, the keyboard (LunaKeyboard), Just Type.
- [ ] Exhibition while charging; screen lock and wake.
- [ ] Every visual change since the last release, checked by its own before/after.

## 8. Phone mode against the Pre3 — [Human]

- [ ] On the Nexus 5: notifications at the bottom, the card view and launcher as the Pre3 has them.

## 9. Accounts and sync — [Human]

Test accounts only, never codepoet's personal ones.
- [ ] Gmail (OAuth): sign in, mail arrives, send one, IDLE push.
- [ ] Microsoft Outlook: a work account over EAS (mail, calendar, contacts) and a personal one
      falling back to IMAP.
- [ ] The legacy Microsoft Exchange type still signs in and syncs.
- [ ] A recurring event with an exception, and an event in another time zone, read as in Outlook.
- [ ] Wrong password, throttling and admin approval each say so plainly; "Change login
      settings" fixes a wrong password.
- [ ] On the fresh install (after `fresh-install.sh`, or a new install by hand): Contacts'
      first screen, Add Account, Android, through Android's permission questions: the account
      is made ("Syncing"), and Android's contacts are in the list without restarting Lunacy;
      then the same from Calendar's first screen on another fresh install. *Seen failing:*
      1.0, both (the Kyocera, 2026-11-15).

## 10. Messaging — [Human]

- [ ] AIM over the webOS Chat server: chat both ways, buddy list, avatars (section 5).

## 11. Calendar, Contacts, Photos, Music — [Human]

- [ ] Import a calendar and a vCard file; the Android account's one-way contacts and calendars.
- [ ] Photos shows the device's pictures; Music plays from the library.

## 12. PDK and hybrid apps — *automated start*, [Human] screenshots

Not every game: one app for each way a PDK or hybrid app has needed something of its own
([Workbench/pdk-scenarios.txt](../Workbench/pdk-scenarios.txt), from [pdk.md](pdk.md)), and a
random sample of the rest (4 by default; `pdk-sweep.sh` lists the device's PDK and hybrid apps
over the SDK relay and draws from them). The scenarios: start scripts (ScummVM, Worms), classic
SDL 1.2 (Keen), OpenAL, OpenSSL, a library the device lacks (ExZeus), an emulator, Android
ports through apkenv (EGL; native on 64-bit), a phone game's touches, a movie through
libSDL_cinema, and a hybrid's plug-in. No app in the corpus uses SDL 2: the PDK shipped 1.2.

- [ ] Every scenario and every sampled app starts and is still running after the wait, with no
      crash (`summary.txt`). The sample is printed, so a failure can be run again by name.
      A scenario the device lacks is SKIP: install it (codepoet's games folder and the App
      Catalog mirror have them all) and run the sweep again, so every device covers every
      scenario. The sweep closes the games when it is done (Lunacy restarted, at its launcher).
- [ ] Its screenshot shows what `pdk-scenarios.txt` says to look for. Fieldrunners: listen.
- [ ] On the Fold3 (arm64, native) and the HP 10 G2 or A7 Lite (arm32, 30 s); on the Pixel
      Tablet (64-bit only, qemu) when codepoet opens it.
- [ ] After a change to the PDK runtime, the whole corpus as well (`pdk-sweep.sh <device> <list>`).
      Never sampled: N.O.V.A. 2, Asphalt 5 and Oregon Trail, which never worked.
- [ ] A PDK movie plays to its end; Quick Office and Adobe Reader open their types.
- [ ] Asphalt 6: Free Race, Nassau, Normal Race starts and the track draws, no crash (GLES 2
      shaders and attributes, [pdk.md](pdk.md) §6l).
- A new kind of fix adds its app to `pdk-scenarios.txt`.

## 13. Install paths — [Human]

- [ ] The release APK on a wiped device: First Use runs, and the launcher comes up after it.
- [ ] The release over the previous release: everything kept (accounts, apps, settings).
- [ ] An app from the App Catalog installs end to end; the bundled App Catalog is the latest.
- [ ] The SDK against the release build: `palm-install`, `palm-launch`, `palm-log` over the relay.

## 14. Device behaviour — [Human]

- [ ] Sleep and wake; Lunacy as the home app; the Fold3 folded and unfolded mid-use.
- [ ] The startup sound (webOS's boot.mp3) plays once as a fresh Lunacy appears, and not
      again on Home, on returning to Lunacy, on a fold, or when Exhibition starts it; with
      Sounds & Alerts' System Sounds off, it doesn't play at all.

## 15. The shared core — *automated, debug build* (since 1.1.0)

What moved out of the Android shell into LunaCore, where Luna DE runs it too
([luna-de.md](luna-de.md)), asked the way an app asks. Each check was seen passing on the release
before the moves (1.0.2, build 652, on the Nexus 5), so a failure is a regression of a move.

- [ ] `systemProperties/Get`: an nduid of 40 hex digits, the device's name, and "no such key"
      for a key there isn't.
- [ ] The media index answers the file picker's relay for each kind (documents, images, albums,
      videos, audio), with a count each.
- [ ] The relay reads the media kinds and nothing else, and db8 refuses an app that isn't a
      consumer of the image kind.
- [ ] The thumbnailer: the first indexed picture read through
      `/var/luna/data/extractfs`, short form in a 64 x 64 box (fitted, never grown) and the long
      form's JSON for 200 x 200 (fitted, grown if smaller). `LunaCore`'s `ExtractfsTest` holds the
      sizes to the TouchPad's measured ones on the build machine.
- [ ] Screen & Lock shows its Android rows (they start hidden and show when the environment
      answers, for Luna DE, which has none). *Wrong signal, found on the baseline:* on a phone
      with twenty cards open its page had gone; the check brings the card up again first.
- [ ] *(build machine, `test.sh`)* `host.js` runs a service's `exec`, `execFile` and `spawn`
      with the webOS root under a folder that is itself a webOS folder name and has a space in
      its name. *Seen failing:* the host.js before 2026-11-18 ("Command failed", the path mapped
      twice).
- [Human] A notification's sound: an app's own file (Check Mate's moves), a banner's, and the
      class's default when it names none; cut off at its duration.
- [Human] Photos & Videos lists the device's albums and pictures and opens one full screen;
      a new picture appears without restarting; the file picker's four tabs each list.

## 16. Against the last release, on one device — *automated compare, read by a person*

For a release that moves or rewrites code rather than adding to it. On a device that can be
wiped: the last release's debug build installed fresh and set up
(`Workbench/fresh-install.sh <device> <its debug apk>`), then

```sh
Workbench/bus-snapshot.sh <device> before.json      # the last release's answers
adb -s <device> install -r out/AndroidLuna-<abi>-debug.apk
Workbench/bus-snapshot.sh <device> after.json       # the candidate's, over the same data
diff before.json after.json
```

`bus-snapshot.sh` asks some hundred read-only bus methods from Email's page (the system
service, the application manager, the connection manager, the activity manager, the device's
properties, Just Type's lists, power, display, audio, the media kinds, db8's counts, and an
unknown service, method and kind for their errors) and leaves out what changes by itself.

- [ ] The diff is empty, or every line of it is explained in the release's notes.
- [ ] After the upgrade the device pass is green, and what a person set before it is as they
      left it: the launcher's pages and quick-launch bar, Just Type's settings, the wallpaper,
      the accounts.

## If something is off — first places to look

```
adb logcat -s Lunacy                       the shell, the bus and every page's console
palm-log -d lunacy -f <appid>              one app's console, also on a release build
novaterm -d lunacy                         a shell in the webOS root: /var/log/messages
Workbench/cdp.sh eval '<js>' <appid>       inside a page (debug builds)
Device Info > Save a Log                   what a tester without adb can send
```
