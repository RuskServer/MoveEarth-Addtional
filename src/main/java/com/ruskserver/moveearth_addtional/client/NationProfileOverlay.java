package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.network.c2s.nation.C2S_RequestNationProfilePacket;
import com.ruskserver.moveearth_addtional.network.s2c.nation.S2C_NationProfilePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/**
 * Another nation's profile over the hub, opened by clicking it in the diplomacy pages: its size, its citizens
 * by name, and on the right who it is allied and hostile with and the wars it is in. The profile is asked of
 * the server when opened rather than carried in every hub snapshot.
 */
final class NationProfileOverlay {
    private static final int LINE = 12;

    private UUID nationId;
    private String fallbackName = "";
    private S2C_NationProfilePacket profile;
    private int memberScroll;
    private int sideScroll;

    boolean isOpen() {
        return nationId != null;
    }

    UUID nationId() {
        return nationId;
    }

    void show(UUID id, String name) {
        nationId = id;
        fallbackName = name == null ? "" : name;
        profile = null;
        memberScroll = 0;
        sideScroll = 0;
        PacketDistributor.sendToServer(new C2S_RequestNationProfilePacket(id));
    }

    void close() {
        nationId = null;
        profile = null;
    }

    void accept(S2C_NationProfilePacket packet) {
        if (packet.nationId().equals(nationId)) profile = packet;
    }

    private static Rect modal(int width, int height) {
        int w = Math.min(500, Math.max(0, width - 24));
        int h = Math.min(310, Math.max(0, height - 24));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private static Rect closeBounds(Rect modal) {
        return new Rect(modal.right() - 28, modal.y() + 8, 20, 20);
    }

    private static Rect memberList(Rect modal) {
        int top = modal.y() + 86;
        return new Rect(modal.x() + 12, top, (modal.width() - 36) / 2, Math.max(0, modal.bottom() - 10 - top));
    }

    private static Rect sideList(Rect modal) {
        Rect members = memberList(modal);
        return new Rect(members.right() + 12, members.y(), modal.right() - 12 - members.right() - 12, members.height());
    }

    /**
     * @param relation the viewer's relation to the nation, as the diplomacy card words it
     * @param badge    the nation's strongest protection, or null
     */
    void render(GuiGraphics graphics, Font font, int width, int height, int mouseX, int mouseY,
                Component relation, int relationColor, Component badge) {
        drawModalBackdrop(graphics, width, height);
        Rect modal = modal(width, height);
        drawPanel(graphics, modal);
        Rect close = closeBounds(modal);
        drawClose(graphics, font, close, close.contains(mouseX, mouseY));

        String name = profile == null ? fallbackName : profile.name();
        String tag = profile == null ? "" : profile.tag();
        String title = tag.isBlank() ? name : "[" + tag + "] " + name;
        graphics.drawString(font, font.plainSubstrByWidth(title, modal.width() - 52), modal.x() + 14, modal.y() + 14, ACCENT, false);
        if (profile == null || profile.name().isEmpty()) {
            graphics.drawCenteredString(font, profile == null ? "読み込み中…" : "この国はもう存在しません",
                    modal.x() + modal.width() / 2, modal.y() + 80, MUTED);
            return;
        }
        String stats = "代表 " + profile.owner() + "   国民 " + profile.members().size() + "人   領土 "
                + profile.chunks() + "チャンク   前哨基地 " + profile.outposts();
        graphics.drawString(font, font.plainSubstrByWidth(stats, modal.width() - 28), modal.x() + 14, modal.y() + 32, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(relation.getString(), modal.width() - 28),
                modal.x() + 14, modal.y() + 46, relationColor, false);
        if (badge != null) {
            graphics.drawString(font, font.plainSubstrByWidth(badge.getString(), modal.width() - 28),
                    modal.x() + 14, modal.y() + 58, GOLD, false);
        }

        Rect members = memberList(modal);
        graphics.drawString(font, "国民" + (profile.onlineShown() ? "（● オンライン）" : ""), members.x(), members.y() - 12, MUTED, false);
        graphics.enableScissor(members.x(), members.y(), members.right(), members.bottom());
        int y = members.y() - memberScroll;
        for (int i = 0; i < profile.members().size(); i++, y += LINE) {
            S2C_NationProfilePacket.Member member = profile.members().get(i);
            if (y + LINE < members.y() || y > members.bottom()) continue;
            int x = members.x() + 2;
            if (profile.onlineShown()) {
                graphics.drawString(font, "●", x, y, member.online() ? SUCCESS : 0xFF3A4350, false);
                x += 10;
            }
            String label = i == 0 && member.name().equals(profile.owner()) ? member.name() + " ★" : member.name();
            graphics.drawString(font, font.plainSubstrByWidth(label, members.right() - x - 6), x, y, TEXT, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, new Rect(members.right() - 3, members.y(), 3, members.height()),
                members.height(), profile.members().size() * LINE, memberScroll);

        Rect side = sideList(modal);
        graphics.enableScissor(side.x(), side.y() - 12, side.right(), side.bottom());
        int sy = side.y() - 12 - sideScroll;
        sy = section(graphics, font, side, sy, "同盟国", SUCCESS, refs(profile.allies()));
        sy = section(graphics, font, side, sy + 6, "敵対国", DANGER, refs(profile.hostiles()));
        section(graphics, font, side, sy + 6, "交戦中", GOLD, profile.wars().stream()
                .map(war -> (war.attacking() ? "攻撃中 → " : "防衛中 ← ")
                        + (war.opponentTag().isBlank() ? "" : "[" + war.opponentTag() + "] ") + war.opponentName())
                .toList());
        graphics.disableScissor();
    }

    private static List<String> refs(List<S2C_NationProfilePacket.Ref> refs) {
        return refs.stream().map(ref -> ref.tag().isBlank() ? ref.name() : "[" + ref.tag() + "] " + ref.name()).toList();
    }

    private static int section(GuiGraphics graphics, Font font, Rect side, int y, String title, int color, List<String> lines) {
        graphics.drawString(font, title, side.x(), y, color, false);
        y += LINE;
        if (lines.isEmpty()) {
            graphics.drawString(font, "なし", side.x() + 6, y, MUTED, false);
            return y + LINE;
        }
        for (String line : lines) {
            graphics.drawString(font, font.plainSubstrByWidth(line, side.width() - 8), side.x() + 6, y, TEXT, false);
            y += LINE;
        }
        return y;
    }

    /** Returns true when the click was the overlay's; a click on the close button or outside closes it. */
    boolean click(double mouseX, double mouseY, int width, int height) {
        Rect modal = modal(width, height);
        if (closeBounds(modal).contains(mouseX, mouseY) || !modal.contains(mouseX, mouseY)) close();
        return true;
    }

    boolean scroll(double mouseX, double mouseY, double scrollY, int width, int height) {
        if (profile == null || profile.name().isEmpty()) return true;
        Rect modal = modal(width, height);
        Rect members = memberList(modal);
        if (members.contains(mouseX, mouseY)) {
            memberScroll = MoveEarthUi.scroll(memberScroll, scrollY, LINE, profile.members().size() * LINE, members.height());
        } else if (sideList(modal).contains(mouseX, mouseY)) {
            // Three titles, each list (or its "none"), and the gaps between the sections, as drawn.
            int lines = 3 + Math.max(1, profile.allies().size()) + Math.max(1, profile.hostiles().size())
                    + Math.max(1, profile.wars().size());
            sideScroll = MoveEarthUi.scroll(sideScroll, scrollY, LINE, lines * LINE + 12, members.height() + 12);
        }
        return true;
    }
}
