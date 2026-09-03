package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.monster.Phantom;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PhantomRestHandler {
    private PhantomRestHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCanPlayerSleep(CanPlayerSleepEvent event) {
        // Observe the final result after other handlers have had an opportunity
        // to allow or reject sleeping. Merely clicking an unusable bed does not
        // grant phantom protection.
        if (event.getProblem() != null) {
            return;
        }

        ServerPlayer player = event.getEntity();
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 0);
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Phantom phantom)
                || !(event.getOriginalAboutToBeSetTarget() instanceof ServerPlayer player)) {
            return;
        }

        int timeSinceRest = player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        if (!PhantomRestPolicy.isProtectedFromPhantoms(timeSinceRest)) {
            return;
        }

        // Prevent downstream target handling from acting on an entity that has
        // just been removed. discard() produces no death animation or drops.
        event.setNewAboutToBeSetTarget(null);
        phantom.discard();
    }
}
