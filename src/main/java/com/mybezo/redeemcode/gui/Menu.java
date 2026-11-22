package com.mybezo.redeemcode.gui;

import com.mybezo.redeemcode.RedeemCodePlugin;
import com.mybezo.redeemcode.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all plugin menus. Clicks are cancelled by default.
 */
public abstract class Menu implements InventoryHolder {

    protected final RedeemCodePlugin plugin;
    protected final Player viewer;
    protected final Inventory inventory;

    protected Menu(RedeemCodePlugin plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    /** (Re)draws all items of the menu. */
    protected abstract void render();

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    /** Opens another menu on the next tick (safe to call from inventory events). */
    protected void openLater(Menu menu) {
        Bukkit.getScheduler().runTask(plugin, menu::open);
    }

    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }

    public void onDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    public void onClose(InventoryCloseEvent event) {
    }

    protected void fill(int from, int to) {
        ItemStack pane = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = from; i <= to; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
