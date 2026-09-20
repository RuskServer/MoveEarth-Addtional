package com.ruskserver.moveearth_addtional.compat.mekanism;

import java.util.Set;

/** Registry-id policy for content that must remain unusable even if obtained without crafting. */
public final class MekanismRuntimeRestrictionPolicy {
    private static final String CORE = "mekanism";
    private static final String GENERATORS = "mekanismgenerators";

    private static final Set<String> CORE_BLOCKS = Set.of(
            "digital_miner",
            "cardboard_box",
            "teleporter",
            "teleporter_frame",
            "quantum_entangloporter",
            "qio_dashboard",
            "qio_drive_array",
            "qio_importer",
            "qio_exporter",
            "qio_redstone_adapter",
            "personal_barrel",
            "personal_chest",
            "energized_smelter",
            "crusher",
            "combiner",
            "purification_chamber",
            "precision_sawmill",
            "formulaic_assemblicator",
            "fuelwood_heater",
            "laser",
            "laser_amplifier",
            "laser_tractor_beam",
            "antiprotonic_nucleosynthesizer",
            "sps_casing",
            "sps_port",
            "supercharged_coil"
    );

    private static final Set<String> CORE_ITEMS = Set.of(
            "portable_teleporter",
            "teleportation_core",
            "qio_drive_base",
            "qio_drive_hyper_dense",
            "qio_drive_time_dilating",
            "qio_drive_supermassive",
            "portable_qio_dashboard",
            "robit",
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
            "module_geothermal_generator_unit",
            "module_solar_recharging_unit",
            "module_jetpack_unit",
            "module_teleportation_unit"
    );

    private static final Set<String> GENERATOR_BLOCKS = Set.of(
            "heat_generator",
            "solar_generator",
            "advanced_solar_generator",
            "wind_generator",
            "bio_generator",
            "gas_burning_generator",
            "fusion_reactor_controller",
            "fusion_reactor_frame",
            "fusion_reactor_port",
            "fusion_reactor_logic_adapter",
            "reactor_glass",
            "laser_focus_matrix"
    );

    private static final Set<String> GENERATOR_ITEMS = Set.of(
            "hohlraum",
            "module_geothermal_generator_unit",
            "module_solar_recharging_unit"
    );

    private MekanismRuntimeRestrictionPolicy() {
    }

    public static boolean isRestrictedBlock(String namespace, String path) {
        if (GENERATORS.equals(namespace)) {
            return GENERATOR_BLOCKS.contains(path);
        }
        if (!CORE.equals(namespace)) {
            return false;
        }
        return CORE_BLOCKS.contains(path)
                || isTiered(path, "_energy_cube")
                || isTiered(path, "_bin")
                || isTiered(path, "_logistical_transporter")
                || isFactory(path);
    }

    public static boolean isRestrictedItem(String namespace, String path) {
        if (GENERATORS.equals(namespace)) {
            return GENERATOR_ITEMS.contains(path) || GENERATOR_BLOCKS.contains(path);
        }
        if (!CORE.equals(namespace)) {
            return false;
        }
        return CORE_ITEMS.contains(path)
                || isRestrictedBlock(namespace, path)
                || isTiered(path, "_tier_installer");
    }

    private static boolean isTiered(String path, String suffix) {
        return path.endsWith(suffix) && (path.startsWith("basic_")
                || path.startsWith("advanced_")
                || path.startsWith("elite_")
                || path.startsWith("ultimate_")
                || path.startsWith("creative_"));
    }

    private static boolean isFactory(String path) {
        return isTiered(path, "_smelting_factory")
                || isTiered(path, "_enriching_factory")
                || isTiered(path, "_crushing_factory")
                || isTiered(path, "_compressing_factory")
                || isTiered(path, "_combining_factory")
                || isTiered(path, "_purifying_factory")
                || isTiered(path, "_injecting_factory")
                || isTiered(path, "_infusing_factory")
                || isTiered(path, "_sawing_factory");
    }
}
