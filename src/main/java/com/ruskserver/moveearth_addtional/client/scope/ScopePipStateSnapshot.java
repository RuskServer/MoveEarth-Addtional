package com.ruskserver.moveearth_addtional.client.scope;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

final class ScopePipStateSnapshot implements AutoCloseable {
    private record Entry(Field field, Object owner, Object value) { }
    private final List<Entry> entries = new ArrayList<>();

    void capture(Object owner, Class<?> type, boolean staticFields) throws ReflectiveOperationException {
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isFinal(modifiers) || Modifier.isStatic(modifiers) != staticFields) continue;
            field.setAccessible(true);
            Object value = field.get(owner);
            if (value instanceof Matrix4fc matrix) value = new Matrix4f(matrix);
            else if (value instanceof Vector3d vector) value = new Vector3d(vector);
            entries.add(new Entry(field, owner, value));
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (int index = entries.size() - 1; index >= 0; index--) {
            Entry entry = entries.get(index);
            try {
                entry.field().set(entry.owner(), entry.value());
            } catch (IllegalAccessException exception) {
                if (failure == null) failure = new IllegalStateException("Cannot restore scope rendering state", exception);
                else failure.addSuppressed(exception);
            }
        }
        entries.clear();
        if (failure != null) throw failure;
    }
}
