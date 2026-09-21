# Luna shell implementation reference (LunaCE, TouchPad / tablet UI)

Purpose: everything an Android developer needs to re-create the webOS 3.x TouchPad system
shell ("Luna", as shipped by LunaCE) natively, without reading the C++.

Source analysed: `webOSArchive/LunaCE` @ `master` (snapshot commit `b412b5b`). Every fact is
linked to the line it came from. Links have the form
`https://github.com/webOSArchive/LunaCE/blob/master/<path>#L<line>`.

## How to read this document

* **[read]** — the value or behaviour is literally in the code or config at the cited line.
* **[inferred]** — derived by me from the cited code (arithmetic, combining two places,
  reading control flow). The derivation is shown. Treat these as "very likely", not gospel.
* **[LunaCE]** — added by the LunaCE community lineage (Herrie's 4.x/5.0.0 work or this
  tree's own launcher work); **[stock]** — present in HP's original webOS 3.0.5 LunaSysMgr.
  How I decided: (1) the LunaCE README credits specific features to LunaCE
  ([README.md#L22-L25](https://github.com/webOSArchive/LunaCE/blob/master/README.md#L22),
  [README.md#L40-L57](https://github.com/webOSArchive/LunaCE/blob/master/README.md#L40));
  (2) I ran `strings` on HP's own 3.0.5 binary that ships in the repo
  ([bin/LunaSysMgr-Stock-topaz](https://github.com/webOSArchive/LunaCE/blob/master/bin/LunaSysMgr-Stock-topaz))
  and checked for the preference keys / symbols (e.g. `sysUiEnableMiniCards`,
  `tabbedCardsEnabled`, `sysUiGestureDetection` are absent from the stock binary;
  `sysUiNoHomeButtonMode`, `sysUiEnableNextPrevGestures`, `CardDimmPercentage`,
  `card-shadow-tile`, `Just type` are present); (3) diffed against the open-webOS
  `luna-sysmgr` tree (HP's 2012 source release, later edited by LG). Where only (3) was
  available the tag says so.
* **Config files: stock and CE are the same file.** Settled on hardware on 2026-09-21 by
  diffing a bone-stock TouchPad (HP webOS 3.0.5, `Nova-HP-Topaz` build 86) against the
  reference device (webOS CE 3.1.0). `luna.conf`, `luna-platform.conf`, `lunaAnimations.conf`,
  `notificationPolicy.conf`, `persistentWindows.conf`, `defaultPreferences-platform.txt` and
  `default-launcher-page-layout.json` are **byte-identical**. So every geometry, ratio and
  easing value this document takes from the reference device's `/etc/palm` is HP's own, not a
  LunaCE change, and needs no `[LunaCE]`/`[stock]` judgement.

  CE's only config change is three added lines in `defaultPreferences.txt`:
  `x_palm_virtualkeyboard_settings` (`{"keyboard size": -1}`),
  `sysUiUseCustomCarrierString` (`true`) and `sysUiCarrierString` (`"webOS CE"`) — which is the
  mechanism Lunacy already uses for its own "Lunacy" carrier text. The tagging above still
  matters for behaviour compiled into the binary; it no longer matters for the config.

* Qt `QEasingCurve::Type` values appear as integers in the animation config. Mapping
  (Qt 4.8 enum order — **external**, from the Qt docs, not LunaCE:
  <https://doc.qt.io/archives/qt-4.8/qeasingcurve.html#Type-enum>):
  `0 Linear, 1 InQuad, 2 OutQuad, 3 InOutQuad, 4 OutInQuad, 5 InCubic, 6 OutCubic,
  7 InOutCubic, 8 OutInCubic, 9 InQuart, 10 OutQuart, 11 InOutQuart, 12 OutInQuart,
  13 InQuint, 14 OutQuint, 15 InOutQuint, 16 OutInQuint, 17 InSine, 18 OutSine,
  19 InOutSine, 20 OutInSine`. Android equivalents: OutCubic ≈ `DecelerateInterpolator(1.5f)`
  (use `PathInterpolator`/a custom `TimeInterpolator` with `1-(1-t)^3` for exactness),
  OutQuart `1-(1-t)^4`, OutQuint `1-(1-t)^5`, InQuart `t^4`, InOutQuad etc.
* Coordinates: every Luna window manager is a `QGraphicsObject` whose bounding rect is
  **centred on (0,0)** (`QRectF(-w/2, -h/2, w, h)`,
  [WindowManagerBase.cpp#L31](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/WindowManagerBase.cpp#L31)),
  and the root item is placed at the screen centre
  ([WindowServerLuna.cpp#L185](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L185)).
  So "y = −32" below means 32 px above the screen centre. Cards and groups are likewise
  positioned by their centre.

## 0. Configuration: where the numbers come from (read this first)

### 0.0 The reference device

The reference TouchPad runs a LunaSysMgr that identifies itself as "WebOS Ports LunaCE 5.0.0"
(the same string as [StatusBarVersion.cpp#L33](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarVersion.cpp#L33)),
built from this tree, on a 3.0.5-based "webOS CE 3.1.0" image (reported by codepoet from the
device). Its configuration files were pulled from the device and are cited below as
"reference TouchPad /etc/palm/&lt;file&gt; L&lt;line&gt;". **Where the device and the repo differ,
the device value is authoritative** and the repo value is kept only for comparison.

Summary of device-vs-repo differences (details in the tables):

| File | Difference from repo |
|---|---|
| luna.conf | same values as `conf/luna.conf` (only header/comment lines and `schemaValidationOption` missing) |
| luna-platform.conf | **exists**; same as `conf/luna-topaz.conf` minus DisplayWidth/Height → it overrides the card values in luna.conf |
| lunaAnimations.conf | HP's stock 3.0.5 file, **not** `conf/configs/lunaAnimations.conf`: several keys LunaCE reads are absent (code defaults apply), several keys it has are not read by LunaCE (§0.3) |
| launcher3/app-keywords-to-designator-map.txt | favorites tab renamed "games"; keyword `wosa-settings` → settings page (§3.5) |
| launcher3/launcher_operational_settings.conf | not in repo; sets `PreferAppKeywordsForAppPlacement=true` (§3.5) |
| other launcher3 confs, default-launcher-page-layout.json, notificationPolicy.conf, persistentWindows.conf | same content as the repo |
| defaultPreferences.txt | adds `sysUiUseCustomCarrierString: true`, `sysUiCarrierString: "webOS CE"` (L4–L5) |

### 0.1 Load order

`Settings` loads compiled-in defaults, then `/etc/palm/luna.conf`, then
`/etc/palm/luna-platform.conf`; later files override earlier ones key by key
[read] ([Settings.cpp#L44-L45](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L44),
[Settings.cpp#L278-L279](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L278)).
Animation settings do the same with `/etc/palm/lunaAnimations.conf` then
`lunaAnimations-platform.conf` [read]
([AnimationSettings.cpp#L27-L28](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L27),
[AnimationSettings.cpp#L91-L92](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L91));
a key missing from both files keeps its code default. Launcher layout:
`/etc/palm/launcher3/layoutSettings.conf` then `layoutSettings-platform.conf` [read]
([layoutsettings.cpp#L33-L34](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L33));
launcher behaviour: `/etc/palm/launcher3/launcher_operational_settings.conf`
([operationalsettings.cpp#L29-L30](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/operationalsettings.cpp#L29)).

LunaCE's installer replaces only `/usr/bin/LunaSysMgr`
([install.sh#L80](https://github.com/webOSArchive/LunaCE/blob/master/install.sh#L80)), which is
why the device carries the CE image's own `/etc/palm` files rather than the repo's `conf/`.

### 0.2 `[UI]` card/space values

"Device" = effective value on the reference TouchPad (luna.conf overridden by luna-platform.conf). In the "Device source" column, `luna.conf L103` means reference TouchPad /etc/palm/luna.conf line 103 (likewise for luna-platform.conf).

| Key | **Device (effective)** | Device source | Code default | Repo `conf/luna.conf` / `conf/luna-topaz.conf` |
|---|---|---|---|---|
| TabletUi | **true** | luna.conf L103, luna-platform.conf L11 | false [Settings.cpp#L208](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L208) | true / true |
| PositiveSpaceTopPadding (status bar height) | **28** | luna.conf L105 | 24 [Settings.cpp#L211](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L211) | 28 [luna.conf#L139](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L139) / – |
| PositiveSpaceBottomPadding | **28** | luna.conf L106 | 24 [Settings.cpp#L212](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L212) | 28 / – |
| MaximumNegativeSpaceHeightRatio | **0.55** | luna.conf L107 | 0.55 [Settings.cpp#L213](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L213) | 0.55 / – |
| ActiveCardWindowRatio | **0.55** | luna-platform.conf L15 (luna.conf L108 says 0.659, overridden) | 0.659 [Settings.cpp#L214](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L214) | 0.659 [luna.conf#L142](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L142) / 0.55 [luna-topaz.conf#L51](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L51) |
| NonActiveCardWindowRatio | **0.50** | luna-platform.conf L16 (luna.conf L109: 0.61) | 0.61 [Settings.cpp#L215](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L215) | 0.61 [luna.conf#L143](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L143) / 0.50 [luna-topaz.conf#L52](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L52) |
| CardGroupRotFactor | **90** | luna-platform.conf L17 (luna.conf L110: 30) | 90 [Settings.cpp#L217](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L217) | 30 [luna.conf#L144](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L144) / 90 [luna-topaz.conf#L53](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L53) |
| GapBetweenCardGroups | **30** | luna-platform.conf L18 (luna.conf L111: 10) | 10 [Settings.cpp#L218](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L218) | 10 [luna.conf#L145](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L145) / 30 [luna-topaz.conf#L54](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L54) |
| CardGroupingXDistanceFactor | **0.35** | luna-platform.conf L19 | 1.0 [Settings.cpp#L173](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L173) | – / 0.35 [luna-topaz.conf#L55](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L55) |
| GhostCardFinalRatio | **0.85** | (unset → default) | 0.85 [Settings.cpp#L216](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L216) | – |
| CardDimmPercentage | **0.8** | (unset → default) | 0.8 [Settings.cpp#L250](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L250) | – |
| SplashIconSize | **192** | luna-platform.conf L23 | 128 [Settings.cpp#L220](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L220) | – / 192 [luna-topaz.conf#L59](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L59) |
| EnableSplashBackgrounds | **false** | luna-platform.conf L22 | true [Settings.cpp#L221](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L221) | – / false |
| ModalWindowWidth × Height | **320 × 480** | luna-platform.conf L20–L21 | 320 × 480 [Settings.cpp#L247-L248](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L247) | – / 320 × 480 |
| HomeButtonOrientationAngle | **270** | luna-platform.conf L13 | 0 [Settings.cpp#L209](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L209) | – / 270 |
| VirtualKeyboardEnabled | **true** | luna-platform.conf L31 (luna.conf L100: false) | false [Settings.cpp#L193](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L193) | false [luna.conf#L134](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L134) / true [luna-topaz.conf#L67](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna-topaz.conf#L67) |
| CardLimit | **−1** | luna.conf L47 | 16 [Settings.cpp#L104](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L104) | −1 [luna.conf#L81](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L81) / – |
| StatusBarTitleMaxWidth | **140** | (unset → default) | 140 [Settings.cpp#L183](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L183) | – |
| TapRadiusMax (px) | **25** | luna.conf L51 | 12 [Settings.cpp#L137](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L137) | 25 [luna.conf#L85](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L85) / – |
| DisplayWidth × Height | **1024 × 768** | luna.conf L14–L15 (absent from luna-platform.conf) | 320 × 320 [Settings.cpp#L118-L119](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L118) | 1024 × 768 / 1024 × 768 |

`VirtualKeyboardEnabled=true` is what puts the dashboard in tablet drop-down mode (§5).

Note: `kActiveScale`/`kNonActiveScale` in `CardWindowManager.cpp` start as hard-coded statics
`0.659`/`0.61` [read]
([CardWindowManager.cpp#L55-L56](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L55)),
but they are **recomputed from the Settings ratios** the first time positive space is known
([CardWindowManager.cpp#L3172-L3192](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3172)),
so the conf values win (see §2.2 for the "only once" subtlety). `GapBetweenCardGroups` is
copied at init ([CardWindowManager.cpp#L191](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L191)).

### 0.3 Animation durations and curves on the reference device

The device's `lunaAnimations.conf` is HP's stock file. LunaCE's `AnimationSettings` only reads
the keys listed in [AnimationSettings.cpp#L109-L218](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L109);
for each of those, the device value is used if the key is present, else the code default
([AnimationSettings.cpp#L44-L87](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L44)).
No `lunaAnimations-platform.conf` was among the device files (Q18). Curves are cast straight
to `QEasingCurve::Type`
([AnimationSettings.h#L186](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.h#L186)).

Legend for "source": **dev L*n*** = reference TouchPad /etc/palm/lunaAnimations.conf line *n*;
**default** = key absent on the device, code default applies.

| Key | **Device ms** | **Device curve** | Source | Repo `conf/configs` value (for comparison) | Used for |
|---|---|---|---|---|---|
| lunaFPS | **60** | – | default ([#L44](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L44)); device has `normalFPS=35`/`slowFPS=20` (L2–L3), not read | 60 | loading-pulse frame interval ([CardLoading.cpp#L124](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L124)) |
| cardSlideDuration / Curve | **300** | **10 OutQuart** | dev L8–L9 | same | groups sliding in card view |
| cardTrackDuration / Curve | **300** | **0 Linear** | default ([#L46](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L46)); the device file lists `cardTrackGroup*` twice (L10–L13) and has no `cardTrack*` | 50 / 0 | per-card anims while groups follow the finger |
| cardTrackGroupDuration / Curve | **50** | **0 Linear** | dev L10–L13 | same | group x while following the finger |
| cardMaximizeDuration / Curve | **300** | **10 OutQuart** | dev L14–L15 | same | card → full screen and back |
| cardDeleteDuration / Curve | **300** | **6 OutCubic** | dev L18–L19 | same | card thrown away |
| cardShuffleReorderDuration / Curve | **350** | **6 OutCubic** | dev L26–L27 | same | reorder within a group |
| cardGroupReorderDuration / Curve | **500** | **6 OutCubic** | dev L28–L29 | same | card moving between groups |
| cardPrepareAddDuration | **150** | – | dev L34 | same | wait for first frame |
| cardAddMaxDuration | **750** | – | dev L36 | same | then wait for app before card view |
| modalCardAddMaxDuration | **10** | – | dev L37 | same | |
| cardLoadingPulsePauseDuration | **1000** | – | dev L38 | same | |
| cardLoadingPulseDuration / Curve | **1000** | 1 InQuad (card glow is stepped linearly; the curve is only used by dock-mode pulses, [DockModeWindow.cpp#L104](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/dock/DockModeWindow.cpp#L104)) | dev L39–L40 | same | loading glow |
| cardLoadingCrossFadeDuration / Curve | **300** | **0 Linear** | dev L41–L42 | same | splash → app |
| cardLoadingTimeBeforeShowingPulsing | **900** | – | dev L43 | same | |
| cardTransitionDuration / Curve | **300** | 20 OutInSine | dev L44–L45 | same | in-app scene transitions |
| cardGhostDuration / Curve | **750** | 10 OutQuart | dev L46–L47 | same | touch-to-share ghost |
| cardDimmingDuration / Curve | **300** | **6 OutCubic** | dev L48–L49 | same | non-active card dimming |
| dashboardSnapDuration / Curve | **500** | **6 OutCubic** | dev L52–L53 | same | dashboard list height |
| dashboardDeleteDuration / Curve | **200** | **0 Linear** | default ([#L75](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L75)) | 200 / 0 | notification swiped away |
| positiveSpaceChangeDuration / Curve | **400** | **6 OutCubic** | dev L56–L57 | same | status bar slide, keyboard |
| quickLaunchDuration / Curve | **350** | **6 OutCubic** | dev L60–L61 | same | dock slide |
| quickLaunchFadeDuration / Curve | **200** | **6 OutCubic** | dev L64–L65 | same | dock / pill fade |
| launcherDuration / Curve | **350** | **15 InOutQuint** | dev L66–L67 | 400 / 14 OutQuint | launcher slide up/down (also dock background opacity) |
| universalSearchCrossFadeDuration / Curve | **150** | **6 OutCubic** | dev L70–L71 | same | Just Type fade |
| reticleDuration / Curve | 200 | 0 | dev L75–L76 | same | tap reticle |
| brick/progress (MSM) | 300 / 2000 / 700 | 0 / 1 / 1 | dev L79–L84 | same | USB-storage screens |
| lockWindowFadeDuration / Curve | **150** | 1 InQuad | dev L87–L88 | same | lock screen |
| lockPinDuration / Curve | 250 | 15 InOutQuint | dev L89–L90 | same | |
| lockFadeDuration / Curve | **200** | 0 | dev L91–L92 | same | unlock panel fade |
| dock* (dockFadeScreen 900, dockFadeDock 500, delay 270, curve 3) | **defaults** | 3 InOutQuad | default ([#L76-L79](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L76)) | same values | Touchstone dock mode |
| statusBarFadeDuration / Curve | **300** | **0** | dev L95–L96 | same | status-bar colour fill fade |
| statusBarColorChangeDuration / Curve | **300** | **0** | dev L97–L98 | same | colour cross-fade |
| statusBarTitleChangeDuration / Curve | **300** | **0** | dev L99–L100 | same | title cross-fade |
| statusBarTabFadeDuration / Curve | **300** | **0 Linear** | duration dev L101; curve **default** ([#L83](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L83)) because the device key is misspelt `statusBarTabFadeeCurve` (L102) | 300 / 3 | notification group show/hide |
| statusBarArrowSlideDuration / Curve | **500** | **3 InOutQuad** | dev L103–L104 | same | ▾ arrow fade |
| statusBarItemSlideDuration / Curve | **1000** | **3 InOutQuad** | dev L105–L106 | same | status icons slide in/out |
| statusBarMenuFadeDuration / Curve | **200** | **0** | dev L107–L108 | same | menu drop-down fade |
| rotationAnimationDuration | **300** | – | dev L111 | 400 | UI rotation |

Device keys that **this LunaSysMgr never reads** (no reference anywhere in `Src/`, checked by
grep), so they have **no effect** on the reference device: `normalFPS`, `slowFPS`,
`cardLaunchDuration/Curve` (400 / 40), `cardMinimizeDuration/Curve` (350 / 6),
`cardScootAwayOnLaunchDuration/Curve` (400 / 30), `cardMoveNormal*` and `cardMoveOverview*`
(100 / 30), `cardSwitchReachEndMaximized*` (200 / 6), `cardSwitchMaximized*` (350 / 6),
`cardBeforeAddDelay` (0), `quickLaunchSlideToStache*` (150 / 6),
`launcherNormalModeArrowSlide*` (350 / 22), `launcherCardReorderScrollPauseDuration` (550),
`statusBarTabFadeeCurve` (typo). (They are leftovers of HP's own, differently structured 3.0.5
shell; the open-webOS tree still has fields for some of them.) In particular, **minimize** uses
cardSlideDuration/cardMaximizeDuration timings (§2.5), not `cardMinimizeDuration`.

Easing-curve numbers — Qt 4.8 `QEasingCurve::Type` (**external**, Qt docs:
<https://doc.qt.io/archives/qt-4.8/qeasingcurve.html#Type-enum>) and Android equivalents
(write a `TimeInterpolator` with the formula for exactness; `t` in [0,1]):

| # | Qt type | Formula / Android | Where it matters on the device |
|---|---|---|---|
| 0 | Linear | `LinearInterpolator` | tracking, status bar fades, deletes of dashboards |
| 1 | InQuad | `t²` (`AccelerateInterpolator(1f)`) | lock window fade |
| 3 | InOutQuad | `t<.5 ? 2t² : 1−(−2t+2)²/2` (≈ `AccelerateDecelerateInterpolator`) | status-bar arrow/icons |
| 6 | OutCubic | `1−(1−t)³` (≈ `DecelerateInterpolator(1.5f)`) | most card/dock/dashboard motion |
| 10 | OutQuart | `1−(1−t)⁴` (≈ `DecelerateInterpolator(2f)`) | card slide, maximize |
| 14 | OutQuint | `1−(1−t)⁵` | (repo file's launcher curve; not on device) |
| **15** | **InOutQuint** | `t<.5 ? 16t⁵ : 1−(−2t+2)⁵/2` | **launcher slide (device)**, lock pin |
| 20 | OutInSine | out-sine for first half, in-sine for second | in-app scene transitions |
| **22** | **OutExpo** | `t==1 ? 1 : 1−2^(−10t)` | only in an unread key (`launcherNormalModeArrowSlideCurve`) |
| **30** | **OutElastic** | Qt default amplitude 1, period 0.3: `2^(−10t)·sin((t−p/4)·2π/p)+1`, p = 0.3 | only in unread keys (`cardScootAwayOnLaunch`, `cardMove*`) |
| **40** | **OutInBounce** | out-bounce on the first half, in-bounce on the second (Qt's `easeOutInBounce`) | only in an unread key (`cardLaunchCurve`) |

(Enum order used: 0 Linear, 1 InQuad, 2 OutQuad, 3 InOutQuad, 4 OutInQuad, 5 InCubic,
6 OutCubic, 7 InOutCubic, 8 OutInCubic, 9 InQuart, 10 OutQuart, 11 InOutQuart, 12 OutInQuart,
13 InQuint, 14 OutQuint, 15 InOutQuint, 16 OutInQuint, 17 InSine, 18 OutSine, 19 InOutSine,
20 OutInSine, 21 InExpo, 22 OutExpo, 23 InOutExpo, 24 OutInExpo, 25 InCirc, 26 OutCirc,
27 InOutCirc, 28 OutInCirc, 29 InElastic, 30 OutElastic, 31 InOutElastic, 32 OutInElastic,
33 InBack, 34 OutBack, 35 InOutBack, 36 OutInBack, 37 InBounce, 38 OutBounce, 39 InOutBounce,
40 OutInBounce.)

### 0.4 User preferences that change behaviour (LunaCE toggles)

All read from the system `getPreferences` subscription
([Preferences.cpp#L603-L648](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Preferences.cpp#L603)).
Code defaults [read] ([Preferences.cpp#L94-L125](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Preferences.cpp#L94)).
Device values as reported by `getPreferences` on the reference TouchPad (codepoet); the
non-default ones also appear in reference TouchPad /etc/palm/defaultPreferences.txt L4–L5 and
defaultPreferences-platform.txt L3. Every other key is unset on the device → code default.

| Pref key | Code default | **Device** | Stock? | Effect |
|---|---|---|---|---|
| sysUiNoHomeButtonMode | true | **true** (default) | [stock] | home/launcher key behaviour ([SystemUiController.cpp#L778](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L778)) |
| sysUiEnableNextPrevGestures ("advanced gestures") | false | **true** | [stock] | master switch for edge gestures |
| sysUiGestureDetection | 0 | **0 = flick** | [LunaCE] | 0 flick, 1 slide, 2 fluid (§1.4) |
| sysUiEnableAppSwitchGestures | false | **false** | [LunaCE] | left/right edge gestures switch apps |
| sysUiEnableGestureDeadzone | true | **true** | [LunaCE] | swallow touches in the 15 px edge band |
| sysUiEnableMiniCards | false | **false** | [LunaCE] | tap empty area toggles mini cards |
| sysUiEnableZoomGesture | false | **false** | [LunaCE] | pinch scales cards |
| sysUiEnableSpreadGesture | false | **false** | [LunaCE] | pinch fans a stack |
| sysUiEnableMaximizeEdges | false | **false** | [LunaCE] | tap a side group maximizes it |
| sysUiEnableWaveLauncher | false | **false** | [LunaCE] | wave launcher |
| sysUiStatusBarSlide | false | **false** | [LunaCE] | drag down on status bar opens menus |
| sysUiEnableStatusBarSearch | false | **false** | [LunaCE] | search icon in status bar |
| sysUiShowDeviceNameAsCarrierText | false | **false** | [LunaCE] | |
| sysUiUseCustomCarrierString | false | **true** | [LunaCE] | use the string below |
| sysUiCarrierString | "HP webOS" | **"webOS CE"** | [LunaCE] | left status-bar text with no app up |
| infiniteCardCyclingEnabled | false | **false** | [LunaCE] | wrap from last card to first |
| tabbedCardsEnabled | false | **false** | [LunaCE] | tabbed stacks when maximized |

**What is therefore active on the reference device** [inferred from the table + cited code]:
* Edge gestures run in **flick** mode. Only the **bottom-edge flick up** does anything
  (`handleUpSwipe`, §1.4). Left/right-edge flicks reach `handleSideSwipe`, which returns
  immediately because app-switch gestures are off
  ([SystemUiController.cpp#L2361-L2365](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L2361)).
  No fluid minimize/switch.
* Because advanced gestures are on and the dead zone is on, pen-downs in the 15 px left/right
  (below the status bar) and bottom bands never reach apps
  ([SystemUiController.cpp#L344-L357](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L344)).
* LunaCE also calls libqpalm's `setAdvancedGestures(1)`, which on topaz may generate its own
  CoreNavi gesture keys (Q5).
* Card view: no mini cards, no pinch zoom/spread, no infinite cycling, no tabbed cards; tapping a
  side group only brings it to the centre.
* No wave launcher, no status-bar search icon (but see §4.2 for the invisible search group), no
  drag-to-open status bar menus.
* The status bar reads **"webOS CE"** on the left whenever no app is maximized.

---

## 1. Scene layout, z-order and input

### 1.1 Window-manager stack (bottom → top)

The root item gets these children in this order; with equal Z, later = on top
[read] ([WindowServerLuna.cpp#L150-L160](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L150)):

| # | Layer | Contents |
|---|---|---|
| 0 | Wallpaper | painted in `drawBackground`, behind all items ([WindowServerLuna.cpp#L1022](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L1022)) |
| 1 | DockModeWindowManager | Exhibition/Touchstone mode (hidden normally) |
| 2 | DockModeMenuManager | " |
| 3 | **CardWindowManager** | app cards |
| 4 | **OverlayWindowManager** | launcher (Z 0), Just-Type pill (Z 10), quick-launch dock (Z 20), launch feedback (Z 40) ([OverlayWindowManager.cpp#L93-L96](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L93)) |
| 5 | EmergencyWindowManager | (also used for Flash full screen, comment [#L154](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L154)) |
| 6 | **DashboardWindowManager** | notification drop-down, popup alerts, transient alerts, banners |
| 7 | **MenuWindowManager** | **status bar** (Z 100 inside it, [MenuWindowManager.cpp#L103](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/MenuWindowManager.cpp#L103)) + system menu drop-down |
| 8 | TopLevelWindowManager | **lock screen**, brick/USB screens |
| 9 | InputWindowManager | virtual keyboard (only when VirtualKeyboardEnabled) |
| – | full-erase confirmation | scene item, Z 1000 ([WindowServerLuna.cpp#L830](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L830)) |

Note the status bar is *above* the dashboard/banners, and the lock screen is above the status
bar but owns its own status bar instance ([LockWindow.cpp#L411](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L411), Z 100 [#L415](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L415)).
When the launcher is fully open the card layer is made invisible for performance
[read] ([OverlayWindowManager.cpp#L1636-L1643](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1636)),
and card drop shadows are switched off while the launcher is fully visible
([CardWindowManager.cpp#L3526-L3540](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3526)).

### 1.2 Positive / negative space

* "Positive space" = the rect apps may use; normally `(0, 28, W, H−28)` — the status bar is a
  28 px strip at the top [read] ([SystemUiController.cpp#L196-L201](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L196),
  [luna.conf#L139](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L139)).
* Full-screen card: positive space becomes `(0,0,W,H)` and the status bar is moved to
  `y = positiveSpace.y − 28`, i.e. slides off the top [read]
  ([SystemUiController.cpp#L1733-L1743](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1733),
  [MenuWindowManager.cpp#L382-L384](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/MenuWindowManager.cpp#L382)).
  The change animates over positiveSpaceChangeDuration 400 ms / OutCubic
  ([SystemUiController.cpp#L154-L162](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L154)).
* Keyboard: with the virtual keyboard enabled the dashboard does **not** own negative space
  (`m_dashboardOwnsNegativeSpace = !virtualKeyboardEnabled`
  [SystemUiController.cpp#L76](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L76));
  when the IME is open the positive-space bottom = keyboard top
  ([SystemUiController.cpp#L1575-L1577](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1575),
  [InputWindowManager.cpp#L150](https://github.com/webOSArchive/LunaCE/blob/master/Src/ime/InputWindowManager.cpp#L150)).
  Keyboard geometry itself is out of scope (use Android's IME) — see Q9.

### 1.3 Wallpaper

If the image is at least screen-sized in either orientation it is scaled with
`scale = max(W/imgW, H/imgH)` ("centre-crop") and a 90°-rotated copy is pre-rendered for the
other orientation; otherwise it is drawn unscaled and centred on black [read]
([WindowServerLuna.cpp#L931-L962](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L931),
[#L1029-L1031](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L1029)).
Default wallpaper `flowers.png` on the reference device (reference TouchPad /etc/palm/defaultPreferences.txt L14) and in LunaCE's default prefs (open-webOS: `bluerocks.png`)
([defaultPreferences.txt#L11](https://github.com/webOSArchive/LunaCE/blob/master/conf/defaultPreferences.txt#L11)).
Wallpaper drawing is skipped while an opaque layer (dock mode, lock etc.) covers it
([#L1024](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L1024)).

### 1.4 TouchPad gestures and buttons

The TouchPad has no gesture area; LunaCE recognises **screen-edge ("bezel") gestures** on the
touchscreen itself [LunaCE]. Constants [read]
([HostBase.h#L50-L52](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/hosts/HostBase.h#L50)):

| Constant | Value |
|---|---|
| kGestureBorderSize (edge band) | 15 px |
| kGestureTriggerDistance | 15 px |
| kGestureTriggerDistanceIME (keyboard up) | 60 px |

Recognizer ([BezelGestureRecognizer.cpp#L21-L180](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L21)), in UI-rotated coordinates:

* Any multi-touch cancels ([#L49-L52](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L49)).
* Touch must **start** within 15 px of the left, right or bottom edge; otherwise cancelled
  ([#L79-L84](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L79)). The top edge is never a gesture edge.
* Left edge: triggers when `dx ≥ 15` and `dx > dy`; right edge: `dx ≤ −15` and `dx < dy`
  ([#L104-L127](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L104)).
* Bottom edge: start y ≥ H−15 **and** 30 px away from both side edges; triggers when
  `dy ≤ −15` and `dy < dx` ([#L130-L142](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L130)).
* "Flick" flag from the per-event movement (not velocity): 25–100 px per touch update in the
  gesture direction ⇒ flick (+1/−1); |move| < 5 ⇒ 0 (not a flick); 5–25 leaves it unchanged
  ([#L145-L164](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L145)).
  On Android use per-`ACTION_MOVE` deltas, ideally normalised to the TouchPad's report rate
  (unknown, Q6).

Dispatch ([SystemUiController.cpp#L412-L478](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L412)),
only when `sysUiEnableNextPrevGestures` is on and not in wave mode. **On the reference device**
advanced gestures are on and detection mode is 0 (flick), with app-switch gestures off, so only
the bottom-edge flick up has an effect (§0.4):

| sysUiGestureDetection | Left edge → right | Right edge → left | Bottom edge ↑ |
|---|---|---|---|
| 0 "flick" | on finish with flick=+1: `handleSideSwipe(true)` | flick=−1: `handleSideSwipe(false)` | flick=−1: `handleUpSwipe()` |
| 1 "slide" | fires once on first update | " | " |
| 2 "fluid" | live `handleSwitchGesture` (cards follow finger) | " | live `handleMinimizeGesture` (card shrinks with finger) |

* `handleUpSwipe` = **the main "swipe up" gesture**: closes dashboard/menus; hides Just Type;
  if the launcher is open, closes it; if a card is maximized (or about to be) → show dock +
  minimize to card view; otherwise (already in card view) → **toggle launcher**
  ([SystemUiController.cpp#L2390-L2426](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L2390)).
* `handleSideSwipe(next)` requires `sysUiEnableAppSwitchGestures`; switches to the
  previous/next card (maximized: animated card-to-card switch) unless the launcher is up
  ([#L2361-L2388](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L2361)).
* Fluid minimize: while dragging up from the bottom, *all* cards scale:
  `curScale = clamp(curScale + dy/250, kActiveScale·mini, 1.0)`,
  `nonCurScale = clamp(nonCur + dy/250, kNonActiveScale·mini, 0.925)` (dy = per-event delta,
  negative going up) and neighbours slide in at `x = Δindex·(cardW+gap)·2.2·max(curScale−0.5,0)`.
  On release: downward flick → maximize; upward flick → minimize; no flick → minimize if
  `curScale < (kActiveScale+1)/2`, else maximize [read]
  ([CardWindowManager.cpp#L2147-L2255](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2147),
  [CardGroup.cpp#L797-L818](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L797)).
  From card view the same bottom gesture toggles the launcher once
  ([SystemUiController.cpp#L2486-L2493](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L2486)).
* Fluid switch: the maximized card goes non-full-screen and all groups track the finger
  horizontally; on release a flick switches app, or releasing past the screen middle does
  ([CardWindowManager.cpp#L2089-L2145](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2089)).
* **Dead zone** [LunaCE]: with `sysUiEnableGestureDeadzone` (default on) and advanced gestures
  on, pen-downs within 15 px of left/right (below the status bar) or bottom edge are eaten so
  apps don't see them ([SystemUiController.cpp#L344-L357](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L344)).
* **Pinch** in card view with total scale ≤ 0.9 or ≥ 1.1 enters the spread/zoom state
  ([CardWindowManager.cpp#L3686-L3698](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3686)).
* **Stock HP** TouchPad "advanced gestures" were produced inside the closed `libqpalm`
  (`setAdvancedGestures`) as CoreNavi key events; LunaCE still calls it when the pref changes
  ([Preferences.cpp#L902-L912](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Preferences.cpp#L902)).
  Their thresholds are not in this source (Q5). Those keys are handled as: `CoreNavi_Launcher`
  (swipe up) in no-home-button mode → minimize a maximized card (and show dock) else toggle
  launcher ([SystemUiController.cpp#L731-L795](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L731));
  `CoreNavi_SwipeDown` → maximize the active card from card view ([#L798-L825](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L798));
  `CoreNavi_Previous/Next` → switch card ([#L680-L694](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L680)).

**Home button** (`Key_CoreNavi_Home`, acts on release) [stock], in priority order
([SystemUiController.cpp#L827-L891](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L827)):
exit dock mode → (locked: pass) → emergency → close dashboard → close alert → close menu →
close launcher → close Just Type → if a card is maximized: show dock + minimize to card view →
otherwise **toggle the launcher**. So: app → card view → launcher → card view… Home
double-click code exists but is commented out ([hiddkbd_qws.cpp#L242-L279](https://github.com/webOSArchive/LunaCE/blob/master/Src/input/hiddkbd_qws.cpp#L242)).

**Keyboard shortcuts** (BT keyboard, [SystemUiController.cpp#L534-L649](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L534)):
Esc toggles the dashboard, Search toggles Just Type, Super(+←/→/↑/↓/Tab/1-8) mirrors the
gestures; digits 1–8 with Super pick a dock icon via the wave launcher [LunaCE].

---

## 2. Card view

### 2.1 State machine

States: Minimize (card view), Maximize, Preparing, Loading, Focus, Reorder, Group ("tabbed",
[LunaCE]), SwitchGesture, MinimizeGesture, SpreadGesture [LunaCE for the last four]
([CardWindowManager.cpp#L211-L316](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L211)).
Initial state: minimized. Card arrangers per group: `Stack` (card view), `Linear`
(maximized: cards side by side), `Minimize` (fluid gesture), `Tab`
([CardGroup.h#L39-L44](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.h#L39)).

### 2.2 Geometry

Definitions [read] ([CardWindowManager.cpp#L3169-L3200](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3169)):

```
r            = positive space rect at first layout = (0, 28, W, H-28)
normalBounds = r                                  // a card's unscaled size = W x (H-28)
activeScale    = max(0.26, (r.h - 48) * ActiveCardWindowRatio    / r.h)
nonActiveScale = max(0.26, (r.h - 48) * NonActiveCardWindowRatio / r.h)
windowOrigin (group centre y, centred coords)
             = -H/2 + (r.y + 48) + int((r.h - 48) * 0.40)
```

The 48 px is a fake reservation for the Just-Type pill (code comment,
[#L3186-L3187](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3186));
`0.40` is `kWindowOriginRatio` ([#L61](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L61));
0.26 is `kMinimumWindowScale` ([#L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L65)).

**Computed only once.** `activeScale`/`nonActiveScale` live in function-local statics guarded by
`static bool initialBounds = true`, so they are computed from the **first** positive space
LunaSysMgr sees and are *not* recomputed on rotation [read]
([CardWindowManager.cpp#L3171-L3200](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3171)).
`windowOrigin`, by contrast, is recomputed in `resize()` on every rotation
([CardWindowManager.cpp#L357-L375](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L357)).
The TouchPad boots in its native 1024×768 landscape (reference TouchPad /etc/palm/luna.conf
L14–L15), so the landscape scale is used in both orientations. This is confirmed by the
reference screenshot (below).

**Worked numbers, reference TouchPad** (device values Active 0.55 / NonActive 0.50, §0.2)
[inferred, arithmetic on the above]:

| | Landscape 1024×768 | Portrait 768×1024 (after rotation) |
|---|---|---|
| r | 1024 × 740 at y 28 | 768 × 996 at y 28 |
| activeScale (boot, landscape) | 692·0.55/740 = **0.5143** | **0.5143** (not recomputed) |
| active card on screen | **527 × 381** | **395 × 512** |
| nonActiveScale | 692·0.50/740 = **0.4676** → 479 × 346 | 0.4676 → 359 × 466 |
| group centre y (centred coords / screen) | −384 + 76 + int(692·0.4) = **−32** / **352** | −512 + 76 + int(948·0.4) = **−57** / **455** |
| active card rect | x 248.5…775.5, y 161.5…542.5 | **x 186.5…581.5, y 199…711** |

Check against the reference portrait screenshot (768×1024 framebuffer, rotated): the active card
occupies **x = 187…581, y = 200…711** — matching the portrait column to within a pixel. That
confirms the device ratios (0.55), the "scale computed once in landscape" behaviour and the
windowOrigin formula. (Had the scale been recomputed in portrait it would be
948·0.55/996 = 0.5235 → 402 × 521.)

For comparison, the repo's `conf/luna.conf` values (0.659/0.61), which the device overrides,
would give 0.6163 → 631 × 456 and 0.5704 → 584 × 422 in landscape.

**Lunacy (Android) worked example — 1 TouchPad px = 1 dp**, so the 800×1280 px, density 1.33
tablet is a **600 × 960 dp** screen [inferred, same formulas]:

| | Portrait 600×960 dp (first layout) | Landscape 960×600 dp |
|---|---|---|
| r | 600 × 932 at y 28 | 960 × 572 at y 28 |
| activeScale | 884·0.55/932 = **0.5217** → card **313 × 486 dp** | 524·0.55/572 = 0.5038 → 484 × 288 dp |
| nonActiveScale | 884·0.50/932 = **0.4742** → 285 × 442 dp | 0.4580 → 440 × 262 dp |
| group centre y (screen) | 76 + int(884·0.4) = **429 dp** | 76 + int(524·0.4) = **285 dp** |

To be faithful to the TouchPad's "compute once at first layout" rule, compute the scales at
the first layout and keep them across rotation; if Lunacy starts in portrait the portrait
column's 0.5217 then also applies in landscape (484 → 501 × 298 dp cards). Whether to copy that
quirk or recompute per orientation is a product decision (Q10).

A card's transform is `translate(x,y) · scale(z) · rotateZ(zRot°)` about the card centre
[read] ([CardWindow.cpp#L2514-L2521](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L2514)),
interpolated component-wise (linear in x, y, scale, angle) during animations
([CardWindow.cpp#L93-L96](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L93)).
The card's translate is relative to its group anchor (group pos).

### 2.3 Card-view layout of a group (Stack arranger)

Per card *i* (0 = back/left … n−1 = front/right) in the **active** group
[read] ([CardGroup.cpp#L753-L777](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L753)):

```
cardW        = unscaled card width (W)
activeW      = cardW * curScale
x_i          = ((i - p) / 3) * activeW * CardGroupingXDistanceFactor
y_i          = x_i > 0 ? x_i / 15 : 0          // cards right of centre drop slightly
scale_i      = curScale * miniScale
zRot_i (deg) = x_i / (curScale * CardGroupRotFactor)
p            = (n-1)/2  (integer division)  + (n even ? 0.5 : 0)   // LunaCE: always centred
```

`p` is recomputed by `clampCurrentPosition()` on every layout, so in LunaCE a group is always
fanned symmetrically around its centre [read]
([CardGroup.cpp#L903-L913](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L903)).
**Stock** (open-webOS tree) instead clamped `p` to 0 / 0.5 / 1 / [1, n−3] so at most ~4 cards
were spread and the rest scrolled, and compressed x beyond `rOff = curScale·50 + activeW`
/ `lOff = −curScale·100` by `(x + 4·off)/5` (only in the open-webOS tree; cannot confirm the
binary — Q3). The unused `rOff/lOff` variables are still computed in LunaCE
([#L735-L736](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L735)).

Examples on the reference TouchPad (XDistance 0.35, RotFactor 90, activeScale 0.5143)
[inferred]: landscape, activeW = 1024·0.5143 = 527 px, rotation divisor 0.5143·90 = 46.3:

| Group size | x offsets (px) | y of right cards | rotation |
|---|---|---|---|
| 2 | ±30.7 | 2.0 px | ±0.66° |
| 3 | −61.4, 0, +61.4 | 4.1 px | ±1.33° |
| 5 | ±61.4, ±122.9 | 4.1 / 8.2 px | ±1.33° / ±2.65° |

Portrait (activeW 395 px): 3-card offsets ±46.1 px, ±1.0°. So on the device a stack is a tight
fan with barely perceptible tilt. (With the repo luna.conf values — XDistance 1.0, Rot 30,
scale 0.6163 — a 2-card stack would be ±105 px / ±5.7°.)

Group width for spacing = transformed extents of the first card's left edge and last card's
right edge ([CardGroup.cpp#L773-L774](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L773)).

**Non-active (side) groups** are laid out with the same formula but a large x offset, which
collapses them — [inferred] from
[CardGroup.cpp#L257-L286](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L257) and
[#L762-L770](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L762):
`amt = max(1, activeW − |xOffset|)/activeW` (≈ 0 when |xOffset| ≥ activeW), then
`x = x·amt + (1−amt)·10·i`, `y *= amt`, `scale = (nonCur + (cur−nonCur)·amt)·mini`,
`zRot *= amt`. Net effect: side groups show their cards **stacked, each 10 px to the right of
the one behind, unrotated, at nonActiveScale**. Groups between the centre and ~one card width
away interpolate continuously (this is the "collapsing as it slides off centre" effect while
dragging).

Groups are laid out left/right of the active one with `GapBetweenCardGroups` px between
their extents; active group x animates to 0 [read]
([CardWindowManager.cpp#L2862-L2947](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2862)).
All groups share y = windowOrigin ([#L375-L384](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L375)).

`calculateClosedPositions` (used by `layoutAllGroups` for non-animated relayout) packs cards
with 7 px steps for the front 3 cards [read]
([CardGroup.cpp#L861-L901](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L861)).

**Z order:** within a group, later cards (right) draw on top; the active group is re-raised
above others after every animation ([CardGroup.cpp#L601-L616](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L601),
[CardWindowManager.cpp#L3474-L3477](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3474)).

**Dimming:** the active card is drawn at full brightness; the previously active card animates
to 0.8 brightness (RGB multiplied, `CardDimmPercentage`) over 300 ms OutCubic [read] [stock]
([SystemUiController.cpp#L1311-L1316](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1311),
[CardWindow.cpp#L248-L258](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L248),
applied through the corner shader's `Active` uniform
[CardRoundedCornerShaderStage.h#L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardRoundedCornerShaderStage.h#L62)).

**No title or icon is drawn above cards** by LunaSysMgr — I found no text/icon painting in the
card code other than the loading splash ([CardLoading.cpp#L235-L293](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L235)).
The active card's app name appears in the status bar instead (§4).

### 2.4 Card chrome: shadow, rounded corners, loading splash

**Drop shadow** [stock] [read] ([CardDropShadowEffect.cpp#L34-L80](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/visual/CardDropShadowEffect.cpp#L34)):
image `images/card-shadow-tile.png` (87×87) drawn as a 9-patch with all four source insets
= `87/2 = 43` px (1 px stretch centre); destination rect = card rect grown by **20 px** on each
side and shifted **down 5 px**; destination insets also 43 px. So the corners of the shadow
image overlap 23 px into the card (hidden behind it). Drawn before the card, in the card's
own (scaled/rotated) coordinate system, so it scales with the card [inferred]. Android: a
nine-patch `card-shadow-tile` with 43-px stretch lines, bounds = card inset by −20 (+5 y).
Shadows are disabled while the launcher is fully up (§1.1).

**Rounded corners** — on device builds a GLSL shader, not a path [read]
([CardRoundedCornerShaderStage.h#L27-L28](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardRoundedCornerShaderStage.h#L27),
[#L58-L63](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardRoundedCornerShaderStage.h#L58)).
Cards pass radius 40 ([CardWindow.cpp#L2583-L2588](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L2583)),
which selects `Start = (0.491, 0.473)` landscape / `(0.491, 0.478)` portrait in normalized
texture coords ([#L98-L106](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardRoundedCornerShaderStage.h#L98)),
`Center = dst/2 ÷ src` ([#L111-L126](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardRoundedCornerShaderStage.h#L111)),
edge softness `Delta = 0.01` at scale 1 else 0.3 ([#L127](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardRoundedCornerShaderStage.h#L127)).
The corner is an ellipse with half-axes `(Center−Start)·srcSize` in buffer pixels, then scaled
with the card [inferred from the shader math]:

| Orientation (reference TouchPad) | Buffer / card | Half-axes in buffer px | On screen at scale 0.5143 |
|---|---|---|---|
| Landscape | 1024×768 buffer, 1024×740 card | x (0.5−0.491)·1024 ≈ 9.2, y (370/768−0.473)·768 ≈ 6.7 | **≈ 4.7 × 3.4 px** |
| Portrait | 768×1024 buffer, 768×996 card | x (0.5−0.491)·768 ≈ 6.9, y (498/1024−0.478)·1024 ≈ 8.5 | **≈ 3.5 × 4.4 px** |

When minimized, the alpha also ramps to 0 over the outer 30 % of that band on all four edges
(Delta 0.3), i.e. a 1–2 px soft edge on screen. The software/fallback path instead uses a
`QPainterPath` rounded rect of **25 px** radius
([CardWindow.cpp#L953](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L953)),
i.e. ≈ 13 px on screen at scale 0.5143, or 8×6 px while resizing
([#L1589](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1589)).
Maximized cards are blitted as plain rectangles (no corners)
([CardWindow.cpp#L1676-L1690](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1676)).

**Device result:** the reference portrait screenshot shows small, subtle corners of only a few
pixels on the 395×512 active card — that matches the **GLSL shader path** (≈3.5×4.4 px),
not the 25 px path (≈13 px). Implement cards on Android with a corner radius of about
**4 dp** at card-view scale (≈ 7–9 px in full-size card coordinates, scaled with the card) and
a ~1 dp anti-aliased edge; no rounding when maximized [inferred].

**Modal parent scrim:** a card with a modal child gets `#0F0F0F` at 60 % over it
([CardWindow.cpp#L1626-L1630](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1626)).

**Loading splash** [stock] ([CardLoading.cpp](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L41)):
* Background: `images/loading-bg.png` (768×1024) stretched to the card rect through the
  corner shader (radius param 77) ([#L65-L68](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L65),
  [#L258-L267](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L258),
  [#L217-L231](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L217)); an app/scene splash
  background replaces it only if EnableSplashBackgrounds; fallback vertical gradient
  `#484848 → #1E1E1E` ([#L270-L273](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L270)).
* Icon: app `splashicon` scaled to SplashIconSize (192 on topaz), else launcher icon ×1.5 capped
  at that size, centred ([#L82-L98](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L82)).
* Glow: `images/loading-glow.png` (228×228) centred behind the icon, opacity pulsing 0→1→0
  linearly in steps at 60 fps over 1000 ms, first pulse after 900 ms, then 1000 ms pause
  between pulses ([#L123-L134](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L123),
  [#L295-L321](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L295)).
* When the app is ready the overlay cross-fades out over 300 ms linear
  ([#L139-L143](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L139)).

### 2.5 Launch, maximize, minimize

* **New card**: a new group is created, the card is placed *just off the bottom of the screen
  at full size* (`setActiveCardOffScreen`, scale 1.0, y so the card's top is at screen bottom)
  and then maximized upward [read]
  ([CardWindowManager.cpp#L709-L716](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L709),
  [#L1378-L1392](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1378),
  [CardWindowManagerStates.cpp#L648-L661](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManagerStates.cpp#L648)).
  Timing [read]: the card is held back up to cardPrepareAddDuration **150 ms** waiting for the
  app's first frame ([CardWindow.cpp#L1331-L1337](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1331));
  if the app isn't ready by then, the card is added with the loading splash and given
  cardAddMaxDuration **750 ms** more ([CardWindow.cpp#L1419-L1428](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1419));
  if that also expires, the card slides up into the *card view* position at activeScale (other
  groups slide aside) and keeps pulsing until the app paints, then maximizes
  ([CardWindow.cpp#L1432-L1438](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1432),
  [CardWindowManager.cpp#L740-L763](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L740),
  [CardWindowManagerStates.cpp#L698-L704](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManagerStates.cpp#L698)).
  Whether the "off-screen, full size, then maximize" path animates visibly when the app is
  ready within 150 ms (i.e. a slide-up from the bottom edge) is my reading of the code
  [inferred]; confirm on the device (Q7).
* **Card groups form automatically** when a new card is launched by the *focused* card's
  process or app (e.g. a compose card from Email): it joins that group, directly to the right
  of the active card (normal mode) — unless the launch requests a new group; otherwise a new
  group is inserted to the right of the active group [read]
  ([CardWindowManager.cpp#L644-L707](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L644),
  [CardGroup.cpp#L58-L79](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L58)).
  (In LunaCE tabbed mode, the new card goes to the front instead.)
* **Maximize** (tap on a card in card view): the active card animates to scale 1.0, rotation 0,
  centre at `(0, r.y/2)` = 14 px below screen centre, i.e. filling y 28…768; sibling cards in
  the group fly off-screen left/right to `x = ±W` (parent left×2) at curScale; other groups are
  laid out `Linear` (side by side, `W + gap` apart); duration cardMaximizeDuration 300 ms
  OutQuart [read]
  ([CardWindowManager.cpp#L1207-L1265](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1207),
  [CardGroup.cpp#L333-L379](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L333),
  [CardGroup.cpp#L779-L795](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L779)).
  Full-screen apps target the whole screen instead ([#L3229-L3236](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3229)).
  After the animation the card gets focus and is resized to positive space
  ([CardWindowManagerStates.cpp#L346-L404](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManagerStates.cpp#L346)).
  The dock hides and the launcher closes when a card maximizes
  ([OverlayWindowManager.cpp#L363](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L363),
  [#L440](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L440)).
* **Minimize** (home / swipe-up): arranger back to `Stack`, all groups `slideAllGroups()`:
  group x over cardSlideDuration 300 ms OutQuart; the active group's cards over a hard-coded
  **200 ms OutCubic**; non-active groups' cards over 300 ms OutQuart [read]
  ([CardWindowManager.cpp#L1273-L1297](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1273),
  [#L2891-L2903](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2891),
  [CardWindowManagerStates.cpp#L162-L176](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManagerStates.cpp#L162)).
  The dock is shown when minimizing via home/swipe ([SystemUiController.cpp#L880-L885](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L880)).
* **Switching while maximized** (stock path, non-infinite): old card is resized to normal
  bounds, the neighbour becomes active and `maximizeActiveWindow()` animates everything along
  the Linear arrangement; at the last card the old card is nudged 40 px and springs back
  ([CardWindowManager.cpp#L2641-L2725](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2641)).
  With infinite cycling it minimizes, switches, re-maximizes ([CardWindowManagerStates.cpp#L447-L468](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManagerStates.cpp#L447)).

### 2.6 Touch handling in card view

* Axis lock: after moving more than the tap radius (TapRadiusMax 25 px on the TouchPad conf),
  movement is **horizontal if |dx| > 0.866·|dy|**, else vertical [read]
  ([CardWindowManager.cpp#L1640-L1655](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1640)).
* Horizontal: if the group can scroll internally it does (`adjustHorizontally`, 3 positions
  per card width), otherwise **all groups follow the finger** 1:1 (group x via cardTrackGroup 50 ms linear;
  the per-card re-fan via cardTrack, which on the device is the 300 ms linear code default
  because its lunaAnimations.conf lacks that key, §0.3) ([#L1661-L1681](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1661),
  [#L2949-L3010](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2949)).
  In LunaCE `atEdge` is always true for ≤4 cards, and since `p` is always centred the internal
  scroll never engages — [inferred] from [CardGroup.cpp#L629-L640](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L629).
  Release: the group closest to x = 0 becomes active and everything slides to rest (300 ms
  OutQuart) ([#L2064-L2069](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2064)).
  A horizontal flick advances exactly one group in the flick direction ([#L1579-L1601](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1579)).
* Vertical: only the touched card moves, following the finger in y 1:1 (only while the finger
  stays inside that card's column) ([#L1682-L1713](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1682)).
* **Throw away (flick up)** [stock]. Constants ([#L68-L70](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L68)):
  VelocityThreshold −1100, DistanceThreshold −50 px, MinimumVelocity −500. On a vertical flick
  the card closes if `distanceY < −50` **and** `vy < −500` **and**
  `vy < (−1100 · −50) / distanceY` (i.e. the shorter the drag, the faster it must be: at −50 px
  needs vy < −1100, at −100 px vy < −550) ([#L1555-L1574](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1555)).
  Without a qualifying flick: released with the card centre above the screen top → close;
  centre below the screen bottom → close as "angry card" (the dragged-down throw, with a bird
  sound when upside-down); else spring back ([#L2047-L2063](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2047)).
  Velocity units: the flick recognizer keeps the last 3 samples and divides displacement by
  `elapsed·3` (so values are ~⅓ of px/s) and only reports a flick above 500 px/s
  (Manhattan) ([FlickGestureRecognizer.cpp#L37](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/FlickGestureRecognizer.cpp#L37),
  [#L91-L116](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/FlickGestureRecognizer.cpp#L91)).
  For Android: `vy_luna ≈ VelocityTracker(px/s)/3` [inferred].
* **Close animation**: card `position.y` animates to `screenTop − (card.y + h/2)` (straight off
  the top, scale/rotation unchanged) over 300 ms OutCubic; the group closes the gap with a
  normal slide; sound `appclose` ([#L3314-L3367](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3314),
  [Settings.cpp#L109](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L109)).
  An app closing itself animates the same way ([#L808-L819](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L808)).
* **Tap** on a card: maximize it. Tap on another (side) group: make it active (+ maximize if
  `sysUiEnableMaximizeEdges`) ([#L2297-L2328](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2297)).
  Tap on empty space with mini cards on: toggle mini mode (below).
* **Tap-and-hold** on a card → **reorder**; on empty space left/right of centre → previous/next
  group ([#L1511-L1524](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1511)).

### 2.7 Reorder and how groups are built by hand

[stock] ([CardWindowManager.cpp#L1777-L2087](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1777)):
* On entering, the held card becomes 80 % opaque and detaches from its group; it follows the
  finger at `activeScale·mini` (no rotation) ([#L1829-L1839](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1829),
  [#L1784-L1790](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1784)).
* Zones: `section = W·activeScale·mini`; finger x (centred coords) < −section/2 → left zone,
  > +section/2 → right zone, else centre ([#L1817-L1827](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1817)).
* Centre zone: reorder within the group by comparing the card's x with siblings
  (cardShuffleReorder 350 ms OutCubic) ([#L1849-L1870](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1849), [CardGroup.cpp#L155-L200](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L155)).
* Entering the right zone: move one slot right in the group; at the group's end, **leave the
  group**: if the card was alone (a temporary group) it joins the *next* group at its back;
  otherwise a new single-card group is created to the right (cardGroupReorder 500 ms OutCubic).
  Left zone mirrors this. While the finger stays in a side zone the move repeats after each
  animation finishes ([#L1872-L1979](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1872),
  [CardWindowManagerStates.cpp#L765-L769](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManagerStates.cpp#L765)).
  So dragging a card sideways "walks" it through stacks and between them; dropping it while
  inside another stack's run merges it into that stack.
* Release: opacity 1, reattach, slide everything to rest ([#L2072-L2087](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2072)).

### 2.8 LunaCE card-view extras

* **Mini cards** [LunaCE]: `m_miniScale` starts at 0.35
  ([CardWindowManager.cpp#L125](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L125));
  tapping empty card-view space toggles every group's miniScale between 1.0 and that value,
  multiplying all card scales ([#L2330-L2338](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2330)).
* **Card zoom** [LunaCE]: pinch changes miniScale by `scaleFactor − 1` per update, clamped
  0.35…1.35 (single-card group, or any group when spread is off) ([#L2257-L2280](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2257)).
* **Stack spread** [LunaCE]: pinch on a multi-card group changes that group's
  XDistanceFactor by `1.5·(scaleFactor−1)`, clamped to
  `[CardGroupingXDistanceFactor, 12·CardGroupingXDistanceFactor/n]`; on release the view
  relaxes (minimize) ([#L2281-L2294](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2281)).
* **Infinite card cycling** [LunaCE]: past the last group wraps to the first; the "flyback"
  animation slides all groups off-screen (`(nGroups+1)·groupWidth`) linearly, then jumps them to
  the other side and slides in ([#L2528-L2639](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2528),
  [#L2851-L2889](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2851)).
* **Tabbed cards** [LunaCE]: when a maximized card belongs to a stack, a side swipe shows the
  stack as "tabs": the active card at scale 1 shifted ±W/4, siblings at scale **0.225** in a
  column at x = ∓W/2.66, y starting at `−h/2.66 + offset` stepping `0.24·h`, where offset = 46
  (landscape) / 71 (portrait); vertically scrollable with a 1 ms timer applying 0.9 velocity
  decay; horizontal flick on a tab closes it
  ([CardGroup.cpp#L820-L854](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardGroup.cpp#L820),
  [CardWindowManager.cpp#L1716-L1775](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L1716),
  [#L2354-L2404](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L2354),
  [#L3559-L3618](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindowManager.cpp#L3559)).
  Off by default; low priority for Lunacy.
* **Fluid gestures** — see §1.4.

---

## 3. Launcher, quick-launch dock, Just-Type pill

The launcher is "launcher3" (HP's Dimensions UI) hosted in the OverlayWindowManager. Assets
come from `images/launcher3/` (`/usr/palm/sysmgr/images/launcher3/` on device)
[read] ([gfxsettings.cpp#L64](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/gfx/gfxsettings.cpp#L64)).
Everything in this section is [stock] unless tagged [LunaCE].

**Device launcher configuration.** The reference TouchPad's `/etc/palm/launcher3/`
`layoutSettings.conf`, `launcher_icon_geom_settings.conf`, `launcher_icon_layoutsettings.conf`
and `app_blacklist.conf` have the same keys and values as the repo's `conf/launcher3/` files (only
licence headers differ), so the numbers below are device values. The two differences — the
"games" tab name and keyword-first app placement — are in §3.5. No `-platform.conf` launcher
overrides and no `dynamicsSettings.conf` were among the device files, so code defaults apply
for those.

### 3.1 States and what is visible when

| Situation | Just-Type pill | Dock | Launcher | Cards |
|---|---|---|---|---|
| Card view (minimized) | visible | shown | hidden | visible |
| Launcher open | hidden | shown | shown | hidden once fully open |
| App maximized | hidden | hidden | hidden | active card full |
| Just Type open | hidden | hidden | hidden | (behind) |

Derived from the three state machines [read]:
dock ([OverlayWindowManager.cpp#L309-L393](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L309)),
launcher ([#L395-L480](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L395)),
search pill ([#L482-L551](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L482)),
card layer hidden when the launcher is fully open ([#L1636-L1643](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1636)).
The dock is also hidden when a card is added/launched ([#L360](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L360)).
Toggling: home button / swipe-up from card view / dock launcher button (§1.4).

### 3.2 Open / close animations

* **Launcher**: slides vertically. Shown position `y = PositiveSpaceTopPadding/2` (centred
  coords; i.e. its top edge sits under the status bar), hidden `y = uiHeight` (entirely below
  the screen); on the reference device `launcherDuration` **350 ms, curve 15 InOutQuint**
  (reference TouchPad /etc/palm/lunaAnimations.conf L66–L67; repo file: 400 ms / 14 OutQuint;
  code default 200 ms / 2 OutQuad) [read]
  ([OverlayWindowManager.cpp#L1890-L1926](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1890),
  [#L267-L271](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L267),
  [AnimationSettings.cpp#L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/AnimationSettings.cpp#L65)).
  No fade on open/close (opacity is forced to 1 in the open state,
  [#L428](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L428)).
  Before it starts closing, the card layer is made visible again
  ([#L1363-L1374](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1363)).
* **Dock**: shown position = bottom-flush (`y = (H − dockH)/2`), hidden = shown + dockH;
  position animates over `quickLaunchDuration` 350 ms OutCubic and opacity 0↔1 over
  `quickLaunchFadeDuration` 200 ms OutCubic [read]
  ([#L1078-L1091](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1078),
  [#L274-L290](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L274),
  [#L1406-L1489](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1406)).
  With the keyboard or another bottom inset the dock rides above it
  ([#L1204-L1236](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1204)).
  A `backgroundOpacity` animation exists (0 in card view, 1 with launcher) but the dock never
  paints with it, so the dock background looks the same in both states [read]
  ([#L1385-L1388](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1385),
  [dimensionsmain.cpp#L390-L397](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionsmain.cpp#L390)).
* **Just-Type pill**: opacity fade over 200 ms OutCubic ([#L292-L298](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L292)).
  Tapping it opens Just Type (a web app, `Type_Launcher` window placed under the status bar and
  cross-faded in over 150 ms) ([#L1067](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1067),
  [#L1837-L1867](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1837)).
* **Launch feedback**: when an icon is tapped, `launcher-touch-feedback.png` is shown centred
  on it for up to 5 s or until the app appears ([#L1877-L1886](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1877),
  [#L1984-L2019](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1984)).
  Note this path is at the images root, where the repo has no such file (only
  `launcher3/launcher-touch-feedback.png`) — Q13.

### 3.3 Just-Type pill ("search pill")

| Property | Value | Cite |
|---|---|---|
| Width | `searchPillWidthPctScreenRelative` (0.75) × min(W, H) → **576 px** on 1024×768 [inferred]; stock used a fixed 588 per the open-webOS tree | [OverlayWindowManager.cpp#L1052-L1056](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1052), [layoutsettings.cpp#L92](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L92) |
| Height | height of the background image = **50 px** | [#L1058](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1058) |
| Top | status bar bottom + **9 px** (centre y = 28 + 9 + 25) | [#L1062-L1065](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1062), [layoutsettings.cpp#L94](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L94) |
| Background | `launcher3/search-field-bg-launcher.png` (90×50) horizontal 3-slice, **left 40 / right 40** caps, middle stretched | [#L1038-L1041](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1038) |
| Icon | `launcher3/search-button-launcher.png` (32×32), right-aligned 15 px from the right edge, vertically centred | [OverlayWindowManager_p.h#L128-L139](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager_p.h#L128), [layoutsettings.cpp#L93](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L93) |
| Hint text | "Just type..." — Prelude **18 px oblique**, white at **0.8** opacity, text box inset 20 px left / 45 px right, left-aligned, vertically centred, elided; drawn at pill top-left + (0, −1) | [OverlayWindowManager_p.h#L61-L85](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager_p.h#L61), [layoutsettings.cpp#L95](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L95) |
| Tap | only when fully opaque | [OverlayWindowManager_p.h#L184-L185](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager_p.h#L184) |

`images/search-pill.png` / `search-pill-no-icon.png` (320×48) exist but are not used by this code.
Remember that card-view scaling reserves 48 px for the pill (§2.2).

### 3.4 Launcher panel layout

| Part | Value | Cite |
|---|---|---|
| Launcher size | 100 % of the screen, sized to positive space (1024 × 740 landscape) [inferred from read code] | [dimensionslauncher.cpp#L3003-L3013](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L3003), [OverlayWindowManager.cpp#L1248-L1297](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1248) |
| Background | `launcher3/launcher-bg.png` (180×180) **tiled** over the whole launcher from its top-left | [dimensionslauncher.cpp#L1758](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L1758), [#L2064-L2071](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L2064) |
| Tab bar | full width × **50 px**, flush with the launcher top | [layoutsettings.cpp#L69](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L69), [dimensionslauncher.cpp#L1776](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L1776) |
| Tab bar background | `tab-bg.png` (10×50) 9-slice top 20 / bottom 20 / left 4 / right 4, **stretched** | [dimensionslauncher.cpp#L1769-L1772](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L1769) |
| Page area | width = launcher width; height = launcher bottom − tab-bar bottom − dock height − 1, rounded down to even → **1024 × 588** landscape [inferred] | [dimensionslauncher.cpp#L1839-L1856](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L1839) |
| Fade under tab bar | `launcher-scrollfade-top.png` (10×10) tiled in a 10 px strip | [overlaylayer.cpp#L114-L119](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/static/overlaylayer.cpp#L114) |
| Fade above dock | `launcher-scrollfade-bottom.png` (20×20) tiled in a 20 px strip ending at the dock top | [overlaylayer.cpp#L132-L138](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/static/overlaylayer.cpp#L132) |
| Page edge shadows | `tab-shadow.png` (8×8) tiled along the page top, `quicklaunch-shadow.png` (8×8) along the page bottom (painted under icons) | [page.cpp#L405-L440](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/page.cpp#L405), [#L547-L583](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/page.cpp#L547) |
| "Done" button (reorder mode) | `edit-button-done.png` 100×80 sprite: normal src (1,2,98,34), pressed (1,42,98,34); label "DONE" 15 px bold white; at the tab bar's right end, +42 px x adjust from conf | [dimensionslauncher.cpp#L98-L123](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L98), [layoutsettings.cpp#L76-L81](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L76), [layoutSettings.conf#L37](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/layoutSettings.conf#L37) |
| App-info scrim | black @ 0.65 over the launcher | [overlaylayer.cpp#L153-L166](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/static/overlaylayer.cpp#L153) |

The `[Launcher]` keys in luna.conf (`LauncherLabelWidthAdjust` etc.) are parsed
([Settings.cpp#L509-L514](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L509))
but nothing in `Src/lunaui` reads them, so they do not affect launcher3 [inferred from grep].

### 3.5 Tabs

| Property | Value | Cite |
|---|---|---|
| Tab width | `min(barWidth / nTabs, 150)`; tabs left-aligned from x = 0; height 50 | [pagetabbar.cpp#L98](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L98), [#L835-L848](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L835) |
| Label | Prelude, **16 px bold**, **UPPERCASED**, centred both ways, word-wrapped | [pagetabbar.cpp#L79-L91](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L79), [pagetab.cpp#L478-L481](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetab.cpp#L478), [pagetabbar.cpp#L673](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L673) |
| Label colour | normal **#C8C8C8**, selected/highlighted **#FFFFFF** | [layoutsettings.cpp#L72-L73](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L72), [pagetab.cpp#L335](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetab.cpp#L335) |
| Normal bg | `tab-bg.png` 9-slice (20,20,4,4) | [pagetabbar.cpp#L304-L307](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L304) |
| Selected bg | `tab-selected-bg.png` (50×50) 9-slice 20 on all sides; selected tab raised (z 1) | [pagetabbar.cpp#L308-L311](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L308), [pagetab.cpp#L355-L362](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetab.cpp#L355) |
| Highlight bg (press / icon dragged over) | `tab-highlight.png` (50×50) 9-slice 20 all sides | [pagetabbar.cpp#L312-L315](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L312) |
| Dividers | `tab-divider.png` (2×50) vertical 3-slice (top 20, bottom 20) at both tab edges; none left of the first / right of the last tab; selected tab hides its left divider | [pagetabbar.cpp#L317-L354](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L317), [pagetab.cpp#L374-L379](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetab.cpp#L374) |
| Tabs on the reference device | **APPS, DOWNLOADS, GAMES, SETTINGS** [inferred order from page indices apps 0, downloads 1, favorites 2, settings 3]. The device's map renames the built-in favorites page to "games" (displayed uppercased); the repo map has no such entry, so a repo build shows FAVORITES | reference TouchPad /etc/palm/launcher3/app-keywords-to-designator-map.txt L1–L8; [appmonitor.cpp#L451-L465](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/systeminterface/appmonitor.cpp#L451), [appmonitor.cpp#L538-L558](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/systeminterface/appmonitor.cpp#L538), [operationalsettings.cpp#L192-L197](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/operationalsettings.cpp#L192), repo [app-keywords-to-designator-map.txt#L1-L6](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/app-keywords-to-designator-map.txt#L1) |
| Which page a new app lands on (device) | `PreferAppKeywordsForAppPlacement=true` (device-only file; code default false): the keyword map is tried first — apps with keyword `wosa-settings` go to SETTINGS — then the built-in designator rules | reference TouchPad /etc/palm/launcher3/launcher_operational_settings.conf L2 and app-keywords-to-designator-map.txt L10–L13; [operationalsettings.cpp#L198](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/operationalsettings.cpp#L198), [#L257](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/operationalsettings.cpp#L257), [dimensionslauncher.cpp#L3155-L3172](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L3155) |
| Initial page contents | apps/downloads/prefs lists identical to the repo's default-launcher-page-layout.json | reference TouchPad /etc/palm/default-launcher-page-layout.json; [default-launcher-page-layout.json#L3](https://github.com/webOSArchive/LunaCE/blob/master/conf/default-launcher-page-layout.json#L3) |
| Tap a tab | jump to that page, 250 ms InQuad | [dimensionslauncher.cpp#L3493-L3504](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L3493), [#L3864-L3868](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L3864) |

[LunaCE] tab editing (this tree's own additions per README; stock leaves the long-press handler
as a TODO according to the open-webOS tree):

* Limits: `MaxTabs` 6, `MinPermanentTabs` 4 (the four stock tabs can't be deleted)
  ([pagetabbar.h#L75-L78](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.h#L75)).
* Rename: long-press a tab → "Rename Tab" dialog; tabs with index ≥ 4 also get a trash button
  ([dimensionslauncher.cpp#L3506-L3536](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L3506)).
  Also settable via `luna-send … systemUi '{"launchertitlechange":{"id":2,"label":"Games"}}'`
  ([README.md#L49-L54](https://github.com/webOSArchive/LunaCE/blob/master/README.md#L49)).
* Add: hold the empty part of the tab bar **650 ms** (cancel if moved > **16 px** Manhattan) →
  a 28×28 "+" appears 12 px right of the last tab, vertically centred (top half of
  `tab-add-icon.png`, 32×64 sprite); tap → "New Tab" dialog; new tab appended with designator
  `usertab_<uuid>` ([pagetabbar.cpp#L62-L66](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L62),
  [#L185-L233](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L185),
  [#L476-L503](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L476),
  [dimensionslauncher.cpp#L306-L327](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L306)).
* Delete (index ≥ 4): its icons move to the first remaining page
  ([dimensionslauncher.cpp#L329-L400](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L329)).
* Rename/New dialog: panel **520×150**, centred then shifted **up 140 px** (keyboard), prompt row
  44 px, padding 18, trash 40 px, max 24 chars; prompt Prelude 18 px, field Prelude 24 px bold;
  launcher dimmed black α120; panel rgba(25,25,25,235) with 1.5 px white α90 border, radius 12;
  prompt white α170; field border rgba(140,180,255,220) 1.5 px, fill black α180, radius 6, text
  white α235; trash = top half of `tab-delete-icon.png` (32×64)
  ([renamedialog.cpp#L37-L47](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/renamedialog.cpp#L37),
  [#L58-L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/renamedialog.cpp#L58),
  [#L94-L117](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/renamedialog.cpp#L94),
  [#L347-L362](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/renamedialog.cpp#L347)).

### 3.6 Icons and grid

| Property | Value | Cite |
|---|---|---|
| Icon cell | **128×128** | [icongeometrysettings.cpp#L178](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L178), [iconlayoutsettings.cpp#L159-L160](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/icon_layouts/iconlayoutsettings.cpp#L159) |
| Icon image | **64×64**, centred at cell centre + (0, −11) (code default −13; conf −11) | [icongeometrysettings.cpp#L188](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L188), [#L193](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L193), [launcher_icon_geom_settings.conf#L40](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/launcher_icon_geom_settings.conf#L40) |
| Label box | **100×40**, top = icon bottom + 2 px (conf; default 5) | [icongeometrysettings.cpp#L180-L182](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L180), [launcher_icon_geom_settings.conf#L42](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/launcher_icon_geom_settings.conf#L42), [icon.cpp#L1957-L1964](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icon.cpp#L1957) |
| Label font | Prelude **14 px bold, white**, centred, wrapped (2 lines in 40 px), overflow elided with "…", **no shadow** | [icongeometrysettings.cpp#L203-L205](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L203), [icon.cpp#L70-L80](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icon.cpp#L70), [icon.cpp#L1876-L1920](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icon.cpp#L1876) |
| Press feedback | `launcher-touch-feedback.png` (90×90) centred at icon offset (0, −13), up to 3000 ms | [iconheap.cpp#L63](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L63), [icongeometrysettings.cpp#L195](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L195), [dynamicssettings.cpp#L108](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dynamicssettings.cpp#L108) |
| Paint order | feedback → frame → icon → decorator → label | [icon.cpp#L1003-L1060](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icon.cpp#L1003) |
| Reorder frame | `edit-icon-bg.png` (128×128) behind each icon in edit mode | [iconheap.cpp#L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L62) |
| Remove/delete badge | `edit-button-remove.png` / `edit-button-delete.png` 40×80 sprites (normal (2,1,32,32), pressed (2,41,32,32)) at (−50, −50) from cell centre | [iconheap.cpp#L36-L42](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L36), [launcher_icon_geom_settings.conf#L36-L37](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/launcher_icon_geom_settings.conf#L36) |
| Installing | `images/loading-strip.png` 32×608 = 19 frames of 32×32 at (+50, −50); icon at 0.5 opacity | [iconheap.cpp#L44-L49](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L44), [dynamicssettings.cpp#L105](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dynamicssettings.cpp#L105) |
| Grid | max **7** per row (conf; default 6), row gap **10**, horizontal adjust **+12**, left margin **27** (conf; default 50), top margin 20 | [launcher_icon_layoutsettings.conf#L36-L39](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/launcher_icon_layoutsettings.conf#L36), [iconlayoutsettings.cpp#L156-L162](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/icon_layouts/iconlayoutsettings.cpp#L156) |
| Column spacing | `free = rowWidth − 128·(n+1) + 12·n`, `spacing = ⌊free/(n−1)⌋`, n reduced until spacing > 0 → 7 columns ~14 px apart at 1024 wide [inferred; row width not fully traced, Q14] | [reorderableiconlayout.cpp#L1782-L1822](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/icon_layouts/reorderableiconlayout.cpp#L1782) |
| Empty page | "Tap and hold any app to drag it to this page." 18 px #AAAAAA in a 350-wide box centred +190 px; `launcher-empty-page.png` (280×220) | [launcher_icon_layoutsettings.conf#L40-L43](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/launcher_icon_layoutsettings.conf#L40), [reorderablepage.cpp#L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L65) |

### 3.7 Scrolling, paging, reordering

* Axis lock: first axis to exceed the tap radius (25 px on TouchPad conf) wins
  ([page.cpp#L1625-L1640](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/page.cpp#L1625)).
* **Vertical** scroll within a page (KineticScroller): flick velocity ÷ 2225 (inverted) capped at
  100, friction 8e‑4, overscroll ≤ 100 px, dragging while overscrolled at half rate, snap-back
  500 ms OutCubic ([KineticScroller.cpp#L33-L39](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/util/KineticScroller.cpp#L33),
  [#L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/util/KineticScroller.cpp#L62),
  [#L299-L304](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/util/KineticScroller.cpp#L299),
  [#L338-L339](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/util/KineticScroller.cpp#L338)).
* **Horizontal** paging: pages side by side at `index × pageWidth`
  ([dimensionslauncher.cpp#L4286](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L4286)).
  Release without flick → snap to nearest page, **250 ms InQuad**
  ([#L2465-L2489](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L2465),
  [dynamicssettings.cpp#L86-L87](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dynamicssettings.cpp#L86)).
  Flick → one page from where the touch began, duration `|distance| / (velocity/1000)` clamped
  **200–1200 ms**, OutCubic ([#L4145-L4186](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L4145),
  [#L3869-L3878](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L3869)).
* Reorder: tap-and-hold an icon → edit mode (Done button appears). Icon shuffles 300 ms InQuad;
  dragging near the page top/bottom edge (20 px) auto-scrolls 150 px steps (300 ms anim, 800 ms
  delay); near left/right edge (50 px) pans to the neighbour page after 1500 ms; border
  timeouts 1000 ms ([reorderablepage.cpp#L474-L538](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L474),
  [dynamicssettings.cpp#L92-L104](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dynamicssettings.cpp#L92),
  [layoutsettings.cpp#L60-L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L60)).
  Dropping an icon onto a tab transfers it to that tab's page (the tab shows its highlight
  background while hovered) ([dimensionslauncher.cpp#L2575-L2578](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L2575)).

### 3.8 App groups (folders) [LunaCE, this tree]

* Form: drag an icon so it hovers over the **central 60 %** of another icon for **300 ms**; outer
  edge = reorder instead ([reorderablepage.cpp#L67-L71](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L67)).
  Default name "Group" ([#L73-L75](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L73)).
  Dissolves when one member is left ([README.md#L43-L48](https://github.com/webOSArchive/LunaCE/blob/master/README.md#L43)).
* Tile: 68×68 composite, 2×2 grid of 26 px member thumbnails, 6 px margin; > 4 members → 4th
  slot shows "+N" (14 px bold, white α220); backplate radius 8, shadow black α120 offset 1.5,
  vertical gradient rgba(111,111,116,160) → rgba(61,61,66,150) @35 % → rgba(36,36,38,170),
  2 px white α95 border ([groupicon.cpp#L39-L43](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/groupicon.cpp#L39),
  [#L254-L316](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/groupicon.cpp#L254)).
* Overlay panel: cells ≥ 110 px (grown to icon size + 8), ≤ 4 columns, ≤ 16 shown, title row
  44 px (Prelude 22 px, white α235), padding 18, same dim/panel colours as the rename dialog;
  opens 160 ms OutCubic / closes 130 ms InCubic scaling from 0.85 out of the group icon; shifts
  up 140 px while editing the name (max 24 chars). Tap member = launch; hold member = pop it
  back onto the page; tap title = rename
  ([groupoverlay.cpp#L43-L57](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/groupoverlay.cpp#L43),
  [#L229-L232](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/groupoverlay.cpp#L229),
  [#L330-L380](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/groupoverlay.cpp#L330),
  [#L758-L787](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/groupoverlay.cpp#L758),
  [README.md#L43-L48](https://github.com/webOSArchive/LunaCE/blob/master/README.md#L43)).

### 3.9 Quick-launch dock

| Property | Value | Cite |
|---|---|---|
| Size | screen width × **100 px** (**[measured on device]**: 100 px on the reference card-view screenshot) | [quicklaunchbar.cpp#L688-L693](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L688), [layoutsettings.cpp#L83-L85](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L83) |
| Background | `quicklaunch-bg.png` (10×105, translucent) **tiled** from the top-left (bottom 5 px of the image never visible) | [quicklaunchbar.cpp#L63-L64](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L63), [#L110-L118](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L110), [#L277-L287](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L277) |
| Items | max **5** app icons + the launcher button; 64 px icon art, **no labels, no frame** | [layoutsettings.cpp#L88](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L88), [quicklaunchbar.cpp#L145-L150](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L145), [#L194-L203](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L194) |
| Icon x [LunaCE layout] | item area = bar left … (launcher-button centre − ~31 px); split into n equal slots, icon centred in slot: `x = left + (areaW/n)·(idx + 0.5)` | [quicklaunchbar.cpp#L397-L403](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L397), [#L742-L760](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L742) |
| Icon y | **Icon art centre = bar top + 54 px**, level with the launcher button's centre (22 + 32 = 54). Code path: each dock icon item is *positioned* at `m_itemsY = bar top + 65` ([quicklaunchbar.cpp#L403](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L403), [#L758-L759](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L758), [layoutsettings.cpp#L87](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L87)), but 65 is the origin of the icon's 128×128 *cell*; the 64×64 art is painted at the cell origin + `mainIconOffsetFromGeomOriginPx` ([icon.cpp#L1795](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icon.cpp#L1795)), which is (0, −11) on the device (reference TouchPad /etc/palm/launcher3/launcher_icon_geom_settings.conf L6; code default −13). So 65 − 11 = 54 [read + arithmetic]. **[measured on device]** the reference card-view screenshot shows a 100 px dock with icon centres ~54 px below the bar top, matching. (With the code default −13 it would be 52.) Implement as: icon centre y = dock top + 54 dp. | [icongeometrysettings.cpp#L193](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L193) |
| Launcher button | `quicklaunch-button-launcher.png` 64×128 sprite (normal top 64, pressed bottom 64), placed at bar top-right + (−64, 22 + 32); toggles launcher | [quicklaunchbar.cpp#L66-L91](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L66), [#L379-L389](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L379), [layoutsettings.cpp#L86](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L86) |
| Reorder in dock | 300 ms InQuad; dragged icon lifted 15 px | [quicklaunchbar.cpp#L69](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L69), [#L794-L798](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L794) |

`quicklaunch-bg-solid.png` is loaded but never used ([#L118](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L118)).

### 3.10 Wave launcher [LunaCE, Herrie 4.x/5.0, off by default]

* Pref `sysUiEnableWaveLauncher` default false ([Preferences.cpp#L107](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Preferences.cpp#L107)).
* Trigger: drag starting within 30 px of the left or right edge, staying in that band, moving
  up > 30 px with |dx| < |dy|. From card view/launcher the dock hides and comes back 16 px lower
  (queued wave); otherwise the dock shows and tracks the finger, y clamped to ≥ H/3, offset −16
  ([SystemUiController.cpp#L303-L340](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L303),
  [OverlayWindowManager.cpp#L1443-L1446](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1443)).
* Icons ride a sine: `iconY += 96·sin((iconX − (wavePos + h + 32)) / (h/1.5))`, scale
  `(sin(…) − 4)·(−0.25)` (0.75–1.25), h = half item-area width; the icon under the finger shows
  feedback and its label *above* the icon; background becomes a "moustache" Bézier with a
  horizontal black gradient α 0→50→160→200→160→50→0 at 2.5/10/25/50/75/90/97.5 %
  ([quicklaunchbar.cpp#L761-L815](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L761),
  [#L288-L340](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L288),
  [icon.cpp#L1965-L1969](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icon.cpp#L1965)).
* Release above the bottom 15 px: if x < W − 128 launch the highlighted icon, else toggle the
  launcher; dock then hides ([SystemUiController.cpp#L359-L375](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L359),
  [quicklaunchbar.cpp#L1776-L1793](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L1776)).

---

## 4. Status bar

### 4.1 Geometry and background

* Height = PositiveSpaceTopPadding = **28 px**, full width, owned by MenuWindowManager at the top
  ([MenuWindowManager.cpp#L75](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/MenuWindowManager.cpp#L75),
  [#L102](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/MenuWindowManager.cpp#L102)).
* Background ([StatusBar.cpp#L942-L955](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L942)):
  1. a solid colour fill at opacity `m_bkgOpacity` (0 in card view, fades to 1 over
     statusBarFadeDuration 300 ms linear when an app is maximized / launcher open)
     ([#L957-L999](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L957));
     colour = app's custom status-bar colour if it set one, else default **#515558**
     ([#L53](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L53)); launcher and Just Type use **#4F545A**
     ([SystemUiController.cpp#L65-L66](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L65)); colour changes cross-fade linearly in RGB over 300 ms
     ([StatusBar.cpp#L1009-L1047](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L1009));
  2. then `images/statusBar/status-bar-background.png` (20×28) **tiled horizontally** on top
     (always) ([StatusBar.cpp#L321](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L321), [#L953](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L953)).
  So in card view the bar is just the translucent tile over the wallpaper; with an app up it
  is tile over solid colour.
* App custom colour: `card->statusBarColor() << 8 | 0xFF` (RGB from window properties)
  ([SystemUiController.cpp#L1131-L1134](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1131)).

### 4.2 Contents, left → right

Tablet layout ([StatusBar.cpp#L130-L207](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L130),
positions [#L482-L534](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L482)):

| Position | Group | Items | Tap action |
|---|---|---|---|
| left edge (x = −W/2) | **title group** (align left, separator on its right, ▾ arrow) | app title / carrier string ("webOS CE" on the reference device) | app menu (when the app declares one) ([#L892-L898](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L892)) |
| directly left of the notification group | **search group** [LunaCE] | separator, search icon, separator | Just Type (if `sysUiEnableStatusBarSearch`) ([#L866-L873](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L866)). **On the reference device the pref is off**: the icon isn't painted and taps do nothing, but the group still occupies 3 + 12 + 5 + 28 + 5 + 12 = 65 px of empty space (padding + separator + search + separator, 5 px spacing) [inferred from [StatusBarItemGroup.cpp#L488-L512](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L488)] |
| left of system group | **notification group** (hidden when empty) | notification icons (+ scrolling banner text) | opens dashboard drop-down ([#L875-L885](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L875)) |
| right edge | **system group** (align right, separator on its left, ▾) | [info icons] [battery] [clock] | system menu |

Within a right-aligned group the *first added item is rightmost*
([StatusBarItemGroup.cpp#L488-L552](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L488));
the system group adds clock, battery, info ([StatusBar.cpp#L136-L144](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L136)),
so the far right reads `… [icons] [battery] [12:34] ▾`. Info icons are painted right-to-left
in this order: RSSI, WAN, Bluetooth, Wi-Fi, TTY, HAC, call-forward, roaming, VPN, rotation lock,
mute, airplane ([StatusBarInfo.cpp#L174-L276](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L174),
[#L162-L172](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L162)),
each only when active; so on a Wi-Fi TouchPad: `[mute][rot-lock][…][BT][wifi] [batt] [time] ▾`.

Spacing constants [read]: ITEM_SPACING 5, GROUP_RIGHT_PADDING 3
([StatusBarItemGroup.cpp#L29-L30](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L29)),
icon spacing 5 ([StatusBarIcon.h#L35](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarIcon.h#L35)),
ARROW_SPACING 7 either side of the ▾ ([StatusBar.h#L30](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.h#L30)),
notification area max 10 icons × (24+5) px + arrow ([StatusBar.h#L28-L29](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.h#L28),
[StatusBar.cpp#L188](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L188)).
Icons taller than 26 px (28−2) are scaled down to 26 ([StatusBarIcon.cpp#L53-L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarIcon.cpp#L53)).
Icons appear/disappear by animating their width 0↔100 % over statusBarItemSlideDuration
1000 ms InOutQuad ([StatusBarIcon.cpp#L81-L146](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarIcon.cpp#L81)).

**Title**: Prelude **bold 14 px**, letter spacing 90 %, white, left padding 7 px, baseline
offset −2, max width 140 px (elided) ([StatusBarTitle.cpp#L29-L63](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L29),
[StatusBar.h#L34](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.h#L34),
[StatusBar.cpp#L122](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L122)).
On the tablet the title is drawn *without* the pill border (`appname-background.png` is the
phone/emulated-card look) ([StatusBarTitle.cpp#L121-L131](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L121)).
Text changes cross-fade over 300 ms while the width interpolates
([#L203-L239](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L203)).
Title text: maximized card → its launch point's menu name; launcher open → launcher title;
Just Type → its app's menu name; card view → carrier string — **"webOS CE" on the reference
device** (custom carrier string pref, §0.4; code default "HP webOS", or the device name)
[LunaCE] ([SystemUiController.cpp#L1107-L1145](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L1107),
[StatusBar.cpp#L608-L689](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L608)).
The title's ▾ appears (500 ms fade) only when the app is "actionable" (has an app menu).

**Clock**: Prelude **bold 15 px**, white, centred text, baseline −2; 12-hour format
`%I:%M` with the leading zero stripped and **no AM/PM**, or `%H:%M`; ticks every second but only
repaints on minute change ([StatusBarClock.cpp#L55-L61](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarClock.cpp#L55),
[#L162-L196](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarClock.cpp#L162)).

**Battery**: 17×20 images; 13 states, level ≤ 12,20,28,36,44,52,60,68,76,84,88,99,100 % →
`battery-0…11.png`, 100 % reuses 11; charging uses `battery-charging-N.png`, full-and-charging
`battery-charged.png`; `battery-error.png` when powerd is disconnected; no percentage text on the
main bar (`showText` defaults false) ([StatusBarBattery.cpp#L35](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarBattery.cpp#L35),
[#L148-L176](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarBattery.cpp#L148),
[StatusBarBattery.h#L35](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarBattery.h#L35),
[StatusBar.cpp#L105](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L105)).

**Wi-Fi**: `wifi-0` (on, no signal) / `wifi-connecting` / `wifi-1..3` (bars) ([StatusBarInfo.cpp#L221-L227](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L221)).

**Search icon** [LunaCE]: `images/launcher3/search-button-launcher.png` drawn at 0.9 scale in a
28×28 box; the "separators" around it are the same image painted at opacity 0 (i.e. 12×32
spacers) ([StatusBarSearch.cpp#L31-L70](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarSearch.cpp#L31),
[StatusBarSeparator.cpp#L29-L67](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarSeparator.cpp#L29)).

### 4.3 Group chrome and menus

* Each tappable group has `status-bar-separator.png` (2×28) on its inner side and a ▾
  `menu-arrow.png` (15×26) ([StatusBarItemGroup.cpp#L54-L64](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L54)).
* While its menu is open, the group gets a highlighted "tab" background:
  `status-bar-menu-dropdown-tab.png` (80×28) drawn as a **horizontal 3-slice with 11 px
  left/right caps**, the rect extended 11 px beyond the group on each side (minus the separator
  width on the separator side), faded in with the menu (statusBarMenuFadeDuration 200 ms
  linear) ([StatusBarItemGroup.cpp#L361-L385](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L361),
  [#L241-L298](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L241)).
  The separator fades out as the tab fades in ([#L412-L413](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L412)).
* Tap (press+release on the group) toggles the menu; opening one closes the others
  ([#L343-L350](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L343),
  [StatusBar.cpp#L910-L920](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L910)).
  With `sysUiStatusBarSlide` [LunaCE], dragging down ≥ 15 px also opens it
  ([StatusBarItemGroup.cpp#L324-L336](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L324)).
* **The drop-down menus are native QML inside LunaSysMgr on 3.x tablets, not luna-systemui**:
  * System menu: `uiComponents/SystemMenu/SystemMenu.qml`, width 300, max height 410,
    right-aligned under the status bar with an 11 px edge offset
    ([SystemMenu.qml#L4-L16](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/SystemMenu.qml#L4),
    [MenuWindowManager.cpp#L106-L113](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/MenuWindowManager.cpp#L106));
    rows 42 px ([MenuListEntry.qml#L6](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/MenuListEntry.qml#L6)),
    date line Prelude 18 px `#AAA` ([DateElement.qml#L16-L19](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/DateElement.qml#L16)).
    Contents: battery, date, brightness slider, Wi-Fi, VPN, Bluetooth, airplane, rotation lock,
    mute (one QML file each in [uiComponents/SystemMenu](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/SystemMenu.qml#L1)).
  * Notification (dashboard) menu: `DashboardMenu.qml` → `MenuContainer.qml` (§5).
  * Common frame (`MenuContainer.qml`): `menu-dropdown-bg.png` BorderImage with borders
    **L30 T10 R30 B30**; content clipped with margins L11 R11 T0 B15; 320 px content width;
    max height 410; top/bottom scroll fades `menu-dropdown-scrollfade-top/bottom.png`
    (BorderImage L20/R20) with `menu-arrow-up/down.png`, fading in 70 ms when scrollable
    ([MenuContainer.qml#L7-L115](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L7)).
  * Menus fade (opacity) in/out over 200 ms linear together with the tab highlight — no slide
    ([StatusBarItemGroup.cpp#L300-L305](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L300)).
  * The *app* menu (title group) is provided by the app (Mojo/Enyo app menu) —
    `toggleCurrentAppMenu` just forwards ([StatusBar.cpp#L892-L898](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L892)).

---

## 5. Notifications

### 5.1 Tablet mode = "overlay" dashboard

With VirtualKeyboardEnabled — true on the reference device (reference TouchPad /etc/palm/luna-platform.conf L31) — `m_isOverlay = true`: notifications do not push the
card area up from the bottom like on phones; instead they live in a drop-down under the
status bar [read] ([DashboardWindowManager.cpp#L99-L116](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L99),
[SystemUiController.cpp#L76](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L76)).

| Parameter | Value | Cite |
|---|---|---|
| Content width (dashboards, popups, transients) | 320 px | [DashboardWindowManager.cpp#L61](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L61) |
| Dashboard row height | 52 px | [DashboardWindowContainer.cpp#L49](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L49) |
| Badge (icon) area not forwarded to app when dragging | 50 px | [#L50](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L50), [#L216](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L216) |
| Max menu height passed to QML | 480 px (then capped by QML maxHeight 410) | [#L149-L153](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L149), [MenuContainer.qml#L7](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L7) |
| Row separator | `images/menu-divider.png` (10×2) | [#L1248](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1248) |
| Swipe-to-dismiss threshold | row moved > width/4 (80 px) on release | [#L356-L370](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L356) |
| Dismiss animation | slide 1.5×width further in the drag direction, 200 ms linear | [#L1170-L1181](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1170) |
| Height change / snap | dashboardSnap 500 ms OutCubic | [#L116-L117](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L116) |
| Popup alert inset from top-right | 5 px | [DashboardWindowManager.cpp#L60](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L60), [#L1288-L1302](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L1288) |

* Drop-down position: its right edge is aligned to the notification group's right edge in the
  status bar (via `signalDashboardAreaRightEdgeOffset`), top at the positive-space top (y 28)
  ([DashboardWindowManager.cpp#L378-L384](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L378),
  [#L1272-L1286](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L1272),
  [StatusBar.cpp#L509-L521](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L509)).
* Stacking order of rows (newest first or last) was not traced — Q8. Rows are Mojo "dashboard" web windows rendered by the app; LunaSysMgr only
  hosts them.
* Dismiss: drag a row horizontally (in menu mode it can only be dragged **right**, x clamped at
  its rest position) ([#L309](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L309)).
  While dragged, the uncovered strip behind it shows `menu-dropdown-swipe-bg.png` (57×54,
  3-slice with 5 px caps) plus a `menu-dropdown-swipe-highlight.png` (1×54) edge line
  ([#L1377-L1402](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1377)).
  Persistent dashboards can't be dismissed. Tapping outside, Home, Esc or opening any other
  menu closes the drop-down.
* The drop-down only opens if there is at least one dashboard and no alert is up
  ([DashboardWindowManager.cpp#L303-L316](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L303)).
* Popup alerts (e.g. calendar reminder): 320 px wide in a `popup-bg.png` 9-patch frame with
  20 px insets on all sides (the frame adds 20 px around the content), pinned 5 px from the
  top-right below the status bar ([GraphicsItemContainer.cpp#L39](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/GraphicsItemContainer.cpp#L39),
  [#L97-L106](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/GraphicsItemContainer.cpp#L97),
  [#L125-L132](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/GraphicsItemContainer.cpp#L125),
  [DashboardWindowManager.cpp#L166-L170](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L166)).
* Transient alerts: 320 px, `transient-alart-bg.png` (80×80) 9-patch, 20 px insets, centred in
  positive space, opacity-animated ([GraphicsItemContainer.cpp#L107-L116](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/GraphicsItemContainer.cpp#L107),
  [DashboardWindowManager.cpp#L171-L178](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L171),
  [#L1304-L1317](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowManager.cpp#L1304)).

### 5.2 Banners

On the TouchPad the default banner view is the **status bar notification area** in
`StatusBarScroll` mode — banners scroll *inside the status bar*, just left of the system
icons, not at the bottom of the screen [read]
([StatusBar.cpp#L180-L183](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L180),
[StatusBarNotificationArea.cpp#L62-L75](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarNotificationArea.cpp#L62)).
(The `BannerWindow` vertical-scroll view is only registered in non-overlay/phone mode
[BannerWindow.cpp#L57-L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerWindow.cpp#L57).)

| Parameter | Value | Cite |
|---|---|---|
| Font | Banner font "Prelude", **16 px**, white | [BannerMessageHandler.cpp#L67](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L67), [#L203-L207](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L203), [#L290](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L290) |
| Text margin | 5 px (left, and between icon and text) | [#L68](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L68) |
| View height | PositiveSpaceBottomPadding = 28 px | [#L201](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L201) |
| Icon | message icon or app mini-icon, scaled to ≤ 26 px high | [#L578-L609](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L578) |
| Show time | 5000 ms if it is the only queued banner, 2000 ms if others are waiting (a running 5 s timer is cut to the 2 s remainder when a new one arrives) | [#L69-L70](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L69), [#L486-L507](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L486), [#L635-L638](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L635) |
| Show animation | 1000 ms, OutCubic (position progress 0→1 + opacity 1) | [#L122-L130](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L122), [#L617-L619](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L617) |
| Hide animation | 1000 ms, default (linear) curve, progress →0, opacity →0.25 | [#L621-L623](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L621) |
| Max width | notification-area max (≈ 10×29 + arrow) | [StatusBarNotificationArea.cpp#L184-L193](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarNotificationArea.cpp#L184) |

StatusBarScroll drawing: the banner (icon + elided text) is revealed right-to-left — clip width
= progress × width and the content is translated left by progress × width from the area's
right edge, so it "unrolls" leftwards out of the notification icons, then rolls back
([BannerMessageHandler.cpp#L371-L408](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L371)).
Banners are queued FIFO, one at a time ([#L477-L485](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L477)).
Tapping the notification area while a banner shows activates the banner (launches its app with
its params) instead of opening the dashboard ([StatusBar.cpp#L875-L878](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L875)).
Opening a banner closes the dashboard ([StatusBar.cpp#L900-L904](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L900)).
Sound: `notificationSoundDuration` 5000 ms ([luna.conf#L58](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L58)).

Notification icons in the bar: one per dashboard window (its icon), newest appended, slide in
with the 1000 ms item animation; the group fades in (300 ms) when the first appears and out
when none remain ([StatusBarNotificationArea.cpp#L254-L282](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarNotificationArea.cpp#L254),
[StatusBarItemGroup.cpp#L182-L226](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L182)).

### 5.3 Lock screen (only what matters for layering)

Lock screen is TopLevelWindowManager, above everything; it has its own status bar showing the
date instead of time ([StatusBar.cpp#L415-L418](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L415)),
a bitmap clock (`screen-lock-clock-0…9/colon/decimal.png`, 80 px tall)
([ClockWindow.cpp#L85-L90](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/ClockWindow.cpp#L85)),
the "Drag up to unlock" padlock (`screen-lock-padlock-off/on.png`, 100×100; font Prelude 20 px)
([LockWindow.cpp#L97](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L97),
[#L2252-L2254](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L2252),
[#L2345-L2347](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L2345)),
PIN/password panel in QML (`uiComponents/UnlockPanel`). Not analysed further (Q11).

---

## 6. Fonts and global colours

### 6.1 Fonts

All shell text uses the **Prelude** family (Palm's typeface). Font *names* come from `[Fonts]`
settings, all "Prelude" (code [Settings.cpp#L197-L206](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L197);
reference TouchPad /etc/palm/luna.conf L93–L97 sets Banner/LockWindow/DockMode/Quicklaunch to
Prelude too); the only file path in source is `/usr/share/fonts/Prelude-Bold.ttf`
([Settings.cpp#L202-L203](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L202)).

Lunacy ships the device's files (`assets/luna/fonts`, device file names). Their internal
family/style names (read with `fc-scan`) are: family **"Prelude"** = `Prelude-Medium.ttf`
(Medium, weight 100), `Prelude-Bold.ttf` (Bold, 200), `Prelude-MediumOblique.ttf`,
`Prelude-BoldOblique.ttf`; the `PreludeWGL-*`, `PreludeCondensed-*`, `PreludeCondWGL-*`,
`PreludeCompWGL-*` files are *different family names*, so a `QFont("Prelude")` request never
resolves to them. Qt therefore maps plain "Prelude" → Medium, bold → Bold, oblique → Medium
Oblique [inferred from font-matching; there is no lighter/regular "Prelude" face to fall back to].

| Element | Request in code | **File to use** | Size (px = dp) | Colour | Cite |
|---|---|---|---|---|---|
| Status bar title | Prelude, bold, letter spacing 90 % | **Prelude-Bold.ttf** | 14 | #FFFFFF | [StatusBarTitle.cpp#L55-L63](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L55), [#L192](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L192) |
| Status bar clock | Prelude, bold | **Prelude-Bold.ttf** | 15 | #FFFFFF | [StatusBarClock.cpp#L55-L61](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarClock.cpp#L55), [#L127](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarClock.cpp#L127) |
| Banner text (status bar) | Prelude | **Prelude-Medium.ttf** | 16 | white | [BannerMessageHandler.cpp#L203-L207](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/BannerMessageHandler.cpp#L203) |
| Just-Type pill "Just type..." | Prelude, oblique | **Prelude-MediumOblique.ttf** | 18 | white @ 80 % | [OverlayWindowManager_p.h#L61-L82](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager_p.h#L61) |
| Launcher tab labels (uppercase) | Prelude, bold | **Prelude-Bold.ttf** | 16 | #FFFFFF / #C8C8C8 | [pagetabbar.cpp#L79-L91](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L79), [layoutsettings.cpp#L70-L73](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L70) |
| Launcher icon labels | Prelude, bold | **Prelude-Bold.ttf** | 14 | white | [icongeometrysettings.cpp#L203-L205](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/icongeometrysettings.cpp#L203) |
| Launcher "DONE" button | Prelude, bold | **Prelude-Bold.ttf** | 15 | white | [layoutsettings.cpp#L76-L81](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L76) |
| Empty-page hint | Prelude, bold (code default embolden) | **Prelude-Bold.ttf** | 18 (device conf) | #AAAAAA | [iconlayoutsettings.cpp#L166](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/icon_layouts/iconlayoutsettings.cpp#L166); reference TouchPad /etc/palm/launcher3/launcher_icon_layoutsettings.conf L8–L9 |
| Rename/New-tab dialog prompt / field [LunaCE] | Prelude / Prelude bold | **Prelude-Medium.ttf** / **Prelude-Bold.ttf** | 18 / 24 | white α170 / α235 | [renamedialog.cpp#L58-L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/renamedialog.cpp#L58) |
| App-group overlay title / tile "+N" [LunaCE] | Prelude bold | **Prelude-Bold.ttf** | 22 / 14 | white α235 / α220 | [groupoverlay.cpp#L70-L72](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/groupoverlay.cpp#L70), [groupicon.cpp#L294-L296](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/groupicon.cpp#L294) |
| System menu date | QML Prelude, bold false | **Prelude-Medium.ttf** | 18 | #AAAAAA | [DateElement.qml#L16-L19](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/DateElement.qml#L16) |
| Lock screen labels | Prelude | **Prelude-Medium.ttf** | 20 | – | [LockWindow.cpp#L2345-L2347](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L2345) |

The WGL / Condensed / Comp faces are used by the apps' CSS (Mojo/Enyo), not by the native shell
[inferred: no native code names them].

### 6.2 Colours

| Use | Value | Cite |
|---|---|---|
| Status bar fill (app with no custom colour) | #515558 | [StatusBar.cpp#L53](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L53) |
| Status bar fill (launcher / Just Type) | #4F545A | [SystemUiController.cpp#L65-L66](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/SystemUiController.cpp#L65) |
| Status bar / clock / title / banner text | #FFFFFF | above |
| Non-active card dim | RGB × 0.8 | [Settings.cpp#L250](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/settings/Settings.cpp#L250) |
| Modal-parent scrim | #0F0F0F @ 60 % | [CardWindow.cpp#L1629](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardWindow.cpp#L1629) |
| Card loading fallback gradient | #484848 → #1E1E1E | [CardLoading.cpp#L270-L272](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L270) |
| Launcher tab text | #FFFFFF / #C8C8C8 | [layoutsettings.cpp#L72-L73](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/layoutsettings.cpp#L72) |
| Empty launcher page text | #AAAAAA, 18 px | [launcher_icon_layoutsettings.conf](https://github.com/webOSArchive/LunaCE/blob/master/conf/launcher3/launcher_icon_layoutsettings.conf#L43) |
| No-wallpaper background | black | [WindowServerLuna.cpp#L1030](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/WindowServerLuna.cpp#L1030) |

---

## 7. Image asset inventory (shell parts covered above)

All paths are relative to `images/` in the repo = `/usr/palm/sysmgr/images/` on the device
(`SystemResourcesPath`, [luna.conf#L42](https://github.com/webOSArchive/LunaCE/blob/master/conf/luna.conf#L42)).
Sizes measured from the PNG headers [read]. Only four files differ from the open-webOS image
set: `corenavi/*`, `emucard-*` and `launcher3/tab-add-icon.png`, `tab-delete-icon.png`
(LunaCE additions); everything else is stock.

### 7.1 Cards

| Asset | Size | Drawn as | Cite |
|---|---|---|---|
| card-shadow-tile.png | 87×87 | 9-patch, insets 43/43/43/43 (src and dst), rect = card +20 px each side, +5 px y | [CardDropShadowEffect.cpp#L44](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/visual/CardDropShadowEffect.cpp#L44), [#L71-L79](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/visual/CardDropShadowEffect.cpp#L71) |
| loading-bg.png | 768×1024 | stretched to the card, through corner shader | [CardLoading.cpp#L67](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L67), [#L263](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L263) |
| loading-glow.png | 228×228 | single, centred, pulsing opacity | [CardLoading.cpp#L54](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L54), [#L282](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardLoading.cpp#L282) |
| scrim.png | 320×480 | PDK host-window overlay (fills card path) | [CardHostWindow.cpp#L90](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardHostWindow.cpp#L90), [#L284](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardHostWindow.cpp#L284) |
| fullscreen-play-button.png | 100×200 | 2-state strip (top/bottom half), PDK cards | [CardHostWindow.cpp#L80](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardHostWindow.cpp#L80), [#L295](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/cards/CardHostWindow.cpp#L295) |

### 7.2 Status bar (`images/statusBar/`)

| Asset | Size | Drawn as | Cite |
|---|---|---|---|
| status-bar-background.png | 20×28 | tiled horizontally across the bar | [StatusBar.cpp#L321](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L321), [#L953](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBar.cpp#L953) |
| status-bar-separator.png | 2×28 | single, at group inner edge | [StatusBarItemGroup.cpp#L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L62), [#L412-L421](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L412) |
| status-bar-menu-dropdown-tab.png | 80×28 | horizontal 3-slice, caps 11 px | [StatusBarItemGroup.cpp#L121](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L121), [#L366-L384](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L366) |
| status-bar-menu-dropdown-tab-pressed.png | 80×28 | unused (commented out) | [StatusBarItemGroup.cpp#L129](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L129) |
| menu-arrow.png | 15×26 | single, vertically centred, 7 px padding | [StatusBarItemGroup.cpp#L55](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L55), [#L396-L409](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarItemGroup.cpp#L396) |
| appname-background.png | 40×26 | 3-slice (13 L / 20 R) — phone/emulated only, not tablet | [StatusBarTitle.cpp#L31-L32](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L31), [#L173-L186](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarTitle.cpp#L173) |
| battery-0…11, battery-charging-0…11, battery-charged, battery-error | 17×20 | single | [StatusBarBattery.cpp#L158-L179](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarBattery.cpp#L158) |
| wifi-0…3, wifi-connecting | 20×18 | single | [StatusBarInfo.cpp#L223-L227](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L223) |
| bluetooth-on/connecting/connected | 13×18 | single | [StatusBarInfo.cpp#L215-L217](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L215) |
| icon-rotation-lock / icon-mute / icon-airplane | 24×24 (drawn scaled to ≤26 h, i.e. as-is) | single | [StatusBarInfo.cpp#L264](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L264), [#L270](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L270), [#L275](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L275) |
| vpn-status-icon | 22×18 | single | [StatusBarInfo.cpp#L258](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L258) |
| rssi-*, network-*, tty, hac, call-forward, network-roaming | 9–33 × 11–18 | single (phone radios; not on Wi-Fi TouchPad) | [StatusBarInfo.cpp#L184-L251](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarInfo.cpp#L184) |
| launcher3/search-button-launcher.png | 32×32 | single at 0.9 scale (LunaCE status-bar search) | [StatusBarSearch.cpp#L37-L39](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/status-bar/StatusBarSearch.cpp#L37) |
| slider-*, brightness-*, system-menu-*, icon-*-off | – | used by SystemMenu QML (not traced) | [uiComponents/SystemMenu](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/SystemMenu/SystemMenu.qml#L1) |

### 7.3 Menus, dashboards, alerts

| Asset | Size | Drawn as | Cite |
|---|---|---|---|
| menu-dropdown-bg.png | 80×160 | BorderImage 9-patch L30 T10 R30 B30 | [MenuContainer.qml#L35-L41](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L35) |
| menu-dropdown-scrollfade-top/bottom.png | 60×30 | BorderImage L20 R20 (horizontal stretch) | [MenuContainer.qml#L79-L83](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L79), [#L102-L106](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L102) |
| menu-arrow-up/down.png | 21×21 | single, centred on the fades | [MenuContainer.qml#L85-L89](https://github.com/webOSArchive/LunaCE/blob/master/uiComponents/MenuContainer/MenuContainer.qml#L85) |
| menu-divider.png | 10×2 | stretched to row width | [DashboardWindowContainer.cpp#L1248](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1248), [#L1373](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1373) |
| menu-dropdown-swipe-bg.png | 57×54 | horizontal 3-slice, caps min(5, w) | [DashboardWindowContainer.cpp#L1236](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1236), [#L1384-L1386](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1384) |
| menu-dropdown-swipe-highlight.png | 1×54 | stretched vertically at the swipe edge | [DashboardWindowContainer.cpp#L1242](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1242), [#L1388](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1388) |
| dashboard-mask-top/bottom.png | 5×8 | phone-mode dashboard masks (not tablet) | [DashboardWindowContainer.cpp#L1191-L1197](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L1191) |
| popup-bg.png | 80×160 | 9-patch, 20 px insets all sides | [GraphicsItemContainer.cpp#L97-L106](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/GraphicsItemContainer.cpp#L97) |
| transient-alart-bg.png (sic) | 80×80 | 9-patch, 20 px insets | [GraphicsItemContainer.cpp#L107-L116](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/GraphicsItemContainer.cpp#L107) |

### 7.4 Lock screen (for completeness)

`screen-lock-clock-{0-9,colon,decimal}.png` (38–54 × 80), `screen-lock-padlock-{off,on}.png`,
`screen-lock-incoming-call-{off,on}.png` (100×100), `screen-lock-target-scrim.png` (320×190),
`screen-lock-wallpaper-mask-top.png` (320×117) / `-bottom.png` (10×250), `dashboard-scroll-fade.png`
(320×29), `pin/*` — cites in §5.3 and [LockWindow.cpp#L2435-L2438](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L2435),
[#L2526-L2531](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/lockscreen/LockWindow.cpp#L2526). How each is stretched was not analysed.

### 7.5 Launcher / dock / pill

Numbers in §3.

| Asset (`images/launcher3/…` unless noted) | Size | Drawn as | Cite |
|---|---|---|---|
| launcher-bg.png | 180×180 | tiled brush over the launcher | [dimensionslauncher.cpp#L2064-L2071](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L2064) |
| tab-bg.png | 10×50 | 9-slice T20 B20 L4 R4, stretched (bar + normal tabs) | [dimensionslauncher.cpp#L1769-L1772](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L1769) |
| tab-selected-bg.png | 50×50 | 9-slice 20 all sides | [pagetabbar.cpp#L308-L311](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L308) |
| tab-highlight.png | 50×50 | 9-slice 20 all sides | [pagetabbar.cpp#L312-L315](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L312) |
| tab-divider.png | 2×50 | vertical 3-slice T20 B20 | [pagetabbar.cpp#L317-L354](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L317) |
| tab-shadow.png / quicklaunch-shadow.png | 8×8 | tiled strip along page top / bottom | [page.cpp#L405-L440](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/page.cpp#L405) |
| launcher-scrollfade-top.png | 10×10 | tiled 10 px strip under tab bar | [overlaylayer.cpp#L114-L119](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/static/overlaylayer.cpp#L114) |
| launcher-scrollfade-bottom.png | 20×20 | tiled 20 px strip above dock | [overlaylayer.cpp#L132-L138](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/static/overlaylayer.cpp#L132) |
| tab-add-icon.png [LunaCE] | 32×64 | top half, drawn 28×28 | [pagetabbar.cpp#L476-L503](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/pagetabbar.cpp#L476) |
| tab-delete-icon.png [LunaCE] | 32×64 | top half, 40 px button | [renamedialog.cpp#L47](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/renamedialog.cpp#L47) |
| edit-button-done.png | 100×80 | 2-state sprite, 98×34 each | [dimensionslauncher.cpp#L98-L123](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/dimensionslauncher.cpp#L98) |
| edit-button-remove.png / edit-button-delete.png | 40×80 | 2-state sprite, 32×32 each | [iconheap.cpp#L36-L42](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L36) |
| edit-icon-bg.png | 128×128 | single (edit-mode frame) | [iconheap.cpp#L62](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L62) |
| launcher-touch-feedback.png | 90×90 | single (press glow) | [iconheap.cpp#L63](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L63) |
| launcher-empty-page.png | 280×220 | single | [reorderablepage.cpp#L65](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/page/reorderablepage.cpp#L65) |
| quicklaunch-bg.png | 10×105 | tiled brush (dock) | [quicklaunchbar.cpp#L277-L287](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L277) |
| quicklaunch-bg-solid.png | 10×105 | loaded, unused | [quicklaunchbar.cpp#L118](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L118) |
| quicklaunch-button-launcher.png | 64×128 | 2-state sprite, 64×64 each | [quicklaunchbar.cpp#L66-L91](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/bars/quicklaunchbar.cpp#L66) |
| search-field-bg-launcher.png | 90×50 | horizontal 3-slice L40 R40 | [OverlayWindowManager.cpp#L1038-L1041](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1038) |
| search-button-launcher.png | 32×32 | single (pill icon; LunaCE status-bar search) | [OverlayWindowManager.cpp#L1045-L1047](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1045) |
| `images/loading-strip.png` | 32×608 | 19-frame film strip, 32×32 | [iconheap.cpp#L44-L49](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L44) |
| `images/warning-icon.png` | 32×32 | single badge | [iconheap.cpp#L51-L52](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/elements/icons/iconheap.cpp#L51) |
| `images/default-app-icon.png` | 64×64 | fallback when an app has no icon | [LaunchPoint.cpp#L165](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/application/LaunchPoint.cpp#L165) |
| list-divider.png | 710×2 | alphabet page only (probably unused on tablet) | – |

Not referenced by this code: `launcher-bg64`, `launcher-icon-64/72`, `superscroll-*`,
`dark-arrow-*`, `*-original`, `edit-icon-bg-small*`/`-light`, `quicklaunch-button-search`,
`images/search-pill*.png`, `images/quick_launch_highlight.png`, `images/dock-item-shadow-tile.png`
[inferred from grep — re-check before deleting anything].

---

## 8. Open questions

Numbered as referenced in the text.

1. **Q1 — resolved.** The reference TouchPad has `/etc/palm/luna-platform.conf` with the topaz
   values (Active 0.55, NonActive 0.50, RotFactor 90, Gap 30, XDistance 0.35,
   VirtualKeyboardEnabled true); §0.2 and §2 now use them. Still unknown: whether
   `/etc/palm/lunaAnimations-platform.conf` or `/etc/palm/launcher3/*-platform.conf` exist
   (none were among the copied files) — see Q18.
2. **Q2 — resolved.** The device runs this tree's LunaCE 5.0.0 build on a 3.0.5-based
   "webOS CE 3.1.0" image, with the preference values listed in §0.4. [LunaCE]-tagged behaviour
   can be validated on it, but only for the toggles that are on.
3. **Q3 — Stock card fanning.** Stock's per-group spread clamp and x-compression are known only
   from the open-webOS tree; the HP 3.0.5 binary can't be read line by line. Compare a 5-card
   stack on stock vs LunaCE on the device.
4. **Q4 — resolved.** The portrait screenshot's active card (x 187…581, y 200…711) has corners of
   a few px, matching the GLSL shader path (≈3.5×4.4 px at scale 0.5143), not the 25 px path
   (≈13 px) (§2.4). The same screenshot confirms the card size/position maths (§2.2).
5. **Q5 — libqpalm gestures on the device.** With `sysUiEnableNextPrevGestures=true`, LunaCE also
   calls libqpalm's `setAdvancedGestures(1)` (topaz 3.0.5 exports it), whose own bottom-edge
   gestures arrive as CoreNavi keys (§1.4) in addition to LunaCE's bezel recognizer. Their
   thresholds are in closed code, and whether both fire for one swipe was not determined.
   Measure a swipe-up on the device if the exact trigger distance matters.
6. **Q6 — Bezel "flick" calibration.** The flick flag uses per-touch-event deltas of 25–100 px
   ([BezelGestureRecognizer.cpp#L148-L153](https://github.com/webOSArchive/LunaCE/blob/master/Src/base/gesture/BezelGestureRecognizer.cpp#L148)),
   which depends on the TouchPad's touch report rate (not in source). Android reports at a
   different rate; convert to a velocity threshold after measuring on the TouchPad.
7. **Q7 — Launch animation look.** See §2.5; confirm by recording a launch on the device.
8. **Q8 — Dashboard row order and insertion animation** in menu (tablet) mode were not fully
   traced ([DashboardWindowContainer.cpp#L540-L640](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/notifications/DashboardWindowContainer.cpp#L540)).
9. **Q9 — Virtual keyboard geometry** (heights per size, candidate bar) was not analysed; Lunacy
   will use Android's IME, but the positive-space/keyboard interplay matters (§1.2).
10. **Q10 — Density: decided** 1 TouchPad px = 1 dp (Lunacy tablet = 600×960 dp). Open: whether
    Lunacy copies the TouchPad's "card scale computed once at first layout" quirk across
    rotation (§2.2).
11. **Q11 — Lock screen layout** (clock position, unlock target geometry, PIN pad) not covered.
12. **Q12 — resolved.** Prelude ships in Lunacy; file per element in §6.1. Remaining: the
    Medium-vs-Bold choice is inferred from font matching, so compare glyph weights against a
    device screenshot once.
13. **Q13 — `launcher-touch-feedback.png` path.** OverlayWindowManager loads it from the images root
    ([OverlayWindowManager.cpp#L1989](https://github.com/webOSArchive/LunaCE/blob/master/Src/lunaui/launcher/OverlayWindowManager.cpp#L1989)),
    where the repo has no such file; the device image set may differ.
14. **Q14 — Launcher grid row width.** The width passed to the icon layout was not traced, so the
    exact column spacing on a 1024-wide page is an estimate (§3.6); portrait column count too.
15. **Q15 — System menu internals** (row contents, sliders, Wi-Fi list) are QML files that were not
    traced beyond the frame; read `uiComponents/SystemMenu/*.qml` when building that menu.
16. **Q16 — Banner timing constants in stock.** Banner show times (2 s / 5 s) and 1 s
    animations are read from LunaCE; assumed stock but not checked against the binary.
17. **Q17 — Dock `backgroundOpacity`.** The fade animation exists but nothing paints with it
    (§3.2), and the device runs this code, so the dock should look the same with the launcher
    open. Confirm with a device screenshot.
18. **Q18 — Platform override files.** No `lunaAnimations-platform.conf`,
    `launcher3/*-platform.conf` or `launcher3/dynamicsSettings.conf` were among the copied
    device files. §0.3 and §3 assume they are absent; `ls /etc/palm /etc/palm/launcher3` on the
    device would confirm it.
