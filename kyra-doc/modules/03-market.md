# 03 — Market

> Katalog instrumen: aset apa yang ada, pair apa yang bisa ditrade, dengan aturan apa.

## Tujuan
Mendefinisikan aset & trading pair beserta aturan presisi dan status operasionalnya. Modul referensi — hampir semua modul lain membaca dari sini.

## Tanggung Jawab
- Registry aset (BTC, ETH, USDT, ...) + presisi + status
- Registry pair (BTC-USDT, ...) + aturan trading
- Status lifecycle market (halt, delist) dan broadcast perubahannya

## Fitur Detail

### F1. Asset registry
Per aset:
- `symbol` (BTC), `name`, `scale` (jumlah desimal: BTC=8, USDT=6)
- `status`: `ACTIVE` / `DEPOSIT_ONLY` / `WITHDRAW_ONLY` / `FROZEN`
  (contoh: chain sedang upgrade → `WITHDRAW_ONLY` supaya deposit tidak nyangkut)
- Mapping aset ke chain (contract address untuk token ERC-20, confirmations minimum) — dipakai modul wallet (custody)

### F2. Pair registry
Per pair (base-quote, contoh BTC-USDT):
- `tick_size` — kelipatan harga valid (0.01 USDT)
- `lot_size` / `step_size` — kelipatan qty valid (0.00001 BTC)
- `min_notional` — nilai order minimum (mis. 5 USDT) → cegah dust order
- `min_qty`, `max_qty`, `max_open_orders_per_user`
- `status`: `PENDING` → `ACTIVE` → `HALT` → `DELISTED`
  - `HALT`: cancel-only (order baru ditolak, cancel boleh) — dipakai saat insiden/berita besar
  - `DELIST`: umumkan → HALT → cancel semua open order otomatis → tutup

### F3. Perubahan config yang aman
- Semua perubahan (tick size, status, dll) = versioned + audit (siapa, kapan, nilai lama→baru).
- Perubahan tick/lot size hanya boleh saat pair HALT (order book harus kosong dari order dengan presisi lama) → prosedur: HALT → cancel all → ubah → ACTIVE.
- Perubahan status di-broadcast sebagai event agar matching & order bereaksi instan (bukan polling).

### F4. Trading calendar (opsional, default 24/7)
Crypto 24/7, tapi siapkan flag `scheduled_maintenance` untuk window maintenance dengan notifikasi.

## Data Model (schema `market`)

```sql
assets(symbol PK, name, scale SMALLINT, status, custody_ref JSONB,
       min_confirmations INT, created_at)
pairs(symbol PK,            -- "BTC-USDT"
      base_asset FK, quote_asset FK,
      tick_size NUMERIC, step_size NUMERIC, min_notional NUMERIC,
      min_qty NUMERIC, max_qty NUMERIC, max_open_orders INT,
      status, created_at)
config_history(id, entity_type, entity_id, changed_by, old JSONB, new JSONB, at)
```

Cache: seluruh registry kecil → in-memory cache di JVM, invalidate via event `MarketConfigChanged`. Baca rule per order TIDAK boleh kena DB.

## Domain Events
- `PairStatusChanged {pair, old, new}` — konsumen: matching (halt intake), order (reject/cancel-all), marketdata (tandai di ticker)
- `AssetStatusChanged {asset, old, new}` — konsumen: wallet (stop deposit/withdraw)
- `MarketConfigChanged {entity, id}` — konsumen: semua cache lokal

## API
```
GET /v1/market/assets            GET /v1/market/pairs
GET /v1/market/pairs/{symbol}    -- publik, tanpa auth
-- admin (lewat modul admin): create/update asset & pair, ubah status
```

## Validasi yang Disediakan (dipakai modul order)
```java
MarketApi.validate(pair, price, qty) →
  - price % tick_size == 0
  - qty % step_size == 0
  - price*qty >= min_notional
  - min_qty <= qty <= max_qty
  - pair.status == ACTIVE
```
Pembulatan TIDAK dilakukan diam-diam — order tidak valid = reject dengan kode error jelas.

## Edge Cases
- Delist pair yang masih ada open order → wajib lewat state machine (HALT → auto-cancel semua + release hold → DELISTED). Tidak boleh langsung.
- Aset dipakai banyak pair (USDT) di-freeze → semua pair quote USDT ikut HALT otomatis.
- Tick size change historis → candle/harga lama tidak diubah; hanya order baru kena rule baru.

## Testing
- Unit: validasi tick/lot/notional semua kombinasi batas.
- Integration: state machine pair (transisi ilegal ditolak), event terpancar saat perubahan status.
