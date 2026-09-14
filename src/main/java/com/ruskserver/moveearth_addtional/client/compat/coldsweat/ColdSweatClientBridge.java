package com.ruskserver.moveearth_addtional.client.compat.coldsweat;

import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.player.Player;

/** Direct optional API boundary. This class is loaded only when Cold Sweat is present. */
final class ColdSweatClientBridge {
    private ColdSweatClientBridge() { }

    static TemperatureSample sample(Player player) {
        double world = Temperature.get(player, Temperature.Trait.WORLD);
        double body = Temperature.get(player, Temperature.Trait.BODY);
        double freezing = Temperature.get(player, Temperature.Trait.FREEZING_POINT);
        double burning = Temperature.get(player, Temperature.Trait.BURNING_POINT);
        double celsius = Temperature.convert(world, Temperature.Units.MC, Temperature.Units.C, true);
        return new TemperatureSample(world, body, freezing, burning, celsius);
    }

    record TemperatureSample(double world, double body, double freezingPoint,
                             double burningPoint, double celsius) { }
}
