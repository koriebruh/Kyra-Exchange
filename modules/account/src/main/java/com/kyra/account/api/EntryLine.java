package com.kyra.account.api;

import com.kyra.common.money.Money;

/**
 * One signed leg of a journal: a positive amount credits {@code account},
 * a negative amount debits it. A journal's lines must sum to zero per asset.
 */
public record EntryLine(AccountKey account, Money amount) {

    public EntryLine {
        if (account == null || amount == null) {
            throw new IllegalArgumentException("account and amount are required");
        }
        if (amount.isZero()) {
            throw new IllegalArgumentException("entry amount must be non-zero: " + account);
        }
    }

    public static EntryLine of(AccountKey account, Money amount) {
        return new EntryLine(account, amount);
    }
}
