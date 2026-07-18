package com.kyra.account.api;

import com.kyra.common.money.AssetId;

import java.util.regex.Pattern;

/**
 * Canonical identifier of a ledger account (kyra-doc/modules/02, chart of
 * accounts). The ledger only ever moves value between account keys — it stays
 * generic; callers build keys through the typed factories here.
 *
 * <p>Forms:
 * <ul>
 *   <li>{@code user:<userId>:<asset>:main} — a user's available balance</li>
 *   <li>{@code user:<userId>:<asset>:hold} — a user's held balance</li>
 *   <li>{@code kyra:fee:<asset>} — exchange fee income</li>
 *   <li>{@code kyra:hotwallet:<asset>} — system mirror of custody</li>
 *   <li>{@code kyra:tax:<kind>:<asset>} — tax liabilities</li>
 *   <li>{@code external:<asset>} — the outside world (deposits/withdrawals)</li>
 * </ul>
 */
public record AccountKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z]+(:[A-Za-z0-9]+){1,3}");

    public AccountKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid account key: " + value);
        }
    }

    /** Account subtype within a user account. Extensible; MARGIN is derivatives collateral. */
    public enum Type {
        MAIN("main"),
        HOLD("hold"),
        MARGIN("margin");

        private final String suffix;

        Type(String suffix) {
            this.suffix = suffix;
        }
    }

    public static AccountKey userMargin(String userId, AssetId asset) {
        return user(userId, asset, Type.MARGIN);
    }

    /** Exchange perpetual-settlement account (counterparty for realized PnL). */
    public static AccountKey perp(AssetId asset) {
        return new AccountKey("kyra:perp:" + asset);
    }

    /** Insurance fund per asset (absorbs liquidation shortfalls). */
    public static AccountKey insurance(AssetId asset) {
        return new AccountKey("kyra:insurance:" + asset);
    }

    public static AccountKey user(String userId, AssetId asset, Type type) {
        requireUserId(userId);
        return new AccountKey("user:" + userId + ":" + asset + ":" + type.suffix);
    }

    public static AccountKey userMain(String userId, AssetId asset) {
        return user(userId, asset, Type.MAIN);
    }

    public static AccountKey userHold(String userId, AssetId asset) {
        return user(userId, asset, Type.HOLD);
    }

    public static AccountKey fee(AssetId asset) {
        return new AccountKey("kyra:fee:" + asset);
    }

    public static AccountKey hotwallet(AssetId asset) {
        return new AccountKey("kyra:hotwallet:" + asset);
    }

    public static AccountKey external(AssetId asset) {
        return new AccountKey("external:" + asset);
    }

    private static void requireUserId(String userId) {
        if (userId == null || !userId.matches("[0-9A-Z]{26}")) {
            throw new IllegalArgumentException("userId must be a ULID: " + userId);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
