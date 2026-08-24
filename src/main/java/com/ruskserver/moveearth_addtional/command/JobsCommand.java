package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.jobs.JobDefinition;
import com.ruskserver.moveearth_addtional.jobs.JobDefinitions;
import com.ruskserver.moveearth_addtional.jobs.JobProgressSavedData;
import com.ruskserver.moveearth_addtional.jobs.JobsScreenSync;
import com.ruskserver.moveearth_addtional.jobs.JobXpFormat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class JobsCommand {
    private static final int PLAYER_PERMISSION_LEVEL = 0;
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private JobsCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("jobs")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
                .executes(context -> open(context.getSource().getPlayerOrException()))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource().getPlayerOrException())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("join")
                        .then(jobArgument()
                                .executes(context -> join(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "job")))))
                .then(Commands.literal("leave")
                        .then(jobArgument()
                                .executes(context -> leave(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "job")))))
                .then(Commands.literal("info")
                        .then(jobArgument()
                                .executes(context -> info(context.getSource(),
                                        StringArgumentType.getString(context, "job")))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.literal("addxp")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(jobArgument()
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> addXp(context.getSource(),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "job"),
                                                                IntegerArgumentType.getInteger(context, "amount")))))))
                        .then(Commands.literal("addpoints")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(context -> addPoints(context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> reset(context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))));
    }

    private static int open(ServerPlayer player) {
        JobsScreenSync.open(player);
        return 1;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> jobArgument() {
        return Commands.argument("job", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        JobDefinitions.INSTANCE.all().stream().map(definition -> definition.id().toString()), builder));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[Jobs] 利用可能な職業:"), false);
        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            source.sendSuccess(() -> Component.literal("- " + definition.id() + " : "
                    + definition.displayName() + " (最大Lv." + definition.maxLevel() + ")"), false);
        }
        return 1;
    }

    private static int status(ServerPlayer player) {
        JobProgressSavedData data = JobProgressSavedData.get(player.getServer());
        JobProgressSavedData.PlayerSnapshot snapshot = data.snapshot(player.getUUID());
        player.sendSystemMessage(Component.literal("[Jobs] ポイント: " + snapshot.points()
                + " / 選択中: " + snapshot.activeJobs().size() + "/" + JobProgressSavedData.MAX_ACTIVE_JOBS));
        if (snapshot.activeJobs().isEmpty()) {
            player.sendSystemMessage(Component.literal("[Jobs] /jobs join <職業> で職業を選択できます。"));
            return 1;
        }
        for (ResourceLocation id : snapshot.activeJobs()) {
            JobProgressSavedData.ProgressSnapshot progress = snapshot.progress(id);
            Optional<JobDefinition> definition = JobDefinitions.INSTANCE.get(id);
            String name = definition.map(JobDefinition::displayName).orElse(id.toString());
            String next = definition.filter(value -> progress.level() < value.maxLevel())
                    .map(value -> JobXpFormat.format(progress.xpInLevel()) + "/"
                            + value.xpNeededForNextLevel(progress.level()) + " XP")
                    .orElse("MAX");
            player.sendSystemMessage(Component.literal("- " + name + " Lv." + progress.level() + " (" + next + ")"));
        }
        return 1;
    }

    private static int join(ServerPlayer player, String input) {
        Optional<JobDefinition> definition = resolve(input);
        if (definition.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Jobs] 不明な職業です: " + input));
            return 0;
        }
        JobProgressSavedData.JoinResult result = JobProgressSavedData.get(player.getServer())
                .join(player.getUUID(), definition.get().id());
        switch (result) {
            case JOINED -> player.sendSystemMessage(Component.literal("[Jobs] "
                    + definition.get().displayName() + "を選択しました。"));
            case ALREADY_ACTIVE -> player.sendSystemMessage(Component.literal("[Jobs] その職業は選択済みです。"));
            case LIMIT_REACHED -> player.sendSystemMessage(Component.literal("[Jobs] 選択できる職業は最大"
                    + JobProgressSavedData.MAX_ACTIVE_JOBS + "個です。"));
        }
        return result == JobProgressSavedData.JoinResult.JOINED ? 1 : 0;
    }

    private static int leave(ServerPlayer player, String input) {
        Optional<JobDefinition> definition = resolve(input);
        if (definition.isEmpty()) {
            player.sendSystemMessage(Component.literal("[Jobs] 不明な職業です: " + input));
            return 0;
        }
        if (!JobProgressSavedData.get(player.getServer()).leave(player.getUUID(), definition.get().id())) {
            player.sendSystemMessage(Component.literal("[Jobs] その職業は選択していません。"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[Jobs] " + definition.get().displayName()
                + "を解除しました。進捗は保持されます。"));
        return 1;
    }

    private static int info(CommandSourceStack source, String input) {
        Optional<JobDefinition> definition = resolve(input);
        if (definition.isEmpty()) {
            source.sendFailure(Component.literal("[Jobs] 不明な職業です: " + input));
            return 0;
        }
        JobDefinition value = definition.get();
        source.sendSuccess(() -> Component.literal("[Jobs] " + value.displayName() + " (" + value.id()
                + ") 最大Lv." + value.maxLevel() + " / レベルごとに " + value.pointsPerLevel() + "ポイント"), false);
        if (!value.description().isBlank()) {
            source.sendSuccess(() -> Component.literal("- " + value.description()), false);
        }
        return 1;
    }

    private static int addXp(CommandSourceStack source, ServerPlayer player, String input, int amount) {
        Optional<JobDefinition> definition = resolve(input);
        if (definition.isEmpty()) {
            source.sendFailure(Component.literal("[Jobs] 不明な職業です: " + input));
            return 0;
        }
        JobProgressSavedData.AwardResult result = JobProgressSavedData.get(source.getServer())
                .awardAdmin(player.getUUID(), definition.get(), amount);
        source.sendSuccess(() -> Component.literal("[Jobs] " + player.getScoreboardName() + "に "
                + JobXpFormat.format(result.awardedXp()) + " XPを付与しました。"), true);
        return result.awardedXp() > 0 ? 1 : 0;
    }

    private static int addPoints(CommandSourceStack source, ServerPlayer player, int amount) {
        JobProgressSavedData.get(source.getServer()).addPoints(player.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("[Jobs] " + player.getScoreboardName() + "のポイントを "
                + amount + "変更しました。"), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        JobProgressSavedData.get(source.getServer()).reset(player.getUUID());
        source.sendSuccess(() -> Component.literal("[Jobs] " + player.getScoreboardName()
                + "の職業データをリセットしました。"), true);
        return 1;
    }

    private static Optional<JobDefinition> resolve(String input) {
        ResourceLocation parsed = ResourceLocation.tryParse(input);
        if (parsed != null) {
            Optional<JobDefinition> exact = JobDefinitions.INSTANCE.get(parsed);
            if (exact.isPresent()) {
                return exact;
            }
        }
        return JobDefinitions.INSTANCE.all().stream()
                .filter(definition -> definition.id().getPath().equals(input))
                .findFirst();
    }
}
