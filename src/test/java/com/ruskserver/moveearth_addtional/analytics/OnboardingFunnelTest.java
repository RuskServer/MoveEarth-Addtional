package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.query.OnboardingFunnel;
import com.ruskserver.moveearth_addtional.analytics.query.dto.GameEventAggregateDto;
import com.ruskserver.moveearth_addtional.s2.tutorial.TutorialCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OnboardingFunnelTest {
    @Test
    void ordersTutorialStepsByCatalogAndCountsPlayers() {
        String first = TutorialCatalog.STEPS.get(0).id();
        String second = TutorialCatalog.STEPS.get(1).id();
        OnboardingFunnel.Result result = OnboardingFunnel.build(12L,
                List.of(new GameEventAggregateDto("onboarding.wilderness", 9L, 0L, 8L),
                        new GameEventAggregateDto("tutorial.completed", 2L, 0L, 2L)),
                List.of(new GameEventAggregateDto(second, 5L, 0L, 5L),
                        new GameEventAggregateDto(first, 10L, 0L, 9L)),
                List.of(new GameEventAggregateDto(second, 1L, 0L, 1L)));

        assertEquals(12L, result.entry().get(0).players());
        assertEquals(8L, result.entry().stream().filter(stage -> stage.id().equals("onboarding.wilderness"))
                .findFirst().orElseThrow().players());
        assertEquals(TutorialCatalog.STEPS.size() + 1, result.tutorial().size());
        assertEquals(first, result.tutorial().get(0).id());
        assertEquals(9L, result.tutorial().get(0).players());
        assertEquals(5L, result.tutorial().get(1).players());
        assertEquals(0L, result.tutorial().get(2).players());
        assertEquals(2L, result.tutorial().get(result.tutorial().size() - 1).players());
        assertEquals(List.of(second), result.skippedAt().stream().map(OnboardingFunnel.Stage::id).toList());
    }

    @Test
    void emptyInputGivesZeroStages() {
        OnboardingFunnel.Result result = OnboardingFunnel.build(0L, List.of(), List.of(), List.of());
        assertTrue(result.entry().stream().allMatch(stage -> stage.players() == 0L));
        assertTrue(result.skippedAt().isEmpty());
    }
}
