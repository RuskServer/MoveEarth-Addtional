"""Depression filling, D8 flow routing and flow accumulation.

Flow accumulation is what makes this a geomorphological simulation rather than a
noise stack: it is the input to the stream power law, and the river network falls
out of it directly instead of having to be faked.
"""
from __future__ import annotations

import numpy as np

from .grid import DISTANCES, flat_index, neighbour_indices, neighbours


def _sweep(w: np.ndarray, z: np.ndarray, eps: float, axis: int, reverse: bool) -> None:
    """Propagate the fill along one axis in place; vectorised across the other.

    Sequential along the scan axis on purpose. The recurrence is a clamp of unit
    slope, so it can be turned into a parallel prefix scan, and that was tried:
    it runs log2(n) passes over the whole array rather than n passes over one
    row, and came out ten times *slower*. With rows this long each step is
    already bound by data rather than by numpy call overhead, so trading n row
    passes for 9 full-array passes is simply nine times the work.
    """
    wt = np.swapaxes(w, 0, axis)
    zt = np.swapaxes(z, 0, axis)
    order = range(wt.shape[0] - 1, -1, -1) if reverse else range(wt.shape[0])
    prev = None
    for i in order:
        if prev is not None:
            wt[i] = np.maximum(zt[i], np.minimum(wt[i], prev + eps))
        prev = wt[i]


def _diagonal_sweep(w: np.ndarray, z: np.ndarray, eps: float,
                    flip_rows: bool, flip_cols: bool) -> None:
    """Carry the fill along a diagonal, one anti-diagonal at a time.

    Without this the diagonal component only advances through the eight
    neighbour pass, which moves it a single cell per round. A depression that
    runs diagonally then needs as many rounds as it is long: filling took
    twenty-four rounds where two would do, and it was four fifths of the whole
    erosion loop.
    """
    wt = w[::-1] if flip_rows else w
    zt = z[::-1] if flip_rows else z
    wt = wt[:, ::-1] if flip_cols else wt
    zt = zt[:, ::-1] if flip_cols else zt
    rows, cols = wt.shape
    step = eps * 1.4142135623730951
    for i in range(1, rows):
        # every cell takes from its up-left neighbour in this orientation
        wt[i, 1:] = np.maximum(zt[i, 1:], np.minimum(wt[i, 1:], wt[i - 1, :-1] + step))


def fill_depressions(z: np.ndarray, sea_level: float,
                     max_rounds: int = 40, eps: float = 1e-5) -> np.ndarray:
    """Planchon-Darboux. Directional sweeps first, then 8-neighbour cleanup.

    Plain iteration would advance one cell per pass and need thousands of passes
    across a continent; the sweeps carry the fill the whole way in one go. Both
    the axis-aligned and the diagonal directions are swept, so no direction is
    left to crawl through the neighbour pass.
    """
    h, w = z.shape
    open_cell = np.zeros((h, w), dtype=bool)
    open_cell[0, :] = open_cell[-1, :] = True
    open_cell[:, 0] = open_cell[:, -1] = True
    open_cell |= z <= sea_level

    filled = np.where(open_cell, z, np.inf).astype(np.float64)
    for _ in range(max_rounds):
        before = filled.copy()
        for axis, rev in ((0, False), (0, True), (1, False), (1, True)):
            _sweep(filled, z, eps, axis, rev)
        for flip_rows, flip_cols in ((False, False), (False, True), (True, False), (True, True)):
            _diagonal_sweep(filled, z, eps, flip_rows, flip_cols)
        nb = neighbours(filled, np.inf)
        cand = (nb + eps * DISTANCES[:, None, None]).min(axis=0)
        filled = np.maximum(z, np.minimum(filled, cand))
        if np.allclose(filled, before, rtol=0.0, atol=1e-9):
            break
    filled[~np.isfinite(filled)] = z[~np.isfinite(filled)]
    return filled


def receivers(surface: np.ndarray):
    """D8 steepest descent. Returns (recv_flat, dist_flat, is_outlet_flat).

    The pad value is -inf so border cells always drain off the tile edge, which
    is what makes the tile an island surrounded by open ocean.
    """
    h, w = surface.shape
    idx = flat_index((h, w))
    nb_s = neighbours(surface, -np.inf)
    nb_i = neighbour_indices((h, w))

    drop = (surface[None, :, :] - nb_s) / DISTANCES[:, None, None]
    k = np.argmax(drop, axis=0)
    best = np.take_along_axis(drop, k[None], axis=0)[0]

    recv = np.take_along_axis(nb_i, k[None], axis=0)[0]
    dist = DISTANCES[k]

    outlet = (recv < 0) | (best <= 0.0)
    recv = np.where(outlet, idx, recv)
    dist = np.where(outlet, 1.0, dist)
    return recv.ravel(), dist.ravel(), outlet.ravel()


def accumulate(recv_flat: np.ndarray, weight_flat: np.ndarray,
               iterations: int, warm: np.ndarray | None = None) -> np.ndarray:
    """Fixed-point flow accumulation: acc = weight + sum of donors' acc.

    Self-receivers (outlets and sinks) are excluded from the transfer, otherwise
    they would feed themselves and diverge.
    """
    n = recv_flat.size
    idx = np.arange(n, dtype=np.int64)
    moving = recv_flat != idx
    src = idx[moving]
    dst = recv_flat[moving]

    acc = weight_flat.copy() if warm is None else warm.copy()
    for _ in range(iterations):
        donor = np.bincount(dst, weights=acc[src], minlength=n)
        nxt = weight_flat + donor
        if np.allclose(nxt, acc, rtol=1e-6, atol=1e-9):
            acc = nxt
            break
        acc = nxt
    return acc
