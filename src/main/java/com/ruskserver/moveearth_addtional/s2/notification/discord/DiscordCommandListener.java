package com.ruskserver.moveearth_addtional.s2.notification.discord;

import com.ruskserver.moveearth_addtional.config.DiscordBotConfig;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import org.jetbrains.annotations.NotNull;

/** Slash-only command surface. The bot never consumes ordinary Discord message content. */
final class DiscordCommandListener extends ListenerAdapter {
    private final DiscordBotService service;

    DiscordCommandListener(DiscordBotService service) { this.service = service; }

    @Override public void onReady(@NotNull ReadyEvent event) {
        event.getJDA().updateCommands().addCommands(
                Commands.slash("moveearth", "MoveEarth server integration")
                        .addSubcommands(
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
