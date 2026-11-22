package com.mybezo.redeemcode.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads messages from config.yml and renders them with MiniMessage.
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private FileConfiguration config;

    public void load(FileConfiguration config) {
        this.config = config;
    }

    /** Builds an unparsed (safe) placeholder. User input is never parsed as MiniMessage. */
    public static TagResolver ph(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver phc(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static Component mm(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers);
    }

    /** MiniMessage for GUI text, with the default item italics removed. */
    public static Component gui(String raw, TagResolver... resolvers) {
        return MM.deserialize(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public String raw(String key) {
        return config.getString("messages." + key, "<red>Missing message: " + key);
    }

    public Component get(String key, TagResolver... resolvers) {
        return mm(raw(key), resolvers);
    }

    public void send(Audience audience, String key, TagResolver... resolvers) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        audience.sendMessage(mm(raw("prefix") + raw, resolvers));
    }

    public void sendRaw(Audience audience, String key, TagResolver... resolvers) {
        audience.sendMessage(get(key, resolvers));
    }

    public List<Component> list(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : config.getStringList("messages." + key)) {
            out.add(mm(line, resolvers));
        }
        return out;
    }
}
