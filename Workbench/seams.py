#!/usr/bin/env python3
"""Find border-image seams in a card, by measuring rather than looking.

A nine-slice frame is drawn as nine pieces. Where two pieces meet, Chromium here
sometimes leaves a one-pixel discontinuity - a row or column that breaks an
otherwise smooth ramp. That is the seam. This reads the widgets' own geometry
out of the page (Workbench/cdp.sh), then checks each slice boundary in a
screenshot for a spike against its immediate neighbours.

  seams.py <screenshot.png> <widgets.json> [--status-bar N] [--min N]

widgets.json is what cdp.sh returns for the query in seams.sh: one entry per
element with a border image, carrying its rect and border widths in CSS px.
On the HP 10 G2 one CSS px is one device px, so the only offset is the shell's
status bar.
"""
import json
import sys
from PIL import Image


def spike(values, i, threshold):
    """values[i] against its neighbours: a one-pixel step both ways is a seam."""
    a, b, c = values[i - 1], values[i], values[i + 1]
    if (b - a) * (b - c) <= 0:
        return None
    step = min(abs(b - a), abs(b - c))
    return step if step >= threshold else None


def grey(im, x, y):
    p = im.getpixel((x, y))
    return (p[0] + p[1] + p[2]) / 3.0


def scan_column(im, x, y0, y1, boundaries, threshold):
    if x < 1 or x >= im.size[0] - 1:
        return []
    col = [grey(im, x, y) for y in range(y0, y1)]
    found = []
    for b in boundaries:
        i = b - y0
        # A piece's edge can land either side of the nominal boundary.
        for j in (i - 1, i, i + 1):
            if 1 <= j < len(col) - 1:
                s = spike(col, j, threshold)
                if s:
                    found.append(("row", y0 + j, round(s, 1)))
                    break
    return found


def scan_row(im, y, x0, x1, boundaries, threshold):
    if y < 1 or y >= im.size[1] - 1:
        return []
    row = [grey(im, x, y) for x in range(x0, x1)]
    found = []
    for b in boundaries:
        i = b - x0
        for j in (i - 1, i, i + 1):
            if 1 <= j < len(row) - 1:
                s = spike(row, j, threshold)
                if s:
                    found.append(("col", x0 + j, round(s, 1)))
                    break
    return found


def main():
    shot, widgets = sys.argv[1], sys.argv[2]
    offset = 0
    threshold = 5.0
    args = sys.argv[3:]
    for i, a in enumerate(args):
        if a == "--status-bar":
            offset = int(args[i + 1])
        if a == "--min":
            threshold = float(args[i + 1])
    im = Image.open(shot).convert("RGB")
    W, H = im.size
    for w in json.load(open(widgets)):
        left, top, width, height = (int(round(v)) for v in w["r"])
        top += offset
        bw = [int(round(v)) for v in w["bw"]]          # top right bottom left
        if width < 8 or height < 8:
            continue
        right, bottom = left + width, top + height
        # The horizontal joins are bw[0] below the top and bw[2] above the
        # bottom; the vertical ones bw[3] right of the left edge and bw[1] left
        # of the right. Sample away from the corners, and away from any text.
        rows = [top + bw[0], bottom - bw[2]]
        cols = [left + bw[3], right - bw[1]]
        hits = []
        for x in (left + max(3, bw[3] // 2), left + width // 4, right - max(3, bw[1] // 2)):
            if 0 < x < W:
                hits += scan_column(im, x, max(0, top - 2), min(H, bottom + 2), rows, threshold)
        for y in (top + max(3, bw[0] // 2), top + height // 2, bottom - max(3, bw[2] // 2)):
            if 0 < y < H:
                hits += scan_row(im, y, max(0, left - 2), min(W, right + 2), cols, threshold)
        if hits:
            seen, uniq = set(), []
            for h in hits:
                if h[:2] not in seen:
                    seen.add(h[:2])
                    uniq.append(h)
            print("%-34s %4dx%-4d at %4d,%-4d  border %s" %
                  (w["cls"][:34] or "(no class)", width, height, left, top - offset, bw))
            for kind, at, step in sorted(uniq):
                print("      seam: %s %d, step %s" % (kind, at, step))


if __name__ == "__main__":
    main()
