package com.ruskserver.moveearth_addtional.worldgen.postprocess;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The post-processing pass ({@link PostProcessPrepass}) as saved with the world, so it resumes after a
 * restart. Chunks already picked from the spiral but not finished are kept as pending and checked again.
 */
public final class PostProcessJobSavedData extends SavedData {
    private ResourceLocation dimension;
    private int centerX;
    private int centerZ;
    private int radius;
    private long nextIndex;
    private boolean anytime;
    private long checked;
    private long processed;
    private long failed;
    private final Set<Long> pending = new LinkedHashSet<>();

    public boolean active() { return dimension != null; }
    public ResourceLocation dimension() { return dimension; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public int radius() { return radius; }
    public long nextIndex() { return nextIndex; }
    public boolean anytime() { return anytime; }
    public long checked() { return checked; }
    public long processed() { return processed; }
    public long failed() { return failed; }
    public Set<Long> pending() { return pending; }

    public void start(ResourceLocation dimension, int centerX, int centerZ, int radius, boolean anytime) {
        this.dimension = dimension;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
        this.anytime = anytime;
        nextIndex = 0L;
        checked = 0L;
        processed = 0L;
        failed = 0L;
        pending.clear();
        setDirty();
    }

    public void stop() {
        dimension = null;
        pending.clear();
        setDirty();
    }

    public void advance(long index) {
        nextIndex = index;
        setDirty();
    }

    public void counted(long checked, long processed, long failed) {
        this.checked = checked;
        this.processed = processed;
        this.failed = failed;
        setDirty();
    }

    public void pending(Collection<Long> chunks) {
        pending.clear();
        pending.addAll(chunks);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (dimension == null) return tag;
        tag.putString("Dimension", dimension.toString());
        tag.putInt("CenterX", centerX);
        tag.putInt("CenterZ", centerZ);
        tag.putInt("Radius", radius);
        tag.putLong("NextIndex", nextIndex);
        tag.putBoolean("Anytime", anytime);
        tag.putLong("Checked", checked);
        tag.putLong("Processed", processed);
        tag.putLong("Failed", failed);
        tag.put("Pending", new LongArrayTag(pending.stream().mapToLong(Long::longValue).toArray()));
        return tag;
    }

    public static PostProcessJobSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PostProcessJobSavedData data = new PostProcessJobSavedData();
        if (!tag.contains("Dimension")) return data;
        data.dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        data.centerX = tag.getInt("CenterX");
        data.centerZ = tag.getInt("CenterZ");
        data.radius = tag.getInt("Radius");
        data.nextIndex = tag.getLong("NextIndex");
        data.anytime = tag.getBoolean("Anytime");
        data.checked = tag.getLong("Checked");
        data.processed = tag.getLong("Processed");
        data.failed = tag.getLong("Failed");
        for (long chunk : tag.getLongArray("Pending")) data.pending.add(chunk);
        return data;
    }

    public static PostProcessJobSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PostProcessJobSavedData::new, PostProcessJobSavedData::load, null),
                "moveearth_postprocess_prepass");
    }
}
