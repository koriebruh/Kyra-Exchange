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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box tests for {@link HttpCustodyProvider} against a stub HTTP server.
 * They pin the wire contract we control today — endpoint/method/path/query, the
 * three auth headers, the withdrawal idempotency key + JSON body, and response
 * parsing — without a live Fystack. The custody-model and live-only gaps are
 * documented on the provider + in TECHDEBT, not asserted here.
 */
class HttpCustodyProviderTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<Captured> last = new AtomicReference<>();

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

    private volatile int status = 200;
    private volatile String responseBody = "{}";

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        last.set(new Captured(
                ex.getRequestMethod(),
                ex.getRequestURI().getPath(),
                ex.getRequestURI().getQuery(),
                ex.getRequestHeaders().getFirst("ACCESS-API-KEY"),
                ex.getRequestHeaders().getFirst("ACCESS-TIMESTAMP"),
                ex.getRequestHeaders().getFirst("ACCESS-SIGN"),
                ex.getRequestHeaders().getFirst("X-Idempotency-Key"),
                body));
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, out.length);
        ex.getResponseBody().write(out);
        ex.close();
    }

    private HttpCustodyProvider provider() {
        return new HttpCustodyProvider(new TestConfig(baseUrl), new ObjectMapper());
    }

    @Test
    void depositAddressCallsSignedGetAndReturnsAddress() {
        responseBody = "{\"success\":true,\"data\":{\"asset_id\":\"a-usdt\",\"address\":\"0xDEADBEEF\"}}";

        String addr = provider().depositAddress("user-1", AssetId.of("USDT"));

        assertEquals("0xDEADBEEF", addr);
        Captured c = last.get();
        assertEquals("GET", c.method());
        assertEquals("/wallets/wallet-1/deposit-address", c.path());
        assertEquals("asset_id=a-usdt&address_type=evm", c.query());
        // all three auth headers present + non-empty
        assertNotNull(c.apiKey());
        assertEquals("api-key-1", c.apiKey());
        assertTrue(c.timestamp() != null && c.timestamp().matches("\\d+"));
        assertNotNull(c.sign());
        assertTrue(c.sign().length() > 0);
    }

    @Test
    void submitWithdrawalPostsBodyWithIdempotencyKey() {
        responseBody = "{\"success\":true,\"data\":{\"id\":\"wd-99\",\"status\":\"PENDING_APPROVAL\"}}";

        String ref = provider().submitWithdrawal(
                "withdraw-abc", AssetId.of("USDT"), "0xRECIPIENT", Money.of("USDT", "12.5"));

        assertEquals("wd-99", ref);
        Captured c = last.get();
        assertEquals("POST", c.method());
        assertEquals("/wallets/wallet-1/request-withdrawal", c.path());
        assertEquals("withdraw-abc", c.idempotencyKey());
        assertTrue(c.body().contains("\"asset_id\":\"a-usdt\""), c.body());
        assertTrue(c.body().contains("\"amount\":\"12.5\""), c.body());
        assertTrue(c.body().contains("\"recipient_address\":\"0xRECIPIENT\""), c.body());
    }

    @Test
    void nonSuccessStatusThrows() {
        status = 401;
        responseBody = "{\"success\":false,\"message\":\"bad signature\"}";
        assertThrows(IllegalStateException.class,
                () -> provider().depositAddress("user-1", AssetId.of("USDT")));
    }

    @Test
    void unmappedAssetIsRejectedBeforeAnyCall() {
        // BTC has no asset-id mapping -> fail fast, no HTTP call
        assertThrows(IllegalStateException.class,
                () -> provider().depositAddress("user-1", AssetId.of("BTC")));
    }

    @Test
    void custodyBalanceIsUnsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> provider().custodyBalance(AssetId.of("USDT")));
    }

    /** Minimal in-test {@link FystackConfig}. */
    private record TestConfig(String base) implements FystackConfig {
        public Optional<String> baseUrl() { return Optional.of(base); }
        public Optional<String> apiKey() { return Optional.of("api-key-1"); }
        public Optional<String> apiSecret() { return Optional.of("secret-1"); }
        public Optional<String> workspaceId() { return Optional.of("ws-1"); }
        public Optional<String> walletId() { return Optional.of("wallet-1"); }
        public String addressType() { return "evm"; }
        public Map<String, String> assetIds() { return Map.of("USDT", "a-usdt"); }
    }
}
