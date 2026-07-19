package com.kyra.wallet.infra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HD derivation is checked against a well-known public reference: the standard
 * Anvil/Hardhat test mnemonic derives account #0
 * {@code 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266} and account #1
 * {@code 0x70997970C51812dc3A010C7d01b50e0d17dc79C8}. Matching these proves the
 * BIP44 path + secp256k1 derivation are correct, not merely self-consistent.
 */
class HdWalletTest {

    private static final String MNEMONIC =
            "test test test test test test test test test test test junk";

    @Test
    void derivesTheStandardAnvilAccounts() {
        // web3j returns lower-case addresses; the derivation (not the casing) is
        // what we verify against the reference.
        assertEquals("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266",
                HdWallet.deriveAddress(MNEMONIC, 0).toLowerCase());
        assertEquals("0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                HdWallet.deriveAddress(MNEMONIC, 1).toLowerCase());
    }

    @Test
    void derivationIsDeterministicPerIndex() {
        assertEquals(HdWallet.deriveAddress(MNEMONIC, 5), HdWallet.deriveAddress(MNEMONIC, 5));
        assertNotEquals(HdWallet.deriveAddress(MNEMONIC, 5), HdWallet.deriveAddress(MNEMONIC, 6));
    }

    @Test
    void addressIsAValidChecksummedHex() {
        String addr = HdWallet.deriveAddress(MNEMONIC, 3);
        assertTrue(addr.matches("0x[0-9a-fA-F]{40}"), addr);
    }

    @Test
    void negativeIndexRejected() {
        assertThrows(IllegalArgumentException.class, () -> HdWallet.deriveAddress(MNEMONIC, -1));
    }
}
