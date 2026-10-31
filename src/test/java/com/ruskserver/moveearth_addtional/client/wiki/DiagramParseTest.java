package com.ruskserver.moveearth_addtional.client.wiki;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramParseTest {
    @Test
    void voxelReadsLegendLayersAndCaptionAfterTheGrid() {
        var scene = VoxelScene.parse("title: 一重\nlegend:\n  # = 補強ブロック #4e5a48\n  C = 領土コア #9bc86a\n  ! = 抜かれた #e05a5a ghost\n  W = 手前 #abc cutaway\nlayers:\n---\n#C\n---\n!W\ncaption: 説明");
        assertEquals("一重", scene.title());
        assertEquals("説明", scene.caption());
        assertEquals(4, scene.legend().size());
        assertEquals(4, scene.blocks().size());
        var ghost = scene.blocks().get(2);
        assertEquals(1, ghost.y());
        assertTrue(ghost.kind().ghost());
        assertFalse(ghost.kind().cutaway());
        assertEquals(0xAABBCC, scene.blocks().get(3).kind().rgb());
        assertTrue(scene.hasCutaway());
    }

    @Test
    void roundsCountsShotsRoundingUp() {
        var chart = RoundsChart.parse("title: 通常弾\ndamage: 96\n丸石 = 32\nダイヤモンド = 320\n");
        assertEquals(96, chart.damage());
        assertEquals(2, chart.rows().size());
        assertEquals(new RoundsChart.Shots(4, 32), RoundsChart.shotsFor(320, 96));
        assertEquals(new RoundsChart.Shots(1, 32), RoundsChart.shotsFor(32, 96));
        assertEquals(new RoundsChart.Shots(0, 0), RoundsChart.shotsFor(10, 0));
    }
}
