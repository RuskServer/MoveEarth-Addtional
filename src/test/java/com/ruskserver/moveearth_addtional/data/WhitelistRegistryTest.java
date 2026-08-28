package com.ruskserver.moveearth_addtional.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WhitelistRegistryTest {

    private WhitelistRegistry registry;
    private UUID owner;
    private UUID member1;
    private UUID member2;

    @BeforeEach
    public void setUp() {
        registry = new WhitelistRegistry();
        owner = UUID.randomUUID();
        member1 = UUID.randomUUID();
        member2 = UUID.randomUUID();
    }

    @Test
    public void testAddAndCheckWhitelist() {
        assertFalse(registry.isWhitelisted(owner, member1));

        registry.addToWhitelist(owner, member1, "PlayerOne");
        assertTrue(registry.isWhitelisted(owner, member1));
        assertFalse(registry.isWhitelisted(owner, member2));

        Set<UUID> uuids = registry.getMemberUuids(owner);
        assertEquals(1, uuids.size());
        assertTrue(uuids.contains(member1));

        Map<UUID, String> members = registry.getMembers(owner);
        assertEquals("PlayerOne", members.get(member1));

        List<String> displayNames = registry.getMemberNamesForDisplay(owner);
        assertTrue(displayNames.contains("PlayerOne"));
    }

    @Test
    public void testRemoveFromWhitelist() {
        registry.addToWhitelist(owner, member1, "PlayerOne");
        registry.addToWhitelist(owner, member2, "PlayerTwo");

        assertTrue(registry.removeFromWhitelist(owner, member1));
        assertFalse(registry.isWhitelisted(owner, member1));
        assertTrue(registry.isWhitelisted(owner, member2));

        // 名前指定削除
        assertTrue(registry.removeFromWhitelistByName(owner, "PlayerTwo"));
        assertFalse(registry.isWhitelisted(owner, member2));
    }

    @Test
    public void testUnresolvedNamesFallback() {
        registry.addToWhitelist(owner, member1, "PlayerOne");
        registry.addByNameFallback(owner, "UnresolvedPlayer", null);

        assertTrue(registry.isWhitelisted(owner, member1));
        assertFalse(registry.isWhitelisted(owner, member2));

        Set<String> unresolved = registry.getUnresolvedNames(owner);
        assertEquals(1, unresolved.size());
        assertTrue(unresolved.contains("UnresolvedPlayer"));

        List<String> displayNames = registry.getMemberNamesForDisplay(owner);
        assertEquals(2, displayNames.size());
        assertTrue(displayNames.contains("PlayerOne"));
        assertTrue(displayNames.contains("UnresolvedPlayer"));

        // 未解決プレイヤーが後でUUID解決されて追加された場合、未解決リストから自動削除されるか
        UUID resolvedUuid = UUID.randomUUID();
        registry.addToWhitelist(owner, resolvedUuid, "UnresolvedPlayer");

        assertTrue(registry.isWhitelisted(owner, resolvedUuid));
        assertEquals(0, registry.getUnresolvedNames(owner).size());
        assertEquals(2, registry.getMemberUuids(owner).size());
    }

    @Test
    public void testMultipleOwnersIsolation() {
        UUID owner2 = UUID.randomUUID();

        registry.addToWhitelist(owner, member1, "PlayerOne");
        registry.addToWhitelist(owner2, member2, "PlayerTwo");

        assertTrue(registry.isWhitelisted(owner, member1));
        assertFalse(registry.isWhitelisted(owner, member2));

        assertFalse(registry.isWhitelisted(owner2, member1));
        assertTrue(registry.isWhitelisted(owner2, member2));
    }
}
