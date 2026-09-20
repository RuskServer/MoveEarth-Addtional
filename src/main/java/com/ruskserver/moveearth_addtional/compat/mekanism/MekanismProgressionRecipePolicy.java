package com.ruskserver.moveearth_addtional.compat.mekanism;

import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;

/**
 * Maps Mekanism's inexpensive entry recipes to MoveEarth's Create/CEE-gated
 * progression recipes. Kept independent of Minecraft classes for unit tests.
 */
public final class MekanismProgressionRecipePolicy {
    public static final String MOVE_EARTH_NAMESPACE = "moveearth_addtional";

    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            entry("metallurgic_infuser", "mekanism/metallurgic_infuser"),
            entry("transmitter/universal_cable/basic", "mekanism/universal_cable/basic"),
            entry("transmitter/universal_cable/advanced", "mekanism/universal_cable/advanced"),
            entry("transmitter/universal_cable/elite", "mekanism/universal_cable/elite"),
            entry("transmitter/universal_cable/ultimate", "mekanism/universal_cable/ultimate"),
            entry("steel_casing", "mekanism/steel_casing"),
            entry("enrichment_chamber", "mekanism/machine/enrichment_chamber"),
            entry("osmium_compressor", "mekanism/machine/osmium_compressor"),
            entry("electrolytic_separator", "mekanism/machine/electrolytic_separator"),
            entry("chemical_oxidizer", "mekanism/machine/chemical_oxidizer"),
            entry("chemical_infuser", "mekanism/machine/chemical_infuser"),
            entry("rotary_condensentrator", "mekanism/machine/rotary_condensentrator"),
            entry("chemical_dissolution_chamber", "mekanism/machine/chemical_dissolution_chamber"),
            entry("isotopic_centrifuge", "mekanism/machine/isotopic_centrifuge"),
            entry("pressurized_reaction_chamber", "mekanism/machine/pressurized_reaction_chamber"),
            entry("boiler_casing", "mekanism/nuclear/boiler_casing"),
            entry("pressure_disperser", "mekanism/nuclear/pressure_disperser"),
            entry("superheating_element", "mekanism/nuclear/superheating_element"),
            entry("induction/casing", "mekanism/nuclear/induction/casing"),
            entry("induction/cell/basic", "mekanism/nuclear/induction/cell/basic"),
            entry("induction/cell/advanced", "mekanism/nuclear/induction/cell/advanced"),
            entry("induction/cell/elite", "mekanism/nuclear/induction/cell/elite"),
            entry("induction/cell/ultimate", "mekanism/nuclear/induction/cell/ultimate"),
            entry("induction/provider/basic", "mekanism/nuclear/induction/provider/basic"),
            entry("induction/provider/advanced", "mekanism/nuclear/induction/provider/advanced"),
            entry("induction/provider/elite", "mekanism/nuclear/induction/provider/elite"),
            entry("induction/provider/ultimate", "mekanism/nuclear/induction/provider/ultimate"),
            entry("fission_reactor/casing", "mekanism/nuclear/fission/casing"),
            entry("fission_reactor/control_rod_assembly", "mekanism/nuclear/fission/control_rod_assembly"),
            entry("fission_reactor/fuel_assembly", "mekanism/nuclear/fission/fuel_assembly"),
            entry("turbine/casing", "mekanism/nuclear/turbine/casing"),
            entry("turbine/blade", "mekanism/nuclear/turbine/blade"),
            entry("turbine/rotor", "mekanism/nuclear/turbine/rotor"),
            entry("electromagnetic_coil", "mekanism/nuclear/turbine/electromagnetic_coil"),
            entry("rotational_complex", "mekanism/nuclear/turbine/rotational_complex"),
            entry("saturating_condenser", "mekanism/nuclear/turbine/saturating_condenser")
    );
    private static final Set<String> GENERATOR_REPLACEMENTS = Set.of(
            "fission_reactor/casing",
            "fission_reactor/control_rod_assembly",
            "fission_reactor/fuel_assembly",
            "turbine/casing",
            "turbine/blade",
            "turbine/rotor",
            "electromagnetic_coil",
            "rotational_complex",
            "saturating_condenser"
    );

    private MekanismProgressionRecipePolicy() {
    }

    public static boolean isLegacyRecipe(String namespace, String path) {
        if (!REPLACEMENTS.containsKey(path)) {
            return false;
        }
        boolean generatorRecipe = GENERATOR_REPLACEMENTS.contains(path);
        return generatorRecipe
                ? MekanismRecipePolicy.GENERATORS_NAMESPACE.equals(namespace)
                : MekanismRecipePolicy.CORE_NAMESPACE.equals(namespace);
    }

    public static boolean isReplacementFor(String legacyPath, String namespace, String path) {
        String replacementPath = REPLACEMENTS.get(legacyPath);
        return replacementPath != null
                && MOVE_EARTH_NAMESPACE.equals(namespace)
                && replacementPath.equals(path);
    }

    public static boolean hasLoadedReplacement(String legacyPath, Set<String> loadedReplacementPaths) {
        String replacementPath = REPLACEMENTS.get(legacyPath);
        return replacementPath != null && loadedReplacementPaths.contains(replacementPath);
    }

    public static Set<String> replacementPaths() {
        return Set.copyOf(REPLACEMENTS.values());
    }
}
