# Lunacy: working notes for agents

Lunacy runs legacy webOS apps on Android. It combines a simulated Luna shell, modernized
Enyo/Mojo frameworks and a simulated Luna service bus. Read [README.md](README.md) and
[Docs/architecture.md](Docs/architecture.md) first.

## People

- The maintainer is **codepoet** ([codepoet80](https://github.com/codepoet80)). Use that name
  only, in code, docs, commits and conversation.
- codepoet is the domain expert on legacy webOS; defer to them on how webOS behaved.

## Rules

1. **No per-app hacks.** Fix the modernized framework or the global compat layer. If an app
   seems to need a special case, find the general fix. The only allowed per-app setting is the
   fixed-viewport fallback.
2. **The bus tells the truth.** Unimplemented services return real errors; never stub a
   success. Subscriptions must deliver updates.
3. **Don't force a viewport.** Enyo layouts are responsive; render at the real device width.
4. **Every change is traceable.** Log each change to the Enyo/Mojo forks against their
   upstream, and log where every compatibility fix landed (framework, compat layer, or
   nowhere general).
5. **Never edit an app.** Installed apps stay exactly as packaged, and nothing may target one
   app. Global serve-time transforms (one mechanical rule applied to every app's files as
   they are served) are allowed, and belong to the compat layer.
6. **Links in docs:** GitHub URLs or relative links only, never local filesystem paths.
7. **Third-party assets carry a NOTICE.** LunaCE images are Apache 2.0; the Prelude fonts,
   TouchPad wallpapers and Mojo ship as abandonware. Anything new copied from a device or
   another project gets a NOTICE with its source and status.
8. **`Docs/motivation.md` is personal and gitignored.** Don't copy its content into committed
   files.
9. **A card is a window, not an app.** Apps own several windows and a lifecycle; see "App
   lifecycle" in the architecture doc.
10. **Caller identity comes from the origin**, never from what the page claims.
11. Ask before any outward-facing action (pushes, PRs, issues, App Museum writes).

## Layout

One Gradle root builds two APKs. `AndroidLuna/` is the shell; `LunaKeyboard/` is the optional
companion keyboard and depends on nothing in Lunacy. `LunaRuntimes/enyo-1.0/` and
`LunaRuntimes/mojo/` hold Lunacy's changes to the frameworks apps load from the OS, as patch
series applied to a stock tree by `AndroidLuna/fetch-assets.sh`. See
[BUILDING.md](BUILDING.md); `Docs/dev-workflow.md` has the folder-by-folder table.

## Platform

The first target is Android 5.0.1 (API 21) on an HP 10 G2 tablet: 1 GB of RAM, 32-bit ARM,
factory WebView Chromium 37.
- Use Kotlin with Android Views, not Compose. Pin AndroidX to releases that support API 21.
- Lunacy's own JS (the bridge, shims and framework changes) must run on Chromium 37.
- Android 5's permissiveness is a head start. Every shortcut it allows is listed as a
  ratchet item in the architecture doc, to be tightened on later targets. Don't add new ones
  without listing them.
- Test on the device over `adb`, and inspect cards through `chrome://inspect`.
- Record every change made to a test device (settings, installed packages, WebView
  versions) in [Docs/android5-setup.md](Docs/android5-setup.md).
- A real TouchPad (webOS CE 3.1.0) is the reference for how webOS behaved. Reach it over
  `novacom`; `Workbench/probe` is a probe app for measuring the contract.

## Resuming work

Start with "Where things stand" in [Docs/roadmap.md](Docs/roadmap.md), then
[Docs/dev-workflow.md](Docs/dev-workflow.md) for devices and tools. Check every visual change
against the reference TouchPad at the same scale before calling it done.

## Priority

Enyo 1 → Mojo → JS services → PDK native. The shell's look and feel is a requirement, not
polish.
