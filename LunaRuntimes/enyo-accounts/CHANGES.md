# Lunacy's changes to webOS CE's accounts library

Lunacy's Enyo takes `framework/lib/accounts` from the webOS CE project (the community
enyo-accounts 1.1.1.1, `Workbench/vendor/webos-ce`), not from the TouchPad, and
`AndroidLuna/fetch-assets.sh` applies the patches here to it after copying it in. They are kept
apart from [the Enyo patches](../enyo-1.0/CHANGES.md) because the SDK
([webos-sdk-redux](https://github.com/webOSArchive/webos-sdk-redux)) builds its Enyo from the
TouchPad's tree, where CE's Add Account list doesn't exist. The library is the webOS CE
project's, so a change here is one to offer back to it.

### 0001-template-subtitle.patch

An account template may give the line under its name in Add Account (`loc_subtitle`).

CE's Add Account list writes each type's capabilities under its name ("Email · Contacts ·
Calendar"). Lunacy's single Microsoft type has Exchange's capabilities, but a personal
Microsoft account falls back to IMAP and gets mail only ([outlook-eas.md](../../Docs/outlook-eas.md)),
so the capabilities promise too much. With `loc_subtitle` the template says it itself:
"Email (Calendar and Contacts, if available)" (codepoet, 2026-11-08). A template without it is
listed as before, and the search box searches whichever line is shown.
