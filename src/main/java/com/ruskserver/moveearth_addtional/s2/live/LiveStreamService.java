package com.ruskserver.moveearth_addtional.s2.live;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_LiveStreamersPacket;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /live}: marks a player as recording or streaming, as the server rules ask
 * them to. A red {@code [LIVE]} goes in front of their name in the tab list, above
 * their head and on their chat lines, so others know what they say may be on a
 * stream. The setting survives death and reconnecting until they turn it off.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class LiveStreamService {
    private static final String NBT_KEY = "MoveEarthLive";
    private static final Set<UUID> ONLINE_LIVE = ConcurrentHashMap.newKeySet();

    private LiveStreamService() { }

    /** The prefix shown before a streaming player's name. */
    public static MutableComponent prefix() {
        return Component.literal("[LIVE] ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
    }

    public static boolean isLive(Player player) {
        return ONLINE_LIVE.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("live")
                .executes(context -> toggle(context.getSource().getPlayerOrException())));
    }

    private static int toggle(ServerPlayer player) {
        boolean live = !persisted(player).getBoolean(NBT_KEY);
        persisted(player).putBoolean(NBT_KEY, live);
        if (live) ONLINE_LIVE.add(player.getUUID());
        else ONLINE_LIVE.remove(player.getUUID());
        player.refreshTabListName();
        broadcast();
        player.sendSystemMessage(live
                ? MoveEarthMessage.success(Component.translatable("message.moveearth_addtional.live.on"))
                : MoveEarthMessage.info(Component.translatable("message.moveearth_addtional.live.off")));
        return 1;
    }

    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!isLive(event.getEntity())) return;
        Component name = event.getDisplayName() != null ? event.getDisplayName() : event.getEntity().getName();
        event.setDisplayName(prefix().append(name.copy().withStyle(ChatFormatting.WHITE)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (persisted(player).getBoolean(NBT_KEY)) {
            ONLINE_LIVE.add(player.getUUID());
            player.refreshTabListName();
            player.sendSystemMessage(MoveEarthMessage.info(
                    Component.translatable("message.moveearth_addtional.live.still_on")));
        }
        broadcast();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (ONLINE_LIVE.remove(event.getEntity().getUUID())) broadcast();
    }

    private static void broadcast() {
        PacketDistributor.sendToAllPlayers(new S2C_LiveStreamersPacket(List.copyOf(ONLINE_LIVE)));
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
