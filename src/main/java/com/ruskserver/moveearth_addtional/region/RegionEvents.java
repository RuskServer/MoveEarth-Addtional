package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate;
import com.ruskserver.moveearth_addtional.compat.rns.RnsMinerStress;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Server lifecycle for the region system.
 *
 * <p>The region allocation is built earlier, with the terrain tiles, because
 * chunk generation needs it. Deposit materials are built here instead: they are
 * read from a datapack registry, which is not loaded until the server has
 * started.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class RegionEvents {

    /** Create: Rock & Stone's mod id. Optional; absent on a plain server. */
    public static final String RNS_MOD_ID = "create_rns";

    private RegionEvents() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ModList.get().isLoaded(RNS_MOD_ID)) {
            return;
        }
        // Resolved again now that every registry and tag is certainly bound. If
        // this finds more than the allocation was made from, the allocation was
        // built on a partial picture and an operator needs to hear about it.
        RnsDepositGate.rebuild(event.getServer());
        // Deposit blocks are named from mining recipes, which only exist once
        // the server has started; the gate reads deposit specs and can be built
        // earlier, so the two are deliberately not rebuilt together.
        RnsMinerStress.rebuild(event.getServer());
        RegionProfiles.verifyAgainst(RnsDepositGate.availableMaterials());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // A single-player client can start a second world in the same process,
        // and the previous world's regions must not answer for the new one.
        RegionProfiles.clear();
        RegionResolver.invalidate();
        if (ModList.get().isLoaded(RNS_MOD_ID)) {
            RnsDepositGate.clear();
            RnsMinerStress.clear();
        }
    }
}
