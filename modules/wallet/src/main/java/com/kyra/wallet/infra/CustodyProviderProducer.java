package com.kyra.wallet.infra;

import com.kyra.wallet.api.CustodyProvider;
import com.kyra.wallet.domain.MockCustodyProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Selects the custody backend from {@code kyra.custody.provider} at <em>runtime</em>
 * (kyra-doc/modules/08): {@code mock} (default) or {@code web3j}. Because it is a
 * runtime read — not a build-time condition — the same build can be pointed at
 * either backend via env, and an MPC vendor can be added here later as one more
 * branch. The chosen provider is the single {@link CustodyProvider} bean that
 * {@code WalletService} injects.
 */
@ApplicationScoped
public class CustodyProviderProducer {

    private static final Logger LOG = Logger.getLogger(CustodyProviderProducer.class);

    @Produces
    @ApplicationScoped
    public CustodyProvider custodyProvider(
            @ConfigProperty(name = "kyra.custody.provider", defaultValue = "mock") String provider,
            Instance<MockCustodyProvider> mock,
            Web3jConfig web3jConfig,
            Web3jCustodyStore web3jStore,
            Instance<WalletSeedStore> seedStore) {
        if ("web3j".equalsIgnoreCase(provider)) {
            LOG.info("custody provider: web3j (self-custody, seed in OpenBao)");
            // Built here (not a CDI bean) so its web3j/OpenBao deps only initialise
            // when web3j is actually selected; seedStore is looked up lazily.
            return new Web3jVaultCustodyProvider(web3jConfig, seedStore.get(), web3jStore);
        }
        LOG.infof("custody provider: mock%s",
                "mock".equalsIgnoreCase(provider) ? "" : " (default; unknown '" + provider + "')");
        return mock.get();
    }
}
