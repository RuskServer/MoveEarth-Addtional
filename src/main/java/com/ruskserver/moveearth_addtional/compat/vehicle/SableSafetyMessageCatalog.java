package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Server-side warning templates, also usable without booting Minecraft. */
public final class SableSafetyMessageCatalog {
    public enum Message {
        ASSEMBLY_REFUSED("body_density.assembly_refused"),
        SPLIT_DEFERRED("body_density.split_deferred"),
        DEBRIS_WARNING("body_density.debris_warning"),
        VOID_RESCUED("sable_void_rescued"),
        VOID_SUSPENDED("sable_void_rescue_suspended");

        private final String key;
        Message(String path) { key = "message.moveearth_addtional." + path; }
        public String key() { return key; }
    }

    private static final Map<String, String> JAPANESE = load("ja_jp");
    private static final Map<String, String> ENGLISH = load("en_us");

    private SableSafetyMessageCatalog() { }

    public static String template(Message message, String language) {
        // No client-supplied language string is ever used as a filesystem/resource path.
        boolean japanese = language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("ja_");
        return (japanese ? JAPANESE : ENGLISH).get(message.key());
    }

    private static Map<String, String> load(String language) {
        String path = "/assets/moveearth_addtional/lang/" + language + ".json";
        try (var input = SableSafetyMessageCatalog.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing safety message resource: " + path);
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> messages = new HashMap<>();
            for (Message message : Message.values()) {
                if (!json.has(message.key())) throw new IllegalStateException("Missing safety message: " + message.key());
                messages.put(message.key(), json.get(message.key()).getAsString());
            }
            return Map.copyOf(messages);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read safety message resource: " + path, error);
        }
    }
}
