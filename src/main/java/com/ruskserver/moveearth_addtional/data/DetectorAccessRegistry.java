package com.ruskserver.moveearth_addtional.data;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stores owner-scoped detector managers independently from whitelist membership. */
public final class DetectorAccessRegistry {
    private final Map<UUID, Map<UUID, String>> managersByOwner = new HashMap<>();

    public Map<UUID, String> getManagers(UUID owner) {
        Map<UUID, String> managers = managersByOwner.get(owner);
        return managers != null ? Collections.unmodifiableMap(managers) : Collections.emptyMap();
    }

    public List<String> getManagerNamesForDisplay(UUID owner) {
        List<String> names = new ArrayList<>(getManagers(owner).values());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public Set<UUID> getAllOwners() {
        return Collections.unmodifiableSet(managersByOwner.keySet());
    }

    public boolean isManager(UUID owner, UUID playerUuid) {
        Map<UUID, String> managers = managersByOwner.get(owner);
        return managers != null && managers.containsKey(playerUuid);
    }

    public boolean canEditWhitelist(UUID owner, UUID playerUuid) {
        return owner.equals(playerUuid) || isManager(owner, playerUuid);
    }

    public boolean addManager(UUID owner, UUID managerUuid, @Nullable String managerName) {
        if (owner.equals(managerUuid)) {
            return false;
        }
        String displayName = managerName != null && !managerName.isBlank()
                ? managerName
                : managerUuid.toString();
        Map<UUID, String> managers = managersByOwner.computeIfAbsent(owner, ignored -> new HashMap<>());
        String previous = managers.put(managerUuid, displayName);
        return !displayName.equals(previous);
    }

    public boolean removeManager(UUID owner, UUID managerUuid) {
        Map<UUID, String> managers = managersByOwner.get(owner);
        if (managers == null || managers.remove(managerUuid) == null) {
            return false;
        }
        if (managers.isEmpty()) {
            managersByOwner.remove(owner);
        }
        return true;
    }

    public boolean removeManagerByName(UUID owner, String managerName) {
        Map<UUID, String> managers = managersByOwner.get(owner);
        if (managers == null) {
            return false;
        }
        UUID target = managers.entrySet().stream()
                .filter(entry -> entry.getValue().equalsIgnoreCase(managerName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        return target != null && removeManager(owner, target);
    }
}
