"""Continents and regions.

Regions are drainage basins, not Voronoi cells. Watersheds are bounded by ridge
lines, so the borders land on mountains and divides -- which is both what real
political boundaries followed and what reads as natural in game.
"""
from __future__ import annotations

import numpy as np

from .config import Config
from .grid import component_sizes, connected_components, find_roots, neighbour_indices, neighbours


def continents(cfg: Config, land: np.ndarray) -> np.ndarray:
    """Label landmasses; islets below the threshold are dropped into the sea's
    label space so they do not become continents of their own."""
    labels = connected_components(land)
    sizes = component_sizes(labels)
    min_cells = cfg.islet_threshold_blocks / cfg.cell_area_blocks
    keep = np.zeros(sizes.size, dtype=bool)
    keep[1:] = sizes[1:] >= min_cells
    keep[0] = False

    remap = np.zeros(sizes.size, dtype=np.int64)
    remap[keep] = np.arange(1, int(keep.sum()) + 1)
    return remap[labels]


def spawn_anchors(cfg: Config, continent_map: np.ndarray, coast_distance: np.ndarray,
                  height: np.ndarray, origin_x: int, origin_z: int) -> list:
    """The most inland point of each continent, in world blocks.

    Uses the pole of inaccessibility -- the land cell furthest from any coast --
    rather than a centroid. A centroid can fall in a bay or off the land entirely
    on a crescent or a forked continent; the furthest-from-water point is always
    on land and always well clear of the shore.
    """
    anchors = []
    for cid in range(1, int(continent_map.max()) + 1):
        mask = continent_map == cid
        if not mask.any():
            continue
        inland = np.where(mask, coast_distance, -np.inf)
        index = int(np.argmax(inland))
        cz, cx = divmod(index, continent_map.shape[1])
        anchors.append({
            "continent": cid,
            "x": int(origin_x + (cx + 0.5) * cfg.blocks_per_cell),
            "z": int(origin_z + (cz + 0.5) * cfg.blocks_per_cell),
            "surface_y": int(height[cz, cx]),
            "inland_blocks": float(coast_distance[cz, cx]),
            "area_blocks": float(mask.sum()) * cfg.cell_area_blocks,
        })
    # the largest landmass first, so the world spawn lands on the main continent
    anchors.sort(key=lambda a: -a["area_blocks"])
    return anchors


def basins(land: np.ndarray, recv_flat: np.ndarray) -> np.ndarray:
    """Basin id per land cell: the coastal outlet it ultimately drains through.

    Routing is cut at the shoreline; without that, every river on a continent
    would share one root out in the ocean and the whole landmass would collapse
    into a single basin.
    """
    n = land.size
    idx = np.arange(n, dtype=np.int64)
    land_flat = land.ravel()
    stop = ~land_flat[recv_flat]
    recv_land = np.where(stop, idx, recv_flat)
    roots = find_roots(recv_land)
    return np.where(land_flat, roots, -1).reshape(land.shape)


def _adjacency(labels: np.ndarray, valid: np.ndarray) -> dict:
    nb_i = neighbour_indices(labels.shape)
    lab = labels.ravel()
    adj: dict[int, dict[int, int]] = {}
    for k in range(8):
        a = lab
        b_idx = nb_i[k].ravel()
        ok = (b_idx >= 0) & valid.ravel()
        b = np.where(b_idx >= 0, lab[np.where(b_idx >= 0, b_idx, 0)], -1)
        ok &= (b >= 0) & (a != b) & (a >= 0)
        for u, v in zip(a[ok], b[ok]):
            adj.setdefault(int(u), {}).setdefault(int(v), 0)
            adj[int(u)][int(v)] += 1
    return adj


def merge_to_target(cfg: Config, basin_map: np.ndarray, continent_map: np.ndarray) -> np.ndarray:
    """Merge basins within each continent until the region count matches the
    area-derived target. Smallest basin is absorbed by its longest-shared
    neighbour, so regions stay compact instead of growing tendrils."""
    out = np.zeros_like(basin_map)
    next_id = 1
    cell_area = cfg.cell_area_blocks

    for cid in range(1, int(continent_map.max()) + 1):
        mask = continent_map == cid
        if not mask.any():
            continue
        area = float(mask.sum()) * cell_area
        target = int(np.clip(round(area / cfg.region_unit_area_blocks),
                             1, cfg.max_regions_per_continent))

        local = np.where(mask, basin_map, -1)
        ids = [int(v) for v in np.unique(local) if v >= 0]
        size = {i: int((local == i).sum()) for i in ids}
        parent = {i: i for i in ids}

        def root(i):
            while parent[i] != i:
                parent[i] = parent[parent[i]]
                i = parent[i]
            return i

        adj = _adjacency(local, mask)
        adj = {u: {v: c for v, c in m.items() if v in size} for u, m in adj.items() if u in size}

        min_cells = cfg.min_region_area_blocks / cell_area
        alive = set(ids)
        while len(alive) > target or (len(alive) > 1 and min(size[i] for i in alive) < min_cells):
            small = min(alive, key=lambda i: size[i])
            options = {v: c for v, c in adj.get(small, {}).items() if root(v) != small and root(v) in alive}
            if not options:
                alive.discard(small)
                if len(alive) <= target:
                    break
                continue
            host = root(max(options, key=lambda v: options[v]))
            parent[small] = host
            size[host] += size[small]
            merged = adj.setdefault(host, {})
            for v, c in adj.get(small, {}).items():
                if v != host:
                    merged[v] = merged.get(v, 0) + c
            alive.discard(small)

        final = {}
        for i in ids:
            r = root(i)
            if r not in final:
                final[r] = next_id
                next_id += 1
            final[i] = final[r]

        lut = np.zeros(max(ids) + 2, dtype=np.int64)
        for i, v in final.items():
            lut[i] = v
        out = np.where(mask & (local >= 0), lut[np.clip(local, 0, None)], out)

    return out
