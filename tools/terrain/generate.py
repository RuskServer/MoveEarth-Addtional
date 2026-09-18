#!/usr/bin/env python3
"""Generate one MoveEarth terrain tile and its verification images.

Phase 1 of terrain_generation_plan.md. Nothing here touches Minecraft: the
output is a tile asset plus PNGs, and the plan is judged on those PNGs before
any game-side work starts.

    python3 tools/terrain/generate.py --size 1024 --out build/terrain/tile_0_0
"""
from __future__ import annotations

import argparse
import pathlib
import sys
import time

import numpy as np

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from terraingen import render, rivers, tiles             # noqa: E402
from terraingen.climate import erosion as erosion_layer, humidity, temperature, wind_is_eastward  # noqa: E402
from terraingen import climate_bands                          # noqa: E402
from terraingen.distance import signed_coast_distance         # noqa: E402
from terraingen.config import Config                      # noqa: E402
from terraingen.erosion import run_erosion                # noqa: E402
from terraingen.grid import connected_components, find_roots, neighbours  # noqa: E402
from terraingen.noise import box_blur                        # noqa: E402
from terraingen.hypsometry import remap as hypsometric_remap  # noqa: E402
from terraingen.regions import basins, continents, merge_to_target, spawn_anchors  # noqa: E402
from terraingen.tectonics import apply_ocean_margin, taper_to_margin, tectonic_height  # noqa: E402


def _coerce(text: str):
    for cast in (int, float):
        try:
            return cast(text)
        except ValueError:
            pass
    if text.lower() in ("true", "false"):
        return text.lower() == "true"
    return text


def parse_args(argv=None):
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--size", type=int)
    p.add_argument("--seed", type=int)
    p.add_argument("--blocks-per-cell", type=int, dest="blocks_per_cell")
    p.add_argument("--erosion-steps", type=int, dest="erosion_steps")
    p.add_argument("--origin", type=int, nargs=2, default=(0, 0), metavar=("X", "Z"))
    p.add_argument("--out", default="build/terrain/tile_0_0")
    p.add_argument("--set", action="append", default=[], metavar="KEY=VALUE",
                   help="override any Config field")
    a = p.parse_args(argv)
    overrides = {k: v for k, v in vars(a).items()
                 if k in Config.__dataclass_fields__ and v is not None}
    for item in a.set:
        key, _, value = item.partition("=")
        overrides[key.strip()] = _coerce(value.strip())
    return a, Config.from_overrides(**overrides)


def build_rivers(cfg: Config, acc: np.ndarray, land: np.ndarray, z: np.ndarray, recv: np.ndarray):
    """Returns the channel width per cell, in blocks; zero away from water."""
    """Channels are cells that both carry enough flow and sit in a hollow.

    Discharge alone is not enough. On a planar slope D8 sends every cell's flow
    to the same neighbour, so accumulation builds up along parallel straight
    lines that are not valleys and should not be drawn as rivers.
    """
    a = acc.reshape(land.shape)
    radius = max(1, int(round(cfg.river_hollow_cells)))
    hollow = z - box_blur(z, radius, passes=2)

    # A real channel carries far more than the ground either side of it. Under
    # parallel flow on a plane every line accumulates at the same rate, so a cell
    # and its lateral neighbours come out nearly equal and the test rejects them.
    # Concavity alone is not enough: on a plane `z - blur(z)` hovers around zero
    # and roughly half the cells pass it by chance.
    banks = np.median(neighbours(a, 0.0), axis=0)
    concentrated = a > cfg.river_contrast * np.maximum(banks, 1e-12)

    seeds = land & (a >= cfg.river_threshold) & (hollow < 0.0) & concentrated
    wet = rivers.connect_downstream(seeds, land, recv)
    return rivers.channel_width(cfg, a, wet)


def elongation(z: np.ndarray, land: np.ndarray) -> float:
    """Area-weighted mean elongation of high ground. Belts score high, blobs ~1."""
    if not land.any():
        return 0.0
    cut = np.quantile(z[land], 0.85)
    labels = connected_components(land & (z >= cut))
    total = weighted = 0.0
    yy, xx = np.mgrid[0:z.shape[0], 0:z.shape[1]]
    for lab in range(1, int(labels.max()) + 1):
        m = labels == lab
        n = int(m.sum())
        if n < 24:
            continue
        y, x = yy[m].astype(float), xx[m].astype(float)
        cov = np.cov(np.stack([y - y.mean(), x - x.mean()]))
        ev = np.linalg.eigvalsh(cov)
        ev = np.clip(ev, 1e-9, None)
        weighted += n * float(np.sqrt(ev[1] / ev[0]))
        total += n
    return weighted / total if total else 0.0


def metrics(cfg: Config, z, sea, land, cont, reg, river, hum, recv) -> dict:
    shape = land.shape
    ocean = ~land
    # "shelf" means shallow water in world terms, so measure it in blocks rather
    # than in simulation units, which drift with resolution and tuning
    world_y = (cfg.sea_y - (cfg.sea_y - cfg.min_y)
               * np.clip((sea - z) / max(sea - float(z.min()), 1e-9), 0.0, 1.0))
    shelf = ocean & (world_y >= cfg.sea_y - cfg.shelf_depth_blocks)

    river_flat = river.ravel() > 0.0
    # A dry intermediate cell terminates the rendered river, even if the full
    # drainage graph would eventually reach the sea.
    drawn_recv = np.where(river_flat, recv, np.arange(recv.size))
    roots = find_roots(drawn_recv)
    reaches = land.ravel()[roots] == False
    river_ok = float(reaches[river_flat].mean()) if river_flat.any() else 0.0

    # rain shadow: compare leeward humidity against windward, using the running
    # maximum elevation the air has already had to climb. The sweep direction has
    # to follow the same per-row wind the climate model used, otherwise the trade
    # wind band is measured backwards and the shadow averages away.
    terrain = np.where(land, z, sea)
    east = np.maximum.accumulate(terrain, axis=1)
    west = np.maximum.accumulate(terrain[:, ::-1], axis=1)[:, ::-1]
    eastward = wind_is_eastward(cfg)[:, None]
    barrier = np.where(eastward, east, west) - sea
    hi_cut = np.quantile(barrier[land], 0.75) if land.any() else 0.0
    lee = land & (barrier >= hi_cut)
    wind = land & (barrier < hi_cut)
    shadow = (float(hum[lee].mean()) / float(hum[wind].mean())) if lee.any() and wind.any() else 1.0

    sizes = np.bincount(cont.ravel())
    biggest = int(sizes[1:].max()) if sizes.size > 1 else 0
    span = 0
    if biggest:
        top = int(np.argmax(sizes[1:])) + 1
        ys, xs = np.nonzero(cont == top)
        span = int(max(ys.max() - ys.min(), xs.max() - xs.min()) * cfg.blocks_per_cell)

    return {
        "land_fraction": round(float(land.mean()), 4),
        "continents": int(cont.max()),
        "regions": int(reg.max()),
        "largest_continent_blocks": int(biggest * cfg.cell_area_blocks),
        "largest_continent_span_blocks": span,
        "shelf_share_of_ocean": round(float(shelf.sum() / max(ocean.sum(), 1)), 4),
        "ridge_elongation": round(elongation(z, land), 3),
        "river_cells": int(river_flat.sum()),
        "river_reaches_sea": round(river_ok, 4),
        "rain_shadow_ratio": round(float(shadow), 3),
    }


def main(argv=None) -> int:
    args, cfg = parse_args(argv)
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    started = time.time()

    def log(msg):
        print(f"[{time.time() - started:7.1f}s] {msg}", flush=True)

    rng = np.random.default_rng(cfg.seed)
    log(f"tectonics  size={cfg.size} ({cfg.tile_blocks} blocks, {cfg.blocks_per_cell} b/cell)")
    z, plates, _continental, uplift, hardness = tectonic_height(cfg, rng)
    margin_seed = int(rng.integers(0, 2 ** 62))
    z = apply_ocean_margin(cfg, z, np.random.default_rng(margin_seed))
    uplift = taper_to_margin(cfg, uplift, np.random.default_rng(margin_seed))
    uplift = np.maximum(uplift, 0.0)

    # Sea level is chosen as a quantile, so the land:sea requirement is met by
    # construction instead of by tuning noise amplitudes.
    sea = float(np.quantile(z, 1.0 - cfg.land_fraction))

    log(f"erosion    {cfg.erosion_steps} steps")
    z, recv, _dist, _outlet, acc = run_erosion(
        cfg, z, uplift, sea, rng, hardness,
        progress=lambda i, n: log(f"  erosion {i}/{n}"))
    sea = float(np.quantile(z, 1.0 - cfg.land_fraction))

    if cfg.apply_hypsometry:
        log("hypsometry")
        z = hypsometric_remap(z, sea)

    log("rivers")
    land = z > sea
    river = build_rivers(cfg, acc, land, z, recv)
    world_y = tiles.height_to_world(cfg, z, sea)
    river_water = rivers.water_surface(cfg, world_y, river, cfg.sea_y, recv)
    river_dist, river_width, river_water_y = rivers.distance_field(cfg, river, river_water)

    log("regions")
    cont = continents(cfg, land)
    land = cont > 0
    reg = merge_to_target(cfg, basins(land, recv), cont)

    log("climate")
    temp = temperature(cfg, z, sea)
    hum = humidity(cfg, z, sea)
    ero_raw = erosion_layer(cfg, z, sea)
    roughness = np.clip((1.0 - ero_raw) * 0.5, 0.0, 1.0)

    # Re-shape the climate onto the distributions vanilla's biome table expects.
    # Ranking is monotonic, so the bands and rain shadows keep their pattern and
    # only the spacing changes.
    hum_raw = hum  # the rain shadow metric needs a non-negative scale
    # Erosion needs the same treatment as the other two, and for a sharper
    # reason: vanilla hands every inland cell in the top erosion band to swamp
    # whatever its humidity. Ocean keeps the raw value; its biomes do not use it.
    ero = np.where(land, climate_bands.rank_to_curve(ero_raw, land,
                                                     climate_bands.EROSION_CURVE), ero_raw)
    temp = climate_bands.rank_to_curve(temp, land, climate_bands.TEMPERATURE_CURVE)
    hum = climate_bands.rank_to_curve(hum, land, climate_bands.VEGETATION_CURVE, fill=0.55)
    coast = signed_coast_distance(land, float(cfg.blocks_per_cell))
    anchors = spawn_anchors(cfg, cont, coast, world_y, args.origin[0], args.origin[1])
    cont_param = climate_bands.continentalness(coast)

    log("images")
    exaggeration = cfg.size * cfg.hillshade_exaggeration
    render.relief(z, sea, river, exaggeration).save(out / "relief.png")
    render.relief(z, sea, None, exaggeration).save(out / "relief_norivers.png")
    render.categorical(cont).save(out / "continents.png")
    render.categorical(reg, seed=19).save(out / "regions.png")
    render.categorical(plates + 1, seed=3).save(out / "plates.png")
    render.scalar(temp).save(out / "temperature.png")
    render.scalar(hum, stops=[(0.0, (120, 96, 40)), (0.5, (110, 170, 90)), (1.0, (30, 90, 170))]).save(out / "humidity.png")
    render.scalar(np.log1p(acc.reshape(land.shape) * 1e4)).save(out / "flow.png")
    render.scalar(ero, -1.0, 1.0).save(out / "erosion.png")
    render.scalar(roughness, 0.0, 1.0).save(out / "roughness.png")
    render.scalar(cont_param, -1.0, 1.0).save(out / "continentalness.png")

    log("tile")
    stats = metrics(cfg, z, sea, land, cont, reg, river, hum_raw, recv)
    tiles.write_tile(cfg, out, args.origin[0], args.origin[1], {
        "height": world_y,
        "temperature": np.rint(np.clip(temp, -1.0, 1.0) * 127).astype(np.int8),
        # signed now: the band curve puts the dry half below zero
        "humidity": np.rint(np.clip(hum, -1.0, 1.0) * 127).astype(np.int8),
        "river_dist": np.clip(np.rint(river_dist), 0, 255).astype(np.uint8),
        "river_width": np.clip(np.rint(river_width), 0, 255).astype(np.uint8),
        "river_water_y": np.clip(np.rint(river_water_y), -32768, 32767).astype(np.int16),
        "erosion": np.rint(np.clip(ero, -1.0, 1.0) * 127).astype(np.int8),
        # jaggedness needs the unshaped roughness: the erosion layer above has
        # been respaced for the biome table and no longer measures anything
        "roughness": np.rint(np.clip(roughness, 0.0, 1.0) * 255).astype(np.uint8),
        "continentalness": np.rint(np.clip(cont_param, -1.0, 1.0) * 127).astype(np.int8),
        "region": reg.astype(np.uint16),
        "continent": cont.astype(np.uint8),
        "hardness": np.rint(np.clip(hardness, 0.0, 1.0) * 255).astype(np.uint8),
    }, sea, extra={"metrics": stats, "config": cfg.__dict__, "spawn_anchors": anchors},
       river_segments=rivers.segments(cfg, river, river_water, recv))
    (out / "config.json").write_text(cfg.to_json(), encoding="utf-8")

    log("done")
    print()
    width = max(len(k) for k in stats)
    for k, v in stats.items():
        print(f"  {k.ljust(width)}  {v}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
