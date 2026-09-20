package com.ruskserver.moveearth_addtional.client.loading;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingScreenLifecyclePolicyTest {
    @Test
    void recognizesVanillaLoadAndSaveMessages() {
        assertTrue(LoadingScreenLifecyclePolicy.isGenericLoadingKey("gui.loadingMinecraft"));
        assertTrue(LoadingScreenLifecyclePolicy.isGenericLoadingKey("menu.savingLevel"));
        assertTrue(LoadingScreenLifecyclePolicy.isGenericLoadingKey("selectWorld.data_read"));
        assertTrue(LoadingScreenLifecyclePolicy.isGenericLoadingKey("createWorld.preparing"));
    }

    @Test
    void leavesDisconnectAndModMessagesUntouched() {
        assertFalse(LoadingScreenLifecyclePolicy.isGenericLoadingKey("disconnect.lost"));
        assertFalse(LoadingScreenLifecyclePolicy.isGenericLoadingKey("example.mod_message"));
        assertFalse(LoadingScreenLifecyclePolicy.isGenericLoadingKey(null));
    }

    @Test
    void onlyRecoversAReadyReceivingScreenWithAClosedConnection() {
        assertTrue(LoadingScreenLifecyclePolicy.shouldRecoverReceiving(true, true, true, false));
        assertFalse(LoadingScreenLifecyclePolicy.shouldRecoverReceiving(false, true, true, false));
        assertFalse(LoadingScreenLifecyclePolicy.shouldRecoverReceiving(true, false, true, false));
        assertFalse(LoadingScreenLifecyclePolicy.shouldRecoverReceiving(true, true, false, false));
        assertFalse(LoadingScreenLifecyclePolicy.shouldRecoverReceiving(true, true, true, true));
    }
}
