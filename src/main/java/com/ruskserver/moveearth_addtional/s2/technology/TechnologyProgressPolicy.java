package com.ruskserver.moveearth_addtional.s2.technology;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/** Pure progression rules shared by persistence and tests. */
public final class TechnologyProgressPolicy {
    private TechnologyProgressPolicy() { }

    public static boolean prerequisitesMet(TechnologyDefinition definition, Set<ResourceLocation> completed) {
        if (definition.prerequisites().isEmpty()) return true;
        return definition.prerequisiteMode() == TechnologyDefinition.PrerequisiteMode.ANY
                ? definition.prerequisites().stream().anyMatch(completed::contains)
                : completed.containsAll(definition.prerequisites());
    }

    public static long increment(long current, long amount, long required) {
        if (amount <= 0L) return Math.min(Math.max(0L, current), required);
        return Math.min(required, Math.max(0L, current) + amount);
    }

    public static boolean objectivesComplete(TechnologyDefinition definition, Map<String, Long> progress) {
        return definition.objectives().stream().allMatch(objective ->
                progress.getOrDefault(objective.id(), 0L) >= objective.required());
    }
}
