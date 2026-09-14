package com.ruskserver.moveearth_addtional.client.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeteorShowerModelTest {
    @Test
    void scheduleIsDeterministicAndSorted() {
        List<MeteorShowerModel.Meteor> first = MeteorShowerModel.create(MeteorShowerModel.DEFAULT_SEED);
        List<MeteorShowerModel.Meteor> second = MeteorShowerModel.create(MeteorShowerModel.DEFAULT_SEED);

        assertEquals(34, first.size());
        assertEquals(first, second);
        for (int index = 1; index < first.size(); index++) {
            assertTrue(first.get(index - 1).startSeconds() <= first.get(index).startSeconds());
        }
    }

    @Test
    void progressLoopsAcrossCycleBoundary() {
        MeteorShowerModel.Meteor meteor = new MeteorShowerModel.Meteor(
                9.8D, 0.8D, 1.0D, 0.0D, 0.5D, 0.1D, 0.01D, 1.0D, 0.5D);

        assertEquals(0.0D, MeteorShowerModel.progress(meteor, 9.8D), 0.0001D);
        assertEquals(0.5D, MeteorShowerModel.progress(meteor, 10.2D), 0.0001D);
        assertTrue(MeteorShowerModel.progress(meteor, 10.7D) < 0.0D);
    }

    @Test
    void middleOfCycleContainsTheDenseShower() {
        List<MeteorShowerModel.Meteor> meteors = MeteorShowerModel.create(MeteorShowerModel.DEFAULT_SEED);
        long middle = meteors.stream().filter(meteor -> MeteorShowerModel.progress(meteor, 5.0D) >= 0.0D).count();
        long quiet = meteors.stream().filter(meteor -> MeteorShowerModel.progress(meteor, 1.5D) >= 0.0D).count();

        assertTrue(middle > quiet);
    }
}
