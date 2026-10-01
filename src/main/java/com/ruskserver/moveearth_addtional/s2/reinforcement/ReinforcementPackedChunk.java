package com.ruskserver.moveearth_addtional.s2.reinforcement;

import java.util.Arrays;
import java.util.List;

/**
 * Columnar save codec for the reinforcements of one chunk column (save format version 2).
 *
 * <p>One primitive array per field instead of one compound tag per block keeps autosave cheap with
 * 100k+ reinforced blocks. Materials are stored as palette indices; the palette itself is written once
 * per save as stable string ids, so reordering {@link ReinforcementMaterial} never corrupts a world.
 * Construction timers are sparse (most armor is finished), so only timed rows carry them.
 * Deliberately free of Minecraft types so the format is unit-testable.
 */
public final class ReinforcementPackedChunk {
    public static final int FORMAT_VERSION = 2;
    private static final byte FLAG_ENABLED = 1;

    private ReinforcementPackedChunk() { }

    /** Raw arrays exactly as stored. Positions are {@code BlockPos#asLong} values. */
    public record Columns(long[] positions, byte[] materials, int[] durability, byte[] flags,
                          int[] timerIndices, long[] startedAt, long[] activatesAt) {
        public Columns {
            positions = positions == null ? new long[0] : positions;
            materials = materials == null ? new byte[0] : materials;
            durability = durability == null ? new int[0] : durability;
            flags = flags == null ? new byte[0] : flags;
            timerIndices = timerIndices == null ? new int[0] : timerIndices;
            startedAt = startedAt == null ? new long[0] : startedAt;
            activatesAt = activatesAt == null ? new long[0] : activatesAt;
        }

        public int size() {
            return positions.length;
        }
    }

    @FunctionalInterface
    public interface Sink {
        void accept(long pos, ReinforcementEntry entry);
    }

    /** Material palette, written as the list of material ids in index order. */
    public static final class Palette {
        private final ReinforcementMaterial[] byIndex;
        private final int[] indexByOrdinal;

        private Palette(ReinforcementMaterial[] byIndex) {
            this.byIndex = byIndex;
            this.indexByOrdinal = new int[ReinforcementMaterial.values().length];
            Arrays.fill(indexByOrdinal, -1);
            for (int index = 0; index < byIndex.length; index++) {
                if (indexByOrdinal[byIndex[index].ordinal()] < 0) indexByOrdinal[byIndex[index].ordinal()] = index;
            }
        }

        /** Palette used for writing: every current material, in declaration order. */
        public static Palette current() {
            return new Palette(ReinforcementMaterial.values());
        }

        /** Unknown ids fall back to cobblestone, matching the legacy per-entry loader. */
        public static Palette read(List<String> ids) {
            ReinforcementMaterial[] materials = new ReinforcementMaterial[ids.size()];
            for (int index = 0; index < materials.length; index++) {
                materials[index] = ReinforcementMaterial.fromId(ids.get(index));
            }
            return new Palette(materials);
        }

        public List<String> ids() {
            return Arrays.stream(byIndex).map(ReinforcementMaterial::id).toList();
        }

        int indexOf(ReinforcementMaterial material) {
            int index = indexByOrdinal[material.ordinal()];
            if (index < 0) throw new IllegalArgumentException("Material missing from palette: " + material);
            return index;
        }

        ReinforcementMaterial at(int index) {
            return index >= 0 && index < byIndex.length ? byIndex[index] : ReinforcementMaterial.COBBLESTONE;
        }
    }

    /** Accumulates rows of one chunk without per-row allocation. */
    public static final class Writer {
        private final Palette palette;
        private long[] positions;
        private byte[] materials;
        private int[] durability;
        private byte[] flags;
        private int size;
        private int[] timerIndices = new int[0];
        private long[] startedAt = new long[0];
        private long[] activatesAt = new long[0];
        private int timers;

        public Writer(Palette palette, int expectedSize) {
            this.palette = palette;
            int capacity = Math.max(1, expectedSize);
            positions = new long[capacity];
            materials = new byte[capacity];
            durability = new int[capacity];
            flags = new byte[capacity];
        }

        public void add(long pos, ReinforcementEntry entry) {
            if (size == positions.length) {
                int capacity = size * 2;
                positions = Arrays.copyOf(positions, capacity);
                materials = Arrays.copyOf(materials, capacity);
                durability = Arrays.copyOf(durability, capacity);
                flags = Arrays.copyOf(flags, capacity);
            }
            positions[size] = pos;
            materials[size] = (byte) palette.indexOf(entry.material());
            durability[size] = entry.durability();
            flags[size] = entry.enabled() ? FLAG_ENABLED : 0;
            if (entry.constructionStartedAt() != 0L || entry.activatesAt() != 0L) {
                if (timers == timerIndices.length) {
                    int capacity = Math.max(4, timers * 2);
                    timerIndices = Arrays.copyOf(timerIndices, capacity);
                    startedAt = Arrays.copyOf(startedAt, capacity);
                    activatesAt = Arrays.copyOf(activatesAt, capacity);
                }
                timerIndices[timers] = size;
                startedAt[timers] = entry.constructionStartedAt();
                activatesAt[timers] = entry.activatesAt();
                timers++;
            }
            size++;
        }

        public int size() {
            return size;
        }

        public Columns finish() {
            return new Columns(Arrays.copyOf(positions, size), Arrays.copyOf(materials, size),
                    Arrays.copyOf(durability, size), Arrays.copyOf(flags, size),
                    Arrays.copyOf(timerIndices, timers), Arrays.copyOf(startedAt, timers),
                    Arrays.copyOf(activatesAt, timers));
        }
    }

    /**
     * Decodes one chunk. Truncated or mismatched arrays (hand-edited or damaged data) are read up to the
     * shortest complete column instead of failing the whole level load.
     */
    public static int decode(Columns columns, Palette palette, Sink sink) {
        int count = Math.min(Math.min(columns.positions().length, columns.materials().length),
                Math.min(columns.durability().length, columns.flags().length));
        long[] started = new long[count];
        long[] activates = new long[count];
        int timers = Math.min(columns.timerIndices().length,
                Math.min(columns.startedAt().length, columns.activatesAt().length));
        for (int timer = 0; timer < timers; timer++) {
            int row = columns.timerIndices()[timer];
            if (row < 0 || row >= count) continue;
            started[row] = columns.startedAt()[timer];
            activates[row] = columns.activatesAt()[timer];
        }
        for (int row = 0; row < count; row++) {
            ReinforcementMaterial material = palette.at(columns.materials()[row] & 0xFF);
            sink.accept(columns.positions()[row], new ReinforcementEntry(material, columns.durability()[row],
                    (columns.flags()[row] & FLAG_ENABLED) != 0, started[row], activates[row]));
        }
        return count;
    }

    /** Format version 1 (one compound per block); kept so pre-v3.3 test worlds still load. */
    public static ReinforcementEntry legacyEntry(String materialId, int durability, boolean enabled,
                                                 boolean hasConstructionTiming, long startedAt, long activatesAt) {
        return new ReinforcementEntry(ReinforcementMaterial.fromId(materialId), durability, enabled,
                hasConstructionTiming ? startedAt : 0L, hasConstructionTiming ? activatesAt : 0L);
    }
}
