package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S2HubOverviewLayoutTest {
    @Test
    void overviewActionsDoNotOverlapAtNormalOrCompactWidths() {
        assertSeparated(new Rect(18, 88, 584, 252));
        assertSeparated(new Rect(18, 88, 224, 252));
    }

    private static void assertSeparated(Rect content) {
        S2HubOverviewLayout.Actions actions = S2HubOverviewLayout.calculate(content);
        List<Rect> all = List.of(actions.membership(), actions.recovery(), actions.vault(),
                actions.treasury(), actions.preview());
        for (int first = 0; first < all.size(); first++) {
            Rect bounds = all.get(first);
            assertTrue(bounds.x() >= content.x() && bounds.right() <= content.right());
            assertTrue(bounds.y() >= content.y() && bounds.bottom() <= content.bottom());
            for (int second = first + 1; second < all.size(); second++) {
                assertFalse(overlaps(bounds, all.get(second)));
            }
        }
    }

    private static boolean overlaps(Rect first, Rect second) {
        return first.x() < second.right() && first.right() > second.x()
                && first.y() < second.bottom() && first.bottom() > second.y();
    }
}
