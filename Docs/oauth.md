# OAuth for mail accounts

Google and Microsoft no longer take a password from a mail client: they want OAuth 2, and
an XOAUTH2 login over IMAP and SMTP. Palm's mail services know only passwords. This is how
Lunacy signs those accounts in while running Palm's binaries exactly as the TouchPad ships
them. The plan was made on 2026-10-10 (two ways to sign in: on the device, and through
webOSArchive's broker for devices too old for the providers' pages); the work started
2026-11-05, for 0.9.5.

## How it fits together

```
 Email / Accounts ──Add Account──▶ "Google" / "Microsoft" template (validator.customUI)
                                     │
                                     ▼
      org.webosarchive.lunacy.signin (the sign-in page, in a cross-app frame)
        │ signIn ─────────▶ palm://org.webosarchive.lunacy.oauth   (card/OAuth.kt)
        │                    ├ on the device: the provider's page in the frame's WebView
        │                    │   (its redirect caught with webOS's `redirects`), or Android's
        │                    │   browser with a loopback listener (a provider that refuses
        │                    │   web views: Google)
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
    hidden, so the service sees only the tagged NO it expects, and reports a wrong password.
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
- **The token** comes over the process's own luna-send link (`LUNACY_BUS` and the caller
  token Lunacy gave the process), so `oauth/token` is reachable on the private bus only, and
  only from processes Lunacy started. It refreshes a token with under five minutes left.
- **The sign-in page** asks the mail service to validate the account with the handle, as
  Email's wizard validates a password, so a new account is checked against the real server
  before it is made. Then Accounts' own Account Settings page and Create Account follow, as for
  any account.
- **Signing in again.** When a refresh is refused (the person revoked access), logins fail as a
  wrong password and Email shows its "Check your login and password". The account's page in
  the Accounts app (Change Login Settings) offers Sign In Again: the same account signed in
  again keeps its handle, and the page saves the credentials (`modifyAccount`) so the mail
  service hears `credentialsChanged` and logs in at once. Email's own Change login settings
  opens Palm's password form instead (open question below).
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
| On the device | Android's browser and a loopback redirect (`http://127.0.0.1:<port>/`): Google refuses sign-in in a WebView (`disallowed_useragent`). Offered only where a browser is installed | The sign-in page's WebView; redirect `https://login.microsoftonline.com/common/oauth2/nativeclient` |
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
  verification and a CASA security assessment. For the broker, a Web client with
  `https://oauth.wosa.link/callback.php` as its redirect.
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
three narrow adapters in the preload's curl glue (`preload.c`, after the Bearer support) carry it
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

Checked on the Galaxy Z Fold3 against `wosatester@jonandnic.onmicrosoft.com`: OAuth sign-in,
validation, provisioning (policy fetched and acknowledged, a policy key), FolderSync (17 folders),
Sync of every collection with advancing sync keys, and Ping. The sign-in page handles a security
policy as Email's wizard does (`mailErrorCode` 2500: `systemmanager/getSecurityPolicy`, then
authlib's `SecurityPolicyGuard`, webOS's "Device Password Upgrade Required"), and Lunacy meets it
with Android's screen lock (luna-deltas A21). Tested with real libcurl on the build machine too.
Two findings: Palm's debug log (`com.palm.eas` at debug) aborts the service while printing a
contacts Sync request against Microsoft 365 (info level is fine), and the crash loop it caused
got the test account throttled (503 with `X-MS-BackOffDuration`). **Open:** mail actually arriving
in Email from Microsoft 365, contacts and calendar items, sending (16.x dropped the raw-MIME
SendMail that 12.1 clients use), once the throttle lifts. The Microsoft account type stays on
IMAP in the repository until then. Outlook.com: Microsoft refuses the EAS scope for personal
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
the stale-token retry, and a message body cut every which way going through byte for byte.

**Checked on the Galaxy Tab A7 Lite (2026-11-05, arm32 debug), against the fake provider:**
Email's welcome lists Google and Microsoft; sign-in in the page's WebView, Allow, validation
through mojomail-imap, Create Account; mail synced over XOAUTH2 and a message sent over SMTP
XOAUTH2 (in the mailbox); a token refused early refreshed through the broker on the next login;
a revoked sign-in showed Email's "Check your login and password", and Sign In Again from the
Accounts app (once on the page, once through the broker with the code entered on another
machine) brought the account back at once. The A7 has no web browser, so the Android-browser
path (Google's) is not yet tried on a device: it needs one with a browser.

## Open


- Client registrations (above), then the real providers: Gmail, Outlook.com, Microsoft 365.
- **Email's own "Change login settings"** opens Palm's password form for these accounts too,
  which can't sign them in. The Accounts app's page can. Email checking the template's
  `customUI` there would be a change to Email (rule 5's exceptions are codepoet's).
- The browser path's look: Android's browser appearing mid-sign-in is a visible difference
  (luna-deltas B23).
- EAS with OAuth (above). Yahoo (app passwords already work with plain IMAP).
- webOS CE: the translation is a few hundred lines of C with no Lunacy dependency but its token
  source; CE could carry it in its mail shim with the token from a service of its own, and use
  the same sign-in page and templates with the broker.
