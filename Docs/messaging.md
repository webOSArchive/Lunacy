# Messaging and instant messaging

Lunacy ships webOS CE's Messaging and its IM stack, so Lunacy, webOS CE and stock webOS devices
can chat over AIM through webOS Chat's server (`oscar.webosarchive.org`). Measured
2026-10-10: on the Galaxy Tab A7 Lite, Messaging 3.2.0 signed in to the prototype server by
itself at startup, listed the reference TouchPad as a buddy with its presence, and exchanged
messages with it in both directions. The plan, the server and its deployment guide are the
webOS Chat project's (codepoet's `Oscar` repository; the plan is a shared doc).

## What runs

| Piece | From | Where it is in Lunacy |
|---|---|---|
| Messaging 3.2.0 (Enyo 1) | webOS CE: Palm's 3.0.6606 with the CE project's changes (servers and channels, reactions, edits, receipts, emoji), 3.0.6607 on the device; 3.2.0 is CE's release number | `CoreApps/com.palm.app.messaging` |
| `imlibpurpletransport` (`com.palm.imlibpurple`) | webOS CE: Herrie's imlibpurpleservice | ROM `/usr/bin`, a native service |
| libpurple 2.14.13 and helper plugins | webOS CE | ROM `/usr/lib/libpurple.so.0`, `/usr/lib/purple-2` |
| `libaim.so` (AIM / OSCAR) | Pidgin 2.14.2's OSCAR code, built by the webOS Chat project for CE's libpurple, with webOS Chat's defaults | ROM `/usr/lib/purple-2/libaim.so` |
| GLib 2.x, GIO, libffi, tidy, opus, ogg, nsl, zlib | webOS CE's transport runtime | ROM `/usr/lib/synergy-runtime` |
| Chat threader (`com.palm.messaging.chatthreader`, JS) | webOS CE: Palm's, with Herrie's fixes | ROM `/usr/palm/services` |
| `imaccountvalidator` and the AIM account template (`com.palm.aol`) | HP webOS 3.0.5's own image; CE no longer carries either | ROM `/usr/bin`, `/usr/palm/public/accounts` |
| The messaging kinds, permissions, activities, frameworks | webOS CE | ROM `/etc/palm`, `/usr/palm/frameworks` |

`AndroidLuna/fetch-assets.sh` lays them in from `Workbench/vendor/touchpad/messaging` (CE) and
`messaging-305` (3.0.5). The transport and validator start with `/usr/lib/synergy-runtime`
first on their library path, written into their D-Bus service files. Lunacy's glibc 2.36 and
libstdc++ stand in for CE's `synergy-glibc` and its libstdc++. The device's launch wrapper
(`imwrap.sh`) isn't used.

## The AIM plugin's defaults

Pidgin removed AIM in 2.14.3, so CE's libpurple 2.14.13 has no AIM, and the 2.14.2 plugin
defaults to AOL's web login and TLS. CE's transport can't pass an account's settings to
libpurple (see "Known bugs"), and the 3.0.5 validator never did. So the webOS Chat build of
`libaim.so` changes four defaults instead:

- the login host is `oscar.webosarchive.org`;
- the SSL login host is the same;
- the login is the MD5 (BUCP) one;
- there is no encryption.

An AIM account then needs only a screen name and its chat key. Stock webOS devices keep their
own plugin and reach the same server through a hosts line for `login.messaging.aol.com`.

## Fixes it needed (all general; [fix-log.md](fix-log.md), 2026-10-10)

- The runtime's preload maps `dlopen` (libpurple loads plugins by webOS path).
- The preload answers `getaddrinfo` and `gethostbyname` from the webOS root's `/etc/hosts` and
  gives glibc the root's nameservers. glibc's resolver had been reading Android's files.
- libenosys traces forked children: libpurple's DNS helper died at its first refused call.
- db8 `watch` fires at once when its query already has results, as measured on the TouchPad.
- Activity files go through `jsonC` (trailing commas).
- The ROM leaves out files aapt won't package (CE's chat threader carried `.gitignore`), and
  `WebosRoot.syncRom` skips a listed file that isn't in the APK instead of stopping the shell.
- The PDK runtime carries `libnsl`, `libresolv` and `libutil`.

## Testing against a development server

Until `oscar.webosarchive.org` is deployed, point the name at a development server in Lunacy's
webOS root (development only, nothing ships it):

```sh
adb shell "run-as org.webosarchive.lunacy sh -c 'printf \"127.0.0.1 localhost\n<server ip> oscar.webosarchive.org\n\" > files/webos/etc/hosts'"
```

Run the server with `OSCAR_ADVERTISE=<server ip>:5190`. In Messaging, add an AIM account with a
screen name and chat key from the server's `accounts.json`.

## Known bugs and open items

- **CE's transport and per-account settings.** `onCreate` stores the options nested under
  `config`, but the transport hands libpurple the whole record. It also keeps the first
  libpurple account it creates in `accounts.xml` and never re-applies options. Palm's original
  has the first bug too. Both are for a pull request to imlibpurpleservice, along with backing
  off a failing login (about 850 retries in 40 seconds, seen on the TouchPad).
- **No "webOS Chat" account type yet.** Messaging offers Palm's AIM, and the validator checks
  the key by signing in. The plan's account type signs in with the community account's token.
- **Chat threader db errors** on a `com.palm.imgroupchat` query with `_del`, seen with 3.0.5's
  threader; not yet checked with CE's.
- **SMS, MMS and telephony** paths answer with real errors (`com.palm.carrierdb.settings.current`
  isn't registered, for one), as Lunacy has no phone.
- Herrie's other connectors (Discord, Telegram, WhatsApp, Signal, Teams) need their own plugins,
  which Lunacy doesn't carry.
