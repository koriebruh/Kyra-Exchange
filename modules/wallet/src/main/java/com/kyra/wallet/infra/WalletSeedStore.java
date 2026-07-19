package com.kyra.wallet.infra;

/**
 * Supplies the HD wallet's BIP39 mnemonic — the master custody secret. In prod
 * this is read from a secret store (OpenBao/Vault), never from plain config, so
 * the seed is not exposed in the repo or environment listings.
 */
public interface WalletSeedStore {

    /** The BIP39 mnemonic used to derive all custody keys. */
    String mnemonic();
}
