"""Gradient (Perlin) noise and cheap separable blurs."""
from __future__ import annotations

import numpy as np


def _perlin(rng: np.random.Generator, size: int, freq: int) -> np.ndarray:
    ang = rng.uniform(0.0, 2.0 * np.pi, (freq + 1, freq + 1))
    gx, gy = np.cos(ang), np.sin(ang)

    t = np.linspace(0.0, float(freq), size, endpoint=False)
    i0 = np.floor(t).astype(np.int64)
    f = t - i0
    u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0)

    x0 = i0[None, :]
    x1 = x0 + 1
    y0 = i0[:, None]
    y1 = y0 + 1
    fx = f[None, :]
    fy = f[:, None]
    ux = u[None, :]
    uy = u[:, None]

    n00 = gx[y0, x0] * fx + gy[y0, x0] * fy
    n10 = gx[y0, x1] * (fx - 1.0) + gy[y0, x1] * fy
    n01 = gx[y1, x0] * fx + gy[y1, x0] * (fy - 1.0)
    n11 = gx[y1, x1] * (fx - 1.0) + gy[y1, x1] * (fy - 1.0)

    a = n00 + ux * (n10 - n00)
    b = n01 + ux * (n11 - n01)
    return a + uy * (b - a)


def fbm(rng: np.random.Generator, size: int, octaves: int,
        base_freq: int = 3, lacunarity: float = 2.0, gain: float = 0.5,
        min_feature_cells: float = 4.0) -> np.ndarray:
    """`min_feature_cells` caps the highest octave.

    The tile only carries large-scale structure; fine detail is added in game by
    the vanilla noise. Octaves whose period approaches one cell are not detail,
    they are aliasing, and they read as a uniform crinkle over the whole map.
    """
    total = np.zeros((size, size))
    amp, freq, norm = 1.0, float(base_freq), 0.0
    limit = max(1.0, size / max(min_feature_cells, 1.0))
    for _ in range(octaves):
        f = max(1, int(round(freq)))
        if f >= size or f > limit:
            break
        total += amp * _perlin(rng, size, f)
        norm += amp
        amp *= gain
        freq *= lacunarity
    return total / max(norm, 1e-9)


def ridged(rng: np.random.Generator, size: int, octaves: int,
           base_freq: int = 3, lacunarity: float = 2.0, gain: float = 0.5,
           min_feature_cells: float = 4.0) -> np.ndarray:
    """Folded noise -- produces long ridge lines rather than isotropic blobs."""
    total = np.zeros((size, size))
    amp, freq, norm = 1.0, float(base_freq), 0.0
    limit = max(1.0, size / max(min_feature_cells, 1.0))
    for _ in range(octaves):
        f = max(1, int(round(freq)))
        if f >= size or f > limit:
            break
        total += amp * (1.0 - np.abs(_perlin(rng, size, f)) * 2.0)
        norm += amp
        amp *= gain
        freq *= lacunarity
    return total / max(norm, 1e-9)


def box_blur(a: np.ndarray, radius: int, passes: int = 2) -> np.ndarray:
    """Separable box blur via cumulative sums; repeated passes approximate a Gaussian."""
    if radius < 1:
        return a.astype(np.float64, copy=True)
    out = a.astype(np.float64, copy=True)
    for _ in range(passes):
        for axis in (0, 1):
            out = np.swapaxes(out, 0, axis)
            pad = np.pad(out, ((radius + 1, radius), (0, 0)), mode="edge")
            cs = np.cumsum(pad, axis=0)
            out = (cs[2 * radius + 1:] - cs[:-(2 * radius + 1)]) / (2.0 * radius + 1.0)
            out = np.swapaxes(out, 0, axis)
    return out
