package com.kyra.derivatives.api;

import com.kyra.common.money.AssetId;

import java.math.BigDecimal;

/**
 * An open perpetual position (kyra-doc/modules/09 Part B).
 *
 * @param positionId      id
 * @param userId          owner
 * @param symbol          perpetual symbol
 * @param side            LONG or SHORT
 * @param size            contract size in base units (positive)
 * @param entryPrice      average entry price (quote per base)
 * @param margin          collateral locked, in the collateral asset
 * @param collateralAsset the margin/settlement asset
 */
public record Position(
        String positionId,
        String userId,
        String symbol,
        PositionSide side,
        BigDecimal size,
        BigDecimal entryPrice,
        BigDecimal margin,
        AssetId collateralAsset) {

    /** Unrealized PnL at a mark price, in the collateral asset. */
    public BigDecimal unrealizedPnl(BigDecimal markPrice) {
        return markPrice.subtract(entryPrice).multiply(size).multiply(BigDecimal.valueOf(side.sign()));
    }

    /** Equity = margin + unrealized PnL. */
    public BigDecimal equity(BigDecimal markPrice) {
        return margin.add(unrealizedPnl(markPrice));
    }
}
