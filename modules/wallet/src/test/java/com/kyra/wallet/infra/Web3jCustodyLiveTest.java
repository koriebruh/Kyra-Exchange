package com.kyra.wallet.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;

import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end proof of the web3j self-custody provider against a REAL local Anvil
 * chain, and of the OpenBao seed store against a REAL OpenBao. These run only
 * when those services are reachable (docker-compose.dev.yml up); in CI, where
 * they are not, the assumptions skip the tests instead of failing.
 *
 * <p>Uses Anvil's standard test mnemonic, so the hot wallet is prefunded account #0
 * ({@code 0xf39F...}, 10000 ETH).
 */
class Web3jCustodyLiveTest {

    private static final String ANVIL_RPC = "http://127.0.0.1:8545";
    private static final String OPENBAO = "http://127.0.0.1:8200";
    private static final String MNEMONIC =
            "test test test test test test test test test test test junk";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static boolean reachable(String url) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HTTP.send(r, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Web3jConfig config() {
        return new Web3jConfig() {
            public Optional<String> rpcUrl() { return Optional.of(ANVIL_RPC); }
            public long chainId() { return 31337L; }
            public long gasLimit() { return 21000L; }
            public Optional<String> gasPriceWei() { return Optional.empty(); }
            public int hotWalletIndex() { return 0; }
        };
    }

    @Test
    void endToEndDepositBalanceWithdrawalAndIdempotency() throws Exception {
        assumeTrue(reachable(ANVIL_RPC), "Anvil not running — skipping live custody test");

        Web3j web3j = Web3j.build(new HttpService(ANVIL_RPC));
        WalletSeedStore seed = () -> MNEMONIC;
        InMemoryStore store = new InMemoryStore();
        Web3jVaultCustodyProvider provider =
                new Web3jVaultCustodyProvider(config(), seed, store, web3j);
        AssetId eth = AssetId.of("ETH");

        // deposit address: per-user, stable, valid, and NOT the hot wallet (index 0)
        String addrA = provider.depositAddress("user-A", eth);
        assertTrue(addrA.matches("0x[0-9a-fA-F]{40}"), addrA);
        assertEquals(addrA, provider.depositAddress("user-A", eth), "stable per user");

        // balance: hot wallet is Anvil account #0 with ~10000 ETH
        Money bal = provider.custodyBalance(eth);
        assertTrue(bal.amount().doubleValue() > 100, "hot wallet prefunded, got " + bal.amount());

        // withdrawal: broadcast 1 ETH to Anvil account #1, then confirm it mines
        String recipient = HdWallet.deriveAddress(MNEMONIC, 1);
        String txHash = provider.submitWithdrawal("wd-live-1", eth, recipient, Money.of("ETH", "1"));
        assertNotNull(txHash);
        assertTrue(txHash.startsWith("0x"));
        TransactionReceipt receipt = awaitReceipt(web3j, txHash);
        assertTrue(receipt.isStatusOK(), "tx mined successfully");

        // idempotency: same withdrawId returns the same hash, no second broadcast
        long before = store.txCount();
        String again = provider.submitWithdrawal("wd-live-1", eth, recipient, Money.of("ETH", "1"));
        assertEquals(txHash, again, "idempotent — same tx hash");
        assertEquals(before, store.txCount(), "no second record / broadcast");

        web3j.shutdown();
    }

    @Test
    void openBaoSeedRoundTrips() throws Exception {
        assumeTrue(reachable(OPENBAO + "/v1/sys/health"), "OpenBao not running — skipping");

        // write the seed into OpenBao KV v2 (dev root token)
        String body = "{\"data\":{\"mnemonic\":\"" + MNEMONIC + "\"}}";
        HttpRequest put = HttpRequest.newBuilder(URI.create(OPENBAO + "/v1/secret/data/kyra/wallet-seed"))
                .header("X-Vault-Token", "root")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> res = HTTP.send(put, HttpResponse.BodyHandlers.ofString());
        assumeTrue(res.statusCode() / 100 == 2, "could not write to OpenBao (status " + res.statusCode() + ")");

        OpenBaoSeedStore seedStore = new OpenBaoSeedStore(
                Optional.of(OPENBAO), Optional.of("root"),
                "v1/secret/data/kyra/wallet-seed", "mnemonic", new ObjectMapper());
        assertEquals(MNEMONIC, seedStore.mnemonic());
    }

    private static TransactionReceipt awaitReceipt(Web3j web3j, String txHash) throws Exception {
        for (int i = 0; i < 40; i++) {
            var r = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (r.isPresent()) {
                return r.get();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("tx not mined: " + txHash);
    }

    private static final class InMemoryStore implements Web3jCustodyStore {
        private final Map<String, Long> idx = new ConcurrentHashMap<>();
        private final Map<String, String> tx = new ConcurrentHashMap<>();
        private final AtomicLong seq = new AtomicLong(0);
        public long indexFor(String userId) {
            return idx.computeIfAbsent(userId, u -> seq.incrementAndGet());
        }
        public Optional<String> txFor(String withdrawId) { return Optional.ofNullable(tx.get(withdrawId)); }
        public void recordTx(String withdrawId, String txHash) { tx.putIfAbsent(withdrawId, txHash); }
        long txCount() { return tx.size(); }
    }
}
