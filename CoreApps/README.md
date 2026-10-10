# Core apps

The webOS apps Lunacy bundles, one folder per app id, each as it is installed under
`/usr/palm/applications`. They live outside the shell so they can be compared with and shared
with other webOS platforms, LuneOS among them. The build copies this folder into the APK at
`assets/apps/` (`bundleCoreApps` in [AndroidLuna/build.gradle.kts](../AndroidLuna/build.gradle.kts)).

Every app carries a NOTICE: where it came from, its licence or abandonware status, and any
change Lunacy made to it. Rule 5 in [CLAUDE.md](../CLAUDE.md) keeps installed apps as packaged.
The only exceptions are Email and Screen & Lock, which codepoet approved, and each of those
changes is marked `[Lunacy]` in its source.

| App | Id | Version | From |
|---|---|---|---|
| Accounts | `com.palm.app.accounts` | 3.1.1 | Open webOS (Apache 2.0), as on the reference TouchPad (webOS CE 3.1.0) |
| Web | `com.palm.app.browser` | 1.0.0 | Palm, from the reference TouchPad (abandonware) |
| Calculator | `com.palm.app.calculator` | 3.2.0 | webOS Archive's modernized Calculator (webOS CE) |
| Calendar | `com.palm.app.calendar` | 3.2.0 | webOS Archive's modernized Calendar, from the webOS CE tree |
| Clock | `com.palm.app.clock` | 3.1.1904 | Palm, HP webOS 3.x (abandonware) |
| Contacts | `com.palm.app.contacts` | 3.2.0 | webOS Archive's modernized Contacts, from the webOS CE tree |
| Device Info | `com.palm.app.deviceinfo` | 1.0.0 | Lunacy's own, in Palm's look |
| Email | `com.palm.app.email` | 3.0.13600 | Palm, HP webOS 3.x (abandonware), with Lunacy's phone-layout changes |
| App Catalog | `com.palm.app.enyo-findapps` | 6.2.2927 | webOS Archive's restoration of the TouchPad's own |
| Exhibition | `com.palm.app.exhibitionpreferences` | 1.0.0 | Palm, HP webOS 3.x (abandonware) |
| Help | `com.palm.app.help` | 2.0.0 | Palm, HP webOS 3.x, content host moved to help.webosarchive.org |
| Messaging | `com.palm.app.messaging` | 3.2.0 | Palm, HP webOS 3.0.5 (abandonware), with the webOS CE project's changes |
| Memos | `com.palm.app.notes` | 3.0.20 | Palm, HP webOS 3.x (abandonware) |
| Photos & Videos | `com.palm.app.photos` | 3.1.8001 | Palm, from the reference TouchPad (abandonware) |
| Screen & Lock | `com.palm.app.screenlock` | 1.0.0 | Palm, HP webOS 3.x (abandonware), with Lunacy's changes |
| Sounds & Alerts | `com.palm.app.soundsandalerts` | 1.1.0 | Lunacy's own, in Palm's look |
| Video Player | `com.palm.app.videoplayer` | 1.0.0 | Palm, HP webOS 3.x (abandonware); no icon, launched by other apps |
| Wi-Fi | `com.palm.app.wifi` | 1.0.0 | Lunacy's shortcut to Android's Wi-Fi settings |
| First Use | `org.webosarchive.lunacy.firstuse` | 1.0.0 | Lunacy's own, in Palm's First Use look; no icon |

These folders hold only the apps. The services they talk to (mail, Exchange, calendar,
contacts, accounts) are the TouchPad's own, laid into Lunacy's webOS root by
[AndroidLuna/fetch-assets.sh](../AndroidLuna/fetch-assets.sh) (see
[Docs/dev-workflow.md](../Docs/dev-workflow.md)).
