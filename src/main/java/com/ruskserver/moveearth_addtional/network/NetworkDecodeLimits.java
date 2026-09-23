package com.ruskserver.moveearth_addtional.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Shared validation for client-controlled collection sizes. */
public final class NetworkDecodeLimits {
    private NetworkDecodeLimits() {
    }

    public static int checkedCount(int count, int maximum, String field) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        }
        return count;
    }

    public static <T> List<T> readList(int count, int maximum, String field, Supplier<T> reader) {
        int checked = checkedCount(count, maximum, field);
        List<T> values = new ArrayList<>(checked);
        for (int index = 0; index < checked; index++) {
            values.add(reader.get());
        }
        return values;
    }
}
