package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.chat.LocalChatPresentation;
import com.ruskserver.moveearth_addtional.chat.LocalChatRules;
import com.ruskserver.moveearth_addtional.config.LocalChatConfig;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/** Limits only ordinary player chat; vanilla keeps its signed-message logging and filtering. */
@Mixin(PlayerList.class)
public abstract class PlayerListLocalChatMixin {
    @Shadow @Final private List<ServerPlayer> players;

    @WrapOperation(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
    private void moveearth$deliverLocalChat(ServerPlayer recipient, OutgoingChatMessage outgoing,
                                            boolean filtered, ChatType.Bound bound, Operation<Void> original,
                                            PlayerChatMessage message, Predicate<ServerPlayer> filterPredicate,
                                            ServerPlayer sender, ChatType.Bound originalBound) {
        if (sender == null || !bound.chatType().is(ChatType.CHAT)) {
            original.call(recipient, outgoing, filtered, bound);
            return;
        }
        if (!LocalChatRules.canReceive(sender.level().dimension().equals(recipient.level().dimension()),
                sender.distanceToSqr(recipient), LocalChatConfig.radiusBlocks())) return;
        original.call(recipient, outgoing, filtered, LocalChatPresentation.bound(sender, recipient));
    }

    @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("TAIL"))
    private void moveearth$logLocalRecipients(PlayerChatMessage message,
                                               Predicate<ServerPlayer> filterPredicate,
                                               ServerPlayer sender, ChatType.Bound bound,
                                               CallbackInfo ci) {
        if (sender == null || !bound.chatType().is(ChatType.CHAT)) return;
        int radius = LocalChatConfig.radiusBlocks();
        List<String> recipients = players.stream()
                .filter(recipient -> LocalChatRules.canReceive(
                        sender.level().dimension().equals(recipient.level().dimension()),
                        sender.distanceToSqr(recipient), radius))
                .filter(recipient -> recipient.clientInformation().chatVisibility() == ChatVisiblity.FULL)
                .filter(recipient -> !message.filter(filterPredicate.test(recipient)).isFullyFiltered())
                .map(recipient -> recipient.getGameProfile().getName())
                .toList();
        Moveearth_addtional.LOGGER.info("[LocalChat] recipients for <{}>: [{}]",
                sender.getGameProfile().getName(), String.join(", ", recipients));
    }
}
