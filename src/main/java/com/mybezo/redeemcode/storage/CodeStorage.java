package com.mybezo.redeemcode.storage;

import com.mybezo.redeemcode.model.ClaimRecord;
import com.mybezo.redeemcode.model.RedeemCode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * YAML backed storage for redeem codes.
 * <p>
 * Data is serialised on the main thread and written to disk on a single background
 * thread, so writes never block the server tick and are always applied in order.
 * Files are written atomically (temp file + move) to avoid corruption on crashes.
 */
public final class CodeStorage {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, RedeemCode> codes = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "RedeemCode-Storage");
        thread.setDaemon(true);
        return thread;
    });

    public CodeStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "codes.yml");
    }

    public void load() {
        codes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("codes");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            try {
                RedeemCode code = read(sec);
                codes.put(code.key(), code);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load redeem code '" + id + "'", ex);
            }
        }
        plugin.getLogger().info("Loaded " + codes.size() + " redeem code(s).");
    }

    private RedeemCode read(ConfigurationSection sec) {
        String name = sec.getString("name", sec.getName());
        RedeemCode code = new RedeemCode(name, sec.getLong("created-at"), sec.getString("created-by", "CONSOLE"));
        code.enabled(sec.getBoolean("enabled", true));
        code.maxClaims(sec.getInt("max-claims", 0));
        code.maxPerIp(sec.getInt("max-per-ip", 0));
        code.expiresAt(sec.getLong("expires-at", 0));
        code.permission(sec.getString("permission", ""));
        code.commands().addAll(sec.getStringList("commands"));

        List<ItemStack> items = new ArrayList<>();
        for (String encoded : sec.getStringList("items")) {
            try {
                items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Skipping unreadable item in code '" + name + "'", ex);
            }
        }
        code.items(items);

        ConfigurationSection claims = sec.getConfigurationSection("claims");
        if (claims != null) {
            for (String uuidKey : claims.getKeys(false)) {
                ConfigurationSection c = claims.getConfigurationSection(uuidKey);
                if (c == null) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    code.claims().put(uuid, new ClaimRecord(uuid, c.getString("name", "?"),
                            c.getString("ip", ""), c.getLong("time")));
                } catch (IllegalArgumentException ignored) {
                    // invalid uuid key, skip
                }
            }
        }
        return code;
    }

    private String serialise() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "RedeemCode data file - managed by the plugin.",
                "Edit codes in-game with /redeem edit <code>. Manual edits require /redeem reload."));
        for (RedeemCode code : codes.values()) {
            String path = "codes." + code.key();
            yaml.set(path + ".name", code.name());
            yaml.set(path + ".enabled", code.enabled());
            yaml.set(path + ".created-at", code.createdAt());
            yaml.set(path + ".created-by", code.createdBy());
            yaml.set(path + ".max-claims", code.maxClaims());
            yaml.set(path + ".max-per-ip", code.maxPerIp());
            yaml.set(path + ".expires-at", code.expiresAt());
            yaml.set(path + ".permission", code.permission() == null ? "" : code.permission());
            yaml.set(path + ".commands", new ArrayList<>(code.commands()));
            List<String> items = new ArrayList<>();
            for (ItemStack item : code.items()) {
                items.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
            yaml.set(path + ".items", items);
            for (ClaimRecord claim : code.claims().values()) {
                String c = path + ".claims." + claim.uuid();
                yaml.set(c + ".name", claim.name());
                yaml.set(c + ".ip", claim.ip());
                yaml.set(c + ".time", claim.timestamp());
            }
        }
        return yaml.saveToString();
    }

    /** Schedules an asynchronous save. Must be called from the main thread. */
    public void save() {
        String data = serialise();
        writer.execute(() -> write(data));
    }

    /** Saves synchronously and stops the writer thread. Used on shutdown. */
    public void shutdown() {
        String data = serialise();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Storage writer did not finish in time.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        write(data);
    }

    /** Blocks until all pending writes are flushed. */
    public void flush() {
        try {
            writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to flush storage", ex);
        }
    }

    private void write(String data) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save codes.yml", ex);
        }
    }

    public RedeemCode get(String code) {
        return codes.get(RedeemCode.key(code));
    }

    public boolean exists(String code) {
        return codes.containsKey(RedeemCode.key(code));
    }

    public void add(RedeemCode code) {
        codes.put(code.key(), code);
        save();
    }

    public boolean remove(String code) {
        boolean removed = codes.remove(RedeemCode.key(code)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Collection<RedeemCode> all() {
        return Collections.unmodifiableCollection(codes.values());
    }
}
