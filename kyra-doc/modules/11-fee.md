# 11 — Fee

> Sumber revenue utama. Kecil kodenya, besar dampaknya — salah hitung fee = rugi diam-diam atau user protes massal.

## Tujuan
Menentukan fee rate yang berlaku untuk setiap trade & withdraw, mengelola tier, dan melaporkan pendapatan.

## Model Fee

### Trading fee: maker/taker
- **Maker** (order resting, menambah likuiditas): rate lebih rendah — insentif isi order book.
- **Taker** (order yang mengeksekusi): rate lebih tinggi.
- Default awal (contoh, tentukan final via analisis kompetitor): maker 0.10%, taker 0.15%.
- Fee dipotong dari **aset yang diterima** (buyer bayar fee dalam base, seller dalam quote) — lihat settlement 06.

### Tier volume (fase 5)
- Volume trading 30 hari rolling (dalam quote referensi, USDT) → tier:

| Tier | Volume 30d | Maker | Taker |
|---|---|---|---|
| VIP0 | < 50k | 0.10% | 0.15% |
| VIP1 | ≥ 50k | 0.08% | 0.12% |
| VIP2 | ≥ 500k | 0.06% | 0.10% |
| VIP3 | ≥ 5M | 0.02% | 0.07% |
| MM | khusus | 0.00% | 0.05% |

- Tier dihitung job harian (00:00 UTC) dari tabel trades → berlaku 24 jam penuh (tidak berubah intra-day → deterministik).
- Override per user (deal market maker, promo) dengan masa berlaku + approval admin.

### Withdraw fee
- Flat per aset (menutup network fee + margin), dimiliki modul wallet, tapi rate-nya dikelola di sini (satu tempat semua fee).

### Fee ≠ Pajak
Pajak (PPh/PPN per trade — modul 15-tax) BUKAN fee: fee = revenue kita, pajak = titipan negara. Akun ledger terpisah (`kyra:fee:*` vs `kyra:tax:*`), laporan terpisah, jangan pernah dicampur.

## Kontrak dengan Settlement (kritis)
- Rate **dibekukan saat order ditempatkan** (`maker_rate`, `taker_rate` disimpan di order) → settle deterministik & replay-safe; perubahan rate tidak retroaktif ke open orders.
- `FeeApi.ratesFor(userId, pair)` dipanggil sekali di order intake; hasil ikut order sampai selesai.

## Fitur
- F1: Rate resolution (default → tier → override), cache in-memory, invalidate via event.
- F2: Konfigurasi fee (admin, versioned, audit — nilai lama→baru, siapa, kapan; perubahan diumumkan ke user minimal H-3).
- F3: Revenue report: pendapatan fee per aset/pair/hari (agregasi dari ledger `kyra:fee:*` — bukan hitung ulang sendiri, baca dari sumber kebenaran).
- F4: (nanti) Referral/rebate: potongan fee dibagi ke referrer — journal terpisah, bukan mengubah logika settle.

## Data Model (schema `fee`)

```sql
fee_schedules(id, pair_scope TEXT,       -- '*' atau pair spesifik
              tier SMALLINT, maker_rate NUMERIC, taker_rate NUMERIC,
              valid_from, valid_to, created_by)
user_fee_overrides(user_id, maker_rate, taker_rate, reason,
                   valid_from, valid_to, approved_by)
user_tiers(user_id PK, tier, volume_30d NUMERIC, computed_at)
withdraw_fees(asset PK, amount NUMERIC, updated_by, updated_at)
```

## Edge Cases
- Order hidup melewati perubahan tier user → tetap pakai rate beku saat place (dokumentasikan ke user).
- Rate 0% (promo/MM) → journal fee entry amount 0 tetap dibuat? TIDAK — skip entry nol, tapi kolom fee di trades tetap 0 (laporan konsisten).
- Pembulatan: rate × amount dibulatkan ke atas pada scale aset (aturan tunggal, di settlement).

## Testing
- Rate resolution: kombinasi default/tier/override/expired-override.
- Konsistensi laporan: revenue report == Σ ledger kyra:fee (test rekonsiliasi).
- Tier job: volume tepat di boundary (≥ inklusif).
