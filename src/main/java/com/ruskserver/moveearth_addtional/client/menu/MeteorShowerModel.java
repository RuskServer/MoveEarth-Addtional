package com.ruskserver.moveearth_addtional.client.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/** Deterministic, allocation-free-at-runtime schedule for the title-screen meteor shower. */
final class MeteorShowerModel {
    static final double CYCLE_SECONDS = 10.0D;
    static final long DEFAULT_SEED = 0x4D4F564545415254L;

    private MeteorShowerModel() {
    }

    static List<Meteor> create(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        List<Meteor> meteors = new ArrayList<>(34);

        // A sparse layer keeps the sky alive for the whole cycle.
        for (int index = 0; index < 12; index++) {
            double slot = CYCLE_SECONDS / 12.0D;
            meteors.add(createMeteor(random, index * slot + random.nextDouble() * slot * 0.62D));
        }
        // The source movie builds into a dense shower around its middle.
        for (int index = 0; index < 22; index++) {
            meteors.add(createMeteor(random, 2.1D + random.nextDouble() * 5.5D));
        }

        meteors.sort(Comparator.comparingDouble(Meteor::startSeconds));
        return List.copyOf(meteors);
    }

    static double progress(Meteor meteor, double elapsedSeconds) {
        double cycleTime = elapsedSeconds % CYCLE_SECONDS;
        if (cycleTime < 0.0D) cycleTime += CYCLE_SECONDS;
        double age = cycleTime - meteor.startSeconds();
        if (age < 0.0D) age += CYCLE_SECONDS;
        return age <= meteor.durationSeconds() ? age / meteor.durationSeconds() : -1.0D;
    }

    private static Meteor createMeteor(SplittableRandom random, double startSeconds) {
        return new Meteor(
                startSeconds % CYCLE_SECONDS,
                0.62D + random.nextDouble() * 0.72D,
                0.18D + random.nextDouble() * 1.14D,
                -0.16D + random.nextDouble() * 0.66D,
                0.25D + random.nextDouble() * 0.43D,
                0.065D + random.nextDouble() * 0.115D,
                0.0026D + random.nextDouble() * 0.0048D,
                0.68D + random.nextDouble() * 0.32D,
                random.nextDouble());
    }

    record Meteor(double startSeconds, double durationSeconds, double startX, double startY,
                  double travel, double length, double width, double brightness,
                  double colorVariation) {
    }
}
