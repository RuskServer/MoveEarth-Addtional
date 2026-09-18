"""Simulation parameters for MoveEarth terrain tiles."""
from __future__ import annotations

from dataclasses import dataclass, field, asdict
import json


@dataclass
class Config:
    # --- tile geometry ---
    seed: int = 20260917
    size: int = 1024                 # grid resolution in cells (square)
    blocks_per_cell: int = 8         # world blocks covered by one cell
    ocean_margin_blocks: int = 512   # forced-ocean band inside the tile edge
    margin_irregularity: float = 0.55  # noise on the margin so the forced edge
                                       # coastline is not a straight line

    # --- world mapping (Minecraft Y) ---
    min_y: int = -56                 # deepest ocean floor
    sea_y: int = 63
    max_y: int = 272                 # highest peak

    # --- plates ---
    plate_count: int = 18
    continental_fraction: float = 0.40   # share of plates that are continental
    lloyd_iterations: int = 3
    plate_warp: float = 0.30         # domain warp, as a fraction of tile size
    plate_warp_freq: int = 3
    uplift_falloff_cells: float = 22.0   # how far orogeny spreads from a boundary
    crest_freq: int = 24             # structure *within* a belt: sub-ridges, gaps
                                     # and along-strike segmentation. At the belt
                                     # scale, not the continent scale
    uplift_strength: float = 1.7
    rift_strength: float = 0.55
    craton_relief: float = 0.30      # large-scale variation inside continents
    shelf_blur_cells: float = 14.0   # blur on the crust step; this is what widens
                                     # the continental shelf and slope

    # --- base noise riding on top of the tectonic field ---
    noise_octaves: int = 9
    noise_amplitude: float = 0.60
    noise_lacunarity: float = 2.0
    noise_gain: float = 0.60      # more energy in the higher octaves; at 0.5 the
                                  # field is all broad swells and no hill country
    hill_amplitude: float = 0.55  # ridged relief over continental crust
    hill_freq: int = 10
    min_feature_cells: float = 4.0  # smallest period the tile is allowed to carry

    # --- land / sea ---
    land_fraction: float = 0.30      # sea level is chosen to hit this exactly
    shelf_depth_blocks: float = 34.0  # water this shallow counts as shelf

    # --- erosion ---
    erosion_steps: int = 200
    stream_power_k: float = 0.22
    stream_power_m: float = 0.5
    stream_power_n: float = 1.0
    erosion_dt: float = 0.9
    uplift_per_step: float = 0.014
    apply_hypsometry: bool = True
    craton_uplift: float = 0.35   # steady uplift over continental crust. Without
                                  # it the plains between the belts wear flat and
                                  # drainage degenerates into parallel D8 lines
    hill_uplift: float = 0.50
    uplift_noise: float = 0.30    # stands in for bedrock heterogeneity. Uniform
                                  # uplift lets incision plane the lowlands into
                                  # a true plane, and D8 on a plane gives a comb
                                  # of parallel straight rivers
    uplift_noise_freq: int = 8
    # Angle of repose, tied to how resistant the bedrock is. A single angle
    # makes a uniform landscape: every slope relaxes to the same steepness, so
    # no cliff survives anywhere and raising the angle just tilts the whole map.
    # Real cliffs are resistant beds that outlasted the softer rock around them.
    talus_soft_degrees: float = 30.0
    talus_hard_degrees: float = 78.0
    # Resistant rock also incises more slowly, which is what leaves it standing
    # proud as the ground around it is cut away.
    hard_rock_erodibility: float = 0.30
    talus_degrees: float = 36.0      # angle of repose. Expressed as an angle and
                                     # converted per step, because a threshold in
                                     # raw simulation units is meaningless: it
                                     # depends on the height range and on
                                     # blocks_per_cell, and if it lands too high
                                     # thermal erosion silently never fires and
                                     # every summit stays a smooth dome
    thermal_rate: float = 0.32
    thermal_every: int = 2

    # --- hydrology solver ---
    fill_rounds: int = 60
    fill_epsilon: float = 1e-7
    route_jitter: float = 3e-4   # breaks D8 ties on flats; without it rivers run
                                 # dead straight along the grid axes
    accum_iterations: int = 40       # per erosion step (warm started)
    accum_final_iterations: int = 900

    # --- rivers ---
    # Fraction of the tile draining through a cell before it counts as a
    # channel. At 0.0004 the network was dense enough to read as clutter --
    # short parallel tributaries everywhere -- so it is set to about half
    # that density. The trunk rivers are unaffected; what goes is the
    # headwaters, and river_width_reference keeps the survivors their size.
    river_threshold: float = 0.0009
    # Width is anchored to an absolute discharge, not to the seeding threshold.
    # Tying the two together means raising the threshold to thin out the
    # network also shrinks every river that survives it, which is the opposite
    # of what thinning it is for: the point is fewer channels of the same size.
    river_width_reference: float = 0.0004
    river_min_width: float = 3.0     # width at the reference discharge, in blocks
    river_max_width: float = 110.0   # a trunk river at its mouth
    # Width grows as discharge^exponent. A hydraulically faithful 0.5 leaves the
    # widest channel about a quarter of the intended maximum, because the spread
    # of accumulation across one tile is far narrower than across a continent's
    # worth of real catchments. 0.8 spends the whole width range.
    river_width_exponent: float = 0.8
    river_reach_blocks: float = 200.0  # how far the distance field is carried
    # Carving is specified in world blocks and applied after the height mapping.
    # Doing it in simulation units, before the hypsometric remap, is what left
    # the channels about one block deep: the remap compresses the lowlands hard
    # and the int16 rounding then swallowed most of what was left.
    river_depth_blocks: float = 3.5    # depth of the narrowest stream
    river_depth_scale: float = 1.15    # extra depth per sqrt(width)
    river_max_depth_blocks: float = 11.0
    # Banks scale with the channel. A fixed width gave a 72 block river a five
    # block bank, so a big river read as a rectangular trench cut into the map
    # rather than as a valley.
    river_bank_blocks: float = 5.0     # floor, for the narrowest streams
    river_bank_ratio: float = 0.8      # bank width as a share of channel width
    river_freeboard_blocks: float = 2.0  # channel surface to water surface
    # The aquifer builds a rock barrier wherever two neighbouring cells disagree
    # about their fluid level. A tight radius put that disagreement right at the
    # channel edge, walling every river in and leaving gaps in the water. A wide
    # radius makes both sides of the channel answer with the same level, so the
    # disagreement moves out onto the valley sides where the ground is solid and
    # a barrier is invisible.
    river_water_radius: float = 64.0
    # ...and it stops at ground that has climbed well clear of the water, so the
    # raised table cannot spill over a nearby drop.
    # The aquifer asks about one jittered point per 16x12x16 cell, so a narrow
    # stream is usually missed and the whole cell falls back to sea level -- dry
    # bed, and grass grows in it. The tolerance has to be loose enough that a
    # point landing up the valley side still reports the stream.
    river_water_elevation_tolerance: float = 56.0
    # Vanilla's 3D noise shifts the surface by about 32*noise/factor blocks. At
    # the default factor that is +-3 to 4 blocks, which is deeper than a small
    # channel, so the bed was being randomly lifted above its own water line and
    # streams came out broken. Raising the factor inside the channel flattens
    # that noise where precision matters, without touching the rest of the map.
    factor_base: float = 3.5
    factor_channel: float = 16.0
    # Vanilla makes its sharp alpine peaks by adding a high frequency noise to
    # depth, scaled by jaggedness. We had pinned that to zero, which is why the
    # mountains came out as smooth swells. Driven from the tile's own roughness
    # it sharpens ridges without touching the lowlands.
    # A cliff is not a steeper version of a hillside, it is a different rock.
    # One jaggedness for the whole world therefore has no good setting: low
    # leaves smooth swells everywhere, high makes every slope spiky. So the
    # ceiling is raised and the bedrock hardness decides how much of it each
    # place gets -- soft ground keeps less than it had before, hard ground far
    # more. This is also the only lever that reaches block scale: the world is
    # 328 blocks tall across an 8192 block tile, so the heightmap itself cannot
    # carry a 2:1 slope however the simulation is tuned.
    jagged_max: float = 0.45
    jagged_soft: float = 0.20    # share of jagged_max that the softest rock keeps
    jagged_exponent: float = 1.6
    # Minecraft only places water at sea level or at the aquifer's coarse
    # 40-block bands, so a channel above sea level is a dry ditch however deep it
    # is cut. Rivers below this height are pulled down to hold water; higher
    # reaches stay dry valleys, which is also what vanilla does.
    river_sea_reach_y: float = 92.0
    river_blend_blocks: float = 30.0
    river_hollow_cells: float = 4.0  # a channel has to sit in a hollow; sheet
                                     # flow down an even slope is not a river
    river_contrast: float = 2.5      # and it has to carry more than its banks

    # --- climate ---
    equator_temp: float = 1.0
    pole_temp: float = -0.55
    lapse_rate: float = 0.9          # temperature drop over full elevation range
    wind_from_west: bool = True
    itcz_width: float = 0.16         # wet equatorial belt
    subtropical_centre: float = 0.34  # the desert latitudes
    subtropical_width: float = 0.13
    subtropical_dryness: float = 0.78
    midlat_centre: float = 0.64       # the storm track
    midlat_width: float = 0.18
    trade_wind_latitude: float = 0.38  # below this latitude the wind reverses,
                                       # so rain shadows do not all face one way
    windward_bonus: float = 2.5
    evaporation: float = 0.055
    rain_base: float = 0.007   # per-column depletion over land; too high and the
                               # air is wrung dry within a few hundred blocks of
                               # the coast, leaving no gradient across a continent
    orographic: float = 5.5
    moisture_decay: float = 0.004

    # --- erosion layer (vanilla's erosion climate parameter) ---
    erosion_window_cells: float = 6.0

    # --- rendering ---
    hillshade_exaggeration: float = 0.05   # multiplied by `size`

    # --- regions ---
    region_unit_area_blocks: float = 4_000_000.0
    max_regions_per_continent: int = 6
    islet_threshold_blocks: float = 250_000.0
    min_region_area_blocks: float = 900_000.0

    @property
    def tile_blocks(self) -> int:
        return self.size * self.blocks_per_cell

    @property
    def cell_area_blocks(self) -> float:
        return float(self.blocks_per_cell * self.blocks_per_cell)

    def to_json(self) -> str:
        return json.dumps(asdict(self), indent=2, sort_keys=True)

    @classmethod
    def from_overrides(cls, **kw) -> "Config":
        known = {f.name for f in cls.__dataclass_fields__.values()}
        bad = set(kw) - known
        if bad:
            raise SystemExit(f"unknown config keys: {sorted(bad)}")
        return cls(**kw)
