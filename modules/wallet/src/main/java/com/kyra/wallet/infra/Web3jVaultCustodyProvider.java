package com.kyra.wallet.infra;

import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.wallet.api.CustodyProvider;

import org.jboss.logging.Logger;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Self-custody over an EVM chain via web3j (kyra-doc/modules/08). Active only when
 * {@code kyra.custody.provider=web3j}; otherwise {@link com.kyra.wallet.domain.MockCustodyProvider}
 * (a {@code @DefaultBean}) is used. The signing seed comes from {@link WalletSeedStore}
 * (OpenBao); addresses/keys are derived with {@link HdWallet}.
 *
 * <ul>
 *   <li><b>Deposit:</b> each user gets a stable HD index → a reproducible per-user
 *       address, so deposits are attributable.</li>
 *   <li><b>Withdrawal:</b> a native transfer signed from the hot wallet and
 *       broadcast; idempotent by {@code withdrawId} (a recorded id returns its
 *       existing tx hash instead of re-broadcasting).</li>
 *   <li><b>Balance:</b> the hot wallet's on-chain balance.</li>
 * </ul>
 *
 * <p><b>Scope / gaps (kyra-doc/TECHDEBT.md):</b> this handles the chain's <i>native</i>
 * coin (proves HD derivation, secp256k1 signing, broadcast, balance end-to-end on
 * Anvil). ERC-20 tokens (e.g. USDT) need a per-asset contract address + a
 * {@code transfer(...)} call — the immediate follow-up. Deposit detection (polling)
 * and the broadcast↔record atomicity are also documented follow-ups. <b>Self-custody
 * means Kyra holds the key</b>; production key security (hot/cold split, OpenBao
 * unseal, backups) is on the operator.
 */
public class Web3jVaultCustodyProvider implements CustodyProvider {

    private static final Logger LOG = Logger.getLogger(Web3jVaultCustodyProvider.class);
    private static final int NATIVE_DECIMALS = 18;

    private final Web3jConfig config;
    private final WalletSeedStore seedStore;
    private final Web3jCustodyStore store;
    private final Web3j web3j;

    public Web3jVaultCustodyProvider(Web3jConfig config, WalletSeedStore seedStore, Web3jCustodyStore store) {
        this(config, seedStore, store, Web3j.build(new HttpService(
                config.rpcUrl().orElseThrow(() -> new IllegalStateException("kyra.custody.web3j.rpc-url not configured")))));
    }

    /** For tests: inject a Web3j pointed at a local chain. */
    Web3jVaultCustodyProvider(Web3jConfig config, WalletSeedStore seedStore, Web3jCustodyStore store, Web3j web3j) {
        this.config = config;
        this.seedStore = seedStore;
        this.store = store;
        this.web3j = web3j;
    }

    @Override
    public String depositAddress(String userId, AssetId asset) {
        long index = store.indexFor(userId);
        return HdWallet.deriveAddress(seedStore.mnemonic(), (int) index);
    }

    @Override
    public String submitWithdrawal(String withdrawId, AssetId asset, String toAddress, Money amount) {
        Optional<String> already = store.txFor(withdrawId);
        if (already.isPresent()) {
            return already.get(); // idempotent: never broadcast twice
        }
        Credentials hot = HdWallet.deriveCredentials(seedStore.mnemonic(), config.hotWalletIndex());
        BigInteger value = amount.amount().movePointRight(NATIVE_DECIMALS).toBigIntegerExact();
        try {
            BigInteger nonce = web3j.ethGetTransactionCount(hot.getAddress(), DefaultBlockParameterName.PENDING)
                    .send().getTransactionCount();
            BigInteger gasPrice = config.gasPriceWei().map(BigInteger::new)
                    .orElseGet(() -> {
                        try {
                            return web3j.ethGasPrice().send().getGasPrice();
                        } catch (java.io.IOException e) {
                            throw new IllegalStateException("failed to read gas price", e);
                        }
                    });
            RawTransaction raw = RawTransaction.createEtherTransaction(
                    nonce, gasPrice, BigInteger.valueOf(config.gasLimit()), toAddress, value);
            byte[] signed = TransactionEncoder.signMessage(raw, config.chainId(), hot);
            EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
            if (sent.hasError()) {
                throw new IllegalStateException("withdrawal broadcast rejected: " + sent.getError().getMessage());
            }
            String txHash = sent.getTransactionHash();
            store.recordTx(withdrawId, txHash);
            LOG.infof("web3j withdrawal broadcast: withdrawId=%s tx=%s", withdrawId, txHash);
            return txHash;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("withdrawal broadcast failed for " + withdrawId, e);
        }
    }

    @Override
    public Money custodyBalance(AssetId asset) {
        String hotAddress = HdWallet.deriveAddress(seedStore.mnemonic(), config.hotWalletIndex());
        try {
            BigInteger wei = web3j.ethGetBalance(hotAddress, DefaultBlockParameterName.LATEST)
                    .send().getBalance();
            return Money.of(asset, new BigDecimal(wei).movePointLeft(NATIVE_DECIMALS));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to read custody balance", e);
        }
    }
}
