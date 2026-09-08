package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.oxygen.GasMaskItem;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CustomHeadLayer.class)
public abstract class CustomHeadLayerMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ArmorItem;getEquipmentSlot()Lnet/minecraft/world/entity/EquipmentSlot;"
            )
    )
    private EquipmentSlot moveearthAdditional$renderGasMaskAsHeadItem(ArmorItem armorItem) {
        if (armorItem instanceof GasMaskItem) {
            // CustomHeadLayer renders head-slot items with the baked item's
            // ItemDisplayContext.HEAD transform when they are not head armor.
            return EquipmentSlot.CHEST;
        }
        return armorItem.getEquipmentSlot();
    }
}
