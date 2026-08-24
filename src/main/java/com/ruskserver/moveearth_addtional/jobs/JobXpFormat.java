package com.ruskserver.moveearth_addtional.jobs;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class JobXpFormat {
    private JobXpFormat() {
    }

    public static String format(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
