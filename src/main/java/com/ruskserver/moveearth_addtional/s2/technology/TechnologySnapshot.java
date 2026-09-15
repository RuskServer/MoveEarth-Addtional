package com.ruskserver.moveearth_addtional.s2.technology;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record TechnologySnapshot(long revision, boolean member, String nationName, List<Node> nodes) {
    public enum State { LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED, INHERITED, DISABLED }
    public record Node(ResourceLocation id, TechnologyDefinition.Scope scope, String category, String chapter,
                       String titleKey, String descriptionKey, ResourceLocation icon,
                       int x, int y, State state, boolean tracked, List<Objective> objectives,
                       TechnologyDefinition.PrerequisiteMode prerequisiteMode,
                       List<ResourceLocation> prerequisites, List<String> unlocks,
                       List<ResourceLocation> jeiItems) { }
    public record Objective(String descriptionKey, long current, long required) { }
}
