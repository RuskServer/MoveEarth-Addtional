package com.ruskserver.moveearth_addtional.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Which materials the region system treats as exclusive, and which it varies.
 *
 * <p>These are names from the {@code c:} convention vocabulary, the same ones
 * the probe reports, not block or item ids. That is what lets the same two
 * lists control vanilla ores, Create's zinc and Create Ore Excavation's veins
 * at once, and keeps a new ore mod from needing a code change.
 *
 * <p>Kept in config rather than hardcoded because the right answer depends on
 * the modpack and on how the server plays, and it is the kind of thing an
 * operator will want to try a few settings of without a rebuild.
 */
public final class RegionResourceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUSIVE;
    private static final ModConfigSpec.DoubleValue EXCLUSIVE_OUTSIDE_SHARE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> COMMON;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> OVERRIDES;
    private static final ModConfigSpec.BooleanValue STRESS_FOLLOWS_DEPOSIT;
    private static final ModConfigSpec.DoubleValue STRESS_BASE;
    private static final ModConfigSpec.DoubleValue STRESS_PER_DEPOSIT;
    private static final ModConfigSpec.DoubleValue STRESS_EXCLUSIVE_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("regionResources");

        EXCLUSIVE = BUILDER
                .comment("Materials that generate only in the region they were assigned to.",
                        "Listed rarest first; the order decides which gets the most defensible",
                        "region and which the least.",
                        "",
                        "A material here must satisfy two conditions, and both have bitten:",
                        "  1. Something in the pack must actually produce it. Create: Rock &",
                        "     Stone registers a deposit for every metal it knows of, including",
                        "     ones whose mod is absent; those deposits generate and yield",
                        "     nothing. Names nothing produces are dropped with a warning rather",
                        "     than handing a region a resource that does not exist.",
                        "  2. Nothing essential may depend on it without another source. Zinc",
                        "     would make brass unobtainable for whole regions, and quartz would",
                        "     block Create's mid game were the Nether not outside this system.",
                        "",
                        "The default is emerald, diamond, gold, uranium and crude_oil. The",
                        "first three are always present -- Rock & Stone ships no diamond or",
                        "emerald deposit, so this mod adds them -- and none of them gates",
                        "anything. Uranium appears once Mekanism is installed and is the",
                        "cleanest exclusive in that pack: fission needs it and nothing else",
                        "does, so a region without it loses a branch rather than a tech tree.",
                        "",
                        "NEVER add osmium. Mekanism's Basic Control Circuit and Steel Casing",
                        "both take osmium, and between them they gate nearly every machine in",
                        "the mod, so confining it to one region would kill Mekanism in the other",
                        "seven. It is that pack's zinc. Tin and lead are milder versions of the",
                        "same problem and are worth checking against the pack before adding.",
                        "",
                        "A name nothing in the pack produces is dropped with a warning, so",
                        "listing uranium before Mekanism arrives is harmless.",
                        "",
                        "crude_oil is Create: Diesel Generators' oil. Diesel is a wholly",
                        "optional branch, so a region without oil loses nothing it needs, and",
                        "fuel is burned rather than kept -- which asks for a standing trade",
                        "rather than one shipment, unlike gold or diamond. That makes it the",
                        "one exclusive that keeps two regions talking after the first deal.",
                        "",
                        "Exclusive does not mean absent elsewhere; see exclusiveOutsideShare.")
                .defineList("exclusiveMaterials",
                        List.of("emerald", "diamond", "gold", "uranium", "crude_oil"),
                        () -> "", entry -> entry instanceof String name && !name.isBlank());

        EXCLUSIVE_OUTSIDE_SHARE = BUILDER
                .comment("How much of an exclusive resource exists outside the regions it was",
                        "assigned to, as a share of the normal amount.",
                        "",
                        "Zero is the strict reading: a region that was not given uranium has no",
                        "uranium at all, and the only way to get any is another nation. It makes",
                        "the map legible and the trade pressure absolute, and it also means a",
                        "region can never see, learn or bootstrap the resource it lacks -- the",
                        "first fission reactor is built by someone who has never held the ore.",
                        "",
                        "A small share instead leaves the resource findable but not farmable.",
                        "A nation can prove out the technology and run one of something; it",
                        "cannot supply itself. The risk is the obvious one: set this high enough",
                        "that a patient nation covers its own needs and the exclusivity is gone",
                        "while still appearing to be there. Raise it only with a reason to.",
                        "",
                        "Applied to fit what is being counted. Ore is thinned vein by vein, so",
                        "0.08 is roughly one vein in twelve. Oil and deposits are counted per",
                        "place rather than per amount, so the same figure means about one",
                        "chunk or site in twelve keeps its full yield and the rest hold none --",
                        "scaling those down instead would make every chunk uniformly poor,",
                        "which reads as a worse world rather than a rarer resource.")
                .defineInRange("exclusiveOutsideShare", 0.08D, 0.0D, 1.0D);


        COMMON = BUILDER
                .comment("Materials present in every region, with the amount varying by region.",
                        "Each region is richer in one of these than its neighbours and poorer in",
                        "another, which gives adjacent regions a reason to trade. A region holds",
                        "dozens of territories, so exclusive resources only ever give blocs a",
                        "reason to deal with each other; these give neighbours one.")
                .defineList("commonMaterials",
                        List.of("coal", "iron", "copper"),
                        () -> "", entry -> entry instanceof String name && !name.isBlank());

        OVERRIDES = BUILDER
                .comment("Manual material names for ores the convention tags cannot name,",
                        "as \"<ore or item id>=<material>\". Measured against the pack, one",
                        "matters: the coal deposit declares its icon as #minecraft:coals, which",
                        "has no prefix to read a material from. All eighteen others resolve on",
                        "their own. Anything still unnamed is allowed everywhere rather than",
                        "deleted, so this list is for gaining control, never for safety.")
                .defineList("materialOverrides", List.of("minecraft:coal=coal"),
                        () -> "", entry -> entry instanceof String line && line.contains("="));

        STRESS_FOLLOWS_DEPOSIT = BUILDER
                .comment("Make a mining rig's stress depend on what it is mining.",
                        "Rock & Stone charges one fixed impact whatever is under the head, so",
                        "a rig on diamond asks no more of a windmill than one on coal and the",
                        "resource tiers mean nothing to the power network. With this on, the",
                        "impact rises with the number of deposit blocks being worked and again",
                        "for the materials listed as exclusive.")
                .define("stressFollowsDeposit", true);

        STRESS_BASE = BUILDER
                .comment("Impact for a rig that is turning but mining nothing.",
                        "32 is the mod's own fixed value, so an idle rig costs what it always did.")
                .defineInRange("stressBase", 32.0D, 0.0D, 4096.0D);

        STRESS_PER_DEPOSIT = BUILDER
                .comment("Impact added per deposit block under the mine head.",
                        "A 1x1 head works one block, a 3x3 works up to nine, so widening the",
                        "head costs power as well as materials -- which is how the rest of",
                        "Create reads.")
                .defineInRange("stressPerDeposit", 16.0D, 0.0D, 1024.0D);

        STRESS_EXCLUSIVE_MULTIPLIER = BUILDER
                .comment("How much more an exclusive material costs to mine.",
                        "The resources regions fight over are also the ones that demand an",
                        "industry behind them. 1.0 removes the distinction.")
                .defineInRange("stressExclusiveMultiplier", 2.0D, 1.0D, 16.0D);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private RegionResourceConfig() { }

    /**
     * Whether the file has been read yet.
     *
     * <p>Biome modifiers run while registries load, which is before a server
     * exists and therefore before its config is read. Asking a config value for
     * itself then throws, and a throw there stops the world loading at all --
     * so the ore gate asks this first and falls back to the defaults, which is
     * the behaviour an untouched config would have given anyway.
     */
    public static boolean ready() {
        return SPEC.isLoaded();
    }

    public static List<String> exclusiveMaterials() {
        return ready() ? List.copyOf(EXCLUSIVE.get()) : List.of();
    }

    /**
     * The share of an exclusive resource that exists outside its own regions.
     *
     * <p>Falls back to zero before the config is read, which is the strict
     * reading rather than the generous one: worldgen that ran early would
     * otherwise scatter a resource the operator may have meant to confine, and
     * ore placed cannot be taken back out of a world.
     */
    public static double exclusiveOutsideShare() {
        return ready() ? EXCLUSIVE_OUTSIDE_SHARE.get() : 0.0D;
    }

    public static List<String> commonMaterials() {
        return ready() ? List.copyOf(COMMON.get()) : List.of();
    }

    public static List<String> materialOverrides() {
        return ready() ? List.copyOf(OVERRIDES.get()) : List.of();
    }

    public static boolean stressFollowsDeposit() {
        return !ready() || STRESS_FOLLOWS_DEPOSIT.get();
    }

    public static double stressBase() {
        return ready() ? STRESS_BASE.get() : 32.0D;
    }

    public static double stressPerDeposit() {
        return ready() ? STRESS_PER_DEPOSIT.get() : 16.0D;
    }

    public static double stressExclusiveMultiplier() {
        return ready() ? STRESS_EXCLUSIVE_MULTIPLIER.get() : 2.0D;
    }
}
