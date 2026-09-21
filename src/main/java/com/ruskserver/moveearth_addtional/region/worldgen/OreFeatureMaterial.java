package com.ruskserver.moveearth_addtional.region.worldgen;

import com.mojang.serialization.JsonOps;
import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.region.MaterialResolver;
import com.ruskserver.moveearth_addtional.region.MaterialResolver.Resolution;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Works out which ore a placed feature generates.
 *
 * <p>Shared by the gate that wraps features and the probe that reports on them,
 * so that what a diagnosis says is named is exactly what gets gated. Two copies
 * of this rule would drift, and the drift would only show as ore quietly
 * appearing in the wrong place. For the same reason the answer and the reason
 * for it come out of one call: they were computed separately once, and a
 * feature could in principle be named by one and explained away by the other.
 *
 * <p>Two ways of reading a feature, in order. {@link OreConfiguration} is the
 * vanilla shape and is read directly. Everything else is read through the
 * feature's own codec — the feature is written out as the JSON a datapack would
 * hold, and every block id in it is a candidate. That second route exists
 * because measuring the live server showed the first one is not enough: every
 * Mekanism ore, Create's striated ores and Incendium's came back "not an ore
 * feature", which put uranium — an exclusive resource — in every region while
 * its deposits were correctly confined to one. A mod's own config class cannot
 * be read without depending on that mod; its codec can, because every feature
 * has one and it is the same API a datapack goes through.
 */
public final class OreFeatureMaterial {

    /**
     * What a feature turned out to be.
     *
     * @param material     the material to gate on, or empty to leave the feature alone
     * @param reason       why it is empty, in words an operator can act on; null when named
     * @param suspectOre whether an unnamed feature looks like ore anyway. This
     *                   is what the audit warns about, so it has to be narrow:
     *                   "places any block" would sweep in every sand disk, lava
     *                   flow and magma patch and bury the one line that matters
     *                   under thirty that do not. It is recorded here rather
     *                   than recovered by reading the reason text, which would
     *                   break the moment the wording changed.
     * @param blocks     every block the feature places, in the order its data
     *                   names them
     * @param conventionTags the {@code c:} tags those blocks carry, which is what
     *                   an unresolved entry needs in order to say whether the mod
     *                   tagged nothing or tagged something this does not read
     * @param resolution the full resolution behind {@code material}, carrying the
     *                   candidates so the probe can report a mispackaged ore
     */
    public record Reading(Optional<String> material, String reason, boolean suspectOre,
                          List<String> blocks, List<String> conventionTags,
                          Resolution resolution) { }

    /** Block ids shown in a reason, enough to recognise a feature by. */
    private static final int BLOCKS_SHOWN = 6;

    /** Nothing named it. */
    private static final Resolution UNRESOLVED = new Resolution(Optional.empty(), List.of());

    private OreFeatureMaterial() { }

    /** The material a feature places, or empty when it is not a recognisable ore. */
    public static Optional<String> of(PlacedFeature feature) {
        return read(feature).material();
    }

    /** Why a feature could not be named. Empty string when it was. */
    public static String whyUnnamed(PlacedFeature feature) {
        String reason = read(feature).reason();
        return reason == null ? "" : reason;
    }

    /**
     * Reads a feature, following nested ones.
     *
     * <p>{@code getFeatures} already flattens selectors and random-choice
     * wrappers, so a selector holding one ore resolves to the ore.
     *
     * <p>A feature that places more than one material stays unnamed. Gating it
     * on whichever material came first would confine the others to that
     * material's regions without anything saying so, and leaving a feature open
     * only ever generates too much ore, never too little.
     */
    public static Reading read(PlacedFeature feature) {
        Map<String, String> overrides =
                MaterialResolver.parseOverrides(RegionResourceConfig.materialOverrides());
        Set<String> materials = new LinkedHashSet<>();
        Set<String> blocks = new LinkedHashSet<>();
        Set<String> conventionTags = new TreeSet<>();
        Resolution resolution = UNRESOLVED;
        boolean sawOreConfiguration = false;

        for (ConfiguredFeature<?, ?> configured : feature.getFeatures().toList()) {
            List<String> candidates;
            if (configured.config() instanceof OreConfiguration ore) {
                sawOreConfiguration = true;
                candidates = new ArrayList<>();
                for (OreConfiguration.TargetBlockState target : ore.targetStates) {
                    candidates.add(idOf(target.state));
                }
            } else {
                candidates = blocksInCodec(configured);
            }
            for (String blockId : candidates) {
                if (blockId == null || !blocks.add(blockId)) {
                    continue;
                }
                List<String> tagIds = tagsOf(blockId);
                tagIds.stream()
                        .filter(tag -> tag.startsWith(MaterialResolver.CONVENTION + ":"))
                        .forEach(conventionTags::add);
                Resolution candidate = MaterialResolver
                        .resolve(blockId, tagIds, MaterialResolver.ORE_PREFIXES, overrides);
                if (candidate.material().isPresent() && materials.add(candidate.material().get())) {
                    // The first block that names anything speaks for the feature;
                    // its candidates are what the probe reports when the tags on
                    // one block disagree with each other.
                    if (resolution.material().isEmpty()) {
                        resolution = candidate;
                    }
                }
            }
        }

        List<String> seen = List.copyOf(blocks);
        List<String> tags = List.copyOf(conventionTags);
        if (materials.size() == 1) {
            String material = materials.iterator().next();
            return new Reading(Optional.of(material), null, true, seen, tags, resolution);
        }
        boolean suspect = conventionTags.stream().anyMatch(tag -> tag.startsWith("c:ores"))
                || blocks.stream().anyMatch(MaterialResolver::namedLikeOre);
        String reason;
        if (materials.size() > 1) {
            reason = "places several materials: " + materials;
            suspect = true;
        } else if (blocks.isEmpty()) {
            reason = "not an ore feature";
            suspect = false;
        } else if (sawOreConfiguration) {
            reason = conventionTags.isEmpty()
                    ? "ore, but no c: tags at all: " + show(blocks)
                    : "ore, c: tags are " + conventionTags;
        } else {
            reason = (suspect ? "looks like ore but " : "") + "no c:ores/* on any of " + show(blocks);
        }
        return new Reading(Optional.empty(), reason, suspect, seen, tags, UNRESOLVED);
    }

    /**
     * Every block id in a feature's own serialised form, in the order it is
     * written.
     *
     * <p>Order is what makes this usable: a config writes what it places before
     * or alongside what it replaces, and the host stones it replaces carry no
     * {@code c:ores/} tag, so they fall out on their own without this needing
     * to know which field means what. That is the point — it knows nothing
     * about any mod's config shape, only that a block id is a block id.
     *
     * <p>Plain {@link JsonOps} rather than a registry-aware one: this runs from
     * a biome modifier, which has no registry access, and the ore configs seen
     * so far hold block states and tag names, neither of which needs a lookup.
     * A config that does need one fails to encode and is reported as unreadable
     * rather than silently treated as holding no ore.
     */
    private static List<String> blocksInCodec(ConfiguredFeature<?, ?> configured) {
        var encoded = ConfiguredFeature.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, configured)
                .result();
        if (encoded.isEmpty()) {
            return List.of();
        }
        // Tag names, rule types and block-state values all live in the same
        // strings; only the ones the block registry knows are kept.
        return JsonBlockScan.blockIds(encoded.get(), OreFeatureMaterial::isBlockId);
    }

    private static boolean isBlockId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    private static List<String> tagsOf(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return List.of();
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block.defaultBlockState().getTags().map(TagKey::location).map(Object::toString)
                .toList();
    }

    private static String idOf(BlockState state) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static String show(Set<String> blocks) {
        if (blocks.size() <= BLOCKS_SHOWN) {
            return blocks.toString();
        }
        List<String> shown = new ArrayList<>(blocks).subList(0, BLOCKS_SHOWN);
        return shown + " (+" + (blocks.size() - BLOCKS_SHOWN) + " more)";
    }
}
