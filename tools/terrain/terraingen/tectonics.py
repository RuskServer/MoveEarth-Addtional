"""Plate tectonics: the source of linear mountain belts, ocean basins and shelves.

Isotropic noise cannot produce mountain ranges that read as Earth-like, because
real ranges are orogenic belts lying along convergent plate boundaries. This
module builds those boundaries explicitly and derives an uplift field from them.
"""
from __future__ import annotations

import numpy as np

from .config import Config
from .grid import OFFSETS, neighbours
from .noise import box_blur, fbm, ridged


def _warped_grid(cfg: Config, rng: np.random.Generator):
    """Sample coordinates pushed around by noise.

    Without this the plate polygons show through as straight coastlines, which
    is the single most artificial-looking artefact of a Voronoi tectonic model.
    """
    size = cfg.size
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64)
    amp = cfg.plate_warp * size
    wy = fbm(rng, size, 5, base_freq=cfg.plate_warp_freq,
             min_feature_cells=cfg.min_feature_cells) * amp
    wx = fbm(rng, size, 5, base_freq=cfg.plate_warp_freq,
             min_feature_cells=cfg.min_feature_cells) * amp
    return yy + wy, xx + wx


def _assign_plates(pts: np.ndarray, size: int, yy=None, xx=None) -> np.ndarray:
    if yy is None:
        yy, xx = np.mgrid[0:size, 0:size].astype(np.float64)
    best_d = np.full((size, size), np.inf)
    owner = np.zeros((size, size), dtype=np.int64)
    for i, (py, px) in enumerate(pts):
        d = (yy - py) ** 2 + (xx - px) ** 2
        closer = d < best_d
        best_d = np.where(closer, d, best_d)
        owner = np.where(closer, i, owner)
    return owner


def build_plates(cfg: Config, rng: np.random.Generator):
    size = cfg.size
    wy, wx = _warped_grid(cfg, rng)
    pts = rng.uniform(0.0, float(size), (cfg.plate_count, 2))
    owner = _assign_plates(pts, size, wy, wx)
    for _ in range(cfg.lloyd_iterations):
        for i in range(cfg.plate_count):
            m = owner == i
            if m.any():
                pts[i] = (wy[m].mean(), wx[m].mean())
        owner = _assign_plates(pts, size, wy, wx)

    speed = rng.uniform(0.35, 1.0, cfg.plate_count)
    ang = rng.uniform(0.0, 2.0 * np.pi, cfg.plate_count)
    vel = np.stack([np.sin(ang) * speed, np.cos(ang) * speed], axis=1)  # (dy, dx)

    n_cont = max(1, int(round(cfg.plate_count * cfg.continental_fraction)))
    continental = np.zeros(cfg.plate_count, dtype=bool)
    continental[rng.permutation(cfg.plate_count)[:n_cont]] = True
    return owner, vel, continental, pts


def convergence_field(owner: np.ndarray, vel: np.ndarray, centroids: np.ndarray) -> np.ndarray:
    """Signed convergence per plate *pair*, written onto their shared boundary.

    Taking the normal from each individual cell edge looks reasonable but is
    wrong at this scale: along a curved margin the edge normal rotates, so the
    same boundary reads as convergent at one end, transform in the middle and
    divergent at the other. Blurring those short convergent fragments produces
    isolated round domes instead of mountain ranges.

    One normal per plate pair, taken between the plate centroids, keeps a whole
    margin coherent the way a real convergent boundary is.
    """
    conv = np.zeros(owner.shape)
    nb_owner = neighbours(owner, -1)
    for k in range(8):
        other = nb_owner[k]
        valid = (other >= 0) & (other != owner)
        if not valid.any():
            continue
        a = owner[valid]
        b = other[valid]
        d = centroids[b] - centroids[a]
        n = d / np.maximum(np.linalg.norm(d, axis=1, keepdims=True), 1e-9)
        rel = vel[a] - vel[b]
        c = (rel * n).sum(axis=1)
        here = np.zeros(owner.shape)
        here[valid] = c
        # a cell touching three plates keeps the strongest of its pairs
        conv = np.where(np.abs(here) > np.abs(conv), here, conv)
    return conv


def _belt(cfg: Config, conv: np.ndarray, spread: int):
    """Spread boundary convergence into a belt without diluting it.

    A plain blur mixes "how convergent" with "how much boundary is nearby", so a
    thin line of strong convergence comes out weak. Normalised convolution keeps
    the two apart: one blur carries the average convergence, the other the
    proximity to a boundary.
    """
    mask = (conv != 0.0).astype(np.float64)
    weight = box_blur(mask, spread, passes=3)
    average = box_blur(conv, spread, passes=3) / (weight + 1e-6)
    near = weight / max(float(np.percentile(weight, 99.5)), 1e-9)
    return average, np.clip(near, 0.0, 1.0)


def tectonic_height(cfg: Config, rng: np.random.Generator):
    """Return (height, owner, continental_mask, uplift_pattern, hardness)."""
    size = cfg.size
    owner, vel, continental, centroids = build_plates(cfg, rng)

    conv = convergence_field(owner, vel, centroids)
    spread = max(1, int(round(cfg.uplift_falloff_cells * size / 1024.0)))
    average, near = _belt(cfg, conv, spread)
    peak = np.abs(average).max()
    if peak > 1e-9:
        average = average / peak
    conv_s = average * near

    # continental crust sits high, oceanic crust low; blurring the step creates
    # the continental shelf and slope at the margin.
    shelf = max(2, int(round(cfg.shelf_blur_cells * size / 1024.0)))
    base = np.where(continental[owner], 0.55, -0.55)
    base = box_blur(base, shelf, passes=2)
    # cratons and interior basins: continents are not uniformly high
    base = base + cfg.craton_relief * fbm(
        rng, size, 4, base_freq=2, min_feature_cells=cfg.min_feature_cells) * (base > 0.0)

    orogeny = np.maximum(conv_s, 0.0) ** 1.4
    rifting = np.maximum(-conv_s, 0.0) ** 1.2
    # ridged noise modulates the belt so ranges gain crests instead of a smooth welt
    # multi-octave, otherwise the belt gets evenly spaced ribs like a caterpillar
    crest = 0.35 + 0.65 * ridged(rng, size, 5, base_freq=cfg.crest_freq,
                                 gain=0.62,
                                 min_feature_cells=cfg.min_feature_cells)

    # hill country: ridged relief riding on continental crust only, so oceans
    # stay smooth while continent interiors get something for erosion to dissect
    land_bias = np.clip(base + 0.25, 0.0, 1.0)
    hills = cfg.hill_amplitude * land_bias * (
        0.5 + 0.5 * ridged(rng, size, 6, base_freq=cfg.hill_freq,
                           min_feature_cells=cfg.min_feature_cells))

    height = (
        base
        + hills
        + cfg.uplift_strength * orogeny * crest
        - cfg.rift_strength * rifting
        + cfg.noise_amplitude * fbm(rng, size, cfg.noise_octaves,
                                    base_freq=3,
                                    lacunarity=cfg.noise_lacunarity,
                                    gain=cfg.noise_gain,
                                    min_feature_cells=cfg.min_feature_cells)
    )
    # what keeps rising during erosion: the orogenic belts, plus a slow uplift
    # over continental crust so interiors do not simply wear flat
    # Bedrock resistance. It drives uplift, the angle of repose and erodibility
    # together, so resistant ground rises, holds a steep face and wears slowly --
    # which is what a cliff actually is.
    grain = 0.5 + 0.5 * fbm(rng, size, 6, base_freq=cfg.uplift_noise_freq,
                            min_feature_cells=cfg.min_feature_cells)
    # Ranked, not just clipped: fbm piles up around its mean, so a raw field
    # would make almost everything middling rock and leave neither real cliffs
    # nor real soft country. Ranking spreads it evenly, which fixes what share
    # of the map is hard instead of leaving it to the noise's distribution.
    order = np.argsort(grain, axis=None, kind="stable")
    hardness = np.empty(grain.size)
    hardness[order] = np.arange(grain.size) / max(grain.size - 1, 1)
    hardness = hardness.reshape(grain.shape)
    uplift = (cfg.uplift_strength * orogeny * crest
              + cfg.craton_uplift * np.maximum(base, 0.0)
              + cfg.hill_uplift * hills
              + cfg.uplift_noise * grain * land_bias)
    uplift = uplift / max(float(uplift.max()), 1e-9)
    return height, owner, continental[owner], uplift, hardness


def margin_ramp(cfg: Config, rng: np.random.Generator | None = None) -> np.ndarray:
    """0 at the tile edge, 1 once clear of the forced-ocean band.

    The distance to the edge is perturbed by noise. A clean `min(dx, dy)` ramp
    has axis-aligned contours, and any continent it clips ends up with a dead
    straight coastline along the map edge.
    """
    size = cfg.size
    margin = max(1, int(round(cfg.ocean_margin_blocks / cfg.blocks_per_cell)))
    ramp = margin * 2
    idx = np.arange(size)
    d = np.minimum(idx, size - 1 - idx).astype(np.float64)
    d2 = np.minimum(d[:, None], d[None, :])
    if rng is not None and cfg.margin_irregularity > 0.0:
        d2 = d2 + cfg.margin_irregularity * ramp * fbm(
            rng, size, 5, base_freq=4, min_feature_cells=cfg.min_feature_cells)
    t = np.clip((d2 - margin) / max(1.0, ramp), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def apply_ocean_margin(cfg: Config, height: np.ndarray,
                       rng: np.random.Generator | None = None) -> np.ndarray:
    """Push the tile border below sea level, keeping its relief.

    The obvious form, `floor + (height - floor) * t`, scales the relief itself by
    `t`, so the whole margin band flattens to a plane as it approaches the edge.
    A planar coastal plain routes D8 flow in dead straight parallel lines, which
    is where the unnatural comb of rivers along every coast came from. Subtract
    the ramp instead: the land still goes under, but it keeps its texture and the
    drainage stays dendritic all the way to the shore.
    """
    t = margin_ramp(cfg, rng)
    drop = (float(height.max()) - float(height.min())) * 1.15
    return height - (1.0 - t) * drop


def taper_to_margin(cfg: Config, field: np.ndarray,
                    rng: np.random.Generator | None = None) -> np.ndarray:
    """Fade a rate field (uplift) out at the border. Scaling is right here --
    unlike an elevation, an uplift rate genuinely should go to zero."""
    return field * margin_ramp(cfg, rng)
