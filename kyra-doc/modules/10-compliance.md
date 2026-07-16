# 10 — Compliance (KYC / AML)

> Bukan fitur teknis favorit, tapi penentu hidup-mati perusahaan. Tanpa ini: tidak ada lisensi, tidak ada bank, bisa pidana. Wajib live SEBELUM real money (fase 4).

## Tujuan
Memenuhi kewajiban regulasi: kenali user (KYC), pantau transaksi mencurigakan (AML/TM), screening sanksi, dan pelaporan ke regulator.

## Konteks Regulasi (Indonesia — verifikasi ulang dengan konsultan hukum!)
- Sejak Januari 2025 pengawasan aset kripto pindah Bappebti → **OJK** (POJK 27/2024). Exchange = PFAK (Pedagang Fisik Aset Kripto), wajib izin.
- Kewajiban umum: KYC (CDD/EDD), pelaporan transaksi mencurigakan (LTKM ke **PPATK**), transaksi tunai besar (LTKT), penerapan APU-PPT, travel rule antar VASP.
- **Non-kode tapi blocking:** badan hukum PT, permodalan minimum, direksi fit-and-proper, kerjasama kustodian & bursa/lembaga kliring sesuai aturan. Mulai urus PARALEL dengan fase 2-3.

## Fitur Detail

### F1. KYC onboarding (pakai provider eksternal)
- Provider ID verification (mis. Verihubs/Privy/Sumsub — evaluasi biaya & coverage KTP+liveness) di balik interface `KycProvider`.
- Level akun:
  - **L0** unverified: hanya lihat-lihat, tidak bisa deposit/trade/withdraw
  - **L1** KYC dasar (KTP + liveness): trade + limit withdraw rendah
  - **L2** EDD (bukti alamat, sumber dana — untuk nominal besar): limit tinggi
- Flow: user submit → provider verifikasi async → webhook hasil → APPROVED/REJECTED/MANUAL_REVIEW (antrian admin).
- Re-KYC: dokumen expired / trigger risiko → paksa ulang, turunkan level sementara.
- Data pribadi: enkripsi at-rest, akses berbasis role + audit setiap view (UU PDP!). Retensi sesuai regulasi (≥5 tahun setelah tutup akun).

- **KYB (fase lanjut):** akun korporat/institusi — verifikasi badan hukum, beneficial owner (pemilik ≥25%), dokumen legalitas. Skema data terpisah dari KYC individu; jangan paksakan ke tabel yang sama.

### F2. Sanction & address screening
- Nama user vs daftar sanksi (UN, OFAC, DTTOT lokal) + **daftar PEP** (politically exposed persons → wajib EDD) saat onboarding + re-screening berkala (daftar berubah).
- Address screening (deposit source & withdraw destination) via provider chain-analytics (Chainalysis/TRM/Elliptic — atau alternatif terjangkau di awal) di balik interface `AddressScreener`:
  - Deposit dari address tercemar (mixer, darknet, hack) di atas skor threshold → tahan dana (HELD) + case.
  - Withdraw ke address sanctioned → tolak + case + pertimbangan lapor.

### F3. Transaction monitoring (TM)
- Rule engine berjalan async atas event (deposit, withdraw, trade):
  - structuring (banyak transaksi kecil di bawah threshold)
  - pass-through cepat (deposit → withdraw < 1 jam tanpa trading)
  - volume tidak wajar vs profil & level KYC
  - pola wash trading antar akun terkait (shared device/IP dari identity)
- Rule kena → case di antrian compliance officer, BUKAN auto-block semua (kecuali rule critical). Manusia memutuskan: clear / freeze / lapor LTKM.

### F3b. Market surveillance (integritas pasar — beda dari AML)
Deteksi manipulasi pasar, kewajiban exchange ke regulator & kesehatan produk sendiri:
- **Spoofing/layering:** pola place-cancel besar berulang tanpa niat eksekusi (rasio cancel ekstrem + order jauh dari mid yang ditarik saat mendekat).
- **Wash trading:** akun terkait (shared device/IP/funding source) saling trade — self-trade guard di matching mencegah akun sama, surveillance menangkap akun BERBEDA yang terkait.
- **Pump & dump / marking the close:** lonjakan volume+harga terkoordinasi.
- Output = case ke antrian compliance (pola sama dengan TM); sanksi: peringatan → pembatasan → penutupan + lapor.

### F4. Case management & pelaporan
- Case: subjek, trigger, bukti (snapshot data), keputusan, siapa, kapan — immutable trail.
- Export laporan format PPATK (goAML). Deadline lapor LTKM itu ketat → alarm internal untuk case yang mendekati batas waktu.
- **Pelaporan rezim PFAK/OJK:** laporan berkala transaksi/aktivitas ke OJK + integrasi pelaporan transaksi ke bursa kripto (CFX) & kliring (KKI) sesuai ketentuan — format & kanal dikonfirmasi saat proses lisensi. Desain: exporter terpisah per tujuan regulator, baca dari tabel trades/ledger (sumber kebenaran), tidak menghitung ulang.

### F5. Travel rule (fase lanjut, sebelum volume besar)
- Pertukaran info originator/beneficiary antar VASP untuk transfer di atas threshold. Implementasi via protokol/jaringan travel rule yang dipakai regional — riset saat fase 5.

## Data Model (schema `compliance`)

```sql
kyc_profiles(user_id PK, level SMALLINT, status, provider_ref,
             full_name_enc, id_number_enc, dob_enc, address_enc,  -- terenkripsi
             verified_at, expires_at)
kyc_submissions(id, user_id, level_requested, provider, provider_ref,
                status, result JSONB, submitted_at, decided_at, decided_by)
screening_results(id, subject_type ENUM(USER,ADDRESS), subject, provider,
                  score, matches JSONB, screened_at)
cases(id, type, subject_user_id, trigger, status ENUM(OPEN,INVESTIGATING,
      CLEARED,REPORTED,CLOSED), assigned_to, opened_at, closed_at)
case_events(id, case_id, actor, action, note, evidence JSONB, at)  -- append-only
tm_rules(id, name, definition JSONB, severity, enabled)
```

## Interface (dipakai modul lain)
```java
ComplianceApi.kycLevel(userId) → level          // cache, dipakai risk utk limit
ComplianceApi.screenDepositAddress(addr, asset) → CLEAR | HOLD(score)
ComplianceApi.screenWithdrawAddress(addr, asset) → CLEAR | BLOCK
// dipanggil SINKRON di jalur deposit-credit & withdraw-approve
```

## Domain Events
- `KycLevelChanged {user, old, new}` → risk (limit), notification
- `CaseOpened {case_id, type, user}` → admin dashboard
- `UserFrozenByCompliance {user}` → identity (suspend), wallet (freeze)

## Edge Cases
- Provider KYC down → onboarding antri (degradasi anggun), BUKAN auto-approve.
- User L1 sudah punya saldo lalu screening ulang kena sanksi → freeze + case + panduan hukum; JANGAN diam-diam sita.
- Dua user, dokumen sama (duplikat identitas) → tolak yang baru + case.
- Permintaan hapus data (UU PDP) vs kewajiban retensi AML → retensi menang untuk data KYC; dokumentasikan dasar hukum di privacy policy.

## Testing
- Rule engine: fixture pola transaksi → rule yang tepat terpicu, tidak ada false-negative pada pola baku.
- Mock provider: semua status webhook (approve/reject/review/timeout).
- Access control: role non-compliance TIDAK bisa baca PII terdekripsi (test kegagalan akses).
