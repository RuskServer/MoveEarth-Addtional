package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.combat.RealPlayers;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Bed/anchor explosions, buckets and tools retain their actual user's identity. */
@Mixin(ServerPlayerGameMode.class)
public abstract class WorldMutationPlayerUseMixin {
    @WrapMethod(method = "useItemOn")
    private InteractionResult moveearth$itemOnOrigin(ServerPlayer player, Level level, ItemStack stack,
                                                      InteractionHand hand, BlockHitResult hit,
                                                      Operation<InteractionResult> original) {
        Object origin = RealPlayers.real(player) == null && ActionContext.current() != null
                ? ActionContext.current() : player;
        try (var scope = ActionContext.push(origin)) { return original.call(player, level, stack, hand, hit); }
    }
    @WrapMethod(method = "useItem")
    private InteractionResult moveearth$itemOrigin(ServerPlayer player, Level level, ItemStack stack,
                                                    InteractionHand hand, Operation<InteractionResult> original) {
        Object origin = RealPlayers.real(player) == null && ActionContext.current() != null
                ? ActionContext.current() : player;
        try (var scope = ActionContext.push(origin)) { return original.call(player, level, stack, hand); }
    }
}
