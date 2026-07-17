package com.kyra.matching.api;

/**
 * How long an order stays active (kyra-doc/modules/04).
 * GTC rests; IOC fills what it can then cancels the rest; FOK fills fully or not
 * at all.
 */
public enum TimeInForce {
    GTC,
    IOC,
    FOK
}
