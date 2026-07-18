package com.kyra.wallet.domain;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.wallet.api.CustodyProvider;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Dev/test custody backend (kyra-doc/modules/08). Deterministic addresses and
 * fake transaction refs — no network. The real Fystack / licensed-custodian
 * implementation replaces this by providing another {@link CustodyProvider}
 * bean selected by configuration; the wallet's logic does not change.
 */
@ApplicationScoped
public class MockCustodyProvider implements CustodyProvider {

    @Override
    public String depositAddress(String userId, AssetId asset) {
        return "mock-" + asset + "-" + userId.substring(0, Math.min(userId.length(), 10));
    }

    @Override
    public String submitWithdrawal(String withdrawId, AssetId asset, String toAddress, Money amount) {
        return "mocktx-" + withdrawId;
    }

    @Override
    public Money custodyBalance(AssetId asset) {
        // Reconciliation against a real custodian is out of scope for the mock.
        return Money.zero(asset);
    }
}
