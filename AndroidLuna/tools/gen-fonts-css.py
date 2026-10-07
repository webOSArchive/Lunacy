#!/usr/bin/env python3
"""Generates src/main/assets/lunacy/fonts.css: @font-face rules that give every page the Prelude
family names webOS had installed as system fonts, resolved as the reference TouchPad resolves
them. Re-run when fonts change.

Measured on the reference TouchPad (webOS CE 3.1.0) with Workbench/probe's font probe
(fontprobe.sh), 2026-09-26: each name at weights normal, 300, bold and 900, identified by
its text widths against each font file's. webOS's fontconfig resolves a name in one of three
ways, and this file does the same:

1. A face's full name - "Prelude Medium", "PreludeWGL Light", "PreludeWGL-Black",
   "PreludeCondensedWGL Bold" - draws that face at every weight: bold does not switch to
   the Bold face, and nothing is emboldened. (Palm's Calculator asks for "Prelude Medium" in
   bold and gets Medium.)
2. A family name draws its Bold face at bold and 900, and at normal and 300 the face
   fontconfig ranks first: Medium for Prelude and PreludeCondensedWGL, but Black for
   PreludeWGL and PreludeCompressedWGL.
3. A name the device doesn't have draws Prelude: Medium, and Bold at bold. That includes the
   file names PreludeCondWGL and PreludeCompWGL, and "Prelude Condensed" and PreludeCondensed,
   although that is the Condensed files' own family name: the device never draws them.
   Apps' CSS names these, so they get Prelude here as there. (sans-serif, serif and
   Helvetica are Prelude on the device too, but a CSS rule can't redefine those: they stay
   the WebView's own.)

Italic was not measured; each face's italic follows its upright one."""
import os
HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "../src/main/assets/luna/fonts")
OUT = os.path.join(HERE, "../src/main/assets/lunacy/fonts.css")
have = set(os.listdir(FONTS))
# One origin for every page's fonts: Chromium checks and decodes a web font (its sanitizer, about
# 150 ms a face on the HP 10 G2) once per URL and keeps the result in its memory cache, which
# all cards share. Served from each app's own origin, every app paid for it again.
BASE = "https://lunacy-fonts.media.cryptofs.apps/__lunacy/fonts/"
rules = []

def face(family, weight, style, f):
    if f not in have:
        raise SystemExit(f"missing font file {f}")
    rules.append(f'@font-face{{font-family:"{family}";font-weight:{weight};font-style:{style};src:url("{BASE}{f}")}}')

# (file prefix, italic suffix, family name, faces, the face a family name draws at normal)
FAMILIES = [
    ("Prelude", "Oblique", "Prelude", ["Medium", "Bold"], "Medium"),
    ("PreludeWGL", "Italic", "PreludeWGL", ["Light", "Medium", "Bold", "Black"], "Black"),
    ("PreludeCondWGL", "Italic", "PreludeCondensedWGL", ["Light", "Medium", "Bold", "Black"], "Medium"),
    ("PreludeCompWGL", "Italic", "PreludeCompressedWGL", ["Light", "Medium", "Bold", "Black"], "Black"),
]

for prefix, italic, family, faces, regular in FAMILIES:
    # 2. The family name: normal (and anything lighter) and bold (and anything heavier).
    for weight, n in ((400, regular), (700, "Bold")):
        face(family, weight, "normal", f"{prefix}-{n}.ttf")
        face(family, weight, "italic", f"{prefix}-{n}{italic}.ttf")
    # A semibold 600 draws Prelude's Medium on the device, where Chromium would pick the Bold
    # face: Fastmail's headings and <strong> (font-weight:600) came out Medium on the reference
    # TouchPad (2026-10-07, Email's message view). Measured for Prelude only.
    if family == "Prelude":
        face(family, 600, "normal", f"{prefix}-{regular}.ttf")
        face(family, 600, "italic", f"{prefix}-{regular}{italic}.ttf")
    # 1. Each face's full name, with a space or a hyphen, pins that face at every weight.
    #    The bold rule names the same file, so the WebView doesn't embolden it either.
    for n in faces:
        for alias in (f"{family} {n}", f"{family}-{n}"):
            for weight in ("normal", "bold"):
                face(alias, weight, "normal", f"{prefix}-{n}.ttf")
                face(alias, weight, "italic", f"{prefix}-{n}{italic}.ttf")

# 3. Names the device hasn't got, which it draws as its default, Prelude.
UNKNOWN = ["PreludeCondWGL", "PreludeCompWGL", "Prelude Condensed", "PreludeCondensed"]
UNKNOWN += [f"{fam}{sep}{n}" for fam in ("PreludeCondWGL", "PreludeCompWGL") for sep in (" ", "-")
            for n in ("Light", "Medium", "Bold", "Black")]
UNKNOWN += ["Prelude Condensed Medium", "Prelude Condensed Bold", "Prelude-CondensedMedium", "Prelude-CondensedBold"]
for alias in UNKNOWN:
    for weight, n in ((400, "Medium"), (600, "Medium"), (700, "Bold")):
        face(alias, weight, "normal", f"Prelude-{n}.ttf")
        face(alias, weight, "italic", f"Prelude-{n}Oblique.ttf")

# Apps that quoted a whole fallback list as one family name, e.g.
# font-family: "PreludeWGL Light, Arial, Helvetica, sans-serif". webOS still resolved these.
for n in ("Light", "Bold"):
    face(f"PreludeWGL {n}, Arial, Helvetica, sans-serif", "normal", "normal", f"PreludeWGL-{n}.ttf")

with open(OUT, "w") as o:
    o.write("/* Generated by tools/gen-fonts-css.py. The Prelude fonts, as the reference TouchPad resolves their names. */\n")
    o.write("\n".join(rules) + "\n")
    # webOS's default font was Prelude; app CSS that names its own font still wins.
    o.write("html{font-family:Prelude,sans-serif}\n")
print(len(rules), "rules")
