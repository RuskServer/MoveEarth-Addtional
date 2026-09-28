package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;

/** Which client volume setting a MoveEarth sound follows; see {@link ClientSoundVolume}. */
enum ClientSoundChannel {
    ANNOUNCER, NOTICE, NONE;

    static ClientSoundChannel of(String namespace, String path) {
        if (!Moveearth_addtional.MODID.equals(namespace)) return NONE;
        if (path.startsWith("warlord_")) return ANNOUNCER;
        if (path.equals("server_notice")) return NOTICE;
        return NONE;
    }
}
