package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/** Ends alliances whose termination notice has run out and tells both nations about each step. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AllianceTerminationService {
    private static final long COOLDOWN_PRUNE_INTERVAL_TICKS = 1200L;

    private AllianceTerminationService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 20L != 11L) return;
        NationSavedData nations = NationSavedData.get(server);
        long openNow = OpenTimeService.now(server);
        for (NationSavedData.EndedAlliance ended : nations.expireAlliances(openNow)) {
            notifyBoth(server, nations, ended.declaringNation(), ended.otherNation(),
                    "message.moveearth_addtional.diplomacy.alliance_end.expired",
                    "message.moveearth_addtional.diplomacy.alliance_end.expired",
                    null, "同盟が失効しました");
        }
        if (gameTime % COOLDOWN_PRUNE_INTERVAL_TICKS == 11L) nations.pruneMembershipCooldowns(openNow);
    }

    public static void declared(MinecraftServer server, UUID declaringNation, UUID otherNation, long openNow) {
        NationSavedData nations = NationSavedData.get(server);
        long remaining = nations.allianceEnd(declaringNation, otherNation)
                .map(end -> AllianceTerminationPolicy.remaining(end.endsAt(), openNow))
                .orElse(AllianceTerminationPolicy.NOTICE_OPEN_TICKS);
        notifyBoth(server, nations, declaringNation, otherNation,
                "message.moveearth_addtional.diplomacy.alliance_end.declared_own",
                "message.moveearth_addtional.diplomacy.alliance_end.declared_other",
                MembershipCooldownService.duration(remaining),
                "同盟破棄が通告されました（サーバー開放時間で2時間後に失効）");
    }

    public static void cancelled(MinecraftServer server, UUID declaringNation, UUID otherNation) {
        NationSavedData nations = NationSavedData.get(server);
        notifyBoth(server, nations, declaringNation, otherNation,
                "message.moveearth_addtional.diplomacy.alliance_end.cancelled_own",
                "message.moveearth_addtional.diplomacy.alliance_end.cancelled_other",
                null, "同盟破棄の通告が撤回されました");
    }

    /**
     * Chats to the online members of both nations and queues the Discord bridge. Each side is told the
     * other nation's name; {@code extra} (a duration) follows it when present.
     */
    private static void notifyBoth(MinecraftServer server, NationSavedData nations, UUID declaringNation,
                                   UUID otherNation, String declaringKey, String otherKey,
                                   Component extra, String discordSummary) {
        if (declaringNation == null || otherNation == null) return;
        String declaringLabel = label(nations, declaringNation);
        String otherLabel = label(nations, otherNation);
        chat(server, nations, declaringNation, extra == null
                ? Component.translatable(declaringKey, otherLabel)
                : Component.translatable(declaringKey, otherLabel, extra));
        chat(server, nations, otherNation, extra == null
                ? Component.translatable(otherKey, declaringLabel)
                : Component.translatable(otherKey, declaringLabel, extra));
        NationNotificationService.publish(server, List.of(declaringNation),
                NationNotificationSavedData.EventType.SYSTEM, null, null, null,
                List.of(discordSummary + "：" + otherLabel));
        NationNotificationService.publish(server, List.of(otherNation),
                NationNotificationSavedData.EventType.SYSTEM, null, null, null,
                List.of(discordSummary + "：" + declaringLabel));
    }

    private static void chat(MinecraftServer server, NationSavedData nations, UUID nationId, Component body) {
        NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
        if (nation == null) return;
        for (UUID memberId : nation.members().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) player.sendSystemMessage(MoveEarthMessage.warning(body));
        }
    }

    private static String label(NationSavedData nations, UUID nationId) {
        return nations.nation(nationId)
                .map(nation -> nation.tag().isBlank() ? nation.name() : "[" + nation.tag() + "] " + nation.name())
                .orElse("?");
    }
}
