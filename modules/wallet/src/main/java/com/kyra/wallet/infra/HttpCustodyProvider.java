package com.kyra.wallet.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyra.common.money.AssetId;
import com.kyra.common.money.Money;
import com.kyra.wallet.api.CustodyProvider;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Real custody backend over the Fystack Apex REST API (kyra-doc/modules/08,
 * docs.fystack.io). Active only when {@code kyra.custody.provider=fystack};
 * otherwise {@link com.kyra.wallet.domain.MockCustodyProvider} (a
 * {@code @DefaultBean}) is used. Requests are HMAC-signed by {@link FystackSigner}.
 *
 * <p><b>Not production-complete — verify against a live Apex instance before
 * enabling (kyra-doc/TECHDEBT.md):</b>
 * <ul>
 *   <li>{@link #depositAddress} returns the configured custody wallet's address
 *       for the asset. Per-user deposit attribution needs a wallet-per-user model
 *       (Fystack {@code wallet_purpose=user}) with a persisted userId→wallet_id
 *       map — a product decision to finalise against the running stack.</li>
 *   <li>The exact signed-{@code PATH} convention and the idempotency-key format
 *       (Fystack documents "a unique UUID"; we pass the withdrawal ULID) must be
 *       confirmed live.</li>
 *   <li>{@link #custodyBalance} has no per-asset Apex endpoint yet, so
 *       reconciliation against real custody is unresolved.</li>
 * </ul>
 * The request signing, endpoint shaping, idempotency header and response parsing
 * are unit-tested against a stub server; the items above are the live-only gaps.
 */
@ApplicationScoped
@IfBuildProperty(name = "kyra.custody.provider", stringValue = "fystack", enableIfMissing = false)
public class HttpCustodyProvider implements CustodyProvider {

    private static final Logger LOG = Logger.getLogger(HttpCustodyProvider.class);

    private final FystackConfig config;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public HttpCustodyProvider(FystackConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String depositAddress(String userId, AssetId asset) {
        String path = "/wallets/" + walletId() + "/deposit-address"
                + "?asset_id=" + assetId(asset) + "&address_type=" + config.addressType();
        JsonNode data = send("GET", path, null).path("data");
        String address = data.path("address").asText(null);
        if (address == null || address.isBlank()) {
            throw new IllegalStateException("Fystack returned no deposit address for " + asset.symbol());
        }
        return address;
    }

    @Override
    public String submitWithdrawal(String withdrawId, AssetId asset, String toAddress, Money amount) {
        String path = "/wallets/" + walletId() + "/request-withdrawal";
        String body;
        try {
            body = mapper.writeValueAsString(mapper.createObjectNode()
                    .put("asset_id", assetId(asset))
                    .put("amount", amount.amount().toPlainString())
                    .put("recipient_address", toAddress));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to build Fystack withdrawal body", e);
        }
        // X-Idempotency-Key makes retries of the same withdrawal safe on Fystack's side.
        JsonNode data = send("POST", path, body, "X-Idempotency-Key", withdrawId).path("data");
        String ref = data.path("id").asText(null);
        if (ref == null || ref.isBlank()) {
            throw new IllegalStateException("Fystack returned no withdrawal id for " + withdrawId);
        }
        LOG.infof("fystack withdrawal submitted: withdrawId=%s status=%s", withdrawId,
                data.path("status").asText("?"));
        return ref;
    }

    @Override
    public Money custodyBalance(AssetId asset) {
        // The Apex API exposes no per-asset custody balance endpoint (only aggregate
        // value_usd / top_assets on the wallet list). Reconciliation against real
        // custody is unresolved — see class Javadoc + TECHDEBT.
        throw new UnsupportedOperationException(
                "Fystack per-asset custody balance not available via Apex API yet");
    }

    // ----- HTTP + signing -----

    private JsonNode send(String method, String path, String body, String... extraHeaders) {
        String base = config.baseUrl()
                .orElseThrow(() -> new IllegalStateException("kyra.custody.fystack.base-url not configured"));
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = FystackSigner.sign(apiSecret(), method, path, timestamp, body);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(Duration.ofSeconds(30))
                .header("ACCESS-API-KEY", apiKey())
                .header("ACCESS-TIMESTAMP", timestamp)
                .header("ACCESS-SIGN", signature)
                .header("Content-Type", "application/json");
        for (int i = 0; i + 1 < extraHeaders.length; i += 2) {
            req.header(extraHeaders[i], extraHeaders[i + 1]);
        }
        req.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));

        HttpResponse<String> res;
        try {
            res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Fystack request failed: " + method + " " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fystack request interrupted", e);
        }
        if (res.statusCode() / 100 != 2) {
            // Never log the response body verbatim (may echo request detail); status only.
            throw new IllegalStateException("Fystack " + method + " " + path
                    + " returned HTTP " + res.statusCode());
        }
        try {
            return mapper.readTree(res.body());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Fystack sent an unparseable response for " + path, e);
        }
    }

    private String walletId() {
        return config.walletId()
                .orElseThrow(() -> new IllegalStateException("kyra.custody.fystack.wallet-id not configured"));
    }

    private String apiKey() {
        return config.apiKey()
                .orElseThrow(() -> new IllegalStateException("kyra.custody.fystack.api-key not configured"));
    }

    private String apiSecret() {
        return config.apiSecret()
                .orElseThrow(() -> new IllegalStateException("kyra.custody.fystack.api-secret not configured"));
    }

    private String assetId(AssetId asset) {
        String id = config.assetIds().get(asset.symbol());
        if (id == null) {
            throw new IllegalStateException("no Fystack asset_id mapped for " + asset.symbol()
                    + " (set kyra.custody.fystack.asset-ids." + asset.symbol() + ")");
        }
        return id;
    }
}
