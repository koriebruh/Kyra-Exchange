package com.kyra.app.wallet;

import com.kyra.common.money.AssetId;
import com.kyra.wallet.api.WalletApi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves the <em>whole app</em> — not just the provider in isolation — resolves
 * custody to the web3j self-custody backend when {@code kyra.custody.provider=web3j}:
 * the CDI {@link WalletApi} bean, running the real {@code Web3jVaultCustodyProvider},
 * derives an HD deposit address from the seed held in OpenBao. Runs only when Anvil
 * + OpenBao are reachable (docker-compose.dev.yml up); in CI it skips.
 */
@QuarkusTest
@TestProfile(WalletWeb3jIntegrationTest.Web3jProfile.class)
class WalletWeb3jIntegrationTest {

    private static final String ANVIL = "http://127.0.0.1:8545";
    private static final String OPENBAO = "http://127.0.0.1:8200";
    private static final String MNEMONIC =
            "test test test test test test test test test test test junk";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Inject
    WalletApi wallet;

    public static class Web3jProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    // profile-prefixed so it beats %test.kyra.custody.provider=mock in application.properties
                    "%test.kyra.custody.provider", "web3j",
                    "kyra.custody.web3j.rpc-url", ANVIL,
                    "kyra.custody.web3j.chain-id", "31337",
                    "kyra.seedstore.openbao.address", OPENBAO,
                    "kyra.seedstore.openbao.token", "root",
                    "kyra.seedstore.openbao.path", "v1/secret/data/kyra/wallet-seed",
                    "kyra.email.provider", "recording",
                    "quarkus.otel.sdk.disabled", "true");
        }
    }

    @Test
    void appDerivesRealHdDepositAddressThroughWeb3j() throws Exception {
        assumeTrue(reachable(ANVIL) && reachable(OPENBAO + "/v1/sys/health"),
                "Anvil/OpenBao not running — skipping app-level web3j custody test");
        seedOpenBao();

        String address = wallet.depositAddress("01ARZ3NDEKTSV4RRFFQ69G5FAX", AssetId.of("USDT"));

        // web3j returns a 0x EVM address — NOT the mock provider's "mock-USDT-..."
        assertTrue(address.matches("0x[0-9a-fA-F]{40}"),
                "expected a real HD address, got: " + address);
    }

    private static boolean reachable(String url) {
        try {
            HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void seedOpenBao() throws Exception {
        HttpRequest put = HttpRequest.newBuilder(URI.create(OPENBAO + "/v1/secret/data/kyra/wallet-seed"))
                .header("X-Vault-Token", "root")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":{\"mnemonic\":\"" + MNEMONIC + "\"}}"))
                .build();
        HTTP.send(put, HttpResponse.BodyHandlers.discarding());
    }
}
