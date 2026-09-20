package com.ruskserver.moveearth_addtional.region;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Works out what material an ore block or a vein output is, from convention
 * tags alone, with no Minecraft in it so it can be tested.
 *
 * <p>The whole region system hangs on one question — "is this thing iron?" —
 * and the tempting answer is a table of block ids. A table is wrong here: it
 * has to be edited every time the modpack gains an ore mod, and it is edited by
 * whoever notices, which is nobody. The `c:` namespace already carries the
 * answer, because ore mods ship those tags so that recipes from other mods work
 * at all. So the only vocabulary this reads is `c:`, and a pack change needs no
 * code change.
 *
 * <p>A thing that cannot be resolved returns empty, and the caller is expected
 * to <em>allow</em> it rather than block it. Silently deleting an unlabelled
 * mod's ore from every region would be a far worse failure than letting one
 * through, and the probe command exists to make the unlabelled ones visible.
 */
public final class MaterialResolver {

    /** The only namespace that counts as a cross-mod vocabulary. */
    public static final String CONVENTION = "c";

    /** Ore blocks: {@code c:ores/<material>}. */
    public static final List<String> ORE_PREFIXES = List.of("ores/");

    /**
     * Vein outputs, most specific first.
     *
     * <p>Order matters. A vein that drops raw iron carries {@code
     * c:raw_materials/iron}; one that drops an ingot directly carries {@code
     * c:ingots/iron}. Both mean iron, but a single item can sit in several of
     * these, so the list fixes which reading wins instead of leaving it to
     * whichever tag happens to be iterated first.
     *
     * <p>{@code ores/} and {@code nuggets/} are here because veins drop those
     * shapes too: the netherite vein drops the ore block itself, and the nether
     * gold vein drops only nuggets. Without them both veins go unnamed.
     */
    public static final List<String> VEIN_OUTPUT_PREFIXES =
            List.of("raw_materials/", "ores/", "gems/", "ingots/", "nuggets/", "dusts/");

    /**
     * What a single block or item resolved to.
     *
     * @param material  the name to use, or empty when nothing matched
     * @param candidates every material the tags suggested, in order. More than
     *                   one means the tags disagree; the probe reports these so
     *                   a mispackaged ore is found before it reaches worldgen,
     *                   rather than after a region quietly stops producing it.
     */
    public record Resolution(Optional<String> material, List<String> candidates) {

        public boolean ambiguous() {
            return candidates.size() > 1;
        }

        static Resolution unresolved() {
            return new Resolution(Optional.empty(), List.of());
        }
    }

    private MaterialResolver() { }

    /**
     * Resolves one block or item from the tags it carries.
     *
     * @param tagIds tag ids as {@code namespace:path}; anything outside {@link
     *               #CONVENTION} is ignored
     * @param prefixes the path prefixes to try, in order of preference
     */
    public static Resolution resolve(Collection<String> tagIds, List<String> prefixes) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Resolution.unresolved();
        }
        for (String prefix : prefixes) {
            // Sorted so that a tie between two equally valid tags picks the same
            // material on every server and every restart. An unstable choice
            // here would move ores between regions on a reload.
            Set<String> found = new TreeSet<>();
            for (String tagId : tagIds) {
                materialFromTag(tagId, prefix).ifPresent(found::add);
            }
            if (!found.isEmpty()) {
                List<String> candidates = List.copyOf(found);
                return new Resolution(Optional.of(candidates.get(0)), candidates);
            }
        }
        return Resolution.unresolved();
    }

    /**
     * Parses the operator's override lines into a lookup.
     *
     * <p>Measured on the live server, four resources cannot be named from tags
     * at all: coal carries only the flat tag {@code c:coal}, with no prefix to
     * key on, and Create Ore Excavation ships its raw diamond, emerald and
     * redstone with no {@code c:} tags whatever. Neither is a failure of the
     * generalisation — one is a gap in the convention, the other a gap in a
     * mod — and neither can be closed by reading more tags. This is the way
     * out, and it is deliberately a list an operator writes rather than a
     * table in the source, so that adding a mod never means editing code.
     *
     * <p>Lines that are not {@code id=material} are dropped rather than
     * throwing: a typo in a config file should cost one override, not the
     * server's start-up.
     */
    public static Map<String, String> parseOverrides(Collection<String> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        if (lines == null) {
            return Map.of();
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0 || equals == line.length() - 1) {
                continue;
            }
            String id = line.substring(0, equals).trim();
            String material = line.substring(equals + 1).trim();
            if (!id.isEmpty() && !material.isEmpty()) {
                out.put(id, material);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * Resolves by registry id first, then by tags.
     *
     * <p>The override wins. An operator who has written down what something is
     * has more information than the tags do, and the whole reason the line
     * exists is that the tags were wrong or missing.
     */
    public static Resolution resolve(String id, Collection<String> tagIds, List<String> prefixes,
                                     Map<String, String> overrides) {
        if (id != null && overrides != null) {
            String material = overrides.get(id);
            if (material != null) {
                return new Resolution(Optional.of(material), List.of(material));
            }
        }
        return resolve(tagIds, prefixes);
    }

    /** Resolves an ore block from the tags it carries. */
    public static Resolution resolveOre(Collection<String> tagIds) {
        return resolve(tagIds, ORE_PREFIXES);
    }

    /** Resolves one of a vein's output items from the tags it carries. */
    public static Resolution resolveVeinOutput(Collection<String> tagIds) {
        return resolve(tagIds, VEIN_OUTPUT_PREFIXES);
    }

    /**
     * What a vein is, from the tags of its outputs in recipe order.
     *
     * <p>Takes the first output that resolves to anything, and stops. The
     * obvious alternative — collect every material and require all of them to
     * be allowed — was measured against the real recipes and is wrong twice
     * over. Create Ore Excavation's netherite vein drops ancient debris plus
     * gold nuggets, netherrack and magma; collecting materials makes that vein
     * need both netherite scrap <em>and</em> gold to be permitted in the same
     * region, so with both as exclusive strategic resources it would generate
     * nowhere. Reading only the first output is no better on its own: that
     * vein's diamond sibling leads with an untagged item and would go unnamed
     * although its second output names it.
     *
     * <p>First-that-resolves gets both right, because a drilling recipe lists
     * what the vein is before what falls out of it. A vein is one resource with
     * byproducts, not a set of equals, and the byproducts must not decide where
     * it can exist.
     */
    public static Optional<String> veinMaterial(Collection<? extends Collection<String>> outputTagIds) {
        return veinMaterial(List.of(), outputTagIds, Map.of());
    }

    /**
     * As above, with the outputs' registry ids so overrides can apply.
     *
     * <p>The ids line up with the tag sets by position. A vein whose first
     * output an operator has named resolves to that, which is what makes the
     * override usable for Create Ore Excavation's own untagged raw items.
     */
    public static Optional<String> veinMaterial(List<String> outputIds,
                                                Collection<? extends Collection<String>> outputTagIds,
                                                Map<String, String> overrides) {
        int index = 0;
        for (Collection<String> tagIds : outputTagIds) {
            String id = index < outputIds.size() ? outputIds.get(index) : null;
            index++;
            Optional<String> material = resolve(id, tagIds, VEIN_OUTPUT_PREFIXES, overrides).material();
            if (material.isPresent()) {
                return material;
            }
        }
        return Optional.empty();
    }

    /**
     * The material a single tag names, if it is a convention tag under the
     * prefix. {@code c:ores} on its own names no material and is not one.
     */
    private static Optional<String> materialFromTag(String tagId, String prefix) {
        if (tagId == null) {
            return Optional.empty();
        }
        int colon = tagId.indexOf(':');
        if (colon < 0 || !CONVENTION.equals(tagId.substring(0, colon))) {
            return Optional.empty();
        }
        String path = tagId.substring(colon + 1);
        if (!path.startsWith(prefix) || path.length() == prefix.length()) {
            return Optional.empty();
        }
        return Optional.of(path.substring(prefix.length()));
    }
}
