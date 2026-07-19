# Dev infra — what runs locally, what is mocked, what needs a vendor

Honest map of every external dependency, so nothing surprises you when running
the app in dev. Source of truth: `docker-compose.dev.yml` + `application.properties`.

## Provided locally (real, in docker-compose.dev.yml)

| Service | Container | Port(s) | Used by |
|---------|-----------|---------|---------|
| Postgres 16 | `postgres` | 5432 | every module (also auto-started by `quarkus dev` Dev Services) |
| Valkey 8 (Redis) | `valkey` | 6379 | rate limiting (also Dev Services) |
| **Mailpit** (SMTP catcher) | `mailpit` | 1025 SMTP, **8025 inbox UI** | email delivery in dev — open http://localhost:8025 to read verification/withdrawal mails |
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

## Mocked in dev — NOT self-hostable, need a real vendor

These are hosted third-party APIs. There is **no local container** for them; dev
uses in-process `Mock*` beans, and the real integration needs vendor credentials
+ their sandbox endpoint (tracked in `TECHDEBT.md`). Do not expect them in
docker-compose — you cannot run a vendor's cloud API locally.

| Capability | Dev bean | Real vendor | Blocked on |
|-----------|----------|-------------|-----------|
| Custody (deposit addr, withdraw broadcast) | `MockCustodyProvider` | **Fystack** (cloud custody API) | API contract + sandbox credentials |
| KYC verification | `MockKycProvider` | KYC vendor | vendor selection + credentials |
| Address screening (AML) | `MockAddressScreener` | screening vendor | vendor selection + credentials |
| Reference / mark price feed | `Mock*PriceProvider` | market data feed | feed subscription |

When credentials exist, each becomes one config-selected bean (same pattern as
email) — an `HttpCustodyProvider` etc. built against the **verified** vendor API
docs. We do not guess vendor API shapes for a system that moves real money.
