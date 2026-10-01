package com.ruskserver.moveearth_addtional.nether;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * One blaze rod from one stick and one Nether shard. Progress is shaft work
 * ({@link KineticWork}) and advances only while the refiner is heated by a lava
 * fan, turns at or above the minimum speed and is not overstressed; otherwise it
 * pauses and keeps what it has done.
 *
 * <p>Placed on a belt or depot, it works in line: sticks and shards passing
 * under it are taken in, a few at a time so a row of refiners over one belt
 * shares them, and finished rods go back onto the belt below. Belts running
 * into its sides also deliver sticks and shards, and rods are otherwise pushed
 * onto a belt leading away from it or a depot beside it.
 */
public final class BlazeRodRefinerBlockEntity extends KineticBlockEntity {
    static final int STICK = 0;
    static final int SHARD = 1;
    static final int OUTPUT = 2;
    /** Sticks or shards taken from a belt below; enough to stay busy, few enough to share. */
    static final int BELT_BUFFER = 2;

    private final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == STICK ? stack.is(Items.STICK)
                    : slot == SHARD && stack.is(NetherGateRegistry.NETHER_SHARD.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) sendData();
        }
    };

    /** What funnels, chutes and hoppers see: sticks and shards in, rods out. */
    private final IItemHandler automation = new IItemHandler() {
        @Override public int getSlots() { return 3; }
        @Override public ItemStack getStackInSlot(int slot) { return inventory.getStackInSlot(slot); }
        @Override public int getSlotLimit(int slot) { return inventory.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return inventory.isItemValid(slot, stack); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == OUTPUT ? stack : inventory.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };

    private long progress;
    private boolean heated;
    // Synced to clients for the press animation and goggles.
    private boolean working;
    private float progressFraction;

    public BlazeRodRefinerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new DirectBeltInputBehaviour(this).onlyInsertWhen(side -> side.getAxis().isHorizontal()));
    }

    public IItemHandler automationHandler() {
        return automation;
    }

    static boolean acceptsInput(ItemStack stack) {
        return stack.is(Items.STICK) || stack.is(NetherGateRegistry.NETHER_SHARD.get());
    }

    ItemStack insertByHand(ItemStack held) {
        int slot = held.is(Items.STICK) ? STICK : SHARD;
        return inventory.insertItem(slot, held.copy(), false);
    }

    void giveOutput(Player player) {
        ItemStack rods = inventory.extractItem(OUTPUT, 64, false);
        if (rods.isEmpty()) return;
        if (!player.getInventory().add(rods)) player.drop(rods, false);
        player.level().playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
    }

    void dropContents() {
        if (level == null) return;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    inventory.getStackInSlot(slot));
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;
        if (level.isClientSide) return;
        long gameTime = level.getGameTime();
        if (gameTime % 20L == 0L) heated = LavaFanHeat.isHeated(level, worldPosition);
        if (gameTime % 2L == 0L) takeFromBelow();
        if (gameTime % 10L == 0L) pushOutput();

        long required = NetherGateConfig.refinerWork();
        boolean inputs = !inventory.getStackInSlot(STICK).isEmpty() && !inventory.getStackInSlot(SHARD).isEmpty();
        ItemStack output = inventory.getStackInSlot(OUTPUT);
        boolean room = output.isEmpty() || output.getCount() < output.getMaxStackSize();
        long step = inputs && room && heated && !isOverStressed()
                ? KineticWork.step(getSpeed(), NetherGateConfig.refinerMinRpm()) : 0L;
        if (step > 0L) {
            progress += step;
            if (progress >= required) {
                progress = 0L;
                inventory.extractItem(STICK, 1, false);
                inventory.extractItem(SHARD, 1, false);
                ItemStack rod = new ItemStack(Items.BLAZE_ROD);
                if (output.isEmpty()) inventory.setStackInSlot(OUTPUT, rod);
                else output.grow(1);
                level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.4F);
                // Credit whoever is running the line nearby; the refiner itself has no owner.
                for (var player : level.players()) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer server
                            && !player.isSpectator() && player.distanceToSqr(worldPosition.getCenter()) <= 16 * 16) {
                        com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(server, com.ruskserver.moveearth_addtional.advancement.ModCriteria.BLAZE_ROD_REFINED);
                    }
                }
            }
            setChanged();
        }
        boolean nowWorking = step > 0L;
        float fraction = required <= 0L ? 0.0F : progress / (float) required;
        if (nowWorking != working || (gameTime % 20L == 0L && Math.abs(fraction - progressFraction) >= 0.005F)) {
            working = nowWorking;
            progressFraction = fraction;
            sendData();
        }
    }

    /** Takes sticks and shards off the middle of the belt or depot underneath. */
    private void takeFromBelow() {
        TransportedItemStackHandlerBehaviour below = BlockEntityBehaviour.get(level, worldPosition.below(),
                TransportedItemStackHandlerBehaviour.TYPE);
        if (below == null) return;
        below.handleCenteredProcessingOnAllItems(0.5F, this::takeFromBelt);
    }

    private TransportedResult takeFromBelt(TransportedItemStack transported) {
        ItemStack stack = transported.stack;
        int slot = stack.is(Items.STICK) ? STICK : stack.is(NetherGateRegistry.NETHER_SHARD.get()) ? SHARD : -1;
        if (slot < 0) return TransportedResult.doNothing();
        int take = Math.min(stack.getCount(), BELT_BUFFER - inventory.getStackInSlot(slot).getCount());
        if (take <= 0) return TransportedResult.doNothing();
        ItemStack refused = inventory.insertItem(slot, stack.copyWithCount(take), false);
        int taken = take - refused.getCount();
        if (taken <= 0) return TransportedResult.doNothing();
        if (taken >= stack.getCount()) return TransportedResult.removeItem();
        TransportedItemStack rest = transported.copy();
        rest.stack = stack.copyWithCount(stack.getCount() - taken);
        return TransportedResult.convertTo(rest);
    }

    /**
     * Hands one rod at a time to the belt or depot underneath, or else to whatever
     * takes items from the side. A belt counts
     * only if it moves away from the refiner, so rods never ride back on the belt
     * that feeds it and jam its end.
     */
    private void pushOutput() {
        ItemStack output = inventory.getStackInSlot(OUTPUT);
        if (output.isEmpty() || level == null) return;
        DirectBeltInputBehaviour under = BlockEntityBehaviour.get(level, worldPosition.below(),
                DirectBeltInputBehaviour.TYPE);
        if (under != null && under.canInsertFromSide(Direction.DOWN)
                && under.handleInsertion(output.copyWithCount(1), Direction.DOWN, false).isEmpty()) {
            inventory.extractItem(OUTPUT, 1, false);
            return;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos target = worldPosition.relative(side);
            if (level.getBlockEntity(target) instanceof BeltBlockEntity belt && belt.getMovementFacing() != side) {
                continue;
            }
            DirectBeltInputBehaviour input = BlockEntityBehaviour.get(level, target, DirectBeltInputBehaviour.TYPE);
            if (input == null || !input.canInsertFromSide(side)) continue;
            ItemStack left = input.handleInsertion(output.copyWithCount(1), side, false);
            if (left.isEmpty()) {
                inventory.extractItem(OUTPUT, 1, false);
                return;
            }
        }
    }

    public boolean working() { return working; }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putLong("Progress", progress);
        tag.putBoolean("Heated", heated);
        if (clientPacket) {
            tag.putBoolean("Working", working);
            tag.putFloat("ProgressFraction", progressFraction);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        progress = Math.max(0L, tag.getLong("Progress"));
        heated = tag.getBoolean("Heated");
        if (clientPacket) {
            working = tag.getBoolean("Working");
            progressFraction = tag.getFloat("ProgressFraction");
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable(
                "goggles.moveearth_addtional.blaze_rod_refiner.title").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component.translatable(
                "goggles.moveearth_addtional.blaze_rod_refiner.progress",
                Math.round(progressFraction * 100.0F)).withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("    ").append(Component.translatable(heated
                ? "goggles.moveearth_addtional.blaze_rod_refiner.heated"
                : "goggles.moveearth_addtional.blaze_rod_refiner.not_heated")
                .withStyle(heated ? ChatFormatting.GREEN : ChatFormatting.RED)));
        int minRpm = NetherGateConfig.SPEC.isLoaded() ? NetherGateConfig.refinerMinRpm() : 48;
        if (Math.abs(getSpeed()) < minRpm) {
            tooltip.add(Component.literal("    ").append(Component.translatable(
                    "goggles.moveearth_addtional.blaze_rod_refiner.too_slow", minRpm).withStyle(ChatFormatting.RED)));
        }
        tooltip.add(Component.literal("    ").append(Component.translatable(
                "goggles.moveearth_addtional.blaze_rod_refiner.contents",
                inventory.getStackInSlot(STICK).getCount(), inventory.getStackInSlot(SHARD).getCount(),
                inventory.getStackInSlot(OUTPUT).getCount()).withStyle(ChatFormatting.GRAY)));
        return true;
    }
}
