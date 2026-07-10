package com.trading.marketdata.ibkr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IBKR articleType semantics (TWS API docs): 0 = plain text OR HTML, 1 = binary/PDF.
 * Dow Jones delivers HTML as type 0 — observed live: raw &lt;p&gt; tags and undecoded
 * entities (&amp;apos;) landed in the Book because the old code only flattened type 1.
 * Type 0 is therefore ALWAYS flattened; these tests pin that flattening down.
 */
class ArticleTextFlattenTest {

    @Test
    void flattensDowJonesStyleHtml() {
        String html = "<p>\n  By George Glover </p>\n<p>\n  Micron Technology dropped 2%. "
                + "SK Hynix&apos;s ADR sale was priced at &quot;$149&quot;. </p>\n"
                + "<div><p>Copyright (c) 2026 Dow Jones &amp; Company, Inc.</p></div>";

        String flat = IbkrWrapper.flattenArticleText(html);

        assertFalse(flat.contains("<"), "tags must be gone");
        assertFalse(flat.contains("&apos;"), "entities must be decoded");
        assertTrue(flat.contains("By George Glover"));
        assertTrue(flat.contains("SK Hynix's ADR sale was priced at \"$149\"."));
        assertTrue(flat.contains("Dow Jones & Company"));
    }

    @Test
    void plainTextPassesThroughWithLineStructure() {
        String plain = "First paragraph.\n\nSecond paragraph.";
        assertEquals(plain, IbkrWrapper.flattenArticleText(plain));
    }

    @Test
    void capsBlankRunsAndTrims() {
        String messy = "  <p>one</p>\n\n\n\n<p>two</p>  ";
        String flat = IbkrWrapper.flattenArticleText(messy);
        assertFalse(flat.contains("\n\n\n"));
        assertEquals(flat, flat.strip());
    }

    @Test
    void nullAndBlankBecomeEmpty() {
        assertEquals("", IbkrWrapper.flattenArticleText(null));
        assertEquals("", IbkrWrapper.flattenArticleText("   "));
    }
}
