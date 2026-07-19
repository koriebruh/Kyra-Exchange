# Running Fystack (self-hosted) + connecting Kyra to it

Fystack is open-core and self-hosts via Docker. This is the opt-in path to run a
local Apex API and point Kyra's custody at it. It is **heavy** (~14 services,
~4 GB RAM, its own DBs) so it is deliberately **not** in `docker-compose.dev.yml`;
run it from Fystack's own scripts.

Status: Kyra ships a real `HttpCustodyProvider` (HMAC-signed Apex client,
unit-tested) but is **not production-wired** — see the gaps at the bottom.

## 1. Bring up the Fystack stack

```bash
git clone https://github.com/fystack/fystack-selfhost-scripts
cd fystack-selfhost-scripts
# Put your CoinMarketCap key in Fystack's config (its price provider needs it).
# Do NOT put this key in the Kyra repo — it belongs to Fystack's config.
#   config.yaml -> price_providers -> coinmarketcap api key
./fystack-ignite.sh        # generates MPCIUM configs + starts all services
```

Services after it is up:

- **Apex API** — `http://localhost:8150` (base for Kyra: `http://localhost:8150/api/v1`)
- **Fystack UI** — `http://localhost:8015` (create an API key + wallet here)
- MPCIUM nodes on **8080–8082**, plus PG :5433 / Redis :6380 / Mongo :27018 / NATS :4223 / Consul :8501

> ⚠ **Port clash:** MPCIUM uses **8080**, the same as the Kyra app. Run Kyra on
> another port while Fystack is up: `QUARKUS_HTTP_PORT=8090 ./mvnw ... quarkus:dev`.

Requirements: Docker, ~4 vCPU / 4 GB RAM free, a CoinMarketCap API key.

## 2. Provision in the Fystack UI

At `http://localhost:8015`:
1. Create a **workspace** → note its id.
2. Create a **custody wallet** (`wallet_type=mpc`) → note the `wallet_id`.
3. Note each **asset's Fystack UUID** for the assets Kyra trades (e.g. USDT).
4. Create an **API key + secret** (this is what Kyra signs requests with).

## 3. Point Kyra at Fystack

Set these as **env / secrets** (never commit — `.env*` and `secrets/` are gitignored):

```bash
export KYRA_CUSTODY_PROVIDER=fystack
export KYRA_CUSTODY_FYSTACK_BASE_URL=http://localhost:8150/api/v1
export KYRA_CUSTODY_FYSTACK_API_KEY=...        # from the Fystack UI
export KYRA_CUSTODY_FYSTACK_API_SECRET=...
export KYRA_CUSTODY_FYSTACK_WORKSPACE_ID=...
export KYRA_CUSTODY_FYSTACK_WALLET_ID=...
export KYRA_CUSTODY_FYSTACK_ASSET_IDS__USDT=<fystack-usdt-uuid>
```

`kyra.custody.provider` is a **build-time** selector (like the email backend), so
enabling Fystack means building/running with `KYRA_CUSTODY_PROVIDER=fystack`.
When unset, Kyra uses `MockCustodyProvider` (default, all tests).

## 4. Gaps to close before trusting it with real money (TECHDEBT)

The Apex client's signing, endpoints, idempotency header and response parsing are
unit-tested, but these need validation against the **running** stack:

- **Per-user deposit attribution.** `depositAddress` currently returns the
  configured custody wallet's address. An exchange needs a wallet-per-user model
  (`wallet_purpose=user`) with a persisted `userId → wallet_id` map so deposits
  are attributable. Finalise this against live Fystack.
- **HMAC PATH convention.** Confirm whether the signed `path` includes the
  `/api/v1` prefix and/or the query string (docs are ambiguous; the client signs
  path+query).
- **Idempotency key format.** Fystack documents "a unique UUID"; Kyra passes the
  withdrawal ULID. Confirm it is accepted.
- **Custody balance / reconciliation.** No per-asset balance endpoint on Apex yet
  — reconciliation source against real custody is unresolved.
- **Withdrawal approval webhook.** Withdrawals return `PENDING_APPROVAL`; wire the
  status callback so Kyra advances the withdrawal on execution.
