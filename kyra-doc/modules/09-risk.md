# 09 — Risk

> Rem darurat sistem. Fase spot: limit & anomali. Fase derivatives: margin, mark price, liquidation — bagian paling berbahaya di seluruh exchange.

## Tujuan
Melindungi exchange & user dari kerugian: limit transaksi, deteksi anomali, dan (fase 6) mesin margin + likuidasi untuk derivatives.

## Bagian A — Spot (Fase 4)

### F1. Limit engine
Dievaluasi di order intake & withdraw request:
- Limit withdraw harian/bulanan per user, bertingkat sesuai level KYC (dari compliance).
- Max open orders per user per pair; max notional order tunggal.
- Velocity: order/menit per user (anti-spam & fat-finger bot), withdraw request/jam.
- Limit global per aset: total withdraw/jam seluruh sistem (circuit breaker kebocoran — kalau ada exploit, kerugian terbatasi).

### F2. Anomaly & circuit breakers
- Price band per pair: order limit dengan harga > X% dari last price → reject (fat finger).
- Trading halt otomatis: pergerakan > Y% dalam Z menit → HALT pair + alarm (kebijakan diumumkan publik).
- Withdraw anomali: user tidur 6 bulan tiba-tiba withdraw semua + IP baru → skor naik → paksa review manual walau di bawah threshold.
- Skor risiko sederhana dulu (rule-based, transparan), ML nanti kalau ada data.

### F3. Admin controls
- Freeze per user (trading / withdraw / semua), per aset, per pair, global kill-switch withdraw.
- Semua aksi = audit log + alasan wajib.

## Bagian B — Derivatives (Fase 6) — desain awal

> Ditulis sekarang supaya arsitektur account/matching menyiapkan slot; detail difinalkan saat fase 6 dimulai.

### Margin account
- Akun terpisah di ledger: `user:{id}:{asset}:margin` (collateral). Cross margin dulu (satu pool collateral untuk semua posisi), isolated menyusul.
- Transfer spot ↔ margin = journal `TRANSFER` biasa.

### Mark price & index price
- Index price = median harga spot dari ≥3 sumber eksternal (agregator + exchange besar) dengan outlier rejection & staleness guard (sumber telat > 30s dibuang).
- Mark price = index + basis EMA (anti manipulasi wick di satu exchange).
- **Likuidasi & unrealized PnL SELALU pakai mark price, bukan last trade internal.** Ini pelajaran berdarah industri — last price internal mudah dimanipulasi di likuiditas tipis.

### Funding rate (perpetual)
- Interval 8 jam: `funding = clamp(premium_index + interest, ±cap)`.
- Settle: journal antar pemegang posisi long ↔ short. Posisi dihitung pada snapshot waktu funding.

### Liquidation engine
- Loop evaluasi: `margin_ratio = equity / maintenance_margin` per akun (event-driven dari perubahan mark price + posisi).
- `margin_ratio < 1` → likuidasi bertahap: (1) cancel open orders, (2) reduce posisi via market order terbatas slippage, (3) sisa loss → insurance fund, (4) fund habis → ADL (auto-deleverage lawan profit tertinggi) — jalan terakhir.
- Insurance fund = akun ledger `kyra:insurance:{asset}`, diisi dari fee likuidasi.
- Prioritas: engine likuidasi TIDAK boleh antri di belakang order retail → jalur command khusus di matching (priority queue).

## Interface

```java
RiskApi.checkOrder(user, pair, side, notional) → ALLOW | REJECT(reason)
RiskApi.checkWithdraw(user, asset, amount, toAddress) → ALLOW | REVIEW | REJECT
RiskApi.reportEvent(...)   // sinyal dari modul lain untuk skor anomali
```
Keputusan risk = cepat (cache limit di memory, hitung ringan); target < 1ms di jalur order.

## Data Model (schema `risk`)

```sql
limits(scope ENUM(USER,KYC_LEVEL,GLOBAL,PAIR), scope_id, limit_type, value, updated_by)
counters(key, window_start, count)         -- di Valkey (TTL), bukan Postgres
risk_flags(id, user_id, type, score, detail JSONB, status, created_at)
halt_log(id, scope, reason, triggered_by ENUM(AUTO,ADMIN), at, resumed_at)
```

## Domain Events
- `RiskLimitBreached {user, type}` → admin dashboard, notification
- `PairHalted {pair, reason, AUTO}` → market (status), publik (transparansi)
- (fase 6) `PositionLiquidated {user, pair, qty, loss}` → notification, audit

## Edge Cases
- Risk service error/timeout → **fail-closed untuk withdraw** (tolak), **fail-open terukur untuk order** (izinkan dengan limit konservatif default) — trading mati total lebih buruk dari limit longgar sesaat; withdraw sebaliknya.
- Limit diubah saat order in-flight → keputusan pakai limit saat evaluasi; tidak retroaktif.
- Jangan pernah likuidasi karena data harga stale → staleness guard menghentikan likuidasi + alarm (freeze lebih baik daripada likuidasi salah).

## Testing
- Unit: semua rule limit boundary (tepat di limit = allow/reject sesuai definisi inklusif yang didokumentasikan).
- Simulasi (fase 6): skenario crash -30% dalam 1 jam pada portofolio sintetis → insurance fund & ADL bekerja sesuai desain; TIDAK ada saldo user negatif tersisa.
- Chaos: risk service dimatikan → order pakai limit default, withdraw tertolak (fail-closed terverifikasi).
