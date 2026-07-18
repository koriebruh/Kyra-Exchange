package com.kyra.compliance.api;

/** Outcome of AML address screening (kyra-doc/modules/10, F2). */
public enum ScreeningResult {
    CLEAR,
    /** Held for review — deposit not credited / withdraw not sent until cleared. */
    HOLD,
    /** Hard block — sanctioned/known-illicit. */
    BLOCK
}
