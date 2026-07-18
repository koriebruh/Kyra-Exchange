package com.kyra.compliance.domain;

import com.kyra.common.money.AssetId;
import com.kyra.compliance.api.AddressScreener;
import com.kyra.compliance.api.ScreeningResult;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Dev/test chain-analytics backend (kyra-doc/modules/10). A simple keyword rule
 * stands in for the real Chainalysis/TRM/Elliptic integration (which replaces
 * this bean). STILL VENDOR-BLOCKED for production (see TECHDEBT).
 */
@ApplicationScoped
public class MockAddressScreener implements AddressScreener {

    @Override
    public ScreeningResult screen(String address, AssetId asset) {
        if (address == null) {
            return ScreeningResult.BLOCK;
        }
        String a = address.toLowerCase();
        if (a.contains("sanctioned")) {
            return ScreeningResult.BLOCK;
        }
        if (a.contains("tainted") || a.contains("mixer")) {
            return ScreeningResult.HOLD;
        }
        return ScreeningResult.CLEAR;
    }
}
