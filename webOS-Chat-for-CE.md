# webOS Chat on webOS CE: a handoff for review

From the Lunacy project, 2026-10-12, for the webOS CE (webos-doctor-ce) project to review.
It covers what webOS CE 3.1.0 on the TouchPad needs so that a signed-in webOS Community
Account can chat over webOS Chat with no setup, as Lunacy now does. Facts marked *measured*
were seen on a device; everything else is a reading of the code and should be checked.

## What webOS Chat is

- **Server:** an OSCAR (AIM) server at `oscar.webosarchive.org:5190`, live since 2026-10-12.
  Source and deployment: [Oscar-AIM-for-webOS](https://github.com/webOSArchive/Oscar-AIM-for-webOS)
  (`server/`, `DEPLOY.md`).
- **Accounts:** the App Museum's webOS Accounts. The screen name is the account's username,
  and the password is a webOS Chat pass phrase shown on the member's account page
  (webos-catalog-service, migration 0012). A device trades its Community Account token for
  both at `https://appcatalog.webosarchive.org/WebService/device.php?m=getChatCredentials`
  (the HTTP 409 answer means the username is still an email).
- **Clients:** libpurple's AIM plugin, which CE's own IM transport and Messaging 3.2.0 load
  like any libpurple protocol. The plugin speaks plain FLAP with the MD5 (BUCP) login and no
  TLS, as stock webOS does.
- **Stock webOS (1.4.5 to 3.0.5)** reaches the same server through one `/etc/hosts` line for
  `login.messaging.aol.com`. That path is live and *measured*: two stock devices chatted.

## What is measured

- **2026-10-10, the reference TouchPad (CE 3.1.0)** signed in with CE's transport and
  Messaging, and chatted both ways with Lunacy. It used a prototype `libaim.so` in
  `/usr/lib/purple-2` and Palm's 3.0.5 `com.palm.aol` account type, both copied in by hand.
  The account's server came from a hand-edited
  `/var/preferences/com.palm.purple/transport/accounts.xml`, pointing at a development
  machine.
- **2026-10-12, Lunacy on a Galaxy Tab A7 Lite** runs CE's own Messaging 3.2.0, transport,
  libpurple 2.14.13, synergy runtime and chat threader, all taken off the reference TouchPad,
  plus the three webOS Chat pieces below. With the Community Account signed in, the chat
  account was made 1.8 s after the sign-in and signing in 5 s after it, and messages went
  both ways with a stock device. Signing out removed the chat account.

So the protocol, the transport and Messaging are known to work on CE. What's missing on CE is
the three pieces below, in the image.

## The three pieces

All three are in the Oscar repository. Lunacy takes them from commit
[`cf5f5d9`](https://github.com/webOSArchive/Oscar-AIM-for-webOS/commit/cf5f5d93e3210cef93e69f4b0b306cd75632be69).

### 1. The AIM plugin: `/usr/lib/purple-2/libaim.so`

- CE's libpurple 2.14.13 was built without OSCAR (`liboscar.so` and `libaim.so` are gone).
  Pidgin removed AIM in 2.14.3, so the plugin is built from Pidgin 2.14.2's OSCAR sources
  against CE's 2.14.13.
- **Build:** `client/libaim/build-touchpad.sh`. It uses crosstool-ng `arm-cortex_a8` and
  lenny's glib 2.16.6 headers.
- **Defaults changed,** so no per-account settings are needed:
  - login host and SSL login host: `oscar.webosarchive.org`;
  - login: `md5_login`;
  - encryption: `no_encryption`;
  - `OSCAR_SERVER` overrides the host at build time.
- **It has to be in the image** (or added by a root install script). libpurple only loads
  plugins from `LIBDIR/purple-2` (`purple_plugins_add_search_path(LIBDIR)` in `plugin.c`),
  and the transport adds no other folder.
- **The reference TouchPad's copy is out of date:** it's the 2026-10-10 build with the
  development defaults.

### 2. The account type: `/usr/palm/public/accounts/org.webosarchive.chat/`

- `client/webos-chat/accounts/org.webosarchive.chat/org.webosarchive.chat.json`. Its one
  capability provider is `MESSAGING`/`IM` with `serviceName` `type_aim`, the
  `com.palm.imlibpurple` implementation and `onEnabled`, and the libpurple immessage and
  imcommand kinds. So the transport signs in its accounts like AIM ones.
- **Why it's its own type:** Palm's `com.palm.aol` only lets Contacts and Messaging create
  accounts (`writePermissions`), and CE no longer has it anyway. This type has no validator,
  so Accounts doesn't offer it under "Add account".
- **Icons, needs CE's attention.** The template's icons point at `../com.palm.aol/images/`
  (`aim-32x32.png`, `aim-logo.png`), which isn't on CE. Either the image ships those images
  in that folder, or the template gets its own icons. The second is a change to the Oscar
  repository; send it there.

### 3. The service: `com.palm.webosarchive.chat` (JS, node)

`client/webos-chat/service/`, installed as `/usr/palm/services/com.palm.webosarchive.chat/`.

What it does, in `service.js`:

1. Gets the token from `palm://com.palm.accountservices/getAccountToken`, CE's Community
   Account service. A signed-out device answers `NO_TOKEN`, "Token could not be fetched".
2. Trades the token at the Museum by running `/usr/bin/curl` through `child_process`, as the
   Community Account's own service does, since node 0.4's TLS can't reach the Museum.
3. Keeps exactly one account of the `org.webosarchive.chat` type, using
   `com.palm.service.accounts` `listAccounts`, `createAccount`, `deleteAccount` and
   `readCredentials`. It replaces the account when the username or pass phrase changes.
4. When signed out, it removes that account, matching the Community Account's sign-out.
   Any other failure changes nothing.

Its three activities (`configuration/activities/com.palm.webosarchive.chat/`), which the
configurator creates at start:

| Activity | When it runs | How it completes |
|---|---|---|
| `start` | At start, with `internet` | Plain complete (runs once) |
| `hourly` | Every hour, with `internet`; picks up a username or pass phrase changed on the website | `restart: true` |
| `profile` | A `com.palm.db/find` with `"watch": true` on `com.palm.account:1` (`beingDeleted` = false, `templateId` = `com.palm.palmprofile`), triggering on `key: "fired"` | `restart: true` |

How the `profile` activity works:

- **Why a `find` watch:** a `find` watch doesn't fire just because results exist. A bare
  `watch` call would fire at once and loop.
- **What it follows:** the Community Account renames that account record to the member at
  sign-in and back to "Local User" at sign-out.
- **The token race:** sign-in renames the record before it saves the token, so after a
  change the service asks for the token again every 5 s, five times.
- **Why not watch the token:** the token's own kind, `com.palm.palmprofile:1`, answers
  `db: permission denied` to anyone but its owner. Measured on Lunacy, which follows the
  TouchPad's db8 rules.

Its completion handler gives up an activity whose trigger answered with an error, instead of
restarting it, so a refused watch can't spin.

What CE has to check or add for it:

- **`ls2` role files.** CE needs public and private roles and a bus service file for
  `com.palm.webosarchive.chat`, as the `full-ce` overlay does for its other services. Lunacy
  doesn't read role files, so this part is untested anywhere. The service calls:
  - `com.palm.accountservices`;
  - `com.palm.service.accounts`;
  - `com.palm.activitymanager` (adopt, complete);
  - `com.palm.db`, through the activity's trigger, which the activity manager runs on the
    service's behalf.
- **The `com.palm.*` id is deliberate:** `com.palm.account:1` is readable by `com.palm.*`
  callers (the accounts service's db permissions).
- **Tools it needs:** `/usr/bin/curl` with a CA bundle that trusts the Museum's certificate,
  and node's `child_process`.
- **Unchecked on the TouchPad:** whether the configurator picks up a service's
  `configuration/activities` from `/usr/palm/services` as Lunacy's does, and whether
  `find`+`watch` triggers behave the same in CE's activity manager.

## The transport

CE's IM transport is Herrie's
[imlibpurpleservice](https://github.com/webOS-ports/imlibpurpleservice). Things to check:

- **Login retries.** On 2026-10-10 the reference TouchPad's transport retried a failing
  login about 850 times in 40 s (it was trying AOL's client login). Upstream at `3e1d0da`
  (2026-08-15) doesn't retry the credential errors (invalid username, authentication
  failed or impossible, invalid settings), and retries the rest on a 20 s timer activity
  (`IMLoginFailRetryHandler`). **Check which transport the CE image ships.** If it predates
  that, an update covers it. If it already includes it, the storm was the AOL client login's
  error type, and nothing is needed for webOS Chat, whose plugin uses the MD5 login.
- **Per-account server settings.** In the build seen on the TouchPad, the
  `com.palm.config.libpurple` record was passed whole as libpurple's options, while the
  template's `onCreate` nests them under `config`. The libpurple account it created in
  `accounts.xml` also never had its options applied again. webOS Chat doesn't need either,
  because the plugin's defaults are right. Upstream may have fixed both: it reads `config`
  in `IMServiceHandler.cpp` and `IMLoginState.cpp`. Check before calling it a bug.
- **Herrie:** changes to his repositories are ours to make, sent as pull requests.

## Getting it onto devices

- **In the CE image:** the three pieces in the `full-ce` overlay, with the role files and
  the icons.
- **On TouchPads already running CE:** the next CE release, or the OTA path in `OTA-PLAN.md`.
  For testers, the Oscar repository's `client/touchpad-install.sh` installs the plugin and
  Palm's AIM type with a remount. It would need extending to the service, its roles and
  `org.webosarchive.chat`, and it needs root.
- **Stock-webOS devices** are covered separately by the hosts line and need nothing from CE.

## The reference TouchPad needs cleaning first

It still has the 2026-10-10 prototype:

- `/usr/lib/purple-2/libaim.so`, the old development defaults;
- `/usr/palm/public/accounts/com.palm.aol`, from stock 3.0.5;
- an AIM account `TouchPadTest`, id `++OSVNCumzO1o3EE`;
- `/var/preferences/com.palm.purple/transport/accounts.xml` pointing at `192.168.10.45:5190`,
  with the original beside it as `accounts.xml.lunacy-bak`.

To undo it: delete the account, put `accounts.xml.lunacy-bak` back, and run
`client/touchpad-install.sh --remove` from the Oscar repository. The Lunacy repository's
[Docs/android5-setup.md](https://github.com/webOSArchive/Lunacy/blob/main/Docs/android5-setup.md)
logs every change made to it.

## A test plan

1. On a clean CE TouchPad with the new image, sign in to the Community Account with a
   member who has a username, not an email.
2. Within seconds, a "webOS Chat" account should appear in Accounts, and Messaging should
   show it signing in.
3. Chat both ways with a Lunacy device and with a stock device that has the hosts line.
4. Get a new pass phrase on the website: within the hour, the account should be replaced
   and sign in again.
5. Sign out (`palm://com.palm.accountservices/signOut`; the UI doesn't offer it). The chat
   account should go away.
6. While watching the server's log (`journalctl -u oscard`), confirm a wrong pass phrase
   isn't retried in a tight loop.

## Questions for the CE project

- Should webOS Chat be in CE by default, or something a user opts into?
- Does CE's Community Account sign-in rename the `com.palm.palmprofile` account record as
  the version on Lunacy does? The `profile` activity depends on it; without it, chat starts
  at the next boot or hourly run.
- Which imlibpurpleservice commit is in the image, and is updating it in scope?
- The AIM template's icons: ship Palm's `com.palm.aol/images`, or new webOS Chat icons?
