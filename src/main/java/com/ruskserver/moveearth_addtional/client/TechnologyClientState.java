package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.s2.technology.TechnologySnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TechnologyClientState {
    private static final Map<ResourceLocation, TechnologySnapshot.Node> BY_ITEM = new ConcurrentHashMap<>();
    private TechnologyClientState() { }
    public static void update(TechnologySnapshot snapshot) {
        BY_ITEM.clear();
        snapshot.nodes().forEach(node -> node.jeiItems().forEach(item -> BY_ITEM.put(item, node)));
    }
    public static TechnologySnapshot.Node forItem(ResourceLocation item) { return BY_ITEM.get(item); }
}
