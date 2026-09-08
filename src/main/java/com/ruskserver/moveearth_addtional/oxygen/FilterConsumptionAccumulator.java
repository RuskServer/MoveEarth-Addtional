package com.ruskserver.moveearth_addtional.oxygen;

final class FilterConsumptionAccumulator {
    private float remainder;

    int consume(float rate) {
        remainder += rate;
        int wholeTicks = (int) remainder;
        remainder -= wholeTicks;
        return wholeTicks;
    }

    void reset() {
        remainder = 0.0f;
    }

    float remainder() {
        return remainder;
    }
}
