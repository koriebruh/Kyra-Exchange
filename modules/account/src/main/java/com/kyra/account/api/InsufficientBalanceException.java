package com.kyra.account.api;

/**
 * Thrown when a journal would drive an account negative. The posting is rolled
 * back in full — a journal never applies partially.
 */
public class InsufficientBalanceException extends RuntimeException {

    public static final String CODE = "INSUFFICIENT_BALANCE";

    private final String accountKey;

    public InsufficientBalanceException(String accountKey) {
        super("insufficient balance in account " + accountKey);
        this.accountKey = accountKey;
    }

    public String accountKey() {
        return accountKey;
    }
}
