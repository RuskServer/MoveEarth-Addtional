"""River channels as a distance field.

Storing "how much to carve" per cell does not survive the tile resolution: the
value is interpolated bilinearly, so a channel can never come out narrower than
about two cells no matter how thin it really is. At 16 blocks per cell that puts
a floor of roughly 30 blocks on every river and makes small streams impossible.

The legacy unsigned distance field is retained for compatibility. It does NOT
preserve narrow diagonal channels under bilinear interpolation. New tiles also
export connected centre-line segments, which the runtime indexes spatially and
samples geometrically, interpolating width and water height along each segment.
"""
from __future__ import annotations

import numpy as np

from .config import Config
from .grid import DISTANCES, OFFSETS, neighbours


def connect_downstream(seeds: np.ndarray, land: np.ndarray, recv: np.ndarray) -> np.ndarray:
    """Filters select sources, never holes in an established channel."""
    wet = np.zeros(land.size, dtype=bool)
    land_flat = land.ravel()
    for start in np.flatnonzero(seeds & land):
        cell = int(start)
        while not wet[cell]:
            wet[cell] = True
            target = int(recv[cell])
            if not land_flat[cell] or target == cell:
                break
            cell = target
    return wet.reshape(land.shape)


def channel_width(cfg: Config, acc: np.ndarray, wet: np.ndarray) -> np.ndarray:
    """Width in blocks, growing with discharge."""
    ratio = np.maximum(acc / max(cfg.river_width_reference, 1e-12), 1.0)
    width = cfg.river_min_width * np.power(ratio, cfg.river_width_exponent)
    return np.where(wet, np.clip(np.rint(width), cfg.river_min_width, cfg.river_max_width), 0.0)


def segments(cfg: Config, width: np.ndarray, water: np.ndarray, recv: np.ndarray) -> list:
    """Tile-relative connected centre lines; avoids bilinear distance-field gaps."""
    result = []
    widths, levels = width.ravel(), water.ravel()
    for source in np.flatnonzero(widths > 0):
        target = int(recv[source])
        if source == target or widths[target] <= 0:
            continue
        z0, x0 = divmod(int(source), width.shape[1])
        z1, x1 = divmod(target, width.shape[1])
        result.append([(x0 + .5) * cfg.blocks_per_cell, (z0 + .5) * cfg.blocks_per_cell,
                       (x1 + .5) * cfg.blocks_per_cell, (z1 + .5) * cfg.blocks_per_cell,
                       float(widths[source]), float(widths[target]),
                       float(levels[source]), float(levels[target])])
    return result


def water_surface(cfg: Config, world_y: np.ndarray, width: np.ndarray, sea_y: int,
                  recv: np.ndarray | None = None) -> np.ndarray:
    """Water surface height of each channel, in world Y.

    Routing uses depression-filled terrain, not raw heights. Enforce descent on
    the actual channel graph; raw terrain minus freeboard alone can flow uphill.
    """
    wet = width > 0
    surface = world_y.astype(np.float64) - cfg.river_freeboard_blocks
    result = np.where(wet, np.maximum(surface, float(sea_y)), 0.0)
    if recv is None:
        return result
    from collections import deque
    active = wet.ravel()
    cells = np.flatnonzero(active)
    edges = cells[(recv[cells] != cells) & active[recv[cells]]]
    donors = np.bincount(recv[edges], minlength=active.size)
    queue = deque(int(i) for i in cells[donors[cells] == 0])
    levels = result.ravel()
    visited = 0
    while queue:
        cell = queue.popleft()
        visited += 1
        target = int(recv[cell])
        if target == cell or not active[target]:
            continue
        levels[target] = min(levels[target], levels[cell])
        donors[target] -= 1
        if donors[target] == 0:
            queue.append(target)
    if visited != cells.size:
        raise ValueError("River receiver graph contains a cycle")
    return result


def distance_field(cfg: Config, width: np.ndarray, water_y: np.ndarray | None = None):
    """Blocks to the nearest channel centre, plus that channel's width.

    A min-plus relaxation over the 8-neighbourhood. Each pass carries the front
    one cell, and the result is clamped to `river_reach_blocks`, so the number of
    passes needed is bounded by the clamp rather than by the map size.
    """
    reach_cells = int(np.ceil(cfg.river_reach_blocks / cfg.blocks_per_cell))
    wet = width > 0

    dist = np.where(wet, 0.0, np.inf)
    carried = np.where(wet, width, 0.0)
    carried_water = np.where(wet, water_y, 0.0) if water_y is not None else np.zeros_like(dist)

    step = DISTANCES * cfg.blocks_per_cell
    for _ in range(reach_cells + 2):
        changed = False
        nb_dist = neighbours(dist, np.inf)
        nb_width = neighbours(carried, 0.0)
        nb_water = neighbours(carried_water, 0.0)
        for k in range(len(OFFSETS)):
            candidate = nb_dist[k] + step[k]
            better = candidate < dist
            if better.any():
                changed = True
                dist = np.where(better, candidate, dist)
                carried = np.where(better, nb_width[k], carried)
                carried_water = np.where(better, nb_water[k], carried_water)
        if not changed:
            break

    dist = np.minimum(dist, cfg.river_reach_blocks)
    dist = np.where(np.isfinite(dist), dist, cfg.river_reach_blocks)
    outside = dist >= cfg.river_reach_blocks
    carried = np.where(outside, 0.0, carried)
    carried_water = np.where(outside, 0.0, carried_water)
    return dist, carried, carried_water
