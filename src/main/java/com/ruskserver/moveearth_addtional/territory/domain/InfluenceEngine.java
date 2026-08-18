package com.ruskserver.moveearth_addtional.territory.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InfluenceEngine {
    public InfluenceResult evaluate(
            TerritoryPosition query,
            Collection<InfluenceSource> sources,
            Collection<RaidEmitter> raidEmitters,
            InfluenceSettings settings
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(raidEmitters, "raidEmitters");
        Objects.requireNonNull(settings, "settings");

        Map<TerritoryOwnerId, Double> rawInfluence = new HashMap<>();
        for (InfluenceSource source : sources) {
            Objects.requireNonNull(source, "influence source");
            TerritoryCore core = source.core();
            if (!core.active()) {
                continue;
            }
            double distance = core.position().distanceTo(query, settings.distanceModel());
            double influence = Math.max(0.0D,
                    source.corePower(settings) - distance * settings.distanceFalloff());
            if (influence > 0.0D) {
                rawInfluence.merge(core.ownerId(), influence, Math::max);
            }
        }

        if (rawInfluence.isEmpty()) {
            return InfluenceResult.unclaimed();
        }

        Map<TerritoryOwnerId, Double> effectiveInfluence = new HashMap<>();
        rawInfluence.forEach((ownerId, influence) -> {
            double suppression = raidEmitters.stream()
                    .filter(Objects::nonNull)
                    .filter(RaidEmitter::active)
                    .filter(emitter -> !emitter.attackerOwnerId().equals(ownerId))
                    .mapToDouble(emitter -> emitter.suppressionAt(query, settings.distanceModel()))
                    .sum();
            suppression = Math.min(suppression, settings.maxRaidSuppression());
            double effective = Math.max(0.0D, influence - suppression);
            if (effective > 0.0D) {
                effectiveInfluence.put(ownerId, effective);
            }
        });

        if (effectiveInfluence.isEmpty()) {
            return InfluenceResult.unclaimed();
        }

        List<Map.Entry<TerritoryOwnerId, Double>> ranking = new ArrayList<>(effectiveInfluence.entrySet());
        ranking.sort(Map.Entry.<TerritoryOwnerId, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));

        Map.Entry<TerritoryOwnerId, Double> leader = ranking.getFirst();
        double runnerUp = ranking.size() > 1 ? ranking.get(1).getValue() : 0.0D;
        double lead = leader.getValue() - runnerUp;
        boolean contested = ranking.size() > 1
                && (Double.compare(lead, 0.0D) == 0 || lead < settings.contestMargin());
        Optional<TerritoryOwnerId> controller = contested
                ? Optional.empty()
                : Optional.of(leader.getKey());

        Set<ProtectionAction> protectedActions = EnumSet.noneOf(ProtectionAction.class);
        if (!contested) {
            for (ProtectionAction action : ProtectionAction.values()) {
                if (leader.getValue() >= settings.thresholdFor(action)) {
                    protectedActions.add(action);
                }
            }
        }

        Map<TerritoryOwnerId, Double> orderedInfluence = new LinkedHashMap<>();
        ranking.forEach(entry -> orderedInfluence.put(entry.getKey(), entry.getValue()));
        return new InfluenceResult(
                Optional.of(leader.getKey()),
                controller,
                leader.getValue(),
                runnerUp,
                contested,
                protectedActions,
                orderedInfluence
        );
    }
}
