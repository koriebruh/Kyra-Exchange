package com.kyra.compliance.api;

import com.kyra.common.money.AssetId;

/**
 * Abstraction over chain-analytics screening (kyra-doc/modules/10, F2). Real
 * implementations integrate Chainalysis/TRM/Elliptic; a mock backs dev/test.
 */
public interface AddressScreener {

    ScreeningResult screen(String address, AssetId asset);
}
