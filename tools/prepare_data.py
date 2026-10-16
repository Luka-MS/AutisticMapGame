"""Builds res/mapgame/data/world.txt from Natural Earth (public domain) GeoJSON.

Usage: python tools/prepare_data.py [cache_dir]

Output format (tab separated, coordinates are lon lat pairs rounded to 2 decimals):
  C <name> <population>      start of a country, followed by R lines
  T <M|D|U>                  terrain region (mountains, desert, tundra), followed by R lines
  R x y x y ...              one polygon ring (holes are plain rings; fill rule is even-odd)
"""
import json
import math
import os
import sys
import urllib.request

BASE = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
FILES = ["ne_50m_admin_0_countries.geojson", "ne_50m_geography_regions_polys.geojson"]
EPS = 0.03
MIN_RING_EXTENT = 0.12


def fetch(cache):
    os.makedirs(cache, exist_ok=True)
    out = {}
    for name in FILES:
        path = os.path.join(cache, name)
        if not os.path.exists(path):
            print("downloading", name)
            urllib.request.urlretrieve(BASE + name, path)
        with open(path, encoding="utf-8") as f:
            out[name] = json.load(f)
    return out


def simplify(pts, eps):
    """Douglas-Peucker on an open polyline."""
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a]
        bx, by = pts[b]
        dx, dy = bx - ax, by - ay
        norm = math.hypot(dx, dy)
        best, idx = -1.0, -1
        for i in range(a + 1, b):
            px, py = pts[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm else math.hypot(px - ax, py - ay)
            if d > best:
                best, idx = d, i
        if best > eps:
            keep[idx] = True
            stack.append((a, idx))
            stack.append((idx, b))
    return [p for p, k in zip(pts, keep) if k]


def rings_of(geom):
    polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
    for poly in polys:
        for ring in poly:
            yield ring


def emit_rings(geom, lines):
    for ring in rings_of(geom):
        xs = [p[0] for p in ring]
        ys = [p[1] for p in ring]
        if max(xs) - min(xs) < MIN_RING_EXTENT and max(ys) - min(ys) < MIN_RING_EXTENT:
            continue
        pts = simplify([(p[0], p[1]) for p in ring[:-1]], EPS)
        if len(pts) < 3:
            continue
        lines.append("R\t" + " ".join("%.2f %.2f" % p for p in pts))


def main():
    cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join("tools", "cache")
    data = fetch(cache)
    lines = []
    for f in data[FILES[0]]["features"]:
        p = f["properties"]
        if p["NAME"] == "Antarctica":
            continue
        lines.append("C\t%s\t%d" % (p["NAME"], max(0, int(p.get("POP_EST") or 0))))
        emit_rings(f["geometry"], lines)
    kinds = {"Range/mtn": "M", "Desert": "D", "Tundra": "U"}
    for f in data[FILES[1]]["features"]:
        kind = kinds.get(f["properties"]["FEATURECLA"])
        if kind is None or f["geometry"] is None:
            continue
        lines.append("T\t" + kind)
        emit_rings(f["geometry"], lines)
    out = os.path.join("res", "mapgame", "data", "world.txt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    print("wrote", out, os.path.getsize(out), "bytes,", len(lines), "lines")


if __name__ == "__main__":
    main()
