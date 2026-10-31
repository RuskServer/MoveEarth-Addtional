package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * Setting a nation's tax rate ({@link NationTaxPolicy}); every citizen is told when it changes. The tax collected
 * from Jobs is paid into each treasury every few minutes as one transaction.
 */
@EventBusSubscriber(modid = com.ruskserver.moveearth_addtional.Moveearth_addtional.MODID)
public final class NationTaxService {
    private static final int FLUSH_INTERVAL_TICKS = 5 * 60 * 20;

    private NationTaxService() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % FLUSH_INTERVAL_TICKS == 0) flush(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        flush(event.getServer());
    }

    private static void flush(MinecraftServer server) {
        NationSavedData nations = NationSavedData.get(server);
        com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.get(server)
                .flushJobTax(nation -> nations.nation(nation).isPresent());
    }

    public static boolean setRate(ServerPlayer player, int requested) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null) return false;
        if (!NationUpkeepService.canManage(player)) {
            player.sendSystemMessage(MoveEarthMessage.error(
                    Component.translatable("message.moveearth_addtional.tax.permission")));
            return false;
        }
        NationTaxSavedData taxes = NationTaxSavedData.get(player.server);
        NationTaxSavedData.Rate before = taxes.rate(nationId);
        int target = NationTaxPolicy.clamp(requested);
        long now = System.currentTimeMillis();
        if (target == before.percent()) return false;
        if (!NationTaxPolicy.mayChange(before.percent(), target, before.lastRaisedAt(), now)) {
            long minutes = NationTaxPolicy.raiseWaitMinutes(before.lastRaisedAt(), now);
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.tax.raise_wait", minutes / 60L, minutes % 60L)));
            return false;
        }
        taxes.set(nationId, target, now);
        String name = player.getGameProfile().getName();
        // A rate taken out of everyone's pay is never changed quietly.
        NationNotificationService.publish(player.server, List.of(nationId),
                NationNotificationSavedData.EventType.SYSTEM, null, null,
                Component.translatable("message.moveearth_addtional.tax.changed", name, before.percent(), target),
                List.of("国税が" + before.percent() + "%から" + target + "%に変わりました（変更者: " + name + "）"));
        return true;
    }
}
