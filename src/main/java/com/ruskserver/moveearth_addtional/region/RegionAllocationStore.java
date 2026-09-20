package com.ruskserver.moveearth_addtional.region;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.region.RegionProfileAssigner.Assignment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Keeps a world's resource allocation fixed once it has been decided.
 *
 * <p>Which region holds which exclusive resource is part of a world, not part
 * of a configuration. Nations are built on it: a country settles beside the
 * gold and goes to war over the uranium. Recomputing it from config on every
 * start means an operator adjusting a list can silently move a resource out
 * from under whoever was mining it, with nothing in the world to show what
 * happened.
 *
 * <p>Written as a file in the world folder rather than through {@code
 * SavedData}, which the plan named. Saved data needs a level, and the
 * allocation has to exist before any level is created — the first chunk is
 * generated during level load and asks for it. The requirement is that the
 * answer survives a restart; the mechanism is free.
 *
 * <p>The file is meant to be read by a person. When someone asks why a region
 * has what it has, the answer should be a file they can open, not a blob.
 */
public final class RegionAllocationStore {

    private static final String FILE_NAME = "moveearth_regions.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RegionAllocationStore() { }

    /** The allocation recorded for this world, if one has been. */
    public static Optional<List<Assignment>> load(MinecraftServer server) {
        Path path = pathFor(server);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                    JsonObject.class);
            List<Assignment> out = new ArrayList<>();
            for (var entry : root.getAsJsonArray("regions")) {
                JsonObject region = entry.getAsJsonObject();
                out.add(new Assignment(
                        region.get("region").getAsInt(),
                        region.has("exclusive") && !region.get("exclusive").isJsonNull()
                                ? region.get("exclusive").getAsString() : null,
                        region.get("baseDensity").getAsDouble(),
                        region.has("richIn") ? region.get("richIn").getAsString() : null,
                        region.has("shortOf") ? region.get("shortOf").getAsString() : null));
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(List.copyOf(out));
        } catch (Exception exception) {
            // Refusing to start would strand a world over a file that can be
            // rebuilt; reallocating silently would move resources under the
            // players. Saying so and reallocating is the least bad of the three.
            Moveearth_addtional.LOGGER.error(
                    "Could not read {} ({}). The allocation will be made again, and regions may "
                            + "not hold what they held before.", path, exception.toString());
            return Optional.empty();
        }
    }

    /** Records an allocation so that later starts use the same one. */
    public static void save(MinecraftServer server, List<Assignment> assignments) {
        Path path = pathFor(server);
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Which region holds what, decided once when this world was "
                + "first generated. Delete this file, or run /moveearth region reallocate, to "
                + "decide again -- doing so may move resources away from where players built.");
        var array = new com.google.gson.JsonArray();
        for (Assignment assignment : assignments) {
            JsonObject region = new JsonObject();
            region.addProperty("region", assignment.regionId());
            region.addProperty("exclusive", assignment.profileId());
            region.addProperty("baseDensity", assignment.baseDensity());
            region.addProperty("richIn", assignment.specialty());
            region.addProperty("shortOf", assignment.shortage());
            array.add(region);
        }
        root.add("regions", array);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            Moveearth_addtional.LOGGER.info("Region allocation recorded in {}", path);
        } catch (IOException exception) {
            // Not fatal: the world runs on this allocation now. It is the next
            // start that would differ, so the warning has to be findable later.
            Moveearth_addtional.LOGGER.error(
                    "Could not write {} ({}). This world's regions will be allocated again on the "
                            + "next start and may come out differently.", path, exception.toString());
        }
    }

    /** Removes the record so the next allocation is made afresh. */
    public static boolean clear(MinecraftServer server) {
        try {
            return Files.deleteIfExists(pathFor(server));
        } catch (IOException exception) {
            Moveearth_addtional.LOGGER.error("Could not delete {}", pathFor(server), exception);
            return false;
        }
    }

    private static Path pathFor(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
