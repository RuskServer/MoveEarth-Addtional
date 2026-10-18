package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.pvp.PvpClientState;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationNameplatesPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationTabOrder;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.Comparator;

/**
 * Nations in the tab list, from the same data as the nameplates: each name gets its nation's tag in the colour
 * of its relation to you, and players are grouped by that relation. It is added on the client after the server
 * has sent the list, so the TAB plugin's name formatting and team order are left as they are: the tag goes in
 * front of TAB's name, and TAB's order still decides within each group. Off during a PvP match, whose teams
 * order the list.
 */
public final class NationTabList {
    private NationTabList() {
    }

    private static boolean active() {
        return ClientDisplayConfig.NATION_TABLIST.get() && !PvpClientState.isMatchActive();
    }

    /** The tab list's order with the nation groups put in front of it; spectators stay at the bottom. */
    public static Comparator<PlayerInfo> order(Comparator<PlayerInfo> original) {
        Comparator<PlayerInfo> spectatorsLast = Comparator.comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0);
        Comparator<PlayerInfo> nations = Comparator.comparingInt(NationTabList::group)
                .thenComparing(NationTabList::tag, String.CASE_INSENSITIVE_ORDER);
        return (first, second) -> {
            if (!active()) return original.compare(first, second);
            return spectatorsLast.thenComparing(nations).thenComparing(original).compare(first, second);
        };
    }

    public static Component decorate(PlayerInfo info, Component name) {
        if (!active() || name == null) return name;
        S2C_NationNameplatesPacket.Entry entry = NationNameplateClientState.entry(info.getProfile().getId());
        if (entry == null || entry.prefix().isBlank()) return name;
        return Component.literal(entry.prefix()).withStyle(NationNameplateClientState.color(entry.relation()))
                .append(name);
    }

    private static int group(PlayerInfo info) {
        S2C_NationNameplatesPacket.Entry entry = NationNameplateClientState.entry(info.getProfile().getId());
        return entry == null ? NationTabOrder.group(null, false)
                : NationTabOrder.group(entry.relation(), !entry.prefix().isBlank());
    }

    private static String tag(PlayerInfo info) {
        S2C_NationNameplatesPacket.Entry entry = NationNameplateClientState.entry(info.getProfile().getId());
        return entry == null ? "" : entry.prefix();
    }
}
