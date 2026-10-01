package com.ruskserver.moveearth_addtional.economy;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A market search run on the server. {@code text} is what the player typed, matched against what the
 * server knows of an item (registry id, gun id, its name in the server's language, custom names).
 * {@code itemKeys} are the {@link #itemKey item keys} whose names the client matched in the player's
 * own language, which the server cannot translate; an order matching either is a hit.
 */
public record MarketSearch(String text, Set<String> itemKeys) {
    public static final int MAX_TEXT_LENGTH = 64;
    public static final int MAX_ITEM_KEYS = 64;
    public static final int MAX_ITEM_KEY_LENGTH = 128;
    public static final MarketSearch NONE = new MarketSearch("", Set.of());

    public MarketSearch {
        text = normalize(text);
        Set<String> keys = new LinkedHashSet<>();
        if (itemKeys != null) {
            for (String key : itemKeys) {
                if (keys.size() >= MAX_ITEM_KEYS) break;
                if (key != null && !key.isBlank() && key.length() <= MAX_ITEM_KEY_LENGTH) keys.add(key);
            }
        }
        // Keys only narrow a typed search; without text everything matches.
        itemKeys = text.isEmpty() ? Set.of() : Set.copyOf(keys);
    }

    public static MarketSearch of(String text, java.util.Collection<String> itemKeys) {
        return new MarketSearch(text, itemKeys == null ? Set.of() : new LinkedHashSet<>(itemKeys));
    }

    public boolean blank() {
        return text.isEmpty();
    }

    /** Whether an order for {@code itemKey}, described by {@code texts}, is a hit. */
    public boolean matches(String itemKey, String... texts) {
        if (text.isEmpty()) return true;
        if (itemKey != null && itemKeys.contains(itemKey)) return true;
        if (texts == null) return false;
        for (String candidate : texts) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).contains(text)) return true;
        }
        return false;
    }

    /**
     * Groups orders by what is sold: the item's registry id, or for a TaCZ gun (all guns share one
     * item) {@code gun:} and the gun id. The client builds the same keys for its local name matches.
     */
    public static String itemKey(String itemId, String gunId) {
        return gunId != null ? "gun:" + gunId : itemId == null ? "" : itemId;
    }

    /** Trimmed, lower-cased and cut to {@link #MAX_TEXT_LENGTH}; the client sends the same form. */
    public static String normalize(String text) {
        if (text == null) return "";
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_TEXT_LENGTH ? normalized.substring(0, MAX_TEXT_LENGTH) : normalized;
    }
}
