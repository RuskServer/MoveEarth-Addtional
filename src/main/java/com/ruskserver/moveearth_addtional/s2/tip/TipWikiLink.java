package com.ruskserver.moveearth_addtional.s2.tip;

/** Which configured wiki URLs the wiki tip may open. Kept free of NeoForge classes for tests. */
public final class TipWikiLink {
    private TipWikiLink() { }

    /** Empty (no wiki tip) or a plain web address; anything else is refused. */
    public static boolean isValid(String url) {
        String value = url == null ? "" : url.trim();
        return value.isEmpty() || value.startsWith("https://") || value.startsWith("http://");
    }
}
