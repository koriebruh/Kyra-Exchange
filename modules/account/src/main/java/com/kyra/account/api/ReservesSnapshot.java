package com.kyra.account.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

/**
 * A point-in-time proof-of-reserves snapshot for one asset (kyra-doc/modules/16
 * §7). The Merkle root commits to every user's balance so each user can verify
 * their balance is included; {@code totalLiabilities} is what custody must cover.
 *
 * @param asset            the asset
 * @param totalLiabilities sum of all user balances (main + hold)
 * @param merkleRoot       hex SHA-256 root over per-user balance leaves
 * @param leafCount        number of user accounts included
 */
public record ReservesSnapshot(AssetId asset, Money totalLiabilities, String merkleRoot, int leafCount) {
}
