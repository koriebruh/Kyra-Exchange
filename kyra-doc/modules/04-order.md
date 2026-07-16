# 04 — Order

> Pintu masuk semua order. Validasi, hold dana, state machine, dan riwayat. Matching engine hanya menerima order yang sudah lolos dari sini.

## Tujuan
Menerima order dari user (REST/WS), memvalidasi, menahan dana, meneruskan ke matching engine, dan melacak lifecycle order sampai selesai.

## Order Types (MVP → lanjut)
- **MVP:** `LIMIT`, `MARKET`
- **Time-in-force:** `GTC` (default), `IOC` (isi sebisanya, sisanya cancel), `FOK` (isi penuh atau batal semua)
- **Fase lanjut:** `STOP_LIMIT`, `STOP_MARKET`, `POST_ONLY` (maker-only, reject kalau bakal taker), `OCO` (one-cancels-other), iceberg (tampilkan sebagian qty), trailing stop (derivatives)

### Arsitektur stop/trigger order (desain awal, implement fase lanjut)
Stop order TIDAK masuk order book — dia menunggu di **trigger engine**:
```
trigger engine (per pair, in-process):
  simpan stop orders di 2 sorted structure (trigger naik / turun)
  subscribe last price dari matching → harga menyentuh trigger
  → konversi jadi limit/market order biasa → jalur intake normal
    (validasi ulang + hold dana SAAT TRIGGER, bukan saat place)
```
- Keputusan desain: dana TIDAK di-hold saat stop dipasang (standar CEX; hold saat trigger; gagal hold saat trigger = order reject + notifikasi).
- Trigger source: last trade price (default). Mark price untuk derivatives (fase 6).
- Persist + recovery sama pola matching: event log + rebuild saat restart.
- OCO = dua order ber-link; satu terpicu/filled → pasangannya auto-cancel (atomik di trigger engine).

## State Machine

```
            ┌──────────► REJECTED (validasi/dana/risk gagal)
            │
 NEW ───► ACCEPTED ───► OPEN ──┬──► PARTIALLY_FILLED ──┬──► FILLED
 (intake)  (dana held) (di book)│                       │
            │                   └──────► CANCELED ◄─────┘
            └► EXPIRED (IOC/FOK tidak terpenuhi)
```
- Transisi hanya maju (tidak ada un-fill). Setiap transisi tercatat dengan timestamp + penyebab.
- `CANCELED`/`FILLED`/`REJECTED`/`EXPIRED` = terminal → release sisa hold.

## Alur Place Order (kritikal — urutan penting)

```
1. Parse + auth (API key scope 'trade' / JWT)
2. Idempotency check: client_order_id unik per user → duplikat = return order lama
3. MarketApi.validate(pair, price, qty)          -- tick/lot/notional/status
4. RiskApi.checkOrder(user, pair, notional)      -- limit user, max open orders
5. Hitung dana yang di-hold:
   - BUY LIMIT : qty × price (quote asset) + estimasi fee
   - SELL LIMIT: qty (base asset)
   - BUY MARKET: pakai 'quote_qty' user (spend amount), bukan qty
6. AccountApi.hold(...)                          -- transaksi DB; gagal = REJECTED
7. Persist order (status ACCEPTED) — dalam transaksi yang sama dengan hold
8. Submit ke matching queue (in-memory). ACK ke user: order_id + status
9. Status selanjutnya datang async via event dari matching/settlement
```

**Aturan:** dana di-hold DULU baru masuk matching. Tidak pernah ada order di book tanpa dana tertahan.

### Alur Cancel
```
1. Cek kepemilikan + status non-terminal
2. Kirim CancelCommand ke matching queue (pair yang sama → serialized dengan match)
3. Matching konfirmasi removed → event OrderCanceled → release sisa hold
```
Cancel bersifat async: response = `CANCEL_PENDING`, konfirmasi via WS/polling. Race cancel-vs-fill wajar terjadi → yang menang yang duluan diproses thread matching.

### Market Order — perlindungan
- BUY market pakai `quote_qty` (mau belanja berapa USDT), SELL market pakai `qty` base.
- Slippage guard: eksekusi berhenti jika harga bergerak > X% dari harga referensi saat intake (X per pair, default 5%) → sisa jadi EXPIRED.
- Order book kosong → reject `NO_LIQUIDITY`.

## Data Model (schema `orders`)

```sql
orders(id ULID PK, user_id, pair, side ENUM(BUY,SELL),
       type ENUM(LIMIT,MARKET,STOP_LIMIT,...), tif ENUM(GTC,IOC,FOK),
       price NUMERIC NULL, qty NUMERIC, quote_qty NUMERIC NULL,
       filled_qty NUMERIC DEFAULT 0, avg_fill_price NUMERIC,
       status, client_order_id, hold_journal_ref,
       created_at, updated_at,
       UNIQUE(user_id, client_order_id))
order_transitions(id, order_id FK, from_status, to_status, cause, at)
-- open orders per user index: (user_id, status) WHERE status IN (OPEN, PARTIALLY_FILLED)
```

Arsip: order terminal > 90 hari dipindah ke tabel arsip (partisi bulanan) — order aktif tetap kecil & cepat.

## Domain Events
- Dikonsumsi DARI matching: `OrderAccepted(book)`, `OrderFilled {fill_qty, price}`, `OrderCanceled`, `OrderExpired`
- Dipublish: `OrderPlaced`, `OrderStatusChanged {order_id, user_id, status}` → WS private stream, notification

## API (prefix `/v1/orders`, auth wajib)
```
POST   /                 -- place (body: pair, side, type, tif, price?, qty?, quote_qty?, client_order_id)
DELETE /{orderId}        -- cancel
DELETE /?pair=BTC-USDT   -- cancel all (per pair / semua)
GET    /open             -- open orders (cursor pagination)
GET    /history          -- riwayat + filter pair/status/waktu
GET    /{orderId}        -- detail + fills
```
Idempotency: `client_order_id` wajib dari API key clients; auto-generate untuk UI.

Fase 5 (kebutuhan market maker & bot): `POST /batch` (place/cancel banyak order satu request, all-or-nothing per item), order entry via WebSocket (latency lebih rendah dari REST), `GET /rate-limits` (sisa kuota).

## Edge Cases
- Partial fill lalu cancel → release hold sisa persis (hold awal − terpakai − fee terpakai). Selisih pembulatan fee = kembalikan ke user (jangan simpan diam-diam).
- IOC/FOK: keputusan di matching engine (atomik terhadap state book saat itu), bukan di order intake.
- Hold sukses tapi crash sebelum submit ke matching → recovery scan: order ACCEPTED yang tidak ada di book → resubmit (order_id sama → matching idempotent).
- Self-trade (order user match dengan ordernya sendiri): kebijakan `CANCEL_NEWEST` (order masuk dibatalkan bagian yang akan self-match). Cegah wash trading.
- Max open orders per user per pair (dari market config) → reject di intake.

## Testing
- Unit: state machine (semua transisi ilegal ditolak), kalkulasi hold per kombinasi side/type.
- Integration: place→partial fill→cancel→release, angka hold/release balance persis (assert via ledger).
- Race test: cancel bersamaan dengan fill → hasil konsisten di kedua kemungkinan urutan.
