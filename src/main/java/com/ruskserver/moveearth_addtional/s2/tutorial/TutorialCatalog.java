package com.ruskserver.moveearth_addtional.s2.tutorial;

import java.util.List;

/**
 * The first-session tutorial: twelve concrete goals from the nation hub to the
 * first brass ingot. Each step is complete once its advancement is done, so
 * steps finished early, in any order, are skipped over.
 */
public final class TutorialCatalog {
    public enum Kind {
        /** Done when {@link Step#advancement()} is done. */
        ADVANCEMENT,
        /** Done while the player belongs to a nation, however they got there. */
        NATION,
        /**
         * Players who may reinforce must reinforce a block; others only craft the
         * welder, since new members hold no reinforcement permission by default.
         */
        DEFENSE
    }

    /**
     * @param advancement        MoveEarth advancement path that completes the step
     * @param memberAdvancement  DEFENSE only: the path for players without the reinforcement permission
     * @param icon               item shown beside the goal, or empty
     */
    public record Step(String id, Kind kind, String advancement, String memberAdvancement, String icon) { }

    public static final List<Step> STEPS = List.of(
            step("open_hub", "getting_started/open_nation_hub", ""),
            // Second: Jobs income and event points require a nation, so everything after pays off.
            new Step("nation", Kind.NATION, "", "", ""),
            step("region", "tutorial/region_viewed", ""),
            step("rest", "getting_started/rest", "minecraft:campfire"),
            step("andesite_alloy", "industry/andesite_alloy", "create:andesite_alloy"),
            step("andesite_casing", "tutorial/andesite_casing", "create:andesite_casing"),
            step("rotation", "industry/rotation", "create:water_wheel"),
            new Step("defense", Kind.DEFENSE, "engineering/reinforce", "engineering/welding_tool",
                    "moveearth_addtional:welding_tool"),
            step("iron_sheet", "industry/iron_sheet", "create:iron_sheet"),
            step("mixer_basin", "tutorial/mixer_basin", "create:mechanical_mixer"),
            step("zinc", "tutorial/zinc_ingot", "create:zinc_ingot"),
            step("brass", "industry/brass", "create:brass_ingot"));

    private TutorialCatalog() { }

    /** Index of the first step {@code done} rejects, or -1 when every step is done. */
    public static int current(java.util.function.Predicate<Step> done) {
        for (int index = 0; index < STEPS.size(); index++) {
            if (!done.test(STEPS.get(index))) return index;
        }
        return -1;
    }

    /** Translation id for a step; DEFENSE has an owner and a member wording. */
    public static String textId(Step step, boolean canReinforce) {
        if (step.kind() != Kind.DEFENSE) return step.id();
        return step.id() + (canReinforce ? "_owner" : "_member");
    }

    private static Step step(String id, String advancement, String icon) {
        return new Step(id, Kind.ADVANCEMENT, advancement, "", icon);
    }
}
