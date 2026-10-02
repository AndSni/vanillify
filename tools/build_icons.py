#!/usr/bin/env python3
"""Builds every Vanillify icon asset from the designer's two master drawings.

Inputs (Inkscape, same coordinates; the icon's 108-unit canvas is drawn at 0.9 scale):
  design/vanillify-icon-final.svg             colour icon: phone, camera dot, swoosh, flower on the gradient
  design/vanillify-icon-final_singlecolor.svg single-colour icon: the swoosh cuts the phone, camera as a hole
Outputs:
  design/icon/*.svg                                    master SVGs of every layer and the feature graphic
  app/src/main/res/drawable/ic_launcher_*.xml          adaptive icon: background, foreground, monochrome
  app/src/main/res/drawable/ic_notification.xml        status-bar icon
  metadata/en-US/images/icon.png, featureGraphic.png   store images (rendered with Inkscape)

Both drawings are scaled together into Android's safe zone, so the themed icon lines up with the
colour one. Needs skia-pathops and svgelements (pip), Inkscape, and the Noto Sans font for the banner.
Usage: tools/build_icons.py
"""
import math
import subprocess
from pathlib import Path as FsPath

import pathops
import svgelements as se

ROOT = FsPath(__file__).resolve().parent.parent
SRC_COLOUR = ROOT / "design" / "vanillify-icon-final.svg"
SRC_MONO = ROOT / "design" / "vanillify-icon-final_singlecolor.svg"
OUT_SVG = ROOT / "design" / "icon"
RES = ROOT / "app" / "src" / "main" / "res"
IMAGES = ROOT / "metadata" / "en-US" / "images"

# Brand colours. The drawings' own blue and gold were replaced by the chosen palette.
# Plum & orange: plum for privacy and independence, orange for warmth and energy.
BG_FROM, BG_TO = "#6A3FA0", "#2A1650"
PHONE, GOLD, CAMERA = "#FFFFFF", "#FF9F43", "#5E3792"

SAFE_RADIUS = 32.0  # first fit: art inside this circle around (54,54); Android's safe zone is 33
# Optical centring: the white phone outweighs the thin flower, so the geometric centre looks
# off to the left. The art's centre of mass is moved towards (54,54): fully across, half-way
# up (weight sitting a little low looks settled). Then it's scaled so its farthest point stays
# within MAX_RADIUS; thin petal tips may pass the 33 safe line but stay inside every mask (36).
OPTICAL_X, OPTICAL_Y = 1.0, 0.5
MAX_RADIUS = 34.0


def to_pathops(shape: se.Shape) -> pathops.Path:
    """Converts an svgelements shape (transforms already applied) into a pathops Path."""
    p = pathops.Path()
    for seg in se.Path(shape).segments():
        if isinstance(seg, se.Move):
            p.moveTo(seg.end.x, seg.end.y)
        elif isinstance(seg, se.Close):
            p.close()
        elif isinstance(seg, se.Line):
            p.lineTo(seg.end.x, seg.end.y)
        elif isinstance(seg, se.CubicBezier):
            p.cubicTo(seg.control1.x, seg.control1.y, seg.control2.x, seg.control2.y, seg.end.x, seg.end.y)
        elif isinstance(seg, se.QuadraticBezier):
            p.quadTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y)
        elif isinstance(seg, se.Arc):
            for c in seg.as_cubic_curves():
                p.cubicTo(c.control1.x, c.control1.y, c.control2.x, c.control2.y, c.end.x, c.end.y)
    return p


def path_d(p: pathops.Path, nd: int = 3) -> str:
    """SVG / VectorDrawable path data, with short numbers."""
    fmt = lambda v: f"{round(v, nd):g}"
    parts = []
    for verb, pts in p.segments:
        if verb == "moveTo":
            parts.append("M" + " ".join(f"{fmt(x)},{fmt(y)}" for x, y in pts))
        elif verb == "lineTo":
            parts.append("L" + " ".join(f"{fmt(x)},{fmt(y)}" for x, y in pts))
        elif verb == "curveTo":
            parts.append("C" + " ".join(f"{fmt(x)},{fmt(y)}" for x, y in pts))
        elif verb == "qCurveTo":
            # TrueType-style implied points: expand into plain quadratic segments.
            *offs, end = pts
            for i, q in enumerate(offs):
                nxt = end if i == len(offs) - 1 else ((q[0] + offs[i + 1][0]) / 2, (q[1] + offs[i + 1][1]) / 2)
                parts.append(f"Q{fmt(q[0])},{fmt(q[1])} {fmt(nxt[0])},{fmt(nxt[1])}")
        elif verb == "closePath":
            parts.append("Z")
    return "".join(parts)


def union(paths) -> pathops.Path:
    """One shape from several; overlaps merge, holes (camera, flower centre) stay holes."""
    out = pathops.Path()
    for p in paths:
        out = pathops.op(out, p, pathops.PathOp.UNION, fix_winding=True)
    return out


def sample_points(p: pathops.Path, n: int = 24):
    """Points along every segment, for fitting the art into the safe circle."""
    pts, cur = [], (0.0, 0.0)
    for verb, seg in p.segments:
        if verb in ("moveTo", "lineTo"):
            cur = seg[-1]
            pts.append(cur)
        elif verb == "curveTo":
            (x1, y1), (x2, y2), (x3, y3) = seg
            x0, y0 = cur
            for i in range(1, n + 1):
                t = i / n
                mt = 1 - t
                pts.append((mt**3 * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t**3 * x3,
                            mt**3 * y0 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t**3 * y3))
            cur = seg[-1]
        elif verb == "qCurveTo":
            pts.extend(seg)
            cur = seg[-1]
    return pts


def enclosing_circle(points):
    """Smallest circle around the points: shrink from the bounding-box centre."""
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    step = max(max(xs) - min(xs), max(ys) - min(ys)) / 4
    best = max(math.dist((cx, cy), p) for p in points)
    while step > 1e-4:
        improved = False
        for dx, dy in ((step, 0), (-step, 0), (0, step), (0, -step)):
            r = max(math.dist((cx + dx, cy + dy), p) for p in points)
            if r < best:
                best, cx, cy, improved = r, cx + dx, cy + dy, True
        if not improved:
            step /= 2
    return cx, cy, best


def centroid(p: pathops.Path):
    """Centre of mass of a filled path (Green's theorem over flattened contours; holes subtract)."""
    area = mx = my = 0.0
    for contour in p.contours:
        pts = sample_points(contour, 40)
        for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]):
            c = x0 * y1 - x1 * y0
            area += c
            mx += (x0 + x1) * c
            my += (y0 + y1) * c
    area /= 2
    return mx / (6 * area), my / (6 * area)


def shapes(svg: FsPath) -> dict:
    doc = se.SVG.parse(str(svg), reify=True)
    return {e.id: e for e in doc.elements() if isinstance(e, se.Shape)}


def inkscape_png(svg: FsPath, png: FsPath, *args: str) -> None:
    subprocess.run(["inkscape", str(svg), "--export-type=png", f"--export-filename={png}", *args],
                   check=True, capture_output=True)


def main() -> None:
    colour = shapes(SRC_COLOUR)
    # The colour drawing's background square is the 108-unit icon canvas drawn at 0.9 scale;
    # the single-colour drawing shares its coordinates (its flower is the same path).
    bg_rect = colour["rect73-9"]
    k = 108 / bg_rect.width
    to_canvas = (k, 0, 0, k, -bg_rect.x * k, -bg_rect.y * k)
    art = {name: to_pathops(colour[i]).transform(*to_canvas) for name, i in
           {"phone": "rect4", "camera": "path6", "flower": "path76-3", "swoosh": "path5"}.items()}

    mono_src = shapes(SRC_MONO)
    mono_parts = [to_pathops(e).transform(*to_canvas) for i, e in mono_src.items() if i != "rect73-9"]
    mono = union(mono_parts)

    # Fit both drawings into the safe circle with one transform, keeping shape and proportions.
    cx, cy, r = enclosing_circle([pt for p in [*art.values(), mono] for pt in sample_points(p)])
    s = SAFE_RADIUS / r
    fit = (s, 0, 0, s, 54 - cx * s, 54 - cy * s)
    art = {name: p.transform(*fit) for name, p in art.items()}
    mono = mono.transform(*fit)

    # Optical centring (see OPTICAL_X/Y): move the centre of mass towards the middle, then
    # scale about the middle so nothing passes MAX_RADIUS.
    mx, my = centroid(union([art["phone"], art["flower"], art["swoosh"]]))
    dx, dy = (54 - mx) * OPTICAL_X, (54 - my) * OPTICAL_Y
    moved = [(x + dx, y + dy) for p in [*art.values(), mono] for x, y in sample_points(p)]
    k = min(1.0, MAX_RADIUS / max(math.dist((54, 54), q) for q in moved))
    optical = (k, 0, 0, k, 54 * (1 - k) + k * dx, 54 * (1 - k) + k * dy)
    art = {name: p.transform(*optical) for name, p in art.items()}
    mono = mono.transform(*optical)
    print(f"artwork scaled ×{s * k:.3f}; centre of mass moved by ({dx:+.2f}, {dy:+.2f}) towards the middle")

    d = {name: path_d(p) for name, p in art.items()}
    dm = path_d(mono)

    # --- Master SVGs ---------------------------------------------------------------------
    OUT_SVG.mkdir(parents=True, exist_ok=True)
    head = '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
    grad = (f'<defs><linearGradient id="bg" x1="18" y1="18" x2="90" y2="90" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{BG_FROM}"/><stop offset="1" stop-color="{BG_TO}"/></linearGradient></defs>')
    fg_svg = (f'<path d="{d["phone"]}" fill="{PHONE}"/><path d="{d["camera"]}" fill="{CAMERA}"/>'
              f'<path d="{d["flower"]}" fill="{GOLD}"/><path d="{d["swoosh"]}" fill="{GOLD}"/>')
    files = {
        "background.svg": f'{head}{grad}<rect width="108" height="108" fill="url(#bg)"/></svg>\n',
        "foreground.svg": f"{head}{fg_svg}</svg>\n",
        "monochrome.svg": f'{head}<path d="{dm}" fill="#000000"/></svg>\n',
        "full-colour.svg": f'{head}{grad}<rect width="108" height="108" fill="url(#bg)"/>{fg_svg}</svg>\n',
    }

    # Notification icon: the single-colour art, cropped to its bounds with a little padding.
    x0, y0, x1, y1 = mono.bounds
    side = max(x1 - x0, y1 - y0) * 1.1
    notif = mono.transform(1, 0, 0, 1, side / 2 - (x0 + x1) / 2, side / 2 - (y0 + y1) / 2)
    vb = round(side, 3)
    files["notification.svg"] = (f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 {vb} {vb}">'
                                 f'<path d="{path_d(notif)}" fill="#000000"/></svg>\n')

    # Feature graphic: 1024×500 store banner, the icon art beside the name.
    files["feature-graphic.svg"] = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">'
        '<defs><linearGradient id="bg" x1="0" y1="0" x2="1024" y2="500" gradientUnits="userSpaceOnUse">'
        f'<stop offset="0" stop-color="{BG_FROM}"/><stop offset="1" stop-color="{BG_TO}"/></linearGradient></defs>'
        '<rect width="1024" height="500" fill="url(#bg)"/>'
        f'<g transform="translate(250 250) scale(5.4) translate(-54 -54)">{fg_svg}</g>'
        '<g font-family="Noto Sans, Roboto, sans-serif">'
        f'<text x="478" y="232" font-size="96" font-weight="700" fill="{PHONE}">Vanillify</text>'
        f'<text x="482" y="294" font-size="40" font-weight="600" fill="{GOLD}">Debloat &amp; Privacy</text>'
        '<text x="482" y="352" font-size="25" fill="#FFFFFF" fill-opacity="0.82">Your phone, clean and private.</text>'
        '<text x="482" y="386" font-size="25" fill="#FFFFFF" fill-opacity="0.82">No root. No tracking.</text>'
        '</g></svg>\n')
    for name, text in files.items():
        (OUT_SVG / name).write_text(text)

    # --- Android vector drawables ----------------------------------------------------------
    def vd(body: str, w=108, h=108, vw=108, vh=108) -> str:
        return ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- Generated by tools/build_icons.py from design/vanillify-icon-final*.svg. Edit the design, not this file. -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    xmlns:aapt="http://schemas.android.com/aapt"\n'
                f'    android:width="{w}dp"\n    android:height="{h}dp"\n'
                f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n{body}</vector>\n')

    def path(data: str, color: str) -> str:
        return f'    <path\n        android:fillColor="{color}"\n        android:pathData="{data}" />\n'

    draw = RES / "drawable"
    (draw / "ic_launcher_background.xml").write_text(vd(
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        '            <gradient android:type="linear" android:startX="18" android:startY="18" android:endX="90" android:endY="90">\n'
        f'                <item android:offset="0" android:color="{BG_FROM}" />\n'
        f'                <item android:offset="1" android:color="{BG_TO}" />\n'
        '            </gradient>\n        </aapt:attr>\n    </path>\n'))
    (draw / "ic_launcher_foreground.xml").write_text(vd(
        path(d["phone"], PHONE) + path(d["camera"], CAMERA) + path(d["flower"], GOLD) + path(d["swoosh"], GOLD)))
    (draw / "ic_launcher_monochrome.xml").write_text(vd(path(dm, "#FF000000")))
    (draw / "ic_notification.xml").write_text(vd(path(path_d(notif), "#FFFFFFFF"), 24, 24, vb, vb))

    # --- Store images ------------------------------------------------------------------------
    IMAGES.mkdir(parents=True, exist_ok=True)
    # Store icon: the 72-unit area a launcher shows, full square (the store rounds the corners).
    inkscape_png(OUT_SVG / "full-colour.svg", IMAGES / "icon.png",
                 "--export-area=18:18:90:90", "--export-width=512", "--export-height=512")
    inkscape_png(OUT_SVG / "feature-graphic.svg", IMAGES / "featureGraphic.png",
                 "--export-width=1024", "--export-height=500")
    print("wrote", ", ".join(sorted(files)), "+ 4 drawables, icon.png, featureGraphic.png")


if __name__ == "__main__":
    main()
