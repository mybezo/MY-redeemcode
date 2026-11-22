package com.mybezo.redeemcode.input;

import com.mybezo.redeemcode.RedeemCodePlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.mybezo.redeemcode.util.Messages.ph;

/**
 * Captures a single chat message from a player and hands it to a callback on the main thread.
 */
public final class ChatInputManager implements Listener {

    private final RedeemCodePlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    private record Pending(Consumer<String> onInput, Runnable onCancel, BukkitTask timeout) {
    }

    public ChatInputManager(RedeemCodePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * @param onInput  called on the main thread with the typed text
     * @param onCancel called on the main thread when the player types "cancel" or the prompt times out
     */
    public void request(Player player, String prompt, Consumer<String> onInput, Runnable onCancel) {
        cancel(player.getUniqueId());
        int seconds = Math.max(5, plugin.getConfig().getInt("settings.chat-input-timeout", 60));
        player.closeInventory();
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Pending p = pending.remove(player.getUniqueId());
            if (p != null && player.isOnline()) {
                plugin.messages().send(player, "input-timeout");
                p.onCancel().run();
            }
        }, seconds * 20L);
        pending.put(player.getUniqueId(), new Pending(onInput, onCancel, task));
        plugin.messages().send(player, "input-prompt", ph("prompt", prompt), ph("time", seconds));
    }

    public void cancel(UUID uuid) {
        Pending p = pending.remove(uuid);
        if (p != null) {
            p.timeout().cancel();
        }
    }

    public boolean isWaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null) {
            return;
        }
        event.setCancelled(true);
        p.timeout().cancel();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(player, "input-cancelled");
                p.onCancel().run();
            } else {
                p.onInput().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        pending.values().forEach(p -> p.timeout().cancel());
        pending.clear();
    }
}
