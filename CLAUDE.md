# Kyra Exchange — Context for Claude

Production crypto exchange (real money, foundation of the owner's company — NOT a learning project).
Modular monolith, Quarkus 3.x, Java 21, Maven multi-module.

## Design source of truth
- `kyra-doc/README.md` — architecture, 10 non-negotiable principles, 7 build phases with exit criteria, technical & API conventions
- `kyra-doc/modules/01..18-*.md` — detailed spec per module (features, flows, data model, edge cases, testing). **Read the module spec BEFORE implementing that module.**
- `kyra-doc/adr/` — architecture decisions. New significant decision = write a new ADR.

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
- Next: close phase 1 stragglers OR start Phase 2 (market + order + matching +
  settlement) per kyra-doc/modules 03-06. Ledger (02) is ready to build on.

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
