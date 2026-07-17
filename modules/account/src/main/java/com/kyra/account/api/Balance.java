package com.kyra.account.api;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

/** A user's balance in one asset: freely spendable plus reserved. */
public record Balance(AssetId asset, Money available, Money onHold) {

    public Money total() {
        return available.plus(onHold);
    }
}
