package com.mybezo.redeemcode.gui;

import com.mybezo.redeemcode.RedeemCodePlugin;
import com.mybezo.redeemcode.model.RedeemCode;
import com.mybezo.redeemcode.util.ItemBuilder;
import com.mybezo.redeemcode.util.Messages;
import com.mybezo.redeemcode.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;

import static com.mybezo.redeemcode.util.Messages.ph;

/**
 * Main editor of a redeem code.
 */
public final class EditorMenu extends Menu {

    private static final int SLOT_INFO = 4;
    private static final int SLOT_ITEMS = 19;
    private static final int SLOT_COMMANDS = 21;
    private static final int SLOT_MAX_CLAIMS = 23;
    private static final int SLOT_MAX_IP = 25;
    private static final int SLOT_EXPIRY = 29;
    private static final int SLOT_PERMISSION = 31;
    private static final int SLOT_STATUS = 33;
    private static final int SLOT_DELETE = 36;
    private static final int SLOT_CLOSE = 40;

    private final String codeKey;

    public EditorMenu(RedeemCodePlugin plugin, Player viewer, RedeemCode code) {
        super(plugin, viewer, 5, Messages.gui("<dark_gray>Editing code: <gold><code>", ph("code", code.name())));
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

        String max = code.maxClaims() == 0 ? "Unlimited" : String.valueOf(code.maxClaims());
        String perIp = code.maxPerIp() == 0 ? "Unlimited" : String.valueOf(code.maxPerIp());
        String expiry = code.expiresAt() == 0 ? "Never"
                : code.isExpired() ? "Expired (" + TimeUtil.formatDate(code.expiresAt()) + ")"
                : TimeUtil.formatDate(code.expiresAt()) + " (in " + TimeUtil.formatDuration(code.expiresAt() - System.currentTimeMillis()) + ")";

        inventory.setItem(SLOT_INFO, ItemBuilder.of(Material.NAME_TAG)
                .name("<gold><bold><code>", ph("code", code.name()))
                .lore("<gray>Status: " + (code.enabled() ? "<green>Enabled" : "<red>Disabled"))
                .lore("<gray>Claims: <white><claims></white>/<white><max>", ph("claims", code.claimCount()), ph("max", max))
                .lore("<gray>Item rewards: <white><n>", ph("n", code.items().size()))
                .lore("<gray>Command rewards: <white><n>", ph("n", code.commands().size()))
                .lore("<gray>Created by <white><by></white> on <white><date>",
                        ph("by", code.createdBy()), ph("date", TimeUtil.formatDate(code.createdAt())))
                .lore(" ")
                .lore("<dark_gray>Changes are saved automatically.")
                .glow(true)
                .build());

        inventory.setItem(SLOT_ITEMS, ItemBuilder.of(Material.CHEST)
                .name("<gold>Item Rewards")
                .lore("<gray>Items given to the player.")
                .lore("<gray>Current: <white><n> item stack(s)", ph("n", code.items().size()))
                .lore(" ")
                .lore("<yellow>Click to edit")
                .build());

        ItemBuilder commands = ItemBuilder.of(Material.COMMAND_BLOCK)
                .name("<gold>Command & Balance Rewards")
                .lore("<gray>Console commands run on redeem,")
                .lore("<gray>e.g. <white>eco give %player% 5000")
                .lore(" ");
        if (code.commands().isEmpty()) {
            commands.lore("<dark_gray>No commands yet.");
        } else {
            int shown = 0;
            for (String cmd : code.commands()) {
                if (shown++ == 5) {
                    commands.lore("<dark_gray>... and <n> more", ph("n", code.commands().size() - 5));
                    break;
                }
                commands.lore("<dark_gray>» <white><cmd>", ph("cmd", cmd));
            }
        }
        inventory.setItem(SLOT_COMMANDS, commands.lore(" ").lore("<yellow>Click to edit").build());

        inventory.setItem(SLOT_MAX_CLAIMS, ItemBuilder.of(Material.PLAYER_HEAD)
                .name("<gold>Max Total Claims")
                .lore("<gray>How many players can redeem this code.")
                .lore("<gray>Current: <white><v>", ph("v", max))
                .lore(" ")
                .lore("<yellow>Left-click <gray>to set a value")
                .lore("<yellow>Right-click <gray>to set unlimited")
                .build());

        inventory.setItem(SLOT_MAX_IP, ItemBuilder.of(Material.COMPASS)
                .name("<gold>Limit Per IP")
                .lore("<gray>Max redemptions from the same IP address.")
                .lore("<gray>Prevents alt-account abuse.")
                .lore("<gray>Current: <white><v>", ph("v", perIp))
                .lore(" ")
                .lore("<yellow>Left-click <gray>to set a value")
                .lore("<yellow>Right-click <gray>to set unlimited")
                .build());

        inventory.setItem(SLOT_EXPIRY, ItemBuilder.of(Material.CLOCK)
                .name("<gold>Expiration")
                .lore("<gray>Current: <white><v>", ph("v", expiry))
                .lore(" ")
                .lore("<yellow>Left-click <gray>to set a duration (e.g. 7d)")
                .lore("<yellow>Right-click <gray>to never expire")
                .build());

        inventory.setItem(SLOT_PERMISSION, ItemBuilder.of(Material.TRIPWIRE_HOOK)
                .name("<gold>Required Permission")
                .lore("<gray>Only players with this permission")
                .lore("<gray>can redeem the code (e.g. VIP codes).")
                .lore("<gray>Current: <white><v>", ph("v", code.permission() == null ? "None" : code.permission()))
                .lore(" ")
                .lore("<yellow>Left-click <gray>to set a permission")
                .lore("<yellow>Right-click <gray>to remove")
                .build());

        inventory.setItem(SLOT_STATUS, ItemBuilder.of(code.enabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(code.enabled() ? "<green>Enabled" : "<red>Disabled")
                .lore("<gray>Disabled codes cannot be redeemed.")
                .lore(" ")
                .lore("<yellow>Click to toggle")
                .build());

        inventory.setItem(SLOT_DELETE, ItemBuilder.of(Material.LAVA_BUCKET)
                .name("<red>Delete Code")
                .lore("<gray>Permanently deletes this code")
                .lore("<gray>and all of its claim data.")
                .lore(" ")
                .lore("<red>Shift + Right-click to confirm")
                .build());

        inventory.setItem(SLOT_CLOSE, ItemBuilder.of(Material.BARRIER).name("<red>Close").build());
        fill(0, inventory.getSize() - 1);
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
        ClickType click = event.getClick();
        boolean right = click.isRightClick();

        switch (event.getRawSlot()) {
            case SLOT_ITEMS -> openLater(new ItemRewardMenu(plugin, viewer, code));
            case SLOT_COMMANDS -> openLater(new CommandRewardMenu(plugin, viewer, code));
            case SLOT_MAX_CLAIMS -> {
                if (right) {
                    update(c -> c.maxClaims(0));
                } else {
                    askNumber("Type the maximum number of total claims (0 = unlimited):", c -> c::maxClaims);
                }
            }
            case SLOT_MAX_IP -> {
                if (right) {
                    update(c -> c.maxPerIp(0));
                } else {
                    askNumber("Type the maximum claims per IP address (0 = unlimited):", c -> c::maxPerIp);
                }
            }
            case SLOT_EXPIRY -> {
                if (right) {
                    update(c -> c.expiresAt(0));
                } else {
                    plugin.chatInput().request(viewer, "Type how long the code stays valid (e.g. 30m, 12h, 7d, never):", text -> {
                        long millis = TimeUtil.parseDuration(text);
                        if (millis < 0) {
                            plugin.messages().send(viewer, "input-invalid-duration");
                        } else {
                            apply(c -> c.expiresAt(millis == 0 ? 0 : System.currentTimeMillis() + millis));
                        }
                        reopen();
                    }, this::reopen);
                }
            }
            case SLOT_PERMISSION -> {
                if (right) {
                    update(c -> c.permission(null));
                } else {
                    plugin.chatInput().request(viewer, "Type the required permission node (or 'none'):", text -> {
                        String perm = text.split("\\s+")[0];
                        apply(c -> c.permission(perm.equalsIgnoreCase("none") ? null : perm));
                        reopen();
                    }, this::reopen);
                }
            }
            case SLOT_STATUS -> update(c -> c.enabled(!c.enabled()));
            case SLOT_DELETE -> {
                if (click == ClickType.SHIFT_RIGHT) {
                    plugin.storage().remove(codeKey);
                    viewer.closeInventory();
                    plugin.messages().send(viewer, "code-deleted", ph("code", code.name()));
                }
            }
            case SLOT_CLOSE -> viewer.closeInventory();
            default -> {
            }
        }
    }

    private void askNumber(String prompt, java.util.function.Function<RedeemCode, java.util.function.IntConsumer> setter) {
        plugin.chatInput().request(viewer, prompt, text -> {
            try {
                int value = Integer.parseInt(text.replace(",", "").replace(".", ""));
                if (value < 0) {
                    throw new NumberFormatException();
                }
                apply(c -> setter.apply(c).accept(value));
            } catch (NumberFormatException ex) {
                plugin.messages().send(viewer, "input-invalid-number");
            }
            reopen();
        }, this::reopen);
    }

    /** Applies a change, saves and re-renders the open menu. */
    private void update(Consumer<RedeemCode> change) {
        if (apply(change)) {
            render();
        }
    }

    private boolean apply(Consumer<RedeemCode> change) {
        RedeemCode code = code();
        if (code == null) {
            plugin.messages().send(viewer, "code-not-found", ph("code", codeKey));
            return false;
        }
        change.accept(code);
        plugin.storage().save();
        return true;
    }

    private void reopen() {
        RedeemCode code = code();
        if (code != null && viewer.isOnline()) {
            new EditorMenu(plugin, viewer, code).open();
        }
    }
}
