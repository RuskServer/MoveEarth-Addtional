package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.scope.ScopePipIrisBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class IrisScopePipPipelineMixin {
    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void moveearth$selectLensPipeline(CallbackInfoReturnable<Object> callback) {
        Object pipeline = ScopePipIrisBridge.selectPipeline();
        if (pipeline != null) callback.setReturnValue(pipeline);
    }
}
