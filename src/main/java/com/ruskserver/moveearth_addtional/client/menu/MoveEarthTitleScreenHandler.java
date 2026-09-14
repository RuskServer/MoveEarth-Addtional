package com.ruskserver.moveearth_addtional.client.menu;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class MoveEarthTitleScreenHandler {
    private MoveEarthTitleScreenHandler() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new MoveEarthTitleScreen());
        }
    }
}
