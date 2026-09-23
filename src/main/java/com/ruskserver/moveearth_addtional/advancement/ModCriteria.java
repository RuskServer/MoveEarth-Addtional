package com.ruskserver.moveearth_addtional.advancement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteria {
    public static final String NATION_HUB_OPENED = "nation_hub_opened";
    public static final String TERRITORY_VIEWED = "territory_viewed";
    public static final String NATION_APPLIED = "nation_applied";
    public static final String NATION_CITIZEN = "nation_citizen";
    public static final String NATION_FOUNDED = "nation_founded";
    public static final String TREASURY_CONFIGURED = "treasury_configured";
    public static final String UPKEEP_PAID = "upkeep_paid";
    public static final String COLD_PROTECTION = "cold_protection";
    public static final String REST_HEALED = "rest_healed";
    public static final String ELECTRICITY_BUILT = "electricity_built";
    public static final String FREIGHT_COMPLETED = "freight_completed";
    public static final String REINFORCEMENT_ACTIVATED = "reinforcement_activated";
    public static final String TERRITORY_SEALED = "territory_sealed";
    public static final String VEHICLE_CORE_REGISTERED = "vehicle_core_registered";
    public static final String VEHICLE_REPAIRED = "vehicle_repaired";
    public static final String SIEGE_REINFORCEMENT_REPAIRED = "siege_reinforcement_repaired";
    public static final String COMBAT_STARTED = "combat_started";
    public static final String SIEGE_PARTICIPATED = "siege_participated";
    public static final String ARTILLERY_HIT = "artillery_hit";
    public static final String MOBILE_FORCE_PARTICIPATED = "mobile_force_participated";
    public static final String ALLY_REVIVED = "ally_revived";
    public static final String PRISONER_IMPRISONED = "prisoner_imprisoned";
    public static final String PRISONER_FREED = "prisoner_freed";
    public static final String CORE_SABOTAGE_COMPLETED = "core_sabotage_completed";
    public static final String TERRITORY_DEFENDED = "territory_defended";
    public static final String WAREHOUSE_ENTERED = "warehouse_entered";
    public static final String WAREHOUSE_RAID_PARTICIPATED = "warehouse_raid_participated";
    public static final String WAREHOUSE_BOSS_DEFEATED = "warehouse_boss_defeated";
    public static final String WAREHOUSE_LOOT_OPENED = "warehouse_loot_opened";

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Moveearth_addtional.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, MoveEarthEventTrigger> EVENT =
            TRIGGERS.register("event", MoveEarthEventTrigger::new);

    private ModCriteria() {
    }

    public static void trigger(ServerPlayer player, String event) {
        EVENT.get().trigger(player, event);
    }
}
