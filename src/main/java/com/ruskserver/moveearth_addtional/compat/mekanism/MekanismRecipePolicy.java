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
    public static final String TOOLS_NAMESPACE = "mekanismtools";

    private static final Set<String> BLOCKED_GENERATORS = Set.of(
            "generator/heat",
            // The basic solar generator is allowed: daylight-only and weak
            // (about 680 W), it is a mid-game electric source that cannot
            // rival steam. Advanced solar and wind stay out.
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
            "jetpack",
            "jetpack_armored",
            "flamethrower",
            "free_runners",
            "free_runners_armored",
            "module_jetpack_unit",
            "module_teleportation_unit",
            // The MekaSuit is allowed; flight, wall-clearing mobility and
            // vanilla night vision are not.
            "module_gravitational_modulating_unit",
            "module_elytra_unit",
            "module_hydraulic_propulsion_unit",
            "module_locomotive_boosting_unit",
            "module_vision_enhancement_unit"
    );

    private static final Set<String> BLOCKED_CREATE_REPLACEMENTS = Set.of(
            "crusher",
            "combiner",
            "purification_chamber",
            "precision_sawmill",
            "formulaic_assemblicator",
            "fuelwood_heater"
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
        if (TOOLS_NAMESPACE.equals(namespace)) {
            return isBlockedToolsRecipe(path) ? RemovalCategory.COMBAT_EQUIPMENT : RemovalCategory.NONE;
        }
        if (!CORE_NAMESPACE.equals(namespace)) {
            return RemovalCategory.NONE;
        }
        if (BLOCKED_LOGISTICS.contains(path)
                || path.startsWith("bin/")
                || path.startsWith("transmitter/logistical_transporter/")
                || "transmitter/diversion_transporter".equals(path)
                || "transmitter/restrictive_transporter".equals(path)) {
            return RemovalCategory.LOGISTICS_BYPASS;
        }
        if (BLOCKED_EQUIPMENT.contains(path)) {
            return RemovalCategory.COMBAT_EQUIPMENT;
        }
        if (BLOCKED_LASERS.contains(path)) {
            return RemovalCategory.DIRECTED_ENERGY_WEAPON;
        }
        // Fluids travel through Create's pipes and pumps. Pressurized tubes
        // stay: chemicals such as fissile fuel, nuclear waste and reactor steam
        // have no fluid form a Create pipe could carry.
        if (BLOCKED_CREATE_REPLACEMENTS.contains(path)
                || path.startsWith("transmitter/mechanical_pipe/")
                || path.startsWith("transmitter/thermodynamic_conductor/")
                || isBlockedFactory(path)
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

    /**
     * Smelting factories are the one factory line left in: ore smelting is
     * slowed on Create's fans, so power is what buys faster smelting. Tier
     * installers stay out because they upgrade any machine in place.
     */
    private static boolean isBlockedFactory(String path) {
        return path.startsWith("factory/") && !path.endsWith("/smelting");
    }

    /**
     * Mekanism Tools' refined obsidian gear (armor 31, toughness 5) out-armors
     * the MekaSuit; bronze, steel and osmium fill the gap left by netherite.
     */
    private static boolean isBlockedToolsRecipe(String path) {
        return path.startsWith("refined_obsidian/armor/")
                || path.startsWith("refined_obsidian/tools/")
                || "refined_obsidian/shield".equals(path);
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
        // The x2 tier (Enrichment Chamber: 3 raw ore -> 4 dust, gems from ore)
        // is allowed; it lives below dust/ or directly under the resource.
        String stage = segments[2];
        return "slurry".equals(stage)
                || "crystal".equals(stage)
                || "shard".equals(stage)
                || "clump".equals(stage)
                || "dirty_dust".equals(stage);
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
