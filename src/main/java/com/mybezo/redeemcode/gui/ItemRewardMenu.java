package com.mybezo.redeemcode.gui;

import com.mybezo.redeemcode.RedeemCodePlugin;
import com.mybezo.redeemcode.model.RedeemCode;
import com.mybezo.redeemcode.util.ItemBuilder;
import com.mybezo.redeemcode.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.mybezo.redeemcode.util.Messages.ph;

/**
 * Lets an admin drag & drop the item rewards of a code. Saved when the menu closes.
 */
public final class ItemRewardMenu extends Menu {

    private static final int STORAGE_SIZE = 45;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_CLEAR = 47;
    private static final int SLOT_BACK = 49;

    private final String codeKey;
    private boolean saved;

    public ItemRewardMenu(RedeemCodePlugin plugin, Player viewer, RedeemCode code) {
        super(plugin, viewer, 6, Messages.gui("<dark_gray>Item rewards: <gold><code>", ph("code", code.name())));
        this.codeKey = code.key();
    }

    @Override
    protected void render() {
        RedeemCode code = plugin.storage().get(codeKey);
        if (code == null) {
            return;
        }
        inventory.clear();
        List<ItemStack> items = code.items();
        for (int i = 0; i < Math.min(STORAGE_SIZE, items.size()); i++) {
            inventory.setItem(i, items.get(i).clone());
        }
        inventory.setItem(SLOT_INFO, ItemBuilder.of(Material.BOOK)
                .name("<gold>How to use")
                .lore("<gray>Place the reward items in the slots above.")
                .lore("<gray>Every item (with its amount, enchantments,")
                .lore("<gray>name and lore) is given exactly as placed.")
                .lore(" ")
                .lore("<gray>Changes are saved when you close this menu.")
                .build());
        inventory.setItem(SLOT_CLEAR, ItemBuilder.of(Material.HOPPER)
                .name("<red>Clear all items")
                .lore("<red>Shift + Right-click to confirm")
                .build());
        inventory.setItem(SLOT_BACK, ItemBuilder.of(Material.ARROW).name("<green>Save & Back").build());
        fill(STORAGE_SIZE, inventory.getSize() - 1);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        if (event.getClickedInventory() == inventory && raw >= STORAGE_SIZE) {
            event.setCancelled(true);
            if (raw == SLOT_BACK) {
                RedeemCode code = plugin.storage().get(codeKey);
                save();
                if (code != null) {
                    openLater(new EditorMenu(plugin, viewer, code));
                }
            } else if (raw == SLOT_CLEAR && event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                for (int i = 0; i < STORAGE_SIZE; i++) {
                    inventory.setItem(i, null);
                }
            }
        }
        // Clicks in the storage area and in the player's own inventory are allowed.
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (slot >= STORAGE_SIZE && slot < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        save();
    }

    private void save() {
        if (saved) {
            return;
        }
        saved = true;
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.isEmpty()) {
                items.add(item.clone());
            }
        }
        RedeemCode code = plugin.storage().get(codeKey);
        if (code == null) {
            // The code was deleted while editing: give the items back so nothing is lost.
            for (ItemStack item : items) {
                viewer.getInventory().addItem(item).values()
                        .forEach(rest -> viewer.getWorld().dropItem(viewer.getLocation(), rest));
            }
            return;
        }
        code.items(items);
        plugin.storage().save();
        plugin.messages().send(viewer, "value-updated");
    }
}
