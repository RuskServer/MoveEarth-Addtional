package com.ruskserver.moveearth_addtional.region;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Shows a material's name in the player's language.
 *
 * <p>Materials travel through this system as bare convention names -- "gold",
 * "crude_oil" -- because that is what the tags say and what an operator writes
 * in the config. None of that is a name to put in front of a player, and
 * guessing an item to borrow a name from would be wrong as often as not: a
 * material is a family of things, not one of them.
 *
 * <p>So each gets its own key, and one that is missing falls back to the bare
 * name rather than to an empty line. A new mod's resource then reads as
 * untranslated instead of disappearing, which is the difference between a
 * visible gap and a silent one.
 */
public final class RegionMaterialNames {

    private RegionMaterialNames() { }

    public static Component of(String material) {
        if (material == null || material.isBlank()) {
            return Component.empty();
        }
        String key = "material.moveearth_addtional." + material;
        Component translated = Component.translatable(key);
        return translated.getString().equals(key) ? Component.literal(material) : translated;
    }

    /** The materials as one comma-separated line. */
    public static Component list(List<String> materials) {
        if (materials == null || materials.isEmpty()) {
            return Component.empty();
        }
        MutableComponent line = Component.empty();
        for (int index = 0; index < materials.size(); index++) {
            if (index > 0) {
                line.append(Component.translatable("message.moveearth_addtional.region.separator"));
            }
            line.append(of(materials.get(index)));
        }
        return line;
    }
}
