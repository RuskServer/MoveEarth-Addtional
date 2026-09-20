package com.ruskserver.moveearth_addtional.compat.rns;

import com.bmaster.createrns.content.deposit.spec.DepositSpec;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.region.MaterialResolver;
import com.ruskserver.moveearth_addtional.region.RegionProfiles;
import com.ruskserver.moveearth_addtional.region.RegionResolver;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Decides whether an ore deposit may generate where Create: Rock &amp; Stone
 * wants to put one.
 *
 * <p>Deposits are ordinary Minecraft structures, so enforcing a region needs
 * nothing but declining to generate: a structure that returns no generation
 * point simply does not appear, which is what every vanilla structure does when
 * its conditions are not met. The deposit scanner asks the same question
 * through the same vanilla structure search, so what players are told and what
 * is in the ground cannot drift apart.
 *
 * <p>Keyed on the structure object by identity, resolved once at start-up. The
 * structure is the receiver of the call being gated and cannot be mistaken for
 * anything else, and identity lookup keeps the per-chunk cost to a hash probe.
 *
 * <p>Every uncertainty answers yes. A deposit whose material cannot be named, a
 * region map that is not built, a spec registry this has not seen — all
 * generate as the mod intended.
 */
public final class RnsDepositGate {

    /** Deposit structure to the material it yields. Immutable once published. */
    private static volatile Map<Structure, String> materials = Map.of();

    /**
     * Whether the structure generator has ever asked.
     *
     * <p>The injection is optional so that an update to Rock &amp; Stone cannot
     * stop the server starting, and the price of that is a gate which could
     * quietly never run. This is how an operator tells "nothing was blocked"
     * from "the hook is not attached".
     */
    private static volatile boolean consulted = false;

    /**
     * Structure id to what it resolved to, in the order an operator reads them.
     *
     * <p>Kept separately from the identity map the gate uses, because the useful
     * thing to look at is the list of deposits that could <em>not</em> be named,
     * and those are exactly the ones missing from that map.
     */
    private static volatile Map<String, String> resolutionReport = Map.of();

    /**
     * The deposits this mod adds, and the placement grids of the sets it does
     * not own.
     *
     * <p>Our deposits sit in their own structure set, because Rock & Stone
     * builds its own set at runtime and a datapack cannot append to it. Two
     * independent grids can land on the same chunk, and two deposits in one
     * chunk means two structures carved through each other. Since every
     * placement already passes through this gate, refusing ours where the mod's
     * own set has already claimed the chunk costs one comparison and removes
     * the collision entirely.
     *
     * <p>Ours yields rather than theirs so the mod's own distribution is left
     * exactly as it was; a deposit of ours simply does not appear at that one
     * position.
     */
    private static volatile Set<Structure> ownDeposits = Set.of();
    private static volatile List<RandomSpreadStructurePlacement> foreignPlacements = List.of();
    private static volatile long worldSeed = 0L;

    /** How many of our deposits stood aside for the mod's own, for diagnostics. */
    private static final java.util.concurrent.atomic.AtomicLong yielded =
            new java.util.concurrent.atomic.AtomicLong();

    private RnsDepositGate() { }

    /** Whether this deposit may generate at a chunk. Called from worldgen threads. */
    public static boolean allows(Structure deposit, ChunkPos chunk) {
        consulted = true;
        String material = materials.get(deposit);
        if (material == null) {
            return true;
        }
        if (ownDeposits.contains(deposit) && claimedByAnotherSet(chunk)) {
            yielded.incrementAndGet();
            return false;
        }
        int region = RegionResolver.regionAt(chunk.getMinBlockX(), chunk.getMinBlockZ());
        return RegionProfiles.allows(region, material);
    }

    /**
     * Resolves every deposit's material from its scanner icon.
     *
     * <p>Only overworld deposits are read at all; the region map describes the
     * surface and says nothing about another dimension. Of those, only the ones
     * this world can actually produce are kept, which takes two tests. The mod registers a spec for every metal it supports and
     * switches off the ones whose mod is missing, and a deposit that is off is
     * absent from the structure set and has no mining recipe. Separately, a
     * spec's icon only resolves when the item it names exists. Either failure
     * means the material is not a resource this world has, and assigning it
     * would give a region an exclusive claim on nothing.
     *
     * <p>The icon is not a decoration: the specs shipped with the mod give it as
     * an ordered list of convention tags — {@code #c:raw_materials/gold}, then
     * {@code #c:ores/gold}, and so on — which is exactly the vocabulary and
     * exactly the order this project already resolves ores by. It is also what
     * the scanner shows the player as "what you get here", so naming a deposit
     * by it means the gate and the player agree on what a deposit is.
     */
    public static void rebuild(MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        Map<String, String> overrides =
                MaterialResolver.parseOverrides(RegionResourceConfig.materialOverrides());
        Map<Structure, String> resolved = new IdentityHashMap<>();
        Map<String, String> report = new TreeMap<>();
        int total = 0;

        var structures = access.registryOrThrow(Registries.STRUCTURE);
        for (DepositSpec spec : access.registryOrThrow(DepositSpec.REGISTRY_KEY)) {
            total++;
            Structure structure = structures.get(spec.structureKey());
            if (structure == null) {
                continue;
            }
            String name = String.valueOf(spec.structureKey().location());
            // Rock & Stone keeps a spec for every metal it knows of and turns
            // the unusable ones off through this flag, which is the same flag
            // that keeps them out of the structure set and out of the mining
            // recipes. A deposit that is off does not generate and cannot be
            // mined, so treating its material as a resource this world has
            // would give some region an exclusive claim on nothing.
            // The region system covers the overworld and nothing else: the plan
            // leaves the Nether and the End uniform, and there is no mapping
            // from a region on the surface to a position in another dimension.
            // Left in, a nether deposit would be judged against whatever region
            // sits at the same x/z above it -- nether gold generating only over
            // the one region that happens to hold gold.
            if (!Level.OVERWORLD.equals(spec.dimension)) {
                report.put(name, "(" + spec.dimension.location() + ", outside the region system)");
                continue;
            }
            if (!spec.scannable) {
                report.put(name, "(disabled in this pack)");
                continue;
            }
            // The icon is resolved lazily and nothing has asked for it yet: the
            // mod resolves its own specs later in start-up, and reading before
            // that gives back nothing at all for every deposit. Resolving here
            // is also the availability test -- it fails exactly when the item a
            // deposit is named after does not exist, which is what happens to a
            // metal whose mod is absent.
            if (!spec.initialize(access)) {
                // Two icons have to resolve, and which one failed says what to
                // fix: a missing scanner icon means the resource itself is not
                // in this pack, a missing map icon means the deposit block's
                // item is. Reporting them as one thing sent the last diagnosis
                // down the wrong path.
                report.put(name, spec.getScannerIcon() == null
                        ? "(no scanner icon -- the item naming this resource does not exist)"
                        : "(no map icon -- the deposit block's item does not exist)");
                continue;
            }
            Optional<String> material = materialOf(spec, overrides);
            material.ifPresent(value -> resolved.put(structure, value));
            report.put(name, material.orElse("(unnamed, generates everywhere)"));
        }
        materials = resolved;
        resolutionReport = Map.copyOf(report);
        mapStructureSets(server, resolved.keySet());

        if (total > 0 && resolved.isEmpty()) {
            // Resolution reads item tags. If none of them bound yet, every
            // deposit would come out unnamed and nothing would ever be gated --
            // a world that quietly ignores its own resource rules. Saying so is
            // the only way that is distinguishable from a pack with no deposits.
            Moveearth_addtional.LOGGER.error(
                    "{} deposit(s) exist but none could be named. Nothing will be confined to "
                            + "a region. This usually means item tags were not bound yet.", total);
        }
        Moveearth_addtional.LOGGER.info(
                "Create: Rock & Stone: {} of {} deposit(s) named.", resolved.size(), total);
        report.forEach((name, material) ->
                Moveearth_addtional.LOGGER.info("  {} -> {}", name, material));
    }

    /** The material of one spec, by override first and then by the icon's tags. */
    static Optional<String> materialOf(DepositSpec spec, Map<String, String> overrides) {
        var icon = spec.getScannerIcon();
        if (icon == null) {
            return Optional.empty();
        }
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(icon));
        List<String> tags = icon.builtInRegistryHolder().tags()
                .map(TagKey::location).map(Object::toString).toList();
        return MaterialResolver.resolve(id, tags, MaterialResolver.VEIN_OUTPUT_PREFIXES, overrides)
                .material();
    }

    /** Whether some other mod's structure set already puts a deposit in this chunk. */
    private static boolean claimedByAnotherSet(ChunkPos chunk) {
        for (RandomSpreadStructurePlacement placement : foreignPlacements) {
            ChunkPos candidate = placement.getPotentialStructureChunk(worldSeed, chunk.x, chunk.z);
            if (candidate.x == chunk.x && candidate.z == chunk.z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sorts the deposit structure sets into ours and everyone else's.
     *
     * <p>Ownership is read from the set's own id, not from the structures in
     * it: a pack author who puts one of our deposits into their own set has
     * taken responsibility for its placement, and the collision rule should not
     * then fire on a set we do not control.
     */
    private static void mapStructureSets(MinecraftServer server, Set<Structure> known) {
        // Read from the world data, not from the overworld. This runs on
        // ServerAboutToStartEvent -- deliberately, because the gate has to be
        // ready before the first chunk -- and at that point no level exists
        // yet, so server.overworld() is still null.
        worldSeed = server.getWorldData().worldGenOptions().seed();
        Set<Structure> own = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<RandomSpreadStructurePlacement> foreign = new java.util.ArrayList<>();
        var sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        sets.entrySet().forEach(entry -> {
            var set = entry.getValue();
            boolean holdsDeposits = set.structures().stream()
                    .anyMatch(e -> known.contains(e.structure().value()));
            if (!holdsDeposits) {
                return;
            }
            if (Moveearth_addtional.MODID.equals(entry.getKey().location().getNamespace())) {
                set.structures().forEach(e -> own.add(e.structure().value()));
            } else if (set.placement() instanceof RandomSpreadStructurePlacement placement) {
                foreign.add(placement);
            }
        });
        ownDeposits = own;
        foreignPlacements = List.copyOf(foreign);
        Moveearth_addtional.LOGGER.info(
                "Deposit sets: {} of ours, {} others to stand aside for.",
                own.size(), foreign.size());
    }

    /** How many of our deposits have stood aside for another set's. */
    public static long yieldedToOtherSets() {
        return yielded.get();
    }

    /**
     * How many deposits of each material a chunk of land can expect.
     *
     * <p>Measured off the live structure sets rather than assumed. A material's
     * share of one grid position is its weight against the set's total, and how
     * often a position occurs is the square of the set's spacing -- both facts
     * the pack decides and neither safe to guess: the mod ships several
     * frequency variants, and a hand calculation came out three times under
     * what the world actually contains.
     *
     * <p>Used to keep an exclusive resource out of a region too small to hold
     * one. A material diluted to five percent of a pool, in a region with nine
     * positions, is half a deposit -- and half the time the region that is
     * supposed to be defined by that resource has none of it.
     */
    public static Map<String, Double> depositsPerChunk(MinecraftServer server) {
        Map<String, Double> out = new TreeMap<>();
        var sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for (StructureSet set : sets) {
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
                continue;
            }
            double spacing = Math.max(1, placement.spacing());
            double positionsPerChunk = 1.0 / (spacing * spacing);
            double totalWeight = set.structures().stream()
                    .mapToInt(StructureSet.StructureSelectionEntry::weight)
                    .filter(weight -> weight > 0).sum();
            if (totalWeight <= 0) {
                continue;
            }
            for (StructureSet.StructureSelectionEntry entry : set.structures()) {
                String material = materials.get(entry.structure().value());
                if (material == null || entry.weight() <= 0) {
                    continue;
                }
                out.merge(material, positionsPerChunk * (entry.weight() / totalWeight), Double::sum);
            }
        }
        return Map.copyOf(out);
    }

    /** Every material some deposit in this pack actually yields. */
    public static Set<String> availableMaterials() {
        return Set.copyOf(materials.values());
    }

    /** Structure id to material, or to why it has none. For diagnostics. */
    public static Map<String, String> resolutionReport() {
        return resolutionReport;
    }

    /** The material a deposit yields, or null when it could not be named. */
    public static String materialOf(Structure deposit) {
        return materials.get(deposit);
    }

    /** Whether the structure generator has called the gate at least once. */
    public static boolean consulted() {
        return consulted;
    }

    /** How many deposits have a material, for diagnostics. */
    public static int namedDepositCount() {
        return materials.size();
    }

    /** Forgets what it knew. Used when the server stops. */
    public static void clear() {
        materials = Map.of();
        resolutionReport = Map.of();
        ownDeposits = Set.of();
        foreignPlacements = List.of();
        yielded.set(0);
        consulted = false;
    }
}
