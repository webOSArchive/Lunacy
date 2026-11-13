# Lunacy in F-Droid

Lunacy and LunaKeyboard are published in the webOS Archive's own F-Droid repository,
**https://stacks.webosarchive.org/feeds/fdroid/repo**, beside its Preware feeds. Users add it
in the F-Droid client and get each release from it: the release APKs (both ABIs; the client
picks the one the device runs) and LunaKeyboard, signed with the same key as the APKs on
GitHub, so a copy from either updates from the other.

F-Droid's own repository can't take Lunacy: it ships Palm's and HP's frameworks, apps and
native libraries, which were never released under an open licence, and its build needs the
reference TouchPad's files, which aren't public. A repository of our own has no such rules.

The repository is a project of its own,
[webOSArchive/fdroid-repo](https://github.com/webOSArchive/fdroid-repo), since it carries other
Android apps too; update-repo.sh expects its clone at `~/Projects/webosarchive-fdroid`. It holds the signing key, every app's
description (Lunacy's are its `metadata/org.webosarchive.lunacy.yml` and
`metadata/org.webosarchive.keyboard.yml`), and its own README on how it was set up.

## A release

1. `./build.sh publish` ([BUILDING.md](../../BUILDING.md)).
2. `Meta/fdroid/update-repo.sh`: checks out/'s release APKs against SHA256SUMS and hands
   them to the repository's `add-apks.sh`, which adds them and re-signs the index.
3. Upload the repository's `repo/` and `archive/` to `feeds/fdroid/` on
   stacks.webosarchive.org (codepoet, by hand).
