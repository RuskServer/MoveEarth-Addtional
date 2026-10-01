package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.pvp.PvpEntryStateSync.Decision;
import com.ruskserver.moveearth_addtional.pvp.PvpEntryStateSync.State;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PvpEntryStateSyncTest {
    private final UUID player = UUID.randomUUID();

    private static State outsider(int entries) {
        return new State(false, false, true, false, entries);
    }

    @Test void firstStateIsSentThenIdenticalStateSkipped() {
        PvpEntryStateSync sync = new PvpEntryStateSync(40);
        assertEquals(Decision.SEND, sync.decide(player, outsider(1), 0));
        assertEquals(Decision.SKIP, sync.decide(player, outsider(1), 1));
        assertEquals(Decision.SKIP, sync.decide(player, outsider(1), 500));
    }

    @Test void countOnlyChangeForOutsiderIsSpaced() {
        PvpEntryStateSync sync = new PvpEntryStateSync(40);
        sync.decide(player, outsider(1), 0);
        assertEquals(Decision.DEFER, sync.decide(player, outsider(2), 1));
        assertEquals(Decision.DEFER, sync.decide(player, outsider(5), 39));
        assertEquals(Decision.SEND, sync.decide(player, outsider(5), 40));
        assertEquals(Decision.SKIP, sync.decide(player, outsider(5), 41));
    }

    @Test void deferredCountThatReturnsToSentValueNeedsNothing() {
        PvpEntryStateSync sync = new PvpEntryStateSync(40);
        sync.decide(player, outsider(1), 0);
        assertEquals(Decision.DEFER, sync.decide(player, outsider(2), 1));
        assertEquals(Decision.SKIP, sync.decide(player, outsider(1), 2));
    }

    @Test void ownFlagsAndMatchStateAreSentImmediately() {
        PvpEntryStateSync sync = new PvpEntryStateSync(40);
        sync.decide(player, outsider(1), 0);
        assertEquals(Decision.SEND, sync.decide(player, new State(true, false, true, false, 2), 1));
        assertEquals(Decision.SEND, sync.decide(player, new State(true, true, true, true, 2), 2));
        assertEquals(Decision.SEND, sync.decide(player, new State(true, true, true, true, 3), 3),
                "participants get count changes immediately");
        assertEquals(Decision.SEND, sync.decide(player, new State(false, false, true, true, 2), 4),
                "leaving is a flag change");
        assertEquals(Decision.SEND, sync.decide(player, new State(false, false, false, false, 0), 5),
                "hosting closed");
    }

    @Test void forgottenAndOfflinePlayersStartFresh() {
        PvpEntryStateSync sync = new PvpEntryStateSync(40);
        UUID other = UUID.randomUUID();
        sync.decide(player, outsider(1), 0);
        sync.decide(other, outsider(1), 0);
        sync.forget(player);
        assertEquals(Decision.SEND, sync.decide(player, outsider(1), 1));
        sync.retainOnly(List.of(player));
        assertEquals(Decision.SEND, sync.decide(other, outsider(1), 2));
    }

    @Test void joinBurstBroadcastsToOutsidersAtMostOncePerInterval() {
        PvpEntryStateSync sync = new PvpEntryStateSync(40);
        sync.decide(player, outsider(0), 0);
        int sends = 0;
        // one join every tick for 4 seconds
        for (int tick = 1; tick <= 80; tick++) {
            if (sync.decide(player, outsider(tick), tick) == Decision.SEND) sends++;
        }
        assertEquals(2, sends);
    }
}
