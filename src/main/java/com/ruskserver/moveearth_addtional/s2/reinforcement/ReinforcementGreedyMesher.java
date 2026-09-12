package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Combines coplanar exposed unit faces that share one visual state into rectangles. */
public final class ReinforcementGreedyMesher {
    private ReinforcementGreedyMesher() { }

    public static List<Quad> mesh(Collection<Cell> cells, Occupancy occupancy) {
        if (cells == null || cells.isEmpty()) return List.of();
        Map<PlaneKey, PrimitiveLongSet> planes = new LinkedHashMap<>();
        for (Cell cell : cells) {
            addIfExposed(planes, occupancy, cell, Face.NORTH, 0, 0, -1,
                    cell.z, cell.x, cell.y);
            addIfExposed(planes, occupancy, cell, Face.SOUTH, 0, 0, 1,
                    cell.z + 1, cell.x, cell.y);
            addIfExposed(planes, occupancy, cell, Face.WEST, -1, 0, 0,
                    cell.x, cell.z, cell.y);
            addIfExposed(planes, occupancy, cell, Face.EAST, 1, 0, 0,
                    cell.x + 1, cell.z, cell.y);
            addIfExposed(planes, occupancy, cell, Face.DOWN, 0, -1, 0,
                    cell.y, cell.x, cell.z);
            addIfExposed(planes, occupancy, cell, Face.UP, 0, 1, 0,
                    cell.y + 1, cell.x, cell.z);
        }

        List<Quad> result = new ArrayList<>();
        for (Map.Entry<PlaneKey, PrimitiveLongSet> plane : planes.entrySet()) {
            PrimitiveLongSet remaining = plane.getValue();
            long[] ordered = remaining.toLongArray();
            Arrays.sort(ordered);
            for (long first : ordered) {
                if (!remaining.remove(first)) continue;
                int startU = u(first);
                int startV = v(first);
                int width = 1;
                while (remaining.contains(uv(startU + width, startV))) width++;
                int height = 1;
                while (rowPresent(remaining, startU, startV + height, width)) height++;
                for (int dv = 0; dv < height; dv++) {
                    for (int du = 0; du < width; du++) remaining.remove(uv(startU + du, startV + dv));
                }
                result.add(toQuad(plane.getKey(), startU, startV, width, height));
            }
        }
        return List.copyOf(result);
    }

    private static void addIfExposed(Map<PlaneKey, PrimitiveLongSet> planes, Occupancy occupancy,
                                     Cell cell, Face face, int dx, int dy, int dz,
                                     int plane, int u, int v) {
        if (occupancy.contains(cell.x + dx, cell.y + dy, cell.z + dz)) return;
        planes.computeIfAbsent(new PlaneKey(face, plane, cell.state),
                ignored -> new PrimitiveLongSet()).add(uv(u, v));
    }

    private static boolean rowPresent(PrimitiveLongSet remaining, int startU, int rowV, int width) {
        for (int du = 0; du < width; du++) {
            if (!remaining.contains(uv(startU + du, rowV))) return false;
        }
        return true;
    }

    private static Quad toQuad(PlaneKey key, int u, int v, int width, int height) {
        return switch (key.face) {
            case NORTH -> new Quad(key.face, u, v, key.plane, width, height, 1, key.state);
            case SOUTH -> new Quad(key.face, u, v, key.plane - 1, width, height, 1, key.state);
            case WEST -> new Quad(key.face, key.plane, v, u, 1, height, width, key.state);
            case EAST -> new Quad(key.face, key.plane - 1, v, u, 1, height, width, key.state);
            case DOWN -> new Quad(key.face, u, key.plane, v, width, 1, height, key.state);
            case UP -> new Quad(key.face, u, key.plane - 1, v, width, 1, height, key.state);
        };
    }

    private static long uv(int u, int v) {
        return ((long) (v ^ Integer.MIN_VALUE) << 32)
                | ((u ^ Integer.MIN_VALUE) & 0xffffffffL);
    }

    private static int u(long packed) { return ((int) packed) ^ Integer.MIN_VALUE; }
    private static int v(long packed) { return ((int) (packed >> 32)) ^ Integer.MIN_VALUE; }

    public enum Face { NORTH, SOUTH, WEST, EAST, DOWN, UP }

    public record Cell(int x, int y, int z, long state) { }

    /** Origin is the owning block-space corner; size is one on the face-normal axis. */
    public record Quad(Face face, int x, int y, int z,
                       int sizeX, int sizeY, int sizeZ, long state) { }

    @FunctionalInterface
    public interface Occupancy {
        boolean contains(int x, int y, int z);
    }

    private record PlaneKey(Face face, int plane, long state) { }

    /** Minimal open-addressed set used here to avoid allocating one Long wrapper per exposed face. */
    private static final class PrimitiveLongSet {
        private long[] keys = new long[16];
        private byte[] states = new byte[16];
        private int size;

        boolean add(long key) {
            if ((size + 1) * 10 >= keys.length * 7) rehash(keys.length << 1);
            int slot = findSlot(key);
            if (states[slot] == 1) return false;
            keys[slot] = key;
            states[slot] = 1;
            size++;
            return true;
        }

        boolean contains(long key) {
            int mask = keys.length - 1;
            int slot = mix(key) & mask;
            while (states[slot] != 0) {
                if (states[slot] == 1 && keys[slot] == key) return true;
                slot = (slot + 1) & mask;
            }
            return false;
        }

        boolean remove(long key) {
            int mask = keys.length - 1;
            int slot = mix(key) & mask;
            while (states[slot] != 0) {
                if (states[slot] == 1 && keys[slot] == key) {
                    states[slot] = 2;
                    size--;
                    return true;
                }
                slot = (slot + 1) & mask;
            }
            return false;
        }

        long[] toLongArray() {
            long[] result = new long[size];
            int target = 0;
            for (int index = 0; index < keys.length; index++) {
                if (states[index] == 1) result[target++] = keys[index];
            }
            return result;
        }

        private int findSlot(long key) {
            int mask = keys.length - 1;
            int slot = mix(key) & mask;
            int deleted = -1;
            while (states[slot] != 0) {
                if (states[slot] == 1 && keys[slot] == key) return slot;
                if (states[slot] == 2 && deleted < 0) deleted = slot;
                slot = (slot + 1) & mask;
            }
            return deleted >= 0 ? deleted : slot;
        }

        private void rehash(int capacity) {
            long[] oldKeys = keys;
            byte[] oldStates = states;
            keys = new long[capacity];
            states = new byte[capacity];
            size = 0;
            for (int index = 0; index < oldKeys.length; index++) {
                if (oldStates[index] == 1) add(oldKeys[index]);
            }
        }

        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            return (int) value;
        }
    }
}
