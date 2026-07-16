# 15 — Tax (Withholding Pajak Indonesia)

> Modul yang hampir semua orang lupa sampai ditagih DJP. Exchange Indonesia = pemungut pajak per transaksi. WAJIB live bersamaan real-money trading (fase 4). Bukan fee, bukan revenue — uang titipan negara.

## Konteks Regulasi (⚠️ verifikasi rate terbaru dengan konsultan pajak sebelum implementasi!)
- Dasar: PMK 68/2022 tentang PPN & PPh atas transaksi aset kripto (dan perubahannya — cek revisi terbaru; rezim pajak kripto Indonesia beberapa kali berubah sejak 2022, termasuk penyesuaian saat pengawasan pindah ke OJK).
- Pola umum rezimnya (angka WAJIB diverifikasi):
  - **PPh final** dipungut dari **penjual** per transaksi (historis: 0.1% dari nilai transaksi untuk exchange terdaftar).
  - **PPN** dipungut dari **pembeli** per transaksi (historis: 0.11% via exchange terdaftar; ada wacana/perubahan penghapusan PPN saat kripto direklasifikasi ke aset keuangan — CEK STATUS TERKINI).
  - Exchange TIDAK terdaftar → rate dobel. Satu lagi alasan lisensi wajib beres.
- Kewajiban exchange: **pungut** per trade → **setor** ke kas negara (bulanan) → **lapor** (SPT Masa / e-Bupot unifikasi) → terbitkan bukti potong.

## Desain Mekanisme

### Pemungutan per trade (integrasi dengan settlement 06)
Journal `TRADE_SETTLEMENT` diperluas — entries tambahan:

```
seller side: PPh = pph_rate × nilai_transaksi (quote)
  entry: seller:USDT:...        -pph_amount
  entry: kyra:tax:pph:USDT      +pph_amount
buyer side:  PPN = ppn_rate × nilai_transaksi
  entry: buyer:...              -ppn_amount
  entry: kyra:tax:ppn:USDT      +ppn_amount
```
- Sama seperti fee: rate **dibekukan per order saat placement** (deterministik, replay-safe), pembulatan ke atas pada scale aset, dipotong dari aset yang mengalir (pajak dalam crypto → konversi nilai IDR pakai kurs referensi pada saat trade, disimpan).
- Akun `kyra:tax:*` = liabilities (bukan revenue). Laporan keuangan memisahkan jelas dari `kyra:fee:*`.
- Trade antar pair non-IDR (BTC-USDT): nilai transaksi dikonversi ke IDR pakai **kurs referensi tercatat saat trade** (sumber: kurs pajak KMK / referensi terdokumentasi) — simpan kurs+sumber di record trade untuk audit.

### Penyetoran & pelaporan (bulanan)
- Job rekap bulanan: total PPh & PPN terpungut per periode + konversi ke IDR (pajak disetor dalam IDR → proses ops: likuidasi crypto tax pool ke IDR — prosedur treasury manual di awal, terdokumentasi).
- Export data untuk SPT Masa / e-Bupot unifikasi + arsip bukti setor (NTPN) dilampirkan kembali ke sistem (jejak audit lengkap).
- Bukti potong PPh per user per periode: bisa digenerate dari data trade (kebutuhan user pelaporan SPT tahunan).

### Laporan untuk user
- Halaman pajak: total pajak terpotong per tahun + export CSV/PDF (nilai IDR) — mengurangi beban support saat musim SPT.

## Data Model (schema `tax`)

```sql
tax_rates(id, tax_type ENUM(PPH,PPN), rate NUMERIC, basis TEXT,
          valid_from, valid_to, legal_ref TEXT, created_by)
          -- rate historis tidak pernah dihapus; trade menunjuk rate yang berlaku
trade_taxes(trade_id PK FK, pph_amount, pph_asset, ppn_amount, ppn_asset,
            idr_rate NUMERIC, idr_rate_source TEXT, idr_value NUMERIC)
tax_periods(period PK,           -- '2026-07'
            status ENUM(OPEN,CLOSED,REMITTED,REPORTED),
            total_pph_idr, total_ppn_idr, remitted_at, ntpn TEXT,
            report_ref TEXT, closed_by)
```

## Interface
```java
TaxApi.ratesFor(pair) → {pph_rate, ppn_rate}     // dipanggil order intake, dibekukan di order
TaxApi.recordTradeTax(...)                        // dipanggil settlement, transaksi sama
```

## Edge Cases
- **Rate berubah di tengah bulan** → rate versioned `valid_from/to`; order lama pakai rate beku → dua rate dalam satu periode = normal, laporan per-rate.
- Trade sangat kecil → pajak dibulatkan bisa 0 pada scale aset → catat 0, akumulasi tidak dilakukan (ikuti aturan pembulatan resmi — tanya konsultan).
- Kurs referensi IDR tidak tersedia (sumber down) → pakai kurs terakhir yang valid + flag; JANGAN blok trading, tapi alarm (koreksi di rekap bila perlu, terdokumentasi).
- User minta refund pajak salah potong → hanya via adjustment ledger + approval + dokumentasi (kasus langka, biasanya arah koreksi ke DJP bukan ke user).
- Reklasifikasi regulasi (mis. PPN dihapus) → set rate 0 dengan `valid_from`, JANGAN hapus kode — sejarah harus tetap bisa direplay.

## Testing
- Rekonsiliasi: Σ trade_taxes per periode == saldo akun `kyra:tax:*` (ledger = kebenaran).
- Rate versioning: trade di boundary pergantian rate pakai rate beku order masing-masing.
- Deterministik: replay settlement → angka pajak identik.

## TODO Sebelum Implementasi (fase 4, mulai lebih awal)
- [ ] Konsultan pajak: konfirmasi rate & basis terkini (PPh/PPN), kewajiban PFAK vs non-PFAK, mekanisme setor-lapor, kurs konversi resmi
- [ ] Tentukan prosedur treasury likuidasi tax pool crypto → IDR
- [ ] Daftar e-Bupot / kanal pelaporan elektronik DJP
