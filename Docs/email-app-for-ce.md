# Email: bugs in the app itself, for the webOS CE project

Bugs found in Palm's Email (`com.palm.app.email` 3.0.13600) while running it in Lunacy whose
cause is in the app, not in the platform under it. Lunacy fixes them in its bundled copy
(`CoreApps/com.palm.app.email`, codepoet's rule 5 exception, each change marked `[Lunacy]`);
webOS CE ships the same app and can take the same change. Bugs that turned out to be Lunacy's
are in [fix-log.md](fix-log.md); the mail services' (mojomail-eas) are in
[eas.md](eas.md), "For the webOS CE project".

| Date | Symptom | Cause | Lunacy's change |
|---|---|---|---|
| 2026-11-14 | Composing on a screen wider than a TouchPad's, the wallpaper beside the sheet has an undarkened strip at each edge, and the wallpaper repeats there | `DesktopBackground` draws the wallpaper at its own size (repeated) and shades 128 px each side of the 768 px sheet (`compose-background-shadow.png`, a black gradient, alpha 51 at the top to 190 at the bottom): exactly a 1024 px screen | `css/compose.css`: the same gradient carries on past the shade to any edge (`:before`/`:after`); `compose/source/Compose.js`: a wallpaper that fills a TouchPad screen is drawn `background-size: cover`, as the system draws it. Unchanged at 1024 px |
