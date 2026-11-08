# Microsoft 365 over Exchange ActiveSync: what's missing

Written 2026-11-07, brought up to date 2026-11-08. What it takes to offer modern Microsoft 365
(work and school) accounts over Exchange ActiveSync with OAuth, so they get mail, contacts and
calendar, not only the mail that IMAP gives. Nothing of it has shipped yet. The background is in [oauth.md](oauth.md) (sign-in,
the five adapters) and [eas.md](eas.md) (Palm's mojomail-eas on Lunacy).

## Two things this work must never break

**1. The legacy "Microsoft Exchange" account (`com.palm.eas`).** Palm's own template, with a
username and password, works fine against stand-alone Exchange servers (an Exchange of one's
own, Zoho) and must stay exactly as it is:

- Never edit the `com.palm.eas` template, or point it at OAuth or Microsoft 365. Modern
  Outlook gets a template of its own (below).
- The preload's Exchange adapters must stay inert for a server that still takes Palm's
  version. Today they are: adapter 1 changes the protocol version only when a host no longer
  offers the one Palm asks for. Adapters 3 to 5 act only where adapter 1 has. Bearer
  replaces the password only when the password is an `oauth2:` handle. Any new adapter (for
  example back-off) has to be scoped the same way, by what the server says or by the handle,
  never by "every EAS request".
- Before anything here ships: a legacy Exchange account against a stand-alone server (Zoho is
  the one tested in [eas.md](eas.md)) still adds, syncs mail, contacts and calendar, and sends.

**2. IMAP after OAuth (the "Microsoft Outlook" account, `org.webosarchive.lunacy.microsoft`; since
2026-11-08 hidden from Add Account and reached through the one "Microsoft Outlook" entry's fallback).**
This is what 0.9.3 ships and what works now: Microsoft sign-in, then Palm's IMAP and SMTP with
XOAUTH2. It must keep working, and keep being what Outlook.com and other personal accounts use:

- Don't change that template's capability providers (`palm://com.palm.imap/`), its validator,
  or the provider's IMAP and SMTP scopes in `providers.json`.
- Existing IMAP accounts are never moved to Exchange behind the person's back. Moving one
  would mean removing it and adding it again, the person's choice.
- Before anything here ships: a Microsoft account over IMAP still signs in, syncs, sends, and
  survives a refresh failure without a sign-in prompt, as checked on the Fold3 on 2026-11-07.

## Where it stands (2026-11-08)

**A "Microsoft 365" account type works end to end on the Fold3** (arm64 debug, Android 15,
against `wosatester@jonandnic.onmicrosoft.com`):

- **The account type** is `org.webosarchive.lunacy.microsoft365`, a template of its own in the
  sign-in app (`CoreApps/org.webosarchive.lunacy.signin/accounts/`), listed in Add Account as
  "Microsoft Outlook" (Email, Contacts, Calendar) beside Palm's "Microsoft Exchange", which didn't
  change. It is the only Microsoft entry: the IMAP type is hidden (below, "One entry"). Its capabilities are Palm's Exchange ones, with the same
  ids (`com.palm.eas.mail`, `.contacts`, `.remotecontacts`, `.calendar`, `.tasks`): mojomail-eas
  looks for those ids, and the accounts service only ever looks a capability up inside an
  account's own template, so two templates sharing them is safe. Its provider in
  `providers.json` is `microsoft365`: the same Entra app, the EAS scope, `scopeOnToken`, and
  `com.palm.eas/validateAccount`.
- **Seen working:** sign-in (Microsoft's page in the sign-in page's web view), validation with
  provisioning (the 449 adapter; the tenant's policy needs no screen lock), FolderSync (17
  folders), mail (17 messages), a message sent to itself and **pushed back through Ping about
  5 s later** without a manual sync, the account's two contacts and three events arriving, a
  contact added and edited on the device and stored by Microsoft (a server id given, status 1),
  events added on the device with a location, a note, a reminder and a weekly recurrence, an
  event renamed and one deleted.
- **What it took, besides the five adapters in [oauth.md](oauth.md):** a sixth, which rewrites
  calendar items in Sync requests sent at 16.x (`libpreload/calfilter.h`), since Exchange Online
  refuses Palm's 12.1 items with status 6; the back-off (`libpreload/backoff.h`); and the
  token rules of IMAP carried over to the Exchange path. All three are in [oauth.md](oauth.md).
- **Legacy Exchange unchanged:** the Fold3's own "Microsoft Exchange" account (a Zoho server
  offering 14.1) pinged, synced and answered 200 throughout, on every build of the day. None of
  the new code acts for it: the calendar rewrite needs the version adapter at 16.x, the
  back-off needs Microsoft's headers, and the token rules need an `oauth2:` handle.
- **How a person picks it** is still codepoet's call (below). A second Add Account entry was
  the reversible way to test.

## What's missing

1. **One entry: done (2026-11-08, codepoet's call).** See "One entry" below. Not yet seen: a
   personal account made that way, since Outlook.com's IMAP still refuses the test account
   (item 9), and a person with no Microsoft sign-in in the web view yet (the retry's page may
   then ask for the password again; `domain_hint` only narrows the picker).
2. **Server-made changes reaching the device.** Items already on the server arrived at the first
   sync. Still to see: a contact and an event created, edited and deleted in Outlook on the web
   reaching the device; recurring events with exceptions (16.x names an exception by
   `InstanceId` where Palm sends `ExceptionStartTime`, and `calfilter.h` doesn't touch
   exceptions yet); time zones other than the device's; meeting invitations from someone else
   (`respondToMeetingInvitations`, untried in [eas.md](eas.md)).
3. **A conflict after a refused edit.** An edit refused before the calendar rewrite existed left
   the device's copy behind the server's; the next edit got status 7 (the server won) and the one
   after went through. Palm keeps its own copy after a 7, so a device edit can be lost that way.
   Seen once, after a failure that can no longer happen.
4. **Back-off: seen against a stand-in, not yet against Microsoft.** A local server answering as
   Exchange Online throttles (503, `Retry-After`, `X-MS-ASThrottle`, `X-MS-BackOffDuration`)
   got one request; the next, 20 s later, never left the device (it failed at once as
   `CONNECTION_FAILED`); after the 120 s it asked for, requests went out again. Microsoft
   hasn't throttled the account since, so its real headers haven't been seen through it.
   Palm's validator reports a 503 as `401_UNAUTHORIZED` ("check your login"), which is
   misleading while adding an account during a throttle.
5. **The token rules on the Exchange path** follow IMAP's (no token just now: a network failure
   the service retries; the first 401 of a token that should have worked: a server error, and the
   retry asks for a fresh token). The normal path was seen working; the two failure paths
   haven't been made to happen on a device yet.
6. **Palm's debug log** aborted the service while printing a contacts Sync against Microsoft 365
   (`com.palm.eas` at debug level) before adapter 4 existed. Not rechecked: the preload's own
   request dump (oauth.md) was used instead, which logs only the test host.
7. **Security policies against Exchange Online.** The sign-in page handles a policy as Email's
   wizard does, and Android's screen lock meets it (luna-deltas A21). Still open: remote wipe
   (`com.palm.storage`, not answered), and the policy's inactivity timeout and attempt limit,
   which Android doesn't enforce ([eas.md](eas.md), Open).
8. **Tenant consent.** The Entra app ("webOS Email", publisher-verified) must list
   `EAS.AccessAsUser.All`. A tenant that doesn't let users consent to apps needs its admin to
   grant it. The sign-in page should tell the person that plainly rather than show Microsoft's
   raw error.
9. **Outlook.com and other personal accounts can't use it.** Microsoft refuses the EAS scope
   for them (AADSTS70011). They get the IMAP account type through the fallback (mail only). (Microsoft's IMAP has its own
   bug for personal accounts, [oauth.md](oauth.md).)
10. **Throttled test devices.** Palm's EAS device id is per device, not per account, so removing
    and adding an account again doesn't clear a throttle. The back-off now keeps a device from
    deepening one.

## One entry (2026-11-08)

codepoet: "merge them into one Microsoft entry with IMAP fallback. Microsoft is so schizo about
what 'Outlook' is, so a fallback makes sense." Add Account shows one **Microsoft Outlook** entry (the
Exchange template, now named "Microsoft Outlook" as the IMAP one was, its line "Email (Calendar and Contacts, if
available)"); the IMAP template is `hidden` (Enyo's accounts library
and Email both leave hidden templates out of Add, while accounts already made with it are listed
and work as before). Signing in:

1. The sign-in page asks the OAuth service for the `microsoft365` provider with `fallback: true`
   (not when signing an existing account in again: an account keeps its type).
2. Microsoft's page takes a personal account through with Exchange's scope and hands back a
   code; the **token request** then answers AADSTS70011 (measured on the Fold3 with
   `wosatester@outlook.com`; the authorize redirect carries no error). The service, seeing the
   scope refused (`invalid_scope`, or AADSTS70011 in the description, at either step), starts
   the `microsoft` (IMAP) provider's sign-in in the same call: a second page step. That one goes
   without `prompt=select_account` and with the refused provider's `fallbackParams`
   (`domain_hint=consumers`), so Microsoft's page, still signed in, hands back at once (0.5 s,
   no second account picker; without the hint it asked to pick again).
3. The `done` step names the IMAP provider's template and validator; the page checks the account
   with Palm's IMAP service, says the account is personal and gets mail only, and hands Accounts
   the hidden template (from `listAccountTemplates`) in place of the one tapped, which
   `entry-add.js`/`entry-first-launch.js` take (`msg.template`).

Work and school accounts never reach the fallback. Seen on the Fold3: one Microsoft entry in
Add Account; the personal test account refused for Exchange, signed in again for IMAP with no
second pick, validated by Palm's IMAP service, which got Outlook.com's "User is authenticated
but not connected" (item 9, Microsoft's side), and its handle forgotten; the hidden template
listed for the page; the existing Exchange accounts (Microsoft 365 and Zoho) syncing as before.

## Before it ships

- Everything in "Two things this work must never break", checked on a device.
- A new Microsoft 365 account over Exchange on a clean install:
  - sign-in, a security policy, mail;
  - contacts and calendar both ways;
  - send, reply and forward;
  - push;
  - a day left alone, then a refresh, with no sign-in prompt.
- `LunaRuntimes/pdk/tests` extended for any new adapter, run in every PDK build as the
  others are (`backoff_test.c` and `calfilter_test.c` are, the second on Palm's own refused Add).
