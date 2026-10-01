package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.economy.MarketSearch;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Item keys whose names, in the player's language, contain the search text. The server matches
 * names only in its own language (and registry ids), so these keys let a search typed in Japanese
 * find items on the server. Only the last result is cached: it is recomputed when the text changes.
 */
final class MarketSearchHints {
    private static String lastText = "";
    private static List<String> lastKeys = List.of();

    private MarketSearchHints() { }

    static List<String> resolve(String rawText) {
        String text = MarketSearch.normalize(rawText);
        if (text.isEmpty()) return List.of();
        if (text.equals(lastText)) return lastKeys;
        List<String> keys = new ArrayList<>();
        // Guns first: they all share one item, so only their own names find them.
        try {
            for (var entry : TimelessAPI.getAllClientGunIndex()) {
                if (keys.size() >= MarketSearch.MAX_ITEM_KEYS) break;
                if (contains(MarketItemNames.display(Component.empty(), entry.getKey()), text))
                    keys.add(MarketSearch.itemKey(null, entry.getKey().toString()));
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Gun packs not loaded: guns are still found by their id on the server.
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (keys.size() >= MarketSearch.MAX_ITEM_KEYS) break;
            if (contains(item.getDescription().getString(), text))
                keys.add(MarketSearch.itemKey(BuiltInRegistries.ITEM.getKey(item).toString(), null));
        }
        lastText = text;
        lastKeys = List.copyOf(keys);
        return lastKeys;
    }

    private static boolean contains(String name, String text) {
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains(text);
    }
}
