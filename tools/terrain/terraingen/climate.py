"""Temperature and orographic precipitation.

The two things that make a climate map read as Earth-like are a latitude
gradient and rain shadows. Both are cheap: the gradient is analytic, and the
rain shadow needs one sweep along the prevailing wind.
"""
from __future__ import annotations

import numpy as np

from .config import Config
from .noise import box_blur


def temperature(cfg: Config, z: np.ndarray, sea_level: float) -> np.ndarray:
    size = cfg.size
    lat = np.abs(2.0 * np.arange(size) / max(1, size - 1) - 1.0)
    base = cfg.equator_temp + (cfg.pole_temp - cfg.equator_temp) * (lat ** 1.3)
    t = np.repeat(base[:, None], size, axis=1)

    span = max(float(z.max()) - sea_level, 1e-6)
    elev = np.clip((z - sea_level) / span, 0.0, 1.0)
    return t - cfg.lapse_rate * elev


# Open water is humid; ocean biomes barely use this parameter anyway.
OCEAN_HUMIDITY = 0.9


def erosion(cfg: Config, z: np.ndarray, sea_level: float) -> np.ndarray:
    """Vanilla's erosion climate parameter, derived from local roughness.

    High erosion means worn-down, flat country; low erosion means mountains.
    Measuring how far the surface departs from its own neighbourhood gives that
    directly, and it keeps the biome parameter consistent with the terrain the
    tile actually carries.
    """
    radius = max(1, int(round(cfg.erosion_window_cells)))
    smooth = box_blur(z, radius, passes=2)
    rough = box_blur(np.abs(z - smooth), radius, passes=2)
    land = z > sea_level
    sample = rough[land] if land.any() else rough
    hi = float(np.percentile(sample, 97.0))
    t = np.clip(rough / max(hi, 1e-9), 0.0, 1.0)
    return 1.0 - 2.0 * t


def zonal_supply(cfg: Config) -> np.ndarray:
    """Latitudinal moisture supply: wet equator, dry subtropics, wet storm track.

    This band structure, not the inland gradient, is what puts deserts, savanna,
    forest and taiga into recognisable belts. Without it every continent is just
    wet at the coast and dry in the middle.
    """
    size = cfg.size
    lat = np.abs(2.0 * np.arange(size) / max(1, size - 1) - 1.0)
    itcz = np.exp(-(lat / max(cfg.itcz_width, 1e-6)) ** 2)
    horse = np.exp(-((lat - cfg.subtropical_centre) / max(cfg.subtropical_width, 1e-6)) ** 2)
    storm = np.exp(-((lat - cfg.midlat_centre) / max(cfg.midlat_width, 1e-6)) ** 2)
    supply = 0.30 + 0.70 * itcz + 0.55 * storm
    supply *= 1.0 - cfg.subtropical_dryness * horse
    return np.clip(supply, 0.03, None)


def _advect(cfg: Config, z: np.ndarray, sea_level: float,
            rows: np.ndarray, eastward: bool) -> np.ndarray:
    """One zonal moisture sweep over a band of rows."""
    size = cfg.size
    span = max(float(z.max()) - sea_level, 1e-6)
    order = range(size) if eastward else range(size - 1, -1, -1)

    out = np.zeros((rows.size, size))
    moisture = np.zeros(rows.size)
    supply = zonal_supply(cfg)[rows]
    prev = None
    for x in order:
        column = z[rows, x]
        over_ocean = column <= sea_level
        # warm tropical ocean feeds the air faster than a subtropical one
        moisture = np.where(over_ocean,
                            np.minimum(1.0, moisture + cfg.evaporation * supply),
                            moisture)
        rise = np.zeros(rows.size) if prev is None else np.maximum(0.0, (column - prev) / span)
        rain = np.minimum(moisture, moisture * (cfg.rain_base + cfg.orographic * rise))
        # What a biome cares about is how much water the air can still deliver,
        # not the instantaneous drop. Storing the rain alone leaves a field that
        # is zero everywhere except a spike on each windward slope.
        out[:, x] = (moisture + cfg.windward_bonus * rain) * supply
        moisture = (moisture - rain) * (1.0 - cfg.moisture_decay)
        prev = column
    return out


def wind_is_eastward(cfg: Config) -> np.ndarray:
    """Per-row prevailing wind direction. True means air moves towards +x."""
    size = cfg.size
    lat = np.abs(2.0 * np.arange(size) / max(1, size - 1) - 1.0)
    trades = lat < cfg.trade_wind_latitude
    return np.where(trades, not cfg.wind_from_west, cfg.wind_from_west)


def humidity(cfg: Config, z: np.ndarray, sea_level: float) -> np.ndarray:
    """Advect moisture inland and let the ranges wring it out.

    Wind direction follows latitude the way Earth's does -- easterly trades near
    the equator, westerlies at mid latitudes -- so rain shadows do not all point
    the same way across the whole map.
    """
    size = cfg.size
    lat = np.abs(2.0 * np.arange(size) / max(1, size - 1) - 1.0)
    trades = lat < cfg.trade_wind_latitude

    out = np.zeros((size, size))
    for rows, eastward in ((np.nonzero(trades)[0], not cfg.wind_from_west),
                           (np.nonzero(~trades)[0], cfg.wind_from_west)):
        if rows.size:
            out[rows] = _advect(cfg, z, sea_level, rows, eastward)

    out = box_blur(out, max(1, size // 128), passes=2)

    # Rank transform over land rather than a linear rescale.
    #
    # Moisture decays roughly exponentially inland, so the raw field is extremely
    # skewed: clipping it between percentiles left a third of all land pinned at
    # the driest value with a median barely above it, which collapses the biome
    # parameter. Ranking spreads the values evenly while preserving order, so the
    # zonal bands and rain shadows survive untouched and only the spacing changes
    # -- the same reasoning as the hypsometric remap in `hypsometry`.
    land = z > sea_level
    result = np.full(out.shape, OCEAN_HUMIDITY)
    if land.any():
        values = out[land]
        order = np.argsort(values, kind="stable")
        ranks = np.empty(values.size)
        ranks[order] = np.arange(values.size) / max(values.size - 1, 1)
        result[land] = np.clip(ranks, 0.0, 1.0)
    return result
