package com.ruskserver.moveearth_addtional.entity;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class AirshipRaiderEvents {
    private AirshipRaiderEvents() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof AirshipRaiderEntity raider && raider.getRaidId() > 0) {
            AirshipRaidManager.onRaiderDeath(raider.getRaidId(), raider.getUUID());
        }
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (event.getEntity() instanceof AirshipRaiderEntity raider && raider.getRaidId() > 0
                && !AirshipRaidManager.isActiveRaid(raider.getRaidId())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof AirshipRaiderEntity raider) || raider.getRaidId() <= 0) return;
        net.minecraft.world.entity.Entity.RemovalReason reason = raider.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            AirshipRaidManager.onRaiderDeath(raider.getRaidId(), raider.getUUID());
        }
    }
}
