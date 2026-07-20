package com.kyra.wallet.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reads the HD wallet mnemonic from OpenBao (open-source Vault) KV v2. The seed
 * lives in OpenBao — encrypted at rest, access-controlled, auditable — not in
 * Kyra config. Fetched once and cached in memory (it is needed in memory to sign
 * anyway). Only constructed when the web3j provider is selected at runtime
 * ({@code kyra.custody.provider=web3j}); in mock mode it is never instantiated,
 * so its OpenBao config is not required then.
 *
 * <p>Reads {@code {address}/v1/{path}} with an {@code X-Vault-Token} header and
 * returns {@code data.data.<field>} from the KV-v2 response.
 */
@ApplicationScoped
@io.quarkus.arc.Unremovable
public class OpenBaoSeedStore implements WalletSeedStore {

    private final String address;
    private final String token;
    private final String path;
    private final String field;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private volatile String cached;

    public OpenBaoSeedStore(
            // Optional so the bean is always valid (only used when custody=web3j);
            // presence is enforced at fetch time, not construction.
            @ConfigProperty(name = "kyra.seedstore.openbao.address") java.util.Optional<String> address,
            @ConfigProperty(name = "kyra.seedstore.openbao.token") java.util.Optional<String> token,
            @ConfigProperty(name = "kyra.seedstore.openbao.path", defaultValue = "v1/secret/data/kyra/wallet-seed") String path,
            @ConfigProperty(name = "kyra.seedstore.openbao.field", defaultValue = "mnemonic") String field,
            ObjectMapper mapper) {
        this.address = address.orElse("");
        this.token = token.orElse("");
        this.path = path;
        this.field = field;
        this.mapper = mapper;
    }

    @Override
    public String mnemonic() {
        String local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            cached = fetch();
            return cached;
        }
    }

    private String fetch() {
        if (address.isBlank() || token.isBlank()) {
            throw new IllegalStateException(
                    "custody=web3j but kyra.seedstore.openbao.address/token not configured");
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(address.replaceAll("/+$", "") + "/" + path))
                .timeout(Duration.ofSeconds(10))
                .header("X-Vault-Token", token)
                .GET().build();
        HttpResponse<String> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("OpenBao seed fetch failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenBao seed fetch interrupted", e);
        }
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("OpenBao returned HTTP " + res.statusCode() + " for the wallet seed");
        }
        try {
            JsonNode value = mapper.readTree(res.body()).path("data").path("data").path(field);
            if (value.isMissingNode() || value.asText().isBlank()) {
                throw new IllegalStateException("OpenBao secret has no '" + field + "' field");
            }
            return value.asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("OpenBao sent an unparseable response", e);
        }
    }
}
