#!/usr/bin/env python3
"""Builds core/ui/src/main/res/raw/world_map.bin from Natural Earth 110m admin-0 countries (public domain).

Usage: build_world_map.py ne_110m_admin_0_countries.geojson out.bin [tolerance_deg]
Source: https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_110m_admin_0_countries.geojson

Format (big-endian): u16 country count; per country: 2 ASCII bytes ISO 3166-1 alpha-2, u16 ring count;
per ring: u16 point count, then points as u16 x = round((lon + 180) * 100), u16 y = round((90 - lat) * 100)
(equirectangular, 0.01 degree grid, x 0..36000, y 0..18000). Rings are Douglas-Peucker simplified.
"""
import json
import struct
import sys


def dp(points, tol):
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = points[a], points[b]
        dx, dy = bx - ax, by - ay
        norm = (dx * dx + dy * dy) ** 0.5 or 1e-12
        best, idx = 0.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm
            if d > best:
                best, idx = d, i
        if best > tol:
            keep[idx] = True
            stack += [(a, idx), (idx, b)]
    return [p for p, k in zip(points, keep) if k]


def simplify_ring(ring, tol):
    # A closed ring has a zero-length chord: split it at the point farthest from the start first.
    pts = ring[:-1]
    far = max(range(len(pts)), key=lambda i: (pts[i][0] - pts[0][0]) ** 2 + (pts[i][1] - pts[0][1]) ** 2)
    a, b = dp(pts[: far + 1], tol), dp(pts[far:] + [pts[0]], tol)
    return a + b[1:]


def iso2(props):
    for key in ("ISO_A2_EH", "ISO_A2", "WB_A2"):
        v = props.get(key)
        if v and v != "-99" and len(v) == 2:
            return v
    return None


def main():
    src, out = sys.argv[1], sys.argv[2]
    tol = float(sys.argv[3]) if len(sys.argv) > 3 else 0.35
    data = json.load(open(src))
    countries = {}
    for f in data["features"]:
        code = iso2(f["properties"])
        if not code:
            continue
        g = f["geometry"]
        polys = g["coordinates"] if g["type"] == "MultiPolygon" else [g["coordinates"]]
        rings = countries.setdefault(code, [])
        for poly in polys:
            ring = [(x, y) for x, y in (p[:2] for p in poly[0])]  # outer ring only
            s = simplify_ring(ring, tol)
            if len(s) >= 4:
                rings.append(s)
    buf = bytearray(struct.pack(">H", len([c for c, r in countries.items() if r])))
    for code, rings in sorted(countries.items()):
        if not rings:
            continue
        buf += code.encode("ascii") + struct.pack(">H", len(rings))
        for ring in rings:
            buf += struct.pack(">H", len(ring))
            for x, y in ring:
                buf += struct.pack(">HH", round((x + 180) * 100), round((90 - y) * 100))
    open(out, "wb").write(buf)
    print(f"{len(countries)} countries, {len(buf)} bytes")


main()
