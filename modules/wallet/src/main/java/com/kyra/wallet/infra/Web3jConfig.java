package com.kyra.wallet.infra;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Settings for the self-custody web3j provider (EVM). Consulted only when
 * {@code kyra.custody.provider=web3j}. The signing seed itself is NOT here — it
 * comes from {@link WalletSeedStore} (OpenBao) so the mnemonic never sits in
 * plain config.
 */
@ConfigMapping(prefix = "kyra.custody.web3j")
public interface Web3jConfig {

    /** JSON-RPC endpoint, e.g. {@code http://localhost:8545} (Anvil) or a node/provider URL. */
    Optional<String> rpcUrl();

    /** EVM chain id (Anvil dev = 31337). */
    @WithDefault("31337")
    long chainId();

    /** Gas limit for a plain native transfer. */
    @WithDefault("21000")
    long gasLimit();

    /** Gas price in wei; if absent the node's suggestion is used. */
    Optional<String> gasPriceWei();

    /** HD index of the exchange hot wallet (funds are paid out from here). */
    @WithDefault("0")
    int hotWalletIndex();
}
