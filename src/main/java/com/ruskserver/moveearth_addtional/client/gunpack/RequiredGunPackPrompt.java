package com.ruskserver.moveearth_addtional.client.gunpack;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

public final class RequiredGunPackPrompt {
    private static boolean checkedThisSession;

    private RequiredGunPackPrompt() {
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (checkedThisSession || !(event.getNewScreen() instanceof TitleScreen titleScreen)) {
            return;
        }
        checkedThisSession = true;
        try {
            List<RequiredGunPack> missing = RequiredGunPackService.findMissing();
            if (!missing.isEmpty()) {
                event.setNewScreen(new RequiredGunPackScreen(titleScreen, missing));
            }
        } catch (RuntimeException exception) {
            Moveearth_addtional.LOGGER.warn("Could not check required TaCZ GunPacks", exception);
        }
    }
}
