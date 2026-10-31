package com.ruskserver.moveearth_addtional.client.wiki;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiMarkdownTest {
    @Test
    void readsFrontMatterInBothListForms() {
        var inline = WikiMarkdown.parse("---\ntitle: 倉庫拠点の襲撃\ndescription: \"説明\"\nitems: [moveearth_addtional:weapon_crate, create:brass_ingot]\n---\n本文");
        assertEquals("倉庫拠点の襲撃", inline.title());
        assertEquals("説明", inline.description());
        assertEquals(List.of("moveearth_addtional:weapon_crate", "create:brass_ingot"), inline.items());
        var block = WikiMarkdown.parse("---\ntitle: T\nitems:\n  - a:b\n  - 'c:d'\nsidebar:\n  order: 2\n---\n");
        assertEquals(List.of("a:b", "c:d"), block.items());
    }

    @Test
    void splitsHeadingsParagraphsListsAndTables() {
        var doc = WikiMarkdown.parse("## 入る前に\n\n一行目\n二行目\n\n- 外\n  - 内\n1. 番号\n\n| 色 | 状態 |\n|---|---|\n| 赤 | **出動** |\n");
        assertInstanceOf(WikiMarkdown.Heading.class, doc.blocks().get(0));
        var paragraph = (WikiMarkdown.Paragraph) doc.blocks().get(1);
        assertEquals("一行目二行目", paragraph.text().getFirst().text());
        var list = (WikiMarkdown.ListBlock) doc.blocks().get(2);
        assertEquals(3, list.items().size());
        assertEquals(1, list.items().get(1).depth());
        assertTrue(list.items().get(2).ordered());
        var table = (WikiMarkdown.Table) doc.blocks().get(3);
        assertEquals(2, table.header().size());
        assertEquals(WikiMarkdown.Style.BOLD, table.rows().getFirst().get(1).getFirst().style());
    }

    @Test
    void keepsDiagramsApartFromPlainCode() {
        var doc = WikiMarkdown.parse("```voxel\nlegend:\n  # = 壁 #4e5a48\nlayers:\n---\n##\n```\n```rounds\ndamage: 48\n丸石 = 32\n```\n```mermaid\ngraph TD\n```\n```js\nx\n```");
        assertInstanceOf(WikiMarkdown.Voxel.class, doc.blocks().get(0));
        assertInstanceOf(WikiMarkdown.Rounds.class, doc.blocks().get(1));
        var mermaid = (WikiMarkdown.Mermaid) doc.blocks().get(2);
        assertEquals(WikiMarkdown.mermaidKey("graph TD"), mermaid.key());
        assertInstanceOf(WikiMarkdown.Code.class, doc.blocks().get(3));
    }

    @Test
    void mermaidKeyMatchesTheSiteBuild() {
        // sha256("graph TD") = 0bd71... ; the site's blocks.mjs takes the first 16 hex digits.
        assertEquals(16, WikiMarkdown.mermaidKey("graph TD").length());
        assertEquals(WikiMarkdown.mermaidKey("graph TD"), WikiMarkdown.mermaidKey("graph TD\n  \n"));
    }

    @Test
    void readsAsidesAndDropsComponents() {
        var doc = WikiMarkdown.parse("import { Card } from '@astrojs/starlight/components';\n<Card title=\"x\">\n:::caution[注意]\n中身\n:::\n");
        assertEquals(1, doc.blocks().size());
        var aside = (WikiMarkdown.Aside) doc.blocks().getFirst();
        assertEquals("caution", aside.kind());
        assertEquals("注意", aside.title());
        assertEquals(1, aside.blocks().size());
    }

    @Test
    void inlineMarkupKeepsLinksAndStyles() {
        var spans = WikiMarkdown.inline("前 **太字** と `code` と [リンク](../base/) と snake_case");
        assertEquals("前 ", spans.get(0).text());
        assertEquals(WikiMarkdown.Style.BOLD, spans.get(1).style());
        assertEquals(WikiMarkdown.Style.CODE, spans.get(3).style());
        assertEquals("../base/", spans.get(5).link());
        assertEquals(" と snake_case", spans.get(6).text());
    }

    @Test
    void resolvesSiteLinksToSlugs() {
        assertEquals("guide/base", WikiMarkdown.resolveLink("guide/siege", "../base/"));
        assertEquals("reference/weapon-damage", WikiMarkdown.resolveLink("guide/siege", "../../reference/weapon-damage/#ap"));
        assertEquals("guide/attack", WikiMarkdown.resolveLink("guide/siege", "/moveearth-web/guide/attack/"));
        assertEquals("guide/attack", WikiMarkdown.resolveLink("guide/siege", "https://rusklabo.github.io/moveearth-web/guide/attack/"));
        assertEquals("reference", WikiMarkdown.resolveLink("guide/siege", "../../reference/"));
        assertNull(WikiMarkdown.resolveLink("guide/siege", "https://discord.gg/x"));
        assertEquals("guide/siege", WikiMarkdown.resolveLink("guide/siege", "#part"));
    }
}
