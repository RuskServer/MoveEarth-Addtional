package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.chat.LocalChatPresentation;
import com.ruskserver.moveearth_addtional.chat.LocalChatRelay;
import com.ruskserver.moveearth_addtional.chat.LocalChatRoute;
import com.ruskserver.moveearth_addtional.chat.LocalChatRules;
import com.ruskserver.moveearth_addtional.config.LocalChatConfig;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies local chat to player chat that reaches a player without passing through the player list.
 *
 * <p>On the server, Paper's chat handling sends a message to each recipient itself, rather than through
 * {@code PlayerList.broadcastChatMessage}, once a plugin has looked at the recipients of a chat event, and
 * a plugin that changes the chat format turns the message into Paper's raw chat type with the name already
 * in the text. Either way the message arrived from any distance as a plain {@code <name>} line. Chat the
 * player list already delivered carries the local chat type and passes through unchanged; ordinary or raw
 * chat from an online player is limited to the radius and given the local decoration here.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerLocalChatMixin {
    private static final ResourceLocation PAPER_RAW = ResourceLocation.withDefaultNamespace("raw");

    @Shadow public abstract void sendChatMessage(OutgoingChatMessage message, boolean filtered, ChatType.Bound bound);

    @Shadow private boolean acceptsChatMessages() { throw new AssertionError(); }

    @Inject(method = "sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"), cancellable = true)
    private void moveearth$localiseDirectChat(OutgoingChatMessage outgoing, boolean filtered, ChatType.Bound bound,
                                              CallbackInfo ci) {
        if (!(outgoing instanceof OutgoingChatMessage.Player chat)) return;
        boolean plain = bound.chatType().is(ChatType.CHAT);
        boolean raw = !plain && bound.chatType().unwrapKey().map(key -> key.location().equals(PAPER_RAW)).orElse(false);
        if (!plain && !raw) return;
        ServerPlayer recipient = (ServerPlayer) (Object) this;
        ServerPlayer sender = recipient.server.getPlayerList().getPlayer(chat.message().sender());
        if (sender == null) return;
        ci.cancel();
        LocalChatRoute.noticed(plain ? "direct" : "raw", sender, recipient);
        if (!LocalChatRules.canReceive(sender.level().dimension().equals(recipient.level().dimension()),
                sender.distanceToSqr(recipient), LocalChatConfig.radiusBlocks())) return;
        if (plain) {
            sendChatMessage(outgoing, filtered, LocalChatPresentation.bound(sender, recipient));
            return;
        }
        // The raw text already holds the plugin's rendering of the name; send the player's own words instead.
        PlayerChatMessage words = chat.message().removeUnsignedContent().filter(filtered);
        if (acceptsChatMessages() && !words.isFullyFiltered()) {
            recipient.connection.sendPlayerChatMessage(words, LocalChatPresentation.bound(sender, recipient));
        }
    }

    /** Player chat another integration forwarded as a plain {@code <name> text} system line. */
    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V", at = @At("HEAD"), cancellable = true)
    private void moveearth$filterRelayedChat(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        ServerPlayer recipient = (ServerPlayer) (Object) this;
        LocalChatRelay.senderName(message).ifPresent(name -> {
            ServerPlayer sender = recipient.server.getPlayerList().getPlayerByName(name);
            if (sender == null) return;
            LocalChatRoute.noticed("system", sender, recipient);
            if (!LocalChatRules.canReceive(
                    sender.level().dimension().equals(recipient.level().dimension()),
                    sender.distanceToSqr(recipient), LocalChatConfig.radiusBlocks())) ci.cancel();
        });
    }
}
