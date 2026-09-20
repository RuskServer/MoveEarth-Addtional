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

    Union-find over the edge list, run to a fixed point. The previous version
    propagated the smallest label it could see and gave up after 64 rounds; on a
    512-cell tile it needed 91 and returned early without saying so, which split
    one landmass into two continents along a straight seam where the two fronts
    stopped. Silently wrong is the worst possible failure for this, because
    everything downstream -- region borders, spawn anchors, which resources are
    exclusive to where -- is built on the answer.

    Hooking roots to roots rather than nodes to labels is what makes it
    converge: a node pointing at a small label says nothing about the tree it
    belongs to, so two trees could keep pointing into each other forever.
    """
    h, w = mask.shape
    out = np.zeros((h, w), dtype=np.int64)
    if not mask.any():
        return out

    idx = flat_index(mask.shape)
    # Four directions carry all eight: every 8-neighbour link is the reverse of
    # one of these seen from the other endpoint.
    pairs = [
        (mask[:, :-1] & mask[:, 1:], idx[:, :-1], idx[:, 1:]),
        (mask[:-1, :] & mask[1:, :], idx[:-1, :], idx[1:, :]),
        (mask[:-1, :-1] & mask[1:, 1:], idx[:-1, :-1], idx[1:, 1:]),
        (mask[:-1, 1:] & mask[1:, :-1], idx[:-1, 1:], idx[1:, :-1]),
    ]
    u = np.concatenate([a[sel] for sel, a, _ in pairs])
    v = np.concatenate([b[sel] for sel, _, b in pairs])

    parent = np.arange(h * w, dtype=np.int64)
    # Each round at least halves the number of distinct roots, so the bound is
    # generous; reaching it means the algorithm is broken, not the terrain.
    for _ in range(64):
        parent = _compress(parent)
        ru, rv = parent[u], parent[v]
        hi, lo = np.maximum(ru, rv), np.minimum(ru, rv)
        differ = hi != lo
        if not differ.any():
            break
        np.minimum.at(parent, hi[differ], lo[differ])
    else:
        raise RuntimeError("connected_components did not converge; the labelling "
                           "would be wrong and must not be used")

    roots = _compress(parent).reshape(h, w)
    _, inv = np.unique(roots[mask], return_inverse=True)
    out[mask] = inv + 1
    return out


def _compress(parent: np.ndarray) -> np.ndarray:
    """Point every node straight at its root."""
    while True:
        nxt = parent[parent]
        if np.array_equal(nxt, parent):
            return parent
        parent = nxt


def component_sizes(labels: np.ndarray) -> np.ndarray:
    """Cell count per label; index 0 is the background count."""
    return np.bincount(labels.ravel())


def normalise(a: np.ndarray) -> np.ndarray:
    lo, hi = float(a.min()), float(a.max())
    if hi - lo < 1e-12:
        return np.zeros_like(a)
    return (a - lo) / (hi - lo)
