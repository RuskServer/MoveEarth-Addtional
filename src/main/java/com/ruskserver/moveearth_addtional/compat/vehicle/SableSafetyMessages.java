package com.ruskserver.moveearth_addtional.compat.vehicle;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Old clients use the fallback; updated clients can still use their own translations. */
public final class SableSafetyMessages {
    private SableSafetyMessages() { }

    public static MutableComponent component(SableSafetyMessageCatalog.Message message, ServerPlayer recipient,
                                             Object... arguments) {
        return localized(message, recipient == null ? "ja_jp" : recipient.clientInformation().language(), arguments);
    }

    static MutableComponent localized(SableSafetyMessageCatalog.Message message, String language, Object... arguments) {
        return Component.translatableWithFallback(message.key(),
                SableSafetyMessageCatalog.template(message, language), arguments);
    }
}
