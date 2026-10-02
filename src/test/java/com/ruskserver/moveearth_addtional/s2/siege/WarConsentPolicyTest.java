package com.ruskserver.moveearth_addtional.s2.siege;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.ruskserver.moveearth_addtional.s2.siege.WarConsentPolicy.Decision.CONSENTED;
import static com.ruskserver.moveearth_addtional.s2.siege.WarConsentPolicy.Decision.NOT_REQUIRED;
import static com.ruskserver.moveearth_addtional.s2.siege.WarConsentPolicy.Decision.REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarConsentPolicyTest {
    private static final UUID ATTACKER = UUID.randomUUID();
    private static final UUID DEFENDER = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private static WarConsentPolicy.Decision decide(boolean fromAttacker, boolean siegeExists, boolean consented) {
        return WarConsentPolicy.decide(ATTACKER, false, DEFENDER, ACTOR, fromAttacker, false, siegeExists, consented);
    }

    @Test void firstAttackOnANeutralNationNeedsConsent() {
        assertEquals(REQUIRED, decide(false, false, false));
    }

    @Test void onlyTheAttackersOwnHostilityWaivesConsent() {
        assertEquals(NOT_REQUIRED, decide(true, false, false));
        // The defender declaring hostility on its own must not drag the attacker's nation into a Siege:
        // that is now simply not an input to the decision.
        assertEquals(REQUIRED, decide(false, false, false));
    }

    @Test void anExistingSiegeBetweenTheNationsNeedsNoConsent() {
        assertEquals(NOT_REQUIRED, decide(false, true, false));
    }

    @Test void aLiveConfirmationLetsTheHitThrough() {
        assertEquals(CONSENTED, decide(false, false, true));
    }

    @Test void nationlessAndIndividualAttackersAreNeverAsked() {
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(null, false, DEFENDER, ACTOR,
                false, false, false, false));
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(ATTACKER, true, DEFENDER, ACTOR,
                false, false, false, false));
    }

    @Test void nationOnlyAttributionNeedsDeclaredHostility() {
        assertEquals(REQUIRED, WarConsentPolicy.decide(ATTACKER, false, DEFENDER, null,
                false, false, false, true));
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(ATTACKER, false, DEFENDER, null,
                true, false, false, false));
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(ATTACKER, false, DEFENDER, null,
                false, false, true, false));
    }

    @Test void ownAlliedOrMissingDefenderIsLeftToTheOtherRules() {
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(ATTACKER, false, ATTACKER, ACTOR,
                false, false, false, false));
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(ATTACKER, false, DEFENDER, ACTOR,
                false, true, false, false));
        assertEquals(NOT_REQUIRED, WarConsentPolicy.decide(ATTACKER, false, null, ACTOR,
                false, false, false, false));
    }

    @Test void consentLastsTenMinutes() {
        long now = 1_000_000L;
        assertTrue(WarConsentPolicy.consentLive(now + WarConsentPolicy.CONSENT_MILLIS, now));
        assertFalse(WarConsentPolicy.consentLive(now, now));
        assertEquals(600_000L, WarConsentPolicy.CONSENT_MILLIS);
    }

    @Test void promptsAreRateLimitedPerPlayer() {
        assertTrue(WarConsentPolicy.promptDue(0L, 5_000L));
        assertFalse(WarConsentPolicy.promptDue(5_000L, 5_000L + WarConsentPolicy.PROMPT_INTERVAL_MILLIS - 1L));
        assertTrue(WarConsentPolicy.promptDue(5_000L, 5_000L + WarConsentPolicy.PROMPT_INTERVAL_MILLIS));
    }

    @Test void onlyARecentPromptCanBeConfirmed() {
        assertFalse(WarConsentPolicy.confirmable(0L, 10_000L));
        assertTrue(WarConsentPolicy.confirmable(10_000L, 10_000L + WarConsentPolicy.PROMPT_VALID_MILLIS));
        assertFalse(WarConsentPolicy.confirmable(10_000L, 10_001L + WarConsentPolicy.PROMPT_VALID_MILLIS));
        assertFalse(WarConsentPolicy.confirmable(10_000L, 9_999L));
    }
}
