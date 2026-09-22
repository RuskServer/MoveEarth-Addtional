package com.ruskserver.moveearth_addtional.compat.tacz;

import java.util.Locale;

/** Gun-pack category, never a display name or hand-maintained gun-ID list. */
public final class WarehouseSniperRecipePolicy {
    private WarehouseSniperRecipePolicy() { }

    public static boolean requiresAssembly(String category) {
        if (category == null) return false;
        String normalized = category.strip().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.contains("marksman") || normalized.equals("dmr")
                || normalized.contains("designated_marksman")) return false;
        return normalized.equals("sniper") || normalized.equals("sniper_rifle")
                || normalized.equals("sniperrifle");
    }
}
