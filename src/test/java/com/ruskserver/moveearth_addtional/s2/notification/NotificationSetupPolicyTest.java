package com.ruskserver.moveearth_addtional.s2.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationSetupPolicyTest {
    @Test
    void inviteLinkUsesApplicationIdAndRequiredPermissions() {
        assertEquals("https://discord.com/oauth2/authorize?client_id=1548331274198982807"
                        + "&permissions=552440170496&integration_type=0&scope=bot+applications.commands",
                DiscordInviteLink.url("1548331274198982807"));
        assertEquals("", DiscordInviteLink.url(""));
        assertEquals("", DiscordInviteLink.url("abc&scope=evil"));
    }

    @Test
    void clientOnlyOpensDiscordOauthLinks() {
        assertTrue(DiscordInviteLink.trusted(DiscordInviteLink.url("1548331274198982807")));
        assertFalse(DiscordInviteLink.trusted("https://example.com/?https://discord.com/oauth2/authorize?client_id="));
        assertFalse(DiscordInviteLink.trusted(null));
    }

    @Test
    void attentionIsShownOnlyToMembersWhoCanFixIt() {
        assertEquals(NotificationAttention.NONE, NotificationAttention.of(false, true, false, 0, 0, 0));
        assertEquals(NotificationAttention.NONE, NotificationAttention.of(true, false, false, 0, 0, 0));
        assertEquals(NotificationAttention.UNLINKED, NotificationAttention.of(true, true, false, 0, 0, 0));
        assertEquals(NotificationAttention.PROBLEM, NotificationAttention.of(true, true, true, 1, 10, 0));
        assertEquals(NotificationAttention.PROBLEM, NotificationAttention.of(true, true, true, 0, 10, 20));
        assertEquals(NotificationAttention.NONE, NotificationAttention.of(true, true, true, 0, 20, 10));
    }
}
