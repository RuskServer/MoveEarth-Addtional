"""Remap elevations onto an Earth-like hypsometric curve.

Tuning uplift against incision to get a realistic mix of lowland, upland and
mountain is a losing game: push uplift up and the whole continent becomes
plateau, pull it down and everything is a featureless plain. The distribution is
easier to impose directly.

The remap is rank based and strictly monotonic within each domain, so it leaves
the drainage network untouched -- every cell still drains to the same receiver,
and land never becomes sea. Only the vertical spacing changes.

Earth reference points: about half of land is below ~500 m of its ~8800 m range,
and roughly 7.5% of the sea floor is continental shelf, the rest dropping over a
narrow slope to abyssal plain.

The land curve sits slightly lower than a literal reading of Earth's, for a
reason specific to Minecraft: water only exists at sea level, so a river is a dry
ditch unless its bed can be cut down to it. Keeping more of the land within
reach of sea level is what lets rivers actually hold water.
"""
from __future__ import annotations

import numpy as np

# (share of land area below, height as a fraction of the land range)
LAND_CURVE = (
    (0.00, 0.000),
    (0.25, 0.022),
    (0.50, 0.070),
    (0.70, 0.175),
    (0.88, 0.400),
    (0.95, 0.600),
    (0.99, 0.835),
    (1.00, 1.000),
)

# (share of sea floor shallower than, depth as a fraction of the ocean range)
OCEAN_CURVE = (
    (0.000, 0.000),
    (0.075, 0.060),   # shelf break
    (0.200, 0.550),   # continental slope
    (0.500, 0.840),
    (0.950, 0.960),
    (1.000, 1.000),
)


def _rank(values: np.ndarray) -> np.ndarray:
    order = np.argsort(values, kind="stable")
    ranks = np.empty(values.size, dtype=np.float64)
    ranks[order] = np.arange(values.size, dtype=np.float64) / max(values.size - 1, 1)
    return ranks


def _apply(values: np.ndarray, curve) -> np.ndarray:
    q = np.array([p[0] for p in curve])
    h = np.array([p[1] for p in curve])
    return np.interp(_rank(values), q, h)


def remap(z: np.ndarray, sea_level: float,
          land_curve=LAND_CURVE, ocean_curve=OCEAN_CURVE) -> np.ndarray:
    """Impose the curve on the elevation distribution.

    Note on where the terrain's steepness comes from, since it is not obvious:
    this remap is what produces it, not the erosion. A rank says how many cells
    are lower, never how far below they are, so the simulation's own relief is
    discarded here and the curve's stretch sets every slope. Measured at size
    512: with the remap the land reaches a gradient of 5.4 blocks per block and
    0.7% of it is steep enough for the cliff surface rule; without it, 1.9 and
    0.0%. The vertical budget is why -- 328 blocks of world height across an
    8192 block tile cannot hold a 2:1 slope at cell resolution, and the curve
    buys the mountains their height by spending the lowlands' range on them.

    Splitting the field and remapping only its blurred part, so the erosion's
    detail survives, was tried and is a dead end: blurring lowers the peaks
    before they are ranked, the mountains come back as hills, and adding the
    residual does not restore them. Cliffs have to come from block-scale
    jaggedness instead, which is not this function's business.
    """
    out = np.empty_like(z)
    land = z > sea_level
    sea = ~land

    if land.any():
        top = float(z[land].max())
        out[land] = sea_level + _apply(z[land], land_curve) * max(top - sea_level, 1e-9)
    if sea.any():
        bottom = float(z[sea].min())
        # ranks run shallow-to-deep, so invert the sea floor before ranking
        depth = _apply(-z[sea], ocean_curve)
        out[sea] = sea_level - depth * max(sea_level - bottom, 1e-9)
    return out
