package com.ruskserver.moveearth_addtional.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.jobs.JobProgressSavedData;
import com.ruskserver.moveearth_addtional.region.RegionProfiles;
import com.ruskserver.moveearth_addtional.region.RegionResolver;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

public final class ResourceEvent {
    public static final long DURATION_TICKS = 45L * 60L * 20L;
    private static final ResourceLocation MINER = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "miner");

    private ResourceEvent() { }

    public static Target chooseTarget(int sequence) {
        if (!RegionResolver.ready() || !RegionProfiles.ready()) return null;
        List<Target> targets = RegionProfiles.assignments().stream()
                .filter(assignment -> List.of("coal", "iron", "copper").contains(assignment.specialty()))
                .map(assignment -> new Target(assignment.regionId(), assignment.specialty()))
                .sorted(Comparator.comparingInt(Target::region).thenComparing(Target::material))
                .toList();
        return targets.isEmpty() ? null : targets.get(Math.floorMod(sequence, targets.size()));
    }

    public static void mine(ServerPlayer player, ServerLevel level, BlockPos position, BlockState state) {
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        String material = material(state);
        if (material == null) return;
        MinecraftServer server = player.getServer();
        if (!OpenTimeService.isOpen(server)) return;
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        if (!"RESOURCE".equals(ledger.eventKind()) || ledger.harvestSettled()
                || !material.equals(ledger.targetMaterial())) return;
        ledger.scoreResource(player.getUUID(), player.getGameProfile().getName(),
                JobProgressSavedData.get(server).isActive(player.getUUID(), MINER),
                RegionResolver.regionAt(position.getX(), position.getZ()), material, OpenTimeService.now(server));
    }

    private static String material(BlockState state) {
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) return "coal";
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) return "iron";
        if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) return "copper";
        return null;
    }

    public record Target(int region, String material) { }
}
