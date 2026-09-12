package com.ruskserver.moveearth_addtional.s2.notification;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Memory-only, one-use codes that prove a Discord guild/channel request was confirmed in game. */
public final class DiscordLinkCodeRegistry {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_PENDING = 1_024;
    private final Map<String, PendingLink> pending = new LinkedHashMap<>();
    private final RandomGenerator random;

    public DiscordLinkCodeRegistry() {
        this(new SecureRandom());
    }

    DiscordLinkCodeRegistry(RandomGenerator random) {
        this.random = random;
    }

    public synchronized String create(long guildId, long channelId, long discordUserId,
                                      long nowMillis, long lifetimeMillis) {
        prune(nowMillis);
        pending.entrySet().removeIf(entry -> entry.getValue().guildId == guildId);
        while (pending.size() >= MAX_PENDING) pending.remove(pending.keySet().iterator().next());
        String code;
        do code = newCode(); while (pending.containsKey(code));
        pending.put(code, new PendingLink(guildId, channelId, discordUserId,
                Math.max(nowMillis + 1L, nowMillis + Math.max(1L, lifetimeMillis))));
        return code;
    }

    public synchronized Optional<PendingLink> consume(String rawCode, long nowMillis) {
        prune(nowMillis);
        String code = normalize(rawCode);
        if (code.length() != CODE_LENGTH) return Optional.empty();
        return Optional.ofNullable(pending.remove(code));
    }

    public synchronized void clear() {
        pending.clear();
    }

    private void prune(long nowMillis) {
        pending.values().removeIf(value -> value.expiresAtMillis <= nowMillis);
    }

    private String newCode() {
        StringBuilder result = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            result.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return result.toString();
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().replace("-", "").toUpperCase(Locale.ROOT);
    }

    public record PendingLink(long guildId, long channelId, long discordUserId,
                              long expiresAtMillis) { }
}
