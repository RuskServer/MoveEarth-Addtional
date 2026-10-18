package com.ruskserver.moveearth_addtional.s2.tutorial;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Objects;

/**
 * Completes the tutorial's atlas step once a Map Atlases atlas in the player's inventory holds a map, which
 * it starts drawing by itself while in the hotbar. Checked every few seconds, and only until the step is done.
 * Map Atlases is not a compile dependency, so its atlas and map list are looked up by id.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TutorialAtlasCheck {
    private static final int INTERVAL_TICKS = 100;
    private static final ResourceLocation ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "tutorial/atlas_mapped");
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("map_atlases", "atlas");
    private static final ResourceLocation MAPS = ResourceLocation.fromNamespaceAndPath("map_atlases", "map_collection");

    private TutorialAtlasCheck() { }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((player.tickCount + Math.floorMod(player.getUUID().hashCode(), INTERVAL_TICKS)) % INTERVAL_TICKS != 0) return;
        var advancement = player.server.getAdvancements().get(ADVANCEMENT);
        if (advancement == null || player.getAdvancements().getOrStartProgress(advancement).isDone()) return;
        if (!BuiltInRegistries.ITEM.containsKey(ATLAS)) return;
        DataComponentType<?> maps = BuiltInRegistries.DATA_COMPONENT_TYPE.get(MAPS);
        if (maps == null) return;
        Item atlas = BuiltInRegistries.ITEM.get(ATLAS);
        Object empty = new ItemStack(atlas).get(maps);
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(atlas) && !Objects.equals(stack.get(maps), empty)) {
                ModCriteria.trigger(player, ModCriteria.ATLAS_MAPPED);
                return;
            }
        }
    }
}
