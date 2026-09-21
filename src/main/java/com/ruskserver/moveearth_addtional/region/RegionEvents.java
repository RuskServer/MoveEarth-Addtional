package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate;
import com.ruskserver.moveearth_addtional.compat.cdg.RegionOilGate;
import com.ruskserver.moveearth_addtional.compat.rns.RnsMinerStress;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.LinkedHashSet;
import java.util.Set;

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

    /** Create: Diesel Generators' mod id. Optional in the same way. */
    public static final String CDG_MOD_ID = "createdieselgenerators";

    private RegionEvents() { }

    /**
     * Every material this pack can actually produce, from all the systems that
     * produce one.
     *
     * <p>One method because two callers need the same answer: the allocation is
     * made from this set, and later checked against it. Built separately they
     * would drift, and the drift would read as "a material appeared after the
     * world was allocated" -- an error about a bug that was in the counting.
     *
     * <p>Oil is not a deposit and not an ore, so nothing else would mention it.
     * Whether a region may have it is a separate question the profiles answer;
     * this only says the resource exists to be allocated at all.
     */
    public static Set<String> availableMaterials() {
        Set<String> materials = new LinkedHashSet<>();
        if (ModList.get().isLoaded(RNS_MOD_ID)) {
            materials.addAll(RnsDepositGate.availableMaterials());
        }
        if (ModList.get().isLoaded(CDG_MOD_ID)) {
            materials.add(RegionOilGate.MATERIAL);
        }
        return Set.copyOf(materials);
    }

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
        RegionProfiles.verifyAgainst(availableMaterials());
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
