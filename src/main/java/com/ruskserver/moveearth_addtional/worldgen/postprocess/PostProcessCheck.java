package com.ruskserver.moveearth_addtional.worldgen.postprocess;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Whether a chunk still has world-generation post-processing waiting: blocks, mostly water, that
 * generation marked to be updated the first time the chunk runs. Read from the saved chunk where
 * possible, so chunks with nothing waiting are never loaded. The rule is {@link PostProcessRule}.
 */
public final class PostProcessCheck {
    private PostProcessCheck() { }

    /** A saved, fully generated chunk with post-processing left. Unfinished chunks are left to generation. */
    public static boolean needed(CompoundTag chunk) {
        if (chunk == null) return false;
        ListTag sections = chunk.getList("PostProcessing", Tag.TAG_LIST);
        int[] sizes = new int[sections.size()];
        for (int i = 0; i < sizes.length; i++) sizes[i] = sections.getList(i).size();
        return PostProcessRule.needed(chunk.getString("Status"), sizes);
    }

    /** The same for a chunk already in memory. */
    public static boolean needed(ShortList[] sections) {
        if (sections == null) return false;
        int[] sizes = new int[sections.length];
        for (int i = 0; i < sizes.length; i++) sizes[i] = sections[i] == null ? 0 : sections[i].size();
        return PostProcessRule.anyWaiting(sizes);
    }
}
