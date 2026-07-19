package com.kyra.wallet.domain;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.wallet.api.CustodyProvider;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default custody backend (kyra-doc/modules/08). Deterministic addresses and
 * fake transaction refs — no network. As a {@link DefaultBean} it backs tests
 * and any run where {@code kyra.custody.provider} is not {@code fystack};
 * setting that property activates {@link com.kyra.wallet.infra.HttpCustodyProvider}
 * (real Fystack Apex API) instead. The wallet's logic does not change.
 */
@ApplicationScoped
@DefaultBean
public class MockCustodyProvider implements CustodyProvider {

    @Override
    public String depositAddress(String userId, AssetId asset) {
        return "mock-" + asset + "-" + userId.substring(0, Math.min(userId.length(), 10));
    }

    private final ConcurrentMap<String, BigDecimal> balances = new ConcurrentHashMap<>();

    @Override
    public String submitWithdrawal(String withdrawId, AssetId asset, String toAddress, Money amount) {
        return "mocktx-" + withdrawId;
    }

    @Override
    public Money custodyBalance(AssetId asset) {
        return Money.of(asset, balances.getOrDefault(asset.symbol(), BigDecimal.ZERO));
    }

    /** Set the custodian's tracked balance for an asset (tests). */
    public void setBalance(AssetId asset, BigDecimal amount) {
        balances.put(asset.symbol(), amount);
    }
}
