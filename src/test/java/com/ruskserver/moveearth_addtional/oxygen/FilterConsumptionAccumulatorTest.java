package com.ruskserver.moveearth_addtional.oxygen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterConsumptionAccumulatorTest {
    @Test
    void fractionalConsumptionMatchesConfiguredRateOverTime() {
        FilterConsumptionAccumulator accumulator = new FilterConsumptionAccumulator();

        int consumed = 0;
        for (int tick = 0; tick < 20; tick++) {
            consumed += accumulator.consume(1.5f);
        }

        assertEquals(30, consumed);
        assertEquals(0.0f, accumulator.remainder());
    }

    @Test
    void fractionalRemainderCarriesBetweenTicks() {
        FilterConsumptionAccumulator accumulator = new FilterConsumptionAccumulator();

        assertEquals(1, accumulator.consume(1.5f));
        assertEquals(0.5f, accumulator.remainder());
        assertEquals(2, accumulator.consume(1.5f));
        assertEquals(0.0f, accumulator.remainder());
    }

    @Test
    void resetDropsConsumptionFromAnInactivePeriod() {
        FilterConsumptionAccumulator accumulator = new FilterConsumptionAccumulator();
        accumulator.consume(1.5f);

        accumulator.reset();

        assertEquals(1, accumulator.consume(1.5f));
    }
}
