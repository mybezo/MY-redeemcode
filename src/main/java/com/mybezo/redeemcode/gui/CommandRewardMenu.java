package com.mybezo.redeemcode.gui;

import com.mybezo.redeemcode.RedeemCodePlugin;
import com.mybezo.redeemcode.model.RedeemCode;
import com.mybezo.redeemcode.util.ItemBuilder;
import com.mybezo.redeemcode.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.math.BigDecimal;
import java.util.List;

import static com.mybezo.redeemcode.util.Messages.ph;

/**
 * Manages the console command rewards (including balance rewards) of a code.
 */
public final class CommandRewardMenu extends Menu {

    private static final int LIST_SIZE = 45;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_ADD_BALANCE = 47;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_ADD_COMMAND = 51;

    private final String codeKey;

    public CommandRewardMenu(RedeemCodePlugin plugin, Player viewer, RedeemCode code) {
        super(plugin, viewer, 6, Messages.gui("<dark_gray>Command rewards: <gold><code>", ph("code", code.name())));
        this.codeKey = code.key();
    }

    private RedeemCode code() {
        return plugin.storage().get(codeKey);
    }

    @Override
    protected void render() {
        RedeemCode code = code();
        if (code == null) {
            return;
        }
        inventory.clear();
        List<String> commands = code.commands();
        for (int i = 0; i < Math.min(LIST_SIZE, commands.size()); i++) {
            String cmd = commands.get(i);
            boolean balance = cmd.toLowerCase().matches("^/?(eco|economy|money|balance|bal)\\b.*");
            inventory.setItem(i, ItemBuilder.of(balance ? Material.GOLD_INGOT : Material.PAPER)
                    .name("<white>#<n> <gray>»</gray> <yellow><cmd>", ph("n", i + 1), ph("cmd", cmd))
                    .lore("<gray>Executed by the console on redeem.")
                    .lore(" ")
                    .lore("<yellow>Left-click <gray>to edit")
                    .lore("<red>Shift + Right-click <gray>to remove")
                    .build());
        }

        String template = plugin.getConfig().getString("settings.balance-command", "eco give %player% %amount%");
        inventory.setItem(SLOT_INFO, ItemBuilder.of(Material.BOOK)
                .name("<gold>Placeholders")
                .lore("<yellow>%player% <gray>- player name")
                .lore("<yellow>%uuid% <gray>- player UUID")
                .lore("<yellow>%code% <gray>- the redeemed code")
                .lore(" ")
                .lore("<gray>Commands are run without the leading '/'.")
                .build());
        inventory.setItem(SLOT_ADD_BALANCE, ItemBuilder.of(Material.SUNFLOWER)
                .name("<gold>Add Balance Reward")
                .lore("<gray>Quickly add money using the template:")
                .lore("<white><t>", ph("t", template))
                .lore(" ")
                .lore("<yellow>Click <gray>and type an amount (e.g. 5000)")
                .lore("<dark_gray>Tip: use 'Add Command' for a custom give command.")
                .build());
        inventory.setItem(SLOT_ADD_COMMAND, ItemBuilder.of(Material.WRITABLE_BOOK)
                .name("<green>Add Command")
                .lore("<gray>Add any console command, for example:")
                .lore("<white>eco give %player% 5000")
                .lore("<white>lp user %player% parent add vip")
                .lore("<white>crate key give %player% legendary 1")
                .lore(" ")
                .lore("<yellow>Click to add")
                .build());
        inventory.setItem(SLOT_BACK, ItemBuilder.of(Material.ARROW).name("<green>Back").build());
        fill(LIST_SIZE, inventory.getSize() - 1);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) {
            return;
        }
        RedeemCode code = code();
        if (code == null) {
            viewer.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot < LIST_SIZE) {
            if (slot >= code.commands().size()) {
                return;
            }
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                code.commands().remove(slot);
                plugin.storage().save();
                render();
            } else if (event.getClick().isLeftClick()) {
                String old = code.commands().get(slot);
                plugin.chatInput().request(viewer, "Type the new command (current: " + old + "):", text -> {
                    RedeemCode c = code();
                    if (c != null && slot < c.commands().size() && c.commands().get(slot).equals(old)) {
                        c.commands().set(slot, normalise(text));
                        plugin.storage().save();
                        plugin.messages().send(viewer, "value-updated");
                    }
                    reopen();
                }, this::reopen);
            }
            return;
        }

        switch (slot) {
            case SLOT_BACK -> openLater(new EditorMenu(plugin, viewer, code));
            case SLOT_ADD_COMMAND -> {
                if (full(code)) return;
                plugin.chatInput().request(viewer, "Type the command to run (placeholders: %player%, %uuid%, %code%):",
                        text -> {
                            addCommand(normalise(text));
                            reopen();
                        }, this::reopen);
            }
            case SLOT_ADD_BALANCE -> {
                if (full(code)) return;
                plugin.chatInput().request(viewer, "Type the balance amount to give (e.g. 5000):", text -> {
                    try {
                        BigDecimal amount = new BigDecimal(text.replace(",", ""));
                        if (amount.signum() <= 0) {
                            throw new NumberFormatException();
                        }
                        String template = plugin.getConfig().getString("settings.balance-command", "eco give %player% %amount%");
                        addCommand(template.replace("%amount%", amount.stripTrailingZeros().toPlainString()));
                    } catch (NumberFormatException ex) {
                        plugin.messages().send(viewer, "input-invalid-amount");
                    }
                    reopen();
                }, this::reopen);
            }
            default -> {
            }
        }
    }

    private boolean full(RedeemCode code) {
        if (code.commands().size() >= LIST_SIZE) {
            viewer.sendMessage(Messages.mm(plugin.messages().raw("prefix") + "<red>A code can have at most " + LIST_SIZE + " commands."));
            return true;
        }
        return false;
    }

    private void addCommand(String command) {
        RedeemCode code = code();
        if (code == null || command.isEmpty()) {
            return;
        }
        code.commands().add(command);
        plugin.storage().save();
        plugin.messages().send(viewer, "value-updated");
    }

    private static String normalise(String command) {
        String c = command.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    private void reopen() {
        RedeemCode code = code();
        if (code != null && viewer.isOnline()) {
            new CommandRewardMenu(plugin, viewer, code).open();
        }
    }
}
