# 06 — Settlement

> Jembatan antara dunia cepat (matching in-memory) dan dunia benar (ledger di DB). Mengubah trade menjadi mutasi uang yang final.

## Tujuan
Mengkonsumsi `TradeExecuted` dari matching engine dan mengeksekusi perpindahan dana di ledger secara atomik, exactly-once, beserta perhitungan fee.

## Posisi di Alur

```
matching (in-memory, cepat)
   │ TradeExecuted (setelah event ter-flush ke matching_events)
   ▼
settlement consumer (batch, ordered per pair)
   │ 1 transaksi DB per batch:
   │   - insert trades
   │   - journal TRADE_SETTLEMENT per trade (debit/credit buyer, seller, fee)
   │   - update orders (filled_qty, status)
   │   - outbox: TradeSettled, OrderStatusChanged, BalanceChanged
   ▼
PostgreSQL (final)
```

## Logic Detail

### F1. Konsumsi & idempotensi
- Consumer membaca `matching_events` (atau in-process queue dengan checkpoint ke `matching_events.seq`) per pair, **berurutan** (seq ascending) — urutan penting supaya partial fill terakumulasi benar.
- Idempotent by `trade_id`: journal reference = `TRADE_SETTLEMENT:trade_id` → duplikat (replay/crash) otomatis no-op di ledger.
- Checkpoint `last_settled_seq` per pair disimpan; restart lanjut dari situ.

### F2. Perhitungan settlement per trade
Input: `{price, qty, taker_side, maker_user, taker_user}` + fee rate dari modul fee.

```
quote_amount = price × qty
maker_fee = quote/base amount × maker_rate   (fee dipotong dari aset yang DITERIMA)
taker_fee = ... × taker_rate

BUY side terima base  → fee dipotong dari base  (terima qty − fee_base)
SELL side terima quote → fee dipotong dari quote (terima quote_amount − fee_quote)
```
- Pembulatan: fee dibulatkan KE ATAS pada scale aset (exchange tidak pernah rugi pembulatan), penerimaan user dibulatkan ke bawah; selisih → akun `kyra:fee`. Deterministik & terdokumentasi di API docs publik.
- Journal entries (contoh di dok ledger 02). Konsumsi dari `hold` kedua belah pihak.
- **Pajak Indonesia (lihat 15-tax):** entries tambahan per trade — PPh dipotong dari penjual, PPN dari pembeli → akun `kyra:tax:*`. Rate dibekukan per order sama seperti fee. `TaxApi.recordTradeTax()` dipanggil dalam transaksi settle yang sama.

### F3. Update order state
- `filled_qty += qty`; penuh → `FILLED` + release hold sisa (untuk BUY yang harganya lebih baik dari limit → kelebihan hold quote dikembalikan).
- `avg_fill_price` dihitung incremental.

### F4. Batching
- Settle per batch (misal 100 trade / 10 ms) dalam SATU transaksi DB → throughput tinggi.
- Gagal satu trade di batch (anomali, mis. hold tidak cukup — seharusnya mustahil) → **seluruh batch rollback**, pair di-HALT otomatis, alarm critical. Ini indikasi bug serius, bukan kondisi normal yang di-skip.

### F5. Lag monitoring
- Metric `settlement_lag = matching.seq − last_settled_seq` per pair. Lag > threshold → alarm (saldo user telat update = tiket support).

## Data Model (schema `settlement`)

```sql
trades(id ULID PK, pair, price NUMERIC, qty NUMERIC,
       maker_order_id, taker_order_id, maker_user_id, taker_user_id,
       taker_side, maker_fee NUMERIC, taker_fee NUMERIC, fee_asset_maker,
       fee_asset_taker, seq BIGINT, executed_at, settled_at,
       UNIQUE(pair, seq))
settlement_checkpoints(pair PK, last_settled_seq BIGINT, updated_at)
```

`trades` = sumber data marketdata (candle) & laporan — immutable.

## Domain Events (via outbox, dalam transaksi settle)
- `TradeSettled {trade_id, pair, price, qty, maker/taker user}` → marketdata, fee report
- `OrderStatusChanged` → order module state, WS private
- `BalanceChanged` → WS private, notification

## Edge Cases
- **Hold tidak cukup saat settle** (bug hulu): batch rollback + halt pair + alarm. Tidak pernah settle sebagian dari sebuah trade.
- **Crash setelah commit sebelum checkpoint update:** checkpoint di transaksi yang sama dengan settle → mustahil terpisah.
- **Fee rate berubah di tengah:** rate diambil pada SAAT trade dieksekusi (dibawa di event dari intake order / dibekukan per order), bukan saat settle → deterministic.
- **Trade sangat kecil** hingga fee dibulatkan = seluruh amount → min_notional di market mencegah; guard tambahan: penerimaan user tidak boleh ≤ 0.

## Testing
- Rekonsiliasi test: N trade acak → assert sum ledger per aset = 0, saldo akhir kedua user + fee = saldo awal.
- Idempotency: settle batch yang sama 2x → ledger tidak berubah pada run kedua.
- Crash test: kill di tengah batch → restart → tidak ada trade hilang / dobel.
- Presisi: kombinasi harga/qty ekstrem (dust, max) → pembulatan sesuai aturan, tidak pernah negatif.
