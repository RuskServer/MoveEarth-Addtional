package com.ruskserver.moveearth_addtional.region.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pulls every block id out of a feature written as JSON.
 *
 * <p>Separated from the rest so it can be tested without a server. Everything
 * it does can fail quietly — a nesting level it forgets to walk, a key it stops
 * at — and the only symptom would be an ore feature reported as placing no ore,
 * which is indistinguishable from a feature that really places none. That is
 * the failure this whole system keeps producing, so this part is tested against
 * shapes rather than trusted.
 *
 * <p>It knows nothing about feature configs. A block id is a string the caller
 * recognises, wherever it sits, and that is the whole rule; everything else in
 * the document — tag names, rule types, state values, numbers — is passed over
 * because the caller does not recognise it.
 */
public final class JsonBlockScan {

    private JsonBlockScan() { }

    /**
     * Block ids in the order the document writes them, without repeats.
     *
     * <p>Order carries meaning for the caller: a config names what it places
     * near where it names what it replaces, and first-that-resolves is how the
     * ore is told from the stone around it.
     *
     * @param isBlockId recognises a string as a block the game knows
     */
    public static List<String> blockIds(JsonElement root, Predicate<String> isBlockId) {
        Set<String> found = new LinkedHashSet<>();
        collect(root, isBlockId, found);
        return List.copyOf(found);
    }

    private static void collect(JsonElement element, Predicate<String> isBlockId, Set<String> out) {
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collect(entry.getValue(), isBlockId, out);
            }
        } else if (element instanceof JsonArray array) {
            for (JsonElement child : array) {
                collect(child, isBlockId, out);
            }
        } else if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            String value = primitive.getAsString();
            if (isBlockId.test(value)) {
                out.add(value);
            }
        }
    }
}
