"""Thermal relaxation and stream-power incision."""
from __future__ import annotations

import numpy as np

from .config import Config
from .grid import DISTANCES, OFFSETS, neighbours
from .hydro import accumulate, fill_depressions, receivers


def talus_in_sim_units(cfg: Config, z: np.ndarray, hardness=None):
    """Convert the angle of repose into a per-cell height difference.

    One cell spans `blocks_per_cell` blocks horizontally, and the height field
    spans `max_y - min_y` blocks vertically once mapped into the world, so the
    conversion needs the current simulation range.
    """
    span_sim = max(float(z.max()) - float(z.min()), 1e-9)
    blocks_per_sim = (cfg.max_y - cfg.min_y) / span_sim
    if hardness is None:
        degrees = cfg.talus_degrees
    else:
        degrees = (cfg.talus_soft_degrees
                   + (cfg.talus_hard_degrees - cfg.talus_soft_degrees) * hardness)
    return np.tan(np.radians(degrees)) * cfg.blocks_per_cell / blocks_per_sim


def thermal_erosion(z: np.ndarray, talus, rate: float) -> np.ndarray:
    """Relax slopes above the talus angle. Material given by a cell is received
    by the neighbour it slid towards, so total mass is conserved."""
    delta = np.zeros_like(z)
    h, w = z.shape
    for k, (dy, dx) in enumerate(OFFSETS):
        nb = neighbours(z, np.inf)[k]
        excess = np.maximum(0.0, z - nb - talus * DISTANCES[k])
        excess = np.where(np.isfinite(nb), excess, 0.0)
        moved = rate * excess / 8.0
        delta -= moved
        # shift the moved material into the receiving neighbour
        pad = np.zeros((h + 2, w + 2))
        pad[1 + dy: 1 + dy + h, 1 + dx: 1 + dx + w] = moved
        delta += pad[1:-1, 1:-1]
    return z + delta


def run_erosion(cfg: Config, z: np.ndarray, uplift: np.ndarray, sea_level: float,
                rng: np.random.Generator | None = None, hardness=None, progress=None):
    """Alternate uplift, stream-power incision and thermal relaxation.

    A fixed per-cell jitter is added to the elevation *before* depression filling.
    Flow routing on a near-flat coastal plain otherwise resolves every tie the
    same way and the drainage comes out as parallel straight lines along the grid
    axes. It has to precede the fill: jitter applied afterwards is far larger than
    the fill's epsilon gradient and punches fresh local minima into filled flats,
    stranding rivers inland.
    """
    n = z.size
    weight = np.full(n, 1.0 / n)
    warm = None
    recv = dist = None
    rng = rng or np.random.default_rng(0)
    span = max(float(z.max()) - float(z.min()), 1e-9)
    jitter = (rng.random(z.shape) - 0.5) * cfg.route_jitter * span
    if hardness is None:
        erodibility = 1.0
    else:
        erodibility = np.ravel(1.0 - (1.0 - cfg.hard_rock_erodibility) * hardness)

    for step in range(cfg.erosion_steps):
        z = z + cfg.uplift_per_step * uplift * cfg.erosion_dt

        surface = fill_depressions(z + jitter, sea_level,
                                   max_rounds=cfg.fill_rounds, eps=cfg.fill_epsilon)
        recv, dist, _outlet = receivers(surface)
        warm = accumulate(recv, weight, cfg.accum_iterations, warm)

        zf = z.ravel()
        slope = np.maximum(0.0, (zf - zf[recv]) / dist)
        incision = (cfg.stream_power_k * erodibility
                    * (warm ** cfg.stream_power_m) * (slope ** cfg.stream_power_n))
        land = zf > sea_level
        zf = zf - np.where(land, incision, 0.0) * cfg.erosion_dt
        z = zf.reshape(z.shape)

        if cfg.thermal_every and step % cfg.thermal_every == 0:
            z = thermal_erosion(z, talus_in_sim_units(cfg, z, hardness), cfg.thermal_rate)

        if progress and (step % 10 == 0 or step == cfg.erosion_steps - 1):
            progress(step + 1, cfg.erosion_steps)

    surface = fill_depressions(z + jitter, sea_level,
                               max_rounds=cfg.fill_rounds, eps=cfg.fill_epsilon)
    recv, dist, outlet = receivers(surface)
    acc = accumulate(recv, weight, cfg.accum_final_iterations, warm)
    return z, recv, dist, outlet, acc
