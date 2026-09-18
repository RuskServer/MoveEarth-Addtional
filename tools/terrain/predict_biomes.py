#!/usr/bin/env python3
"""Predict the biome mix of a tile without generating a world.

The loop of building a tile, running a server, pulling a save and counting
biomes takes long enough that climate tuning was proceeding almost blind. This
runs vanilla's own selection against the tile's climate layers instead, so the
mix can be read in seconds.

The parameter table is dumped from the real `OverworldBiomeBuilder` rather than
transcribed; see tools/terrain/README.md.

    python3 tools/terrain/predict_biomes.py run/config/moveearth_terrain/tile_0_0
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from terraingen.noise import fbm  # noqa: E402

QUANTISE = 10000.0
# TerrainField.RIVER_BIOME_RADIUS, in blocks
RIVER_BIOME_RADIUS = 14.0
PARAMS = ("temperature", "humidity", "continentalness", "erosion", "depth", "weirdness")


def load_table(path: pathlib.Path):
    rows = json.loads(path.read_text())
    names = [r["biome"] for r in rows]
    lo = np.array([[r[p][0] for p in PARAMS] for r in rows], dtype=np.int64)
    hi = np.array([[r[p][1] for p in PARAMS] for r in rows], dtype=np.int64)
    offset = np.array([r["offset"] for r in rows], dtype=np.int64)
    return names, lo, hi, offset


def pick(points: np.ndarray, lo, hi, offset, chunk=512):
    """Vanilla's fitness: squared distance to each box, plus the box's offset.

    Accumulating one parameter at a time rather than building an (n, boxes, 6)
    array keeps this within cache. The table holds 7593 boxes, so the full array
    runs to gigabytes per chunk and the run spends its time in the allocator.
    """
    out = np.empty(points.shape[0], dtype=np.int64)
    base = (offset.astype(np.float32) ** 2)[None, :]
    lo32 = lo.astype(np.float32)
    hi32 = hi.astype(np.float32)
    for start in range(0, points.shape[0], chunk):
        block = points[start:start + chunk].astype(np.float32)
        fitness = np.repeat(base, block.shape[0], axis=0)
        for p in range(block.shape[1]):
            column = block[:, p][:, None]
            d = np.maximum(column - hi32[None, :, p], lo32[None, :, p] - column)
            np.maximum(d, 0.0, out=d)
            fitness += d * d
        out[start:start + chunk] = np.argmin(fitness, axis=1)
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tile")
    ap.add_argument("--table", default="build/terrain/biome_table.json")
    ap.add_argument("--top", type=int, default=25)
    ap.add_argument("--seed", type=int, default=7,
                    help="seed for the stand-in weirdness noise")
    ap.add_argument("--stride", type=int, default=2,
                    help="sample every Nth cell; a mix does not need every one")
    args = ap.parse_args(argv)

    tile = pathlib.Path(args.tile)
    meta = json.loads((tile / "tile.json").read_text())
    n = meta["size_cells"]
    sea = meta["sea_y"]

    def layer(name, dtype):
        return np.frombuffer((tile / f"{name}.bin").read_bytes(), dtype=dtype).reshape(n, n)

    height = layer("height", "<i2").astype(np.float64)
    land = height > sea
    temperature = layer("temperature", "i1") / 127.0
    humidity = layer("humidity", "i1") / 127.0
    erosion = layer("erosion", "i1") / 127.0
    continentalness = layer("continentalness", "i1") / 127.0

    # Weirdness is vanilla's own ridge noise gated by our channels. The tile does
    # not carry the noise, and for a distribution it does not need to: an
    # independent noise of the same shape gives the same statistics. The gate is
    # not optional, though -- it is what puts river biomes along the channels,
    # and leaving it out is the difference between a plausible mix and one with
    # no rivers in it at all. Same curve as TerrainField.RIVER_GATE.
    rng = np.random.default_rng(args.seed)
    ridge = np.clip(fbm(rng, n, 4, base_freq=6) * 2.2, -1.0, 1.0)
    river_dist = layer("river_dist", "u1").astype(np.float64)
    t = np.clip(river_dist / RIVER_BIOME_RADIUS, 0.0, 1.0)
    weirdness = ridge * (t * t * (3.0 - 2.0 * t))

    # Surface biomes are sampled where depth is ~0.
    depth = np.zeros_like(height)

    step = max(1, args.stride)
    view = (slice(None, None, step), slice(None, None, step))
    stack = np.stack([temperature[view], humidity[view], continentalness[view],
                      erosion[view], depth[view], weirdness[view]], axis=-1)
    shape = stack.shape[:2]
    points = np.rint(stack.reshape(-1, 6) * QUANTISE).astype(np.int64)

    names, lo, hi, offset = load_table(pathlib.Path(args.table))
    chosen = pick(points, lo, hi, offset).reshape(shape)
    land = land[view]

    print(f"tile {tile}  {n}x{n} cells, {meta['blocks_per_cell']} blocks/cell, "
          f"every {step} cell(s)")
    for label, mask in (("all", np.ones_like(land)), ("land", land)):
        counts = {}
        for index in np.unique(chosen[mask]):
            counts[names[index]] = counts.get(names[index], 0) + int((chosen[mask] == index).sum())
        total = sum(counts.values())
        print(f"\n=== {label} ({total} cells, {len(counts)} biomes) ===")
        for name, count in sorted(counts.items(), key=lambda kv: -kv[1])[:args.top]:
            print("  %6.2f%%  %s" % (100 * count / total, name))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
