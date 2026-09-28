package com.ruskserver.moveearth_addtional.analytics.state;

/**
 * The latest {@link S2StateSnapshot}, written on the server thread and read by
 * the web dashboard's threads. It holds no game classes so the web server can
 * load it anywhere.
 */
public final class S2StateSnapshotStore {
    private static volatile S2StateSnapshot latest = S2StateSnapshot.EMPTY;

    private S2StateSnapshotStore() { }

    public static S2StateSnapshot latest() { return latest; }

    public static void publish(S2StateSnapshot snapshot) {
        latest = snapshot == null ? S2StateSnapshot.EMPTY : snapshot;
    }
}
