package com.ruskserver.moveearth_addtional.compat.mekanism;

import java.util.Set;

/**
 * Pure recipe-id policy used to keep Mekanism's chemistry and nuclear chain
 * while removing shortcuts that replace MoveEarth's power, logistics, storage,
 * combat and regional-resource systems.
 */
public final class MekanismRecipePolicy {
    public static final String CORE_NAMESPACE = "mekanism";
    public static final String GENERATORS_NAMESPACE = "mekanismgenerators";

    private static final Set<String> BLOCKED_GENERATORS = Set.of(
            "generator/heat",
            "generator/solar",
            "generator/advanced_solar",
            "generator/wind",
            "generator/bio",
            "generator/gas_burning"
    );

    private static final Set<String> BLOCKED_GENERATOR_EQUIPMENT = Set.of(
            "module_geothermal_generator_unit",
            "module_solar_recharging_unit"
    );

    /**
     * Fusion is intentionally held back until fission balance has been proven
     * in live play. Blocking both the multiblock and its fuel chain prevents a
     * datapack or recipe-viewer path from presenting it as currently available.
     */
    private static final Set<String> BLOCKED_FUSION = Set.of(
            "activating/tritium",
            "chemical_infusing/fusion_fuel",
            "hohlraum",
            "laser_focus_matrix",
            "reactor/controller",
            "reactor/frame",
            "reactor/glass",
            "reactor/logic_adapter",
            "reactor/port",
            "rotary/deuterium",
            "rotary/fusion_fuel",
            "rotary/tritium",
            "separator/heavy_water"
    );

    private static final Set<String> BLOCKED_LASERS = Set.of(
            "laser",
            "laser_amplifier",
            "laser_tractor_beam"
    );

    private static final Set<String> BLOCKED_SPS = Set.of(
            "sps_casing",
            "sps_port",
            "supercharged_coil"
    );

    private static final Set<String> BLOCKED_LOGISTICS = Set.of(
            "digital_miner",
            "cardboard_box",
            "teleporter",
            "portable_teleporter",
            "teleporter_frame",
            "teleportation_core",
            "quantum_entangloporter",
            "qio_dashboard",
            "qio_drive_array",
            "qio_drive_base",
            "qio_drive_hyper_dense",
            "qio_drive_time_dilating",
            "qio_drive_supermassive",
            "qio_importer",
            "qio_exporter",
            "qio_redstone_adapter",
            "portable_qio_dashboard",
            "robit",
            "personal_barrel",
            "personal_chest"
    );

    private static final Set<String> BLOCKED_EQUIPMENT = Set.of(
            "atomic_disassembler",
            "meka_tool",
            "mekasuit_helmet",
            "mekasuit_bodyarmor",
            "mekasuit_pants",
            "mekasuit_boots",
            "jetpack",
            "jetpack_armored",
            "flamethrower",
            "free_runners",
            "free_runners_armored",
            "module_jetpack_unit",
            "module_teleportation_unit"
    );

    private static final Set<String> BLOCKED_CREATE_REPLACEMENTS = Set.of(
            "energized_smelter",
            "crusher",
            "combiner",
            "purification_chamber",
            "precision_sawmill",
            "formulaic_assemblicator",
            "fuelwood_heater"
    );

    private static final Set<String> SIMPLE_ORE_RESOURCES = Set.of(
            "coal",
            "diamond",
            "emerald",
            "fluorite",
            "lapis_lazuli",
            "quartz",
            "redstone"
    );

    private MekanismRecipePolicy() {
    }

    public static RemovalCategory classify(String namespace, String path) {
        if (namespace == null || path == null) {
            return RemovalCategory.NONE;
        }
        if (GENERATORS_NAMESPACE.equals(namespace)) {
            if (BLOCKED_GENERATORS.contains(path)) {
                return RemovalCategory.INDEPENDENT_GENERATOR;
            }
            if (BLOCKED_FUSION.contains(path)) {
                return RemovalCategory.FUSION;
            }
            return BLOCKED_GENERATOR_EQUIPMENT.contains(path)
                    ? RemovalCategory.COMBAT_EQUIPMENT
                    : RemovalCategory.NONE;
        }
        if (!CORE_NAMESPACE.equals(namespace)) {
            return RemovalCategory.NONE;
        }
        if (BLOCKED_LOGISTICS.contains(path)
                || path.startsWith("bin/")
                || path.startsWith("transmitter/logistical_transporter/")) {
            return RemovalCategory.LOGISTICS_BYPASS;
        }
        if (BLOCKED_EQUIPMENT.contains(path)) {
            return RemovalCategory.COMBAT_EQUIPMENT;
        }
        if (BLOCKED_LASERS.contains(path)) {
            return RemovalCategory.DIRECTED_ENERGY_WEAPON;
        }
        if (BLOCKED_CREATE_REPLACEMENTS.contains(path)
                || path.startsWith("factory/")
                || path.startsWith("tier_installer/")) {
            return RemovalCategory.CREATE_REPLACEMENT;
        }
        if (path.startsWith("energy_cube/")) {
            return RemovalCategory.ENERGY_BYPASS;
        }
        if (BLOCKED_SPS.contains(path)
                || "antiprotonic_nucleosynthesizer".equals(path)
                || path.startsWith("nucleosynthesizing/")) {
            return RemovalCategory.ENDGAME_MATTER;
        }
        if (isOreMultiplicationPath(path)) {
            return RemovalCategory.ORE_MULTIPLICATION;
        }
        return RemovalCategory.NONE;
    }

    public static boolean shouldRemove(String namespace, String path) {
        return classify(namespace, path) != RemovalCategory.NONE;
    }

    public static boolean isBlockedLaserBlock(String namespace, String path) {
        return CORE_NAMESPACE.equals(namespace) && BLOCKED_LASERS.contains(path);
    }

    static boolean isOreMultiplicationPath(String path) {
        if (!path.startsWith("processing/")) {
            return false;
        }

        String[] segments = path.split("/");
        if (segments.length < 3) {
            return false;
        }

        // Mekanism's x3-x5 chains consistently use these intermediate folders.
        String stage = segments[2];
        if ("slurry".equals(stage)
                || "crystal".equals(stage)
                || "shard".equals(stage)
                || "clump".equals(stage)
                || "dirty_dust".equals(stage)) {
            return true;
        }

        // x2 ore/raw-ore recipes live below dust/, while reversible ingot/dust
        // recipes are intentionally retained for alloy and chemistry use.
        if ("dust".equals(stage) && segments.length >= 4) {
            String source = segments[3];
            return "from_ore".equals(source)
                    || "from_raw_ore".equals(source)
                    || "from_raw_block".equals(source);
        }

        // Gems and similar resources use a flatter processing/<name>/from_ore id.
        return SIMPLE_ORE_RESOURCES.contains(segments[1])
                && ("from_ore".equals(stage) || "from_deepslate_ore".equals(stage));
    }

    public enum RemovalCategory {
        NONE,
        INDEPENDENT_GENERATOR,
        FUSION,
        LOGISTICS_BYPASS,
        COMBAT_EQUIPMENT,
        DIRECTED_ENERGY_WEAPON,
        CREATE_REPLACEMENT,
        ENERGY_BYPASS,
        ORE_MULTIPLICATION,
        ENDGAME_MATTER
    }
}
