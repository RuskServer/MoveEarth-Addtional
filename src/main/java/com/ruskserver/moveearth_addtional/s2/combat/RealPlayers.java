package com.ruskserver.moveearth_addtional.s2.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Who gets credit for damage and kills. Machines such as Create deployers and
 * sentry turrets act through fake players; they are never an attacking player,
 * so they cannot clear player-only fights, count as combat or earn kill credit.
 */
public final class RealPlayers {
    private RealPlayers() { }

    /** The player behind a damage source, or null for anything else, fake players included. */
    public static ServerPlayer attacker(DamageSource source) {
        return real(source.getEntity());
    }

    /** {@code entity} as a real player, or null. */
    public static ServerPlayer real(Entity entity) {
        return entity instanceof ServerPlayer player && !(player instanceof FakePlayer) ? player : null;
    }
}
