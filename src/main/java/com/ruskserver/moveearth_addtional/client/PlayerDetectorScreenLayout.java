package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;

import java.util.Locale;

/**
 * Pure geometry and state formatting for {@link PlayerDetectorScreen}, aligned with the S2 Hub / PvP UI
 * vocabulary ({@link MoveEarthUi}).
 */
public final class PlayerDetectorScreenLayout {
    public static final int FIT_WIDTH = 420;
    public static final int FIT_HEIGHT = 228;

    private static final int PAD = 14;
    private static final int HEADER_HEIGHT = 36;
    private static final int OWNER_CARD_HEIGHT = 48;
    private static final int UPKEEP_CARD_HEIGHT = 68;
    private static final int NAME_CARD_HEIGHT = 48;
    private static final int CARD_GAP = 5;

    private PlayerDetectorScreenLayout() {
    }

    public record Layout(
            Rect panel,
            Rect close,
            Rect ownerCard,
            Rect upkeepCard,
            Rect progressBar,
            Rect payButton,
            Rect nameCard,
            Rect nameInput,
            Rect saveButton,
            Rect resetButton
    ) {
    }

    public static Layout calculate(int screenWidth, int screenHeight) {
        int panelWidth = Math.min(screenWidth, Math.min(FIT_WIDTH, Math.max(304, screenWidth - 16)));
        int panelHeight = Math.min(screenHeight, Math.min(FIT_HEIGHT, Math.max(216, screenHeight - 12)));
        int left = Math.max(0, (screenWidth - panelWidth) / 2);
        int top = Math.max(0, (screenHeight - panelHeight) / 2);

        Rect panel = new Rect(left, top, panelWidth, panelHeight);
        Rect close = new Rect(panel.right() - 28, panel.y() + 8, 20, 20);

        int cardX = panel.x() + PAD;
        int cardWidth = panel.width() - PAD * 2;

        Rect ownerCard = new Rect(cardX, panel.y() + HEADER_HEIGHT, cardWidth, OWNER_CARD_HEIGHT);
        Rect upkeepCard = new Rect(cardX, ownerCard.bottom() + CARD_GAP, cardWidth, UPKEEP_CARD_HEIGHT);

        int payWidth = Math.min(138, Math.max(104, cardWidth / 3));
        Rect payButton = new Rect(upkeepCard.right() - payWidth - 10, upkeepCard.y() + 23, payWidth, 22);
        int barWidth = Math.max(72, payButton.x() - (upkeepCard.x() + 12) - 10);
        Rect progressBar = new Rect(upkeepCard.x() + 12, upkeepCard.y() + 49, barWidth, 5);

        Rect nameCard = new Rect(cardX, upkeepCard.bottom() + CARD_GAP, cardWidth, NAME_CARD_HEIGHT);
        int resetWidth = 78;
        int saveWidth = 60;
        int buttonGap = 6;
        int inputY = nameCard.y() + 21;
        Rect resetButton = new Rect(nameCard.right() - resetWidth - 10, inputY, resetWidth, 20);
        Rect saveButton = new Rect(resetButton.x() - saveWidth - buttonGap, inputY, saveWidth, 20);
        int inputWidth = Math.max(72, saveButton.x() - (nameCard.x() + 12) - buttonGap);
        Rect nameInput = new Rect(nameCard.x() + 12, inputY, inputWidth, 20);

        return new Layout(
                panel,
                close,
                ownerCard,
                upkeepCard,
                progressBar,
                payButton,
                nameCard,
                nameInput,
                saveButton,
                resetButton
        );
    }

    public static boolean stopped(boolean active, long nextPaymentTime, long now) {
        return !active || now >= nextPaymentTime;
    }

    public static boolean warmingUp(boolean active, long placedTime, long nextPaymentTime, long now) {
        return !stopped(active, nextPaymentTime, now)
                && now < placedTime + PlayerDetectorBlockEntity.WARMUP_DURATION_MS;
    }

    public static int stateColor(boolean active, long placedTime, long nextPaymentTime, long now) {
        if (stopped(active, nextPaymentTime, now)) {
            return MoveEarthUi.DANGER;
        }
        return warmingUp(active, placedTime, nextPaymentTime, now) ? MoveEarthUi.GOLD : MoveEarthUi.SUCCESS;
    }

    public static String stateText(boolean active, long placedTime, long nextPaymentTime, long now) {
        if (stopped(active, nextPaymentTime, now)) {
            return active ? "停止中（期限切れ）" : "停止中（維持費未払い）";
        }
        long warmup = placedTime + PlayerDetectorBlockEntity.WARMUP_DURATION_MS - now;
        if (warmup > 0L) {
            return String.format(Locale.ROOT, "起動準備中（作動まで %s）", clock(warmup));
        }
        return String.format(Locale.ROOT, "稼働中（次回引き落としまで %s）", clock(nextPaymentTime - now));
    }

    /** @param cycleMillis one paid cycle, to show how much of it is left */
    public static float progressRatio(boolean active, long placedTime, long nextPaymentTime, long now,
                                      long cycleMillis) {
        if (stopped(active, nextPaymentTime, now)) {
            return 0.0F;
        }
        long warmupRemaining = placedTime + PlayerDetectorBlockEntity.WARMUP_DURATION_MS - now;
        if (warmupRemaining > 0L) {
            long elapsed = Math.max(0L, PlayerDetectorBlockEntity.WARMUP_DURATION_MS - warmupRemaining);
            return clamp01((float) elapsed / (float) PlayerDetectorBlockEntity.WARMUP_DURATION_MS);
        }
        long remaining = Math.max(0L, nextPaymentTime - now);
        return clamp01((float) remaining / (float) Math.max(1L, cycleMillis));
    }

    /** The server's upkeep, as the state packet carries it: the client keeps no copy of its settings. */
    public static String upkeepSummaryText(long cost, int cycleHours) {
        return String.format(
                Locale.ROOT,
                "維持費: %d時間ごとに %d TC（半径%.0fm検知）",
                cycleHours,
                cost,
                PlayerDetectorBlockEntity.DETECTION_RANGE
        );
    }

    public static String clock(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L);
    }

    public static String money(long amount) {
        return String.format(Locale.US, "%,d TC", amount);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
