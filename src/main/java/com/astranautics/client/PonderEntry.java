package com.astranautics.client;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One item's "ponder": a short, hand-written explanation of what the item is for and how to
 * use it, shown in {@link PonderScreen} while holding W with the cursor over the item.
 *
 * <p>Text lives in the language files, one key per page, so the whole thing is translatable
 * without touching code. Pages are kept short on purpose: a ponder is a glance, not a manual.</p>
 */
public final class PonderEntry {
    /** One page of an entry. */
    public record Page(String titleKey, String bodyKey) {
        public Component title() {
            return Component.translatable(this.titleKey);
        }

        public Component body() {
            return Component.translatable(this.bodyKey);
        }
    }

    private final String itemId;
    private final List<Page> pages;

    public PonderEntry(String itemId, List<Page> pages) {
        this.itemId = itemId;
        this.pages = List.copyOf(pages);
    }

    /** Registry name, used for the language keys ({@code ponder.astranautics.<item>.*}). */
    public String itemId() {
        return this.itemId;
    }

    public List<Page> pages() {
        return this.pages;
    }

    public int pageCount() {
        return this.pages.size();
    }

    /** Heading of the whole entry: the item's own name. */
    public Component title() {
        return Component.translatable("block.astranautics." + this.itemId);
    }

    /** Builds the language keys for a page: {@code ponder.astranautics.<item>.title.0}. */
    public static Page page(String itemId, int index) {
        String base = "ponder.astranautics." + itemId;
        return new Page(base + ".title." + index, base + ".body." + index);
    }
}
