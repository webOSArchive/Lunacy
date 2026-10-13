# Messaging and instant messaging

Lunacy ships webOS CE's Messaging and its IM stack, so Lunacy, webOS CE and stock webOS devices
can chat over AIM through webOS Chat's server (`oscar.webosarchive.org`). Measured
2026-10-10: on the Galaxy Tab A7 Lite, Messaging 3.2.0 signed in to the prototype server by
itself at startup, listed the reference TouchPad as a buddy with its presence, and exchanged
messages with it in both directions. The plan, the server and its deployment guide are the
webOS Chat project's (codepoet's `Oscar` repository; the plan is a shared doc). Since
2026-10-12 the server is live, and a Lunacy device signed in to the webOS Account signs in to
chat by itself ("Signing in with the webOS Account" below).

## What runs

| Piece | From | Where it is in Lunacy |
|---|---|---|
| Messaging 3.2.0 (Enyo 1) | webOS CE: Palm's 3.0.6606 with the CE project's changes (servers and channels, reactions, edits, receipts, emoji), 3.0.6607 on the device; 3.2.0 is CE's release number | `CoreApps/com.palm.app.messaging` |
| `imlibpurpletransport` (`com.palm.imlibpurple`) | webOS CE's build of codepoet's imlibpurpleservice fork (Herrie's, with the fixes below), `Workbench/vendor/im/transport` (`SOURCE` names the commit) | ROM `/usr/bin`, a native service |
| libpurple 2.14.13 and helper plugins | webOS CE | ROM `/usr/lib/libpurple.so.0`, `/usr/lib/purple-2` |
| `libaim.so` (AIM / OSCAR) | Pidgin 2.14.2's OSCAR code, built by the webOS Chat project for CE's libpurple, with webOS Chat's defaults | ROM `/usr/lib/purple-2/libaim.so` |
| GLib 2.x, GIO, libffi, tidy, opus, ogg, nsl, zlib | webOS CE's transport runtime | ROM `/usr/lib/synergy-runtime` |
| Chat threader (`com.palm.messaging.chatthreader`, JS) | webOS CE: Palm's, with Herrie's fixes | ROM `/usr/palm/services` |
| The messaging kinds, permissions, activities, frameworks | webOS CE | ROM `/etc/palm`, `/usr/palm/frameworks` |
| webOS Chat's account type (`org.webosarchive.chat`), service (`com.palm.webosarchive.chat`, JS) and setup page (`org.webosarchive.chat.setup`, a hidden Enyo app) | The webOS Chat project's `client/webos-chat`, at the commit `fetch-assets.sh` pins (`OSCAR_COMMIT`; cloned into `Workbench/vendor/oscar` when missing) | ROM `/usr/palm/public/accounts`, `/usr/palm/services`; bundled app |

`AndroidLuna/fetch-assets.sh` lays them in from `Workbench/vendor/touchpad/messaging` (CE) and,
for the transport, `Workbench/vendor/im/transport`. The transport starts with
`/usr/lib/synergy-runtime` first on its library path, written into its D-Bus service file.
The transport fixes from 2026-10-12, made in codepoet's fork and built by CE (Lunacy never
rebuilds it):
- accounts no longer go away by themselves five minutes after signing in (libpurple's
  auto-away), which showed every webOS Chat buddy idle;
- removing a buddy no longer aborts the transport (a string literal stored as an alias), which
  crash-looped at every sign-in while the delete waited;
- buddies' presence reaches Messaging after a restart, and accounts are signed back in;
- only the accounts webOS asks for are signed in, so an account removed while the transport
  wasn't running no longer signs in invisibly from libpurple's own `accounts.xml`. Lunacy's glibc 2.36 and
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

## Signing in with the webOS Account

With the webOS Community Account installed and signed in, nobody types a screen name or pass
phrase. The webOS Chat service (`com.palm.webosarchive.chat`) runs from three activities: at
start, hourly, and when the device's webOS Account record changes. It gets the account's token (`com.palm.accountservices/getAccountToken`), trades it
at the App Museum (`device.php?m=getChatCredentials`, through `/usr/bin/curl` as the community
account's service does) for the username and pass phrase, and keeps one account of the
`org.webosarchive.chat` type with them, replacing it when the username or pass phrase changes on
the website. That type has AIM's capability (`serviceName` `type_aim`, the transport as its
`onEnabled`), so the transport signs it in like an AIM account it was handed. Signed out (the
token call answers `NO_TOKEN`), it removes the webOS Chat account, as the Community Account's
sign-out clears its own data. With a username that is still an email (the Museum answers 409),
or when the token can't be asked for at all, it changes nothing.

In Accounts, webOS Chat is under Add an Account (Messaging & Chat) and never asks for a
password: its type's validator is a custom page (`customUI`, the hidden app
`org.webosarchive.chat.setup`), which turns chat on and has the service make the account ("webOS
Chat enabled"), or says to sign in or choose a username first. Change Login Settings on the
account opens the same page, showing the screen name. Removing the account (Accounts' own
Remove Account) turns chat off: the service keeps the id of the account it made in
`/var/preferences/com.palm.webosarchive.chat/state.json`, and when that account is gone without
its doing, no run makes another until webOS Chat is added again. Signing out of the webOS
Account resets that (codepoet, 2026-10-12). Palm's 3.0.5 AIM type and its validator are no
longer in the ROM, so nothing offers a password form for AIM; it asked for the pass phrase,
which nobody knows to type. Measured on the A7 Lite 2026-10-12: removed, relaunched (stayed
off), added again (enabled in about 9 s, signed in).

The type is its own because the 3.0.5 AIM template only lets Contacts and Messaging create its
accounts (`writePermissions`). The service has a `com.palm.*` id for the same reason as the community's apps: the
profile and account kinds only answer `com.palm.*` callers on a TouchPad.

Following a sign-in: the Community Account renames its `com.palm.palmprofile` account record
to the member when signing in, and back to "Local User" when signing out. The token's own kind
(`com.palm.palmprofile:1`) is the account service's alone (`db: permission denied` to anyone
else), so the activity watches the record: a db8 `find` with `"watch": true` on
`com.palm.account:1` for that template, which fires on a change and not because results exist
(a `watch` call would fire at once). Signing in renames the record before it saves the token,
so a change with no token yet is asked about again every 5 s, five times, before it counts as
signed out. The hourly and profile activities are completed with `restart`; one whose trigger
answered with an error is ended instead, so a refused watch can't spin. A re-sign-in that
doesn't rename the record (the same member, never signed out) is followed by the hourly run.

Measured 2026-10-12 on the Galaxy Tab A7 Lite against the live server: signed in as
`curator`, the service made the account and the transport set it online. Signed out (the
service's `signOut`, which the Community Account's UI doesn't offer), the next run removed the
chat account; signed in again through the UI, the watch fired, the account was made 1.8 s
later and the transport had it signing in 5 s after the sign-in, and messages went both ways.

## Testing against a development server

To try a development server, point the name at it in Lunacy's webOS root (development only,
nothing ships it):

```sh
adb shell "run-as org.webosarchive.lunacy sh -c 'printf \"127.0.0.1 localhost\n<server ip> oscar.webosarchive.org\n\" > files/webos/etc/hosts'"
```

Run the server with `OSCAR_ADVERTISE=<server ip>:5190`. In Messaging, add an AIM account with a
screen name and chat key from the server's `accounts.json`.

## Known bugs and open items

- **CE's transport and per-account settings.** `onCreate` stores the options nested under
  `config`, but the transport as seen on the TouchPad on 2026-10-10 handed libpurple the whole
  record, and never re-applied options to a libpurple account it had created. Unchecked
  against the current fork; webOS Chat doesn't need either, since the plugin's defaults are
  right. The login retry storm seen then (about 850 in 40 s) was AOL's client login, not a
  missing backoff (the CE project's finding: the transport already doesn't retry credential
  errors).
- **webOS CE is building webOS Chat in:** an enabler package for 3.1.0 and the 3.2.0 image,
  with the setup page inside its own app (the account type's `customUI` names that app there).
  Lunacy ships the page as the hidden app `org.webosarchive.chat.setup`.
- **The transport is CE's build, and CE's transport watchdog isn't ours.** Lunacy takes the
  transport webOS CE builds from codepoet's imlibpurpleservice fork (never rebuilt here;
  `Workbench/vendor/im/transport/SOURCE` names the commit). For 3.1.0, CE added a watchdog to its
  wrapper, `imwrap.sh` (fork `a40e37c`): it kills an on-demand transport stuck at startup (one
  thread on a futex for 60 s) and lets the resident one take over, after the first transport
  hung on 2 of 7 TouchPad boots. The cause isn't found; CE's leading suspect is its own
  wrappers deleting `/dev/shm/sem.PmLogLib`, a semaphore every PmLog process shares. Lunacy
  runs the binary directly, with no wrappers and no PmLog daemon, so it has neither the
  suspect nor the watchdog. When CE finds and fixes the cause (its 3.2.0 plan), check whether
  the fix is in the transport itself, and if so take that build.
- **Two AIM accounts: Messaging sends on one.** Its account list is keyed by service name,
  and Palm's AIM type (in the ROM until 2026-10-12) and webOS Chat's are both `type_aim`, so with both on a device every
  outgoing message goes out on one of them, signed in or not. Seen 2026-10-12 on the A7 Lite:
  messages queued on the old development account (`waiting-for-connection`) while the webOS
  Chat account was online; removing the old account fixed it. Palm's Messaging behaves so with
  any two accounts of one service; nothing in Lunacy changes it.
- **The invitation message in Add Buddy is never sent.** The transport's `sendBuddyInvite`
  (`SendOneCommandHandler::inviteBuddy` in imlibpurpleservice) reads only `group` from the
  command and adds the buddy without the message. Even passed on, AIM's plugin would send it
  only as an authorization request to a buddy already waiting for one. Whether Palm's 3.0.5
  transport behaved the same is unmeasured.
- The chat threader's `_del` query errors, seen with both threaders, were Lunacy's db8
  (fixed 2026-10-12, [fix-log.md](fix-log.md)).
- **SMS, MMS and telephony** paths answer with real errors (`com.palm.carrierdb.settings.current`
  isn't registered, for one), as Lunacy has no phone.
- Herrie's other connectors (Discord, Telegram, WhatsApp, Signal, Teams) need their own plugins,
  which Lunacy doesn't carry.
