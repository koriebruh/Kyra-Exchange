package com.kyra.wallet.infra;

import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;

/**
 * BIP32/44 hierarchical-deterministic key derivation for EVM custody (self-custody
 * via web3j). One mnemonic seed derives a distinct key per index along the
 * Ethereum path {@code m/44'/60'/0'/0/index}, so each user gets a unique,
 * reproducible deposit address and the exchange holds a single seed.
 *
 * <p>The seed/mnemonic is the master secret — sourced from a secret store
 * ({@link WalletSeedStore}), never from plain config in prod.
 */
public final class HdWallet {

    private static final int HARDENED = Bip32ECKeyPair.HARDENED_BIT;

    private HdWallet() {
    }

    /** Credentials (address + signing key) at the Ethereum BIP44 path for {@code index}. */
    public static Credentials deriveCredentials(String mnemonic, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("HD index must be non-negative");
        }
        byte[] seed = MnemonicUtils.generateSeed(mnemonic, null);
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
        int[] path = { 44 | HARDENED, 60 | HARDENED, 0 | HARDENED, 0, index };
        Bip32ECKeyPair derived = Bip32ECKeyPair.deriveKeyPair(master, path);
        return Credentials.create(derived);
    }

    /** The checksummed EVM address at {@code index}. */
    public static String deriveAddress(String mnemonic, int index) {
        return deriveCredentials(mnemonic, index).getAddress();
    }
}
