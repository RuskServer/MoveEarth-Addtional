package com.ruskserver.moveearth_addtional.chat;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Identifies player chat that another integration has forwarded as a system message. */
public final class LocalChatRelay {
    private static final Pattern VANILLA_LINE = Pattern.compile("^<([A-Za-z0-9_]{3,16})> ");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private LocalChatRelay() { }

    public static Optional<String> senderName(Component message) {
        if (message.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("chat.type.text") && translated.getArgs().length == 2) {
            Object name = translated.getArgs()[0];
            String value = name instanceof Component component ? component.getString() : String.valueOf(name);
            return USERNAME.matcher(value).matches() ? Optional.of(value) : Optional.empty();
        }
        if (message.getContents() instanceof PlainTextContents.LiteralContents literal) {
            return senderNameFromVanillaLine(literal.text());
        }
        return Optional.empty();
    }

    static Optional<String> senderNameFromVanillaLine(String line) {
        Matcher match = VANILLA_LINE.matcher(line);
        return match.find() ? Optional.of(match.group(1)) : Optional.empty();
    }
}
