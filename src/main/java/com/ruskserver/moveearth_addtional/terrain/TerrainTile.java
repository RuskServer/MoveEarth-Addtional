package com.ruskserver.moveearth_addtional.terrain;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One precomputed terrain tile: a rectangle of world covered by a set of layers.
 *
 * <p>Layers are mapped rather than read into the heap. A tile is a few tens of
 * megabytes and is touched from the chunk generation threads, so paging it in on
 * demand costs less than holding every layer of every tile resident.
 */
public final class TerrainTile {
    private final Path directory;
    private final int originX;
    private final int originZ;
    private final int sizeCells;
    private final int blocksPerCell;
    private final int sizeBlocks;
    private final int seaY;
    private final int minY;
    private final int maxY;
    private final Map<String, TerrainLayer> layers;
    private final RiverShape riverShape;
    private final RiverNetwork riverNetwork;
    private final double jaggedMax;
    private final double jaggedSoft;
    private final double jaggedExponent;
    private final List<SpawnAnchor> spawnAnchors;

    private TerrainTile(Path directory, JsonObject meta, Map<String, TerrainLayer> layers) throws IOException {
        this.directory = directory;
        this.originX = meta.get("origin_x").getAsInt();
        this.originZ = meta.get("origin_z").getAsInt();
        this.sizeCells = meta.get("size_cells").getAsInt();
        this.blocksPerCell = meta.get("blocks_per_cell").getAsInt();
        this.sizeBlocks = meta.get("size_blocks").getAsInt();
        this.seaY = meta.get("sea_y").getAsInt();
        this.minY = meta.get("min_y").getAsInt();
        this.maxY = meta.get("max_y").getAsInt();
        this.layers = layers;
        this.riverShape = readRiverShape(meta);
        JsonObject shaping = meta.has("shaping") ? meta.getAsJsonObject("shaping") : new JsonObject();
        this.jaggedMax = get(shaping, "jagged_max", 0.45);
        this.jaggedSoft = get(shaping, "jagged_soft", 0.20);
        this.jaggedExponent = get(shaping, "jagged_exponent", 1.6);
        this.spawnAnchors = readSpawnAnchors(meta);
        RiverNetwork network = null;
        if (meta.has("river") && meta.getAsJsonObject("river").has("segments_file")) {
            Path segmentsFile = directory.resolve(meta.getAsJsonObject("river").get("segments_file").getAsString());
            java.util.List<RiverNetwork.Segment> segments = new java.util.ArrayList<>();
            try (Reader reader = Files.newBufferedReader(segmentsFile)) {
                for (var element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    var s = element.getAsJsonArray();
                    if (s.size() != 8) throw new IOException("Invalid river segment in " + segmentsFile);
                    segments.add(new RiverNetwork.Segment(s.get(0).getAsDouble(), s.get(1).getAsDouble(),
                            s.get(2).getAsDouble(), s.get(3).getAsDouble(), s.get(4).getAsDouble(),
                            s.get(5).getAsDouble(), s.get(6).getAsDouble(), s.get(7).getAsDouble()));
                }
            }
            network = new RiverNetwork(segments, riverShape);
        }
        riverNetwork = network;
    }

    /** The most inland point of a landmass, where a spawn should sit. */
    public record SpawnAnchor(int continent, int x, int z, int surfaceY,
                              double inlandBlocks, double areaBlocks) { }

    private static List<SpawnAnchor> readSpawnAnchors(JsonObject meta) {
        if (!meta.has("spawn_anchors")) {
            return List.of();
        }
        List<SpawnAnchor> anchors = new java.util.ArrayList<>();
        for (var element : meta.getAsJsonArray("spawn_anchors")) {
            JsonObject anchor = element.getAsJsonObject();
            anchors.add(new SpawnAnchor(
                    anchor.get("continent").getAsInt(),
                    anchor.get("x").getAsInt(),
                    anchor.get("z").getAsInt(),
                    anchor.get("surface_y").getAsInt(),
                    get(anchor, "inland_blocks", 0.0),
                    get(anchor, "area_blocks", 0.0)));
        }
        return List.copyOf(anchors);
    }

    /** Largest landmass first, so the first entry is the main continent. */
    public List<SpawnAnchor> spawnAnchors() {
        return spawnAnchors;
    }

    private static RiverShape readRiverShape(JsonObject meta) {
        if (!meta.has("river")) {
            return RiverShape.NONE;
        }
        JsonObject river = meta.getAsJsonObject("river");
        RiverShape fallback = RiverShape.NONE;
        return new RiverShape(
                get(river, "min_width", fallback.minWidth()),
                get(river, "max_width", fallback.maxWidth()),
                get(river, "reach_blocks", fallback.reachBlocks()),
                get(river, "depth_blocks", fallback.depthBlocks()),
                get(river, "depth_scale", fallback.depthScale()),
                get(river, "max_depth_blocks", fallback.maxDepth()),
                get(river, "bank_blocks", fallback.bankBlocks()),
                get(river, "bank_ratio", fallback.bankRatio()),
                get(river, "sea_reach_y", fallback.seaReachY()),
                get(river, "blend_blocks", fallback.blendBlocks()),
                get(river, "freeboard_blocks", fallback.freeboardBlocks()),
                get(river, "water_radius", fallback.waterRadius()),
                get(river, "water_elevation_tolerance", fallback.waterElevationTolerance()),
                get(river, "factor_base", fallback.factorBase()),
                get(river, "factor_channel", fallback.factorChannel()));
    }

    private static double get(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    public RiverShape riverShape() {
        return riverShape;
    }

    /** How strongly a point sits inside a channel: one at the centre, zero past the banks. */
    public double channelInfluence(double blockX, double blockZ) {
        var river = sampleRiver(blockX, blockZ);
        double width = river.width();
        if (width <= 0.0) {
            return 0.0;
        }
        double half = width * 0.5;
        double bank = RiverProfile.bank(width, riverShape);
        double distance = river.distance();
        if (distance <= half) {
            return 1.0;
        }
        if (distance >= half + bank) {
            return 0.0;
        }
        double t = (distance - half) / bank;
        return 1.0 - t * t * (3.0 - 2.0 * t);
    }

    /**
     * How much of vanilla's 3D noise to let through: one normally, zero inside a
     * channel.
     *
     * <p>Reading Epic Terrain (MIT) showed it drops {@code base_3d_noise} from
     * {@code sloped_cheese} altogether, so its surface lands exactly where the
     * 2D graph says. That is the right lever here too: the noise was displacing
     * channel beds by more than a small channel is deep. Gating it rather than
     * removing it keeps the cliffs and overhangs everywhere else.
     */
    public double noiseGate(double blockX, double blockZ) {
        return 1.0 - channelInfluence(blockX, blockZ);
    }

    /**
     * Vanilla's jaggedness, from the tile's roughness and its bedrock.
     *
     * <p>Pinning this to zero is what left the mountains as smooth swells: it is
     * the term vanilla uses to build sharp peaks. Rough ground gets it, flat
     * ground and channels do not.
     *
     * <p>Roughness alone is not enough to decide how much, because it only says
     * the ground is broken, not whether the rock can stand up. Applied evenly it
     * makes every upland equally spiky, and that is what made the terrain look
     * wrong: cliff and hillside were the same material with different amounts of
     * noise on them. The hardness layer splits them -- resistant rock keeps a
     * face, soft rock stays a slope -- and squaring it keeps the sharpest
     * setting to the minority of the map that has earned it. It matters more
     * here than anywhere else upstream, because jaggedness is the only term that
     * acts at block scale; the heightmap is quantised to whole cells and cannot
     * produce a cliff at all.
     */
    public double jaggedness(double blockX, double blockZ) {
        if (!has("erosion")) {
            return 0.0;
        }
        double shaped = Math.pow(roughness(blockX, blockZ), jaggedExponent);
        return jaggedMax * shaped * lithology(blockX, blockZ) * noiseGate(blockX, blockZ);
    }

    /**
     * How broken the ground is here, zero to one.
     *
     * <p>From its own layer rather than from the erosion layer, which used to
     * serve both. Erosion is respaced onto vanilla's band edges before it is
     * written, because the biome table reads those bands literally -- it hands
     * every inland cell in the top one to swamp -- and after that respacing the
     * layer no longer measures how rough anything is. Older tiles have only the
     * erosion layer, and it is still the better of the two answers available.
     */
    public double roughness(double blockX, double blockZ) {
        double value = has("roughness")
                ? sample("roughness", blockX, blockZ) / 255.0
                : (1.0 - sample("erosion", blockX, blockZ) / 127.0) * 0.5;
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * How much of {@link #jaggedness} the rock here is entitled to, in
     * {@code jagged_soft..1}. One where the tile carries no hardness layer.
     */
    public double lithology(double blockX, double blockZ) {
        if (!has("hardness")) {
            return 1.0;
        }
        return lithologyOf(sample("hardness", blockX, blockZ) / 255.0, jaggedSoft);
    }

    /** The mapping alone, so it can be checked without a tile on disk. */
    static double lithologyOf(double hardness, double soft) {
        double hard = Math.max(0.0, Math.min(1.0, hardness));
        return soft + (1.0 - soft) * hard * hard;
    }

    /**
     * Vanilla's shaping factor, raised inside channels.
     *
     * <p>The surface is displaced by roughly {@code 32 * noise / factor} blocks.
     * At the default factor that is several blocks, which is more than a small
     * channel is deep, so the bed was being lifted above its own water line at
     * random and streams came out in pieces. Raising the factor flattens that
     * noise where the carve needs to be exact, and the bank blend keeps the rest
     * of the terrain untouched.
     */
    public double shapingFactor(double blockX, double blockZ) {
        double influence = channelInfluence(blockX, blockZ);
        return riverShape.factorBase()
                + (riverShape.factorChannel() - riverShape.factorBase()) * influence;
    }

    /**
     * Which way the channel runs here, or {@link RiverCurrent#NONE}.
     *
     * <p>Answered only inside the carved bowl. Beyond it there is no channel to
     * have a direction, and the nearest segment's would be a direction from
     * somewhere else.
     */
    public RiverCurrent riverCurrent(double blockX, double blockZ) {
        if (riverNetwork == null || !(channelInfluence(blockX, blockZ) > 0.0)) {
            return RiverCurrent.NONE;
        }
        var river = sampleRiver(blockX, blockZ);
        return new RiverCurrent(river.flowX(), river.flowZ(),
                riverStrengthOf(river.width(), river.slope(), riverShape));
    }

    static double riverStrengthOf(double width, double slope, RiverShape shape) {
        double widthRange = Math.max(1.0D, shape.maxWidth() - shape.minWidth());
        double widthScore = Math.sqrt(Math.max(0.0D, Math.min(1.0D,
                (width - shape.minWidth()) / widthRange)));
        double slopeScore = Math.max(0.0D, Math.min(1.0D, slope / 0.04D));
        return Math.max(0.0D, Math.min(1.0D, 0.25D + widthScore * 0.60D + slopeScore * 0.15D));
    }

    /**
     * Water surface height of the channel near this position, or
     * {@link Integer#MIN_VALUE} when there is none within reach.
     */
    public int riverWaterLevel(int blockX, int blockZ) {
        if (!has("river_water_y") || !has("river_dist")) {
            return Integer.MIN_VALUE;
        }
        var river = sampleRiver(blockX, blockZ);
        double distance = river.distance();
        double width = river.width();
        if (width <= 0 || distance > Math.max(riverShape.waterRadius(), width * 0.5)) {
            return Integer.MIN_VALUE;
        }
        double level = river.water();
        if (level <= 0.0) {
            return Integer.MIN_VALUE;
        }
        // A sanity bound rather than a shaping one. Everything that reads this
        // is already confined to the carved bowl by channelInfluence, so the
        // ground here should be within a carve depth of the water; a reading far
        // above it means the sampled channel is not the one under this column,
        // and answering with its level would put water up a hillside.
        double surface = sample("height", blockX, blockZ);
        if (surface - level > riverShape.waterElevationTolerance()) {
            return Integer.MIN_VALUE;
        }
        return (int) Math.round(level);
    }

    /**
     * Surface height with the river channels cut in.
     *
     * <p>The channels are not baked into the height layer: a baked carve cannot
     * be narrower than the tile resolution, so every river came out about thirty
     * blocks wide. New tiles sample connected segments at block resolution;
     * legacy tiles fall back to their approximate interpolated distance field.
     */
    public double carvedSurface(double blockX, double blockZ) {
        double surface = sample("height", blockX, blockZ);
        if (!has("river_dist") || !has("river_width")) {
            return surface;
        }
        var river = sampleRiver(blockX, blockZ);
        double distance = river.distance();
        double width = river.width();
        if (has("river_water_y")) {
            double water = river.water();
            if (water > 0) return surface - RiverProfile.carveAtWater(
                    distance, width, surface, water, riverShape);
        }
        return surface - RiverProfile.carve(distance, width, surface, seaY, riverShape);
    }

    public static TerrainTile load(Path directory) throws IOException {
        Path descriptor = directory.resolve("tile.json");
        JsonObject meta;
        try (Reader reader = Files.newBufferedReader(descriptor)) {
            meta = JsonParser.parseReader(reader).getAsJsonObject();
        }
        int size = meta.get("size_cells").getAsInt();
        Map<String, TerrainLayer> layers = new HashMap<>();
        JsonObject declared = meta.getAsJsonObject("layers");
        for (String name : declared.keySet()) {
            JsonObject entry = declared.getAsJsonObject(name);
            Path file = directory.resolve(entry.get("file").getAsString());
            TerrainLayer.Type type = TerrainLayer.Type.parse(entry.get("dtype").getAsString());
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                ByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0L, channel.size());
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                layers.put(name, new TerrainLayer(buffer, type, size));
            }
        }
        TerrainTile tile = new TerrainTile(directory, meta, layers);
        Moveearth_addtional.LOGGER.info("Loaded terrain tile {} at ({}, {}), {} blocks, layers {}",
                directory.getFileName(), tile.originX, tile.originZ, tile.sizeBlocks, layers.keySet());
        return tile;
    }

    private RiverNetwork.Sample sampleRiver(double blockX, double blockZ) {
        if (riverNetwork != null) return riverNetwork.sample(blockX - originX, blockZ - originZ);
        // Legacy tiles carry rasters rather than segments, and a raster has no
        // direction in it, so those rivers have no current.
        return new RiverNetwork.Sample(sample("river_dist", blockX, blockZ),
                sample("river_width", blockX, blockZ), sample("river_water_y", blockX, blockZ),
                0.0, 0.0, 0.0);
    }

    public boolean covers(int blockX, int blockZ) {
        return blockX >= originX && blockX < originX + sizeBlocks
                && blockZ >= originZ && blockZ < originZ + sizeBlocks;
    }

    public boolean has(String layer) {
        return layers.containsKey(layer);
    }

    /**
     * Bilinear sample of a layer in world coordinates.
     *
     * <p>Cell centres sit half a cell in from the tile origin, so the sample
     * position is shifted by half a cell before interpolating. Without that the
     * field is offset by eight blocks and coastlines land beside their rivers.
     */
    public double sample(String layer, double blockX, double blockZ) {
        if (riverNetwork != null && (layer.equals("river_dist") || layer.equals("river_width")
                || layer.equals("river_water_y"))) {
            var river = riverNetwork.sample(blockX - originX, blockZ - originZ);
            return switch (layer) {
                case "river_dist" -> river.distance();
                case "river_width" -> river.width();
                default -> river.water();
            };
        }
        TerrainLayer data = layers.get(layer);
        if (data == null) {
            return Double.NaN;
        }
        double cx = (blockX - originX) / blocksPerCell - 0.5;
        double cz = (blockZ - originZ) / blocksPerCell - 0.5;
        int x0 = (int) Math.floor(cx);
        int z0 = (int) Math.floor(cz);
        double fx = cx - x0;
        double fz = cz - z0;

        double v00 = data.at(x0, z0);
        double v10 = data.at(x0 + 1, z0);
        double v01 = data.at(x0, z0 + 1);
        double v11 = data.at(x0 + 1, z0 + 1);
        double top = v00 + (v10 - v00) * fx;
        double bottom = v01 + (v11 - v01) * fx;
        return top + (bottom - top) * fz;
    }

    /**
     * Raw cell value of a layer, with no interpolation.
     *
     * <p>For layers that hold an identity rather than a quantity. Bilinear
     * sampling averages its four neighbours, which is right for a height field
     * and wrong for an id: between region 3 and region 7 it produces region 5,
     * a region that is not there and whose resources would generate in a strip
     * along every border.
     */
    public int sampleNearest(String layer, double blockX, double blockZ) {
        TerrainLayer data = layers.get(layer);
        if (data == null) {
            return -1;
        }
        return (int) Math.round(data.at(cellX(blockX), cellZ(blockZ)));
    }

    /** Cell column containing a world X. */
    public int cellX(double blockX) {
        return (int) Math.floor((blockX - originX) / blocksPerCell);
    }

    /** Cell row containing a world Z. */
    public int cellZ(double blockZ) {
        return (int) Math.floor((blockZ - originZ) / blocksPerCell);
    }

    /** Raw cell value by cell coordinates, for callers that walk the grid. */
    public int cellValue(String layer, int cellX, int cellZ) {
        TerrainLayer data = layers.get(layer);
        return data == null ? -1 : (int) Math.round(data.at(cellX, cellZ));
    }

    /** Width of the tile in cells. */
    public int sizeCells() {
        return sizeCells;
    }

    /** Distance in blocks from the tile edge; negative outside. Used to blend into open ocean. */
    public double insetFromEdge(double blockX, double blockZ) {
        double dx = Math.min(blockX - originX, originX + sizeBlocks - blockX);
        double dz = Math.min(blockZ - originZ, originZ + sizeBlocks - blockZ);
        return Math.min(dx, dz);
    }

    public int seaY() {
        return seaY;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    /** World X of the tile's corner. */
    public int originX() {
        return originX;
    }

    /** World Z of the tile's corner. */
    public int originZ() {
        return originZ;
    }

    public int blocksPerCell() {
        return blocksPerCell;
    }

    public Path directory() {
        return directory;
    }
}
