# Lunacy's F-Droid repository

Lunacy's own F-Droid repository, hosted by the webOS Archive beside its Preware feeds:
**https://stacks.webosarchive.org/feeds/fdroid/repo**. Users add it in the F-Droid client and
get each release from it. It carries the release APKs (both ABIs, the client picks the one the device
runs) and LunaKeyboard, signed with the same key as the APKs on GitHub, so a copy installed
from GitHub updates from here and the other way round.

F-Droid's own repository can't take Lunacy: it ships Palm's and HP's frameworks, apps and
native libraries, which were never released under an open licence, and its build needs the
reference TouchPad's files, which aren't public. A repository of our own has no such rules.

## What is here

- `metadata/<application id>.yml`: each app's name, summary, description and links, in
  F-Droid's metadata format.
- `update-repo.sh`: puts out/'s release APKs in the repository and re-signs its index.

## A release

1. `./build.sh publish` ([BUILDING.md](../../BUILDING.md)).
2. `Meta/fdroid/update-repo.sh`.
3. Upload the workspace's `repo/` and `archive/` to `https://stacks.webosarchive.org/feeds/fdroid/`, as
   they are (rsync with `--delete` is fine; nothing else lives there).

## The workspace

`~/Projects/LunacyFDroid` on codepoet's machine, outside the repository: `config.yml` (the
repository's name, URL, and the keystore's passwords), `keystore.p12` (the repository's
signing key) and the repository itself. Neither secret is committed. **Back up the keystore
and config.yml**: the key is the repository's identity, and losing it means every user has to
remove the repository and add it again. Its fingerprint (SHA-256), which users can check
when they add the repository:

    0D57CFCD5E6B0B366C108ECEAF0286CFC01B89B977C7E580E718FA05E800F3C5

It was made with `fdroid init --repo-keyalias webosarchive`, and then these were added to
config.yml: `repo_url`, `repo_name` ("webOS Archive"), `repo_description`, `repo_icon`
(`icon.png` in the workspace, which update-repo.sh copies from Meta/LunacyRobot.png) and
`archive_older: 3`.

## fdroidserver

fdroidserver 2.4.5 with androguard 4.1.4, in a virtualenv at `~/.local/share/fdroidserver`
that also sees the system's packages. Ubuntu's own fdroidserver (2.2.1, with androguard 3.4)
can't read APKs from AGP 8 ("res1 must be zero!"), but it is installed for the dependencies.
An unpinned `pip install fdroidserver` ran away with the machine's CPU and memory for an hour
(2026-11-13), so the venv takes exact versions with no dependency resolution:

    sudo apt install fdroidserver python3.12-venv
    python3 -m venv --system-site-packages ~/.local/share/fdroidserver
    P=~/.local/share/fdroidserver/bin/pip
    $P install --no-deps --only-binary :all: androguard==4.1.4
    $P install --no-deps fdroidserver==2.4.5 apkInspector loguru mutf8 platformdirs \
        puremagic python-vagrant sdkmanager dataset

update-repo.sh runs `fdroid update` under a 6 GB memory cap and a 15-minute timeout; it
takes a few seconds.
