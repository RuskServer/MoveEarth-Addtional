package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthTextField;
import com.ruskserver.moveearth_addtional.client.ui.SuppressesChatOverlay;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_LinkDiscordAccountPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_LinkNationDiscordPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_NotificationActionPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_UpdateNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_OpenNationNotificationsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_S2ActionResultPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordInviteLink;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkAccess;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationCategory;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference.DiscordMode;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference.InGameMode;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreference.MentionPolicy;
import com.ruskserver.moveearth_addtional.s2.notification.NotificationPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * Notification center. Unlinked nations open on a guided setup (invite → /moveearth setup → code → test);
 * every clickable element is produced by one layout method that both drawing and click handling use.
 * Discord IDs and credentials never enter this screen.
 */
public final class NationNotificationsScreen extends Screen implements SuppressesChatOverlay {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final String KEY = "screen.moveearth_addtional.notifications.";
    private static final int[] DIGEST_OPTIONS = {15, 30};
    private static final int[] COOLDOWN_OPTIONS = {15, 30, 60};
    private static int nextRequestId;
    private static Page requestedPage;

    private S2C_OpenNationNotificationsPacket snapshot;
    private final EnumMap<NotificationCategory, NotificationPreference> preferences =
            new EnumMap<>(NotificationCategory.class);
    private final EnumMap<NotificationCategory, NotificationPreference> savedPreferences =
            new EnumMap<>(NotificationCategory.class);
    private Page page;
    private boolean coordinates;
    private boolean savedCoordinates;
    private int digestMinutes;
    private int mentionCooldownMinutes;
    private long revision;
    private int pendingRequestId = -1;
    private int pendingTicks;
    private Confirm confirm = Confirm.NONE;
    private boolean discardConfirm;
    private MoveEarthTextField accountCode;
    private MoveEarthTextField nationCode;
    private Component toast;
    private int toastTicks;
    private int toastColor = SUCCESS;
    private int contentScroll;
    private boolean draggingScrollbar;

    /** Opens the notification center on {@code page}, or on the most useful page when {@code null}. */
    static void request(Page page) {
        requestedPage = page;
        PacketDistributor.sendToServer(new C2S_RequestNationNotificationsPacket());
    }

    public NationNotificationsScreen(S2C_OpenNationNotificationsPacket packet) {
        super(Component.translatable(KEY + "title"));
        applySnapshot(packet, true);
        page = requestedPage != null ? requestedPage : needsSetup() ? Page.LINK : Page.STATUS;
        requestedPage = null;
    }

    public void update(S2C_OpenNationNotificationsPacket packet) {
        applySnapshot(packet, pendingRequestId < 0 || !dirty());
    }

    private void applySnapshot(S2C_OpenNationNotificationsPacket packet, boolean replaceDraft) {
        snapshot = packet;
        revision = packet.revision();
        if (replaceDraft) {
            preferences.clear();
            for (var setting : packet.categories()) preferences.put(setting.category(),
                    new NotificationPreference(setting.inGame(), setting.discord(), setting.mention()));
            for (NotificationCategory category : NotificationCategory.values()) preferences.putIfAbsent(category,
                    new NotificationPreference(InGameMode.IMMEDIATE, DiscordMode.OFF, MentionPolicy.NONE));
            savedPreferences.clear();
            savedPreferences.putAll(preferences);
            coordinates = savedCoordinates = packet.includeCoordinates();
            digestMinutes = packet.digestMinutes();
            mentionCooldownMinutes = packet.mentionCooldownMinutes();
        }
        updateEditable();
    }

    private boolean needsSetup() {
        return snapshot.botState() != DiscordLinkAccess.BotState.DISABLED && !snapshot.nationLinked();
    }

    @Override protected void init() {
        String previousAccountCode = accountCode == null ? "" : accountCode.getValue();
        String previousNationCode = nationCode == null ? "" : nationCode.getValue();
        accountCode = codeField(KEY + "account_code");
        nationCode = codeField(KEY + "nation_code");
        accountCode.setValue(previousAccountCode);
        nationCode.setValue(previousNationCode);
        addRenderableWidget(accountCode);
        addRenderableWidget(nationCode);
        updateFieldVisibility();
    }

    private MoveEarthTextField codeField(String key) {
        MoveEarthTextField field = new MoveEarthTextField(font, 0, 0, 100, 20, Component.translatable(key));
        field.setMaxLength(16);
        field.setFilter(value -> value.matches("[A-Za-z0-9-]*"));
        field.setVisible(false);
        return field;
    }

    // ------------------------------------------------------------------ rendering

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackground(graphics, width, height);
        Rect panel = panelBounds();
        drawPanel(graphics, panel);
        graphics.drawString(font, title, panel.x() + 18, panel.y() + 13, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "detail").getString(),
                panel.width() - 60), panel.x() + 18, panel.y() + 28, MUTED, false);
        drawClose(graphics, font, closeBounds(panel), closeBounds(panel).contains(mouseX, mouseY));
        Rect tabs = tabBounds(panel);
        for (Page candidate : Page.values()) {
            Rect bounds = slot(tabs, candidate.ordinal(), Page.values().length, 4);
            drawTab(graphics, font, bounds, Component.translatable(candidate.key), candidate == page,
                    bounds.contains(mouseX, mouseY));
        }
        Rect viewport = contentBounds(panel);
        Rect content = pageContent(viewport);
        contentScroll = Math.max(0, Math.min(contentScroll, content.height() - viewport.height()));
        int scrolledMouseY = mouseY + contentScroll;
        boolean mouseInViewport = viewport.contains(mouseX, mouseY) && !modalOpen();
        int hoverX = mouseInViewport ? mouseX : Integer.MIN_VALUE;
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        graphics.pose().pushPose();
        graphics.pose().translate(0, -contentScroll, 0);
        switch (page) {
            case STATUS -> drawStatus(graphics, content, hoverX, scrolledMouseY);
            case PREFERENCES -> drawPreferences(graphics, content, hoverX, scrolledMouseY);
            case LINK -> drawLink(graphics, content, hoverX, scrolledMouseY);
            case HISTORY -> drawHistory(graphics, content);
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(viewport.right() - 3, viewport.y(), 3, viewport.height()),
                viewport.height(), content.height(), contentScroll);
        Rect back = backBounds(panel), save = saveBounds(panel);
        drawButton(graphics, font, back, Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, back.contains(mouseX, mouseY) && !modalOpen(), true);
        if (page == Page.PREFERENCES && snapshot.canManage()) {
            boolean enabled = dirty() && pendingRequestId < 0;
            if (dirty()) {
                Component unsaved = Component.translatable(KEY + "unsaved");
                graphics.drawString(font, unsaved, save.x() - font.width(unsaved) - 10, save.y() + 7, GOLD, false);
            }
            drawButton(graphics, font, save, Component.translatable(KEY + "save"),
                    SUCCESS, enabled && save.contains(mouseX, mouseY) && !modalOpen(), enabled);
        }
        updateFieldVisibility();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (confirm != Confirm.NONE) drawModal(graphics, mouseX, mouseY, KEY + "unlink_confirm", DANGER, KEY + "unlink");
        if (discardConfirm) drawModal(graphics, mouseX, mouseY, KEY + "discard_confirm", GOLD, KEY + "discard");
        if (toastTicks > 0 && toast != null) drawToast(graphics, font, width, height, toast, toastColor);
    }

    // ------------------------------------------------------------------ status page

    private enum StatusAction { SETUP, HISTORY, TEST }

    private record StatusLayout(Rect card, List<StatusAction> actions, List<Rect> buttons, int reasonY, int height) { }

    private StatusLayout statusLayout(Rect content) {
        Rect card = new Rect(content.x(), content.y(), content.width(), 86);
        List<StatusAction> actions = new ArrayList<>();
        if (!snapshot.nationLinked()) actions.add(StatusAction.SETUP);
        else {
            if (hasProblem()) actions.add(StatusAction.HISTORY);
            if (snapshot.canManage()) actions.add(StatusAction.TEST);
        }
        List<Rect> buttons = new ArrayList<>();
        int x = content.x();
        for (int index = 0; index < actions.size(); index++) {
            int buttonWidth = Math.min(180, (content.width() - 8 * (actions.size() - 1)) / actions.size());
            buttons.add(new Rect(x, card.bottom() + 10, buttonWidth, 24));
            x += buttonWidth + 8;
        }
        int reasonY = card.bottom() + (actions.isEmpty() ? 10 : 44);
        int reasonHeight = hasProblem() ? wrappedHeight(font, failureText(), content.width()) : 0;
        return new StatusLayout(card, actions, buttons, reasonY, reasonY + reasonHeight - content.y());
    }

    private void drawStatus(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        StatusLayout layout = statusLayout(content);
        Rect card = layout.card();
        int statusColor = overallColor();
        drawCard(graphics, card, statusColor, true, false);
        int textWidth = card.width() - 28;
        graphics.drawString(font, Component.translatable(overallKey()), card.x() + 14, card.y() + 11, statusColor, false);
        graphics.drawString(font, Component.translatable(KEY + "bot." + snapshot.botState().name().toLowerCase(Locale.ROOT)),
                card.x() + 14, card.y() + 26, TEXT, false);
        String target = snapshot.nationLinked()
                ? display(snapshot.guildName(), "Discord") + " • " + display(snapshot.channelName(), "#?")
                : Component.translatable(KEY + "unlinked").getString();
        graphics.drawString(font, font.plainSubstrByWidth(target, textWidth), card.x() + 14, card.y() + 40,
                snapshot.nationLinked() ? TEXT : MUTED, false);
        if (snapshot.nationLinked()) graphics.drawString(font, font.plainSubstrByWidth(mentionTarget().getString(),
                textWidth), card.x() + 14, card.y() + 54, MUTED, false);
        graphics.drawString(font, Component.translatable(KEY + "health",
                snapshot.pendingCount(), snapshot.retryingCount()), card.x() + 14, card.y() + 68, MUTED, false);
        for (int index = 0; index < layout.actions().size(); index++) {
            StatusAction action = layout.actions().get(index);
            Rect button = layout.buttons().get(index);
            boolean enabled = pendingRequestId < 0;
            drawButton(graphics, font, button, Component.translatable(KEY + switch (action) {
                        case SETUP -> "start_setup";
                        case HISTORY -> "open_history";
                        case TEST -> "test";
                    }), action == StatusAction.HISTORY ? DANGER : action == StatusAction.SETUP ? GOLD : SUCCESS,
                    enabled && button.contains(mouseX, mouseY), enabled);
        }
        if (hasProblem()) drawWrapped(graphics, font, failureText(), content.x(), layout.reasonY(),
                content.width(), DANGER);
    }

    private Component failureText() {
        return Component.translatable(KEY + "last_failure", formatTime(snapshot.lastFailureAtMillis()),
                snapshot.lastFailureReason().isBlank()
                        ? Component.translatable(KEY + "reason.retry")
                        : Component.translatable(snapshot.lastFailureReason()));
    }

    private Component mentionTarget() {
        return snapshot.roleName().isBlank()
                ? Component.translatable(KEY + "mention_role_unset")
                : Component.translatable(KEY + "mention_role", snapshot.roleName());
    }

    // ------------------------------------------------------------------ preferences page

    private enum Group {
        IN_GAME(2), DISCORD(3), MENTION(2);
        final int options;
        Group(int options) { this.options = options; }
    }

    private enum Option { COORDINATES, DIGEST, COOLDOWN }

    private record PrefLayout(List<Rect> presets, int noticeY, int headerY, List<Rect> rows,
                              List<Rect> optionRows, int roleY, int readOnlyY, int height) { }

    private PrefLayout prefLayout(Rect content) {
        boolean compact = compact(content);
        List<Rect> presets = new ArrayList<>();
        int presetCount = NotificationPreset.values().length;
        for (int index = 0; index < presetCount; index++) {
            if (compact) {
                Rect row = new Rect(content.x(), content.y() + (index / 2) * 27, content.width(), 22);
                presets.add(slot(row, index % 2, 2, 6));
            } else presets.add(slot(new Rect(content.x(), content.y(), content.width(), 22), index, presetCount, 6));
        }
        int y = presets.getLast().bottom() + 8;
        int noticeY = -1;
        if (!snapshot.nationLinked()) {
            noticeY = y;
            y += 14;
        }
        int headerY = -1;
        if (!compact) {
            headerY = y;
            y += 12;
        }
        List<Rect> rows = new ArrayList<>();
        for (int index = 0; index < NotificationCategory.values().length; index++) {
            Rect row = new Rect(content.x(), y, content.width(), compact ? 46 : 26);
            rows.add(row);
            y = row.bottom() + 4;
        }
        y += 8;
        List<Rect> optionRows = new ArrayList<>();
        for (Option option : Option.values()) {
            int rowHeight = compact ? 46 : 26;
            int extra = option == Option.COORDINATES
                    ? wrappedHeight(font, Component.translatable(KEY + "coordinates_warning"), content.width() - 12) + 4 : 0;
            Rect row = new Rect(content.x(), y, content.width(), rowHeight + extra);
            optionRows.add(row);
            y = row.bottom() + 4;
        }
        int roleY = y + 4;
        y = roleY + LINE_HEIGHT + 4;
        int readOnlyY = -1;
        if (!snapshot.canManage()) {
            readOnlyY = y;
            y += 12;
        }
        return new PrefLayout(presets, noticeY, headerY, rows, optionRows, roleY, readOnlyY, y - content.y());
    }

    /** Area of {@code group}'s choices inside a category row; groups share width by their option counts. */
    private Rect groupBounds(Rect row, Group group, boolean compact) {
        int labelWidth = compact ? 0 : Math.min(130, row.width() / 4);
        int left = row.x() + 8 + labelWidth;
        int width = row.right() - 8 - left;
        int unit = Math.max(1, (width - 2 * 10) / 7);
        int offset = switch (group) {
            case IN_GAME -> 0;
            case DISCORD -> 2 * unit + 10;
            case MENTION -> 5 * unit + 20;
        };
        return new Rect(left + offset, compact ? row.y() + 21 : row.y() + 3, group.options * unit, 20);
    }

    private Rect choiceBounds(Rect row, Group group, int option, boolean compact) {
        return slot(groupBounds(row, group, compact), option, group.options, 2);
    }

    private Rect optionChoiceBounds(Rect row, Option option, int index, boolean compact) {
        int count = optionCount(option);
        int width = Math.min(66 * count, compact ? row.width() - 16 : row.width() / 2 - 8);
        Rect group = new Rect(row.right() - 8 - width, compact ? row.y() + 21 : row.y() + 3, width, 20);
        return slot(group, index, count, 2);
    }

    private static int optionCount(Option option) {
        return switch (option) {
            case COORDINATES -> 2;
            case DIGEST -> DIGEST_OPTIONS.length;
            case COOLDOWN -> COOLDOWN_OPTIONS.length;
        };
    }

    private void drawPreferences(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        PrefLayout layout = prefLayout(content);
        boolean compact = compact(content);
        boolean editable = snapshot.canManage() && pendingRequestId < 0;
        NotificationPreset detected = detectedPreset();
        for (int index = 0; index < layout.presets().size(); index++) {
            NotificationPreset preset = NotificationPreset.values()[index];
            Rect bounds = layout.presets().get(index);
            Component label = Component.translatable(KEY + "preset." + preset.name().toLowerCase(Locale.ROOT));
            boolean clickable = editable && preset != NotificationPreset.CUSTOM;
            drawChoice(graphics, font, bounds, label, SUCCESS, preset == detected,
                    clickable && bounds.contains(mouseX, mouseY), clickable || preset == detected);
        }
        if (layout.noticeY() >= 0) graphics.drawString(font, font.plainSubstrByWidth(
                Component.translatable(KEY + "discord_unlinked_notice").getString(), content.width()),
                content.x(), layout.noticeY(), GOLD, false);
        if (layout.headerY() >= 0) {
            Rect sample = layout.rows().getFirst();
            for (Group group : Group.values()) {
                Rect area = groupBounds(sample, group, false);
                graphics.drawCenteredString(font, Component.translatable(KEY + "column." + group.name().toLowerCase(Locale.ROOT)),
                        area.x() + area.width() / 2, layout.headerY(), MUTED);
            }
        }
        int index = 0;
        for (NotificationCategory category : NotificationCategory.values()) {
            Rect row = layout.rows().get(index++);
            drawCard(graphics, row, category == NotificationCategory.DEFENSE ? DANGER : ACCENT, false, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(categoryKey(category)).getString(),
                            compact ? row.width() - 16 : Math.min(130, row.width() / 4) - 4),
                    row.x() + 8, row.y() + (compact ? 6 : 9), TEXT, false);
            NotificationPreference preference = preferences.get(category);
            for (Group group : Group.values()) for (int option = 0; option < group.options; option++) {
                Rect choice = choiceBounds(row, group, option, compact);
                boolean enabled = editable && choiceEnabled(preference, group);
                drawChoice(graphics, font, choice, Component.translatable(choiceKey(group, option)),
                        group == Group.MENTION ? GOLD : ACCENT, selected(preference, group, option),
                        enabled && choice.contains(mouseX, mouseY), enabled);
            }
        }
        for (Option option : Option.values()) {
            Rect row = layout.optionRows().get(option.ordinal());
            drawCard(graphics, row, option == Option.COORDINATES && coordinates ? DANGER : BORDER, false, false);
            graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(KEY + "option."
                            + option.name().toLowerCase(Locale.ROOT)).getString(),
                            compact ? row.width() - 16 : row.width() / 2 - 16),
                    row.x() + 8, row.y() + (compact ? 6 : 9), TEXT, false);
            for (int choice = 0; choice < optionCount(option); choice++) {
                Rect bounds = optionChoiceBounds(row, option, choice, compact);
                drawChoice(graphics, font, bounds, optionLabel(option, choice),
                        option == Option.COORDINATES && choice == 1 ? DANGER : ACCENT,
                        optionSelected(option, choice), editable && bounds.contains(mouseX, mouseY), editable);
            }
            if (option == Option.COORDINATES) drawWrapped(graphics, font,
                    Component.translatable(KEY + "coordinates_warning"), row.x() + 8,
                    row.y() + (compact ? 46 : 26), row.width() - 12, coordinates ? DANGER : MUTED);
        }
        graphics.drawString(font, font.plainSubstrByWidth(mentionTarget().getString(), content.width()),
                content.x(), layout.roleY(), MUTED, false);
        if (layout.readOnlyY() >= 0) graphics.drawString(font, Component.translatable(KEY + "read_only"),
                content.x(), layout.readOnlyY(), DANGER, false);
    }

    private boolean choiceEnabled(NotificationPreference preference, Group group) {
        return switch (group) {
            case IN_GAME -> true;
            case DISCORD -> snapshot.nationLinked();
            case MENTION -> snapshot.nationLinked() && preference.discord() == DiscordMode.IMMEDIATE;
        };
    }

    private static boolean selected(NotificationPreference preference, Group group, int option) {
        return switch (group) {
            case IN_GAME -> preference.inGame() == (option == 0 ? InGameMode.IMMEDIATE : InGameMode.OFF);
            case DISCORD -> preference.discord() == DiscordMode.values()[option];
            case MENTION -> preference.mention() == (option == 0 ? MentionPolicy.URGENT_ONLY : MentionPolicy.NONE);
        };
    }

    private static String choiceKey(Group group, int option) {
        return KEY + "choice." + switch (group) {
            case IN_GAME -> option == 0 ? "on" : "off";
            case DISCORD -> switch (option) { case 0 -> "immediate"; case 1 -> "digest"; default -> "off"; };
            case MENTION -> option == 0 ? "on" : "off";
        };
    }

    private void choose(NotificationCategory category, Group group, int option) {
        NotificationPreference old = preferences.get(category);
        NotificationPreference next = switch (group) {
            case IN_GAME -> new NotificationPreference(option == 0 ? InGameMode.IMMEDIATE : InGameMode.OFF,
                    old.discord(), old.mention());
            case DISCORD -> {
                DiscordMode discord = DiscordMode.values()[option];
                // Mentions only accompany immediate delivery, so leaving it clears the mention.
                yield new NotificationPreference(old.inGame(), discord,
                        discord == DiscordMode.IMMEDIATE ? old.mention() : MentionPolicy.NONE);
            }
            case MENTION -> new NotificationPreference(old.inGame(), old.discord(),
                    option == 0 ? MentionPolicy.URGENT_ONLY : MentionPolicy.NONE);
        };
        preferences.put(category, next);
    }

    private Component optionLabel(Option option, int index) {
        return switch (option) {
            case COORDINATES -> Component.translatable(KEY + "choice." + (index == 0 ? "off" : "on"));
            case DIGEST -> Component.translatable(KEY + "minutes", DIGEST_OPTIONS[index]);
            case COOLDOWN -> Component.translatable(KEY + "minutes", COOLDOWN_OPTIONS[index]);
        };
    }

    private boolean optionSelected(Option option, int index) {
        return switch (option) {
            case COORDINATES -> coordinates == (index == 1);
            case DIGEST -> digestMinutes == DIGEST_OPTIONS[index];
            case COOLDOWN -> mentionCooldownMinutes == COOLDOWN_OPTIONS[index];
        };
    }

    private void chooseOption(Option option, int index) {
        switch (option) {
            case COORDINATES -> coordinates = index == 1;
            case DIGEST -> digestMinutes = DIGEST_OPTIONS[index];
            case COOLDOWN -> mentionCooldownMinutes = COOLDOWN_OPTIONS[index];
        }
    }

    // ------------------------------------------------------------------ link page

    private record Line(Component text, int x, int y, int width, int color) { }

    private record LinkLayout(Rect nationCard, Rect accountCard, List<Line> lines, Rect invite,
                              Rect nationField, Rect nationSubmit, Rect nationUnlink,
                              Rect accountField, Rect accountSubmit, Rect accountUnlink, int height) { }

    /** Builds the setup cards top-down; wrapped text heights drive every following position. */
    private LinkLayout linkLayout(Rect content) {
        List<Line> lines = new ArrayList<>();
        int x = content.x() + 14, textWidth = content.width() - 28;
        boolean narrow = content.width() < 320;
        Rect invite = null, nationField = null, nationSubmit = null, nationUnlink = null;
        Rect accountField = null, accountSubmit = null, accountUnlink = null;

        int y = content.y() + 10;
        boolean botDisabled = snapshot.botState() == DiscordLinkAccess.BotState.DISABLED;
        if (botDisabled) {
            y = text(lines, Component.translatable(KEY + "nation_setup_title"), x, y, textWidth, ACCENT) + 4;
            y = text(lines, Component.translatable(KEY + "bot.disabled"), x, y, textWidth, MUTED);
        } else if (snapshot.nationLinked()) {
            y = text(lines, Component.translatable(KEY + "nation_linked_title"), x, y, textWidth, SUCCESS) + 4;
            y = text(lines, Component.literal(display(snapshot.guildName(), "Discord") + " • "
                    + display(snapshot.channelName(), "#?")), x, y, textWidth, TEXT) + 2;
            y = text(lines, mentionTarget(), x, y, textWidth, MUTED) + 4;
            y = text(lines, Component.translatable(KEY + "nation_linked_hint"), x, y, textWidth, MUTED) + 6;
            if (snapshot.canManage()) {
                nationUnlink = new Rect(x, y, Math.min(137, textWidth), 18);
                y = nationUnlink.bottom();
            }
        } else if (!snapshot.canManage()) {
            y = text(lines, Component.translatable(KEY + "nation_unlinked_title"), x, y, textWidth, GOLD) + 4;
            y = text(lines, Component.translatable(KEY + "manage_required"), x, y, textWidth, MUTED);
        } else {
            y = text(lines, Component.translatable(KEY + "nation_setup_title"), x, y, textWidth, ACCENT) + 4;
            y = text(lines, Component.translatable(KEY + "nation_setup_intro"), x, y, textWidth, MUTED) + 8;
            y = text(lines, Component.translatable(KEY + "step.invite"), x, y, textWidth, TEXT) + 4;
            if (DiscordInviteLink.trusted(snapshot.inviteUrl())) {
                invite = new Rect(x, y, Math.min(170, textWidth), 20);
                y = invite.bottom() + 8;
            } else y = text(lines, Component.translatable(KEY + "invite_unavailable"), x, y, textWidth, MUTED) + 8;
            y = text(lines, Component.translatable(KEY + "step.setup"), x, y, textWidth, TEXT) + 8;
            y = text(lines, Component.translatable(KEY + "step.code"), x, y, textWidth, TEXT) + 4;
            Rect[] input = inputRow(x, y, textWidth, narrow);
            nationField = input[0];
            nationSubmit = input[1];
            y = nationSubmit.bottom() + 6;
            y = text(lines, Component.translatable(KEY + "nation_setup_footer"), x, y, textWidth, MUTED);
        }
        Rect nationCard = new Rect(content.x(), content.y(), content.width(), y + 10 - content.y());

        Rect accountCard = null;
        int bottom = nationCard.bottom();
        if (!botDisabled) {
            int top = nationCard.bottom() + 9;
            y = top + 10;
            if (snapshot.accountLinked()) {
                y = text(lines, Component.translatable(KEY + "account_linked_title"), x, y, textWidth, SUCCESS) + 4;
                y = text(lines, Component.translatable(KEY + "account_purpose"), x, y, textWidth, MUTED) + 6;
                accountUnlink = new Rect(x, y, Math.min(137, textWidth), 18);
                y = accountUnlink.bottom();
            } else {
                y = text(lines, Component.translatable(KEY + "account_title"), x, y, textWidth, ACCENT) + 4;
                y = text(lines, Component.translatable(KEY + "account_purpose"), x, y, textWidth, MUTED) + 6;
                y = text(lines, Component.translatable(KEY + "account_steps"), x, y, textWidth, TEXT) + 4;
                Rect[] input = inputRow(x, y, textWidth, narrow);
                accountField = input[0];
                accountSubmit = input[1];
                y = accountSubmit.bottom();
            }
            accountCard = new Rect(content.x(), top, content.width(), y + 10 - top);
            bottom = accountCard.bottom();
        }
        return new LinkLayout(nationCard, accountCard, lines, invite, nationField, nationSubmit, nationUnlink,
                accountField, accountSubmit, accountUnlink, bottom - content.y());
    }

    private int text(List<Line> lines, Component text, int x, int y, int width, int color) {
        lines.add(new Line(text, x, y, width, color));
        return y + wrappedHeight(font, text, width);
    }

    private static Rect[] inputRow(int x, int y, int width, boolean narrow) {
        if (narrow) return new Rect[]{new Rect(x, y, width, 20), new Rect(x, y + 24, width, 20)};
        int submitWidth = Math.min(137, width / 3);
        return new Rect[]{new Rect(x, y, width - submitWidth - 8, 20),
                new Rect(x + width - submitWidth, y, submitWidth, 20)};
    }

    private void drawLink(GuiGraphics graphics, Rect content, int mouseX, int mouseY) {
        LinkLayout layout = linkLayout(content);
        drawCard(graphics, layout.nationCard(), snapshot.nationLinked() ? SUCCESS : ACCENT, true, false);
        if (layout.accountCard() != null)
            drawCard(graphics, layout.accountCard(), snapshot.accountLinked() ? SUCCESS : BORDER, false, false);
        for (Line line : layout.lines())
            drawWrapped(graphics, font, line.text(), line.x(), line.y(), line.width(), line.color());
        boolean idle = pendingRequestId < 0;
        if (layout.invite() != null) drawButton(graphics, font, layout.invite(),
                Component.translatable(KEY + "invite_open"), ACCENT, layout.invite().contains(mouseX, mouseY), true);
        if (layout.nationSubmit() != null) {
            boolean enabled = idle && !nationCode.getValue().isBlank();
            drawButton(graphics, font, layout.nationSubmit(), Component.translatable(KEY + "link_submit"),
                    SUCCESS, enabled && layout.nationSubmit().contains(mouseX, mouseY), enabled);
        }
        if (layout.accountSubmit() != null) {
            boolean enabled = idle && !accountCode.getValue().isBlank();
            drawButton(graphics, font, layout.accountSubmit(), Component.translatable(KEY + "account_link_submit"),
                    SUCCESS, enabled && layout.accountSubmit().contains(mouseX, mouseY), enabled);
        }
        for (Rect unlink : new Rect[]{layout.nationUnlink(), layout.accountUnlink()}) {
            if (unlink != null) drawButton(graphics, font, unlink, Component.translatable(KEY + "unlink"),
                    DANGER, idle && unlink.contains(mouseX, mouseY), idle);
        }
    }

    private void openInvite() {
        String url = snapshot.inviteUrl();
        if (!DiscordInviteLink.trusted(url) || minecraft == null) return;
        ConfirmLinkScreen.confirmLinkNow(this, url);
    }

    // ------------------------------------------------------------------ history page

    private void drawHistory(GuiGraphics graphics, Rect content) {
        if (snapshot.history().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(KEY + "history_empty"),
                    content.x() + content.width() / 2, content.y() + 50, MUTED);
            return;
        }
        int y = content.y();
        for (S2C_OpenNationNotificationsPacket.HistoryEntry entry : snapshot.history()) {
            int color = switch (entry.state()) {
                case "delivered" -> SUCCESS;
                case "pending" -> GOLD;
                default -> DANGER;
            };
            drawCard(graphics, new Rect(content.x(), y, content.width(), 30), color, false, false);
            // Titles/details are translation keys; server-rendered event titles fall back to their literal text.
            graphics.drawString(font, font.plainSubstrByWidth(formatTime(entry.atMillis()) + "  "
                            + Component.translatable(entry.title()).getString(), content.width() - 18),
                    content.x() + 9, y + 5, TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth(
                            Component.translatable(entry.detail()).getString(), content.width() - 18),
                    content.x() + 9, y + 17, color, false);
            y += 34;
        }
    }

    // ------------------------------------------------------------------ input

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (confirm != Confirm.NONE) return handleConfirmation(mouseX, mouseY);
        if (discardConfirm) return handleDiscard(mouseX, mouseY);
        Rect panel = panelBounds();
        if (closeBounds(panel).contains(mouseX, mouseY) || backBounds(panel).contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        Rect tabs = tabBounds(panel);
        for (Page candidate : Page.values()) if (slot(tabs, candidate.ordinal(), Page.values().length, 4)
                .contains(mouseX, mouseY)) {
            selectPage(candidate);
            return true;
        }
        if (page == Page.PREFERENCES && snapshot.canManage() && dirty() && pendingRequestId < 0
                && saveBounds(panel).contains(mouseX, mouseY)) {
            save();
            return true;
        }
        Rect viewport = contentBounds(panel);
        if (pageContent(viewport).height() > viewport.height()
                && scrollbarBounds(viewport).contains(mouseX, mouseY)) {
            draggingScrollbar = true;
            scrollFromMouse(viewport, mouseY);
            return true;
        }
        if (!viewport.contains(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        Rect content = pageContent(viewport);
        double scrolledY = mouseY + contentScroll;
        boolean handled = switch (page) {
            case STATUS -> clickStatus(content, mouseX, scrolledY);
            case PREFERENCES -> clickPreferences(content, mouseX, scrolledY);
            case LINK -> clickLink(content, mouseX, scrolledY);
            case HISTORY -> false;
        };
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickStatus(Rect content, double mouseX, double mouseY) {
        if (pendingRequestId >= 0) return false;
        StatusLayout layout = statusLayout(content);
        for (int index = 0; index < layout.actions().size(); index++) {
            if (!layout.buttons().get(index).contains(mouseX, mouseY)) continue;
            switch (layout.actions().get(index)) {
                case SETUP -> selectPage(Page.LINK);
                case HISTORY -> selectPage(Page.HISTORY);
                case TEST -> sendAction(C2S_NotificationActionPacket.Action.TEST);
            }
            return true;
        }
        return false;
    }

    private boolean clickPreferences(Rect content, double mouseX, double mouseY) {
        if (!snapshot.canManage() || pendingRequestId >= 0) return false;
        PrefLayout layout = prefLayout(content);
        boolean compact = compact(content);
        for (int index = 0; index < layout.presets().size(); index++) {
            NotificationPreset preset = NotificationPreset.values()[index];
            if (preset != NotificationPreset.CUSTOM && layout.presets().get(index).contains(mouseX, mouseY)) {
                preferences.clear();
                preferences.putAll(preset.preferences(snapshot.nationLinked()));
                return true;
            }
        }
        int index = 0;
        for (NotificationCategory category : NotificationCategory.values()) {
            Rect row = layout.rows().get(index++);
            for (Group group : Group.values()) for (int option = 0; option < group.options; option++) {
                if (choiceBounds(row, group, option, compact).contains(mouseX, mouseY)) {
                    if (choiceEnabled(preferences.get(category), group)) choose(category, group, option);
                    return true;
                }
            }
        }
        for (Option option : Option.values()) {
            Rect row = layout.optionRows().get(option.ordinal());
            for (int choice = 0; choice < optionCount(option); choice++) {
                if (optionChoiceBounds(row, option, choice, compact).contains(mouseX, mouseY)) {
                    chooseOption(option, choice);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clickLink(Rect content, double mouseX, double mouseY) {
        LinkLayout layout = linkLayout(content);
        if (layout.invite() != null && layout.invite().contains(mouseX, mouseY)) {
            openInvite();
            return true;
        }
        if (pendingRequestId >= 0) return false;
        if (layout.nationSubmit() != null && layout.nationSubmit().contains(mouseX, mouseY)) {
            if (!nationCode.getValue().isBlank()) linkNation();
            return true;
        }
        if (layout.accountSubmit() != null && layout.accountSubmit().contains(mouseX, mouseY)) {
            if (!accountCode.getValue().isBlank()) linkAccount();
            return true;
        }
        if (layout.nationUnlink() != null && layout.nationUnlink().contains(mouseX, mouseY)) {
            confirm = Confirm.NATION;
            return true;
        }
        if (layout.accountUnlink() != null && layout.accountUnlink().contains(mouseX, mouseY)) {
            confirm = Confirm.ACCOUNT;
            return true;
        }
        return false;
    }

    private void selectPage(Page next) {
        if (page != next) contentScroll = 0;
        page = next;
        draggingScrollbar = false;
        updateFieldVisibility();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modalOpen()) return true;
        Rect viewport = contentBounds(panelBounds());
        if (!viewport.contains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        contentScroll = scroll(contentScroll, scrollY, 24, pageContent(viewport).height(), viewport.height());
        updateFieldVisibility();
        return true;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
            scrollFromMouse(contentBounds(panelBounds()), mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollFromMouse(Rect viewport, double mouseY) {
        int contentHeight = pageContent(viewport).height();
        int maxScroll = contentHeight - viewport.height();
        if (maxScroll <= 0) return;
        int thumbHeight = Math.max(20, viewport.height() * viewport.height() / contentHeight);
        double travel = Math.max(1, viewport.height() - thumbHeight);
        double fraction = Math.max(0.0D, Math.min(1.0D, (mouseY - viewport.y() - thumbHeight / 2.0D) / travel));
        contentScroll = (int) Math.round(fraction * maxScroll);
        updateFieldVisibility();
    }

    // ------------------------------------------------------------------ requests

    private NotificationPreset detectedPreset() {
        return NotificationPreset.detect(preferences, snapshot.nationLinked());
    }

    private void save() {
        pendingRequestId = ++nextRequestId;
        pendingTicks = 0;
        List<C2S_UpdateNationNotificationsPacket.PreferenceUpdate> updates = preferences.entrySet().stream()
                .map(entry -> new C2S_UpdateNationNotificationsPacket.PreferenceUpdate(entry.getKey(),
                        entry.getValue().inGame(), entry.getValue().discord(), entry.getValue().mention())).toList();
        PacketDistributor.sendToServer(new C2S_UpdateNationNotificationsPacket(pendingRequestId, revision,
                updates, coordinates, digestMinutes, mentionCooldownMinutes));
        updateEditable();
    }

    private void linkAccount() {
        pendingRequestId = ++nextRequestId; pendingTicks = 0;
        PacketDistributor.sendToServer(new C2S_LinkDiscordAccountPacket(pendingRequestId, accountCode.getValue()));
        updateEditable();
    }

    private void linkNation() {
        pendingRequestId = ++nextRequestId; pendingTicks = 0;
        PacketDistributor.sendToServer(new C2S_LinkNationDiscordPacket(pendingRequestId, nationCode.getValue()));
        updateEditable();
    }

    private void sendAction(C2S_NotificationActionPacket.Action action) {
        pendingRequestId = ++nextRequestId; pendingTicks = 0;
        PacketDistributor.sendToServer(new C2S_NotificationActionPacket(pendingRequestId, action));
        updateEditable();
    }

    public void handleResult(S2C_S2ActionResultPacket packet) {
        if (pendingRequestId >= 0 && packet.requestId() != pendingRequestId) return;
        pendingRequestId = -1; pendingTicks = 0;
        if (packet.success()) {
            revision = packet.latestRevision();
            accountCode.setValue(""); nationCode.setValue("");
        }
        showToast(Component.translatable(packet.messageKey()), packet.success() ? SUCCESS : DANGER, 90);
        updateEditable();
    }

    private void showToast(Component message, int color, int ticks) {
        toast = message;
        toastColor = color;
        toastTicks = ticks;
    }

    @Override public void tick() {
        super.tick();
        if (toastTicks > 0) toastTicks--;
        if (pendingRequestId >= 0 && ++pendingTicks >= 200) {
            pendingRequestId = -1; pendingTicks = 0;
            showToast(Component.translatable(KEY + "timeout"), DANGER, 120);
            updateEditable();
            PacketDistributor.sendToServer(new C2S_NotificationActionPacket(++nextRequestId,
                    C2S_NotificationActionPacket.Action.REFRESH));
        }
    }

    private boolean dirty() {
        return coordinates != savedCoordinates || digestMinutes != snapshot.digestMinutes()
                || mentionCooldownMinutes != snapshot.mentionCooldownMinutes()
                || !preferences.equals(savedPreferences);
    }

    private void updateEditable() {
        if (accountCode != null) accountCode.setEditable(pendingRequestId < 0);
        if (nationCode != null) nationCode.setEditable(snapshot.canManage() && pendingRequestId < 0);
    }

    private void updateFieldVisibility() {
        if (accountCode == null || nationCode == null) return;
        Rect viewport = contentBounds(panelBounds());
        LinkLayout layout = page == Page.LINK ? linkLayout(pageContent(viewport)) : null;
        positionField(accountCode, viewport, layout == null ? null : layout.accountField());
        positionField(nationCode, viewport, layout == null ? null : layout.nationField());
        updateEditable();
    }

    private void positionField(MoveEarthTextField field, Rect viewport, Rect bounds) {
        boolean visible = bounds != null && !modalOpen();
        if (visible) {
            field.setX(bounds.x());
            field.setY(bounds.y() - contentScroll);
            field.setWidth(bounds.width());
            visible = field.getY() >= viewport.y() && field.getY() + field.getHeight() <= viewport.bottom();
        }
        field.setVisible(visible);
        if (!visible) field.setFocused(false);
    }

    // ------------------------------------------------------------------ modals

    private boolean modalOpen() { return confirm != Confirm.NONE || discardConfirm; }

    private void drawModal(GuiGraphics graphics, int mouseX, int mouseY, String messageKey, int color, String confirmKey) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = modalBounds();
        drawPanel(graphics, modal);
        drawWrapped(graphics, font, Component.translatable(messageKey), modal.x() + 16, modal.y() + 18,
                modal.width() - 32, color);
        drawButton(graphics, font, modalCancel(modal), Component.translatable("screen.moveearth_addtional.nation.cancel"),
                MUTED, modalCancel(modal).contains(mouseX, mouseY), true);
        drawButton(graphics, font, modalConfirm(modal), Component.translatable(confirmKey),
                DANGER, modalConfirm(modal).contains(mouseX, mouseY), true);
    }

    private boolean handleConfirmation(double mouseX, double mouseY) {
        Rect modal = modalBounds();
        if (modalCancel(modal).contains(mouseX, mouseY)) confirm = Confirm.NONE;
        else if (modalConfirm(modal).contains(mouseX, mouseY)) {
            Confirm action = confirm;
            confirm = Confirm.NONE;
            sendAction(action == Confirm.ACCOUNT ? C2S_NotificationActionPacket.Action.UNLINK_ACCOUNT
                    : C2S_NotificationActionPacket.Action.UNLINK_NATION);
        }
        return true;
    }

    private boolean handleDiscard(double mouseX, double mouseY) {
        Rect modal = modalBounds();
        if (modalCancel(modal).contains(mouseX, mouseY)) discardConfirm = false;
        else if (modalConfirm(modal).contains(mouseX, mouseY)) { discardConfirm = false; returnToHub(); }
        return true;
    }

    @Override public void onClose() { if (dirty()) discardConfirm = true; else returnToHub(); }
    @Override public boolean isPauseScreen() { return false; }
    private void returnToHub() { PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW)); }

    // ------------------------------------------------------------------ status helpers

    private int overallColor() {
        if (snapshot.botState() == DiscordLinkAccess.BotState.AUTHENTICATION_FAILED || hasProblem()) return DANGER;
        if (snapshot.botState() != DiscordLinkAccess.BotState.READY || !snapshot.nationLinked()) return GOLD;
        return SUCCESS;
    }

    private boolean hasProblem() {
        return snapshot.retryingCount() > 0 || !snapshot.lastFailureReason().isBlank()
                && snapshot.lastFailureAtMillis() > snapshot.lastSuccessAtMillis();
    }

    private String overallKey() {
        int color = overallColor();
        return KEY + (color == SUCCESS ? "status_ok" : color == DANGER ? "status_error" : "status_setup");
    }

    private static String display(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private static String formatTime(long millis) {
        return millis <= 0 ? "--" : TIME.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static String categoryKey(NotificationCategory category) {
        return KEY + "category." + category.name().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ geometry

    private Rect panelBounds() {
        int w = Math.min(620, width - 16), h = Math.min(388, height - 16);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    private static Rect closeBounds(Rect panel) { return new Rect(panel.right() - 28, panel.y() + 8, 20, 20); }
    private static Rect tabBounds(Rect panel) { return new Rect(panel.x() + 16, panel.y() + 48, panel.width() - 32, 24); }
    private static Rect contentBounds(Rect panel) {
        return new Rect(panel.x() + 20, panel.y() + 82, panel.width() - 40, Math.max(0, panel.height() - 132));
    }
    private static Rect scrollbarBounds(Rect viewport) { return new Rect(viewport.right() - 8, viewport.y(), 8, viewport.height()); }
    private static boolean compact(Rect content) { return content.width() < 440; }

    private Rect pageContent(Rect viewport) {
        int needed = switch (page) {
            case STATUS -> statusLayout(viewport).height();
            case PREFERENCES -> prefLayout(viewport).height();
            case LINK -> linkLayout(viewport).height();
            case HISTORY -> Math.max(50, snapshot.history().size() * 34);
        };
        return new Rect(viewport.x(), viewport.y(), viewport.width(), Math.max(viewport.height(), needed));
    }

    private static Rect backBounds(Rect panel) { return new Rect(panel.x() + 20, panel.bottom() - 35, 100, 22); }
    private static Rect saveBounds(Rect panel) { return new Rect(panel.right() - 150, panel.bottom() - 35, 130, 22); }
    private Rect modalBounds() { return new Rect((width - 330) / 2, (height - 100) / 2, 330, 100); }
    private static Rect modalCancel(Rect modal) { return new Rect(modal.right() - 184, modal.bottom() - 32, 82, 21); }
    private static Rect modalConfirm(Rect modal) { return new Rect(modal.right() - 94, modal.bottom() - 32, 82, 21); }

    enum Page {
        STATUS(KEY + "page.status"),
        PREFERENCES(KEY + "page.preferences"),
        LINK(KEY + "page.link"),
        HISTORY(KEY + "page.history");
        private final String key;
        Page(String key) { this.key = key; }
    }

    private enum Confirm { NONE, ACCOUNT, NATION }
}
