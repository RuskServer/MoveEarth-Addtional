package com.ruskserver.moveearth_addtional.warehouse;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/** Refuses malformed or unsafe bundled structure data before worldgen starts. */
public final class WarehouseTemplateValidator {
    private static final String RESOURCE = "/data/moveearth_addtional/structure/warehouse.nbt";
    // NbtAccounter counts per-tag/object overhead as well as payload bytes. The
    // 33 KiB compressed warehouse exceeds 4 MiB of accounted NBT allocations.
    // Keep a finite ceiling so a damaged or substituted template cannot exhaust memory.
    private static final long MAX_ACCOUNTED_NBT_BYTES = 32L * 1024L * 1024L;

    private WarehouseTemplateValidator() { }

    public static void validate() throws IOException {
        CompoundTag data = readBundledTemplate();
        ListTag size = data.getList("size", Tag.TAG_INT);
        if (size.size() != 3 || size.getInt(0) != WarehouseSitePolicy.WIDTH
                || size.getInt(1) != WarehouseSitePolicy.HEIGHT
                || size.getInt(2) != WarehouseSitePolicy.LENGTH) {
            throw new IOException("warehouse.nbt dimensions differ from protected footprint");
        }
        if (!data.getList("entities", Tag.TAG_COMPOUND).isEmpty()) {
            throw new IOException("warehouse.nbt must not contain entities");
        }
        ListTag palette = data.getList("palette", Tag.TAG_COMPOUND);
        if (palette.isEmpty()) throw new IOException("warehouse.nbt has no block palette");
        for (Tag raw : palette) {
            String id = ((CompoundTag) raw).getString("Name");
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
                throw new IOException("Missing warehouse block: " + id);
            }
        }
        for (Tag raw : data.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) raw;
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) throw new IOException("Invalid warehouse palette index " + state);
            if (block.contains("nbt", Tag.TAG_COMPOUND)
                    && hasUnsafePayload(block.getCompound("nbt"))) {
                throw new IOException("warehouse.nbt contains inventory, loot-table or command data");
            }
        }
    }

    static CompoundTag readBundledTemplate() throws IOException {
        try (InputStream stream = WarehouseTemplateValidator.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IOException("Bundled warehouse.nbt is missing");
            return NbtIo.readCompressed(stream, NbtAccounter.create(MAX_ACCOUNTED_NBT_BYTES));
        }
    }

    private static boolean hasUnsafePayload(CompoundTag tag) {
        for (String key : tag.getAllKeys()) {
            if (key.equalsIgnoreCase("LootTable") || key.equalsIgnoreCase("Command")) return true;
            Tag nested = tag.get(key);
            if (key.equalsIgnoreCase("Items") && nested instanceof ListTag items && !items.isEmpty()) return true;
            if (nested instanceof CompoundTag compound && hasUnsafePayload(compound)) return true;
            if (nested instanceof ListTag list) {
                for (Tag element : list) {
                    if (element instanceof CompoundTag compound && hasUnsafePayload(compound)) return true;
                }
            }
        }
        return false;
    }

}
