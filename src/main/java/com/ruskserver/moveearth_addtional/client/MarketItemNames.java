package com.ruskserver.moveearth_addtional.client;

import com.tacz.guns.api.TimelessAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** TaCZ's variant names live in client gun packs, not in the server's item translation table. */
final class MarketItemNames {
    private MarketItemNames() { }

    static String display(Component name, ResourceLocation gunId) {
        if (gunId == null) return name.getString();
        try {
            var index = TimelessAPI.getClientGunIndex(gunId).orElse(null);
            if (index != null && index.getName() != null && !index.getName().isBlank()) {
                String key = index.getName();
                String translated = Component.translatable(key).getString();
                if (!translated.equals(key) || !key.contains(".")) return translated;
            }
        } catch (LinkageError | RuntimeException ignored) {
            // The gun pack may not be loaded yet. Keep the variant ID visible instead of a generic TaCZ item key.
        }
        return gunId.getPath().replace('_', ' ');
    }
}
