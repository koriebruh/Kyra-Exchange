package com.kyra.market.api;

import com.kyra.common.money.AssetId;

/**
 * Fired when an asset's status changes (kyra-doc/modules/03). Consumers: wallet
 * (stop deposit/withdraw), and — for FROZEN — pairs quoting the asset are halted.
 */
public record AssetStatusChanged(AssetId asset, AssetStatus oldStatus, AssetStatus newStatus) {
}
