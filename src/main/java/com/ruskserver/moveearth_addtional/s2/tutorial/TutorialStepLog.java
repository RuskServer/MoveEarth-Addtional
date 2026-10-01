package com.ruskserver.moveearth_addtional.s2.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which tutorial steps to report as done. A step is reported once, when it is
 * actually done, in whatever order that happens: the nation step can be passed
 * over while the player has none, and is reported only when they join or found
 * one, not when the goal list moves past it.
 */
public final class TutorialStepLog {
    private TutorialStepLog() { }

    /** Indices of steps done now but not reported yet, in catalog order. */
    public static List<Integer> newlyDone(boolean[] done, Set<String> reported) {
        List<Integer> fresh = new ArrayList<>();
        for (int index = 0; index < TutorialCatalog.STEPS.size() && index < done.length; index++) {
            if (done[index] && !reported.contains(TutorialCatalog.STEPS.get(index).id())) fresh.add(index);
        }
        return fresh;
    }

    /**
     * What the old index-based log already reported: the steps before {@code reached}
     * that are really done. A deferred step the old log counted on passing, but which
     * is not done, is left out so it is reported when it is.
     */
    public static List<String> migrated(int reached, boolean[] done) {
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < reached && index < TutorialCatalog.STEPS.size() && index < done.length; index++) {
            if (done[index]) ids.add(TutorialCatalog.STEPS.get(index).id());
        }
        return ids;
    }
}
