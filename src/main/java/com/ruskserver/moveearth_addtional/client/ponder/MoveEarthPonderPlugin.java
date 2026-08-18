package com.ruskserver.moveearth_addtional.client.ponder;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MoveEarthPonderPlugin implements PonderPlugin {
    private static final ResourceLocation TERRITORY_CORE =
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_core");
    private static final ResourceLocation SHAFT_RELAY_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath("create", "shaft/relay");

    @Override
    public String getModId() {
        return Moveearth_addtional.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(
                TERRITORY_CORE,
                SHAFT_RELAY_STRUCTURE,
                TerritoryCoreScenes::usage
        );
    }
}
