package com.ruskserver.moveearth_addtional.compat.vehicle;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Set;

/**
 * Positions whose blocks Sable is currently moving (assembly, disassembly,
 * merge), so their removal from the source is not treated as the core being
 * broken. Nested moves stack; each {@link #end} closes the innermost one.
 */
public final class VehicleAssemblyGuard {
    private static final ThreadLocal<ArrayDeque<Set<BlockPos>>> MOVING = ThreadLocal.withInitial(ArrayDeque::new);
    private VehicleAssemblyGuard() { }

    public static void begin(Set<BlockPos> positions) { MOVING.get().push(Set.copyOf(positions)); }

    public static boolean isMoving(BlockPos pos) {
        for (Set<BlockPos> positions : MOVING.get()) {
            if (positions.contains(pos)) return true;
        }
        return false;
    }

    public static void end() {
        ArrayDeque<Set<BlockPos>> stack = MOVING.get();
        stack.poll();
        if (stack.isEmpty()) MOVING.remove();
    }
}
