# OAuth for mail accounts

Google and Microsoft no longer take a password from a mail client: they want OAuth 2, and
an XOAUTH2 login over IMAP and SMTP. Palm's mail services know only passwords. This is how
Lunacy signs those accounts in while running Palm's binaries exactly as the TouchPad ships
them. The plan was made on 2026-10-10 (two ways to sign in: on the device, and through
webOSArchive's broker for devices too old for the providers' pages); the work started
2026-11-05, for 0.9.5.

## How it fits together

```
 Email / Accounts ──Add Account──▶ "Google" / "Microsoft Outlook" template (validator.customUI; Microsoft's IMAP type is hidden, its fallback)
                                     │
                                     ▼
      org.webosarchive.lunacy.signin (the sign-in page, in a cross-app frame)
        │ signIn ─────────▶ palm://org.webosarchive.lunacy.oauth   (card/OAuth.kt)
        │                    ├ on the device: the provider's page in the frame's WebView
        │                    │   (its redirect caught with webOS's `redirects`), or Android's
        │                    │   browser, as a Custom Tab, with a loopback listener (a provider
        │                    │   that refuses web views: Google)
        │                    └ broker: oauth.wosa.link's code; the person signs in elsewhere
        │ ◀── handle "oauth2:<32 hex>", email, the provider's server settings
        │
        │ com.palm.imap/validateAccount {username, password: handle, config}
        ▼
   mojomail-imap / mojomail-smtp (Palm's, unchanged)
        │  LOGIN "user" "oauth2:…"   /   AUTH PLAIN …
        ▼
   liblunacy-preload: g_io_channel_write_chars / read_chars   (libpreload/mailauth.h)
        │  token? ── luna-send link ──▶ oauth/token {handle}  (private bus; refreshes)
        ▼
   AUTHENTICATE XOAUTH2 …   /   AUTH XOAUTH2 …   ──▶ the provider's server
```

- **The handle is the password.** Accounts keeps it in the key manager like any password;
  the tokens stay in the OAuth service (`files/oauth/accounts.json`, a ratchet item like the
  key manager's file). The handle never changes for an account, so a refresh needs nothing
  from Accounts or the mail services.
- **The translation** (`LunaRuntimes/pdk/libpreload/mailauth.h`, glue at the end of
  `preload.c`). Palm's mail library reads and writes its connections, plain and TLS, through
  glib's `g_io_channel_read_chars` and `g_io_channel_write_chars`; Lunacy's preload, already
  in every native process, stands in front of both:
  - IMAP: `tag LOGIN "user" "oauth2:…"` becomes `tag AUTHENTICATE XOAUTH2 <payload>` (SASL-IR).
    A refused token draws a `+ <json>` challenge (Gmail); it is answered with an empty line and
    hidden, so the service sees only the tagged NO it expects (but see "A refusal" below).
  - SMTP: mojomail prefers AUTH LOGIN, which sends the password last, in a line of its own.
    So when a server offers XOAUTH2, its EHLO is shown without LOGIN and with PLAIN, and
    `AUTH PLAIN <\0user\0oauth2:…>` becomes `AUTH XOAUTH2 <payload>`; a `334` challenge is
    answered and hidden. A real password on such a server (Microsoft offers LOGIN and XOAUTH2,
    no PLAIN) is sent as the AUTH LOGIN exchange mojomail would have made.
  - A handle the service doesn't know leaves the line untouched. Channels on files and pipes
    are left alone. Nothing that isn't text is ever held, and a connection that sends IMAP's
    `COMPRESS DEFLATE` is left alone from then on: Gmail offers it, and mojomail takes it, so
    every byte after it is deflate's. (Holding a compressed command, which has no newline, hung
    every Gmail sync and every other IMAP account that compresses, on the first Gmail test.) A refused token makes the next login for that handle ask for a fresh one
    (`stale`), since a provider can end a token early (a password change).
  - **A refusal is final only when the sign-in is over (2026-11-07).** Palm's services take a
    refused login as a wrong password and stop: no retry until the credentials change, and
    Email's "Check your login and password" stays up. That turned one bad moment into a
    sign-in prompt (Gmail on the Fold3, overnight 2026-11-06: most likely a refresh that
    couldn't reach Google while the phone dozed, handing back the expired token). So when no
    token can be had just now (`token` answers error -12), the login's write fails; and the
    first refusal of a token that should have worked drops the connection instead of showing
    the NO. The service takes either as a network failure and tries again (30 s, measured),
    and that login asks for a fresh token. A refusal is shown as it is for a token the
    provider issued within the minute (`fresh`), an empty one (the refresh was refused), and
    one a retry asked for. IMAP and SMTP only: for Exchange (curl) no token just now still
    goes as the handle and is refused as before (Open).
- **The token** comes over the process's own luna-send link (`LUNACY_BUS` and the caller
  token Lunacy gave the process), so `oauth/token` is reachable on the private bus only, and
  only from processes Lunacy started. It refreshes a token with under five minutes left. Its
  answer: the token, with `fresh` when it is under a minute old; `""` when the provider refused
  the refresh (`invalid_grant`, logged with the provider's reason, Microsoft's AADSTS code); error
  -12 when the provider couldn't be asked and the token held won't do (expired, or the one just
  refused); error -11 for a handle it doesn't know.
- **The sign-in page** asks the mail service to validate the account with the handle, as
  Email's wizard validates a password, so a new account is checked against the real server
  before it is made. Then Accounts' own Account Settings page and Create Account follow, as for
  any account.
- **Signing in again.** When a refresh is refused (the person revoked access), logins fail as a
  wrong password and Email shows its "Check your login and password". The account's page in
  the Accounts app (Change Login Settings) offers Sign In Again: the same account signed in
  again keeps its handle, and the page saves the credentials (`modifyAccount`) so the mail
  service hears `credentialsChanged` and logs in at once. The OAuth service also sends the
  template's `credentialsChanged` hooks itself as soon as it has the new tokens (as Accounts'
  modifyAccount would): the page may be gone by the time the browser hands back, and on the
  Fold3 (2026-11-07, 14:59) a sign-in saved good tokens while the account kept its error and
  its notification, since nothing told the mail service. Twice is harmless. Email's own Change
  login settings opens Palm's password form instead (open question below).
- **Removing an account** takes its tokens at the next start of Lunacy (a day's grace for a
  sign-in whose account is still being made).

## Providers

`AndroidLuna/src/main/assets/oauth/providers.json`: endpoints, scopes, the mail servers, which
way each signs in on the device, and the broker app's name. `clientId` empty means sign-in on
the device isn't offered for that provider; `brokerApp` empty means no broker sign-in. A
development build also reads `files/oauth-providers.json` on the device, which may replace a
provider (the test setup below).

| | Google | Microsoft (Outlook.com, Microsoft 365) |
|---|---|---|
| Mail | IMAP imap.gmail.com:993 SSL, SMTP smtp.gmail.com:465 SSL | IMAP outlook.office365.com:993 SSL, SMTP smtp.office365.com:587 TLS |
| Scopes | `https://mail.google.com/ openid email` | `https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send offline_access openid email` |
| On the device | Android's browser, as a Custom Tab where the browser has them (Lunacy's task, so Lunacy is in front again when it closes), and a loopback redirect (`http://127.0.0.1:<port>/`): Google refuses sign-in in a WebView (`disallowed_useragent`). Offered only where a browser is installed. The loopback's "Signed in" page has a Back to Lunacy button (`org.webosarchive.lunacy://signed-in`, the shell's BROWSABLE filter) for when Android won't let Lunacy come forward itself | The sign-in page's WebView; redirect `https://login.microsoftonline.com/common/oauth2/nativeclient` |
| Client | A "Desktop app" OAuth client (its secret ships with it, as Google intends for desktop clients) | A public client ("Mobile and desktop applications"), no secret |

**The clients (2026-11-05).** Google: a Desktop app client, its id in `providers.json`. Its
secret is not in the repository: Google watches public repositories for its client secrets
and may revoke one found there. It lives in `oauth.properties` at the top of the repo
(`google.clientSecret=…`, git-ignored like `keystore.properties`), which the build turns into
`assets/oauth/clients.json` (`OAuthClients` in `AndroidLuna/build.gradle.kts`); a machine that
builds releases needs that file. Microsoft: an Entra app registration "webOS Email"
(`95d7ba4d-1ae2-44d2-a8ef-3b06a31345f6`), multitenant plus personal accounts, public client, no
secret.

**Gmail works (2026-11-05, Galaxy Z Fold3):** `jonathan@bennit.ai` signed in through Firefox
(Google's unverified-app warning, 2-Step Verification by text message), Lunacy came back over
the browser on its own, the account was validated and made, and its inbox synced and went to
IDLE. Google granted `https://mail.google.com/ openid userinfo.email`. A first attempt that
day got "Invalid credentials" from Gmail; the service now logs the granted scopes and refuses a
sign-in without the mail scope (Google's consent lets a person untick it) with a message saying so.

**Microsoft 365 works (2026-11-05, Fold3):** `wosatester@jonandnic.onmicrosoft.com`, once
codepoet granted the tenant's admin consent: signed in in the page's WebView (no consent page:
the admin had given it), validated, made, synced. Microsoft granted the Exchange Online scopes
the consent covers (`https://outlook.office.com/…`, EAS among them). **Outlook.com**
(`wosatester@outlook.com`) still signs in but its IMAP answers "User is authenticated but not
connected", on the Fold3 as on the A7, while Outlook on the web says the mailbox isn't set up yet
for that account: Microsoft's side, not Lunacy's. To try again once the mailbox is there.

First real tests, on the A7 Lite (2026-11-05): Microsoft's own sign-in page works in the
sign-in page's WebView (the Samsung keyboard typed into it; key events - adb's, a hardware keyboard's - didn't reach a
native browser view until `AppWindow.dispatchKeyEvent` passed them to the focused one, fixed the
same day), and its redirect, the code exchange and the
address from the id_token all work. Then:
- **Outlook.com** (`wosatester@outlook.com`): the token was taken, but IMAP answered "User is
  authenticated but not connected." Microsoft had asked to verify the account by a code sent to
  its recovery address before the password was offered; the account's own setting "Let devices
  and apps use POP/IMAP" is also off by default on many accounts. To check in Outlook on the web.
- **Microsoft 365** (`wosatester@jonandnic.onmicrosoft.com`): "Need admin approval" for the
  unverified app; declining came back to the page as `access_denied`, shown there.

**The registrations, as set up (decision 1 of the 2026-10-10 plan):**

- **Google Cloud:** an OAuth client of type Desktop app; the OAuth consent screen with the
  scopes above; the test accounts added as test users while the app is unverified (at most 100
  users). `https://mail.google.com/` is a restricted scope: public use needs Google's
  restricted-scope verification (codepoet started it on 2026-11-05; the domain and information
  pages wait 24 hours to be checked again). A security assessment (CASA, by an authorised lab,
  paid at the higher tiers) is tied by Google's policy to an app that can reach the restricted
  data through a server; whether one that keeps everything on the device is exempt isn't settled
  in what Google publishes (its forums disagree). Lunacy's sign-in on the device has no server in
  the Gmail path: the tokens stay on the device and Lunacy talks to Google directly. **The broker
  is the exception:** for Google it would make the code exchange, hold the client secret and pass
  the refresh token, which is reaching the data through a server. If Google's review raises it,
  Google can be left off the broker (sign-in on the device only). For the broker, a Web client
  with `https://oauth.wosa.link/callback.php` as its redirect.
- **Microsoft Entra (Azure):** an app registration for "Accounts in any organizational directory
  and personal Microsoft accounts"; platform Mobile and desktop applications with the
  nativeclient redirect above; public client flows on; delegated permissions IMAP.AccessAsUser.All
  and SMTP.Send, which Lunacy asks for on the `https://outlook.office.com/` resource (Office 365
  Exchange Online: under API permissions, "APIs my organization uses"), and offline_access,
  openid, email. A tenant that doesn't let users consent to an unverified app needs its admin to
  grant consent for exactly those. For the broker, a Web redirect `https://oauth.wosa.link/callback.php`
  and a client secret kept on the broker.
- **The broker:** `apps/google/` and `apps/microsoft/` configs on oauth.wosa.link (the secrets
  stay on the server), and their names in `brokerApp`.

**Exchange ActiveSync to Microsoft 365, on Palm's own mojomail-eas (2026-11-05).** Exchange
Online refuses ActiveSync older than 16.1 since 2026-03-01 (MC1197103) and offers this client
16.1 only. Palm's service negotiates the newest version offered but actually works at 12.1, so
five narrow adapters in the preload's curl glue (`preload.c`, after the Bearer support) carry it
to 16.1, with the binary unchanged:

1. **Version.** The versions each server offers (`MS-ASProtocolVersions`, kept per host) decide:
   a request whose `MS-ASProtocolVersion` the server no longer offers goes at the nearest offered
   version above it (12.1 becomes 16.1 for Exchange Online). FolderSync, Sync and Ping are the same
   on the wire from 12.1 to 16.1. A server still offering 14.1 or lower (Zoho, an Exchange of one's
   own) is shown no newer version and gets Palm's requests unchanged.
2. **Provisioning.** At 12.1 a server asked a device to accept its security policy with HTTP 449;
   from 14.0 it says so in the reply's Status (142, 143, 144). Such a reply is reported to the
   service as 449, and it provisions as it always did.
3. **Device information.** From 14.1 the first Provision request must describe the device
   (`settings:DeviceInformation`), or Exchange Online answers 165 and gives no policy key. Where
   adapter 1 is at work, Palm's exact initial Provision request gets the block: Model and
   FriendlyName "Lunacy", OS "webOS", UserAgent "Palm/1.0.1".
4. **Replies cut to 12.1** (`libpreload/wbxmlfilter.h`, 2026-11-06). A 16.1 server puts code
   pages Palm's parser doesn't know in its replies (Email2's conversation ids, RightsManagement,
   Find: pages 21 to 25), and the service aborts on the first one (`EasTagCode::BuildId` asserts).
   Where adapter 1 is at work, every element in a page above 20 is dropped from a WBXML reply
   with all it contains, page switches kept right, everything else byte for byte, streamed.
5. **Sends in ComposeMail form** (`libpreload/composemail.h`, 2026-11-06). Palm sends mail as
   12.1 does: `Cmd=SendMail` (or `SmartReply`, `SmartForward` with `ItemId` and `CollectionId`)
   and the raw message as `message/rfc822`. From 14.0 a server wants WBXML ComposeMail; Exchange
   Online answers the old form with HTTP 200 and ComposeMail status 101, which the service took
   for sent, and the mail went nowhere. Where the request goes out at 14.0 or later, the body is
   streamed as ClientId, Source (replies and forwards), SaveInSentItems and the message in Mime,
   Palm's own read callback passing the message through unread; Content-Type and length follow.
   A reply with a ComposeMail status other than 1 is reported to the service as HTTP 500, so it
   retries rather than marking the mail sent.
6. **Calendar items at 16.x** (`libpreload/calfilter.h`, 2026-11-08). Palm writes a calendar
   item as 12.1 had it, and Exchange Online refuses it with status 6 ("couldn't be converted").
   In a Sync request that goes out at 16.x (adapter 1 decides), the calendar elements are
   rewritten, the rest byte for byte: `DtStamp`, `MeetingStatus`, `OrganizerName` and
   `OrganizerEmail` go (the server sets them); `UID` becomes `ClientUid` in an Add and goes in a
   Change (the item is the server's by then); the 12.1 `Location` string becomes AirSyncBase's
   `Location` with it as `DisplayName`; a Body's `EstimatedDataSize` goes (the server's to say);
   a `Recurrence` that doesn't name its first day of the week gets `FirstDayOfWeek` 0. Each was
   found on the Fold3 against the test account by one event at a time: plain, with a location,
   with a note, with a reminder, weekly; each refused before its rule and stored after it.

Two more rules, not about the protocol version:

- **The server's back-off** (`libpreload/backoff.h`, 2026-11-08). Exchange Online throttles a
  device partnership and says so in a reply: `X-MS-BackOffDuration` (`L/<ms>`), or `Retry-After`
  with a 503 or 429, with `X-MS-ASThrottle` naming the reason. Palm's service reads none of it,
  and every request in a back-off lengthens it (the Fold3 was locked out for hours on 2026-11-06
  by a crash loop). The preload reads those headers and keeps the host's "not before" time in
  `/var/preferences/lunacy-eas-backoff` in the webOS root, so it outlives the service process,
  which exits when idle and is a new process each time it crashes. Until then a request to that
  host is pointed at a closed port on the device as it starts (`curl_multi_add_handle`) and fails
  at once as a network failure, which the service retries later; the log says
  `lunacy: <host> asks to be left alone for N s` and `a request to <host> waits`. A server that
  sends none of these headers is never held back. A throttle reason with no duration counts as
  15 minutes; any duration is capped at 6 hours.
- **A token that can't be had just now, on the Exchange path** (2026-11-08), as IMAP's
  (`mailauth.h`): when the OAuth service has no token just now (-12), the request goes without
  the handle as a Basic password and fails locally as a network failure, rather than reaching
  the server as a wrong password, after which Palm's service asks the person to sign in again.
  A 401 to a token that should have worked (not fresh, not already a retry, not empty) is
  reported to the service as HTTP 500, which it retries, and that retry asks for a fresh token.
  A 401 to a fresh token, or to the retry, reaches the service as it is.

**A request dump, for development.** With `/var/preferences/lunacy-eas-dump` in the webOS root
holding one host name, the preload appends every request body sent to that host and every reply
body from it to `/tmp/lunacy-eas-dump` (`> <url> <length>` or `< <length>`, then the bytes).
Nothing is written without that file, and only for the host it names, so another account's mail
isn't dumped. Bodies hold mail: use it with a test account, and remove both files after.

Checked on the Galaxy Z Fold3 against `wosatester@jonandnic.onmicrosoft.com`: OAuth sign-in,
validation, provisioning (policy fetched and acknowledged, a policy key), FolderSync (17 folders),
Sync of every collection with advancing sync keys, and Ping. The sign-in page handles a security
policy as Email's wizard does (`mailErrorCode` 2500: `systemmanager/getSecurityPolicy`, then
authlib's `SecurityPolicyGuard`, webOS's "Device Password Upgrade Required"), and Lunacy meets it
with Android's screen lock (luna-deltas A21). Tested with real libcurl on the build machine too.
Two findings: Palm's debug log (`com.palm.eas` at debug) aborts the service while printing a
contacts Sync request against Microsoft 365 (info level is fine), and the crash loop it caused
got the test account throttled (503 with `X-MS-BackOffDuration`). The throttle was per device
partnership (`X-MS-ASThrottle: RecentCommands`, `Retry-After`), not per account: a fresh device id
got in at once. That crash was adapter 4's case, so the debug log was not the only trigger.

**Checked on the Galaxy Tab A7 Lite (2026-11-06)** with adapters 4 and 5: a new account set up
(provisioned under the tenant's relaxed policy, no PIN), mail arrived and opened, calendar and
contact collections synced (the test account has next to nothing in them), and a new message, a
reply (Exchange quoted the original once) and a forward were sent and delivered, one of them to
an outside address. The Microsoft account type stays on IMAP in the repository until the
account type is chosen. Outlook.com: Microsoft refuses the EAS scope for personal
accounts (AADSTS70011) and its IMAP refuses them too (above). Microsoft's token endpoint wants the
scope again on the code exchange (`scopeOnToken`).

## Testing without the real providers

`Workbench/oauth-test/fake-provider.py` stands in for a provider: an OAuth server (authorize
with Allow and Deny, token with PKCE and refresh; `/admin/expire` and `/admin/revoke`), and IMAP
and SMTP fronts that take XOAUTH2 only - refusing LOGIN, and offering SMTP's `AUTH LOGIN
XOAUTH2` as Microsoft does - in front of the dummy GreenMail (`Workbench/dummy-mail.sh`). The
broker runs from a scratch copy of oauth-broker-for-webos in Docker (`php:8.3-cli`, PHP's own
server with a small router for the pretty URLs), with an app `fake` pointing at the fake
provider. A development build is pointed at both by `files/oauth-providers.json`:

```json
{ "broker": "http://<host>:8092", "providers": { "google": { "title": "Google (test)",
  "templateId": "org.webosarchive.lunacy.google", "clientId": "lunacy-test",
  "authorizeUrl": "http://<host>:8091/authorize", "tokenUrl": "http://<host>:8091/token",
  "scope": "mail openid email", "onDevice": "page", "redirectUri": "http://localhost/lunacy-oauth",
  "brokerApp": "fake", "mail": { "server": "<host>", "port": 3145, "encryption": "none",
  "smtpConfig": { "server": "<host>", "port": 3027, "encryption": "none", "useSmtpAuth": true } } } } }
```

`LunaRuntimes/pdk/tests/mailauth_test.c` tests the translation on the build machine (it runs
in every PDK build): the logins, the hidden challenges, the EHLO change and AUTH LOGIN replay,
the stale-token retry, the dropped first refusal and the failed write when no token can be had, and a message body cut every which way going through byte for byte.
`wbxmlfilter_test.c` and `composemail_test.c` do the same for adapters 4 and 5: replies with the
newer pages fed whole and in pieces, and the ComposeMail bytes Exchange Online accepted. `calfilter_test.c` runs adapter 6 on Palm's own refused calendar Add and on a series' exceptions
(as their own Change and Delete, prefixes of real requests also run under AddressSanitizer), and
`backoff_test.c` reads Exchange Online's throttle replies and keeps the store.

**Checked on the Galaxy Tab A7 Lite (2026-11-05, arm32 debug), against the fake provider:**
Email's welcome lists Google and Microsoft; sign-in in the page's WebView, Allow, validation
through mojomail-imap, Create Account; mail synced over XOAUTH2 and a message sent over SMTP
XOAUTH2 (in the mailbox); a token refused early refreshed through the broker on the next login;
a revoked sign-in showed Email's "Check your login and password", and Sign In Again from the
Accounts app (once on the page, once through the broker with the code entered on another
machine) brought the account back at once. The A7 has no web browser, so the Android-browser
path (Google's) is not yet tried on a device: it needs one with a browser.

**Checked on the Galaxy Z Fold3 (2026-11-07, Android 15, arm64 debug, Firefox), real Gmail:**
- Before: Android blocked Lunacy coming forward from behind Firefox (`BAL_BLOCK`); the
  person came back through the task switcher.
- Google's page now opens as a Firefox Custom Tab in Lunacy's task. When Google hands back,
  Android lets Lunacy forward (`BAL_ALLOW_FOREGROUND`), which closes the tab: back in Accounts
  at once (twice).
- Sign In Again: the OAuth service's own `credentialsChanged` reached mojomail-imap
  (`returnValue: true`) 0.6 s before the page's modifyAccount.
- An expired token whose refresh failed (the record pointed at the broker, which has no
  Google app): no error on the account, no notification; mojomail-imap tried again in 30 s,
  and once the refresh could reach Google, it refreshed and went back to push.
- A token Gmail refused before its time: the connection dropped, the retry 0.4 s later asked
  for a fresh token (`stale`), refreshed, push.
- After a force-stop in the middle of those failures, mojomail-imap tried Gmail again only
  when Email was opened (its syncFolder). Without the force-stop it retried by itself.

## Open

- **Exchange and a token that can't be had just now:** handled since 2026-11-08 (above), but
  the failure paths haven't been made to happen on a device yet.


- **Microsoft over Exchange** (in full: [outlook-eas.md](outlook-eas.md)): works on the Fold3
  (2026-11-08): mail, push, sending, contacts and calendar both ways (changes made in Outlook
  reach the device by Ping). One "Microsoft Outlook" entry since 2026-11-08: a personal account, whose
  Exchange scope Microsoft refuses (AADSTS70011, at the token request), is signed in again for
  IMAP in the same sign-in (a provider's `fallback` and `fallbackParams`) and gets mail only.
  Still to see: recurring events with exceptions, invitations, a personal account actually made
  (Outlook.com's IMAP bug, above).
- **Gmail's verification** for public use: Google's restricted-scope review (started
  2026-11-05). Whether it also asks for a security assessment turns on servers: none in the
  on-device path; decide whether Google goes through the broker at all.
- **The broker's apps** on oauth.wosa.link for Google and Microsoft (a Web client and secret each).
- **Email's own "Change login settings"** opens Palm's password form for these accounts too,
  which can't sign them in. The Accounts app's page can. Email checking the template's
  `customUI` there would be a change to Email (rule 5's exceptions are codepoet's).
- The browser path's look: Android's browser appearing mid-sign-in is a visible difference
  (luna-deltas B23).
- Yahoo (app passwords already work with plain IMAP).
- webOS CE: the translation is a few hundred lines of C with no Lunacy dependency but its token
  source; CE could carry it in its mail shim with the token from a service of its own, and use
  the same sign-in page and templates with the broker.
