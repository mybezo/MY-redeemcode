package com.mybezo.redeemcode.service;

import com.mybezo.redeemcode.RedeemCodePlugin;
import com.mybezo.redeemcode.model.ClaimRecord;
import com.mybezo.redeemcode.model.RedeemCode;
import com.mybezo.redeemcode.util.Messages;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static com.mybezo.redeemcode.util.Messages.ph;

/**
 * Validates and executes code redemptions.
 */
public final class RedeemService {

    private static final Sound SUCCESS = Sound.sound(
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 1.2f);
    private static final Sound FAIL = Sound.sound(
            org.bukkit.Sound.ENTITY_VILLAGER_NO, Sound.Source.MASTER, 1f, 1f);

    private final RedeemCodePlugin plugin;
    private final Map<UUID, Long> invalidCooldown = new ConcurrentHashMap<>();

    public RedeemService(RedeemCodePlugin plugin) {
        this.plugin = plugin;
    }

    public void redeem(Player player, String input) {
        Messages msg = plugin.messages();

        long now = System.currentTimeMillis();
        Long until = invalidCooldown.get(player.getUniqueId());
        if (until != null && until > now && !player.hasPermission("redeemcode.bypass.cooldown")) {
            long seconds = Math.max(1, (until - now + 999) / 1000);
            msg.send(player, "cooldown", ph("seconds", seconds));
            fail(player);
            return;
        }

        RedeemCode code = plugin.storage().get(input);
        if (code == null) {
            int cd = plugin.getConfig().getInt("settings.invalid-attempt-cooldown", 3);
            if (cd > 0) {
                invalidCooldown.put(player.getUniqueId(), now + cd * 1000L);
            }
            msg.send(player, "invalid-code");
            fail(player);
            return;
        }

        String reason = check(player, code);
        if (reason != null) {
            msg.send(player, reason, ph("code", code.name()));
            fail(player);
            return;
        }

        // Inventory space check (CANCEL mode)
        boolean cancelWhenFull = "CANCEL".equalsIgnoreCase(
                plugin.getConfig().getString("settings.full-inventory-action", "DROP"));
        if (cancelWhenFull && !code.items().isEmpty()) {
            int missing = missingSlots(player.getInventory(), code);
            if (missing > 0) {
                msg.send(player, "inventory-full", ph("slots", missing));
                fail(player);
                return;
            }
        }

        // Record the claim FIRST, so a failing reward can never be exploited for duplicate claims.
        code.claims().put(player.getUniqueId(),
                new ClaimRecord(player.getUniqueId(), player.getName(), ip(player), now));
        plugin.storage().save();

        giveItems(player, code);
        runCommands(player, code);

        msg.send(player, "redeem-success", ph("code", code.name()));
        if (plugin.getConfig().getBoolean("settings.broadcast-on-redeem", false)) {
            Bukkit.getServer().sendMessage(msg.get("prefix").append(msg.get("redeem-broadcast",
                    ph("player", player.getName()), ph("code", code.name()))));
        }
        if (sounds()) {
            player.playSound(SUCCESS);
        }
        plugin.getLogger().info(player.getName() + " (" + ip(player) + ") redeemed code " + code.name()
                + " [" + code.claimCount() + (code.maxClaims() > 0 ? "/" + code.maxClaims() : "") + "]");
    }

    /** @return message key describing why the player can't redeem, or null when allowed. */
    private String check(Player player, RedeemCode code) {
        if (!code.enabled()) return "code-disabled";
        if (code.isExpired()) return "code-expired";
        if (!code.hasRewards()) return "code-no-rewards";
        if (code.permission() != null && !player.hasPermission(code.permission())) return "code-no-permission";
        if (code.hasClaimed(player.getUniqueId())) return "already-redeemed";
        if (code.isFull()) return "max-claims-reached";
        if (code.maxPerIp() > 0 && code.claimsFromIp(ip(player)) >= code.maxPerIp()) return "ip-limit-reached";
        return null;
    }

    private void giveItems(Player player, RedeemCode code) {
        boolean dropped = false;
        for (ItemStack item : code.items()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), rest, drop -> drop.setOwner(player.getUniqueId()));
                dropped = true;
            }
        }
        if (dropped) {
            plugin.messages().send(player, "items-dropped");
        }
    }

    private void runCommands(Player player, RedeemCode code) {
        for (String raw : code.commands()) {
            String command = raw
                    .replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%code%", code.name());
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    plugin.getLogger().warning("Reward command of code " + code.name() + " failed: " + command);
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Error while running reward command: " + command, ex);
            }
        }
    }

    /** Number of additional empty slots needed to fit all reward items. */
    private int missingSlots(PlayerInventory inventory, RedeemCode code) {
        ItemStack[] copy = new ItemStack[36];
        ItemStack[] storage = inventory.getStorageContents();
        for (int i = 0; i < Math.min(36, storage.length); i++) {
            copy[i] = storage[i] == null ? null : storage[i].clone();
        }
        int missing = 0;
        for (ItemStack reward : code.items()) {
            int amount = reward.getAmount();
            int max = reward.getMaxStackSize();
            for (int i = 0; i < copy.length && amount > 0; i++) {
                ItemStack slot = copy[i];
                if (slot != null && slot.isSimilar(reward) && slot.getAmount() < max) {
                    int add = Math.min(amount, max - slot.getAmount());
                    slot.setAmount(slot.getAmount() + add);
                    amount -= add;
                }
            }
            for (int i = 0; i < copy.length && amount > 0; i++) {
                if (copy[i] == null || copy[i].isEmpty()) {
                    ItemStack placed = reward.clone();
                    int add = Math.min(amount, max);
                    placed.setAmount(add);
                    copy[i] = placed;
                    amount -= add;
                }
            }
            if (amount > 0) {
                missing += (amount + max - 1) / max;
            }
        }
        return missing;
    }

    public static String ip(Player player) {
        InetSocketAddress address = player.getAddress();
        if (address == null || address.getAddress() == null) {
            return "unknown";
        }
        return address.getAddress().getHostAddress();
    }

    private boolean sounds() {
        return plugin.getConfig().getBoolean("settings.sounds", true);
    }

    private void fail(Player player) {
        if (sounds()) {
            player.playSound(FAIL);
        }
    }

    public void forget(UUID uuid) {
        invalidCooldown.remove(uuid);
    }
}
