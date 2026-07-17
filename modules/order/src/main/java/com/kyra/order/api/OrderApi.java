package com.kyra.order.api;

import com.kyra.common.money.PairSymbol;

import java.util.List;
import java.util.Optional;

/**
 * Order intake and lifecycle (kyra-doc/modules/04). Placement validates against
 * the pair grid, holds funds, then submits to the matching engine and settles
 * any resulting trades — all serialized per pair (single writer per pair).
 */
public interface OrderApi {

    /**
     * Place a limit order.
     *
     * @return the resulting order view (may already be FILLED, OPEN, EXPIRED, …)
     * @throws OrderRejectedException if it fails intake validation
     * @throws com.kyra.account.api.InsufficientBalanceException if funds can't be held
     */
    OrderView place(PlaceOrder cmd);

    /** Cancel an open order and release its remaining hold. Idempotent. */
    void cancel(String userId, String orderId);

    Optional<OrderView> get(String userId, String orderId);

    List<OrderView> openOrders(String userId, PairSymbol pair);
}
