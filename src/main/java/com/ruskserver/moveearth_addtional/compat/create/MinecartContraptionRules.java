package com.ruskserver.moveearth_addtional.compat.create;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Server-authoritative runtime checks for Create minecart contraptions. */
public final class MinecartContraptionRules {
    private static final String OWNER_TAG = "moveearth:minecart_owner_nation";
    private static final String NOTICE_TAG = "moveearth:minecart_notice_after";

    private MinecartContraptionRules() {
    }

    public static boolean validateAssembly(ServerLevel level, BlockPos assemblerPos,
                                           MountedContraption contraption, AbstractMinecart cart) {
        TerritorySavedData territories = TerritorySavedData.get(level.getServer());
        UUID owner = territories.controllingNation(level.getServer(), level.dimension().location(), assemblerPos)
                .orElse(null);
        int blocks = contraption.getBlocks().size() - 1; // Create's synthetic minecart anchor is not cargo.
        int containers = 0;
        boolean allAtHome = owner != null;
        boolean reinforced = false;
        boolean protectedBlock = false;
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        BlockPos anchor = contraption.anchor == null ? assemblerPos : contraption.anchor;

        for (var entry : contraption.getBlocks().entrySet()) {
            BlockState state = entry.getValue().state();
            if (isCreateBlock(state, "minecart_anchor")) continue;
            BlockPos source = anchor.offset(entry.getKey());
            UUID controlling = territories.controllingNation(
                    level.getServer(), level.dimension().location(), source).orElse(null);
            if (owner == null || !owner.equals(controlling)) allAtHome = false;
            if (reinforcements.get(source).isPresent()) reinforced = true;
            if (isProtected(state)) protectedBlock = true;
            if (level.getBlockEntity(source) instanceof Container) containers++;
        }

        MinecartContraptionPolicy.AssemblyFailure failure = MinecartContraptionPolicy.assemblyFailure(
                owner != null, allAtHome, blocks, containers, reinforced, protectedBlock);
        if (failure != MinecartContraptionPolicy.AssemblyFailure.NONE) {
            notifyNearby(level, assemblerPos, cart, key(failure));
            return false;
        }
        cart.getPersistentData().putUUID(OWNER_TAG, owner);
        if (contraption.connectedCart != null) {
            contraption.connectedCart.getPersistentData().putUUID(OWNER_TAG, owner);
        }
        return true;
    }

    public static boolean canDisassemble(ServerLevel level, BlockPos destination, AbstractMinecart cart) {
        UUID owner = owner(cart);
        UUID controlling = TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), destination).orElse(null);
        boolean allowed = MinecartContraptionPolicy.canDisassemble(owner, controlling);
        if (!allowed) notifyNearby(level, destination, cart,
                "message.moveearth_addtional.minecart.foreign_disassembly");
        return allowed;
    }

    public static boolean shouldSuspendWorkActors(AbstractContraptionEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || !(entity.getContraption() instanceof MountedContraption contraption)
                || !containsWorkDevice(contraption)) return false;
        UUID owner = owner(entity.getVehicle());
        UUID controlling = TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), entity.blockPosition())
                .orElse(null);
        return MinecartContraptionPolicy.suspendWorkActors(owner, controlling, true);
    }

    public static boolean isMinecartContraption(Entity target) {
        if (target instanceof AbstractContraptionEntity contraption) target = contraption.getVehicle();
        if (!(target instanceof AbstractMinecart cart) || cart.getPassengers().isEmpty()) return false;
        return cart.getPassengers().getFirst() instanceof AbstractContraptionEntity entity
                && entity.getContraption() instanceof MountedContraption;
    }

    public static UUID owner(Entity entity) {
        if (entity == null || !entity.getPersistentData().hasUUID(OWNER_TAG)) return null;
        return entity.getPersistentData().getUUID(OWNER_TAG);
    }

    private static boolean containsWorkDevice(Contraption contraption) {
        for (var info : contraption.getBlocks().values()) {
            BlockState state = info.state();
            if (isCreateBlock(state, "mechanical_drill") || isCreateBlock(state, "mechanical_saw")
                    || isCreateBlock(state, "deployer") || isCreateBlock(state, "mechanical_plough")
                    || isCreateBlock(state, "mechanical_harvester")) return true;
        }
        return false;
    }

    private static boolean isProtected(BlockState state) {
        if (state.is(ModBlocks.TERRITORY_CORE.get()) || state.is(ModBlocks.VEHICLE_CORE.get())
                || state.is(ModBlocks.PRISON_INTAKE.get()) || state.is(ModBlocks.STORAGE_WRECKAGE.get())) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && MinecartContraptionPolicy.isWeaponNamespace(id.getNamespace());
    }

    private static boolean isCreateBlock(BlockState state, String path) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "create".equals(id.getNamespace()) && path.equals(id.getPath());
    }

    private static String key(MinecartContraptionPolicy.AssemblyFailure failure) {
        return switch (failure) {
            case NO_HOME_TERRITORY -> "message.moveearth_addtional.minecart.no_home";
            case CROSSES_TERRITORY -> "message.moveearth_addtional.minecart.crosses_territory";
            case TOO_LARGE -> "message.moveearth_addtional.minecart.too_large";
            case TOO_MANY_CONTAINERS -> "message.moveearth_addtional.minecart.too_many_containers";
            case REINFORCED_BLOCK -> "message.moveearth_addtional.minecart.reinforced";
            case PROTECTED_BLOCK -> "message.moveearth_addtional.minecart.protected_block";
            case NONE -> throw new IllegalArgumentException("No failure message for an allowed assembly");
        };
    }

    private static void notifyNearby(ServerLevel level, BlockPos pos, AbstractMinecart cart, String key) {
        long now = level.getGameTime();
        if (cart.getPersistentData().getLong(NOTICE_TAG) > now) return;
        cart.getPersistentData().putLong(NOTICE_TAG, now + 40L);
        Component message = MoveEarthMessage.warning(Component.translatable(key));
        for (var player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 256.0D) {
                player.sendSystemMessage(message);
            }
        }
    }
}
