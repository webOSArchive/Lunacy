# Lunacy's changes to the photos service

Upstream: Palm's photos service, `com.palm.service.photos`, as it is in `/usr/palm/services` on the
reference TouchPad (webOS CE 3.1.0), copied into `Workbench/vendor/touchpad/os-services/`. It makes
Photos & Videos' thumbnails and the copies its full view shows. Abandonware, like Mojo.

`fetch-assets.sh` copies it into the webOS root and applies these patches in order, failing the
build if one no longer applies.

**Adding a patch:** change `AndroidLuna/local-assets/rootfs/usr/palm/services/com.palm.service.photos/`,
then, from `AndroidLuna/`, `tools/make-patch.sh photos-service 000N-name <file>...` diffs it against
the tree before it. Each change is marked `[Lunacy]` in the source.

## 0001: the screennail is the screen's size

Photos & Videos' full view always shows a picture's screennail (`appScreenNail`), which the service
made by fitting the picture into a 1024 x 768 box: the TouchPad's screen, in its pixels. On a
phone held upright, with about three times the TouchPad's pixel density, that box made a
fraction of the screen's pixels, and Photos magnified it: a 1080 x 1920 screenshot's screennail
was 432 x 768, shown 1080 px wide, which users reported as blurry (2026-11-07, measured on the
Kyocera). Lunacy gives JS services the screen's longer side in pixels (`LUNACY_SCREEN_PX`,
`JsServices.kt`), and the box is that size both ways; a screen no bigger than 1024 keeps the
TouchPad's box. The grid thumbnails and the full-size copy are unchanged.

Screennails made at another size are cleared once (the file deleted, `appScreenNail` nulled),
so Photos asks for each again as it is shown (`PhotoCache.kt`); again whenever the screen's size
changes.
