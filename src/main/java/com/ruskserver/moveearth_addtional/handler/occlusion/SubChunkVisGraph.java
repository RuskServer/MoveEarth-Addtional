package com.ruskserver.moveearth_addtional.handler.occlusion;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Queue;
import java.util.Set;

/**
 * 16×16×16 のサブチャンクにおける外周6面（DOWN, UP, NORTH, SOUTH, WEST, EAST）間の
 * 視線透過連結性（VisGraph）を計算・解析するクラス。
 */
public class SubChunkVisGraph {

    public static final int CHUNK_SIZE = 16;
    public static final int TOTAL_VOXELS = 4096; // 16 * 16 * 16

    /**
     * 全面が完全に全通している場合のマスク（空のサブチャンク等）
     */
    public static final long ALL_OPEN_MASK = computeAllOpenMask();

    /**
     * 全面が完全に遮蔽されている場合のマスク（中身が詰まった岩盤層等）
     */
    public static final long ALL_CLOSED_MASK = 0L;

    private static final Direction[] DIRECTIONS = Direction.values();

    private static long computeAllOpenMask() {
        long mask = 0L;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                mask |= (1L << (i * 6 + j));
            }
        }
        return mask;
    }

    /**
     * 指定した2面間で視線が通り抜け可能か判定します。
     *
     * @param mask サブチャンクの透過ビットマスク
     * @param from 入射面
     * @param to   出射面
     * @return 接続されている場合 true
     */
    public static boolean isConnected(long mask, Direction from, Direction to) {
        return isConnected(mask, from.ordinal(), to.ordinal());
    }

    /**
     * 指定した2面のインデックス（0:DOWN, 1:UP, 2:NORTH, 3:SOUTH, 4:WEST, 5:EAST）間で
     * 視線が通り抜け可能か判定します。
     */
    public static boolean isConnected(long mask, int fromOrdinal, int toOrdinal) {
        if (mask == ALL_OPEN_MASK) return true;
        if (mask == ALL_CLOSED_MASK) return false;
        return (mask & (1L << (fromOrdinal * 6 + toOrdinal))) != 0L;
    }

    /**
     * LevelChunkSection から透過ビットマスクを計算します。
     *
     * @param section 対象のサブチャンクセクション
     * @return 6面間の接続を表すビットマスク
     */
    public static long computeVisibilityMask(LevelChunkSection section) {
        if (section == null || section.hasOnlyAir()) {
            return ALL_OPEN_MASK;
        }

        PalettedContainer<BlockState> states = section.getStates();
        BitSet opaqueVoxels = new BitSet(TOTAL_VOXELS);
        int opaqueCount = 0;

        for (int y = 0; y < CHUNK_SIZE; y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    BlockState state = states.get(x, y, z);
                    if (state.canOcclude()) {
                        opaqueVoxels.set(getIndex(x, y, z));
                        opaqueCount++;
                    }
                }
            }
        }

        // 遮蔽ブロックが0個なら全通
        if (opaqueCount == 0) {
            return ALL_OPEN_MASK;
        }
        // 4096個すべて遮蔽ブロックなら全遮蔽
        if (opaqueCount == TOTAL_VOXELS) {
            return ALL_CLOSED_MASK;
        }

        return floodFillSection(opaqueVoxels);
    }

    /**
     * 透過ボクセルのフラッドフィルを行い、面間の接続性を抽出します。
     */
    private static long floodFillSection(BitSet opaqueVoxels) {
        long resultMask = 0L;
        BitSet visited = new BitSet(TOTAL_VOXELS);
        int[] queue = new int[TOTAL_VOXELS];

        for (int i = 0; i < TOTAL_VOXELS; i++) {
            if (opaqueVoxels.get(i) || visited.get(i)) {
                continue;
            }

            // 新しい透過連結成分の探索開始
            int head = 0;
            int tail = 0;
            queue[tail++] = i;
            visited.set(i);

            int connectedFacesMask = 0;

            while (head < tail) {
                int index = queue[head++];
                int x = getX(index);
                int y = getY(index);
                int z = getZ(index);

                // 外周面に接しているかチェック
                if (y == 0) connectedFacesMask |= (1 << Direction.DOWN.ordinal());
                if (y == 15) connectedFacesMask |= (1 << Direction.UP.ordinal());
                if (z == 0) connectedFacesMask |= (1 << Direction.NORTH.ordinal());
                if (z == 15) connectedFacesMask |= (1 << Direction.SOUTH.ordinal());
                if (x == 0) connectedFacesMask |= (1 << Direction.WEST.ordinal());
                if (x == 15) connectedFacesMask |= (1 << Direction.EAST.ordinal());

                // 6方向の隣接ボクセルをキューに追加
                checkNeighbor(x + 1, y, z, opaqueVoxels, visited, queue, tail);
                if (isValidNeighbor(x + 1, y, z, opaqueVoxels, visited)) {
                    visited.set(getIndex(x + 1, y, z));
                    queue[tail++] = getIndex(x + 1, y, z);
                }
                if (isValidNeighbor(x - 1, y, z, opaqueVoxels, visited)) {
                    visited.set(getIndex(x - 1, y, z));
                    queue[tail++] = getIndex(x - 1, y, z);
                }
                if (isValidNeighbor(x, y + 1, z, opaqueVoxels, visited)) {
                    visited.set(getIndex(x, y + 1, z));
                    queue[tail++] = getIndex(x, y + 1, z);
                }
                if (isValidNeighbor(x, y - 1, z, opaqueVoxels, visited)) {
                    visited.set(getIndex(x, y - 1, z));
                    queue[tail++] = getIndex(x, y - 1, z);
                }
                if (isValidNeighbor(x, y, z + 1, opaqueVoxels, visited)) {
                    visited.set(getIndex(x, y, z + 1));
                    queue[tail++] = getIndex(x, y, z + 1);
                }
                if (isValidNeighbor(x, y, z - 1, opaqueVoxels, visited)) {
                    visited.set(getIndex(x, y, z - 1));
                    queue[tail++] = getIndex(x, y, z - 1);
                }
            }

            // この連結成分で接続された面ペアをマスクに反映
            for (int f1 = 0; f1 < 6; f1++) {
                if ((connectedFacesMask & (1 << f1)) != 0) {
                    for (int f2 = 0; f2 < 6; f2++) {
                        if ((connectedFacesMask & (1 << f2)) != 0) {
                            resultMask |= (1L << (f1 * 6 + f2));
                        }
                    }
                }
            }
        }

        return resultMask;
    }

    private static boolean isValidNeighbor(int x, int y, int z, BitSet opaque, BitSet visited) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return false;
        }
        int index = getIndex(x, y, z);
        return !opaque.get(index) && !visited.get(index);
    }

    private static void checkNeighbor(int x, int y, int z, BitSet opaque, BitSet visited, int[] queue, int tail) {
        // dummy for inlining if needed
    }

    public static int getIndex(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    public static int getX(int index) {
        return index & 0xF;
    }

    public static int getY(int index) {
        return (index >> 8) & 0xF;
    }

    public static int getZ(int index) {
        return (index >> 4) & 0xF;
    }
}
