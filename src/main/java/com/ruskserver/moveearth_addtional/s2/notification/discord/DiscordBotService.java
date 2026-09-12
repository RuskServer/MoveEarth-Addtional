package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.DiscordBotConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkCodeRegistry;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** Owns the embedded JDA lifecycle and bridges the persistent outbox without blocking server ticks. */
public final class DiscordBotService {
    private static final DiscordBotService INSTANCE = new DiscordBotService();

    private final DiscordLinkCodeRegistry linkCodes = new DiscordLinkCodeRegistry();
    private final DiscordLinkCodeRegistry accountLinkCodes = new DiscordLinkCodeRegistry();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile MinecraftServer server;
    private volatile JDA jda;
    private int deliveryTicks;

    private DiscordBotService() { }

    public static DiscordBotService instance() {
        return INSTANCE;
    }

    public synchronized void start(MinecraftServer minecraftServer) {
        stop();
        if (!DiscordBotConfig.enabled()) {
            Moveearth_addtional.LOGGER.info("[MoveEarth] Embedded Discord bot is disabled");
            return;
        }
        String token = DiscordBotConfig.botToken().trim();
        if (token.isEmpty()) {
            Moveearth_addtional.LOGGER.warn("[MoveEarth] Discord bot is enabled but botToken is empty");
            return;
        }
        server = minecraftServer;
        try {
            jda = JDABuilder.createLight(token, Collections.<GatewayIntent>emptyList())
                    .setEnableShutdownHook(false)
                    .setActivity(Activity.watching("MoveEarth Season 2"))
                    .addEventListeners(new DiscordCommandListener(this))
                    .build();
            Moveearth_addtional.LOGGER.info("[MoveEarth] Embedded Discord bot startup requested");
        } catch (RuntimeException exception) {
            server = null;
            jda = null;
            Moveearth_addtional.LOGGER.error("[MoveEarth] Discord bot startup failed ({})",
                    exception.getClass().getSimpleName());
        }
    }

    public synchronized void stop() {
        JDA active = jda;
        jda = null;
        server = null;
        deliveryTicks = 0;
        linkCodes.clear();
        accountLinkCodes.clear();
        inFlight.clear();
        if (active != null) {
            active.shutdownNow();
            Moveearth_addtional.LOGGER.info("[MoveEarth] Embedded Discord bot stopped");
        }
    }

    public void tick(MinecraftServer minecraftServer) {
        if (minecraftServer != server || !isReady()) return;
        if (++deliveryTicks < DiscordBotConfig.deliveryIntervalTicks()) return;
        deliveryTicks = 0;
        dispatchReady(minecraftServer);
    }

    public boolean isReady() {
        JDA active = jda;
        return active != null && active.getStatus() == JDA.Status.CONNECTED;
    }

    public String createLinkCode(long guildId, long channelId, long discordUserId,
                                 long nowMillis, long lifetimeMillis) {
        return linkCodes.create(guildId, channelId, discordUserId, nowMillis, lifetimeMillis);
    }

    public Optional<DiscordLinkCodeRegistry.PendingLink> consumeLinkCode(String code) {
        return linkCodes.consume(code, System.currentTimeMillis());
    }

    public String createAccountLinkCode(long guildId, long channelId, long discordUserId,
                                        long nowMillis, long lifetimeMillis) {
        return accountLinkCodes.create(guildId, channelId, discordUserId, nowMillis, lifetimeMillis);
    }

    public Optional<DiscordLinkCodeRegistry.PendingLink> consumeAccountLinkCode(String code) {
        return accountLinkCodes.consume(code, System.currentTimeMillis());
    }

    public boolean canDeliverTo(long guildId, long channelId) {
        JDA active = jda;
        if (active == null || active.getStatus() != JDA.Status.CONNECTED) return false;
        Guild guild = active.getGuildById(guildId);
        if (guild == null) return false;
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, channelId);
        return channel != null && channel.canTalk();
    }

    void replyStatus(SlashCommandInteractionEvent event) {
        MinecraftServer activeServer = server;
        if (activeServer == null || !isReady()) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            UUID nationId = data.nationForGuild(event.getGuild().getIdLong()).orElse(null);
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.status(
                    activeServer.getMotd(), activeServer.getPlayerCount(), nationId != null,
                    nationId == null ? 0 : data.pendingCount(nationId))).queue();
        }));
    }

    void replyAccountUnlink(SlashCommandInteractionEvent event) {
        MinecraftServer activeServer = server;
        if (activeServer == null) { event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue(); return; }
        long discordId = event.getUser().getIdLong();
        event.deferReply(true).queue(hook -> activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            UUID minecraft = data.minecraftForDiscord(discordId).orElse(null);
            boolean success = minecraft != null && data.unlinkAccount(minecraft);
            data.audit("account_unlink", null, minecraft, discordId, success,
                    success ? "unlinked" : "not_linked");
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation("本人確認を解除",
                    success ? "Minecraftアカウントとの関連付けを解除しました。" : "関連付けはありません。", success)).queue();
        }));
    }

    void replyNationUnlink(SlashCommandInteractionEvent event) {
        runAuthorized(event, "nation_unlink", (data, nationId, minecraftId) -> {
            data.unlink(nationId);
            return "Discordサーバーと国家の連携を解除しました。";
        });
    }

    void replyChannel(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("target");
        GuildMessageChannel target;
        try { target = option == null ? null : option.getAsChannel().asGuildMessageChannel(); }
        catch (IllegalStateException ignored) { target = null; }
        if (target == null || target.getGuild().getIdLong() != event.getGuild().getIdLong() || !target.canTalk()) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.channelUnavailable()).setEphemeral(true).queue();
            return;
        }
        long channelId = target.getIdLong();
        runAuthorized(event, "channel_update", (data, nationId, minecraftId) -> {
            NationNotificationSavedData.Link link = data.link(nationId);
            data.updateDiscordTarget(nationId, channelId, link.mentionRoleId());
            return "通知先を <#" + channelId + "> に変更しました。";
        });
    }

    void replyMention(SlashCommandInteractionEvent event) {
        long roleId = event.getOption("role") == null ? 0L : event.getOption("role").getAsRole().getIdLong();
        runAuthorized(event, "mention_update", (data, nationId, minecraftId) -> {
            NationNotificationSavedData.Link link = data.link(nationId);
            data.updateDiscordTarget(nationId, link.channelId(), roleId);
            return roleId == 0L ? "Siegeメンションを解除しました。" : "Siegeメンション役職を更新しました。";
        });
    }

    void replySettings(SlashCommandInteractionEvent event) {
        Boolean enabled = optionBoolean(event, "enabled");
        Boolean coordinates = optionBoolean(event, "coordinates");
        Boolean mention = optionBoolean(event, "mention_siege");
        if (enabled == null && coordinates == null && mention == null) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.operation("設定を更新できません",
                    "少なくとも1項目を指定してください。", false)).setEphemeral(true).queue();
            return;
        }
        runAuthorized(event, "settings_update", (data, nationId, minecraftId) -> {
            NationNotificationSavedData.Settings old = data.settings(nationId);
            data.updateSettings(nationId, new NationNotificationSavedData.Settings(old.inGame(),
                    enabled == null ? old.discord() : enabled,
                    coordinates == null ? old.includeCoordinates() : coordinates,
                    mention == null ? old.mentionOnSiege() : mention));
            return "国家通知設定を更新しました。";
        });
    }

    void replyAudit(SlashCommandInteractionEvent event) {
        MinecraftServer activeServer = server;
        if (activeServer == null) { event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue(); return; }
        long guildId = event.getGuild().getIdLong();
        long discordId = event.getUser().getIdLong();
        event.deferReply(true).queue(hook -> activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            UUID nationId = data.nationForGuild(guildId).orElse(null);
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.audit(
                    data.recentAudit(nationId, discordId, 10))).queue();
        }));
    }

    void replyTest(SlashCommandInteractionEvent event) {
        MinecraftServer activeServer = server;
        if (activeServer == null) { event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue(); return; }
        long guildId = event.getGuild().getIdLong();
        long discordId = event.getUser().getIdLong();
        event.deferReply(true).queue(hook -> activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            UUID minecraftId = data.minecraftForDiscord(discordId).orElse(null);
            UUID nationId = data.nationForGuild(guildId).orElse(null);
            NationSavedData nations = NationSavedData.get(activeServer);
            boolean authorized = minecraftId != null && nationId != null
                    && nations.nationIdFor(minecraftId).filter(nationId::equals).isPresent()
                    && nations.can(minecraftId, S2Permission.MANAGE_NOTIFICATIONS);
            if (!authorized) {
                String detail = minecraftId == null ? "先に /moveearth account link で本人確認してください。"
                        : "国家が未連携か、ゲーム内の通知管理権限がありません。";
                data.audit("test_delivery", nationId, minecraftId, discordId, false, detail);
                hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation("操作できません", detail, false)).queue();
                return;
            }
            GuildMessageChannel target = channel(data.link(nationId));
            if (target == null) {
                String detail = "通知先チャンネルへ送信できません。";
                data.audit("test_delivery", nationId, minecraftId, discordId, false, detail);
                hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation("送信できません", detail, false)).queue();
                return;
            }
            target.sendMessageEmbeds(MoveEarthDiscordEmbeds.operation("MoveEarth 通知テスト",
                    "埋め込み通知は正常に送信されました。", true))
                    .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue(
                            ignored -> finishTest(activeServer, hook, nationId, minecraftId, discordId, true),
                            failure -> finishTest(activeServer, hook, nationId, minecraftId, discordId, false));
        }));
    }

    private void finishTest(MinecraftServer expectedServer,
                            net.dv8tion.jda.api.interactions.InteractionHook hook,
                            UUID nationId, UUID minecraftId, long discordId, boolean success) {
        if (expectedServer != server) return;
        expectedServer.execute(() -> {
            if (expectedServer != server) return;
            String detail = success ? "テスト通知を送信しました。" : "Discord APIへの送信に失敗しました。";
            NationNotificationSavedData.get(expectedServer).audit(
                    "test_delivery", nationId, minecraftId, discordId, success, detail);
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation(
                    success ? "送信しました" : "送信できません", detail, success)).queue();
        });
    }

    private void runAuthorized(SlashCommandInteractionEvent event, String action, AuthorizedOperation operation) {
        MinecraftServer activeServer = server;
        if (activeServer == null) { event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue(); return; }
        long guildId = event.getGuild().getIdLong();
        long discordId = event.getUser().getIdLong();
        event.deferReply(true).queue(hook -> activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            UUID minecraft = data.minecraftForDiscord(discordId).orElse(null);
            UUID nationId = data.nationForGuild(guildId).orElse(null);
            NationSavedData nations = NationSavedData.get(activeServer);
            boolean authorized = minecraft != null && nationId != null
                    && nations.nationIdFor(minecraft).filter(nationId::equals).isPresent()
                    && nations.can(minecraft, S2Permission.MANAGE_NOTIFICATIONS);
            String detail;
            boolean success = false;
            if (!authorized) detail = minecraft == null
                    ? "先に /moveearth account link で本人確認してください。"
                    : "国家が未連携か、ゲーム内の通知管理権限がありません。";
            else try {
                detail = operation.run(data, nationId, minecraft);
                success = true;
            } catch (RuntimeException exception) {
                detail = exception.getMessage() == null ? "操作に失敗しました。" : exception.getMessage();
            }
            data.audit(action, nationId, minecraft, discordId, success, detail);
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation(
                    success ? "操作を完了しました" : "操作できません", detail, success)).queue();
        }));
    }

    private static Boolean optionBoolean(SlashCommandInteractionEvent event, String name) {
        OptionMapping option = event.getOption(name);
        return option == null ? null : option.getAsBoolean();
    }

    void onGuildRemoved(long guildId, String reason) {
        mutateLink(guildId, data -> data.unlinkGuild(guildId), "link_invalidated", reason);
    }

    void onChannelRemoved(long guildId, long channelId) {
        mutateLink(guildId, data -> data.nationForGuild(guildId).filter(id -> data.link(id).channelId() == channelId)
                .flatMap(id -> data.unlinkGuild(guildId)), "link_invalidated", "channel_deleted");
    }

    void onRoleRemoved(long guildId, long roleId) {
        mutateLink(guildId, data -> data.clearMentionRole(guildId, roleId),
                "mention_invalidated", "role_deleted");
    }

    private void mutateLink(long guildId,
                            java.util.function.Function<NationNotificationSavedData, Optional<UUID>> operation,
                            String action, String reason) {
        MinecraftServer activeServer = server;
        if (activeServer == null) return;
        activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            operation.apply(data).ifPresent(nation -> data.audit(action, nation, null, 0L, true, reason));
        });
    }

    void onCommandsRegistered() {
        Moveearth_addtional.LOGGER.info("[MoveEarth] Discord slash commands registered");
    }

    void onCommandRegistrationFailed() {
        Moveearth_addtional.LOGGER.warn("[MoveEarth] Discord slash command registration failed");
    }

    private void dispatchReady(MinecraftServer activeServer) {
        NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
        NationSavedData nations = NationSavedData.get(activeServer);
        long now = System.currentTimeMillis();
        data.pruneExpired(now, DiscordBotConfig.outboxRetentionHours() * 3_600_000L,
                DiscordBotConfig.deduplicationWindowSeconds() * 1_000L);
        for (NationNotificationSavedData.Delivery delivery
                : data.ready(now, DiscordBotConfig.deliveryBatchSize())) {
            if (!inFlight.add(delivery.id())) continue;
            NationNotificationSavedData.Link link = data.link(delivery.nationId());
            GuildMessageChannel channel = channel(link);
            if (channel == null) {
                inFlight.remove(delivery.id());
                data.fail(delivery.id(), now);
                continue;
            }
            String nationName = nations.nation(delivery.nationId())
                    .map(NationSavedData.Nation::name).orElse("Unknown nation");
            MessageCreateAction action = channel.sendMessageEmbeds(
                    MoveEarthDiscordEmbeds.notification(nationName, delivery))
                    .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));
            NationNotificationSavedData.Settings settings = data.settings(delivery.nationId());
            if (settings.mentionOnSiege() && delivery.type() == NationNotificationSavedData.EventType.SIEGE_STARTED
                    && link.mentionRoleId() > 0L) {
                Role role = channel.getGuild().getRoleById(link.mentionRoleId());
                if (role != null) action.setAllowedMentions(EnumSet.of(Message.MentionType.ROLE)).mention(role);
            }
            action.queue(ignored -> finish(activeServer, delivery.id(), true),
                    failure -> finish(activeServer, delivery.id(), false));
        }
    }

    private GuildMessageChannel channel(NationNotificationSavedData.Link link) {
        JDA active = jda;
        if (active == null || !link.linked()) return null;
        Guild guild = active.getGuildById(link.guildId());
        if (guild == null) return null;
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, link.channelId());
        return channel != null && channel.canTalk() ? channel : null;
    }

    private void finish(MinecraftServer expectedServer, UUID deliveryId, boolean success) {
        inFlight.remove(deliveryId);
        if (expectedServer != server) return;
        expectedServer.execute(() -> {
            if (expectedServer != server) return;
            NationNotificationSavedData data = NationNotificationSavedData.get(expectedServer);
            if (success) data.acknowledge(deliveryId);
            else {
                data.fail(deliveryId, System.currentTimeMillis());
            }
        });
    }

    @FunctionalInterface
    private interface AuthorizedOperation {
        String run(NationNotificationSavedData data, UUID nationId, UUID minecraftId);
    }
}
