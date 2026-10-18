package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.DispatchContractActions.Action;
import com.ruskserver.moveearth_addtional.client.DispatchContractActions.Facts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchContractActionsTest {
    private static Facts facts(String state, boolean employerApproved, boolean providerApproved, int participants,
                               int consents, boolean viewerParticipant, boolean viewerConsented,
                               boolean viewerEmployer, boolean viewerProvider, boolean siege, boolean dispatch,
                               boolean treasury, boolean admin, long subsidy, boolean subsidyApproved,
                               boolean subsidyNeedsAdmin) {
        return new Facts(state, employerApproved, providerApproved, subsidyApproved, subsidy, participants, consents,
                viewerParticipant, viewerConsented, viewerEmployer, viewerProvider, siege, dispatch, treasury, admin,
                subsidyNeedsAdmin);
    }

    @Test
    void employerApprovesOnlyItsOwnSide() {
        Facts employer = facts("APPROVAL_PENDING", false, false, 1, 0, false, false, true, false,
                true, true, false, false, 0L, false, false);
        assertEquals(List.of(Action.APPROVE, Action.CANCEL), DispatchContractActions.available(employer));
        Facts approvedAlready = facts("APPROVAL_PENDING", true, false, 1, 0, false, false, true, false,
                true, true, false, false, 0L, false, false);
        assertEquals(List.of(Action.CANCEL), DispatchContractActions.available(approvedAlready));
    }

    @Test
    void outsiderWithPermissionsHasNoActions() {
        Facts outsider = facts("APPROVAL_PENDING", false, false, 1, 0, false, false, false, false,
                true, true, true, false, 0L, false, false);
        assertTrue(DispatchContractActions.available(outsider).isEmpty());
    }

    @Test
    void participantConsentsThenCanWithdraw() {
        Facts before = facts("CONSENT_PENDING", true, true, 2, 0, true, false, false, false,
                false, false, false, false, 0L, false, false);
        assertEquals(List.of(Action.CONSENT), DispatchContractActions.available(before));
        Facts after = facts("CONSENT_PENDING", true, true, 2, 1, true, true, false, false,
                false, false, false, false, 0L, false, false);
        assertEquals(List.of(Action.DECLINE), DispatchContractActions.available(after));
    }

    @Test
    void fundingWaitsForEveryConsentAndAnApprovedSubsidy() {
        Facts missingConsent = facts("CONSENT_PENDING", true, true, 2, 1, false, false, true, false,
                true, false, true, false, 0L, false, false);
        assertFalse(DispatchContractActions.available(missingConsent).contains(Action.FUND));
        Facts ready = facts("CONSENT_PENDING", true, true, 2, 2, false, false, true, false,
                true, false, true, false, 0L, false, false);
        assertEquals(List.of(Action.FUND, Action.CANCEL), DispatchContractActions.available(ready));
        Facts subsidyPending = facts("CONSENT_PENDING", true, true, 2, 2, false, false, true, false,
                true, false, true, false, 500L, false, true);
        assertFalse(DispatchContractActions.available(subsidyPending).contains(Action.FUND));
        Facts subsidyOptional = facts("CONSENT_PENDING", true, true, 2, 2, false, false, true, false,
                true, false, true, false, 500L, false, false);
        assertTrue(DispatchContractActions.available(subsidyOptional).contains(Action.FUND));
    }

    @Test
    void adminApprovesSubsidyAndMayEndAnything() {
        Facts admin = facts("APPROVAL_PENDING", false, false, 1, 0, false, false, false, false,
                true, true, true, true, 300L, false, true);
        assertEquals(List.of(Action.APPROVE_SUBSIDY, Action.CANCEL), DispatchContractActions.available(admin));
    }

    @Test
    void finishedAndTransitionalContractsCannotBeEnded() {
        for (String state : List.of("COMPLETED", "CANCELLED", "REVIEW_REQUIRED", "FUNDING", "SETTLING")) {
            Facts finished = facts(state, true, true, 1, 1, true, true, true, true,
                    true, true, true, true, 0L, true, false);
            assertTrue(DispatchContractActions.available(finished).isEmpty(), state);
        }
        Facts active = facts("ACTIVE", true, true, 1, 1, false, false, false, true,
                false, true, false, false, 0L, false, false);
        assertEquals(List.of(Action.CANCEL), DispatchContractActions.available(active));
    }
}
