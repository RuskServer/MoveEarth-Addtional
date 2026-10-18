package com.ruskserver.moveearth_addtional.compat.create;

import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** {@link MinecartContraptionPolicy} applied to Create's carts as they are assembled, run and unloaded. */
@EventBusSubscriber(modid = com.ruskserver.moveearth_addtional.Moveearth_addtional.MODID)
public final class MinecartContraptionRules {
    private static final String OWNER_TAG = "moveearth:minecart_owner_nation";
    /** Set on every cart assembled under these rules, owned or not; carts without it are older. */
    private static final String TAGGED_TAG = "moveearth:minecart_ruled";
    private static final String NOTICE_TAG = "moveearth:minecart_notice_after";
    /** Contraptions an assembler is unloading this tick, which may put their blocks down. */
    private static final Set<UUID> UNLOADING_AT_ASSEMBLER = new HashSet<>();

    private MinecartContraptionRules() {
    }

    public static boolean validateAssembly(ServerLevel level, BlockPos assemblerPos,
                                           MountedContraption contraption, AbstractMinecart cart) {
        UUID owner = landNation(level, assemblerPos);
        int blocks = contraption.getBlocks().size() - 1; // Create's synthetic minecart anchor is not cargo.
        int containers = 0;
        boolean allOwnLand = true;
        boolean reinforced = false;
        boolean protectedBlock = false;
        ReinforcementSavedData reinforcements = ReinforcementSavedData.get(level);
        BlockPos anchor = contraption.anchor == null ? assemblerPos : contraption.anchor;

        for (var entry : contraption.getBlocks().entrySet()) {
            BlockState state = entry.getValue().state();
            if (isCreateBlock(state, "minecart_anchor")) continue;
            BlockPos source = anchor.offset(entry.getKey());
            if (!MinecartContraptionPolicy.ownLand(owner, landNation(level, source))) allOwnLand = false;
            if (reinforcements.get(source).isPresent()) reinforced = true;
            if (isProtected(state)) protectedBlock = true;
            if (isStorage(level, source, state)) containers++;
        }

        MinecartContraptionPolicy.AssemblyFailure failure = MinecartContraptionPolicy.assemblyFailure(
                allOwnLand, blocks, containers, reinforced, protectedBlock);
        if (failure != MinecartContraptionPolicy.AssemblyFailure.NONE) {
            notifyNearby(level, assemblerPos, cart, Component.translatable(key(failure),
                    MinecartContraptionPolicy.MAX_BLOCKS, MinecartContraptionPolicy.MAX_CONTAINERS));
            return false;
        }
        tag(cart, owner);
        if (contraption.connectedCart != null) tag(contraption.connectedCart, owner);
        return true;
    }

    /** An assembler unloading {@code cart}: refused on another nation's land, and noted so the unload may place. */
    public static boolean canDisassemble(ServerLevel level, BlockPos destination, AbstractMinecart cart) {
        boolean allowed = MinecartContraptionPolicy.canUnload(tagged(cart), owner(cart),
                landNation(level, destination), true);
        if (!allowed) {
            notifyNearby(level, destination, cart,
                    Component.translatable("message.moveearth_addtional.minecart.foreign_disassembly"));
            return false;
        }
        for (Entity passenger : cart.getPassengers()) UNLOADING_AT_ASSEMBLER.add(passenger.getUUID());
        return true;
    }

    /**
     * Called as a contraption puts its blocks down. On land that is not the cart's own, and not at an
     * assembler, as when the cart is broken there, the cargo drops as items instead and the contraption is
     * removed: true when that happened and the placement must not go ahead.
     */
    public static boolean dropInsteadOfUnloading(AbstractContraptionEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || !(entity.getContraption() instanceof MountedContraption contraption)) return false;
        if (UNLOADING_AT_ASSEMBLER.remove(entity.getUUID())) return false;
        CompoundTag data = cartData(entity);
        if (MinecartContraptionPolicy.canUnload(data.getBoolean(TAGGED_TAG),
                data.hasUUID(OWNER_TAG) ? data.getUUID(OWNER_TAG) : null,
                landNation(level, entity.blockPosition()), false)) return false;
        dropCargo(level, entity, contraption);
        notifyNearby(level, entity.blockPosition(), null,
                Component.translatable("message.moveearth_addtional.minecart.cargo_dropped"));
        return true;
    }

    /** Keeps the cart's rules on the contraption too, for when it is unloaded after its cart is gone. */
    public static void rememberCart(AbstractContraptionEntity entity) {
        if (!(entity.getVehicle() instanceof AbstractMinecart cart)) return;
        CompoundTag own = entity.getPersistentData();
        CompoundTag cartTags = cart.getPersistentData();
        if (own.getBoolean(TAGGED_TAG) == cartTags.getBoolean(TAGGED_TAG)
                && java.util.Objects.equals(own.hasUUID(OWNER_TAG) ? own.getUUID(OWNER_TAG) : null,
                cartTags.hasUUID(OWNER_TAG) ? cartTags.getUUID(OWNER_TAG) : null)) return;
        own.putBoolean(TAGGED_TAG, cartTags.getBoolean(TAGGED_TAG));
        if (cartTags.hasUUID(OWNER_TAG)) own.putUUID(OWNER_TAG, cartTags.getUUID(OWNER_TAG));
        else own.remove(OWNER_TAG);
    }

    public static boolean shouldSuspendWorkActors(AbstractContraptionEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || !(entity.getContraption() instanceof MountedContraption contraption)
                || !containsWorkDevice(contraption)) return false;
        // Any part of it on foreign land, not just its centre: a long drill arm reaches over the border.
        UUID owner = owner(entity.getVehicle());
        var box = entity.getBoundingBox();
        int[][] corners = {{(int) Math.floor(box.minX), (int) Math.floor(box.minZ)}, {(int) Math.floor(box.maxX), (int) Math.floor(box.minZ)},
                {(int) Math.floor(box.minX), (int) Math.floor(box.maxZ)}, {(int) Math.floor(box.maxX), (int) Math.floor(box.maxZ)}};
        if (MinecartContraptionPolicy.suspendWorkActors(owner, landNation(level, entity.blockPosition()), true)) return true;
        for (int[] corner : corners) {
            BlockPos pos = new BlockPos(corner[0], entity.getBlockY(), corner[1]);
            if (MinecartContraptionPolicy.suspendWorkActors(owner, landNation(level, pos), true)) return true;
        }
        return false;
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

    private static boolean tagged(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(TAGGED_TAG);
    }

    private static void tag(AbstractMinecart cart, UUID owner) {
        cart.getPersistentData().putBoolean(TAGGED_TAG, true);
        if (owner != null) cart.getPersistentData().putUUID(OWNER_TAG, owner);
        else cart.getPersistentData().remove(OWNER_TAG);
    }

    /** The cart's tags, from the cart while it is there, from the contraption's own copy once it is gone. */
    private static CompoundTag cartData(AbstractContraptionEntity entity) {
        return entity.getVehicle() instanceof AbstractMinecart cart ? cart.getPersistentData() : entity.getPersistentData();
    }

    private static UUID landNation(ServerLevel level, BlockPos pos) {
        return TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), pos).orElse(null);
    }

    /** Every mounted item and one item per block, dropped where the contraption is; then it is gone. */
    private static void dropCargo(ServerLevel level, AbstractContraptionEntity entity, Contraption contraption) {
        double x = entity.getX();
        double y = entity.getY() + 0.5D;
        double z = entity.getZ();
        // Only the contraption's own storage blocks: the inventories of the carts it rides are attached to it too,
        // and those carts drop their own contents.
        for (var storage : contraption.getStorage().getAllItemStorages().values()) {
            for (int slot = 0; slot < storage.getSlots(); slot++) {
                ItemStack stack = storage.getStackInSlot(slot);
                if (!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, x, y, z, stack.copy()));
            }
        }
        // What each block drops when broken by hand, so a door or bed drops once and stone gives cobblestone.
        BlockPos where = entity.blockPosition();
        for (var info : contraption.getBlocks().values()) {
            BlockState state = info.state();
            if (state.isAir() || isCreateBlock(state, "minecart_anchor")) continue;
            for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, level, where, null)) {
                if (!drop.isEmpty()) level.addFreshEntity(new ItemEntity(level, x, y, z, drop));
            }
        }
        entity.ejectPassengers();
        entity.discard();
    }

    private static boolean containsWorkDevice(Contraption contraption) {
        for (var info : contraption.getBlocks().values()) {
            BlockState state = info.state();
            if (isCreateBlock(state, "mechanical_drill") || isCreateBlock(state, "mechanical_saw")
                    || isCreateBlock(state, "deployer") || isCreateBlock(state, "mechanical_plough")
                    || isCreateBlock(state, "mechanical_harvester") || isCreateBlock(state, "mechanical_roller")) return true;
        }
        return false;
    }

    /** Chests, barrels and the like, and Create's item vaults, which are not vanilla containers. */
    private static boolean isStorage(ServerLevel level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof Container || isCreateBlock(state, "item_vault");
    }

    private static boolean isProtected(BlockState state) {
        if (state.is(ModBlocks.TERRITORY_CORE.get()) || state.is(ModBlocks.VEHICLE_CORE.get())
                || state.is(ModBlocks.PRISON_INTAKE.get()) || state.is(ModBlocks.STORAGE_WRECKAGE.get())) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        // A bearing carries its own contraption, assembled after these checks and placed wherever it is unloaded.
        if ("create".equals(id.getNamespace()) && id.getPath().endsWith("_bearing")) return true;
        return MinecartContraptionPolicy.isWeaponNamespace(id.getNamespace());
    }

    private static boolean isCreateBlock(BlockState state, String path) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "create".equals(id.getNamespace()) && path.equals(id.getPath());
    }

    private static String key(MinecartContraptionPolicy.AssemblyFailure failure) {
        return switch (failure) {
            case FOREIGN_BLOCK -> "message.moveearth_addtional.minecart.foreign_block";
            case TOO_LARGE -> "message.moveearth_addtional.minecart.too_large";
            case TOO_MANY_CONTAINERS -> "message.moveearth_addtional.minecart.too_many_containers";
            case REINFORCED_BLOCK -> "message.moveearth_addtional.minecart.reinforced";
            case PROTECTED_BLOCK -> "message.moveearth_addtional.minecart.protected_block";
            case NONE -> throw new IllegalArgumentException("No failure message for an allowed assembly");
        };
    }

    /** Tells the players by the assembler or cart why, on their action bar, at most every two seconds. */
    private static void notifyNearby(ServerLevel level, BlockPos pos, AbstractMinecart cart, Component message) {
        long now = level.getGameTime();
        if (cart != null) {
            if (cart.getPersistentData().getLong(NOTICE_TAG) > now) return;
            cart.getPersistentData().putLong(NOTICE_TAG, now + 40L);
        }
        Component styled = message.copy().withStyle(ChatFormatting.GOLD);
        for (var player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 256.0D) {
                player.displayClientMessage(styled, true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        UNLOADING_AT_ASSEMBLER.clear();
    }
}
