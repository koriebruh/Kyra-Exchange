package com.kyra.wallet.infra;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

/**
 * Fystack Apex API settings (kyra-doc/modules/08). Only consulted when
 * {@code kyra.custody.provider=fystack}. Secrets (api-key/api-secret) come from
 * the environment — never committed. Example (dev, self-hosted Apex):
 *
 * <pre>
 * kyra.custody.provider=fystack
 * kyra.custody.fystack.base-url=http://localhost:8150/api/v1
 * kyra.custody.fystack.api-key=${FYSTACK_API_KEY}
 * kyra.custody.fystack.api-secret=${FYSTACK_API_SECRET}
 * kyra.custody.fystack.workspace-id=...
 * kyra.custody.fystack.wallet-id=...
 * kyra.custody.fystack.address-type=evm
 * kyra.custody.fystack.asset-ids.USDT=&lt;fystack-asset-uuid&gt;
 * </pre>
 */
@ConfigMapping(prefix = "kyra.custody.fystack")
public interface FystackConfig {

    /** Apex API base URL including the version prefix, e.g. {@code http://localhost:8150/api/v1}. */
    Optional<String> baseUrl();

    /** {@code ACCESS-API-KEY}. */
    Optional<String> apiKey();

    /** HMAC secret for request signing. */
    Optional<String> apiSecret();

    /** Workspace the custody wallet lives in. */
    Optional<String> workspaceId();

    /** The custody wallet whose deposit addresses / withdrawals are used. */
    Optional<String> walletId();

    /** Address family for deposit addresses: {@code evm} or {@code sol}. */
    @WithDefault("evm")
    String addressType();

    /** Map of Kyra asset symbol (e.g. {@code USDT}) to the Fystack asset UUID. */
    Map<String, String> assetIds();
}
