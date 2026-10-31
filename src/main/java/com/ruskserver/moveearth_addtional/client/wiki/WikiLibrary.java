package com.ruskserver.moveearth_addtional.client.wiki;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The wiki bundled into the mod at build time (see fetchWiki in build.gradle): its pages, the site's
 * sidebar for their order, which items each page explains, and the sizes of the Mermaid diagram PNGs.
 * Loaded on first use and again after a resource reload.
 */
public final class WikiLibrary {
    public static final String SITE = "https://rusklabo.github.io/moveearth-web";
    private static final String ROOT = "wiki/";
    private static WikiLibrary loaded;

    public record Page(String slug, WikiMarkdown.Document document) {
        public String title() {
            return document.title().isBlank() ? slug : document.title();
        }
    }

    /** A sidebar entry: a page, or a link off the wiki ({@code url}). */
    public record Entry(String label, String slug, String url) { }

    public record Group(String label, List<Entry> entries) { }

    private final Map<String, Page> pages;
    private final List<Group> groups;
    private final Map<Item, List<String>> itemPages;
    private final Map<String, int[]> diagramSizes;

    private WikiLibrary(Map<String, Page> pages, List<Group> groups, Map<Item, List<String>> itemPages,
                        Map<String, int[]> diagramSizes) {
        this.pages = pages;
        this.groups = groups;
        this.itemPages = itemPages;
        this.diagramSizes = diagramSizes;
    }

    public static WikiLibrary get() {
        if (loaded == null) loaded = load(Minecraft.getInstance().getResourceManager());
        return loaded;
    }

    public static void invalidate() {
        loaded = null;
    }

    public boolean isEmpty() {
        return pages.isEmpty();
    }

    public Optional<Page> page(String slug) {
        return Optional.ofNullable(pages.get(slug));
    }

    public List<Group> groups() {
        return groups;
    }

    /** The page to open first: the site's first sidebar page. */
    public Optional<String> home() {
        for (Group group : groups) {
            for (Entry entry : group.entries()) if (entry.slug() != null) return Optional.of(entry.slug());
        }
        return pages.keySet().stream().findFirst();
    }

    /** Pages that list this item in their front matter, best first. */
    public List<String> pagesFor(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        return itemPages.getOrDefault(stack.getItem(), List.of());
    }

    /** The CSS size of a Mermaid diagram's PNG, or null when this build has none for it. */
    public int[] diagramSize(String key) {
        return diagramSizes.get(key);
    }

    public static ResourceLocation diagramTexture(String key) {
        return ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, ROOT + "mermaid/" + key + ".png");
    }

    private static WikiLibrary load(ResourceManager resources) {
        Map<String, Page> pages = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> found = resources.listResources(ROOT + "pages",
                location -> location.getNamespace().equals(Moveearth_addtional.MODID)
                        && (location.getPath().endsWith(".md") || location.getPath().endsWith(".mdx")));
        found.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String slug = slug(entry.getKey().getPath());
            try (InputStream input = entry.getValue().open()) {
                pages.put(slug, new Page(slug, WikiMarkdown.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8))));
            } catch (IOException | RuntimeException failure) {
                Moveearth_addtional.LOGGER.warn("In-game wiki: could not read {}", entry.getKey(), failure);
            }
        });

        Map<Item, List<String>> itemPages = new HashMap<>();
        for (Page page : pages.values()) {
            for (String id : page.document().items()) {
                ResourceLocation location = ResourceLocation.tryParse(id);
                if (location == null || !BuiltInRegistries.ITEM.containsKey(location)) continue;
                itemPages.computeIfAbsent(BuiltInRegistries.ITEM.get(location), item -> new ArrayList<>()).add(page.slug());
            }
        }
        itemPages.replaceAll((item, slugs) -> List.copyOf(slugs));

        return new WikiLibrary(Map.copyOf(pages), groups(resources, pages), Map.copyOf(itemPages),
                diagramSizes(resources));
    }

    /** {@code wiki/pages/guide/siege.md} is {@code guide/siege}; an {@code index} page takes its folder's name. */
    static String slug(String path) {
        String slug = path.substring((ROOT + "pages/").length()).replaceFirst("\\.mdx?$", "");
        if (slug.equals("index")) return "";
        return slug.endsWith("/index") ? slug.substring(0, slug.length() - "/index".length()) : slug;
    }

    private static List<Group> groups(ResourceManager resources, Map<String, Page> pages) {
        List<Group> groups = new ArrayList<>();
        Optional<Resource> sidebar = resources.getResource(
                ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, ROOT + "sidebar.json"));
        if (sidebar.isPresent()) {
            try (Reader reader = sidebar.get().openAsReader()) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonArray()) {
                    JsonObject group = element.getAsJsonObject();
                    String label = group.has("label") ? group.get("label").getAsString() : "";
                    List<Entry> entries = new ArrayList<>();
                    if (group.has("items")) {
                        for (JsonElement itemElement : group.getAsJsonArray("items")) {
                            JsonObject item = itemElement.getAsJsonObject();
                            if (item.has("slug")) {
                                String slug = item.get("slug").getAsString();
                                Page page = pages.get(slug);
                                if (page != null) entries.add(new Entry(item.has("label")
                                        ? item.get("label").getAsString() : page.title(), slug, null));
                            } else if (item.has("link")) {
                                String link = item.get("link").getAsString();
                                entries.add(new Entry(item.has("label") ? item.get("label").getAsString() : link, null,
                                        link.startsWith("http") ? link : SITE + (link.startsWith("/") ? link : "/" + link)));
                            }
                        }
                    } else if (group.has("autogenerate")) {
                        String directory = group.getAsJsonObject("autogenerate").get("directory").getAsString();
                        pages.values().stream()
                                .filter(page -> page.slug().equals(directory) || page.slug().startsWith(directory + "/"))
                                .sorted(Comparator.comparing(Page::slug))
                                .forEach(page -> entries.add(new Entry(page.title(), page.slug(), null)));
                    }
                    if (!entries.isEmpty()) groups.add(new Group(label, List.copyOf(entries)));
                }
            } catch (IOException | RuntimeException failure) {
                Moveearth_addtional.LOGGER.warn("In-game wiki: could not read the sidebar", failure);
                groups.clear();
            }
        }
        // Pages the sidebar does not list are still reachable.
        List<String> listed = groups.stream().flatMap(group -> group.entries().stream())
                .map(Entry::slug).filter(java.util.Objects::nonNull).toList();
        List<Entry> rest = pages.values().stream().filter(page -> !listed.contains(page.slug()))
                .sorted(Comparator.comparing(Page::slug)).map(page -> new Entry(page.title(), page.slug(), null)).toList();
        if (!rest.isEmpty()) groups.add(new Group(groups.isEmpty() ? "Wiki" : "その他", rest));
        return List.copyOf(groups);
    }

    private static Map<String, int[]> diagramSizes(ResourceManager resources) {
        Map<String, int[]> sizes = new HashMap<>();
        Optional<Resource> index = resources.getResource(
                ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, ROOT + "mermaid/index.json"));
        if (index.isEmpty()) return Map.of();
        try (Reader reader = index.get().openAsReader()) {
            JsonObject diagrams = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("diagrams");
            for (var entry : diagrams.entrySet()) {
                JsonObject size = entry.getValue().getAsJsonObject();
                // Only diagrams whose PNG this build actually carries.
                if (resources.getResource(diagramTexture(entry.getKey())).isEmpty()) continue;
                sizes.put(entry.getKey(), new int[] {size.get("width").getAsInt(), size.get("height").getAsInt()});
            }
        } catch (IOException | RuntimeException failure) {
            Moveearth_addtional.LOGGER.warn("In-game wiki: could not read the diagram index", failure);
        }
        return Map.copyOf(sizes);
    }
}
