package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

/**
 * Says why each hole a closure diagnosis found lets the core out, and what to do about it, in chat.
 * The overlay marks the same holes; it cannot tell them apart.
 */
final class TerritoryClosureReport {
    private static final String KEY = "message.moveearth_addtional.territory_closure.";
    /** Holes listed in chat; the overlay marks every one. */
    private static final int CHAT_LINES = 8;

    private TerritoryClosureReport() {
    }

    enum Reason {
        AIR, PASSABLE, UNREINFORCED, NOT_FULL, PENDING, DISABLED, BROKEN, SIEGE, DOOR_HALF
    }

    record Hole(BlockPos pos, Reason reason, BlockState state, long secondsLeft) { }

    static Hole classify(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return new Hole(pos, state.isAir() ? Reason.AIR : Reason.PASSABLE, state, 0L);
        }
        if (SiegeSavedData.get(level.getServer()).isReinforcementDisabled(level.dimension().location(), pos)) {
            return new Hole(pos, Reason.SIEGE, state, 0L);
        }
        boolean shapeCounts = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.isCollisionShapeFullBlock(level, pos);
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        ReinforcementEntry entry = reinforcements.get(pos).orElse(null);
        if (!shapeCounts) return new Hole(pos, Reason.NOT_FULL, state, 0L);
        if (entry == null) return new Hole(pos, Reason.UNREINFORCED, state, 0L);
        if (!entry.enabled()) {
            long ticks = entry.activationTicksRemaining(level.getGameTime());
            return entry.activatesAt() > 0L
                    ? new Hole(pos, Reason.PENDING, state, (ticks + 19L) / 20L)
                    : new Hole(pos, Reason.DISABLED, state, 0L);
        }
        if (entry.durability() <= 0) return new Hole(pos, Reason.BROKEN, state, 0L);
        // Anything else was fixed after the diagnosis looked; callers drop working barriers first.
        return new Hole(pos, Reason.DOOR_HALF, state, 0L);
    }

    /** The holes, nearest to {@code player} first. */
    static List<Hole> nearestFirst(List<Hole> holes, ServerPlayer player) {
        return holes.stream()
                .sorted(Comparator.comparingDouble(hole -> hole.pos().distToCenterSqr(player.position())))
                .toList();
    }

    static void send(ServerPlayer player, List<Hole> holes, boolean complete) {
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatableWithFallback(KEY + "holes",
                "密閉の穴が%sか所見つかりました（近い順）。画面のマーカーの位置を塞いでください",
                holes.size())));
        for (int i = 0; i < Math.min(CHAT_LINES, holes.size()); i++) {
            player.sendSystemMessage(line(holes.get(i)));
        }
        if (holes.size() > CHAT_LINES) {
            player.sendSystemMessage(Component.translatableWithFallback(KEY + "more",
                    "  …ほか%sか所はマーカーで確認してください", holes.size() - CHAT_LINES)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!complete) {
            player.sendSystemMessage(MoveEarthMessage.info(Component.translatableWithFallback(KEY + "incomplete",
                    "ほかにも穴が残っている可能性があります。見つかった穴を塞いでから、もう一度診断してください")));
        }
    }

    static void sendTooLarge(ServerPlayer player) {
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatableWithFallback(KEY + "too_large",
                "囲いは閉じていますが、内側の空間が広すぎます（%sブロックまで）。コアの周りを壁で小さく仕切ってください",
                TerritoryClosureScanner.MAX_VISITED)));
    }

    private static MutableComponent line(Hole hole) {
        BlockPos pos = hole.pos();
        Component block = Component.translatable(hole.state().getBlock().getDescriptionId());
        MutableComponent where = Component.literal("  • " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "  ")
                .withStyle(ChatFormatting.YELLOW);
        Component why = switch (hole.reason()) {
            case AIR -> Component.translatableWithFallback(KEY + "reason.air",
                    "空いています。ブロックを置いて補強してください");
            case PASSABLE -> Component.translatableWithFallback(KEY + "reason.passable",
                    "%sは通り抜けられるので壁になりません。完全なブロックに置き換えて補強してください", block);
            case UNREINFORCED -> Component.translatableWithFallback(KEY + "reason.unreinforced",
                    "%sが未補強です。補強してください", block);
            case NOT_FULL -> Component.translatableWithFallback(KEY + "reason.not_full",
                    "%sは完全なブロックではないので、補強しても密閉に数えません。完全なブロックかドア・トラップドアに置き換えてください",
                    block);
            case PENDING -> Component.translatableWithFallback(KEY + "reason.pending",
                    "%sの補強は起動待ちです（あと%s秒）。起動すると密閉に数えます", block, hole.secondsLeft());
            case DISABLED -> Component.translatableWithFallback(KEY + "reason.disabled",
                    "%sの補強が無効になっています", block);
            case BROKEN -> Component.translatableWithFallback(KEY + "reason.broken",
                    "%sの補強の耐久が0です。修理してください", block);
            case SIEGE -> Component.translatableWithFallback(KEY + "reason.siege",
                    "攻城戦で、この位置の補強が無効になっています");
            case DOOR_HALF -> Component.translatableWithFallback(KEY + "reason.door_half",
                    "ドアのもう片方（上または下）が未補強です。両方を補強してください");
        };
        return where.append(why.copy().withStyle(ChatFormatting.WHITE));
    }
}
