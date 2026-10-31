package com.ruskserver.moveearth_addtional.compat.paintings;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;

/**
 * Immersive Paintings paintings take no harm from TaCZ guns: a stray shot in a firefight would otherwise tear
 * down whatever hangs on the walls. Other ways of breaking a painting are unchanged.
 *
 * <p>Both a bullet's hit and its explosive round's blast name the bullet as the direct cause, so the one check
 * covers both. The painting is only made immune to the damage, not taken out of the shot: the bullet still
 * stops there and an explosive round still goes off, so hanging paintings cannot be used to soak up rockets.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PaintingGunProof {
    private static final String NAMESPACE = "immersive_paintings";

    private PaintingGunProof() {
    }

    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.getSource().getDirectEntity() instanceof EntityKineticBullet
                && NAMESPACE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).getNamespace())) {
            event.setInvulnerable(true);
        }
    }
}
