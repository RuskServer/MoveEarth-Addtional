"""Exact Euclidean distance transform, separable and free of scipy.

Felzenszwalb and Huttenlocher's lower-envelope algorithm: one pass per axis,
each O(n). The naive alternative -- relaxing a min-plus stencil until it settles
-- needs one pass per cell of reach, which is hopeless for a coast distance that
has to run thousands of blocks inland.
"""
from __future__ import annotations

import numpy as np

INF = 1e20


def _edt_1d(f: np.ndarray) -> np.ndarray:
    n = f.size
    d = np.empty(n)
    v = np.zeros(n, dtype=np.int64)
    z = np.empty(n + 1)
    k = 0
    v[0] = 0
    z[0] = -INF
    z[1] = INF
    for q in range(1, n):
        s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2.0 * q - 2.0 * v[k])
        while s <= z[k]:
            k -= 1
            s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2.0 * q - 2.0 * v[k])
        k += 1
        v[k] = q
        z[k] = s
        z[k + 1] = INF
    k = 0
    for q in range(n):
        while z[k + 1] < q:
            k += 1
        d[q] = (q - v[k]) ** 2 + f[v[k]]
    return d


def distance(mask: np.ndarray, cell_size: float = 1.0) -> np.ndarray:
    """Distance from every cell to the nearest True cell, in cell_size units."""
    if not mask.any():
        return np.full(mask.shape, INF)
    f = np.where(mask, 0.0, INF)
    for axis in (0, 1):
        f = np.swapaxes(f, 0, axis)
        for i in range(f.shape[1]):
            f[:, i] = _edt_1d(f[:, i])
        f = np.swapaxes(f, 0, axis)
    return np.sqrt(f) * cell_size


def signed_coast_distance(land: np.ndarray, cell_size: float = 1.0) -> np.ndarray:
    """Positive inland, negative offshore, measured to the shoreline."""
    inland = distance(~land, cell_size)
    offshore = distance(land, cell_size)
    return np.where(land, inland, -offshore)
