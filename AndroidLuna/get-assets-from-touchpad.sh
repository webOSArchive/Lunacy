#!/bin/sh
# Gets what fetch-assets.sh needs to make Luna DE's assets, for anyone who hasn't Lunacy's own
# Workbench/vendor: the webOS files from a TouchPad on the cable, and the webOS CE project's
# from its public repository. The webOS frameworks and system files aren't Lunacy's to put in
# its repository, so each builder takes them from a device they own.
#
#   AndroidLuna/get-assets-from-touchpad.sh [-d <novacom device id>] [--force]
#   AndroidLuna/fetch-assets.sh --desktop
#   (cd DesktopLuna && npm install && npm run dist)
#
# The TouchPad must run webOS CE 3.1.0 (https://github.com/webOSArchive/webOS-Community-Edition),
# which is what Lunacy's patches are diffs against; it is read, never written, but for one
# archive in /media/internal that is removed again. Needs novacom (the webOS SDK's, or
# webos-sdk-redux's), git and tar. Luna DE's assets only: the Android build's native parts (the
# mail and messaging engines, Node for Android, busybox, the PDK runtime) come from more than a
# device holds, and stay with the development machine.
#
# What is taken, as Workbench/vendor/touchpad/ names it (fetch-assets.sh says what each is for):
#   /usr/palm/frameworks       Enyo (enyo/0.10), Mojo, mojocommon and Palm's JS frameworks
#   /usr/lib/luna/luna-media-shim   the "media" framework, which /usr/palm/frameworks links to
#   /usr/palm/services         jsservicelauncher and the system's JS services Lunacy runs
#   /usr/palm/public/accounts  the IMAP, "Email Account" and Exchange account templates
#   /etc/palm                  db8 kinds and permissions, activities, the file cache's types
#   /usr/share/dbus-1/system-services   the native services' names and command lines
#   /usr/palm/ext-timezones.json, /usr/palm/command-resource-handlers.json
# and from the webOS CE repository, at the commits Lunacy was last checked against: its contacts
# and calendar frameworks, accounts library, account template and photo edit service.
set -e
cd "$(dirname "$0")"
V=${LUNACY_VENDOR:-../Workbench/vendor}
DEVICE=; FORCE=
while [ $# -gt 0 ]; do
    case $1 in
        -d) DEVICE=$2; shift ;;
        --force) FORCE=1 ;;
        *) echo "usage: get-assets-from-touchpad.sh [-d <novacom device id>] [--force]" >&2; exit 2 ;;
    esac
    shift
done
fail() { echo "get-assets-from-touchpad: $*" >&2; exit 1; }
command -v novacom >/dev/null || fail "no novacom on the PATH (the webOS SDK, or https://github.com/webOSArchive/webos-sdk-redux)"
command -v git >/dev/null || fail "no git on the PATH"
[ -e "$V/touchpad" ] && [ -z "$FORCE" ] && fail "$V/touchpad is already there; --force replaces what this script takes (not the rest of it)"

# novacomd drops the odd connection, more so just after a device appears: each step is tried a few times.
nova() { if [ -n "$DEVICE" ]; then novacom -d "$DEVICE" "$@"; else novacom "$@"; fi; }
try() { n=0; until "$@"; do n=$((n + 1)); [ $n -lt 5 ] || return 1; sleep 6; done; }

info=$(try nova run file:///bin/cat /etc/palm-build-info 2>/dev/null) || fail "no TouchPad answers over novacom (novacom -l lists devices; -d <id> picks one)"
echo "$info" | grep -q '^BUILDNAME=Nova-HP-Topaz' || fail "the device isn't a TouchPad: $(echo "$info" | grep BUILDNAME)"
echo "$info" | grep -q '^PRODUCT_VERSION_STRING=webOS CE 3\.1\.0' ||
    fail "the TouchPad runs '$(echo "$info" | sed -n 's/^PRODUCT_VERSION_STRING=//p')', not webOS CE 3.1.0, which Lunacy's patches are written against"
echo "get-assets-from-touchpad: $(echo "$info" | sed -n 's/^PRODUCT_VERSION_STRING=//p'), build $(echo "$info" | sed -n 's/^BUILDMARK=//p')"

W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT
REMOTE=/media/internal/.luna-de-assets.tgz
packed() {
    nova run file:///bin/sh <<X | grep -q '^packed$'
cd / && tar czf $REMOTE usr/palm/frameworks usr/lib/luna/luna-media-shim usr/palm/services usr/palm/public/accounts etc/palm usr/share/dbus-1/system-services usr/palm/ext-timezones.json usr/palm/command-resource-handlers.json && echo packed
X
}
fetched() { nova get file://$REMOTE > "$W/device.tgz" && tar -tzf "$W/device.tgz" >/dev/null 2>&1; }
removed() { echo "rm -f $REMOTE" | nova run file:///bin/sh; }
echo "get-assets-from-touchpad: packing on the device"
try packed || fail "couldn't pack the files on the device"
echo "get-assets-from-touchpad: fetching"
try fetched || { try removed || true; fail "couldn't fetch the archive from the device"; }
try removed || echo "get-assets-from-touchpad: couldn't remove $REMOTE from the device; delete it by hand" >&2
mkdir "$W/tree" && tar -xzf "$W/device.tgz" -C "$W/tree"
D=$W/tree

# Laid out as fetch-assets.sh reads it. take <path under vendor/touchpad> <path on the device>
TP=$V/touchpad
take() {
    [ -e "$D/$2" ] || [ -L "$D/$2" ] || fail "the device has no /$2"
    rm -rf "$TP/$1"; mkdir -p "$(dirname "$TP/$1")"
    cp -R "$D/$2" "$TP/$1"
}
F=usr/palm/frameworks
take enyo-0.10 $F/enyo/0.10
for f in mojo mojocommon mojo2 prototype mojo.core underscore foundations globalization mojoloader.js \
         metascene.base metascene.videos metascene.videos.share mediastream mediaextension mediacapture \
         imagethumbnail mojodbshim contacts caja; do take $f $F/$f; done
take media usr/lib/luna/luna-media-shim
for f in foundations.crypto foundations.io foundations.json foundations.xml mojoservice mojoservice.transport \
         mojoservice.transport.sync calendar calendar.io; do take services-fw/$f $F/$f; done
take services-fw/jsservicelauncher usr/palm/services/jsservicelauncher
for s in com.palm.service.accounts com.palm.service.palmprofile com.palm.service.calendar.reminders \
         com.palm.service.calendar.meetingupdate com.palm.service.contacts com.palm.service.contacts.linker \
         com.palm.service.photos; do take os-services/$s usr/palm/services/$s; done
take etc-db/db etc/palm/db
take etc-db/tempdb etc/palm/tempdb
take etc-db/db_kinds etc/palm/db_kinds
take etc-activities etc/palm/activities
take tz/ext-timezones.json usr/palm/ext-timezones.json
take command-resource-handlers.json usr/palm/command-resource-handlers.json
# The mail services' side that isn't a program: their kinds, templates, the file cache's
# configuration and the names they are started by.
take email/etc/palm/db_kinds etc/palm/db_kinds
take email/etc/palm/FileCache.conf etc/palm/FileCache.conf
take email/etc/palm/filecache_types etc/palm/filecache_types
for a in com.palm.imap com.palm.othermail; do take email/usr/palm/public/accounts/$a usr/palm/public/accounts/$a; done
for s in com.palm.imap com.palm.smtp com.palm.filecache; do take email/usr/share/dbus-1/system-services/$s.service usr/share/dbus-1/system-services/$s.service; done
for k in com.palm.calendar.eas com.palm.calendarevent.eas com.palm.contact.eas com.palm.contactset.eas com.palm.eas.account \
         com.palm.email.eas com.palm.folder.eas com.palm.securitypolicy.eas com.palm.task.eas com.palm.tasklist.eas; do
    take eas/etc/palm/db/kinds/$k etc/palm/db/kinds/$k
done
take eas/etc/palm/db/permissions/com.palm.securitypolicy.eas etc/palm/db/permissions/com.palm.securitypolicy.eas
for k in com.palm.securitypolicy com.palm.securitypolicy.device; do take eas/etc/palm/db_kinds/$k etc/palm/db_kinds/$k; done
take eas/usr/palm/public/accounts/com.palm.eas usr/palm/public/accounts/com.palm.eas
take eas/usr/share/dbus-1/system-services/com.palm.eas.service usr/share/dbus-1/system-services/com.palm.eas.service
# Messaging's side that isn't a program: CE's chat threader, its frameworks, kinds and activities.
for k in com.palm.app.messagingprefs com.palm.chatthread com.palm.config.libpurple com.palm.contact.libpurple com.palm.imchannel \
         com.palm.imcommand com.palm.imcommand.libpurple com.palm.imgroupchat com.palm.iminvitation com.palm.imloginstate \
         com.palm.imloginstate.libpurple com.palm.immessage com.palm.immessage.libpurple com.palm.imretaineddata com.palm.imserver \
         com.palm.message; do
    take messaging/etc/palm/db/kinds/$k etc/palm/db/kinds/$k
    [ $k = com.palm.iminvitation ] || take messaging/etc/palm/db/permissions/$k etc/palm/db/permissions/$k
done
for k in com.palm.imbuddystatus com.palm.imbuddystatus.libpurple; do take messaging/etc/palm/tempdb/kinds/$k etc/palm/tempdb/kinds/$k; done
take messaging/etc/palm/tempdb/permissions/com.palm.imbuddystatus etc/palm/tempdb/permissions/com.palm.imbuddystatus
for a in com.palm.imlibpurple com.palm.messaging.chatthreader; do take messaging/etc/palm/activities/$a etc/palm/activities/$a; done
for p in com.palm.app.messaging.json com.palm.app.phone.json com.palm.smartKey.json; do take messaging/etc/palm/contact_linker_plugins/$p etc/palm/contact_linker_plugins/$p; done
take messaging/usr/palm/services/com.palm.messaging.chatthreader usr/palm/services/com.palm.messaging.chatthreader
for f in messaging.library contacts.plugin.messaging; do take messaging/usr/palm/frameworks/$f $F/$f; done
take messaging/usr/share/dbus-1/system-services/com.palm.messaging.chatthreader.service usr/share/dbus-1/system-services/com.palm.messaging.chatthreader.service
echo "From a TouchPad over novacom, by AndroidLuna/get-assets-from-touchpad.sh: $(echo "$info" | sed -n 's/^PRODUCT_VERSION_STRING=//p'), build $(echo "$info" | sed -n 's/^BUILDMARK=//p'), $(date -u +%Y-%m-%d)." > "$TP/FROM-DEVICE"
echo "get-assets-from-touchpad: $TP ($(du -sh "$TP" | cut -f1))"

# The webOS CE project's own, newer than any flashed image: each path at the commit Lunacy's
# patches and checks were made against (Workbench/vendor/webos-ce/COMMIT on the development
# machine). Move a pin to take a newer one.
CE_REPO=https://github.com/webOSArchive/webOS-Community-Edition.git
CE=$V/webos-ce
if [ -e "$CE" ] && [ -z "$FORCE" ]; then
    echo "get-assets-from-touchpad: $CE is already there; left as it is"
else
    G=$V/webos-ce.git
    [ -d "$G" ] || git clone -q --bare --filter=blob:none "$CE_REPO" "$G" || fail "can't clone $CE_REPO"
    rm -rf "$CE"; mkdir -p "$CE"; : > "$CE/COMMIT"
    ce() {  # ce <commit> <path>...
        c=$1; shift
        git -C "$G" cat-file -e "$c^{commit}" 2>/dev/null || git -C "$G" fetch -q origin || fail "can't fetch $CE_REPO"
        git -C "$G" archive "$c" "$@" | tar -x -C "$CE" || fail "webOS CE commit $c hasn't $*"
        for p in "$@"; do echo "$p: $c (webOS-Community-Edition), fetched $(date -u +%Y-%m-%d)" >> "$CE/COMMIT"; done
    }
    ce 67ad3d8 system/usr/palm/frameworks/contacts system/usr/palm/frameworks/enyo/0.10/framework/lib/accounts \
        system/usr/palm/frameworks/mojo/builtins/palmcontactsVersion1_0.js system/usr/palm/public/accounts/com.palm.palmprofile
    ce 8284a49 system/usr/palm/frameworks/calendar
    ce 7bd5641 apps/org.webosarchive.photos.service
    echo "get-assets-from-touchpad: $CE ($(du -sh "$CE" | cut -f1))"
fi
echo "get-assets-from-touchpad: done. Next: AndroidLuna/fetch-assets.sh --desktop"
