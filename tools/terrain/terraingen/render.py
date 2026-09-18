"""PNG output. Phase 1 is judged by eye, so these images are the deliverable."""
from __future__ import annotations

import numpy as np
from PIL import Image

OCEAN_STOPS = [
    (0.00, (5, 16, 48)),
    (0.55, (12, 44, 96)),
    (0.85, (26, 86, 150)),
    (1.00, (70, 142, 190)),
]
LAND_STOPS = [
    (0.000, (208, 198, 150)),
    (0.040, (112, 148, 84)),
    (0.220, (72, 118, 62)),
    (0.460, (108, 108, 68)),
    (0.640, (126, 106, 84)),
    (0.800, (146, 142, 138)),
    (0.920, (216, 218, 222)),
    (1.000, (255, 255, 255)),
]


def _ramp(t: np.ndarray, stops) -> np.ndarray:
    pos = np.array([s[0] for s in stops])
    col = np.array([s[1] for s in stops], dtype=np.float64)
    out = np.empty(t.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(t, pos, col[:, c])
    return out


def hillshade(z: np.ndarray, scale: float = 1.0,
              azimuth: float = 315.0, altitude: float = 42.0) -> np.ndarray:
    gy, gx = np.gradient(z * scale)
    slope = np.arctan(np.hypot(gx, gy))
    aspect = np.arctan2(-gx, gy)
    az = np.radians(360.0 - azimuth + 90.0)
    alt = np.radians(altitude)
    sh = np.sin(alt) * np.cos(slope) + np.cos(alt) * np.sin(slope) * np.cos(az - aspect)
    return np.clip(sh, 0.0, 1.0)


def relief(z: np.ndarray, sea_level: float, river: np.ndarray | None = None,
           exaggeration: float = 12.0) -> Image.Image:
    """Shaded relief.

    Heights are in arbitrary simulation units, so the hillshade is computed on a
    normalised copy with an explicit vertical exaggeration. Feeding raw units in
    makes every slope read as flat.
    """
    land = z > sea_level
    img = np.zeros(z.shape + (3,))

    depth = np.clip((z - z.min()) / max(sea_level - z.min(), 1e-9), 0.0, 1.0)
    img[~land] = _ramp(depth, OCEAN_STOPS)[~land]

    hi = max(float(z.max()) - sea_level, 1e-9)
    elev = np.clip((z - sea_level) / hi, 0.0, 1.0)
    img[land] = _ramp(elev, LAND_STOPS)[land]

    span = max(float(z.max()) - float(z.min()), 1e-9)
    sh = hillshade((z - float(z.min())) / span, exaggeration)
    shade = np.where(land, 0.40 + 0.75 * sh, 0.80 + 0.25 * sh)[..., None]
    img = img * shade

    if river is not None:
        wet = river > 0
        t = np.clip(river / max(river.max(), 1), 0.0, 1.0)[..., None]
        blue = np.array([70, 128, 190]) * (1.0 - t) + np.array([24, 72, 148]) * t
        # blend narrow threads into the land instead of painting them at full
        # strength, so trunk rivers read and headwaters stay subordinate
        mix = np.clip(0.45 + 0.55 * t, 0.0, 1.0)
        img[wet] = (img * (1.0 - mix) + blue * mix)[wet]

    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def categorical(labels: np.ndarray, seed: int = 7, background=(18, 22, 30)) -> Image.Image:
    rng = np.random.default_rng(seed)
    n = int(labels.max()) + 1
    palette = rng.integers(60, 235, (max(n, 1), 3)).astype(np.uint8)
    if n > 0:
        palette[0] = background
    img = palette[np.clip(labels, 0, n - 1)]
    # darken boundaries so regions are legible
    edge = np.zeros(labels.shape, dtype=bool)
    edge[:-1, :] |= labels[:-1, :] != labels[1:, :]
    edge[:, :-1] |= labels[:, :-1] != labels[:, 1:]
    img[edge] = (10, 10, 12)
    return Image.fromarray(img)


def scalar(a: np.ndarray, lo=None, hi=None, stops=None) -> Image.Image:
    lo = float(a.min()) if lo is None else lo
    hi = float(a.max()) if hi is None else hi
    t = np.clip((a - lo) / max(hi - lo, 1e-9), 0.0, 1.0)
    stops = stops or [
        (0.0, (20, 30, 90)), (0.35, (30, 140, 160)),
        (0.65, (220, 200, 90)), (1.0, (190, 50, 40)),
    ]
    return Image.fromarray(_ramp(t, stops).astype(np.uint8))
