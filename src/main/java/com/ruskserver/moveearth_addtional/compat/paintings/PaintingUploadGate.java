package com.ruskserver.moveearth_addtional.compat.paintings;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.pvp.reward.ServerOpenDayCycle;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.format.DateTimeFormatter;

/**
 * Applies {@link PaintingUploadPolicy} where Immersive Paintings registers an uploaded image
 * ({@code mixin.paintings.PaintingUploadLimitMixin}). The mod's own checks (permission level, size, its lifetime
 * count) have passed by then; this adds nation membership and the daily count.
 */
public final class PaintingUploadGate {
    /** Shown on the painting screen as {@code immersive_paintings.error.<key>}; the texts are in this mod's lang. */
    public static final String NO_NATION = "moveearth_no_nation";
    public static final String DAILY_LIMIT = "moveearth_daily_limit";
    private static final DateTimeFormatter RESET = DateTimeFormatter.ofPattern("HH:mm");

    private PaintingUploadGate() {
    }

    /** @return the error key to refuse with, or {@code null} to let the upload through */
    public static String refusal(ServerPlayer player) {
        int limit = S2TerritoryConfig.paintingDailyUploads();
        PaintingUploadPolicy.Verdict verdict = PaintingUploadPolicy.check(player.hasPermissions(2),
                NationSavedData.get(player.server).nationIdFor(player.getUUID()).isPresent(),
                PaintingUploadSavedData.get(player.server).usedToday(player.getUUID(), ServerOpenDayCycle.currentId()),
                limit);
        return switch (verdict) {
            case ALLOWED -> null;
            case NO_NATION -> NO_NATION;
            case DAILY_LIMIT -> {
                player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                        "message.moveearth_addtional.painting_upload.limit", limit,
                        ServerOpenDayCycle.nextReset().format(RESET))));
                yield DAILY_LIMIT;
            }
        };
    }

    /** A painting was registered: it counts, and the player hears what is left today. */
    public static void uploaded(ServerPlayer player) {
        if (player.hasPermissions(2)) return;
        String today = ServerOpenDayCycle.currentId();
        PaintingUploadSavedData data = PaintingUploadSavedData.get(player.server);
        data.record(player.getUUID(), today);
        int left = Math.max(0, S2TerritoryConfig.paintingDailyUploads() - data.usedToday(player.getUUID(), today));
        player.displayClientMessage(Component.translatable(
                "message.moveearth_addtional.painting_upload.remaining", left), true);
        Moveearth_addtional.LOGGER.info("{} uploaded a painting ({} left today)", player.getGameProfile().getName(), left);
    }
}
