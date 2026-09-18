package com.ruskserver.moveearth_addtional.terrain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Reads a precomputed terrain tile as a density function.
 *
 * <p>Registered into {@code minecraft:worldgen/density_function_type}, which is
 * a plain vanilla registry, so the whole terrain is driven from an otherwise
 * ordinary {@code minecraft:noise} generator. Surface rules, aquifers, carvers,
 * structure placement and the biome system all keep working untouched.
 *
 * <p>Called once per noise cell during generation, on the chunk workers. The
 * store it reads is immutable once installed and a bilinear sample is cheaper
 * than the multi-octave noise stack it replaces.
 */
public record MapFieldDensityFunction(TerrainField field, double blendBlocks) implements DensityFunction.SimpleFunction {

    private static final Codec<TerrainField> FIELD_CODEC = Codec.STRING.comapFlatMap(
            name -> TerrainField.byName(name)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown terrain field: " + name)),
            TerrainField::serializedName);

    public static final MapCodec<MapFieldDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    FIELD_CODEC.fieldOf("field").forGetter(MapFieldDensityFunction::field),
                    Codec.DOUBLE.optionalFieldOf("blend_blocks", 384.0)
                            .forGetter(MapFieldDensityFunction::blendBlocks)
            ).apply(instance, MapFieldDensityFunction::new));

    public static final KeyDispatchDataCodec<MapFieldDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        TerrainTileStore store = TerrainTileStore.active();
        if (store == null) {
            // Generating before the tiles are installed would bake a broken world
            // that cannot be repaired afterwards, so fail loudly instead.
            throw new IllegalStateException(
                    "Terrain tiles are not loaded but world generation has started; refusing to generate terrain");
        }
        int x = context.blockX();
        int z = context.blockZ();
        TerrainTile tile = store.tileAt(x, z);
        if (tile == null) {
            return field.outside();
        }
        // OFFSET is the terrain surface, so it is the one field that has to see
        // the river channels cut into it
        // OFFSET is the terrain surface, so it is the one field that has to see
        // the river channels cut into it; FACTOR is derived from the same channels
        double value = switch (field) {
            case OFFSET -> field.convert(tile.carvedSurface(x, z), tile);
            case FACTOR -> tile.shapingFactor(x, z);
            case NOISE_GATE -> tile.noiseGate(x, z);
            case JAGGEDNESS -> tile.jaggedness(x, z);
            default -> field.convert(tile.sample(field.layer(), x, z), tile);
        };
        if (blendBlocks > 0.0) {
            double inset = tile.insetFromEdge(x, z);
            if (inset < blendBlocks) {
                double t = Math.max(0.0, inset) / blendBlocks;
                t = t * t * (3.0 - 2.0 * t);
                value = field.outside() + (value - field.outside()) * t;
            }
        }
        return value;
    }

    @Override
    public double minValue() {
        return Math.min(field.min(), field.outside());
    }

    @Override
    public double maxValue() {
        return Math.max(field.max(), field.outside());
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
