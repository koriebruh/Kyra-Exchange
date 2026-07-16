# 02 — Account (Ledger Double-Entry)

> Jantung exchange. Satu-satunya sumber kebenaran tentang uang. Kalau modul ini salah, perusahaan mati.

## Tujuan
Mencatat semua kepemilikan & perpindahan aset dengan ledger double-entry yang auditable. Menyediakan operasi saldo (available, hold) untuk modul lain.

## Konsep Inti

### Double-entry
Setiap mutasi dana = **journal** berisi minimal 2 **entry** (debit & credit) yang totalnya nol per aset. Contoh trade 1 BTC @ 50.000 USDT (fee 0.1%):

```
Journal: TRADE_SETTLEMENT trade_id=T123
  entry: account=buyer:USDT:hold      amount=-50000
  entry: account=seller:USDT:main     amount=+49950
  entry: account=kyra:fee:USDT        amount=+50
  entry: account=seller:BTC:hold      amount=-1
  entry: account=buyer:BTC:main       amount=+0.999
  entry: account=kyra:fee:BTC         amount=+0.001
```
Sum per aset = 0 → uang tidak pernah tercipta/hilang, hanya berpindah.

### Jenis akun (chart of accounts)
- `user:{user_id}:{asset}:main` — saldo tersedia
- `user:{user_id}:{asset}:hold` — saldo tertahan (open order, withdraw pending)
- `kyra:fee:{asset}` — pendapatan fee
- `kyra:hotwallet:{asset}` — cermin aset di custody (sisi sistem)
- `external:{asset}` — dunia luar (deposit masuk dari sini, withdraw keluar ke sini)

Deposit = `external → user:main`. Withdraw = `user:hold → external`. Dengan ini **total internal selalu bisa direkonsiliasi** ke saldo custody.

### Available vs Hold
- `available = sum(main)`, `on_hold = sum(hold)`.
- Place order beli → pindahkan quote asset `main → hold` (journal `ORDER_HOLD`).
- Cancel/expire → `hold → main` (journal `HOLD_RELEASE`).
- Fill → konsumsi dari `hold` (journal `TRADE_SETTLEMENT`).
- **Invariant: saldo `main` dan `hold` tidak boleh negatif.** Ditegakkan di DB (constraint + check saat transaksi) dan di property test.

## Fitur Detail

### F1. Journal API (dipakai modul lain, bukan endpoint publik)
```java
AccountApi.post(JournalRequest)   // atomik: semua entry atau tidak sama sekali
AccountApi.hold(userId, asset, amount, ref)      // helper: main → hold
AccountApi.release(userId, asset, amount, ref)   // helper: hold → main
AccountApi.balanceOf(userId, asset)              // {available, on_hold}
```
- Setiap journal wajib bawa: `type`, `reference` (order_id/trade_id/deposit_id — unik per type → idempotensi), `entries[]`.
- Reference duplikat → return journal yang sudah ada (idempotent), bukan error.

### F2. Saldo cepat (snapshot)
- Saldo dihitung dari agregat entries, tapi full-scan mahal → tabel `balances` sebagai **materialized running balance**: di-update dalam transaksi yang sama dengan insert entry (`UPDATE balances SET amount = amount + delta`).
- Constraint `CHECK (amount >= 0)` di `balances` = penjaga terakhir no-negative-balance; kena constraint → seluruh transaksi rollback.
- Job periodik verifikasi `balances == SUM(entries)` per akun (deteksi drift = alarm critical).

### F3. Statement & histori
- Endpoint user: histori transaksi per aset (deposit, withdraw, trade, fee) dengan cursor pagination.
- Export CSV per rentang tanggal (kebutuhan pajak user).

### F3b. Internal transfer antar user (fase 5)
- Kirim aset ke sesama user Kyra (by email/UID) — off-chain, instan, fee 0. Fitur standar CEX, murah diimplementasi (journal `TRANSFER`: `sender:main → receiver:main`).
- Guard: 2FA untuk amount besar, rate limit, kena TM compliance (jalur favorit pencucian antar akun), konfirmasi nama penerima sebagian (anti salah kirim).

### F4. Multi-account type (fase derivatives)
- Fase 6: tambah `user:{id}:{asset}:margin` (collateral) — struktur ledger tidak berubah, hanya tambah account type. Desain dari awal: kolom `account_type` bukan hardcode main/hold.

## Data Model (schema `account`)

```sql
journals(id ULID PK, type ENUM(DEPOSIT,WITHDRAW,ORDER_HOLD,HOLD_RELEASE,
         TRADE_SETTLEMENT,FEE,ADJUSTMENT,TRANSFER), reference TEXT,
         created_at, UNIQUE(type, reference))
entries(id ULID PK, journal_id FK, account_key TEXT, asset TEXT,
        amount NUMERIC(38,18), created_at)
        -- INDEX (account_key, id), append-only: tidak ada UPDATE/DELETE
balances(account_key PK, asset, amount NUMERIC(38,18) CHECK(amount>=0),
         updated_at)
adjustments(id, journal_id FK, reason TEXT, approved_by, evidence_url)
        -- koreksi manual: selalu double-entry juga + wajib approval ADMIN
```

- `entries` **append-only**: revoke UPDATE/DELETE di level grant DB.
- Trigger DB menolak journal yang sum per aset ≠ 0 (defense in depth; validasi utama di aplikasi).

## Domain Events
- `BalanceChanged {user_id, asset, available, on_hold, cause}` — konsumen: WS private stream, notification.
- `JournalPosted {journal_id, type, reference}` — konsumen: audit, rekonsiliasi.

## Konsistensi & Concurrency
- Semua posting journal = satu transaksi Postgres, isolation `READ COMMITTED` + row lock di `balances` (`SELECT ... FOR UPDATE` urut account_key untuk hindari deadlock).
- Hot account (misal `kyra:fee:USDT` kena setiap trade) → fase awal biarkan (lock sebentar), kalau jadi bottleneck: pattern fee accrual batch (kumpulkan, post per detik).
- Modul lain **tidak pernah** menulis tabel account langsung — hanya via `AccountApi` dalam transaksi yang dikoordinasi (settlement pakai transaksi gabungan via API internal, bukan lintas-schema query).

## Edge Cases
- Journal dengan aset presisi beda (BTC 8 desimal, USDT 6) → presisi per-asset dari modul market; ledger simpan NUMERIC(38,18), pembulatan SELALU di edge (order/settlement), ledger menolak nilai dengan presisi melebihi definisi aset.
- Adjustment manual (misal ganti rugi user) → journal type `ADJUSTMENT` + approval + alasan + bukti. Tidak ada jalan pintas.
- User CLOSED → saldo harus nol dulu (dipaksa withdraw/transfer) sebelum penutupan.

## Testing (paling ketat di seluruh sistem)
- Property-based (jqwik): ribuan sequence acak hold/release/settle/deposit/withdraw → invariant: (1) sum semua entry per aset = 0, (2) tidak ada balance negatif, (3) balances == SUM(entries).
- Concurrency test: N thread posting ke akun sama → tidak ada lost update, tidak ada deadlock permanen.
- Idempotency test: journal reference sama dikirim 2x → hanya 1 journal tercatat.
