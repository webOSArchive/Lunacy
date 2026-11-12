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
Tier 2:            device-test.sh        per device, below; every device on build NNN
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
| Boox Go Color 7 | | | release | codepoet installs it and reports back; nobody else touches it |

Addresses are in [dev-workflow.md](dev-workflow.md), and every change to a device goes in
[android5-setup.md](android5-setup.md). The reference is the TouchPad (webOS CE 3.1.0), and in
phone mode the Pre3.

## How to run

```sh
# Tier 1, on the build machine (build.sh release runs it and stops on a failure)
./test.sh                 # all of it; `./test.sh quick` leaves out the JVM tests

# Tier 2, on each device, the candidate installed and First Use done
Workbench/device-test.sh 192.168.10.36:5555 NNN Docs/test-results/NNN/hp10g2.txt

# PDK and hybrid apps: the scenarios and a random sample (section 12)
Workbench/pdk-sweep.sh 192.168.10.175:5555 scenarios Workbench/results/pdk-NNN-fold3 16
cp Workbench/results/pdk-NNN-fold3/summary.txt Docs/test-results/NNN/pdk-fold3.txt
#   (only the summary is kept: the folder's logcats and screenshots may show mail)
```

`device-test.sh` restarts Lunacy twice and opens every bundled app; it reads no mail or
messages, only counts, ids and error codes, so its results file can be committed. Bus calls go
through the SDK's novacomd relay when it runs here (it works with a release build), or through
DevTools on a debug build. On the relay, `luna-send` needs `</dev/null` or it exits silently
(learnt from CE's plan; it cost a morning on the TouchPad too).

**Tier 1** (`test.sh`) is:
- every bundled JSON file parses (a stray comma silently stops a kind, a template or an app);
- the account templates and the OAuth providers agree (ids, folders, fallbacks);
- the preload's host tests, in C (mail auth, the Exchange adapters, back-off, PDK sends);
- the bundled apps' own Node tests (Calendar's iCal import, Contacts' vCards);
- the JVM unit tests (db8 on a real SQLite, the transforms, the page hooks, EAS dates, ...).

Palm's Jasmine spec for Contacts is listed as skipped: it needs a browser harness.

## 1. Identity — *automated*

- [ ] The installed versionCode is the candidate's; debug or release and the ABI are recorded.

## 2. Start and the bus — *automated*

- [ ] The shell comes up and the activity manager runs, from cold.
- [ ] Every native service that starts becomes ready (one that waits for demand is INFO).
- [ ] Email loads headless at boot.
- [ ] The bus answers: the time, `listApps` (20 apps or more), a real error for a service that
      doesn't exist (rule 2), and db8 refuses a caller without rights.

## 3. Mail accounts — *automated, debug build*

- [ ] No mail account has an error. An account the person removed may leave its record behind
      until the delete finishes; that is INFO, not a failure.

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
- [Human] **Messaging's broken avatar** (6071f3c): a chat with someone new shows their avatar,
      or the generic one, from the first message; never a broken image. Section 4's image
      check covers an open chat only if one is open.
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

## If something is off — first places to look

```
adb logcat -s Lunacy                       the shell, the bus and every page's console
palm-log -d lunacy -f <appid>              one app's console, also on a release build
novaterm -d lunacy                         a shell in the webOS root: /var/log/messages
Workbench/cdp.sh eval '<js>' <appid>       inside a page (debug builds)
Device Info > Save a Log                   what a tester without adb can send
```
