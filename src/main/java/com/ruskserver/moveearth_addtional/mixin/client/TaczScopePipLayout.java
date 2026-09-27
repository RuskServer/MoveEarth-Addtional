package com.ruskserver.moveearth_addtional.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.model.BedrockAttachmentModel", remap = false)
public interface TaczScopePipLayout {
    @Accessor(value = "isScopeOcular", remap = false)
    List<Boolean> moveearth$ocularKinds();
}
