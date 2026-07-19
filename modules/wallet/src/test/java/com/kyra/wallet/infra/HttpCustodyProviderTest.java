package com.kyra.wallet.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box tests for {@link HttpCustodyProvider} against a stub HTTP server.
 * They pin the wire contract we control today — endpoints, methods, paths,
 * queries, the three auth headers, the per-user wallet create/reuse flow, the
 * withdrawal idempotency key + JSON body, and response parsing — without a live
 * Fystack. The live-only gaps are documented on the provider + in TECHDEBT.
 */
class HttpCustodyProviderTest {

    private HttpServer server;
    private String baseUrl;
    private final List<Captured> requests = new CopyOnWriteArrayList<>();
    private volatile int forcedStatus = 0; // 0 = route normally

    private record Captured(String method, String path, String query,
                            String apiKey, String timestamp, String sign,
                            String idempotencyKey, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getPath();
        requests.add(new Captured(
                ex.getRequestMethod(), path, ex.getRequestURI().getQuery(),
                ex.getRequestHeaders().getFirst("ACCESS-API-KEY"),
                ex.getRequestHeaders().getFirst("ACCESS-TIMESTAMP"),
                ex.getRequestHeaders().getFirst("ACCESS-SIGN"),
                ex.getRequestHeaders().getFirst("X-Idempotency-Key"), body));

        int status = forcedStatus != 0 ? forcedStatus : 200;
        String res;
        if (forcedStatus != 0) {
            res = "{\"success\":false,\"message\":\"error\"}";
        } else if (path.equals("/wallets")) {
            res = "{\"success\":true,\"data\":{\"wallet_id\":\"uw-new\",\"status\":\"success\"}}";
        } else if (path.endsWith("/deposit-address")) {
            res = "{\"success\":true,\"data\":{\"address\":\"0xDEADBEEF\"}}";
        } else if (path.endsWith("/request-withdrawal")) {
            res = "{\"success\":true,\"data\":{\"id\":\"wd-99\",\"status\":\"PENDING_APPROVAL\"}}";
        } else {
            res = "{}";
        }
        byte[] out = res.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private final InMemoryStore store = new InMemoryStore();

    private HttpCustodyProvider provider() {
        return new HttpCustodyProvider(new TestConfig(baseUrl), new ObjectMapper(), store);
    }

    @Test
    void firstDepositCreatesPerUserWalletThenFetchesAddress() {
        String addr = provider().depositAddress("user-1", AssetId.of("USDT"));

        assertEquals("0xDEADBEEF", addr);
        assertEquals(2, requests.size(), "creates the wallet, then gets the address");

        Captured create = requests.get(0);
        assertEquals("POST", create.method());
        assertEquals("/wallets", create.path());
        assertTrue(create.body().contains("\"wallet_purpose\":\"user\""), create.body());
        assertEquals("api-key-1", create.apiKey());
        assertNotNull(create.sign());

        Captured get = requests.get(1);
        assertEquals("GET", get.method());
        assertEquals("/wallets/uw-new/deposit-address", get.path());
        assertEquals("asset_id=a-usdt&address_type=evm", get.query());

        assertEquals("uw-new", store.walletIdFor("user-1").orElse(null), "mapping persisted");
    }

    @Test
    void secondDepositReusesStoredWalletWithoutRecreating() {
        store.save("user-2", "uw-existing");

        String addr = provider().depositAddress("user-2", AssetId.of("USDT"));

        assertEquals("0xDEADBEEF", addr);
        assertEquals(1, requests.size(), "no wallet creation on reuse");
        assertEquals("/wallets/uw-existing/deposit-address", requests.get(0).path());
    }

    @Test
    void submitWithdrawalPostsFromHotWalletWithIdempotencyKey() {
        String ref = provider().submitWithdrawal(
                "withdraw-abc", AssetId.of("USDT"), "0xRECIPIENT", Money.of("USDT", "12.5"));

        assertEquals("wd-99", ref);
        Captured c = requests.get(0);
        assertEquals("POST", c.method());
        assertEquals("/wallets/hot-wallet/request-withdrawal", c.path());
        assertEquals("withdraw-abc", c.idempotencyKey());
        assertTrue(c.body().contains("\"asset_id\":\"a-usdt\""), c.body());
        assertTrue(c.body().contains("\"amount\":\"12.5\""), c.body());
        assertTrue(c.body().contains("\"recipient_address\":\"0xRECIPIENT\""), c.body());
    }

    @Test
    void nonSuccessStatusThrows() {
        forcedStatus = 401;
        assertThrows(IllegalStateException.class,
                () -> provider().submitWithdrawal("w", AssetId.of("USDT"), "0x", Money.of("USDT", "1")));
    }

    @Test
    void unmappedAssetIsRejected() {
        store.save("user-3", "uw-x"); // wallet exists, but BTC has no asset-id mapping
        assertThrows(IllegalStateException.class,
                () -> provider().depositAddress("user-3", AssetId.of("BTC")));
    }

    @Test
    void custodyBalanceIsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> provider().custodyBalance(AssetId.of("USDT")));
    }

    private static final class InMemoryStore implements FystackWalletStore {
        private final Map<String, String> map = new ConcurrentHashMap<>();
        public Optional<String> walletIdFor(String userId) { return Optional.ofNullable(map.get(userId)); }
        public void save(String userId, String walletId) { map.put(userId, walletId); }
    }

    private record TestConfig(String base) implements FystackConfig {
        public Optional<String> baseUrl() { return Optional.of(base); }
        public Optional<String> apiKey() { return Optional.of("api-key-1"); }
        public Optional<String> apiSecret() { return Optional.of("secret-1"); }
        public Optional<String> workspaceId() { return Optional.of("ws-1"); }
        public Optional<String> walletId() { return Optional.of("hot-wallet"); }
        public String addressType() { return "evm"; }
        public Map<String, String> assetIds() { return Map.of("USDT", "a-usdt"); }
    }
}
