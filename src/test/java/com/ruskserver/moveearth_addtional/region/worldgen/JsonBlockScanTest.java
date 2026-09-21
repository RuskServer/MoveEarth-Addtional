package com.ruskserver.moveearth_addtional.region.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scan is what stands between an exclusive resource and generating
 * everywhere, so it is tested against the document shapes it will actually be
 * handed rather than against a tidy one.
 */
class JsonBlockScanTest {

    /** A pack's block registry, as far as this test is concerned. */
    private static final Predicate<String> KNOWN = Set.of(
            "minecraft:stone", "minecraft:deepslate", "minecraft:tuff",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "mekanism:uranium_ore", "mekanism:deepslate_uranium_ore",
            "create:zinc_ore")::contains;

    private static List<String> scan(String json) {
        JsonElement root = JsonParser.parseString(json);
        return JsonBlockScan.blockIds(root, KNOWN);
    }

    @Test
    @DisplayName("finds the ore inside a vanilla ore config, hosts and all")
    void readsVanillaShape() {
        List<String> found = scan("""
                {
                  "type": "minecraft:ore",
                  "config": {
                    "size": 9,
                    "discard_chance_on_air_exposure": 0.0,
                    "targets": [
                      {
                        "target": {"predicate_type": "minecraft:tag_match",
                                   "tag": "minecraft:stone_ore_replaceables"},
                        "state": {"Name": "minecraft:iron_ore"}
                      },
                      {
                        "target": {"predicate_type": "minecraft:blocks",
                                   "blocks": ["minecraft:deepslate", "minecraft:tuff"]},
                        "state": {"Name": "minecraft:deepslate_iron_ore"}
                      }
                    ]
                  }
                }""");
        // The tag string names no block and drops out on its own; the hosts
        // listed as blocks do not, and are left for the caller to reject by
        // their tags. Both orderings matter, so the whole list is asserted.
        assertEquals(List.of("minecraft:iron_ore", "minecraft:deepslate",
                "minecraft:tuff", "minecraft:deepslate_iron_ore"), found);
    }

    @Test
    @DisplayName("reaches a block buried under an unfamiliar config shape")
    void readsUnknownShape() {
        // Modelled on what a mod's own feature config looks like once written
        // out: the same information, none of the vanilla key names. Nothing
        // here is an OreConfiguration, and the ore is three levels down.
        List<String> found = scan("""
                {
                  "type": "mekanism:resizable_ore",
                  "config": {
                    "vein_type": "uranium",
                    "radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4},
                    "targets": [
                      {"host": {"tag": "minecraft:stone_ore_replaceables"},
                       "placed": {"Name": "mekanism:uranium_ore"}},
                      {"host": {"tag": "minecraft:deepslate_ore_replaceables"},
                       "placed": {"Name": "mekanism:deepslate_uranium_ore"}}
                    ]
                  }
                }""");
        assertEquals(List.of("mekanism:uranium_ore", "mekanism:deepslate_uranium_ore"), found);
    }

    @Test
    @DisplayName("keeps document order and drops repeats")
    void orderedAndDeduplicated() {
        List<String> found = scan("""
                {"a": ["create:zinc_ore", "minecraft:stone"],
                 "b": {"c": ["minecraft:stone", "minecraft:iron_ore"]}}""");
        assertEquals(List.of("create:zinc_ore", "minecraft:stone", "minecraft:iron_ore"), found);
    }

    @Test
    @DisplayName("a feature that places no block yields nothing")
    void noBlocksAtAll() {
        // A lava flow or a disk of dirt: the caller must be able to tell this
        // apart from an ore it failed to recognise, and an empty list is how.
        assertTrue(scan("""
                {"type": "minecraft:lake", "config": {"fluid": {"Name": "minecraft:lava"},
                 "barrier": {"Name": "minecraft:obsidian"}}}""").isEmpty());
    }

    @Test
    @DisplayName("numbers, booleans and nulls are not block ids")
    void ignoresNonStrings() {
        assertTrue(scan("""
                {"size": 9, "rare": true, "unused": null, "chance": 0.25,
                 "nested": [[1, 2], [{"deeper": false}]]}""").isEmpty());
    }

    @Test
    @DisplayName("a block id sitting in a key rather than a value is not found")
    void keysAreNotValues() {
        // Recorded because it is a real limit, not an oversight: a config that
        // keys its map by block id reads as placing nothing, and the audit will
        // report it as "not an ore feature". If one ever turns up, this test is
        // where the decision to walk keys too gets made.
        assertTrue(scan("{\"minecraft:iron_ore\": {\"weight\": 3}}").isEmpty());
    }
}
