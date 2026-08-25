package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.C2S_JobsActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OpenJobsScreenPacket;
import com.ruskserver.moveearth_addtional.network.S2C_JobsLeaderboardPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds Jobs GUI snapshots and validates every GUI action on the server. */
public final class JobsScreenSync {
    private static final int ADMIN_PERMISSION_LEVEL = 2;
    private static final int LEADERBOARD_DISPLAY_LIMIT = 10;

    private JobsScreenSync() {
    }

    public static void open(ServerPlayer viewer) {
        send(viewer, viewer);
    }

    public static void send(ServerPlayer viewer, ServerPlayer subject) {
        JobProgressSavedData data = JobProgressSavedData.get(viewer.getServer());
        data.rememberName(viewer.getUUID(), viewer.getGameProfile().getName());
        data.rememberName(subject.getUUID(), subject.getGameProfile().getName());
        data.reconcileActiveJobs(subject.getUUID(), JobDefinitions.INSTANCE.ids());
        JobProgressSavedData.PlayerSnapshot snapshot = data.snapshot(subject.getUUID());
        JobProgressSavedData.RecurringPointSnapshot recurring = data.recurringSnapshot(subject.getUUID(),
                viewer.getServer().overworld().getGameTime());
        List<S2C_OpenJobsScreenPacket.JobEntry> entries = JobDefinitions.INSTANCE.all().stream()
                .map(definition -> entry(definition, snapshot))
                .toList();
        List<String> onlinePlayers = viewer.getServer().getPlayerList().getPlayers().stream()
                .map(player -> player.getGameProfile().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        boolean canAdmin = viewer.createCommandSourceStack().hasPermission(ADMIN_PERMISSION_LEVEL);
        PacketDistributor.sendToPlayer(viewer, new S2C_OpenJobsScreenPacket(
                subject.getGameProfile().getName(), viewer.getUUID().equals(subject.getUUID()), canAdmin,
                snapshot.points(), recurring.xpTowardsNextPoint(), recurring.pointsInWindow(),
                secondsRemaining(recurring.ticksRemaining()), JobProgressSavedData.MAX_ACTIVE_JOBS,
                entries, onlinePlayers));
    }

    private static int secondsRemaining(long ticks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (ticks + 19L) / 20L));
    }

    public static void sendLeaderboard(ServerPlayer viewer, JobDefinition definition) {
        JobProgressSavedData data = JobProgressSavedData.get(viewer.getServer());
        data.rememberName(viewer.getUUID(), viewer.getGameProfile().getName());
        List<S2C_JobsLeaderboardPacket.Entry> entries = data
                .leaderboard(definition.id(), LEADERBOARD_DISPLAY_LIMIT).stream()
                .map(entry -> new S2C_JobsLeaderboardPacket.Entry(entry.playerName(), entry.level(),
                        entry.xpInLevel(), entry.totalXp()))
                .toList();
        PacketDistributor.sendToPlayer(viewer,
                new S2C_JobsLeaderboardPacket(definition.id(), entries));
    }

    private static S2C_OpenJobsScreenPacket.JobEntry entry(
            JobDefinition definition, JobProgressSavedData.PlayerSnapshot snapshot) {
        JobProgressSavedData.ProgressSnapshot progress = snapshot.progress(definition.id());
        double nextXp = progress.level() >= definition.maxLevel()
                ? 0 : definition.xpNeededForNextLevel(progress.level());
        return new S2C_OpenJobsScreenPacket.JobEntry(
                definition.id(), definition.displayName(), definition.description(),
                definition.maxLevel(), definition.pointsPerLevel(),
                snapshot.activeJobs().contains(definition.id()), progress.level(), progress.xpInLevel(),
                nextXp, progress.totalXp());
    }

    public static void handleAction(ServerPlayer viewer, C2S_JobsActionPacket packet) {
        Action action;
        try {
            action = Action.valueOf(packet.action().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return;
        }

        if (action.admin && !viewer.createCommandSourceStack().hasPermission(ADMIN_PERMISSION_LEVEL)) {
            viewer.sendSystemMessage(Component.literal("[Jobs] この操作を行う権限がありません。"));
            send(viewer, viewer);
            return;
        }

        ServerPlayer target = action.admin ? findTarget(viewer, packet.targetName()) : viewer;
        if (target == null) {
            viewer.sendSystemMessage(Component.literal("[Jobs] 対象プレイヤーが見つかりません。"));
            send(viewer, viewer);
            return;
        }

        Optional<JobDefinition> definition = resolve(packet.jobId());
        JobProgressSavedData data = JobProgressSavedData.get(viewer.getServer());
        switch (action) {
            case JOIN -> {
                if (definition.isEmpty()) return;
                data.join(viewer.getUUID(), definition.get().id());
                send(viewer, viewer);
            }
            case LEAVE -> {
                if (definition.isEmpty()) return;
                data.leave(viewer.getUUID(), definition.get().id());
                send(viewer, viewer);
            }
            case VIEW -> send(viewer, target);
            case ADD_XP -> {
                if (definition.isEmpty() || packet.amount() <= 0
                        || packet.amount() > C2S_JobsActionPacket.MAX_ABSOLUTE_AMOUNT) return;
                data.awardAdmin(target.getUUID(), definition.get(), packet.amount(),
                        viewer.getServer().overworld().getGameTime());
                audit(viewer, target, "XP +" + packet.amount() + " (" + definition.get().id() + ")");
                send(viewer, target);
            }
            case ADD_POINTS -> {
                if (packet.amount() == 0 || Math.abs((long) packet.amount()) > C2S_JobsActionPacket.MAX_ABSOLUTE_AMOUNT) return;
                data.addPoints(target.getUUID(), packet.amount());
                audit(viewer, target, "ポイント " + signed(packet.amount()));
                send(viewer, target);
            }
            case RESET -> {
                data.reset(target.getUUID());
                audit(viewer, target, "全職業データをリセット");
                send(viewer, target);
            }
            case REFRESH -> send(viewer, viewer);
            case RANKING -> {
                if (definition.isEmpty()) return;
                sendLeaderboard(viewer, definition.get());
            }
        }
    }

    private static ServerPlayer findTarget(ServerPlayer viewer, String name) {
        if (name.isBlank()) {
            return viewer;
        }
        return viewer.getServer().getPlayerList().getPlayerByName(name);
    }

    private static Optional<JobDefinition> resolve(String input) {
        ResourceLocation id = ResourceLocation.tryParse(input);
        return id == null ? Optional.empty() : JobDefinitions.INSTANCE.get(id);
    }

    private static void audit(ServerPlayer viewer, ServerPlayer target, String operation) {
        String message = viewer.getScoreboardName() + " -> " + target.getScoreboardName() + ": " + operation;
        Moveearth_addtional.LOGGER.info("[Jobs admin] {}", message);
        viewer.sendSystemMessage(Component.literal("[Jobs管理] " + message));
    }

    private static String signed(int amount) {
        return amount > 0 ? "+" + amount : Integer.toString(amount);
    }

    private enum Action {
        JOIN(false),
        LEAVE(false),
        REFRESH(false),
        RANKING(false),
        VIEW(true),
        ADD_XP(true),
        ADD_POINTS(true),
        RESET(true);

        private final boolean admin;

        Action(boolean admin) {
            this.admin = admin;
        }
    }
}
