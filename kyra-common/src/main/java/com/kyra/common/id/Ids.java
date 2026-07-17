package com.kyra.common.id;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * ULID generation — the only ID scheme in the system (sortable, no
 * coordination, 26-char Crockford base32).
 */
public final class Ids {

    private Ids() {
    }

    public static String newUlid() {
        return UlidCreator.getMonotonicUlid().toString();
    }
}
