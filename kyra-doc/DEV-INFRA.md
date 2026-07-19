# Dev infra — what runs locally, what is mocked, what needs a vendor

Honest map of every external dependency, so nothing surprises you when running
the app in dev. Source of truth: `docker-compose.dev.yml` + `application.properties`.

## Provided locally (real, in docker-compose.dev.yml)

| Service | Container | Port(s) | Used by |
|---------|-----------|---------|---------|
| Postgres 16 | `postgres` | 5432 | every module (also auto-started by `quarkus dev` Dev Services) |
| Valkey 8 (Redis) | `valkey` | 6379 | rate limiting (also Dev Services) |
| **Mailpit** (SMTP catcher) | `mailpit` | 1025 SMTP, **8025 inbox UI** | email delivery in dev — open http://localhost:8025 to read verification/withdrawal mails |
| **Anvil** (EVM chain) | `anvil` | 8545 JSON-RPC | local blockchain for the web3j custody provider (deterministic accounts, fake money) |
| **OpenBao** (Vault fork) | `openbao` | 8200 | secret store for the wallet seed; dev root token `root` |
| Grafana/OTLP (obs) | `lgtm` (`--profile obs`) | 3000 UI, 4317/4318 OTLP | traces/metrics/logs when you want them |

Bring the stack up: `docker compose -f docker-compose.dev.yml up`
(Mailpit + Postgres + Valkey). Add `--profile obs` for Grafana, `--profile app`
to also run the packaged jar.

## Email — how it behaves per mode

`kyra.email.provider` selects the backend:

- `recording` (default in **tests**): `RecordingEmailSender` keeps mails in memory
  and logs a masked line. No network, no inbox. Tests assert against it.
- `smtp` (default otherwise): `SmtpEmailSender` sends via `quarkus-mailer`.
  - **dev** (`quarkus dev`): points at Mailpit on `localhost:1025`, `mock=false`
    → register a user, then read the verification email at http://localhost:8025.
  - **prod**: real relay via env (`QUARKUS_MAILER_HOST/_PORT/_USERNAME/_PASSWORD/_TLS`,
    `QUARKUS_MAILER_MOCK=false`) + SPF/DKIM/DMARC on the domain (see TECHDEBT).

Swapping backends is a bean selection (`@DefaultBean` vs `@IfBuildProperty`),
never a change to notification logic.

## Tracing (OTLP)

Off by default in dev (`%dev.quarkus.otel.sdk.disabled=true`) so `quarkus dev`
without a collector does not log export failures. To inspect traces: run
`--profile obs` and start with `-Dquarkus.otel.sdk.disabled=false`. Disabled in
tests always.

## Secrets / keys — dev vs prod

Dev values are baked into `kyra-app/src/main/resources` **for local use only**:

- `kyra.crypto.data-key` — AES-256 key encrypting TOTP + API-key secrets at rest.
- `resources/jwt/privateKey.pem` / `publicKey.pem` — JWT RS256 signing/verify.

**These dev keys must never be used in prod.** Prod overrides them via env /
secret manager (SOPS): set `KYRA_CRYPTO_DATA_KEY` and point
`smallrye.jwt.sign.key.location` / `mp.jwt.verify.publickey.location` at
mounted secrets. There is no local secrets container — dev reads the baked
values directly; that is intentional, not a missing piece.

## External providers — currently mocked in dev

Each is behind an interface with a `Mock*` bean; the app is **not yet connected**
to any real provider. Status differs per provider — do not assume "cloud-only":

| Capability | Dev bean | Real provider | Self-hostable? |
|-----------|----------|---------------|----------------|
| Custody (deposit addr, withdraw, balance) | `MockCustodyProvider` | **web3j self-custody** (default alt) or **Fystack** | **YES** — web3j+OpenBao+Anvil below; Fystack further down |
| KYC verification | `MockKycProvider` | KYC vendor (TBD) | hosted API — needs vendor selection + credentials |
| Address screening (AML) | `MockAddressScreener` | screening vendor (TBD) | hosted API — needs vendor selection + credentials |
| Reference / mark price feed | `Mock*PriceProvider` | market data feed (e.g. CoinMarketCap) | hosted API — needs a key |

### web3j self-custody (working now, free, no login)

`kyra.custody.provider=web3j` runs custody in-process with **web3j** against an EVM
chain, keys derived from one HD seed held in **OpenBao**. No vendor, no registry
login. Verified end-to-end against local Anvil + OpenBao (`Web3jCustodyLiveTest`):
per-user HD deposit address, on-chain balance, a signed+broadcast withdrawal that
mines, and withdrawal idempotency; plus the OpenBao seed round-trip.

Run it locally:
```bash
docker compose -f docker-compose.dev.yml up -d anvil openbao postgres valkey
# seed OpenBao once (dev root token "root"); Anvil's standard test mnemonic:
curl -H "X-Vault-Token: root" -X POST http://localhost:8200/v1/secret/data/kyra/wallet-seed \
  -d '{"data":{"mnemonic":"test test test test test test test test test test test junk"}}'
# build/run Kyra with the web3j provider:
KYRA_CUSTODY_PROVIDER=web3j \
KYRA_CUSTODY_WEB3J_RPC_URL=http://localhost:8545 \
KYRA_SEEDSTORE_OPENBAO_ADDRESS=http://localhost:8200 KYRA_SEEDSTORE_OPENBAO_TOKEN=root \
  ./mvnw -f kyra-app/pom.xml quarkus:dev -Dkyra.custody.provider=web3j
```

**Scope / gaps (TECHDEBT):** handles the chain's *native* coin today (proves the
crypto plumbing). ERC-20 tokens (USDT) = per-asset contract + `transfer()` call,
the immediate follow-up. Deposit detection (polling), broadcast↔record atomicity,
and **production key security** (hot/cold split, real OpenBao unseal, backups) are
the operator's responsibility — self-custody means Kyra holds the key. For
key-never-whole security, use MPC (Fystack/Fireblocks) instead.

### Fystack custody is self-hostable (corrects an earlier wrong note)

Fystack is **open-core, self-hosted** (docs.fystack.io, selfhost.fystack.io,
github.com/fystack/fystack-selfhost-scripts). "Fystack Ignite" (`./fystack-ignite.sh`)
brings up a full Docker-Compose stack of ~14 services:

- **Apex API** — REST API, `http://localhost:8150` (the endpoint the app integrates against)
- **Fystack UI** — `http://localhost:8015`
- **3× MPCIUM nodes** (threshold signing) — ports **8080–8082**
- Own infra: PostgreSQL :5433, Redis :6380, MongoDB :27018, NATS :4223, Consul :8501
- Multichain indexer, rescanner, migrate, mpcium-init

Requirements: Docker, **~4 vCPU / 4 GB RAM**, and a **CoinMarketCap API key**
(its price provider). ⚠ **Port clash:** MPCIUM uses 8080 — the same port as the
Kyra app; run Kyra on another port (`QUARKUS_HTTP_PORT`) when Fystack is up.

Because it is a heavy, separate stack (its own DBs + a required CMC key + config
generated by the ignite script), it is **not** folded into `docker-compose.dev.yml`.
Run it opt-in via their scripts. The app connects by implementing
`CustodyProvider` as an `HttpCustodyProvider` against the Apex REST API
(config-selected bean, mock stays default) — built against the **verified** Apex
API contract, not guessed. This integration is **not done yet** (see TECHDEBT).
