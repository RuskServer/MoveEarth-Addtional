"""Map our fields onto the distributions vanilla's biome table expects.

Vanilla's climate parameters come from multi-octave noise, so their mass sits
near zero and thins towards the edges, and the biome parameter boxes are cut to
match. Handing the same table a differently shaped distribution does not shift
biomes slightly -- it collapses them. Ranking our humidity uniformly across
[-1, 1] put a third of all land in the driest band and a third in the wettest,
and nearly a third of the whole world came out as one biome.

Each curve below is (share of land below, parameter value). The values are
vanilla's own band edges, so the shares are literally the band occupancies.
"""
from __future__ import annotations

import numpy as np

# vanilla band edges, for reference:
#   temperature  0|-0.45  1|-0.15  2|0.20  3|0.55  4
#   vegetation   0|-0.35  1|-0.10  2|0.10  3|0.30  4
TEMPERATURE_CURVE = (
    (0.00, -1.00),
    (0.10, -0.45),   # coldest
    (0.30, -0.15),   # cold
    (0.60, 0.20),    # temperate
    (0.85, 0.55),    # warm
    (1.00, 1.00),    # hot
)

VEGETATION_CURVE = (
    (0.00, -1.00),
    (0.15, -0.35),   # driest
    (0.35, -0.10),   # dry
    (0.60, 0.10),    # neutral
    (0.82, 0.30),    # wet
    (1.00, 1.00),    # wettest
)

# Erosion decides the landform class, and vanilla reads it far more literally
# than the name suggests: swamp and mangrove swamp ignore humidity entirely and
# take any inland cell whose erosion is in the top band. Our erosion comes from
# local roughness, and a tile is mostly lowland, so 70% of the land sat in that
# one band and half the world came out as swamp. Spreading it over the bands is
# what makes the landform classes mean anything.
#
# vanilla band edges:
#   0|-0.78  1|-0.375  2|-0.2225  3|0.05  4|0.45  5|0.55  6
EROSION_CURVE = (
    (0.00, -1.00),
    (0.05, -0.78),     # peaks
    (0.19, -0.375),    # mountains
    (0.29, -0.2225),   # hills
    (0.51, 0.05),      # rolling
    (0.87, 0.45),      # plains
    (0.92, 0.55),      # a narrow band vanilla uses for windswept variants
    (1.00, 1.00),      # flat lowland; this is the swamp band, so keep it small
)

# Continentalness is a distance from the shore, not a height. Vanilla's own
# value behaves that way: the band edges are how far inland you are, and tying
# them to elevation instead skips the coastal bands entirely -- which is why no
# beach ever generated and shallow water was treated as mid inland.
#
# (signed distance in blocks, continentalness); negative is offshore.
COAST_CURVE = (
    (-4000.0, -1.00),
    (-1400.0, -0.60),   # deep ocean
    (-400.0, -0.30),    # ocean
    (-80.0, -0.19),     # coast begins
    (0.0, -0.15),       # shoreline
    (80.0, -0.11),      # coast ends
    (320.0, 0.03),      # near inland
    (900.0, 0.30),      # mid inland
    (2400.0, 0.70),
    (6000.0, 1.00),     # far inland
)


def rank_to_curve(values: np.ndarray, mask: np.ndarray, curve, fill: float = 0.0) -> np.ndarray:
    """Rank the masked values and read them off the curve. Monotonic, so the
    spatial pattern is untouched and only the spacing changes."""
    out = np.full(values.shape, fill, dtype=np.float64)
    if not mask.any():
        return out
    sample = values[mask]
    order = np.argsort(sample, kind="stable")
    ranks = np.empty(sample.size)
    ranks[order] = np.arange(sample.size) / max(sample.size - 1, 1)
    q = np.array([p[0] for p in curve])
    v = np.array([p[1] for p in curve])
    out[mask] = np.interp(ranks, q, v)
    return out


def continentalness(signed_coast: np.ndarray) -> np.ndarray:
    d = np.array([p[0] for p in COAST_CURVE])
    v = np.array([p[1] for p in COAST_CURVE])
    return np.clip(np.interp(signed_coast, d, v), -1.0, 1.0)
