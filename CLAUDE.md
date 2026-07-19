# Kyra Exchange — Context for Claude

Production crypto exchange (real money, foundation of the owner's company — NOT a learning project).
Modular monolith, Quarkus 3.x, Java 21, Maven multi-module.

## Design source of truth
- `kyra-doc/README.md` — architecture, 10 non-negotiable principles, 7 build phases with exit criteria, technical & API conventions
- `kyra-doc/modules/01..18-*.md` — detailed spec per module (features, flows, data model, edge cases, testing). **Read the module spec BEFORE implementing that module.**
- `kyra-doc/adr/` — architecture decisions. New significant decision = write a new ADR.
- `kyra-doc/TECHDEBT.md` — features deferred because they need another piece first (frontend, notification module, etc.), with the reason + spec ref. When you build one, remove its entry.

## Status (update when a phase completes)
- Phase 0 (skeleton) DONE.
- Phase 1 in progress:
  - account ledger (modules/02) DONE — double-entry, hold/release, idempotency,
    race-safe, invariant + concurrency tests.
  - identity (modules/01) core DONE — register, Argon2id, email verify, login,
    JWT (RS256) + refresh rotation + reuse detection, sessions, 2FA TOTP
    (enroll/confirm/disable, recovery codes, two-step login), AES-GCM at-rest
    secret encryption. REST at /v1/auth.
  - API keys (HMAC, modules/01 F4) DONE — create/list/revoke, signed-request
    auth (timestamp window, scopes, IP whitelist, encrypted secret). REST at
    /v1/auth/api-keys.
  - Audit-log framework DONE — kyra-app audit schema (append-only), AuditLog
    service, wired to API-key + 2FA actions.
  - Remaining phase 1 (deferred, need other pieces first): captcha (needs
    frontend + Turnstile), anti-phishing code (needs notification module email
    templating), login/register audit hooks. Property-test ledger + no-negative
    invariant already covered by account tests.
- Phase 2 in progress:
  - market (modules/03) DONE — asset/pair registry, in-memory cache, order-grid
    validation, status lifecycle + freeze cascade, config history. REST /v1/market.
  - matching (modules/05) DONE — pure deterministic OrderBook + MatchingEngine
    service (per-pair single writer, sequencer, restoreResting for recovery).
  - settlement (modules/06) DONE — TradeSettlement → one balanced ledger journal,
    idempotent by tradeId, trades table.
  - order (modules/04) DONE (LIMIT GTC/IOC/FOK) — intake, validate (market),
    hold (account), submit (engine), settle each trade, update maker+taker state,
    release over-hold/remainder. Per-pair serialized (lock + programmatic tx).
    End-to-end tested: two accounts trade, price improvement release, partial
    fill, cancel, IOC expiry, insufficient-balance/off-grid/dup-client rejects.
  - order REST (kyra-app /v1/orders place/cancel/get/open) DONE.
  - matching recovery DONE — book_seq persisted, OrderRecovery rebuilds books
    from open orders on startup (restoreInto tested against a fresh engine).
  - marketdata (modules/07) DONE (partial) — 1m OHLCV candles + 24h ticker from
    TradeSettled (synchronous, atomic with settlement), depth snapshot from
    engine. REST: /v1/market/candles|ticker|depth. V700 candles.
  - PHASE 2 EXIT CRITERIA MET (two accounts trade + recovery).
- Phase 3 (public API & market data) DONE:
  - marketdata REST (candles multi-interval, ticker, depth) + public WebSocket
    stream (/v1/stream, trades pushed on TradeSettled, fire-and-forget).
  - Valkey-backed per-IP rate limiting (X-RateLimit-* headers, 429 + Retry-After).
  - idempotent order placement by client_order_id.
  - OpenAPI spec at /q/openapi.
  - Remaining phase 2/3 (NOT blocked, build next): MARKET orders (quote-budget
    for market-buy), STOP/OCO trigger engine, WebSocket streams (07 F5),
    multi-interval candles (5m/1h/…), rate limiting (Valkey), idempotency-key
    enforcement on money/order endpoints.
- Phases 1-3 DONE. Phase 4 INTERNAL LOGIC DONE (all against mock providers;
  real vendors deferred behind interfaces — pattern: interface + Mock*, real impl
  is a config-selected bean swap):
  - wallet (08): deposit address, creditDeposit (idempotent by txid),
    requestWithdrawal (hold+fee, auto-approve small / PENDING_REVIEW large),
    approve/reject/complete/fail. CustodyProvider + MockCustodyProvider. V800.
  - compliance (10): KYC levels (submitKyc raises, never lowers), AML address
    screening. KycProvider/AddressScreener + mocks. V1000. Gates wallet withdraw
    (KYC L1+ and CLEAR address required).
  - notification (13): templated transactional email, idempotent by dedupKey.
    EmailSender + RecordingEmailSender. V1300. Wired into identity register
    (verification email sent).
  - admin (12): withdrawal approve/reject with immutable audit trail (V1200,
    append-only). Delegates to wallet.
  - STILL VENDOR-BLOCKED (TECHDEBT): real Fystack custody, real KYC provider,
    real email/SMTP, deposit detector (webhook/poll), reconciliation vs real
    custody, tax IDR conversion.
  - 4-eyes for large withdrawals DONE (wallet.requiredApprovals + admin distinct
    approvers, V1201).
- Phase 5 (launch & liquidity) substantially DONE:
  - liquidity (14) MM bot — quotes N bid/ask around a mock reference price via the
    order path. ReferencePriceProvider + mock. Inventory skew / re-quote next.
  - Proof of Reserves (16 §7) — Merkle liabilities commitment over user balances
    (account.ProofOfReservesApi), inclusion verification.
- Phase 6 (derivatives) FOUNDATION DONE (new modules/derivatives):
  - MarkPriceProvider + MockMarkPriceProvider; AccountKey MARGIN type + kyra:perp
    + kyra:insurance; PERP_MARGIN/PERP_SETTLEMENT/PERP_FUNDING journals.
  - PerpetualService: openPosition (locks margin), close/liquidate (mark-price PnL,
    insurance backstop so users never go negative), funding rate (long pays short,
    idempotent by round). V1400/V1401.
- ALL 15 domain modules built. All 6 phases delivered.
- Refinements DONE this pass: derivatives position averaging + partial close;
  account freeze (compliance freeze/unfreeze, gates order + withdrawal, admin
  freeze/unfreeze audited); admin REST (/v1/admin, ADMIN role); wallet custody
  reconciliation (vs proof-of-reserves liabilities, critical alarm).
- Remaining (documented in TECHDEBT):
  - tax (15): legal-blocked (mechanism + rates need consultant; IDR kurs).
  - derivatives: ADL, cross/isolated margin modes, real mark-price+funding-premium feed.
  - Small/unblocked follow-ups: deposit detector (webhook/poll, mock), MM inventory
    skew + re-quote, anti-phishing code, velocity limits (Valkey), more notification
    producers, admin config/kill-switch ops.
  - Vendor-blocked: real Fystack/KYC/email/price-feed integrations, PFAK/OJK licence.
  - fee (11) DONE — maker/taker rates frozen per order, deducted at settlement to
    kyra:fee:*. Tiers/overrides planned.
  - risk (09 spot) DONE — checkOrder (max notional + price band) wired into order
    placement. Velocity limits (Valkey) planned next.
  - NOT blocked, buildable next: MARKET orders (engine needs a quote-budget path
    for market-buy; market-sell works on the existing by-qty path), velocity
    limits (Valkey), multi-interval candles (aggregate 1m), admin backoffice (12),
    liquidity MM bot (14). tax (15): PARTIALLY BLOCKED — withholding mechanism is
    buildable but the IDR conversion needs a reference-rate source + consultant
    confirmation of rates (see TECHDEBT scope).

## Layout
```
kyra-common/      shared kernel: Money, AssetId, PairSymbol, EventEnvelope, Result, Ids (ULID)
modules/<name>/   14 domain modules; packages api/ (public) | domain/ | infra/ (internal)
kyra-app/         Quarkus app wiring; application.properties; db/migration; ArchUnit + smoke tests
kyra-doc/         all specs + ADRs
```

## Hard rules (enforced by tests/review — never violate)
1. A module may only reference a neighbor module's `api` package (ArchUnit `ModuleBoundaryTest`).
2. Money = `Money`/BigDecimal. double/float for monetary values is FORBIDDEN.
3. Balances = double-entry ledger (account module). No balance column is ever UPDATEd without a journal.
4. One Postgres schema per module; cross-schema FKs forbidden. Flyway numbering: `V<module*100+seq>__desc.sql` (identity=1xx, account=2xx, …).
5. Events use `EventEnvelope` + `trace_context`; written to the outbox in the same transaction as the data mutation.
6. Valkey = disposable cache only, never a source of truth.
7. Money/order endpoints are idempotent; IDs are ULIDs (`Ids.newUlid()`).
8. Never log PII/secrets; API errors never leak internals (see kyra-doc/modules/18).
9. Matching engine must be deterministic — no wall-clock/Random in domain logic.

## Build & run (this WSL environment)
```bash
export JAVA_HOME=~/jdks/jdk-21.0.11+10 && export PATH=$JAVA_HOME/bin:$PATH   # REQUIRED: system java = 25, project = 21
./mvnw verify                              # build + tests (Dev Services need Docker)
./mvnw -DskipTests install                 # once, before first dev-mode run
./mvnw -f kyra-app/pom.xml quarkus:dev     # dev mode (do NOT use -pl without -am)
docker compose -f docker-compose.dev.yml up                        # Postgres+Valkey
docker compose -f docker-compose.dev.yml --profile obs up          # + Grafana/OTLP :3000
docker compose -f docker-compose.dev.yml --profile app up --build  # packaged app (package first)
```
- **Native executable** (GraalVM native-image, no JVM at runtime):
  ```bash
  export JAVA_HOME=/usr/lib/jvm/mandrel-java25-25.0.3.0-Final   # Mandrel has native-image; Quarkus 3.37 accepts it
  ./mvnw -pl kyra-app -am verify -Dnative -Dquarkus.native.native-image-xmx=4g   # cap xmx on the 7.6G box
  # → kyra-app/target/kyra-app-*-runner (ELF, ~110MB, boots in ~0.4s). AppSmokeIT runs against the binary.
  ```
  ULID lib (f4b6a3) is forced `--initialize-at-run-time` in application.properties — its static
  SecureRandom must not be frozen into the image heap. Container build (no local Mandrel):
  add `-Dquarkus.native.container-build=true` (jdk-21 builder image, needs Docker).
- Quarkus CLI: via jbang (`~/.jbang/bin/quarkus`, v3.37.3). The `/usr/local/bin/quarkus` symlink is BROKEN — do not use it.
- Health `/q/health` · metrics `/q/metrics`.

## Git & GitHub
- Remote: `git@github-koriebruh:koriebruh/Kyra-Exchange.git` — SSH host alias `github-koriebruh` (see `~/.ssh/config`) using key `~/.ssh/id_ed25519_kyra` (no passphrase, registered to the `koriebruh` account). Plain `git push` works. Do not use `git@github.com:` directly — the default SSH key belongs to a different GitHub account.
- NEVER push without the user's confirmation.
- LICENSE is proprietary — never replace it with an open-source license.

## Working conventions with the user
- Conversation language: Indonesian. This is a serious product — the user demands rigor: re-check completeness, don't miss logic/edge cases, and always verify things actually run (not just compile) before claiming done.
- Build phases are sequential; each phase has exit criteria in kyra-doc/README.md §5 — meet and prove them before moving on.
- Custody = Fystack (verify their current API docs before implementing the wallet module). Indonesian compliance = OJK/PFAK; tax = module 15.
