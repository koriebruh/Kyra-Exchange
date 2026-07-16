# 05 — Matching Engine

> Inti teknis exchange. In-memory, deterministic, single-threaded per pair. Kecepatan & kebenaran di sini menentukan reputasi produk.

## Tujuan
Mencocokkan order beli & jual per pair dengan aturan **price-time priority**, menghasilkan trade, secara deterministik dan bisa di-replay.

## Prinsip Desain (non-negotiable)
1. **Single writer per pair:** satu thread memproses semua command (place/cancel) satu pair secara serial → tanpa lock, tanpa race. Multi-pair = paralel antar thread (satu thread bisa pegang beberapa pair via sharding `hash(pair) % N`).
2. **In-memory order book:** struktur data di heap. TIDAK ada query DB di hot path.
3. **Deterministic:** input sequence sama → output sама persis. Tidak ada `System.currentTimeMillis()` / random di logic — timestamp di-assign SEBELUM masuk queue (di sequencer).
4. **Event-sourced:** semua yang terjadi = event ke log append-only. State book bisa dibangun ulang dari (snapshot + replay).

## Struktur Data

```
OrderBook per pair:
  bids: TreeMap<Price(desc), PriceLevel>      asks: TreeMap<Price(asc), PriceLevel>
  PriceLevel: ArrayDeque<BookOrder> (FIFO), totalQty
  ordersById: HashMap<OrderId, BookOrder>     // O(1) cancel lookup
BookOrder: {orderId, userId, price, remainingQty, seq}
```
Harga & qty di dalam engine = `long` (scaled integer: harga dalam satuan tick, qty dalam satuan step) → aritmetika eksak & cepat, konversi BigDecimal↔long di boundary.

## Alur Matching (limit order masuk)

```
1. Ambil command dari queue pair (sequencer sudah kasih seq & timestamp)
2. Cek self-trade guard (userId sama dengan resting order teratas → cancel bagian itu)
3. Cross check: BUY price >= best ask → match loop:
     - lawan = best ask level, FIFO order pertama
     - trade_qty = min(remaining incoming, remaining resting)
     - trade_price = HARGA RESTING ORDER (price improvement ke taker)
     - emit TradeExecuted {taker, maker, price, qty, seq}
     - kurangi qty kedua sisi; resting habis → keluarkan dari book
     - ulangi sampai incoming habis / tidak cross / (IOC) berhenti
4. Sisa incoming (GTC limit) → masuk book sebagai resting → emit OrderRested
5. FOK: pre-check total qty tersedia pada harga limit; kurang → EXPIRED tanpa eksekusi apapun
6. IOC: match sebisanya, sisa → EXPIRED
```

Market order: sama, tapi tanpa rest — habiskan terhadap book sampai qty/quote_qty terpenuhi atau slippage guard berhenti.

## Persistence & Recovery

### Event log
- Setiap command yang MENGUBAH state → event tersimpan ke tabel `matching_events` (append-only) **sebelum efeknya diakui keluar** (write-ahead). Batch insert (per N event / per T ms) untuk throughput; ACK trade ke settlement hanya setelah event ter-flush.
- Event: `{seq BIGSERIAL per pair, pair, type, payload JSONB, at}`

### Snapshot
- Per pair, tiap M event / T menit: serialisasi seluruh book → `matching_snapshots(pair, upto_seq, state BYTEA/JSONB)`.
- **Retensi: simpan 3 snapshot terakhir per pair** + checksum diverifikasi saat tulis & baca; snapshot korup → fallback ke sebelumnya (replay lebih panjang, tetap benar). `matching_events` tidak pernah DELETE — partisi bulanan, > 6 bulan diarsip ke object storage (18 §A2).

### Recovery (start/crash)
```
1. Load snapshot terakhir per pair
2. Replay matching_events dengan seq > snapshot.upto_seq
3. Rekonsiliasi dengan modul order: order ACCEPTED yang hilang → resubmit;
   trade yang ter-emit tapi belum settled → settlement mengejar (idempotent by trade_id)
4. Baru buka intake
```
- Replay deterministic = hasil book identik. Ini DITES di CI (golden test).

## Interface

```java
// masuk (dari order module, via ring buffer / ArrayBlockingQueue per shard)
MatchingCommand: PlaceOrder | CancelOrder | HaltPair | ResumePair

// keluar (event, konsumen: settlement, marketdata, order)
TradeExecuted {trade_id ULID, pair, taker_order, maker_order, taker_user,
               maker_user, price, qty, taker_side, seq, at}
OrderRested / OrderCanceled / OrderExpired {order_id, remaining, reason}
BookDelta {pair, side, price, new_level_qty, seq}   // untuk depth stream
```

## Performa (target fase awal)
- Throughput: ≥ 10.000 order/detik per pair (jauh di atas kebutuhan awal; single-thread TreeMap sanggup).
- Latency intake→trade event: p99 < 1 ms in-process (di luar persist batch).
- Backpressure: queue penuh → reject order baru dengan `SYSTEM_BUSY` (jangan buffer tak terbatas).

## Edge Cases
- **Halt di tengah sesi:** `HaltPair` → berhenti match, book dibekukan (cancel tetap boleh — cancel-only mode).
- **Cancel order yang sedang dieksekusi di loop yang sama:** command serial → tidak mungkin; cancel setelah fill penuh → return `NOT_FOUND` (sudah terminal).
- **Presisi:** semua long-scaled; overflow guard saat qty × price (gunakan Math.multiplyHigh check / batas max_qty dari market).
- **Crash antara flush event & ACK:** settlement idempotent by trade_id → duplikat aman; event hilang tidak mungkin karena write-ahead.
- **Pair baru saat runtime:** `PairStatusChanged(ACTIVE)` → alokasikan book + thread shard tanpa restart.

## Testing (paling ketat bersama ledger)
- **Golden/replay test:** file sequence order (ribuan) → hasil trades + book akhir harus byte-identical lintas run & lintas versi (regresi = breaking change sadar).
- **Property test (jqwik):** invariant — (1) tidak ada trade dengan harga di luar cross, (2) price-time priority tidak pernah dilanggar (order lebih dulu di harga sama selalu fill duluan), (3) sum filled ≤ qty order, (4) book konsisten (bid tertinggi < ask terendah setelah match selesai).
- **Crash test:** kill -9 di tengah load → restart → replay → book identik dengan kontrol.
- **Benchmark:** JMH untuk hot path, CI menolak regresi > 20%.
