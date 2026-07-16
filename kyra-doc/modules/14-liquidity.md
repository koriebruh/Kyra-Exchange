# 14 — Liquidity

> Exchange tanpa likuiditas = kota hantu. User datang, lihat spread 5%, order book kosong, pergi selamanya. Wajib beres SEBELUM public launch (fase 5).

## Tujuan
Menjamin order book pair utama selalu punya spread & depth yang layak, via market maker bot internal dan/atau partner eksternal.

## Strategi (bertahap)

### Tahap 1 — Internal MM bot (fase 5)
Bot milik exchange yang quoting dua sisi mengikuti harga referensi eksternal.

- **Harga referensi:** median dari ≥2 sumber (mis. Binance, agregator) per pair, staleness guard (> 5s → lebarkan spread / tarik quote).
- **Quoting:** N level per sisi (mis. 5 bid + 5 ask), spread dasar per pair (mis. 0.2%), ukuran per level menurun menjauhi mid; refresh saat referensi bergerak > threshold atau quote termakan.
- **Inventory management:** target komposisi (mis. 50:50 nilai base:quote). Inventory miring → skew quotes (geser mid ke arah yang mengurangi inventory). Batas keras inventory per aset → berhenti quoting sisi itu + alarm (JANGAN akumulasi tak terbatas).
- **Hedging (opsional lanjut):** posisi netto di-hedge di exchange eksternal → butuh akun + API di sana; awal cukup inventory limit ketat.
- **Risk controls bot:** kill-switch, max loss harian (bot berhenti sendiri), reject quoting saat referensi anomali (flash crash sumber tunggal).

### Tahap 2 — Partner MM eksternal
- Program fee MM (tier khusus 0%/rebate) + API stabil + (kadang) pinjaman inventory. Kontrak: kewajiban uptime quoting, max spread, min depth.
- Teknis dari sisi kita: API key MM dengan rate limit tinggi + endpoint bulk (place/cancel batch) + WS latensi rendah — kebutuhan modul order/marketdata, dicatat sebagai requirement.

## Arsitektur Bot

```
liquidity module (dalam monolith, tapi terisolasi ketat):
  price-feed poller (WS/REST eksternal) → reference price store (in-memory)
  strategy loop per pair → target quotes → diff dengan quotes aktif
  → place/cancel via OrderApi INTERNAL (akun khusus kyra:mm)
```
- Bot memakai jalur order yang SAMA dengan user (fairness + test alur sendiri), akun `kyra:mm:*` dengan fee 0 (override).
- Dana bot = ledger biasa (transparan, terekonsiliasi). Profit/loss MM terlihat di laporan per akun.
- Konfigurasi per pair hot-reload: spread, depth, level, inventory limit.

### Self-trade
Bot vs user biasa: normal. Bot vs bot sendiri (multi-strategy): self-trade guard di matching (05) mencegah — kebijakan CANCEL_NEWEST cukup.

## Metrics (definisi "likuiditas sehat" — dipantau & dialarm)
- Spread p95 per pair < X% (target awal: 0.3% BTC/ETH)
- Depth ±1% dari mid ≥ Y USDT
- Uptime quoting ≥ 99% (durasi book kosong = insiden)
- Inventory drift & PnL bot harian

## Data Model (schema `liquidity`)
```sql
mm_configs(pair PK, enabled, base_spread, levels INT, level_size JSONB,
           inventory_target, inventory_limit, updated_by, updated_at)
reference_prices(pair, source, price, at)     -- ring buffer di memory; sampel ke DB utk audit
mm_pnl_daily(date, pair, realized NUMERIC, inventory_value NUMERIC)
```

## Edge Cases
- Harga referensi flash-crash di satu sumber → median + outlier rejection menahan; semua sumber gila → tarik semua quote (lebih baik book tipis daripada quote salah).
- Latency kita > pergerakan pasar → bot termakan di harga basi (adverse selection) → spread dasar harus menutup biaya ini; ukur "markout" (harga 10s setelah fill) untuk kalibrasi.
- Maintenance exchange referensi → failover ke sumber lain otomatis.
- Bot crash → open orders bot TIDAK otomatis dicancel oleh sistem? HARUS: startup bot = cancel-all akun MM dulu, baru quoting ulang (state bersih).

## Testing
- Simulasi: replay pergerakan harga referensi historis → bot menjaga spread/inventory dalam batas, PnL masuk akal.
- Kill-switch & max-loss: terpicu tepat.
- Failover sumber harga: satu sumber mati/stale → tetap quoting benar.
