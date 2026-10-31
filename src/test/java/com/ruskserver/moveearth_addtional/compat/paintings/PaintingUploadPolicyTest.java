package com.ruskserver.moveearth_addtional.compat.paintings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaintingUploadPolicyTest {
    @Test
    void citizensUploadUpToTheDailyLimit() {
        assertEquals(PaintingUploadPolicy.Verdict.ALLOWED, PaintingUploadPolicy.check(false, true, 0, 3));
        assertEquals(PaintingUploadPolicy.Verdict.ALLOWED, PaintingUploadPolicy.check(false, true, 2, 3));
        assertEquals(PaintingUploadPolicy.Verdict.DAILY_LIMIT, PaintingUploadPolicy.check(false, true, 3, 3));
        assertEquals(PaintingUploadPolicy.Verdict.DAILY_LIMIT, PaintingUploadPolicy.check(false, true, 0, 0));
    }

    @Test
    void playersWithoutANationCannotUploadButStaffAlwaysCan() {
        assertEquals(PaintingUploadPolicy.Verdict.NO_NATION, PaintingUploadPolicy.check(false, false, 0, 3));
        assertEquals(PaintingUploadPolicy.Verdict.ALLOWED, PaintingUploadPolicy.check(true, false, 99, 3));
    }

    @Test
    void theCountStartsOverOnANewOpenDay() {
        assertEquals(2, PaintingUploadPolicy.usedToday("2026-10-22", 2, "2026-10-22"));
        assertEquals(0, PaintingUploadPolicy.usedToday("2026-10-21", 3, "2026-10-22"));
    }
}
