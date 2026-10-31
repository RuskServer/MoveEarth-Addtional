package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruskserver.moveearth_addtional.s2.territory.CapitalRelocationPolicy.Result.*;

class CapitalRelocationPolicyTest {
    @Test
    void permissionAndRevisionAreCheckedBeforeAnyStateChange() {
        assertEquals(OWNER_ONLY, CapitalRelocationPolicy.authority(false, true, false, false, 0));
        assertEquals(STALE, CapitalRelocationPolicy.authority(true, false, false, false, 0));
        assertEquals(READY, CapitalRelocationPolicy.authority(true, true, false, false, 0));
    }
    @Test
    void cannotEscapeSiegeOrRecoveryOrSpamCapitalChanges() {
        assertEquals(SIEGE_LOCKED, CapitalRelocationPolicy.authority(true, true, true, false, 0));
        assertEquals(RECOVERY_LOCKED, CapitalRelocationPolicy.authority(true, true, false, true, 0));
        assertEquals(COOLDOWN, CapitalRelocationPolicy.authority(true, true, false, false, 1));
    }
    @Test
    void onlyAFullyHealedSealedOwnOutpostCanReplaceTheCapital() {
        assertEquals(INVALID_TARGET, CapitalRelocationPolicy.candidate(false, true, true, false, true));
        assertEquals(NOT_SEALED, CapitalRelocationPolicy.candidate(true, false, true, false, true));
        assertEquals(DAMAGED, CapitalRelocationPolicy.candidate(true, true, false, false, true));
        assertEquals(READY, CapitalRelocationPolicy.candidate(true, true, true, false, true));
    }
    @Test
    void cannotPlaceTheCapitalOnTheVaultOrCommitAgainstAnUnloadedCore() {
        assertEquals(VAULT_CONFLICT, CapitalRelocationPolicy.candidate(true, true, true, true, true));
        assertEquals(NOT_LOADED, CapitalRelocationPolicy.candidate(true, true, true, false, false));
    }
    @Test
    void cooldownExpiresExactlyAtItsStoredOpenTimeDeadline() {
        assertEquals(1, CapitalRelocationPolicy.remaining(1199, 1200));
        assertEquals(0, CapitalRelocationPolicy.remaining(1200, 1200));
        assertEquals(0, CapitalRelocationPolicy.remaining(1201, 1200));
    }
}
