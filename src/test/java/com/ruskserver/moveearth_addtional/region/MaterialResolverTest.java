package com.ruskserver.moveearth_addtional.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruskserver.moveearth_addtional.region.MaterialResolver.Resolution;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaterialResolverTest {

    @Test
    @DisplayName("an ore block is named by its c:ores/<material> tag")
    void resolvesOreFromConventionTag() {
        Resolution r = MaterialResolver.resolveOre(List.of("minecraft:iron_ores", "c:ores/iron"));
        assertEquals(Optional.of("iron"), r.material());
        assertFalse(r.ambiguous());
    }

    @Test
    @DisplayName("c:ores on its own names no material")
    void bareOresTagIsNotAMaterial() {
        assertEquals(Optional.empty(), MaterialResolver.resolveOre(List.of("c:ores")).material());
    }

    @Test
    @DisplayName("only the c: namespace is read")
    void ignoresOtherNamespaces() {
        Resolution r = MaterialResolver.resolveOre(List.of("forge:ores/iron", "create:ores/zinc"));
        assertEquals(Optional.empty(), r.material());
    }

    @Test
    @DisplayName("nothing to go on resolves to nothing, so the caller can allow it")
    void emptyInputIsUnresolved() {
        assertEquals(Optional.empty(), MaterialResolver.resolveOre(List.of()).material());
        assertEquals(Optional.empty(), MaterialResolver.resolveOre(null).material());
    }

    @Test
    @DisplayName("a raw drop outranks an ingot tag on the same item")
    void prefixOrderDecidesBetweenReadings() {
        Resolution r = MaterialResolver.resolveVeinOutput(
                List.of("c:ingots/iron", "c:raw_materials/iron", "c:dusts/iron"));
        assertEquals(Optional.of("iron"), r.material());
        assertEquals(List.of("iron"), r.candidates());
    }

    @Test
    @DisplayName("a later prefix is used when no earlier one matches")
    void fallsThroughToLaterPrefixes() {
        assertEquals(Optional.of("zinc"),
                MaterialResolver.resolveVeinOutput(List.of("c:ingots/zinc")).material());
        assertEquals(Optional.of("quartz"),
                MaterialResolver.resolveVeinOutput(List.of("c:gems/quartz")).material());
    }

    @Test
    @DisplayName("tags that disagree still pick one material, and say they disagreed")
    void conflictingTagsAreReportedNotHidden() {
        Resolution r = MaterialResolver.resolveOre(List.of("c:ores/iron", "c:ores/copper"));
        assertTrue(r.ambiguous());
        assertEquals(List.of("copper", "iron"), r.candidates());
        assertEquals(Optional.of("copper"), r.material());
    }

    @Test
    @DisplayName("the choice does not depend on the order the tags arrive in")
    void choiceIsStableAcrossInputOrder() {
        // A HashSet of tags iterates in an arbitrary order, and an ore that
        // changed region on restart would be near impossible to diagnose.
        Resolution a = MaterialResolver.resolveOre(List.of("c:ores/iron", "c:ores/copper"));
        Resolution b = MaterialResolver.resolveOre(List.of("c:ores/copper", "c:ores/iron"));
        Resolution c = MaterialResolver.resolveOre(Set.of("c:ores/copper", "c:ores/iron"));
        assertEquals(a.material(), b.material());
        assertEquals(a.material(), c.material());
    }

    @Test
    @DisplayName("a vein is named by its first output, not by its byproducts")
    void byproductsDoNotNameTheVein() {
        // Create Ore Excavation's netherite vein, exactly as the server reports it.
        // Collecting every material would make this vein need netherite_scrap and
        // gold permitted together; reading only the nuggets would call it gold.
        Optional<String> material = MaterialResolver.veinMaterial(List.of(
                List.of("c:ores/netherite_scrap", "c:ore_rates/singular", "c:ores"),
                List.of("c:nuggets/gold", "c:nuggets"),
                List.of("c:ore_bearing_ground/netherrack", "c:netherracks"),
                List.of()));
        assertEquals(Optional.of("netherite_scrap"), material);
    }

    @Test
    @DisplayName("an untagged first output falls through to the next one")
    void untaggedLeadOutputDoesNotLoseTheVein() {
        // The hardened diamond vein leads with an item its own mod never tagged,
        // then drops plain diamonds. Taking strictly the first output loses it.
        Optional<String> material = MaterialResolver.veinMaterial(List.of(
                List.of(),
                List.of("c:gems/diamond")));
        assertEquals(Optional.of("diamond"), material);
    }

    @Test
    @DisplayName("a vein that only drops nuggets is still named")
    void nuggetsNameAVein() {
        assertEquals(Optional.of("gold"), MaterialResolver.veinMaterial(List.of(
                List.of("c:nuggets/gold", "c:nuggets"))));
    }

    @Test
    @DisplayName("a vein that drops the ore block itself is named by it")
    void oreBlockOutputNamesAVein() {
        assertEquals(Optional.of("netherite_scrap"), MaterialResolver.veinMaterial(List.of(
                List.of("c:ores/netherite_scrap", "c:ores"))));
    }

    @Test
    @DisplayName("a flat tag with no prefix names nothing")
    void flatTagsAreNotRead() {
        // minecraft:coal carries c:coal and nothing else. There is no prefix to
        // key on, and reading bare c: tags as materials would turn c:water into
        // a material and make the water vein a regional resource.
        assertEquals(Optional.empty(), MaterialResolver.veinMaterial(List.of(List.of("c:coal"))));
    }

    @Test
    @DisplayName("a vein with nothing resolvable is left unnamed, and so allowed")
    void veinWithNoConventionTagsYieldsNothing() {
        assertEquals(Optional.empty(),
                MaterialResolver.veinMaterial(List.of(List.of("somemod:untagged_lump"))));
        assertEquals(Optional.empty(), MaterialResolver.veinMaterial(List.of()));
    }

    @Test
    @DisplayName("an operator override names what the tags cannot")
    void overridesWinOverTags() {
        // Coal is the one deposit whose icon is not a c: tag at all: it is
        // declared as #minecraft:coals, which has no prefix to key on. Every
        // other deposit in the pack resolves without help.
        var overrides = MaterialResolver.parseOverrides(List.of("minecraft:coal=coal"));
        assertEquals(Optional.of("coal"), MaterialResolver.veinMaterial(
                List.of("minecraft:coal"), List.of(List.of("minecraft:coals")), overrides));
        assertEquals(Optional.empty(), MaterialResolver.veinMaterial(
                List.of("minecraft:coal"), List.of(List.of("minecraft:coals")), Map.of()));
    }

    @Test
    @DisplayName("an override beats a tag that disagrees with it")
    void overrideBeatsTheTags() {
        var overrides = MaterialResolver.parseOverrides(List.of("somemod:ore=thorium"));
        assertEquals(Optional.of("thorium"),
                MaterialResolver.resolve("somemod:ore", List.of("c:ores/iron"),
                        MaterialResolver.ORE_PREFIXES, overrides).material());
    }

    @Test
    @DisplayName("a malformed override line costs one override, not the server")
    void malformedOverridesAreDropped() {
        var overrides = MaterialResolver.parseOverrides(java.util.Arrays.asList(
                "no_equals_sign", "=leading", "trailing=", null, " a = b "));
        assertEquals(1, overrides.size());
        assertEquals("b", overrides.get("a"));
    }

    @Test
    @DisplayName("no overrides behaves exactly as before")
    void emptyOverridesChangeNothing() {
        assertEquals(Optional.of("iron"),
                MaterialResolver.resolve("minecraft:iron_ore", List.of("c:ores/iron"),
                        MaterialResolver.ORE_PREFIXES, Map.of()).material());
        assertEquals(Optional.empty(),
                MaterialResolver.resolve(null, List.of("c:coal"),
                        MaterialResolver.VEIN_OUTPUT_PREFIXES, Map.of()).material());
    }

    @Test
    @DisplayName("a material name may itself contain a slash")
    void keepsTheWholeRemainderAsTheName() {
        assertEquals(Optional.of("rare/thorium"),
                MaterialResolver.resolveOre(List.of("c:ores/rare/thorium")).material());
    }

    @Nested
    @DisplayName("namedLikeOre")
    class NamedLikeOre {

        @Test
        @DisplayName("recognises the shapes mods actually use")
        void recognisesOreNames() {
            assertTrue(MaterialResolver.namedLikeOre("mekanism:uranium_ore"));
            assertTrue(MaterialResolver.namedLikeOre("expandeddelight:deepslate_salt_ore"));
            assertTrue(MaterialResolver.namedLikeOre("minecraft:ore_coal"));
            assertTrue(MaterialResolver.namedLikeOre("somemod:ore"));
            assertTrue(MaterialResolver.namedLikeOre("somemod:rich_ore_vein"));
        }

        @Test
        @DisplayName("vanilla terrain is not ore, which is the whole point")
        void terrainIsNotOre() {
            // These are what vanilla builds out of the same config as its ores.
            // Before this rule they raised the audit's warning on every world,
            // eleven at a time, and the warning stopped meaning anything.
            for (String terrain : List.of("minecraft:andesite", "minecraft:diorite",
                    "minecraft:granite", "minecraft:tuff", "minecraft:gravel",
                    "minecraft:clay", "minecraft:dirt", "minecraft:stone",
                    "minecraft:deepslate")) {
                assertFalse(MaterialResolver.namedLikeOre(terrain), terrain);
            }
        }

        @Test
        @DisplayName("a word merely containing the letters is not ore")
        void substringIsNotEnough() {
            // "forest" and "shore" carry the three letters and nothing else.
            assertFalse(MaterialResolver.namedLikeOre("biomesoplenty:forest_grass"));
            assertFalse(MaterialResolver.namedLikeOre("somemod:shoreline_sand"));
            assertFalse(MaterialResolver.namedLikeOre("somemod:store_block"));
        }

        @Test
        @DisplayName("an id with no namespace is read the same way")
        void namespaceIsOptional() {
            assertTrue(MaterialResolver.namedLikeOre("iron_ore"));
            assertFalse(MaterialResolver.namedLikeOre("dirt"));
        }
    }
}
