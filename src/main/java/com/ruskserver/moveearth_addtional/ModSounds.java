package com.ruskserver.moveearth_addtional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Moveearth_addtional.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SERVER_NOTICE = register("server_notice");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_START = warlord("start");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_RAMPAGE = warlord("rampage");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_DOUBLE_KILL = warlord("double_kill");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_TRIPLE_KILL = warlord("triple_kill");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_DOMINATING = warlord("dominating");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_UNSTOPPABLE = warlord("unstoppable");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_TARGET_LOCKED = warlord("target_locked");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_FIRST_BLOOD = warlord("first_blood");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_HEADSHOT = warlord("headshot");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_KILLSHOT = warlord("killshot");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_PAYLOAD_DELIVERED = warlord("payload_delivered");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_TEAM_DEATHMATCH = warlord("team_deathmatch");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_ELIMINATION = warlord("elimination");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_CAPTURE_THE_FLAG = warlord("capture_the_flag");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_LAST_MAN_STANDING = warlord("last_man_standing");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_PRECISION_KILL = warlord("precision_kill");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_OBJECTIVE_COMPLETED = warlord("objective_completed");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_ENEMY_ELIMINATED = warlord("enemy_eliminated");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_ROUND_WINNER = warlord("round_winner");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_MISSION_FAILED = warlord("mission_failed");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_TANGO_DOWN = warlord("tango_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_MISSION_ACCOMPLISHED = warlord("mission_accomplished");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_FINAL_STAND = warlord("final_stand");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_ANNIHILATION = warlord("annihilation");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_PAYBACK = warlord("payback");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_ERADICATION = warlord("eradication");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_REVENGE_KILL = warlord("revenge_kill");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_TARGET_SECURED = warlord("target_secured");
    public static final DeferredHolder<SoundEvent, SoundEvent> WARLORD_GAME_OVER = warlord("game_over");

    private static DeferredHolder<SoundEvent, SoundEvent> warlord(String name) {
        return register("warlord_" + name);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, name)));
    }
}
