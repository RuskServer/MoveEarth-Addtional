package com.ruskserver.moveearth_addtional.client.loading;

import java.util.Set;

/** Pure classification and recovery rules for custom loading-screen lifecycle handling. */
final class LoadingScreenLifecyclePolicy {
    private static final Set<String> GENERIC_LOADING_KEYS = Set.of(
            "gui.loadingMinecraft",
            "menu.savingLevel",
            "selectWorld.data_read",
            "selectWorld.resource_load",
            "dataPack.validation.working",
            "recover_world.restoring",
            "createWorld.preparing"
    );

    private LoadingScreenLifecyclePolicy() {
    }

    static boolean isGenericLoadingKey(String translationKey) {
        return translationKey != null && GENERIC_LOADING_KEYS.contains(translationKey);
    }

    static boolean shouldRecoverReceiving(boolean receivingLevel, boolean playerReady,
                                          boolean connectionPresent, boolean connectionOpen) {
        return receivingLevel && playerReady && connectionPresent && !connectionOpen;
    }
}
