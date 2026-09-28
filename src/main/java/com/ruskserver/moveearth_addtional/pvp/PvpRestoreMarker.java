package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

/**
 * Records, in the player's own saved data, which PvP stash has been restored to
 * them. Because it lives in the player file, it is on disk exactly when the
 * restored inventory is, which is when the stash may finally go.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PvpRestoreMarker {
    private static final String KEY = "MoveEarthPvpRestoredToken";

    private PvpRestoreMarker() { }

    /** Whether the stash with {@code token} has already been applied to this player. */
    static boolean restored(ServerPlayer player, UUID token) {
        CompoundTag data = persisted(player);
        return token != null && data.hasUUID(KEY) && token.equals(data.getUUID(KEY));
    }

    static void mark(ServerPlayer player, UUID token) {
        persisted(player).putUUID(KEY, token);
    }

    /** The player file now holds the restored inventory and its marker; the stash has done its job. */
    @SubscribeEvent
    public static void onPlayerSaved(PlayerEvent.SaveToFile event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PvpSessionSavedData sessions = PvpSessionSavedData.get(player.server);
        if (restored(player, sessions.token(player.getUUID()))) sessions.remove(player.getUUID());
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
