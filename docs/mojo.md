# Mojo

What was learned bringing Palm's Mojo up in Lunacy on 2026-09-20, when an Exhibition app from
the App Museum (Flying Toasters 1.1.2) turned out to be a Mojo app and ran. Phase 5 is not
done — one app is not a framework — but the packaging is understood and written down here so
the next person doesn't have to work it out again.

Everything below was read from the reference TouchPad's own copy (webOS CE 3.1.0,
`/usr/palm/frameworks/mojo`, vendored at `spike/vendor/touchpad/mojo`) or measured on the
device. Where something is a guess, it says so.

## The one thing to know

**Mojo's code was part of webOS's browser, not part of the framework on disk.**

That single fact explains almost every difficulty. Enyo is a folder of JavaScript you can
serve; Mojo is a folder of *assets* plus a hook into a browser that no longer exists.

A page asks for Mojo like this:

```html
<script src="/usr/palm/frameworks/mojo/mojo.js" x-mojo-version="1"></script>
```

and `mojo.js` — which *is* on disk, and is only a loader — does this:

1. Reads its own script tag for `x-mojo-version` (or `x-mojo-submission`) and maps it to a
   submission number through `Mojo.Versions`, which on this copy is `{"1": "506", "2": "344"}`.
2. Looks for a global named `palmInitFramework506` and, if it is there, calls it. On a device
   that global came from WebKit: Palm compiled the framework into the browser.
3. If it isn't there, falls back to `document.write`-ing
   `submissions/506/javascripts/loader.js` — **a file webOS does not ship**. That failure is
   the message people see: *"The load of framework submission 506 failed. Perhaps it is not
   installed?"* It does not mean the submission is missing; it means the browser didn't have
   the framework built in.

What is actually on disk:

| Path | What |
|---|---|
| `mojo.js` | the loader above, ~200 lines |
| `submissions/506/` | the submission's **assets only**: `stylesheets/`, `images/`, `templates/`, `formats/`, `resources/`, `framework_config.json`. No `javascripts/`. |
| `builtins/` | the code, as the V8 **native scripts** the browser was compiled from |

`builtins/` holds `InstallPrototypeBuiltIn.js` (webOS's Prototype 1.6),
`palmInitFramework506.js` (the framework, ~810 KB), `palmInitFramework2205.js`, and
`palmmojo_core`, `palmfoundations`, `palmglobalization`, `palmunderscore`, `palmcontacts`
(loaded on demand; Lunacy hasn't needed them yet).

## What Lunacy does

- **Serves Palm's Mojo** at `/usr/palm/frameworks/mojo/…`, and `mojocommon` at its own path
  beside it — a separate framework the submission symlinks into for shared resources and
  images. `fetch-assets.sh` copies both **dereferenced** (`cp -rL`), because Gradle's asset
  merger fails on symlinks with `Cannot invoke DataFile.getItems() because dataFile is null`.
- **Injects the builtins in front of the app's own tag**, as a global serve-time transform
  (`AppServer.mojoBuiltins`): a page whose HTML loads `mojo/mojo.js` gets Prototype and the
  framework before it, with the submission taken from the tag the same way mojo.js reads it.
  No app is named; no app is changed.
- **Patches the builtins so a browser can load them**, as a patch series against the device's
  copy: [android/framework/mojo/CHANGES.md](../android/framework/mojo/CHANGES.md).

## The V8 natives, and the one that hurt

The builtins are written in V8's internal syntax. Four things needed ordinary meanings:

| V8 | In a page | Notes |
|---|---|---|
| `global` | `this` (the window) | V8's global object |
| `%SetProperty(global, "x", $x, flags)` | `global["x"] = $x` | registers a native on the global |
| `%ToFastProperties(x)`, `%FunctionSetPrototype(f, p)` | `(x)`, `f.prototype = p` | optimisation hints |
| `$Object`, `$Function`, `$Array`, … | the constructors themselves | V8's aliases |
| `builtinEval` | `(0, eval)` | V8's own eval — see below |

**`builtinEval` is the one to remember.** webOS's Prototype parses JSON with it:

```js
evalJSON: function (sanitize) {
    var json = this.unfilterJSON();
    try {
        if (!sanitize || json.isJSON()) return builtinEval('(' + json + ')');
    } catch (e) { }
    throw new $SyntaxError('Badly formed JSON string: ' + this.inspect());
}
```

Undefined, `builtinEval` throws a ReferenceError **into an empty catch**, and every
`evalJSON` in the framework then reports "Badly formed JSON string" about input that is
perfectly good JSON. Mojo's `convertLaunchParams` uses it, so an app's launch parameters
arrived as a raw string instead of an object — which is why Flying Toasters kept opening its
settings scene rather than its exhibition one, with nothing in the log to say why. If
something in Mojo fails in a way that makes no sense, look for a swallowed exception first.

## What Mojo assumes about its host

- **Device or desktop, nothing else.** `Mojo._calculateAppRootPath` matches the document URI
  against `file:///…` (a device) and then `http://…` (a desktop browser). Lunacy serves apps
  from `https://<appid>.media.cryptofs.apps`, which matched neither, so the match was `null`
  and the framework threw before it started. `Mojo.hostingPrefix` had the same assumption.
  Both accept `https` in Lunacy's fork.
- **Its device test was already right.** `Mojo.Host.current` is `palm-sys-mgr` when
  `window.palmGetResource` exists, which Lunacy's bridge provides. This matters: in the
  browser path Mojo *overwrites* `PalmSystem.launchParams` from a `mojoHostLaunchParams` query
  parameter, so an app would lose the parameters the shell passed.
- **`PalmSystem.stagePreparing()`** is called by mojo.js for submissions ≥ 135. Lunacy logs it
  as not implemented; nothing has needed it yet.
- **Resources are looked up by locale, then less specific**: an app's scene HTML is tried at
  `resources/en_us/…`, `resources/en/us/…`, `resources/en/…`, then the plain path. The 404s
  in the log on the way to the plain path are normal, not a fault.

## Services a Mojo app asks for while starting

Found by watching the bus log, which names every call and whether it was handled:

- `com.palm.systemservice/time/getSystemTime`, subscribed — implemented, in the shape measured
  on the device (`utc`, a `localtime` object, `offset` in minutes, `timezone`, `TZ`).
- `PalmSystem.enableFullScreenMode(false)` — logged, not implemented.

Mojo wraps its calls with an `$activity` parameter (`{"subscribe": true, "$activity":
{"activityId": 1}}`); the bus ignores it, as webOS's services did for a call that doesn't
need one.

## Still unknown

Honest list, for whoever picks this up:

- **Only one app has run.** Single stage, two scenes, a slider and a canvas. Multi-stage
  windows, scene transitions, the widget set, dashboards and banners from Mojo, and Mojo's own
  service wrappers are all untried.
- **Submission 2 is unresolved.** `mojo.js` maps `x-mojo-version="2"` to submission **344**,
  and the device has neither `submissions/344` nor a `palmInitFramework344`. It does have
  `palmInitFramework2205.js`, which is presumably a later Mojo 2.x build, but nothing has been
  tested against it. A v2 app will currently 404 on the builtin and fail. Worth checking on a
  device what a real Mojo 2 app loads.
- **The other builtins** (`foundations`, `globalization`, `underscore`, `contacts`,
  `mojo_core`) have never been loaded. They are V8 native scripts too and will need the same
  treatment when something asks for them.
- **Stage and window handling.** Mojo's multi-stage model maps onto cards the way Enyo's
  `window.open` does, but none of that path has been exercised.

## How to debug a Mojo app here

- The bus log names every call: `adb logcat -v brief Lunacy:V '*:S'` and look for
  `bus [<appid>] <service>/<method>` with `UNHANDLED` on the ones nobody answers.
- `spike/cdp.sh eval '<expr>' <appid>` reaches into the page. Useful ones:
  `Mojo.Host.current`, `Mojo.getLaunchParameters()`,
  `Mojo.Controller.getAppController().getActiveStageController().topScene().sceneName`.
- `Mojo.Log.info` output only appears when the app's own logging is on; absence of a log line
  proves nothing about whether the code ran.
