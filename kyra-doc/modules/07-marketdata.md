# 07 — Market Data

> Wajah publik exchange: ticker, candle, depth, trade feed. Read-heavy, boleh eventually-consistent beberapa ratus ms, tapi tidak boleh salah.

## Tujuan
Mengolah trade & book delta menjadi data pasar yang bisa dikonsumsi UI, bot, dan pihak ketiga — via REST & WebSocket.

## Tanggung Jawab
- Candle OHLCV multi-interval
- Ticker 24 jam (last, high, low, volume, change%)
- Order book depth (snapshot + delta stream)
- Trade history publik
- WebSocket hub: channel publik + private

## Fitur Detail

### F1. Candle (OHLCV)
- Interval: `1m, 5m, 15m, 1h, 4h, 1d` (1m = sumber; lainnya boleh diagregasi).
- Real-time: aggregator in-memory per pair meng-update candle 1m berjalan dari `TradeSettled`; tutup interval → persist + mulai baru.
- Backfill/koreksi: candle historis SELALU bisa dibangun ulang dari tabel `trades` (source of truth) — job rebuild untuk recovery.
- Gap (tidak ada trade): candle kosong TIDAK dibuat; API mengembalikan gap, klien mengisi flat (dokumentasikan).

### F2. Ticker 24h
- Rolling window 24 jam: `last_price, open_24h, high, low, volume_base, volume_quote, change_pct`.
- Implementasi: ring buffer per menit (1440 slot) di memory + rebuild dari candle saat start.
- Push ke WS tiap perubahan (throttle max 1/detik per pair).

### F3. Depth (order book publik)
- Sumber: `BookDelta` event dari matching.
- Snapshot: level teratas (25/100/500) per pair, di-cache di memory, versi = seq matching.
- Protokol WS standar industri: klien terima snapshot + seq, lalu delta berurutan; seq loncat → klien re-request snapshot. (Sama seperti Binance diff-depth — bot-friendly.)
- Agregasi presisi (grouping 0.1 / 1 / 10) dihitung on-the-fly.

### F4. Trade feed publik
- 500 trade terakhir per pair di memory; histori lama via REST dari tabel trades (tanpa user_id! — publik hanya: price, qty, side, waktu).

### F5. WebSocket hub
- Channel publik: `trades:{pair}`, `depth:{pair}`, `ticker:{pair}`, `candle:{pair}:{interval}`
- Channel private: `orders`, `balances` — **auth token dikirim di message pertama setelah connect, DILARANG di URL query string** (query nyangkut di access log proxy/CDN = token bocor)
- **Kontrak anonimitas channel publik:** dilarang memuat `user_id`/`order_id`/apapun yang bisa di-link ke akun; depth = agregat per price level. Ditegakkan schema test (18 §B4)
- Manajemen: heartbeat ping/pong 30s, max subscription per koneksi, backpressure — klien lambat di-drop (slow consumer tidak boleh menahan yang lain), reconnect dengan resume via seq.
- Skala: fase monolith, hub in-process (langganan event bus internal). Nanti pecah → hub baca dari broker.

## Data Model (schema `marketdata`)

```sql
candles(pair, interval, open_time, open, high, low, close,
        volume_base, volume_quote, trade_count,
        PRIMARY KEY(pair, interval, open_time))
-- partisi per bulan; trades TIDAK diduplikasi di sini (punya settlement)
```

Valkey: cache respons REST panas (ticker all-pairs, depth snapshot) TTL 1 detik.

## API (publik, rate-limited per IP)
```
GET /v1/market/ticker?pair=            GET /v1/market/tickers
GET /v1/market/candles?pair=&interval=&from=&to=&limit=
GET /v1/market/depth?pair=&limit=25|100|500
GET /v1/market/trades?pair=&limit=
WS  /v1/stream
```
Format & penamaan field meniru konvensi umum (Binance-like) → integrasi bot & chart library (TradingView UDF adapter, fase lanjut) gampang.

## Edge Cases
- WS client subscribe pair yang HALT/DELIST → tetap boleh (lihat book beku), ticker diberi flag status.
- Restart app → candle berjalan & ticker di-rebuild dari trades/candles; depth dari snapshot matching → seq nyambung.
- Duplikat event saat replay → aggregator idempotent by trade_id/seq (skip yang sudah diproses).
- Jam candle: open_time = floor(executed_at, interval) UTC. Trade tepat di batas → masuk candle baru.

## Testing
- Rebuild test: candle hasil streaming == hasil rebuild batch dari trades (harus identik).
- Depth consistency: apply semua delta dari snapshot seq N → sama dengan snapshot seq M.
- WS: slow consumer di-drop tanpa mengganggu klien lain; resume seq benar.
