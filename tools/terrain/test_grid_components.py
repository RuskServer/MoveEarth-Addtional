"""Connected components must agree with a plain flood fill, always.

The previous implementation was right on every small example anyone would write
by hand and wrong on the real map, so the only test worth having is one that
compares against an obviously-correct reference on masks awkward enough to
break a clever algorithm.
"""
from __future__ import annotations

import collections
import unittest

import numpy as np

from terraingen.grid import connected_components


def flood_fill(mask: np.ndarray) -> np.ndarray:
    """8-connected labelling by breadth-first search. Slow and obviously right."""
    h, w = mask.shape
    out = np.zeros((h, w), dtype=np.int64)
    label = 0
    for z0 in range(h):
        for x0 in range(w):
            if not mask[z0, x0] or out[z0, x0]:
                continue
            label += 1
            out[z0, x0] = label
            queue = collections.deque([(x0, z0)])
            while queue:
                x, z = queue.popleft()
                for dz in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, nz = x + dx, z + dz
                        if 0 <= nx < w and 0 <= nz < h and mask[nz, nx] and not out[nz, nx]:
                            out[nz, nx] = label
                            queue.append((nx, nz))
    return out


def partition(labels: np.ndarray, mask: np.ndarray) -> set:
    """Components as sets of cells, so the comparison ignores label numbering."""
    groups = collections.defaultdict(set)
    flat_labels = labels.ravel()
    for index in np.flatnonzero(mask.ravel()):
        groups[int(flat_labels[index])].add(int(index))
    return {frozenset(cells) for cells in groups.values()}


class ConnectedComponentTests(unittest.TestCase):

    def assertMatchesReference(self, mask: np.ndarray) -> None:
        self.assertEqual(partition(connected_components(mask), mask),
                         partition(flood_fill(mask), mask))

    def test_empty_and_full(self):
        self.assertMatchesReference(np.zeros((8, 8), dtype=bool))
        self.assertMatchesReference(np.ones((8, 8), dtype=bool))

    def test_diagonal_touch_is_one_component(self):
        mask = np.zeros((4, 4), dtype=bool)
        mask[0, 0] = mask[1, 1] = mask[2, 2] = True
        self.assertEqual(1, connected_components(mask).max())

    def test_separated_blobs_stay_separate(self):
        mask = np.zeros((7, 7), dtype=bool)
        mask[0:2, 0:2] = True
        mask[5:7, 5:7] = True
        self.assertEqual(2, connected_components(mask).max())

    def test_long_spiral_is_one_component(self):
        """A snake whose ends are far apart measured along the land.

        This is the shape that defeats label propagation: it crawls along the
        body a cell or so per round, so any fixed round limit eventually cuts a
        landmass in half and leaves a straight seam where the fronts stopped.
        """
        size = 121
        mask = np.zeros((size, size), dtype=bool)
        y = x = size // 2
        mask[y, x] = True
        step, turn = 1, 0
        moves = [(0, 1), (1, 0), (0, -1), (-1, 0)]
        running = True
        while running:
            for _ in range(2):
                dy, dx = moves[turn % 4]
                for _ in range(step):
                    y, x = y + dy, x + dx
                    if not (0 <= y < size and 0 <= x < size):
                        running = False
                        break
                    mask[y, x] = True
                if not running:
                    break
                turn += 1
            step += 1
        self.assertEqual(1, connected_components(mask).max())

    def test_random_masks_match_the_reference(self):
        rng = np.random.default_rng(12345)
        for density in (0.15, 0.35, 0.5, 0.7):
            for _ in range(3):
                self.assertMatchesReference(rng.random((40, 40)) < density)

    def test_blobby_mask_at_tile_scale(self):
        """Winding landmasses at a size where the old version actually failed."""
        rng = np.random.default_rng(7)
        field = rng.random((256, 256))
        for _ in range(6):
            field = (field
                     + np.roll(field, 1, 0) + np.roll(field, -1, 0)
                     + np.roll(field, 1, 1) + np.roll(field, -1, 1)) / 5.0
        self.assertMatchesReference(field > field.mean())


if __name__ == "__main__":
    unittest.main()
