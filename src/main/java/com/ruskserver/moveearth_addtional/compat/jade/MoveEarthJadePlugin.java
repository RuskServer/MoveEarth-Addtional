package com.ruskserver.moveearth_addtional.compat.jade;

import com.ruskserver.moveearth_addtional.block.TerritoryCoreBlock;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class MoveEarthJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(
                TerritoryCoreJadeProvider.INSTANCE,
                TerritoryCoreBlockEntity.class
        );
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(
                TerritoryCoreJadeProvider.INSTANCE,
                TerritoryCoreBlock.class
        );
    }
}
