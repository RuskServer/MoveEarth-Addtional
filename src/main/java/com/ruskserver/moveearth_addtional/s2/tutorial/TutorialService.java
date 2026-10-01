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
    /** Steps already reported to analytics as done, so each is reported once. */
    private static final String NBT_DONE = "Done";
    /** The older index-based log; read once to fill {@link #NBT_DONE}. */
    private static final String NBT_REACHED = "Reached";
    private static final Map<UUID, S2C_TutorialPacket> LAST_SENT = new HashMap<>();
    /** Players told this session that the deferred nation goal is still open. */
    private static final java.util.Set<UUID> NATION_REMINDED = new java.util.HashSet<>();

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
            NationSavedData nations = NationSavedData.get(player.server);
            boolean[] done = doneSteps(player, nations);
            int current = TutorialCatalog.current(step -> done[TutorialCatalog.STEPS.indexOf(step)]);
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_SKIPPED, player, doneCount(done),
                    current >= 0 ? TutorialCatalog.STEPS.get(current).id() : null);
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
        com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_RESTARTED, player,
                doneCount(doneSteps(player, NationSavedData.get(player.server))), null);
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
        boolean[] done = doneSteps(player, nations);
        int index = TutorialCatalog.current(step -> done[TutorialCatalog.STEPS.indexOf(step)]);
        recordDone(player, state, done);
        if (index < 0) {
            state.putBoolean(NBT_COMPLETED, true);
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_COMPLETED, player, TutorialCatalog.STEPS.size(), null);
            player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "tutorial.moveearth_addtional.completed")));
            return S2C_TutorialPacket.hidden();
        }
        TutorialCatalog.Step step = TutorialCatalog.STEPS.get(index);
        remindNation(player, nations, step);
        return new S2C_TutorialPacket(true, index, TutorialCatalog.STEPS.size(),
                TutorialCatalog.textId(step, canReinforce), step.icon());
    }

    /**
     * The nation step no longer holds the HUD, so a nationless player with no application waiting
     * is reminded once per session that joining or founding is still a goal. Players waiting for
     * approval are not: there is nothing for them to do but wait.
     */
    private static void remindNation(ServerPlayer player, NationSavedData nations, TutorialCatalog.Step shown) {
        if (shown.kind() == TutorialCatalog.Kind.NATION || NATION_REMINDED.contains(player.getUUID())) return;
        if (nations.nationIdFor(player.getUUID()).isPresent()
                || nations.joinApplicationFor(player.getUUID()).isPresent()) return;
        NATION_REMINDED.add(player.getUUID());
        player.sendSystemMessage(MoveEarthMessage.tip(Component.translatable(
                "tutorial.moveearth_addtional.nation_reminder")));
    }

    private static boolean[] doneSteps(ServerPlayer player, NationSavedData nations) {
        boolean canReinforce = nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT);
        boolean[] done = new boolean[TutorialCatalog.STEPS.size()];
        for (int index = 0; index < done.length; index++) {
            done[index] = done(player, nations, TutorialCatalog.STEPS.get(index), canReinforce);
        }
        return done;
    }

    private static int doneCount(boolean[] done) {
        int count = 0;
        for (boolean step : done) if (step) count++;
        return count;
    }

    /** Reports each step once, when it is actually done ({@link TutorialStepLog}). */
    private static void recordDone(ServerPlayer player, CompoundTag state, boolean[] done) {
        if (!state.contains(NBT_DONE, Tag.TAG_COMPOUND)) {
            CompoundTag seeded = new CompoundTag();
            TutorialStepLog.migrated(state.getInt(NBT_REACHED), done).forEach(id -> seeded.putBoolean(id, true));
            state.put(NBT_DONE, seeded);
            state.remove(NBT_REACHED);
        }
        CompoundTag reported = state.getCompound(NBT_DONE);
        for (int index : TutorialStepLog.newlyDone(done, reported.getAllKeys())) {
            String id = TutorialCatalog.STEPS.get(index).id();
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.TUTORIAL_STEP, player, index + 1, id);
            reported.putBoolean(id, true);
        }
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
        NATION_REMINDED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
        NATION_REMINDED.remove(event.getEntity().getUUID());
    }
}
