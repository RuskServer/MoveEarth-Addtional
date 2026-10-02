package com.ruskserver.moveearth_addtional.compat.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who asked for the assembly Sable is about to perform, and from where.
 *
 * <p>Sable's {@code assembleBlocks} only receives a block set and an arbitrary
 * first block of it as anchor. Simulated knows more: which player pulled the
 * Physics Assembler's lever and where the assembler stands. The Simulated
 * mixins record that here, on the server thread, for the duration of one
 * call; the Sable hook only trusts a request whose block collection is the
 * very instance it is handed.
 */
public final class AssemblyRequests {
    private static final ThreadLocal<ServerPlayer> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();

    private AssemblyRequests() { }

    /** @param blocks the collection Simulated passes on to Sable; matched by identity */
    public record Request(BlockPos origin, ServerPlayer actor, Iterable<BlockPos> blocks) { }

    public static void actor(ServerPlayer player) { ACTOR.set(player); }

    public static ServerPlayer actor() { return ACTOR.get(); }

    public static void clearActor() { ACTOR.remove(); }

    public static void open(Request request) { REQUEST.set(request); }

    /** The open request made for exactly {@code blocks}, if any. */
    public static Request forBlocks(Iterable<BlockPos> blocks) {
        Request request = REQUEST.get();
        return request != null && request.blocks() == blocks ? request : null;
    }

    /** Ends the request and forgets the actor: one lever pull is one assembly. */
    public static void close() {
        REQUEST.remove();
        ACTOR.remove();
    }
}
