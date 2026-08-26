package com.ruskserver.moveearth_addtional.client.gunpack;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class RequiredGunPackService {
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_DROP_COUNT = 16;
    private static final Map<String, RequiredGunPack> REQUIRED_BY_NAMESPACE = createRequiredMap();

    private RequiredGunPackService() {
    }

    public static Path gunPackDirectory() {
        return FMLPaths.GAMEDIR.get().resolve("tacz");
    }

    public static List<RequiredGunPack> findMissing() {
        Set<String> installed = findInstalledNamespaces();
        return RequiredGunPack.ALL.stream()
                .filter(pack -> !installed.contains(pack.namespace()))
                .toList();
    }

    public static InstallResult installDropped(List<Path> droppedFiles) {
        int installedCount = 0;
        int rejectedCount = 0;
        int alreadyInstalledCount = 0;
        String lastError = "";
        Set<String> installedNamespaces = findInstalledNamespaces();

        if (droppedFiles.size() > MAX_DROP_COUNT) {
            return new InstallResult(0, droppedFiles.size(), 0, "Too many files were dropped at once.");
        }

        for (Path dropped : droppedFiles) {
            try {
                Path source = dropped.toAbsolutePath().normalize();
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)
                        || !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    rejectedCount++;
                    lastError = source.getFileName() + " is not a regular ZIP file.";
                    continue;
                }
                if (Files.size(source) > MAX_ARCHIVE_BYTES) {
                    rejectedCount++;
                    lastError = source.getFileName() + " exceeds the 1 GiB safety limit.";
                    continue;
                }

                String namespace = GunPackArchive.readNamespace(source).orElse(null);
                if (namespace == null || !REQUIRED_BY_NAMESPACE.containsKey(namespace)) {
                    rejectedCount++;
                    lastError = source.getFileName() + " is not one of the required GunPacks.";
                    continue;
                }
                if (installedNamespaces.contains(namespace)) {
                    alreadyInstalledCount++;
                    continue;
                }

                installArchive(source, namespace);
                installedNamespaces.add(namespace);
                installedCount++;
            } catch (IOException | RuntimeException exception) {
                rejectedCount++;
                lastError = dropped.getFileName() + ": " + exception.getMessage();
                Moveearth_addtional.LOGGER.warn("Could not install dropped TaCZ GunPack {}", dropped, exception);
            }
        }

        return new InstallResult(installedCount, rejectedCount, alreadyInstalledCount, lastError);
    }

    private static Set<String> findInstalledNamespaces() {
        Set<String> installed = new HashSet<>();
        Path directory = gunPackDirectory();
        if (!Files.isDirectory(directory)) {
            return installed;
        }

        try (Stream<Path> children = Files.list(directory)) {
            children.forEach(path -> {
                try {
                    GunPackArchive.readNamespace(path).ifPresent(installed::add);
                } catch (IOException | RuntimeException exception) {
                    Moveearth_addtional.LOGGER.warn("Could not inspect TaCZ GunPack {}", path, exception);
                }
            });
        } catch (IOException exception) {
            Moveearth_addtional.LOGGER.warn("Could not scan the TaCZ GunPack directory {}", directory, exception);
        }
        return installed;
    }

    private static void installArchive(Path source, String namespace) throws IOException {
        Path directory = gunPackDirectory().toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path target = directory.resolve(source.getFileName()).normalize();
        if (!target.getParent().equals(directory)) {
            throw new IOException("Unsafe GunPack destination");
        }
        if (source.equals(target)) {
            return;
        }
        if (Files.exists(target)) {
            throw new IOException("A file with the same name already exists in the GunPack directory");
        }

        Path partial = Files.createTempFile(directory, ".moveearth-gunpack-", ".part");
        try {
            Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING);
            String copiedNamespace = GunPackArchive.readZipNamespace(partial).orElse(null);
            if (!namespace.equals(copiedNamespace)) {
                throw new IOException("Copied GunPack failed metadata verification");
            }
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(partial, target);
            }
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private static Map<String, RequiredGunPack> createRequiredMap() {
        Map<String, RequiredGunPack> result = new HashMap<>();
        for (RequiredGunPack pack : RequiredGunPack.ALL) {
            result.put(pack.namespace(), pack);
        }
        return Map.copyOf(result);
    }

    public record InstallResult(int installed, int rejected, int alreadyInstalled, String lastError) {
    }
}
