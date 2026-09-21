# Mojo

What was learned bringing Palm's Mojo up in Lunacy. It started on 2026-09-20, when an
Exhibition app from the App Museum (Flying Toasters 1.1.2) turned out to be a Mojo app and
ran, and went on through 2026-09-21 with the suite codepoet named as the apps that must work:
drPodder Redux, IAmA reddit, MeTube, Check Mate and SimpleChat, which brought in Palm's own
Video Player and with it **Mojo 2**. All six run; what each one turned up is in
[fix-log.md](fix-log.md).

Everything below was read from the reference TouchPad's own copy (webOS CE 3.1.0,
`/usr/palm/frameworks`, vendored at `Workbench/vendor/touchpad/`) or measured on the device.
Where something is a guess, it says so.

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
  copy: [LunaRuntimes/mojo/CHANGES.md](../LunaRuntimes/mojo/CHANGES.md).
- **Serves the rest of `/usr/palm/frameworks`** at their own paths (`mojo2`, `prototype`,
  `mojoloader.js`, `mojo.core`, the metascene and media frameworks), which is what a Mojo 2
  app and MojoLoader need. Only the frameworks something has asked for are copied.

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

## Self-closing tags

**`<script src="x.js" />` closes the element on webOS.** Chromium follows the standard, where
only void elements do, so the script opens and swallows the rest of the file as its (ignored)
text content. drPodder Redux's index.html is XHTML throughout, with its stylesheet, its other
scripts and its whole body after that tag; in Lunacy it loaded nothing at all, and the app was
a 4 x 4 blank window.

Measured rather than assumed, with a probe page in drPodder's shape
(`Workbench/probe/org.webosarchive.lunacy.htmlprobe`), on the reference TouchPad:

| | |
|---|---|
| `selfClosed` (did the script after it run?) | **true** |
| the body's own markup | present, `bodyKids=5` |
| the stylesheet after the tag | applied (`background-color: rgb(1, 2, 3)`) |
| `document.xmlVersion` | **null** |
| `document.compatMode` | `CSS1Compat` |

`xmlVersion` being null is the interesting one: this is an **HTML** document, not an XHTML one.
webOS's WebKit is old enough to keep the pre-HTML5 tokenizer, which honoured a trailing slash
on any tag. So Lunacy does the same, as a global serve-time transform
(`HtmlTransforms.selfClosingTags`): a self-closed non-void tag becomes an open and close pair.
It runs on every piece of HTML Lunacy serves, pages and templates alike, because the device's
parser read both; script and style content is skipped, so a string like `"<div/>"` in an app's
JavaScript is left alone.

## Reading a file is not loading a page

Mojo reads **every widget template and every scene** with `palmGetResource`. On a device that
call read the file off the disk and no browser was involved. In Lunacy it is an XHR against
the card host, which was injecting Lunacy's own boot scripts into anything it served as HTML -
so a template's rendered node began with the injected `<link>`, and every widget that looks
something up inside its own template found nothing:

```
Error: Caught exception in _Menu widget 'undefined' setup():
TypeError: Cannot read property 'parentNode' of null
```

`palmGetResource` now marks its request (`?__lunacy_res=1`) and the host serves the file as it
is. The parser fix above still applies, because that is the parser, not the page.

## Mojo 2

Palm's own **Video Player** (`com.palm.app.videoplayer`) is a Mojo 2 app, and MeTube hands
every video to it, so Mojo 2 had to work. It is packaged differently again:

- The page loads `/usr/palm/frameworks/mojo2/mojo.js`, which ignores `x-mojo-version` and
  takes only `x-mojo-submission`, defaulting to **205**.
- That loader `document.write`s `/usr/palm/frameworks/mojoloader.js`, and on load asks
  `MojoLoader.require({name: "mojo.core", version: "1.0"})` before the framework itself.
- Then it looks for `palmInitFramework2` + the submission - `palmInitFramework2205`, which is
  in Mojo 1's `builtins/` folder, beside 506.
- **MojoLoader** finds each library either as a builtin on the window
  (`palm<name>Version<v>`, dots to underscores) or by reading `manifest.json` under
  `/usr/palm/frameworks/<name>/version/<v>/` and evaluating `concatenated.js`. Lunacy serves
  the tree, so both routes work; the builtins win, as they did on the device.
- Prototype comes from `/usr/palm/frameworks/prototype/` through the app's own script tag, not
  from a builtin, so a Mojo 2 page gets only the four libraries and the framework in front of
  it.

What Lunacy added for it: a route for the rest of `/usr/palm/frameworks` (`fw/frameworks/` in
the assets), the frameworks themselves copied off the device by `fetch-assets.sh`, and
[patch 0002](../LunaRuntimes/mojo/CHANGES.md) for the V8 natives and the two `http://`
assumptions. `mojo-boot.js` already wrapped *every* `palmInitFramework*` global for the
argument order, so 2205 needed nothing there.

The submission mapping in `mojo.js` for Mojo 1 (`{"1": "506", "2": "344"}`) turns out to be a
red herring: a Mojo 2 app doesn't load `mojo/mojo.js` at all, it loads `mojo2/mojo.js`, which
has its own default. Submission 344 is still unaccounted for and nothing on the device has it.

## What a browser gets wrong about WebSQL

Both measured on the reference TouchPad (`Workbench/probe` 0.1.0):

| | TouchPad | Chromium |
|---|---|---|
| `openDatabase.length` | 0 | 4 |
| `openDatabase("x", "1.0")` | opens at version 1.0 | throws "4 arguments required, but only 2 present" |
| a failing statement's `error.message` | `no such table: nosuchtable` | `could not prepare statement (1 no such table: nosuchtable)` |

Both matter, and both stopped drPodder: the first at "Error Creating DB!", the second at
"Loading Feeds" for ever, because the app creates its tables when it sees SQLite's own message
and never saw it. The compat layer fills in the two missing arguments and strips the wrapper
back off the message.

## PalmSystem.runTextIndexer

webOS's linkifier, which Mojo hands every piece of user text through
(`Mojo.Format.runTextIndexer`). It was a logged no-op returning undefined, and SimpleChat -
which reads `.length` off what comes back - showed an empty chat log for ever.

Measured on the TouchPad (probe 0.1.2), ten shapes:

| in | out |
|---|---|
| `hello there` | unchanged |
| `call 555-1234` | `call <a href="tel:555-1234">555-1234</a>` |
| `www.example.com` | `<a href="http://www.example.com">www.example.com</a>` |
| `a@b.com` | `<a href="mailto:a@b.com">a@b.com</a>` |
| `(555) 123-4567`, `5551234567` | linked; the `tel:` href keeps the number exactly as written |
| `1234`, `100-200`, `2026-09-21` | unchanged - it is a telephone grammar, not a digit count |
| `a <b>bold</b> & 'quoted'` | unchanged; `&` is **not** escaped |
| `already <a href="http://x">linked</a>` | the href's own text is linkified again inside it |

That last row says what kind of thing it is: one naive pass over the whole string, markup and
all. Lunacy's does the same, in a single pass so it can't re-process what it just inserted.

## Still unknown

Honest list, for whoever picks this up:

- **Submission 344** is still unaccounted for: `mojo/mojo.js` maps `x-mojo-version="2"` to it
  and the device has neither the submission nor a builtin. Nothing has asked for it.
- **`palmcontactsVersion1_0`** is patched with the other builtins but has never been loaded.
- **Scene transitions.** `PalmSystem.prepareSceneTransition` and `runSceneTransition` are
  logged no-ops, so a scene change cuts rather than slides.
- **Full-screen cards.** `PalmSystem.enableFullScreenMode` is a logged no-op, so the Video
  Player's card keeps the status bar where a device hides it.
- **Multi-stage windows** map onto cards the way Enyo's `window.open` does; only the Video
  Player's own card has exercised that path.
- **The frameworks MojoLoader can reach** are only the ones something has needed so far.
  `mediaextension`, which drPodder asks for, still 404s.

## How to debug a Mojo app here

- The bus log names every call: `adb logcat -v brief Lunacy:V '*:S'` and look for
  `bus [<appid>] <service>/<method>` with `UNHANDLED` on the ones nobody answers.
- `Workbench/cdp.sh eval '<expr>' <appid>` reaches into the page. Note that a card is a
  *second* page: `index.html?…&window=card` holds the scenes and the assistants, while the
  plain `index.html` is the app's root window, and each has its own `Mojo`. In the card,
  `Mojo.Controller.stageController.topScene()` is the scene, and
  `topScene().getWidgetSetup("<id>")` shows a widget's attributes and model.
- `Mojo.Log.info` output only appears when the app's own logging is on
  (`framework_config.json`'s `logLevel`); absence of a log line proves nothing about whether
  the code ran.
- Mojo catches an exception in a widget's `setup()` and reports it as a string with no stack.
  If something fails in a way that makes no sense, look for a swallowed exception first.
