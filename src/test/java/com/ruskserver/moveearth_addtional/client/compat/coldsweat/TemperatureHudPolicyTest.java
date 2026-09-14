package com.ruskserver.moveearth_addtional.client.compat.coldsweat;

import org.junit.jupiter.api.Test;

import static com.ruskserver.moveearth_addtional.client.compat.coldsweat.TemperatureHudPolicy.ThermalStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TemperatureHudPolicyTest {
    @Test
    void keepsTheMiddleOfThePersonalRangeComfortable() {
        assertEquals(COMFORTABLE, TemperatureHudPolicy.classify(1.1D, 0.0D, 0.5D, 1.7D));
    }

    @Test
    void increasesColdWarningsAtAndBelowTheFreezingPoint() {
        assertEquals(COLD, TemperatureHudPolicy.classify(0.7D, -10.0D, 0.5D, 1.7D));
        assertEquals(SEVERE_COLD, TemperatureHudPolicy.classify(0.55D, -40.0D, 0.5D, 1.7D));
        assertEquals(EXTREME_COLD, TemperatureHudPolicy.classify(0.5D, -90.0D, 0.5D, 1.7D));
    }

    @Test
    void increasesHeatWarningsAtAndAboveTheBurningPoint() {
        assertEquals(WARM, TemperatureHudPolicy.classify(1.5D, 10.0D, 0.5D, 1.7D));
        assertEquals(HOT, TemperatureHudPolicy.classify(1.65D, 40.0D, 0.5D, 1.7D));
        assertEquals(EXTREME_HEAT, TemperatureHudPolicy.classify(1.7D, 90.0D, 0.5D, 1.7D));
    }

    @Test
    void rejectsMissingOrInvalidCapabilityValues() {
        assertEquals(UNAVAILABLE, TemperatureHudPolicy.classify(0.0D, 0.0D, 0.0D, 0.0D));
        assertEquals(UNAVAILABLE, TemperatureHudPolicy.classify(Double.NaN, 0.0D, 0.5D, 1.7D));
    }
}
