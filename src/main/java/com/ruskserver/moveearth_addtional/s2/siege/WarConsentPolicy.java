package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.UUID;

/**
 * Whether a nation attack on a protected target needs the attacking player's explicit consent first.
 *
 * <p>One damaging hit on another nation's reinforcement or core turns into a nation-vs-nation Siege that
 * makes every member of both nations capturable and, if it fails, locks the attacking nation out of that
 * core. That must never happen by accident (a newcomer mining one reinforced block), so a first attack on a
 * nation with which nothing is going on yet asks the player first. A nation that itself declared hostility
 * on the defender, or already fights a Siege with it, is past that point and is never asked.
 *
 * <p>Only the attacker's own declaration counts. A defender that declared hostility on its own could
 * otherwise waive the protection for its target: bait one of the target's newcomers into mining its wall,
 * or splash its border with the target's own redstone cannon, and a Siege opens with the target as the
 * attacker, though that nation never chose it.
 */
public final class WarConsentPolicy {
    /** Real time one confirmation stays valid; it also ends when the player logs out. */
    public static final long CONSENT_MILLIS = 10L * 60_000L;
    /** At most one prompt per player this often, whatever triggered it. */
    public static final long PROMPT_INTERVAL_MILLIS = 15_000L;
    /** A confirmation is accepted only for a nation the player was actually prompted about this recently. */
    public static final long PROMPT_VALID_MILLIS = 120_000L;

    public enum Decision {
        /** Nothing to ask: no nation attack, the attacker declared hostility, or a Siege is already on. */
        NOT_REQUIRED,
        /** The attacking player confirmed this target nation and the confirmation is still valid. */
        CONSENTED,
        /** Refuse the hit; prompt the attacking player when there is one. */
        REQUIRED
    }

    private WarConsentPolicy() { }

    /**
     * @param attackerNation      nation the attack would be recorded for; null for a nationless attacker
     * @param individualAttacker  the attack is recorded for the player alone (no nation is dragged in)
     * @param defenderNation      nation owning the target
     * @param actorId             player behind the attack; null for nation-only attribution (nobody to ask)
     * @param hostileFromAttacker the attacker's nation declared hostility towards the defender
     * @param allied              the nations are allied (other rules refuse the attack anyway)
     * @param siegeExists         a nation Siege between the two nations is already open or fallen
     * @param consented           the actor holds a live confirmation for the defender nation
     */
    public static Decision decide(UUID attackerNation, boolean individualAttacker, UUID defenderNation,
                                  UUID actorId, boolean hostileFromAttacker,
                                  boolean allied, boolean siegeExists, boolean consented) {
        if (individualAttacker || attackerNation == null || defenderNation == null
                || attackerNation.equals(defenderNation)) return Decision.NOT_REQUIRED;
        if (allied || hostileFromAttacker || siegeExists) return Decision.NOT_REQUIRED;
        // An unmanned weapon attributed only to a nation has nobody who could confirm: it needs declared hostility.
        if (actorId == null) return Decision.REQUIRED;
        return consented ? Decision.CONSENTED : Decision.REQUIRED;
    }

    public static boolean consentLive(long expiresAtMillis, long nowMillis) {
        return expiresAtMillis > nowMillis;
    }

    public static boolean promptDue(long lastPromptMillis, long nowMillis) {
        return lastPromptMillis <= 0L || nowMillis - lastPromptMillis >= PROMPT_INTERVAL_MILLIS
                || nowMillis < lastPromptMillis;
    }

    /** A confirm packet counts only for a prompt this player was really shown, and not long ago. */
    public static boolean confirmable(long promptedAtMillis, long nowMillis) {
        return promptedAtMillis > 0L && nowMillis >= promptedAtMillis
                && nowMillis - promptedAtMillis <= PROMPT_VALID_MILLIS;
    }
}
