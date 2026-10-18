package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi;
import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDetectorScreenLayoutTest {

    @Test
    void upkeepBalanceMatchesS2TwentyFourHourCycle() {
        assertEquals(2L, PlayerDetectorBlockEntity.UPKEEP_COST);
        assertEquals(24, PlayerDetectorBlockEntity.UPKEEP_CYCLE_HOURS);
        assertEquals(24L * 60L * 60L * 1000L, PlayerDetectorBlockEntity.UPKEEP_INTERVAL_MS);
        assertEquals("維持費: 24時間ごとに 2 TC（半径100m検知）", PlayerDetectorScreenLayout.upkeepSummaryText());
    }

    @Test
    void layoutStaysInsideThePanelWithoutOverlapAcrossScreenSizes() {
        int[][] sizes = {{320, 240}, {427, 260}, {568, 328}, {640, 360}, {960, 540}, {1920, 1080}};
        for (int[] size : sizes) {
            PlayerDetectorScreenLayout.Layout layout = PlayerDetectorScreenLayout.calculate(size[0], size[1]);
            Rect panel = layout.panel();
            assertTrue(panel.x() >= 0 && panel.right() <= size[0], "panel width at " + size[0]);
            assertTrue(panel.y() >= 0 && panel.bottom() <= size[1], "panel height at " + size[1]);

            for (Rect child : new Rect[]{
                    layout.close(),
                    layout.ownerCard(),
                    layout.upkeepCard(),
                    layout.progressBar(),
                    layout.payButton(),
                    layout.nameCard(),
                    layout.nameInput(),
                    layout.saveButton(),
                    layout.resetButton(),
                    layout.footerClose()
            }) {
                assertTrue(inside(child, panel), "child inside panel at " + size[0] + "x" + size[1]);
            }

            assertTrue(inside(layout.progressBar(), layout.upkeepCard()));
            assertTrue(inside(layout.payButton(), layout.upkeepCard()));
            assertTrue(inside(layout.nameInput(), layout.nameCard()));
            assertTrue(inside(layout.saveButton(), layout.nameCard()));
            assertTrue(inside(layout.resetButton(), layout.nameCard()));

            assertFalse(overlaps(layout.ownerCard(), layout.upkeepCard()));
            assertFalse(overlaps(layout.upkeepCard(), layout.nameCard()));
            assertFalse(overlaps(layout.nameCard(), layout.footerClose()));
            assertFalse(overlaps(layout.progressBar(), layout.payButton()));
            assertFalse(overlaps(layout.nameInput(), layout.saveButton()));
            assertFalse(overlaps(layout.saveButton(), layout.resetButton()));
        }
    }

    @Test
    void stateFormattingAndProgressReflectStoppedWarmupAndActiveStates() {
        long placed = 1_000_000L;
        long nextPayment = placed + PlayerDetectorBlockEntity.UPKEEP_INTERVAL_MS;

        // Unpaid / inactive
        assertTrue(PlayerDetectorScreenLayout.stopped(false, 0L, placed));
        assertEquals(MoveEarthUi.DANGER, PlayerDetectorScreenLayout.stateColor(false, placed, 0L, placed));
        assertEquals("停止中（維持費未払い）", PlayerDetectorScreenLayout.stateText(false, placed, 0L, placed));
        assertEquals(0.0F, PlayerDetectorScreenLayout.progressRatio(false, placed, 0L, placed));

        // Expired
        assertTrue(PlayerDetectorScreenLayout.stopped(true, nextPayment, nextPayment));
        assertEquals(MoveEarthUi.DANGER, PlayerDetectorScreenLayout.stateColor(true, placed, nextPayment, nextPayment));
        assertEquals("停止中（期限切れ）", PlayerDetectorScreenLayout.stateText(true, placed, nextPayment, nextPayment));

        // Halfway through 20-minute warmup
        long warmupHalf = placed + PlayerDetectorBlockEntity.WARMUP_DURATION_MS / 2;
        assertFalse(PlayerDetectorScreenLayout.stopped(true, nextPayment, warmupHalf));
        assertTrue(PlayerDetectorScreenLayout.warmingUp(true, placed, nextPayment, warmupHalf));
        assertEquals(MoveEarthUi.GOLD, PlayerDetectorScreenLayout.stateColor(true, placed, nextPayment, warmupHalf));
        assertEquals("起動準備中（作動まで 0:10:00）", PlayerDetectorScreenLayout.stateText(true, placed, nextPayment, warmupHalf));
        assertEquals(0.5F, PlayerDetectorScreenLayout.progressRatio(true, placed, nextPayment, warmupHalf), 0.001F);

        // Operational with 12 hours remaining
        long halfCycle = nextPayment - PlayerDetectorBlockEntity.UPKEEP_INTERVAL_MS / 2;
        assertFalse(PlayerDetectorScreenLayout.warmingUp(true, placed, nextPayment, halfCycle));
        assertEquals(MoveEarthUi.SUCCESS, PlayerDetectorScreenLayout.stateColor(true, placed, nextPayment, halfCycle));
        assertEquals("稼働中（次回引き落としまで 12:00:00）", PlayerDetectorScreenLayout.stateText(true, placed, nextPayment, halfCycle));
        assertEquals(0.5F, PlayerDetectorScreenLayout.progressRatio(true, placed, nextPayment, halfCycle), 0.001F);
    }

    private static boolean inside(Rect child, Rect parent) {
        return child.x() >= parent.x() && child.right() <= parent.right()
                && child.y() >= parent.y() && child.bottom() <= parent.bottom();
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && a.right() > b.x() && a.y() < b.bottom() && a.bottom() > b.y();
    }
}
