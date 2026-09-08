package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.network.S2C_OpenPvpTasksPacket;
import com.ruskserver.moveearth_addtional.tpa.OpenDayCycle;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PvpRewardData extends SavedData {
    public static final int CRATE_COST = 100;
    private final Map<UUID, PlayerProgress> players = new HashMap<>();
    private int eventCycle;

    public int points(UUID id) {
        return progress(id).points;
    }

    public void beginEvent() {
        eventCycle++;
        setDirty();
    }

    public void recordKill(ServerPlayer player, boolean closeRange) {
        addProgress(player, PvpTaskType.KILL, 1);
        if (closeRange) addProgress(player, PvpTaskType.CLOSE_RANGE_KILL, 1);
    }

    public void recordZoneSecond(ServerPlayer player) {
        addProgress(player, PvpTaskType.ZONE_SECONDS, 1);
    }

    public void recordMatchResult(ServerPlayer player, boolean won) {
        addProgress(player, PvpTaskType.MATCH_PLAY, 1);
        if (won) addProgress(player, PvpTaskType.MATCH_WIN, 1);
    }

    public boolean claim(ServerPlayer player, String taskId) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(Component.literal("§c試合中はタスク報酬を受け取れません。"));
            return false;
        }
        PvpTaskDefinition definition = PvpTaskDefinition.BY_ID.get(taskId);
        if (definition == null) return false;
        PlayerProgress playerProgress = progress(player.getUUID());
        TaskProgress task = task(playerProgress, definition);
        if (task.claimed || task.value < definition.target()) return false;

        ItemStack itemReward = new ItemStack(definition.itemReward(), definition.itemRewardCount());
        if (!canFullyAdd(player, itemReward)) {
            player.sendSystemMessage(Component.literal("§c素材報酬を受け取るためのインベントリ空きがありません。"));
            return false;
        }

        if (!player.getInventory().add(itemReward)) {
            player.drop(itemReward, false);
        }
        player.inventoryMenu.broadcastChanges();
        playerProgress.points += definition.pointReward();
        task.claimed = true;
        setDirty();
        player.sendSystemMessage(Component.literal("§a" + definition.title() + " の報酬を受け取りました: §6+"
                + definition.pointReward() + "pt §f＆ §b" + itemReward.getHoverName().getString()
                + " ×" + definition.itemRewardCount()));
        return true;
    }

    private static boolean canFullyAdd(ServerPlayer player, ItemStack reward) {
        int capacity = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing.isEmpty()) {
                capacity += reward.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(existing, reward)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (capacity >= reward.getCount()) return true;
        }
        return false;
    }

    public void openTaskScreen(ServerPlayer player) {
        PlayerProgress playerProgress = progress(player.getUUID());
        List<S2C_OpenPvpTasksPacket.TaskEntry> entries = new ArrayList<>();
        for (PvpTaskDefinition definition : PvpTaskDefinition.ALL) {
            TaskProgress task = task(playerProgress, definition);
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(definition.itemReward());
            entries.add(new S2C_OpenPvpTasksPacket.TaskEntry(
                    definition.id(), definition.category().name(), definition.title(), definition.description(),
                    task.value, definition.target(), definition.pointReward(), itemId,
                    definition.itemRewardCount(), task.claimed));
        }
        PacketDistributor.sendToPlayer(player, new S2C_OpenPvpTasksPacket(playerProgress.points, entries));
    }

    public boolean exchangeCrate(ServerPlayer player) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(Component.literal("§c試合中は武器箱を交換できません。"));
            return false;
        }
        PlayerProgress progress = progress(player.getUUID());
        if (progress.points < CRATE_COST) return false;
        ItemStack crate = new ItemStack(ModItems.WEAPON_CRATE.get());
        if (!player.getInventory().add(crate)) {
            player.drop(crate, false);
        }
        player.inventoryMenu.broadcastChanges();
        progress.points -= CRATE_COST;
        setDirty();
        return true;
    }

    public String summary(UUID id) {
        PlayerProgress progress = progress(id);
        long claimable = PvpTaskDefinition.ALL.stream()
                .filter(definition -> {
                    TaskProgress task = task(progress, definition);
                    return !task.claimed && task.value >= definition.target();
                }).count();
        return "武器箱ポイント: " + progress.points + " | 受取可能タスク: " + claimable
                + " | /pvp tasks で確認";
    }

    private void addProgress(ServerPlayer player, PvpTaskType type, int amount) {
        PlayerProgress playerProgress = progress(player.getUUID());
        boolean changed = false;
        for (PvpTaskDefinition definition : PvpTaskDefinition.ALL) {
            if (definition.type() != type) continue;
            TaskProgress task = task(playerProgress, definition);
            if (task.claimed || task.value >= definition.target()) continue;
            int oldValue = task.value;
            task.value = Mth.clamp(task.value + amount, 0, definition.target());
            changed |= task.value != oldValue;
            if (oldValue < definition.target() && task.value >= definition.target()) {
                player.sendSystemMessage(Component.literal("タスク達成：" + definition.title()
                        + "（/pvp tasks から報酬を受け取れます）"));
            }
        }
        if (changed) setDirty();
    }

    private PlayerProgress progress(UUID id) {
        PlayerProgress progress = players.computeIfAbsent(id, ignored -> new PlayerProgress());
        refreshCycles(progress);
        return progress;
    }

    private void refreshCycles(PlayerProgress progress) {
        String dailyCycle = currentDailyCycle();
        if (!dailyCycle.equals(progress.dailyCycle)) {
            progress.dailyCycle = dailyCycle;
            clearCategory(progress, PvpTaskCategory.DAILY);
            setDirty();
        }
        if (progress.eventCycle != eventCycle) {
            progress.eventCycle = eventCycle;
            clearCategory(progress, PvpTaskCategory.EVENT);
            setDirty();
        }
    }

    private static void clearCategory(PlayerProgress progress, PvpTaskCategory category) {
        for (PvpTaskDefinition definition : PvpTaskDefinition.ALL) {
            if (definition.category() == category) progress.tasks.remove(definition.id());
        }
    }

    private static TaskProgress task(PlayerProgress progress, PvpTaskDefinition definition) {
        return progress.tasks.computeIfAbsent(definition.id(), ignored -> new TaskProgress());
    }

    private static String currentDailyCycle() {
        return OpenDayCycle.currentId();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("EventCycle", eventCycle);
        CompoundTag all = new CompoundTag();
        players.forEach((id, progress) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putInt("Points", progress.points);
            playerTag.putString("DailyCycle", progress.dailyCycle);
            playerTag.putInt("EventCycle", progress.eventCycle);
            CompoundTag tasksTag = new CompoundTag();
            progress.tasks.forEach((taskId, task) -> {
                CompoundTag taskTag = new CompoundTag();
                taskTag.putInt("Value", task.value);
                taskTag.putBoolean("Claimed", task.claimed);
                tasksTag.put(taskId, taskTag);
            });
            playerTag.put("Tasks", tasksTag);
            all.put(id.toString(), playerTag);
        });
        tag.put("Players", all);
        return tag;
    }

    public static PvpRewardData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpRewardData data = new PvpRewardData();
        data.eventCycle = tag.getInt("EventCycle");
        CompoundTag all = tag.getCompound("Players");
        for (String key : all.getAllKeys()) {
            try {
                CompoundTag playerTag = all.getCompound(key);
                PlayerProgress progress = new PlayerProgress();
                progress.points = playerTag.getInt("Points");
                progress.dailyCycle = playerTag.contains("DailyCycle")
                        ? playerTag.getString("DailyCycle") : currentDailyCycle();
                progress.eventCycle = playerTag.contains("EventCycle")
                        ? playerTag.getInt("EventCycle") : data.eventCycle;
                CompoundTag tasksTag = playerTag.getCompound("Tasks");
                for (String taskId : tasksTag.getAllKeys()) {
                    CompoundTag taskTag = tasksTag.getCompound(taskId);
                    TaskProgress task = new TaskProgress();
                    task.value = taskTag.getInt("Value");
                    task.claimed = taskTag.getBoolean("Claimed");
                    progress.tasks.put(taskId, task);
                }
                // v1.7の旧固定フィールドから途中進捗を移行する。
                migrateLegacy(playerTag, progress, "Kills", "daily_kills");
                migrateLegacy(playerTag, progress, "CloseKills", "daily_close_kills");
                migrateLegacy(playerTag, progress, "ZoneSeconds", "daily_zone");
                data.players.put(UUID.fromString(key), progress);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    private static void migrateLegacy(CompoundTag tag, PlayerProgress progress, String oldKey, String taskId) {
        if (!tag.contains(oldKey) || progress.tasks.containsKey(taskId)) return;
        TaskProgress task = new TaskProgress();
        PvpTaskDefinition definition = PvpTaskDefinition.BY_ID.get(taskId);
        task.value = Math.min(tag.getInt(oldKey), definition.target());
        progress.tasks.put(taskId, task);
    }

    public static PvpRewardData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PvpRewardData::new, PvpRewardData::load, null),
                "moveearth_pvp_rewards");
    }

    private static final class PlayerProgress {
        int points;
        String dailyCycle = currentDailyCycle();
        int eventCycle;
        final Map<String, TaskProgress> tasks = new HashMap<>();
    }

    private static final class TaskProgress {
        int value;
        boolean claimed;
    }
}
