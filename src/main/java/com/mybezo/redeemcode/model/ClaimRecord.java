package com.mybezo.redeemcode.model;

import java.util.UUID;

/**
 * A single successful redemption of a code.
 */
public record ClaimRecord(UUID uuid, String name, String ip, long timestamp) {
}
