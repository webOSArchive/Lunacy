# Microsoft 365 over Exchange ActiveSync: what's missing

Written 2026-11-07. What it would take to offer modern Microsoft 365 (work and school) accounts
over Exchange ActiveSync with OAuth, so they get mail, contacts and calendar, not only the mail
that IMAP gives. None of this has shipped. The background is in [oauth.md](oauth.md) (sign-in,
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

**2. IMAP after OAuth (the "Microsoft Outlook" account, `org.webosarchive.lunacy.microsoft`).**
This is what 0.9.3 ships and what works now: Microsoft sign-in, then Palm's IMAP and SMTP with
XOAUTH2. It must keep working, and keep being what Outlook.com and other personal accounts use:

- Don't change that template's capability providers (`palm://com.palm.imap/`), its validator,
  or the provider's IMAP and SMTP scopes in `providers.json`.
- Existing IMAP accounts are never moved to Exchange behind the person's back. Moving one
  would mean removing it and adding it again, the person's choice.
- Before anything here ships: a Microsoft account over IMAP still signs in, syncs, sends, and
  survives a refresh failure without a sign-in prompt, as checked on the Fold3 on 2026-11-07.

## Where it stands

- **Mail works over Exchange in local test setups.** Palm's mojomail-eas reaches Exchange
  Online through the five preload adapters in [oauth.md](oauth.md): version, provisioning
  status, DeviceInformation, replies cut to 12.1, and sends in ComposeMail form. On the A7
  Lite (2026-11-06), mail arrived and opened, the calendar and contact collections synced
  (nearly empty), and a new message, a reply and a forward were delivered.
- **That only ever ran as a local setup:** a development build reading
  `files/oauth-providers.json` (the Microsoft provider's EAS scope and the `com.palm.eas`
  validator), plus an Exchange copy of the Microsoft template in `local-assets`. The repository
  has neither.
- **Half of that setup breaks the account.** On 2026-11-07 the Fold3 had the override but the
  repository's IMAP template. The account validated through Exchange, then was created as an
  IMAP account with Exchange's settings, and failed to fetch mail ("internal error"). The
  Exchange path needs its own template. The override is now set aside on the Fold3
  (`files/oauth-providers.json.eas-test`, [android5-setup.md](android5-setup.md)).

## What's missing

1. **An account type of its own.** A new template, not a change to either of the two above,
   for example `org.webosarchive.lunacy.microsoft365` ("Microsoft 365"):
   - its MAIL, CONTACTS and CALENDAR capability providers implemented by `palm://com.palm.eas/`;
   - the sign-in page as its `customUI`;
   - a provider entry in `providers.json` with the EAS scope
     (`https://outlook.office.com/EAS.AccessAsUser.All offline_access openid email`,
     `scopeOnToken`) and the `com.palm.eas/validateAccount` validator.

   Then decide how a person picks it:
   - A second entry in Add Account ("Microsoft 365 (work or school)" beside "Microsoft
     Outlook").
   - Or one Microsoft entry that chooses after sign-in. The id_token says which kind of
     account it is: personal Microsoft accounts carry the consumer tenant id
     `9188040d-6c67-4c5b-b112-36a304b66dad` in `tid`.

   Choosing after sign-in means scopes for both, or a second consent. codepoet's call, and a
   look-and-feel question (luna-deltas B23).
2. **Contacts and calendar with real data.** Only near-empty collections have been seen.
   Still to check:
   - contacts and events created on each side showing up on the other;
   - edits and deletions;
   - recurring events and time zones;
   - meeting invitations from someone else (`respondToMeetingInvitations`, untried in
     [eas.md](eas.md)).
3. **Push.** Ping requests go out and are answered, but new mail arriving by push without a
   manual sync hasn't been seen yet.
4. **Microsoft's back-off.** Palm's service ignores `X-MS-BackOffDuration` and `Retry-After`.
   Exchange Online throttles per device partnership (`X-MS-ASThrottle: RecentCommands`), so
   one crash loop or retry storm locks a device out for 15 minutes or more (seen on the
   Fold3). This needs an adapter that holds a host's requests until the back-off has passed,
   scoped to hosts that send it, as above.
5. **A token that can't be had just now, on the curl path.** IMAP and SMTP got this fix on
   2026-11-07 (`mailauth.h`): no token just now fails the connection, and the first refusal
   of a good-looking token is retried with a fresh one. In the Exchange glue (`preload.c`,
   `CURLOPT_USERPWD`), no token still sends the handle as a Basic password, which the server
   refuses, and Palm's service then asks the person to sign in again. It needs to fail as a
   network error that mojomail-eas retries, and to treat a first 401 for a Bearer token like
   IMAP's first refusal.
6. **Palm's debug log aborts the service** while printing a contacts Sync against Microsoft
   365 (`com.palm.eas` at debug level; info level is fine). It was adapter 4's case at the
   time. Recheck at debug level with adapter 4 in place before relying on debug logs.
7. **Security policies against Exchange Online.** The sign-in page handles a policy as Email's
   wizard does, and Android's screen lock meets it (luna-deltas A21). Still open: remote wipe
   (`com.palm.storage`, not answered), and the policy's inactivity timeout and attempt limit,
   which Android doesn't enforce ([eas.md](eas.md), Open).
8. **Tenant consent.** The Entra app ("webOS Email", publisher-verified) must list
   `EAS.AccessAsUser.All`. A tenant that doesn't let users consent to apps needs its admin to
   grant it. The sign-in page should tell the person that plainly rather than show Microsoft's
   raw error.
9. **Outlook.com and other personal accounts can't use it.** Microsoft refuses the EAS scope
   for them (AADSTS70011). They stay on the IMAP account type. (Microsoft's IMAP has its own
   bug for personal accounts, [oauth.md](oauth.md).)
10. **Throttled test devices.** The Fold3's Exchange partnership was throttled after the
    2026-11-06 crash loop. Palm's EAS device id is per device, not per account, so removing
    and adding the account again doesn't clear it. Wait the throttle out before testing there.

## Before it ships

- Everything in "Two things this work must never break", checked on a device.
- A new Microsoft 365 account over Exchange on a clean install:
  - sign-in, a security policy, mail;
  - contacts and calendar both ways;
  - send, reply and forward;
  - push;
  - a day left alone, then a refresh, with no sign-in prompt.
- `LunaRuntimes/pdk/tests` extended for any new adapter, run in every PDK build as the
  others are.
