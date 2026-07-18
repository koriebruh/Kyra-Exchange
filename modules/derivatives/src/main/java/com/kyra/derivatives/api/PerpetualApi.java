package com.kyra.derivatives.api;

import com.kyra.common.money.Money;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Perpetual futures (kyra-doc/modules/09 Part B): open/close positions with
 * margin collateral, mark-price PnL, and maintenance-margin liquidation. Every
 * money movement is a balanced ledger journal; the insurance fund backstops
 * losses that exceed a position's margin so a user's balance never goes negative.
 */
public interface PerpetualApi {

    /**
     * Open a position, locking {@code margin} from the user's main balance into
     * their margin account.
     *
     * @return the position id
     * @throws com.kyra.account.api.InsufficientBalanceException if margin can't be held
     */
    String openPosition(String userId, String symbol, PositionSide side,
            BigDecimal size, BigDecimal entryPrice, Money margin);

    /** Close a position at the current mark price, realizing PnL and releasing margin. */
    void closePosition(String positionId);

    /**
     * Partially close a position: realize PnL on {@code closeSize} and release the
     * proportional margin; the remainder stays open at the same entry price.
     * Closing the full size behaves like {@link #closePosition}.
     *
     * @throws IllegalArgumentException if closeSize is not in (0, position size]
     */
    void reducePosition(String positionId, java.math.BigDecimal closeSize);

    /**
     * Apply a funding round to all open positions on a symbol (kyra-doc/modules/09
     * Part B): with a positive rate longs pay shorts, settled against margin.
     * Idempotent by {@code roundId}. Returns the number of positions funded.
     */
    int applyFunding(String symbol, java.math.BigDecimal fundingRate, String roundId);

    /**
     * Liquidate the position if its equity has fallen to/below the maintenance
     * margin (marked to the current mark price). Returns true if liquidated.
     */
    boolean liquidateIfUnderwater(String positionId);

    Optional<Position> position(String positionId);
}
