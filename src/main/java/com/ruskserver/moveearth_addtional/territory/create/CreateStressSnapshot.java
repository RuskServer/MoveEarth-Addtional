package com.ruskserver.moveearth_addtional.territory.create;

public record CreateStressSnapshot(
        double generatedCapacity,
        double usedStress,
        double directCoreStress,
        double industrialScore,
        int sourceCount,
        int networkCount,
        long sampledGameTime
) {
    public static CreateStressSnapshot empty(long sampledGameTime) {
        return new CreateStressSnapshot(0.0D, 0.0D, 0.0D, 0.0D, 0, 0, sampledGameTime);
    }
}
