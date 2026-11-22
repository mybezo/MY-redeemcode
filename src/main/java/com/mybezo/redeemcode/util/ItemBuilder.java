package com.mybezo.redeemcode.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Small fluent helper to build GUI icons.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private Component name;
    private final List<Component> lore = new ArrayList<>();
    private boolean glow;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(ItemStack.of(material));
    }

    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item.clone());
    }

    public ItemBuilder name(String mini, TagResolver... resolvers) {
        this.name = Messages.gui(mini, resolvers);
        return this;
    }

    public ItemBuilder lore(String mini, TagResolver... resolvers) {
        this.lore.add(Messages.gui(mini, resolvers));
        return this;
    }

    public ItemBuilder lore(Component component) {
        this.lore.add(component);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemStack build() {
        item.editMeta(meta -> apply(meta));
        return item;
    }

    private void apply(ItemMeta meta) {
        if (name != null) {
            meta.displayName(name);
        }
        if (!lore.isEmpty()) {
            List<Component> existing = meta.lore();
            List<Component> merged = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            merged.addAll(lore);
            meta.lore(merged);
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.values());
    }
}
