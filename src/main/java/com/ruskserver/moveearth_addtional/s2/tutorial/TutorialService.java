package com.ruskserver.moveearth_addtional.s2.tutorial;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TutorialPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationOnboardingService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's place in {@link TutorialCatalog} and keeps the HUD line
 * under their balance current. Starts once first-join onboarding is over; no
 * rewards. Players can skip the whole tutorial at any time and restart it.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TutorialService {
    private static final String NBT_ROOT = "MoveEarthTutorial";
    private static final String NBT_SKIPPED = "Skipped";
    private static final String NBT_COMPLETED = "Completed";
    /** How many steps this player has passed, so each is reported to analytics once. */
    private static final String NBT_REACHED = "Reached";
    private static final Map<UUID, S2C_TutorialPacket> LAST_SENT = new HashMap<>();

    private TutorialService() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20L != 7L) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            S2C_TutorialPacket view = view(player);
            if (view.equals(LAST_SENT.get(player.getUUID()))) continue;
            LAST_SENT.put(player.getUUID(), view);
            PacketDistributor.sendToPlayer(player, view);
        }
    }

    public static void skip(ServerPlayer player) {
        CompoundTag state = state(player);
        if (!state.getBoolean(NBT_SKIPPED) && !state.getBoolean(NBT_COMPLETED)) {
            int reached = state.getInt(NBT_REACHED);
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_SKIPPED, player, reached,
                    reached < TutorialCatalog.STEPS.size() ? TutorialCatalog.STEPS.get(reached).id() : null);
        }
        state.putBoolean(NBT_SKIPPED, true);
        LAST_SENT.remove(player.getUUID());
        player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "tutorial.moveearth_addtional.skipped")));
    }

    public static void restart(ServerPlayer player) {
        CompoundTag state = state(player);
        state.remove(NBT_SKIPPED);
        state.remove(NBT_COMPLETED);
        LAST_SENT.remove(player.getUUID());
        com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_RESTARTED, player, state.getInt(NBT_REACHED), null);
        player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "tutorial.moveearth_addtional.restarted")));
    }

    private static S2C_TutorialPacket view(ServerPlayer player) {
        CompoundTag state = state(player);
        if (state.getBoolean(NBT_SKIPPED) || state.getBoolean(NBT_COMPLETED)
                || NationOnboardingService.pending(player)) {
            return S2C_TutorialPacket.hidden();
        }
        NationSavedData nations = NationSavedData.get(player.server);
        boolean canReinforce = nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT);
        int index = TutorialCatalog.current(step -> done(player, nations, step, canReinforce));
        recordReached(player, state, index < 0 ? TutorialCatalog.STEPS.size() : index);
        if (index < 0) {
            state.putBoolean(NBT_COMPLETED, true);
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_COMPLETED, player, TutorialCatalog.STEPS.size(), null);
            player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "tutorial.moveearth_addtional.completed")));
            return S2C_TutorialPacket.hidden();
        }
        TutorialCatalog.Step step = TutorialCatalog.STEPS.get(index);
        return new S2C_TutorialPacket(true, index, TutorialCatalog.STEPS.size(),
                TutorialCatalog.textId(step, canReinforce), step.icon());
    }

    private static void recordReached(ServerPlayer player, CompoundTag state, int reached) {
        int previous = state.getInt(NBT_REACHED);
        if (reached <= previous) return;
        for (int step = previous; step < reached; step++) {
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_STEP, player, step + 1, TutorialCatalog.STEPS.get(step).id());
        }
        state.putInt(NBT_REACHED, reached);
    }

    private static boolean done(ServerPlayer player, NationSavedData nations, TutorialCatalog.Step step,
                                boolean canReinforce) {
        return switch (step.kind()) {
            case NATION -> nations.nationIdFor(player.getUUID()).isPresent();
            case DEFENSE -> advancementDone(player, canReinforce ? step.advancement() : step.memberAdvancement());
            case ADVANCEMENT -> advancementDone(player, step.advancement());
        };
    }

    private static boolean advancementDone(ServerPlayer player, String path) {
        AdvancementHolder advancement = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, path));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static CompoundTag state(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(NBT_ROOT, Tag.TAG_COMPOUND)) persisted.put(NBT_ROOT, new CompoundTag());
        return persisted.getCompound(NBT_ROOT);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }
}
