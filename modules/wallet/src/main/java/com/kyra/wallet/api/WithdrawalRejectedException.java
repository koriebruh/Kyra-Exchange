package com.kyra.wallet.api;

/**
 * A withdrawal was refused at request time by a compliance gate (kyra-doc/
 * modules/08, /10): insufficient KYC or a screened destination address.
 * {@code code} is a stable API error code.
 */
public class WithdrawalRejectedException extends RuntimeException {

    private final String code;

    public WithdrawalRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
