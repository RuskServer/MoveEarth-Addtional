package com.ruskserver.moveearth_addtional.raid;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.SableAirshipController;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class AirshipDamageEvents {
    private AirshipDamageEvents() {
    }

    @SubscribeEvent
    public static void onAmmoHitBlock(AmmoHitBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos hitPos = event.getHitResult().getBlockPos();
        UUID shipId = null;
        SubLevelAccess access = SableCompanion.INSTANCE.getContaining(level, hitPos);
        if (access != null) shipId = access.getUniqueId();
        if (shipId == null) return;
        float damage = Math.max(8.0F, event.getAmmo().getDamage(event.getHitResult().getLocation()));
        AirshipRaidManager.damageAirship(level.getServer(), shipId, hitPos, event.getState(), damage);
    }
}
