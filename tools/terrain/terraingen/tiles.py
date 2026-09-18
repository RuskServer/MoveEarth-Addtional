"""Tile asset packing.

Layers are written as flat little-endian binaries plus a JSON descriptor, so the
Java side can memory-map them without a parser.
"""
from __future__ import annotations

import json
import pathlib

import numpy as np

from .config import Config

LAYERS = {
    "height": "<i2",
    "erosion": "i1",
    "continentalness": "i1",
    "temperature": "i1",
    "humidity": "i1",
    "river_dist": "u1",
    "river_width": "u1",
    "river_water_y": "<i2",
    "region": "<u2",
    "continent": "u1",
    "hardness": "u1",
    "roughness": "u1",
}


def height_to_world(cfg: Config, z: np.ndarray, sea_level: float) -> np.ndarray:
    """Piecewise-linear map into Minecraft Y, pinned at the sea level."""
    below = (z - z.min()) / max(sea_level - z.min(), 1e-9)
    above = (z - sea_level) / max(float(z.max()) - sea_level, 1e-9)
    y = np.where(
        z <= sea_level,
        cfg.min_y + (cfg.sea_y - cfg.min_y) * np.clip(below, 0.0, 1.0),
        cfg.sea_y + (cfg.max_y - cfg.sea_y) * np.clip(above, 0.0, 1.0),
    )
    return np.rint(y).astype(np.int16)


def write_tile(cfg: Config, out_dir: pathlib.Path, origin_x: int, origin_z: int,
               layers: dict, sea_level: float, extra: dict | None = None,
               river_segments: list | None = None) -> pathlib.Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    meta = {
        "format": 1,
        "origin_x": origin_x,
        "origin_z": origin_z,
        "size_cells": cfg.size,
        "blocks_per_cell": cfg.blocks_per_cell,
        "size_blocks": cfg.tile_blocks,
        "ocean_margin_blocks": cfg.ocean_margin_blocks,
        "sea_y": cfg.sea_y,
        "min_y": cfg.min_y,
        "max_y": cfg.max_y,
        "sim_sea_level": float(sea_level),
        "shaping": {
            "jagged_max": cfg.jagged_max,
            "jagged_exponent": cfg.jagged_exponent,
            "jagged_soft": cfg.jagged_soft,
        },
        "river": {
            "min_width": cfg.river_min_width,
            "max_width": cfg.river_max_width,
            "reach_blocks": cfg.river_reach_blocks,
            "depth_blocks": cfg.river_depth_blocks,
            "depth_scale": cfg.river_depth_scale,
            "max_depth_blocks": cfg.river_max_depth_blocks,
            "bank_blocks": cfg.river_bank_blocks,
            "bank_ratio": cfg.river_bank_ratio,
            "freeboard_blocks": cfg.river_freeboard_blocks,
            "water_radius": cfg.river_water_radius,
            "water_elevation_tolerance": cfg.river_water_elevation_tolerance,
            "factor_base": cfg.factor_base,
            "factor_channel": cfg.factor_channel,
            "sea_reach_y": cfg.river_sea_reach_y,
            "blend_blocks": cfg.river_blend_blocks,
        },
        "layers": {},
    }
    for name, dtype in LAYERS.items():
        if name not in layers:
            continue
        arr = np.ascontiguousarray(layers[name].astype(np.dtype(dtype)))
        path = out_dir / f"{name}.bin"
        path.write_bytes(arr.tobytes())
        meta["layers"][name] = {"file": path.name, "dtype": dtype, "bytes": path.stat().st_size}
    if extra:
        meta.update(extra)
    if river_segments is not None:
        path = out_dir / "river_segments.json"
        path.write_text(json.dumps(river_segments, separators=(",", ":")), encoding="utf-8")
        meta["river"]["segments_file"] = path.name
    meta_path = out_dir / "tile.json"
    meta_path.write_text(json.dumps(meta, indent=2, sort_keys=True), encoding="utf-8")
    return meta_path
