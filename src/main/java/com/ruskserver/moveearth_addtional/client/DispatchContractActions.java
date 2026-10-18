package com.ruskserver.moveearth_addtional.client;

import java.util.ArrayList;
import java.util.List;

/**
 * What the viewer can do to a dispatch contract, decided once for both the buttons drawn and the click
 * sent. The screen used to work these out twice, and the two disagreed: a button labelled to end the
 * contract could send a funding or subsidy request. Pure, so it is unit tested; it mirrors what
 * {@code DispatchContractService} accepts.
 */
final class DispatchContractActions {
    enum Action { CONSENT, DECLINE, APPROVE, APPROVE_SUBSIDY, FUND, CANCEL }

    /** The contract and viewer facts the server checks. */
    record Facts(String state, boolean employerApproved, boolean providerApproved, boolean subsidyApproved,
                 long requestedSubsidy, int participants, int consents, boolean viewerParticipant,
                 boolean viewerConsented, boolean viewerEmployer, boolean viewerProvider,
                 boolean canManageSiege, boolean canManageDispatch, boolean canManageTreasury,
                 boolean admin, boolean subsidyNeedsAdmin) { }

    private DispatchContractActions() { }

    /** Every action open to the viewer, most useful first; {@link Action#CANCEL} is always last. */
    static List<Action> available(Facts facts) {
        List<Action> actions = new ArrayList<>();
        boolean pending = pending(facts.state());
        if (pending && facts.viewerParticipant()) actions.add(facts.viewerConsented() ? Action.DECLINE : Action.CONSENT);
        if (pending && (facts.viewerEmployer() && facts.canManageSiege() && !facts.employerApproved()
                || facts.viewerProvider() && facts.canManageDispatch() && !facts.providerApproved())) {
            actions.add(Action.APPROVE);
        }
        if (pending && facts.admin() && facts.requestedSubsidy() > 0L && !facts.subsidyApproved()) {
            actions.add(Action.APPROVE_SUBSIDY);
        }
        if (mayFund(facts)) actions.add(Action.FUND);
        if (!terminal(facts.state()) && !"FUNDING".equals(facts.state()) && !"SETTLING".equals(facts.state())
                && (facts.admin() || facts.viewerEmployer() && facts.canManageSiege()
                || facts.viewerProvider() && facts.canManageDispatch())) {
            actions.add(Action.CANCEL);
        }
        return List.copyOf(actions);
    }

    static boolean mayFund(Facts facts) {
        return "CONSENT_PENDING".equals(facts.state()) && facts.viewerEmployer() && facts.canManageTreasury()
                && facts.employerApproved() && facts.providerApproved()
                && facts.participants() > 0 && facts.consents() >= facts.participants()
                && (!facts.subsidyNeedsAdmin() || facts.requestedSubsidy() <= 0L || facts.subsidyApproved());
    }

    static boolean pending(String state) {
        return "APPROVAL_PENDING".equals(state) || "CONSENT_PENDING".equals(state);
    }

    static boolean terminal(String state) {
        return "COMPLETED".equals(state) || "CANCELLED".equals(state) || "REVIEW_REQUIRED".equals(state);
    }
}
