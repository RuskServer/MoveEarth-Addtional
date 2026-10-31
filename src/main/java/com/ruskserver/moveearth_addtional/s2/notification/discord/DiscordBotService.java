package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.DiscordBotConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkCodeRegistry;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkAccess;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationCategory;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPresentation;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/** Owns the embedded JDA lifecycle and bridges the persistent outbox without blocking server ticks. */
public final class DiscordBotService implements DiscordLinkAccess {
    private static final DiscordBotService INSTANCE = new DiscordBotService();

    private final DiscordLinkCodeRegistry linkCodes = new DiscordLinkCodeRegistry();
    private final DiscordLinkCodeRegistry accountLinkCodes = new DiscordLinkCodeRegistry();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private volatile MinecraftServer server;
    private volatile JDA jda;
    private final com.ruskserver.moveearth_addtional.s2.notification.DiscordFailureNoticePolicy
            failureNotices = new com.ruskserver.moveearth_addtional.s2.notification.DiscordFailureNoticePolicy();
    private int deliveryTicks;
    private volatile BotState lifecycleState = BotState.DISABLED;

    private DiscordBotService() { }

    public static DiscordBotService instance() {
        return INSTANCE;
    }

    public synchronized void start(MinecraftServer minecraftServer) {
        stop();
        if (!DiscordBotConfig.enabled()) {
            lifecycleState = BotState.DISABLED;
            Moveearth_addtional.LOGGER.info("[MoveEarth] Embedded Discord bot is disabled");
            return;
        }
        // Keep the outbox maintenance/notice path active even if authentication cannot start.
        server = minecraftServer;
        String token = DiscordBotConfig.botToken().trim();
        if (token.isEmpty()) {
            lifecycleState = BotState.AUTHENTICATION_FAILED;
            Moveearth_addtional.LOGGER.warn("[MoveEarth] Discord bot is enabled but botToken is empty");
            return;
        }
        lifecycleState = BotState.STARTING;
        try {
            jda = JDABuilder.createLight(token, Collections.<GatewayIntent>emptyList())
                    .setEnableShutdownHook(false)
                    .setActivity(Activity.watching("MoveEarth Season 2"))
                    .addEventListeners(new DiscordCommandListener(this))
                    .build();
            Moveearth_addtional.LOGGER.info("[MoveEarth] Embedded Discord bot startup requested");
        } catch (RuntimeException exception) {
            jda = null;
            lifecycleState = BotState.AUTHENTICATION_FAILED;
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
        failureNotices.clear();
        if (active != null) {
            active.shutdownNow();
            Moveearth_addtional.LOGGER.info("[MoveEarth] Embedded Discord bot stopped");
        }
        if (!DiscordBotConfig.enabled()) lifecycleState = BotState.DISABLED;
        else if (DiscordBotConfig.botToken().trim().isEmpty()) lifecycleState = BotState.AUTHENTICATION_FAILED;
        else lifecycleState = BotState.UNAVAILABLE;
    }

    public void tick(MinecraftServer minecraftServer) {
        if (minecraftServer != server) return;
        if (++deliveryTicks < DiscordBotConfig.deliveryIntervalTicks()) return;
        deliveryTicks = 0;
        try {
            // Retention applies while disconnected too; otherwise expired alerts pile up until reconnect.
            pruneOutbox(minecraftServer);
            if (isReady()) dispatchReady(minecraftServer);
            else {
                NationNotificationSavedData data = NationNotificationSavedData.get(minecraftServer);
                for (var delivery : data.ready(System.currentTimeMillis(), 256)) {
                    warnFailure(minecraftServer, delivery.nationId(), "unavailable", false);
                }
            }
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.error("[MoveEarth] Discord delivery tick failed ({})",
                    exception.getClass().getSimpleName());
        }
    }

    public boolean isReady() {
        JDA active = jda;
        return active != null && active.getStatus() == JDA.Status.CONNECTED;
    }

    @Override public BotState state() { return isReady() ? BotState.READY : lifecycleState; }

    @Override public String guildName(long guildId) {
        JDA active = jda;
        Guild guild = active == null ? null : active.getGuildById(guildId);
        return guild == null ? "" : guild.getName();
    }

    @Override public String channelName(long guildId, long channelId) {
        JDA active = jda;
        Guild guild = active == null ? null : active.getGuildById(guildId);
        GuildMessageChannel channel = guild == null ? null
                : guild.getChannelById(GuildMessageChannel.class, channelId);
        return channel == null ? "" : "#" + channel.getName();
    }

    @Override public String roleName(long guildId, long roleId) {
        JDA active = jda;
        Guild guild = active == null ? null : active.getGuildById(guildId);
        Role role = guild == null ? null : guild.getRoleById(roleId);
        return role == null ? "" : "@" + role.getName();
    }

    @Override public String inviteUrl() {
        JDA active = jda;
        if (active == null || active.getStatus() != JDA.Status.CONNECTED) return "";
        return com.ruskserver.moveearth_addtional.s2.notification.DiscordInviteLink.url(
                active.getSelfUser().getApplicationId());
    }

    @Override public synchronized boolean reconnect() {
        MinecraftServer activeServer = server;
        if (activeServer == null || !DiscordBotConfig.enabled()) return false;
        start(activeServer);
        return true;
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
        return canEmbed(guild, channel);
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

    void replySetup(SlashCommandInteractionEvent event) {
        MinecraftServer activeServer = server;
        if (activeServer == null || !isReady()) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue(hook -> activeServer.execute(() -> {
            NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
            long guildId = event.getGuild().getIdLong();
            long discordId = event.getUser().getIdLong();
            UUID nationId = data.nationForGuild(guildId).orElse(null);
            NationNotificationSavedData.Link link = nationId == null
                    ? NationNotificationSavedData.Link.unlinked() : data.link(nationId);
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.setup(
                    data.minecraftForDiscord(discordId).isPresent(), nationId != null,
                    channelName(guildId, link.channelId()), roleName(guildId, link.mentionRoleId())))
                    .setComponents(DiscordCommandListener.setupComponents(event.getMember()
                            .hasPermission(Permission.MANAGE_SERVER))).queue();
        }));
    }

    void replyComponentUnlink(GenericComponentInteractionCreateEvent event, boolean account) {
        MinecraftServer activeServer = server;
        if (activeServer == null) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue();
            return;
        }
        if (account) {
            long discordId = event.getUser().getIdLong();
            event.deferReply(true).queue(hook -> activeServer.execute(() -> {
                NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
                UUID minecraft = data.minecraftForDiscord(discordId).orElse(null);
                boolean success = minecraft != null && data.unlinkAccount(minecraft);
                data.audit("account_unlink", null, minecraft, discordId, success,
                        success ? "unlinked" : "not_linked");
                hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation("アカウント連携を解除",
                        success ? "Minecraftアカウントとの関連付けを解除しました。" : "関連付けはありません。",
                        success)).setComponents(List.of()).queue();
            }));
        } else runAuthorizedComponent(event, "nation_unlink", (data, nationId, minecraftId) -> {
            data.unlink(nationId);
            return "Discordサーバーと国家の連携を解除しました。";
        });
    }

    void replyComponentTarget(GenericComponentInteractionCreateEvent event,
                              GuildMessageChannel target, Role role) {
        runAuthorizedComponent(event, role == null ? "channel_update" : "mention_update",
                (data, nationId, minecraftId) -> {
                    NationNotificationSavedData.Link link = data.link(nationId);
                    if (target != null) {
                        if (!canEmbed(target.getGuild(), target)) throw new IllegalStateException(
                                "通知先へ送信できません。Bot権限を確認してください。");
                        data.updateDiscordTarget(nationId, target.getIdLong(), link.mentionRoleId());
                        return "通知先を #" + target.getName() + " に変更しました。";
                    }
                    data.updateDiscordTarget(nationId, link.channelId(), role == null ? 0L : role.getIdLong());
                    return role == null ? "Siegeメンションを解除しました。"
                            : "Siegeメンションを @" + role.getName() + " に変更しました。";
                });
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
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation("アカウント連携を解除",
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
        if (target == null || target.getGuild().getIdLong() != event.getGuild().getIdLong()
                || !canEmbed(event.getGuild(), target)) {
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
                String detail = minecraftId == null ? "先に /moveearth account link でアカウントを連携してください。"
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
            try {
                target.sendMessageEmbeds(MoveEarthDiscordEmbeds.operation("MoveEarth 通知テスト",
                        "埋め込み通知は正常に送信されました。", true))
                        .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class)).queue(
                                ignored -> finishTest(activeServer, hook, nationId, minecraftId, discordId, true),
                                failure -> finishTest(activeServer, hook, nationId, minecraftId, discordId, false));
            } catch (RuntimeException exception) {
                Moveearth_addtional.LOGGER.warn("[MoveEarth] Discord test delivery rejected ({})",
                        exception.getClass().getSimpleName());
                finishTest(activeServer, hook, nationId, minecraftId, discordId, false);
            }
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
                    ? "先に /moveearth account link でアカウントを連携してください。"
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

    private void runAuthorizedComponent(GenericComponentInteractionCreateEvent event, String action,
                                        AuthorizedOperation operation) {
        MinecraftServer activeServer = server;
        if (activeServer == null || event.getGuild() == null) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.unavailable()).setEphemeral(true).queue();
            return;
        }
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
            boolean success = false;
            String detail;
            if (!authorized) detail = minecraft == null
                    ? "先に個人アカウント連携を完了してください。"
                    : "国家が未連携か、ゲーム内の通知管理権限がありません。";
            else try { detail = operation.run(data, nationId, minecraft); success = true; }
            catch (RuntimeException exception) { detail = exception.getMessage() == null
                    ? "操作に失敗しました。" : exception.getMessage(); }
            data.audit(action, nationId, minecraft, discordId, success, detail);
            hook.editOriginalEmbeds(MoveEarthDiscordEmbeds.operation(
                    success ? "操作を完了しました" : "操作できません", detail, success))
                    .setComponents(List.of()).queue();
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
        lifecycleState = BotState.READY;
        Moveearth_addtional.LOGGER.info("[MoveEarth] Discord slash commands registered");
    }

    void onCommandRegistrationFailed() {
        lifecycleState = BotState.UNAVAILABLE;
        Moveearth_addtional.LOGGER.warn("[MoveEarth] Discord slash command registration failed");
    }

    private void pruneOutbox(MinecraftServer activeServer) {
        NationNotificationSavedData.get(activeServer).pruneExpired(System.currentTimeMillis(),
                DiscordBotConfig.outboxRetentionHours() * 3_600_000L,
                DiscordBotConfig.deduplicationWindowSeconds() * 1_000L, delivery -> {
                    inFlight.remove(delivery.id());
                    warnFailure(activeServer, delivery.nationId(), "expired", true);
                });
    }

    private void dispatchReady(MinecraftServer activeServer) {
        NationNotificationSavedData data = NationNotificationSavedData.get(activeServer);
        NationSavedData nations = NationSavedData.get(activeServer);
        long now = System.currentTimeMillis();
        for (NationNotificationSavedData.Delivery delivery
                : data.ready(now, DiscordBotConfig.deliveryBatchSize())) {
            if (!inFlight.add(delivery.id())) continue;
            try {
                dispatchOne(activeServer, data, nations, delivery);
            } catch (RuntimeException exception) {
                inFlight.remove(delivery.id());
                failDelivery(activeServer, data, delivery, "send_failed");
                Moveearth_addtional.LOGGER.warn("[MoveEarth] Discord delivery {} rejected ({})",
                        delivery.id(), exception.getClass().getSimpleName());
            }
        }
    }

    private void dispatchOne(MinecraftServer activeServer, NationNotificationSavedData data,
                             NationSavedData nations, NationNotificationSavedData.Delivery delivery) {
        NationNotificationSavedData.Link link = data.link(delivery.nationId());
        GuildMessageChannel channel = channel(link);
        if (channel == null) {
            inFlight.remove(delivery.id());
            failDelivery(activeServer, data, delivery, "channel_unavailable");
            return;
        }
        String nationName = nations.nation(delivery.nationId())
                .map(NationSavedData.Nation::name).orElse("Unknown nation");
        MessageCreateAction action = channel.sendMessageEmbeds(
                MoveEarthDiscordEmbeds.notification(nationName, delivery))
                .setAllowedMentions(EnumSet.noneOf(Message.MentionType.class));
        NationNotificationSavedData.Settings settings = data.settings(delivery.nationId());
        NotificationCategory category = NotificationCategory.of(delivery.type());
        NotificationPreference preference = data.preference(delivery.nationId(), category);
        if (settings.mentionOnSiege()
                && preference.mention() == NotificationPreference.MentionPolicy.URGENT_ONLY
                && NotificationPresentation.severity(delivery.type())
                == com.ruskserver.moveearth_addtional.s2.notification.NotificationSeverity.URGENT
                && link.mentionRoleId() > 0L) {
            Role role = channel.getGuild().getRoleById(link.mentionRoleId());
            if (role != null && data.allowMention(delivery.nationId(), category, System.currentTimeMillis())) {
                action.setAllowedMentions(EnumSet.of(Message.MentionType.ROLE)).mention(role);
            }
        }
        action.queue(ignored -> finish(activeServer, delivery, true),
                failure -> finish(activeServer, delivery, false));
    }

    private GuildMessageChannel channel(NationNotificationSavedData.Link link) {
        JDA active = jda;
        if (active == null || !link.linked()) return null;
        Guild guild = active.getGuildById(link.guildId());
        if (guild == null) return null;
        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, link.channelId());
        return canEmbed(guild, channel) ? channel : null;
    }

    private static boolean canEmbed(Guild guild, GuildMessageChannel channel) {
        return channel != null && channel.canTalk()
                && guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_EMBED_LINKS);
    }

    private void finish(MinecraftServer expectedServer, NationNotificationSavedData.Delivery delivery, boolean success) {
        com.ruskserver.moveearth_addtional.s2.notification.DiscordDeliveryCompletion.schedule(
                () -> expectedServer == server, expectedServer::execute, () -> inFlight.remove(delivery.id()), () -> {
                    NationNotificationSavedData data = NationNotificationSavedData.get(expectedServer);
                    if (success) data.acknowledge(delivery.id());
                    else failDelivery(expectedServer, data, delivery, "send_failed");
                });
    }

    private void failDelivery(MinecraftServer activeServer, NationNotificationSavedData data,
                              NationNotificationSavedData.Delivery delivery, String reason) {
        long now = System.currentTimeMillis();
        if (!data.fail(delivery.id(), now)) return;
        boolean lost = com.ruskserver.moveearth_addtional.s2.notification.NotificationDeliveryPolicy
                .shouldDrop(delivery.attempts() + 1);
        data.recordDeliveryFailure(delivery.nationId(), lost ? "retry_limit" : reason, now);
        warnFailure(activeServer, delivery.nationId(), reason, lost);
    }

    private void warnFailure(MinecraftServer activeServer, UUID nationId, String reason, boolean lost) {
        NationSavedData nations = NationSavedData.get(activeServer);
        var nation = nations.nation(nationId).orElse(null);
        if (nation == null) return;
        long now = System.currentTimeMillis();
        for (UUID member : nation.members().keySet()) {
            var player = activeServer.getPlayerList().getPlayer(member);
            if (player == null || !nations.can(member, S2Permission.MANAGE_NOTIFICATIONS)
                    || !failureNotices.allow(nationId, member, lost, now)) continue;
            boolean japanese = player.clientInformation().language().startsWith("ja");
            String kind = lost && !"expired".equals(reason) ? "lost" : reason;
            String fallback = switch (kind) {
                case "expired" -> japanese
                        ? "保留中のDiscord通知が保存期限を過ぎたため、配信を打ち切りました。国家のDiscord通知設定とBotの接続・権限を確認してください。"
                        : "A pending Discord notification expired and was dropped. Check your nation's Discord settings and the bot connection and permissions.";
                case "lost" -> japanese
                        ? "Discord通知を送信できず、再試行の上限に達したため配信を打ち切りました。国家のDiscord通知設定とBotの権限を確認してください。"
                        : "A Discord notification could not be delivered and was dropped after repeated retries. Check your nation's Discord settings and bot permissions.";
                case "channel_unavailable" -> japanese
                        ? "Discord通知先に送信できません。国家の通知先チャンネルとBotの送信・埋め込み権限を確認してください。自動で再試行します。"
                        : "Discord notifications cannot reach the target channel. Check the nation's channel and bot send/embed permissions. Delivery will retry automatically.";
                case "unavailable" -> japanese
                        ? "BotがDiscordに接続していないため、Discord通知が保留されています。接続復旧後に再試行します。"
                        : "Discord notifications are pending because the bot is disconnected. Delivery will retry after reconnection.";
                default -> japanese
                        ? "Discord通知の送信に失敗しました。自動で再試行します。問題が続く場合は国家のDiscord通知設定を確認し、運営に連絡してください。"
                        : "A Discord notification failed to send and will retry automatically. If this continues, check the nation's Discord settings and contact an administrator.";
            };
            player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.warning(
                    net.minecraft.network.chat.Component.translatableWithFallback(
                            "message.moveearth_addtional.discord.delivery_" + kind, fallback)));
        }
    }

    @FunctionalInterface
    private interface AuthorizedOperation {
        String run(NationNotificationSavedData data, UUID nationId, UUID minecraftId);
    }
}
