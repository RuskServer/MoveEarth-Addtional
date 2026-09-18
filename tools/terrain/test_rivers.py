import unittest
import numpy as np

from terraingen.config import Config
from terraingen import rivers


class RiverTests(unittest.TestCase):
    def test_seed_crosses_rejected_cells_to_ocean(self):
        land = np.array([[True, True, True, False]])
        recv = np.array([1, 2, 3, 3])
        actual = rivers.connect_downstream(np.array([[True, False, False, False]]), land, recv)
        self.assertTrue(actual.all())

    def test_water_does_not_climb_depression_outlet_or_confluence(self):
        # Two sources merge, then cross a terrain rise before reaching the sea.
        recv = np.array([2, 2, 3, 4, 4])
        height = np.array([[100, 85, 80, 95, 60]])
        widths = np.full_like(height, 3)
        water = rivers.water_surface(Config(), height, widths, 63, recv).ravel()
        self.assertTrue(np.all(water[recv] <= water))
        self.assertEqual(water[-1], 63)
        self.assertEqual(water[3], 78)

    def test_segments_include_diagonal_receiver_and_shared_endpoints(self):
        cfg = Config(blocks_per_cell=16)
        width = np.array([[3, 0], [0, 6]])
        water = np.array([[90, 0], [0, 80]])
        output = rivers.segments(cfg, width, water, np.array([3, 1, 2, 3]))
        self.assertEqual(output, [[8, 8, 24, 24, 3, 6, 90, 80]])

    def test_cycle_fails_instead_of_exporting_broken_water(self):
        with self.assertRaises(ValueError):
            rivers.water_surface(Config(), np.array([[90, 80]]), np.array([[3, 3]]),
                                 63, np.array([1, 0]))


if __name__ == "__main__":
    unittest.main()
