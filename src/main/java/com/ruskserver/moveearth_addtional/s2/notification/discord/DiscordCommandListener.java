package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.config.DiscordBotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.IMentionable;
import org.jetbrains.annotations.NotNull;

/** Slash-only command surface. The bot never consumes ordinary Discord message content. */
final class DiscordCommandListener extends ListenerAdapter {
    private final DiscordBotService service;

    DiscordCommandListener(DiscordBotService service) { this.service = service; }

    @Override public void onReady(@NotNull ReadyEvent event) {
        event.getJDA().updateCommands().addCommands(
                Commands.slash("moveearth", "MoveEarth server integration")
                        .addSubcommands(
                                new SubcommandData("setup", "Open the interactive setup guide"),
                                new SubcommandData("status", "Show integration and delivery status"),
                                new SubcommandData("test", "Send a test notification"),
                                new SubcommandData("audit", "Show recent integration audit entries"))
                        .addSubcommandGroups(
                                new SubcommandGroupData("account", "Minecraft account verification")
                                        .addSubcommands(
                                                new SubcommandData("link", "Verify your Minecraft account"),
                                                new SubcommandData("unlink", "Remove your account verification")),
                                new SubcommandGroupData("nation", "Nation integration")
                                        .addSubcommands(
                                                new SubcommandData("link", "Link this channel to a nation"),
                                                new SubcommandData("unlink", "Unlink this Discord server"),
                                                new SubcommandData("channel", "Change the notification channel")
                                                        .addOption(OptionType.CHANNEL, "target", "Notification channel", true),
                                                new SubcommandData("mention", "Set or clear the Siege mention role")
                                                        .addOption(OptionType.ROLE, "role", "Role; omit to clear", false),
                                                new SubcommandData("settings", "Update Discord notification settings")
                                                        .addOption(OptionType.BOOLEAN, "enabled", "Enable Discord delivery", false)
                                                        .addOption(OptionType.BOOLEAN, "coordinates", "Include coordinates", false)
                                                        .addOption(OptionType.BOOLEAN, "mention_siege", "Mention role on Siege", false)))
        ).queue(ignored -> service.onCommandsRegistered(), failure -> service.onCommandRegistrationFailed());
    }

    @Override public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!"moveearth".equals(event.getName())) return;
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.guildOnly()).setEphemeral(true).queue();
            return;
        }
        String group = event.getSubcommandGroup();
        String command = event.getSubcommandName();
        if ("setup".equals(command)) {
            service.replySetup(event);
            return;
        }
        if ("account".equals(group) && "link".equals(command)) {
            long lifetime = DiscordBotConfig.linkCodeExpirySeconds() * 1_000L;
            long now = System.currentTimeMillis();
            String code = service.createAccountLinkCode(event.getGuild().getIdLong(),
                    event.getChannel().getIdLong(), event.getUser().getIdLong(), now, lifetime);
            event.replyEmbeds(MoveEarthDiscordEmbeds.accountLinkCode(code, now + lifetime))
                    .setEphemeral(true).queue();
            return;
        }
        if ("account".equals(group) && "unlink".equals(command)) {
            service.replyAccountUnlink(event);
            return;
        }
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue();
            return;
        }
        if ("status".equals(command)) service.replyStatus(event);
        else if ("test".equals(command)) service.replyTest(event);
        else if ("audit".equals(command)) service.replyAudit(event);
        else if ("nation".equals(group) && "link".equals(command)) {
            GuildMessageChannel channel;
            try { channel = event.getChannel().asGuildMessageChannel(); }
            catch (IllegalStateException ignored) { channel = null; }
            if (channel == null || !channel.canTalk()) {
                event.replyEmbeds(MoveEarthDiscordEmbeds.channelUnavailable()).setEphemeral(true).queue();
                return;
            }
            long lifetime = DiscordBotConfig.linkCodeExpirySeconds() * 1_000L;
            long now = System.currentTimeMillis();
            String code = service.createLinkCode(event.getGuild().getIdLong(), channel.getIdLong(),
                    event.getUser().getIdLong(), now, lifetime);
            event.replyEmbeds(MoveEarthDiscordEmbeds.linkCode(code, now + lifetime)).setEphemeral(true).queue();
        } else if ("nation".equals(group) && "unlink".equals(command)) service.replyNationUnlink(event);
        else if ("nation".equals(group) && "channel".equals(command)) service.replyChannel(event);
        else if ("nation".equals(group) && "mention".equals(command)) {
            if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
                event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue();
            } else service.replyMention(event);
        }
        else if ("nation".equals(group) && "settings".equals(command)) service.replySettings(event);
    }

    static java.util.List<MessageTopLevelComponent> setupComponents(boolean manager) {
        return java.util.List.of(
                ActionRow.of(Button.primary("me:setup:account", "個人アカウント連携"),
                        Button.primary("me:setup:nation", "国家をリンク").withDisabled(!manager),
                        Button.secondary("me:setup:channel", "通知先を変更").withDisabled(!manager)),
                ActionRow.of(Button.secondary("me:setup:mention", "メンション設定").withDisabled(!manager),
                        Button.danger("me:setup:unlink", "連携を解除")));
    }

    @Override public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("me:setup:")) return;
        if (!event.isFromGuild() || event.getGuild() == null || event.getMember() == null) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.guildOnly()).setEphemeral(true).queue();
            return;
        }
        String action = event.getComponentId().substring("me:setup:".length());
        boolean manager = event.getMember().hasPermission(Permission.MANAGE_SERVER);
        long now = System.currentTimeMillis();
        long lifetime = DiscordBotConfig.linkCodeExpirySeconds() * 1_000L;
        switch (action) {
            case "account" -> {
                String code = service.createAccountLinkCode(event.getGuild().getIdLong(),
                        event.getChannel().getIdLong(), event.getUser().getIdLong(), now, lifetime);
                event.replyEmbeds(MoveEarthDiscordEmbeds.accountLinkCode(code, now + lifetime))
                        .setEphemeral(true).queue();
            }
            case "nation" -> {
                if (!manager) { event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue(); break; }
                GuildMessageChannel channel;
                try { channel = event.getChannel().asGuildMessageChannel(); }
                catch (IllegalStateException ignored) { channel = null; }
                if (channel == null || !channel.canTalk()) {
                    event.replyEmbeds(MoveEarthDiscordEmbeds.channelUnavailable()).setEphemeral(true).queue();
                    break;
                }
                String code = service.createLinkCode(event.getGuild().getIdLong(), channel.getIdLong(),
                        event.getUser().getIdLong(), now, lifetime);
                event.replyEmbeds(MoveEarthDiscordEmbeds.linkCode(code, now + lifetime)).setEphemeral(true).queue();
            }
            case "channel" -> {
                if (!manager) { event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue(); break; }
                EntitySelectMenu menu = EntitySelectMenu.create("me:setup:select-channel",
                                EntitySelectMenu.SelectTarget.CHANNEL).setPlaceholder("通知先チャンネルを選択").build();
                event.replyEmbeds(MoveEarthDiscordEmbeds.operation("通知先を選択",
                                "Botが閲覧・送信・埋め込み可能なチャンネルを選んでください。", true))
                        .addComponents(ActionRow.of(menu)).setEphemeral(true).queue();
            }
            case "mention" -> {
                if (!manager) { event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue(); break; }
                EntitySelectMenu menu = EntitySelectMenu.create("me:setup:select-role",
                                EntitySelectMenu.SelectTarget.ROLE).setPlaceholder("Siege通知の役職を選択").build();
                event.replyEmbeds(MoveEarthDiscordEmbeds.operation("メンション役職を選択",
                                "緊急な防衛通知だけ、この役職へメンションします。", true))
                        .addComponents(ActionRow.of(menu), ActionRow.of(
                                Button.secondary("me:setup:clear-role", "メンションなし")))
                        .setEphemeral(true).queue();
            }
            case "unlink" -> event.replyEmbeds(MoveEarthDiscordEmbeds.confirmation("Discord連携"))
                    .addComponents(ActionRow.of(Button.danger("me:setup:confirm-account", "個人連携を解除"),
                            Button.danger("me:setup:confirm-nation", "国家リンクを解除").withDisabled(!manager)))
                    .setEphemeral(true).queue();
            case "confirm-account" -> service.replyComponentUnlink(event, true);
            case "confirm-nation" -> {
                if (!manager) event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue();
                else service.replyComponentUnlink(event, false);
            }
            case "clear-role" -> {
                if (!manager) event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue();
                else service.replyComponentTarget(event, null, null);
            }
            default -> event.replyEmbeds(MoveEarthDiscordEmbeds.operation("操作できません",
                    "このセットアップ画面は古くなっています。/moveearth setup を再実行してください。", false))
                    .setEphemeral(true).queue();
        }
    }

    @Override public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("me:setup:select-")) return;
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.replyEmbeds(MoveEarthDiscordEmbeds.permissionDenied()).setEphemeral(true).queue();
            return;
        }
        IMentionable selected = event.getValues().isEmpty() ? null : event.getValues().getFirst();
        if ("me:setup:select-channel".equals(event.getComponentId())) {
            GuildMessageChannel channel = selected instanceof GuildMessageChannel value ? value : null;
            if (channel == null) event.replyEmbeds(MoveEarthDiscordEmbeds.channelUnavailable()).setEphemeral(true).queue();
            else service.replyComponentTarget(event, channel, null);
        } else if ("me:setup:select-role".equals(event.getComponentId())) {
            Role role = selected instanceof Role value ? value : null;
            if (role == null) event.replyEmbeds(MoveEarthDiscordEmbeds.operation(
                    "役職を選択できません", "もう一度セットアップを開いてください。", false)).setEphemeral(true).queue();
            else service.replyComponentTarget(event, null, role);
        }
    }

    @Override public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        service.onGuildRemoved(event.getGuild().getIdLong(), "bot_removed");
    }

    @Override public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        service.onChannelRemoved(event.getGuild().getIdLong(), event.getChannel().getIdLong());
    }

    @Override public void onRoleDelete(@NotNull RoleDeleteEvent event) {
        service.onRoleRemoved(event.getGuild().getIdLong(), event.getRole().getIdLong());
    }
}
