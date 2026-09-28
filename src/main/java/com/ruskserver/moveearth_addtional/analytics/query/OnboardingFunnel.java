package com.ruskserver.moveearth_addtional.analytics.query;

import com.ruskserver.moveearth_addtional.analytics.event.GameEventType;
import com.ruskserver.moveearth_addtional.analytics.query.dto.GameEventAggregateDto;
import com.ruskserver.moveearth_addtional.s2.tutorial.TutorialCatalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The first-join funnel for the dashboard. Every stage counts distinct players
 * who reached it inside the window, so stages measure activity in that window
 * rather than following one cohort of new players.
 */
public final class OnboardingFunnel {
    private OnboardingFunnel() { }

    public record Stage(String id, String label, long players) { }

    public record Result(long newPlayers, List<Stage> entry, List<Stage> tutorial, List<Stage> skippedAt) { }

    /**
     * @param byType  game events grouped by type
     * @param steps   {@code tutorial.step} events grouped by step id
     * @param skipped {@code tutorial.skipped} events grouped by the step the player was on
     */
    public static Result build(long newPlayers, List<GameEventAggregateDto> byType,
                               List<GameEventAggregateDto> steps, List<GameEventAggregateDto> skipped) {
        Map<String, Long> types = players(byType);
        List<Stage> entry = new ArrayList<>();
        entry.add(new Stage("new_players", "新規プレイヤー", newPlayers));
        for (GameEventType type : List.of(GameEventType.ONBOARDING_WILDERNESS, GameEventType.ONBOARDING_APPLIED,
                GameEventType.ONBOARDING_APPROVED, GameEventType.ONBOARDING_REJECTED, GameEventType.NATION_FOUNDED,
                GameEventType.NATION_JOINED, GameEventType.NATION_LEFT)) {
            entry.add(new Stage(type.id(), type.label(), types.getOrDefault(type.id(), 0L)));
        }

        Map<String, Long> reached = players(steps);
        List<Stage> tutorial = new ArrayList<>();
        for (int index = 0; index < TutorialCatalog.STEPS.size(); index++) {
            String id = TutorialCatalog.STEPS.get(index).id();
            tutorial.add(new Stage(id, (index + 1) + ". " + id, reached.getOrDefault(id, 0L)));
        }
        tutorial.add(new Stage(GameEventType.TUTORIAL_COMPLETED.id(), GameEventType.TUTORIAL_COMPLETED.label(),
                types.getOrDefault(GameEventType.TUTORIAL_COMPLETED.id(), 0L)));

        Map<String, Long> skippedAt = players(skipped);
        List<Stage> skips = new ArrayList<>();
        for (TutorialCatalog.Step step : TutorialCatalog.STEPS) {
            long count = skippedAt.getOrDefault(step.id(), 0L);
            if (count > 0L) skips.add(new Stage(step.id(), step.id(), count));
        }
        return new Result(newPlayers, entry, tutorial, skips);
    }

    private static Map<String, Long> players(List<GameEventAggregateDto> rows) {
        Map<String, Long> result = new HashMap<>();
        if (rows == null) return result;
        for (GameEventAggregateDto row : rows) {
            if (row.key() != null) result.merge(row.key(), row.players(), Long::sum);
        }
        return result;
    }
}
