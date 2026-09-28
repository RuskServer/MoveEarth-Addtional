package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientSoundChannelTest {
    @Test
    void announcerSoundsFollowTheAnnouncerVolume() {
        assertEquals(ClientSoundChannel.ANNOUNCER, ClientSoundChannel.of("moveearth_addtional", "warlord_start"));
        assertEquals(ClientSoundChannel.ANNOUNCER, ClientSoundChannel.of("moveearth_addtional", "warlord_first_blood"));
    }

    @Test
    void serverNoticeFollowsTheNoticeVolume() {
        assertEquals(ClientSoundChannel.NOTICE, ClientSoundChannel.of("moveearth_addtional", "server_notice"));
    }

    @Test
    void otherSoundsAreLeftAlone() {
        assertEquals(ClientSoundChannel.NONE, ClientSoundChannel.of("moveearth_addtional", "track_running"));
        assertEquals(ClientSoundChannel.NONE, ClientSoundChannel.of("minecraft", "warlord_start"));
        assertEquals(ClientSoundChannel.NONE, ClientSoundChannel.of("tacz", "server_notice"));
    }
}
