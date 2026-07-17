# Kyra Exchange — Dokumentasi Desain & Rencana Build

> Crypto exchange production-grade. Spot dulu, derivatives menyusul.
> Arsitektur: **modular monolith** di atas **Quarkus (Java 21)**.
> Status: fase desain → implementasi. Dokumen ini = sumber kebenaran arah produk & teknis.

---

## 1. Visi & Keputusan Kunci

| Keputusan | Pilihan | Alasan |
|---|---|---|
| Target | Produksi real-money, cikal bakal perusahaan | Bukan proyek belajar — semua desain harus auditable & aman |
| Produk | Spot (launch) → Derivatives/perpetual (fase lanjut) | Spot = fondasi; arsitektur sudah menyiapkan slot derivatives |
| Fiat | Crypto-only dulu, pair berbasis stablecoin (USDT/USDC) | Tanpa bank integration → launch lebih cepat, regulasi lebih ringan |
| Custody | **Fystack** (MPC wallet-as-a-service) via API | Tidak pegang private key sendiri → risiko & effort turun drastis |
| Backbone | **PostgreSQL-sentris** (opsi A) | Postgres + Valkey, event in-process + outbox table. Broker (Redpanda) menyusul saat perlu |
| Hosting | VPS self-managed, Docker Compose | Murah, kontrol penuh, cukup untuk fase awal |
| Bahasa/stack | Java 21, Quarkus, Maven multi-module | Native image (Mandrel) opsional untuk startup cepat |

## 2. Prinsip Non-Negotiable

1. **Ledger double-entry = satu-satunya sumber kebenaran saldo.** Tidak ada kolom `balance` yang di-UPDATE langsung. Semua mutasi dana = sepasang entry debit–credit dalam satu transaksi DB.
2. **Matching engine in-memory**, single-threaded per pair, deterministic. Persist via event log append-only + snapshot. DB-based matching dilarang (terlalu lambat).
3. **Modul terisolasi.** Komunikasi antar modul hanya lewat interface `api` + domain event. Dilarang query tabel modul lain.
4. **Semua endpoint yang menyentuh dana = idempotent** (idempotency key wajib).
5. **Audit log immutable** untuk semua aksi sensitif (login, withdraw, perubahan config, aksi admin).
6. **Rekonsiliasi harian** ledger internal vs saldo Fystack. Selisih = alarm + investigasi manual, bukan auto-fix.
7. **Abstraksi event publisher dari hari 1** — pindah dari outbox Postgres ke broker = ganti 1 implementasi, bukan refactor.
8. **Valkey = cache disposable only.** Dilarang menyimpan data yang tidak bisa direkonstruksi dari Postgres (18 §A1).
9. **Ownership check (anti-IDOR) di setiap endpoint ber-resource-ID** + respons 404 untuk resource orang lain; error API tidak pernah bocorkan internal (18 §B1-B2).
10. **Webhook masuk selalu diverifikasi + selalu ada polling fallback**; webhook keluar selalu di-guard anti-SSRF (18 §A4, B7, B8).

## 3. Arsitektur

```
                        ┌──────────────────────────────────────────┐
   Users / Bots ──────► │  Caddy/Nginx (TLS, rate-limit L7)        │
                        └───────────────┬──────────────────────────┘
                                        │ REST + WebSocket
                        ┌───────────────▼──────────────────────────┐
                        │  kyra-app (Quarkus, satu JVM)            │
                        │                                          │
                        │  identity  account  market   order      │
                        │  matching  settlement marketdata        │
                        │  wallet    risk     compliance fee      │
                        │  admin     notification liquidity       │
                        │                                          │
                        │  komunikasi: CDI events / Vert.x bus     │
                        └───────┬──────────────┬───────────────────┘
                                │              │
                     ┌──────────▼───┐   ┌──────▼──────┐        ┌─────────────┐
                     │ PostgreSQL 16│   │ Valkey      │        │ Fystack API │
                     │ data+ledger+ │   │ cache, rate │        │ (custody)   │
                     │ event outbox │   │ limit, sess │        └─────────────┘
                     └──────────────┘   └─────────────┘
```

### Struktur repo (Maven multi-module)

```
kyra-exchange/
├── kyra-app/            # Quarkus main, wiring, config, REST/WS layer
├── kyra-common/         # Money, AssetId, Pair, event envelope, error types
├── modules/
│   ├── identity/        ├── settlement/     ├── fee/
│   ├── account/         ├── marketdata/     ├── admin/
│   ├── market/          ├── wallet/         ├── notification/
│   ├── order/           ├── risk/           └── liquidity/
│   ├── matching/        ├── compliance/
└── Kyra-doc/            # dokumentasi ini
```

Tiap modul punya sub-package: `api/` (interface + DTO yang boleh dipakai modul lain), `domain/` (logic), `infra/` (persistence, klien eksternal). Enforcement pakai ArchUnit test.

## 4. Indeks Modul (urutan prioritas build)

| # | Modul | Dokumen | Ringkas |
|---|---|---|---|
| 01 | Identity (Auth) | [modules/01-identity.md](modules/01-identity.md) | Register, login, 2FA, API key, session |
| 02 | Account (Ledger) | [modules/02-account-ledger.md](modules/02-account-ledger.md) | Double-entry ledger, saldo, hold |
| 03 | Market | [modules/03-market.md](modules/03-market.md) | Pair, tick/lot size, status market |
| 04 | Order | [modules/04-order.md](modules/04-order.md) | Order intake, validasi, lifecycle |
| 05 | Matching | [modules/05-matching.md](modules/05-matching.md) | Order book in-memory, matching engine |
| 06 | Settlement | [modules/06-settlement.md](modules/06-settlement.md) | Trade → mutasi ledger atomik |
| 07 | Market Data | [modules/07-marketdata.md](modules/07-marketdata.md) | Candle, ticker, depth, WS stream |
| 08 | Wallet | [modules/08-wallet.md](modules/08-wallet.md) | Fystack: deposit, withdraw, rekonsiliasi |
| 09 | Risk | [modules/09-risk.md](modules/09-risk.md) | Limit, velocity check; nanti margin & liquidation |
| 10 | Compliance | [modules/10-compliance.md](modules/10-compliance.md) | KYC, AML, sanction screening, travel rule |
| 11 | Fee | [modules/11-fee.md](modules/11-fee.md) | Maker/taker, tier VIP, fee report |
| 12 | Admin | [modules/12-admin.md](modules/12-admin.md) | Backoffice: approval, freeze, config, dashboard |
| 13 | Notification | [modules/13-notification.md](modules/13-notification.md) | Email, webhook, push |
| 14 | Liquidity | [modules/14-liquidity.md](modules/14-liquidity.md) | MM bot internal / integrasi liquidity partner |
| 15 | Tax | [modules/15-tax.md](modules/15-tax.md) | Withholding PPh/PPN per trade, setor & lapor DJP |
| 16 | Ops & Security | [modules/16-ops-security.md](modules/16-ops-security.md) | Environments, deploy, DR, alerting, incident, security program, PoR |
| 17 | Observability | [modules/17-observability.md](modules/17-observability.md) | OTel tracing, standar logging JSON, metrics, exporter infra, SLO, ISO/standar |
| 18 | Data Protection | [modules/18-data-protection.md](modules/18-data-protection.md) | Matriks durability, anti-IDOR/SSRF/enumeration, error hygiene, aturan Valkey, polling sweep |

## 5. Fase Build (detail)

### Fase 0 — Fondasi (± 1-2 minggu)
**Tujuan:** skeleton jalan, developer experience beres.
- [ ] Repo git + Maven multi-module skeleton (semua modul kosong tapi ter-wiring)
- [ ] `kyra-common`: `Money` (BigDecimal + asset, tanpa float!), `Pair`, event envelope, `Result`/error types
- [ ] Docker Compose dev: Postgres 16, Valkey, app
- [ ] Flyway migration setup per modul (schema terpisah per modul: `identity.*`, `account.*`, dst)
- [ ] CI (GitHub Actions): build, test, ArchUnit boundary check
- [ ] Observability baseline (spec lengkap di modul 17): OpenTelemetry tracing (Tempo) + JSON structured logging dengan trace_id (Loki) + Micrometer/Prometheus + exporter infra (node/postgres/redis/blackbox/cAdvisor) + Grafana korelasi log↔trace↔metric + health checks
- [ ] ADR (Architecture Decision Records) folder — keputusan besar dicatat
- **Exit criteria:** `docker compose up` → app hidup, `/q/health` hijau, CI hijau.

### Fase 1 — Identitas & Uang (± 3-4 minggu)
**Tujuan:** user bisa punya akun; sistem bisa mencatat uang dengan benar.
- [ ] Identity: register, login (Argon2id), JWT + refresh, 2FA TOTP, session management, API key HMAC, captcha (Turnstile), anti-phishing code
- [ ] Account: ledger double-entry lengkap (journal, entry, hold/release), snapshot saldo
- [ ] Property-based test ledger: invariant total-aset-konstan & no-negative-balance
- [ ] Audit log framework (dipakai semua modul)
- **Exit criteria:** bisa register → login → 2FA → lihat saldo 0; test ledger 100% invariant hold.

### Fase 2 — Trading Internal (± 4-6 minggu)
**Tujuan:** trading jalan penuh dengan dana dummy (belum ada deposit asli).
- [ ] Market: CRUD pair (admin), tick/lot/min-notional rules, status (ACTIVE/HALT/DELIST)
- [ ] Order: place/cancel, validasi, hold saldo, state machine lengkap
- [ ] Matching: order book TreeMap, limit + market order, single-thread per pair, event log + snapshot + replay recovery
- [ ] Settlement: konsumsi TradeExecuted → mutasi ledger atomik + fee basic (flat)
- [ ] Deterministic replay test matching engine (input sama → hasil identik byte-per-byte)
- **Exit criteria:** dua akun dummy bisa saling trade limit/market order; kill -9 app → restart → order book pulih identik.

### Fase 3 — API Publik & Market Data (± 3-4 minggu)
**Tujuan:** exchange kelihatan hidup dari luar.
- [ ] Marketdata: trade history, candle OHLCV (1m→1d), ticker 24h, depth snapshot + delta
- [ ] WebSocket stream: orderbook, trades, ticker, user orders (private channel)
- [ ] REST API publik v1 (dokumentasi OpenAPI) + rate limiting per user/IP (Valkey)
- [ ] Idempotency key enforcement di semua endpoint dana/order
- **Exit criteria:** bot eksternal bisa trading penuh via REST+WS; load test 1k order/detik per pair tanpa error.

### Fase 4 — Real Money (± 6-8 minggu) ⚠️ titik kritis
**Tujuan:** deposit & withdraw crypto asli. Mulai fase ini, bug = kehilangan uang beneran.
- [ ] Wallet: integrasi Fystack (deposit webhook + verifikasi signature, withdraw API, address per user)
- [ ] **Tax: withholding PPh/PPN per trade + akun ledger `kyra:tax:*` + rekap periode** (konsultan pajak paralel)
- [ ] Compliance: KYC onboarding (provider eksternal), sanction + PEP screening, level akun → limit
- [ ] Risk: limit withdraw harian, velocity check, approval threshold
- [ ] Admin backoffice: approval withdraw manual, freeze akun/aset, dashboard operasional
- [ ] Rekonsiliasi harian otomatis ledger ↔ Fystack + alarm selisih
- [ ] Notification: email transaksi, webhook
- [ ] Security hardening: pentest eksternal, review dependency (OWASP), secrets management (SOPS/Vault)
- [ ] Runbook incident + backup restore drill (benar-benar dicoba restore!)
- **Exit criteria:** deposit kecil asli masuk benar; withdraw dengan approval jalan; rekonsiliasi 7 hari berturut-turut selisih nol; pentest findings critical/high = nol.
- **Paralel (non-kode):** urus badan hukum + lisensi PFAK/OJK. Tanpa ini tidak boleh terima dana publik Indonesia.

### Fase 5 — Launch & Liquidity (± 3-4 minggu)
**Tujuan:** exchange layak dipakai publik.
- [ ] Liquidity: MM bot internal (spread quoting dari harga referensi eksternal) atau kontrak liquidity partner
- [ ] Fee: maker/taker penuh + tier volume
- [ ] Internal transfer antar user; stop/trigger order engine (STOP_LIMIT, OCO)
- [ ] Market surveillance dasar (spoofing, wash trading antar akun terkait)
- [ ] Status page publik + monitoring alert (on-call); sandbox/testnet publik untuk developer bot
- [ ] Proof of Reserves (Merkle tree) publikasi pertama; bug bounty private program
- [ ] DR: replica Postgres, failover terdokumentasi & dilatih
- **Exit criteria:** spread & depth sehat di pair utama 24/7; soft launch user terbatas → publik.

### Fase 6 — Derivatives (± 8-12 minggu)
**Tujuan:** perpetual futures.
- [ ] Mark price & index price engine (agregasi harga eksternal)
- [ ] Margin account (cross dulu, isolated menyusul) di modul account
- [ ] Funding rate: hitung + settle per 8 jam
- [ ] Liquidation engine di modul risk + insurance fund
- [ ] Matching: reuse engine spot, instrumen baru
- **Exit criteria:** perpetual BTC-USDT jalan di testnet internal 30 hari tanpa insiden ledger.

## 6. Infra Deployment

```
VPS-1 (app):   Quarkus app, Valkey, Caddy (TLS otomatis)
VPS-2 (data):  PostgreSQL 16 primary, backup harian → object storage (restic, terenkripsi)
               streaming replica (awalnya boleh di VPS-1)
VPS-3 (obs):   Prometheus, Alertmanager, Grafana, Loki, Tempo
               + Grafana Alloy sebagai agent di TIAP VPS (log+metrics+traces, satu binary)
               + exporters: node, postgres, redis, blackbox, cAdvisor
               (bisa gabung VPS-1 di awal; Grafana di belakang VPN/IP allowlist)
               + probe uptime eksternal pihak ketiga (kalau semua VPS mati, tetap ada yang teriak)
```

- Deploy: GitHub Actions → build image → push registry → `docker compose pull && up -d` via SSH
- Secrets: SOPS + age (file terenkripsi di repo) atau Vault kalau tim membesar
- Disk: **LUKS full-disk encryption** minimal di volume DB & backup (PII tidak telanjang di disk provider); Postgres `synchronous_commit=on` wajib (jangan dimatikan demi benchmark)
- Backup: full harian + WAL archiving (point-in-time recovery). **Restore dilatih tiap bulan.**
- Skala nanti: pisah matching engine ke proses sendiri → baru pertimbangkan k8s/Redpanda

## 7. Konvensi Teknis

- **Uang:** `BigDecimal` presisi per-asset, simpan sebagai `NUMERIC` di DB. Dilarang `double/float`.
- **ID:** ULID (sortable, no coordination).
- **Waktu:** UTC semua, `Instant`. Timezone urusan frontend.
- **Schema DB:** satu schema Postgres per modul. Cross-schema FK dilarang.
- **Event:** envelope `{event_id, type, version, occurred_at, trace_context, payload}`, disimpan ke outbox table dalam transaksi yang sama dengan mutasi data. `trace_context` (W3C traceparent) wajib — tracing nyambung lintas modul/async.
- **Logging:** JSON structured, field wajib + larangan PII/secret — standar penuh di modul 17. Timestamp internal ISO-8601/RFC 3339 UTC.
- **Tracing:** OpenTelemetry, OTel semantic conventions + namespace `kyra.*`.
- **Testing:** unit + property-based (jqwik) untuk ledger/matching, Testcontainers untuk integration, ArchUnit untuk boundary.

## 8. Konvensi API Publik

- Versi di path: `/v1/...`. Breaking change = versi baru, deprecation minimal 90 hari dengan pengumuman.
- Error format seragam: `{code: "INSUFFICIENT_BALANCE", message, details?}` — kode string stabil (bot bergantung ke ini), katalog kode terdokumentasi di OpenAPI.
- Rate limit headers: `X-RateLimit-Limit / -Remaining / -Reset` di semua respons; 429 + `Retry-After` saat kena.
- Pagination: cursor-based (`cursor` + `limit`), bukan offset.
- Timestamp: epoch milliseconds di API (konvensi industri), ISO-8601 hanya di dokumen.
- Angka uang/qty dikirim sebagai **string** di JSON (hindari presisi float di klien).

## 9. Non-Goals (sekarang — sadar, bukan lupa)

- Fiat IDR on/off-ramp (crypto-only dulu; rancang ulang saat lisensi & bank siap)
- Earn/staking/lending, launchpad, NFT — fokus exchange inti
- Margin spot (pinjaman) — beda dari derivatives fase 6, butuh desain kredit sendiri
- Aplikasi mobile & frontend web = proyek terpisah (dokumen ini backend only); API dirancang frontend-agnostic
- Customer support ticketing = pakai SaaS (Zendesk/Freshdesk dsb) + integrasi lihat-profil dari admin, bukan bangun sendiri
- Multi-region active-active — DR single-region dulu

---

Fitur yang ditunda karena nunggu piece lain (FE, modul notification, dll): [`TECHDEBT.md`](TECHDEBT.md).

*Dokumen per modul ada di [`modules/`](modules/). Mulai baca dari 01.*
