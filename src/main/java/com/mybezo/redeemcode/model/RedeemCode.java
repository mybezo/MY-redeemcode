package com.mybezo.redeemcode.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A redeem code with its rewards, limits and claim history.
 */
public final class RedeemCode {

    private final String name;
    private final long createdAt;
    private final String createdBy;

    private boolean enabled = true;
    private int maxClaims;          // 0 = unlimited
    private int maxPerIp;           // 0 = unlimited
    private long expiresAt;         // epoch millis, 0 = never
    private String permission;      // null/empty = none

    private final List<ItemStack> items = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private final Map<UUID, ClaimRecord> claims = new LinkedHashMap<>();

    public RedeemCode(@NotNull String name, long createdAt, @NotNull String createdBy) {
        this.name = Objects.requireNonNull(name);
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public static String key(String code) {
        return code.toLowerCase(Locale.ROOT);
    }

    public String key() {
        return key(name);
    }

    public String name() {
        return name;
    }

    public long createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int maxClaims() {
        return maxClaims;
    }

    public void maxClaims(int maxClaims) {
        this.maxClaims = Math.max(0, maxClaims);
    }

    public int maxPerIp() {
        return maxPerIp;
    }

    public void maxPerIp(int maxPerIp) {
        this.maxPerIp = Math.max(0, maxPerIp);
    }

    public long expiresAt() {
        return expiresAt;
    }

    public void expiresAt(long expiresAt) {
        this.expiresAt = Math.max(0, expiresAt);
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    public @Nullable String permission() {
        return permission == null || permission.isBlank() ? null : permission;
    }

    public void permission(@Nullable String permission) {
        this.permission = permission;
    }

    public List<ItemStack> items() {
        return Collections.unmodifiableList(items);
    }

    public void items(Collection<ItemStack> newItems) {
        items.clear();
        for (ItemStack item : newItems) {
            if (item != null && !item.isEmpty()) {
                items.add(item.clone());
            }
        }
    }

    public List<String> commands() {
        return commands;
    }

    public boolean hasRewards() {
        return !items.isEmpty() || !commands.isEmpty();
    }

    public Map<UUID, ClaimRecord> claims() {
        return claims;
    }

    public int claimCount() {
        return claims.size();
    }

    public boolean hasClaimed(UUID uuid) {
        return claims.containsKey(uuid);
    }

    public long claimsFromIp(String ip) {
        if (ip == null) {
            return 0;
        }
        return claims.values().stream().filter(c -> ip.equals(c.ip())).count();
    }

    public boolean isFull() {
        return maxClaims > 0 && claims.size() >= maxClaims;
    }
}
