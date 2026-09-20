# Lunacy's changes to Enyo 1.0

Upstream: [enyojs/enyo-1.0](https://github.com/enyojs/enyo-1.0), tag `r1` (Enyo 1.0 submission
128, as shipped on HP webOS 3.0.5). Apache 2.0.

Lunacy serves this framework at `/usr/palm/frameworks/enyo/…`, where apps load it from the OS,
so a fix here reaches every app built on it - which is the point (rule 1 in
[CLAUDE.md](../../../CLAUDE.md)). The fork is **stock Enyo plus the patches in `patches/`**:
`fetch-assets.sh` copies the upstream clone and applies them in order, and fails if one
doesn't apply. That keeps every change to the framework readable as a diff against upstream,
which is what rule 4 asks for.

Enyo is served from `framework/build/enyo-build.js` (its non-debug path), so a change has to
be made in the built file as well as in `framework/source/`. Each patch does both.

**Adding one:** edit `android/local-assets/fw/enyo/1.0/`, then

```sh
cd android/local-assets/fw/enyo/1.0
for f in <the files you changed>; do
    diff -u ../../../../../spike/vendor/enyo-1.0/$f $f | sed "s|^--- .*|--- a/$f|; s|^+++ .*|+++ b/$f|"
done > ../../../framework/enyo-1.0/patches/000N-name.patch
```

and add an entry below.

## Patches

### 0001-screen-orientation-changed.patch

`Mojo.screenOrientationChanged` raises Enyo's own orientation change.

Enyo watches `window.resize` to decide the window has rotated, and leaves the host's
`Mojo.screenOrientationChanged` an empty stub - its own note says why: the callback wasn't
made on one of Palm's devices, and the stub exists only so sysmgr doesn't error on the ones
where it is. Turning a tablet end for end changes the orientation while the window keeps its
size, so no resize is fired and an app never hears about it.

Lunacy's shell does make that call, so the stub now feeds `enyo.sendOrientationChange()`, the
same function the resize path uses. It only dispatches `windowRotated` when the orientation
really changed, so the two paths can't double up.

Files: `framework/source/compatibility/webosGesture.js`,
`framework/build/enyo-build.js`.
