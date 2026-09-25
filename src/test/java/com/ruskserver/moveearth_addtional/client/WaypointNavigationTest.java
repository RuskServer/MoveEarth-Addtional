package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointNavigationTest {
    @Test void arrowsFollowPlayerFacing() {
        assertEquals("↑", WaypointNavigation.arrow(0, 0, 0, 0, 10));
        assertEquals("→", WaypointNavigation.arrow(0, 0, 0, -10, 0));
        assertEquals("←", WaypointNavigation.arrow(0, 0, 0, 10, 0));
        assertEquals("↓", WaypointNavigation.arrow(0, 0, 0, 0, -10));
        assertEquals("↑", WaypointNavigation.arrow(0, 0, 90, -10, 0));
        assertEquals("◆", WaypointNavigation.arrow(0, 0, 0, 0.5, 0.5));
    }

    @Test void distanceUsesHorizontalPlane() {
        assertEquals(5, WaypointNavigation.horizontalDistance(0, 0, 3, 4));
    }

    @Test void distantWorldMarkersGrowToMaintainReadableSize() {
        assertEquals(1.0F, WaypointNavigation.worldMarkerScale(8));
        assertEquals(2.0F, WaypointNavigation.worldMarkerScale(32));
        assertEquals(12.0F, WaypointNavigation.worldMarkerScale(192));
        assertEquals(12.0F, WaypointNavigation.worldMarkerScale(1000));
    }

    @Test void distantMarkersStayVisibleAndPointAtScreenEdges() {
        var ahead = WaypointNavigation.screenMarker(0, 0, 1000, 640, 360, 70);
        assertEquals(320, ahead.x());
        assertEquals("", ahead.direction());
        var right = WaypointNavigation.screenMarker(1000, 0, 0, 640, 360, 70);
        assertEquals("▶", right.direction());
        assertTrue(right.x() < 640);
        var left = WaypointNavigation.screenMarker(-1000, 0, 0, 640, 360, 70);
        assertEquals("◀", left.direction());
        assertTrue(left.x() > 0);
        var above = WaypointNavigation.screenMarker(0, 1000, 0, 640, 360, 70);
        assertEquals("▲", above.direction());
    }
}
