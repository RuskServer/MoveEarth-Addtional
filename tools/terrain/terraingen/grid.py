"""Grid primitives: neighbourhoods, connected components, pointer jumping.

Everything here is pure numpy. The two graph algorithms we need -- connected
components and forest-root finding -- are done with pointer jumping so they stay
vectorised instead of degrading into per-cell Python loops.
"""
from __future__ import annotations

import numpy as np

# 8-neighbourhood, (dy, dx)
OFFSETS = np.array(
    [(-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1)],
    dtype=np.int64,
)
DISTANCES = np.array([np.hypot(dy, dx) for dy, dx in OFFSETS], dtype=np.float64)


def neighbours(a: np.ndarray, fill) -> np.ndarray:
    """Return an (8, H, W) stack of `a` shifted into each neighbour direction."""
    h, w = a.shape
    pad = np.full((h + 2, w + 2), fill, dtype=a.dtype)
    pad[1:-1, 1:-1] = a
    out = np.empty((8,) + a.shape, dtype=a.dtype)
    for k, (dy, dx) in enumerate(OFFSETS):
        out[k] = pad[1 + dy: 1 + dy + h, 1 + dx: 1 + dx + w]
    return out


def flat_index(shape) -> np.ndarray:
    h, w = shape
    return np.arange(h * w, dtype=np.int64).reshape(h, w)


def neighbour_indices(shape) -> np.ndarray:
    """(8, H, W) flat indices of each neighbour; -1 where it falls outside."""
    idx = flat_index(shape)
    return neighbours(idx, -1)


def find_roots(recv: np.ndarray) -> np.ndarray:
    """Root of every node in a functional graph (each node has one successor).

    Pointer doubling: O(log depth) vectorised passes instead of walking chains.
    """
    root = recv.astype(np.int64, copy=True)
    for _ in range(64):
        nxt = root[root]
        if np.array_equal(nxt, root):
            break
        root = nxt
    return root


def connected_components(mask: np.ndarray) -> np.ndarray:
    """Label 8-connected components of a boolean mask. 0 means 'not in mask'.

    Hooking + pointer jumping. Converges in O(log n) rounds rather than the
    O(diameter) a plain min-propagation would need on a snaking landmass.
    """
    h, w = mask.shape
    n = h * w
    idx = flat_index(mask.shape)
    label = np.where(mask, idx, -1).astype(np.int64)

    nb_idx = neighbour_indices(mask.shape)
    nb_mask = neighbours(mask, False)
    # valid links: both endpoints inside the mask
    link_ok = nb_mask & mask[None, :, :] & (nb_idx >= 0)

    parent = np.where(mask.ravel(), np.arange(n, dtype=np.int64), np.arange(n, dtype=np.int64))
    for _ in range(64):
        lab = parent.reshape(h, w)
        nb_lab = np.where(link_ok, parent[np.where(nb_idx >= 0, nb_idx, 0)], np.iinfo(np.int64).max)
        best = nb_lab.min(axis=0)
        cand = np.minimum(lab, np.where(mask, best, lab))
        changed = cand != lab
        if not changed.any():
            break
        # hook: every node points at the smallest label it can see
        new_parent = cand.ravel()
        parent = np.minimum(parent, new_parent)
        # jump: flatten the forest so the next round sees component minima
        for _ in range(8):
            nxt = parent[parent]
            if np.array_equal(nxt, parent):
                break
            parent = nxt

    roots = parent.reshape(h, w)
    out = np.zeros((h, w), dtype=np.int64)
    if not mask.any():
        return out
    uniq, inv = np.unique(roots[mask], return_inverse=True)
    out[mask] = inv + 1
    return out


def component_sizes(labels: np.ndarray) -> np.ndarray:
    """Cell count per label; index 0 is the background count."""
    return np.bincount(labels.ravel())


def normalise(a: np.ndarray) -> np.ndarray:
    lo, hi = float(a.min()), float(a.max())
    if hi - lo < 1e-12:
        return np.zeros_like(a)
    return (a - lo) / (hi - lo)
