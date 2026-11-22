package com.mybezo.redeemcode;

import com.mybezo.redeemcode.command.RedeemCommand;
import com.mybezo.redeemcode.gui.Menu;
import com.mybezo.redeemcode.gui.MenuListener;
import com.mybezo.redeemcode.input.ChatInputManager;
import com.mybezo.redeemcode.service.RedeemService;
import com.mybezo.redeemcode.storage.CodeStorage;
import com.mybezo.redeemcode.util.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * RedeemCode - main plugin class.
 */
public final class RedeemCodePlugin extends JavaPlugin implements Listener {

    private final Messages messages = new Messages();
    private CodeStorage storage;
    private RedeemService redeemService;
    private ChatInputManager chatInput;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        messages.load(getConfig());

        storage = new CodeStorage(this);
        storage.load();
        redeemService = new RedeemService(this);
        chatInput = new ChatInputManager(this);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(this, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new RedeemCommand(this).build(),
                        "Redeem codes and manage them.", List.of("redeemcode")));

        getLogger().info("RedeemCode enabled.");
    }

    @Override
    public void onDisable() {
        // Close open editors so pending item edits are saved.
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
        }
        if (chatInput != null) {
            chatInput.shutdown();
        }
        if (storage != null) {
            storage.shutdown();
        }
    }

    public void reload() {
        reloadConfig();
        messages.load(getConfig());
        storage.flush();
        storage.load();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        redeemService.forget(event.getPlayer().getUniqueId());
    }

    public Messages messages() {
        return messages;
    }

    public CodeStorage storage() {
        return storage;
    }

    public RedeemService redeemService() {
        return redeemService;
    }

    public ChatInputManager chatInput() {
        return chatInput;
    }
}
