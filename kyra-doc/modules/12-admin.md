# 12 — Admin (Backoffice)

> Mata & tangan tim operasional. Tanpa backoffice yang baik, ops = query SQL manual di production — resep bencana.

## Tujuan
Antarmuka internal untuk operasional: approval, investigasi user, konfigurasi, monitoring bisnis. Modul ini TIDAK punya logic domain sendiri — dia memanggil API modul lain dengan permission admin.

## Prinsip
1. Admin tidak pernah menyentuh DB langsung. Semua aksi via API modul terkait → tervalidasi + ter-audit.
2. **4-eyes principle** untuk aksi berdampak dana besar (withdraw besar, adjustment ledger): satu mengajukan, satu lain menyetujui.
3. Akses admin: akun terpisah dari akun user biasa, 2FA wajib, IP allowlist (VPN kantor), session pendek.
4. SEMUA aksi admin ter-log immutable: siapa, apa, kapan, alasan, sebelum→sesudah.

## Fitur Detail

### F1. User management
- Cari user (email/id), lihat profil: status, KYC level, saldo, riwayat login/order/deposit/withdraw, session aktif, flag risiko.
- Aksi: suspend/unsuspend (dengan alasan), reset 2FA (prosedur verifikasi ketat via compliance), revoke sessions/API keys, ubah level KYC (via compliance case).
- PII masking default; buka penuh = permission khusus + tercatat (siapa melihat data siapa).

### F2. Withdraw approval queue
- Antrian withdraw PENDING_REVIEW: detail user, skor risk, riwayat, address tujuan (+ hasil screening).
- Approve / reject (alasan wajib). Nominal > threshold besar → butuh approver kedua (4-eyes).
- SLA timer: pending > X jam → eskalasi (user menunggu = tiket support).

### F3. Compliance case queue
- Case dari TM/screening (modul 10): investigasi, catat langkah, putuskan clear/freeze/report.

### F4. Market & fee ops
- CRUD aset/pair, ubah status (HALT/ACTIVE) dengan konfirmasi dampak ("ada N open order akan dibatalkan").
- Ubah fee schedule (versioned, jadwal berlaku, pengumuman).

### F5. Ledger ops
- Lihat journal/entries per akun (read-only).
- Manual adjustment (ganti rugi, koreksi insiden): ajukan → approver kedua → journal `ADJUSTMENT` + bukti. Satu-satunya jalan menyentuh saldo di luar alur normal.

### F6. Dashboard operasional
- Bisnis: volume 24h per pair, user aktif, deposit/withdraw flow, revenue fee, top holders.
- Sistem (link ke Grafana): settlement lag, matching queue depth, WS connections, error rate, hasil rekonsiliasi terakhir.
- Panel status: pair HALT, aset frozen, kill-switch aktif — semua yang "tidak normal" terlihat satu layar.

### F7. Kill switches (butuh role tertinggi + konfirmasi ketik-ulang)
- Freeze semua withdraw (global) — tombol insiden #1
- HALT semua pair / satu pair
- Disable pendaftaran / login (insiden keamanan)

## Implementasi
- Backend: REST endpoints `/admin/*` di kyra-app, dilindungi role + IP allowlist di reverse proxy JUGA (defense in depth).
- Frontend: SPA sederhana terpisah (bukan bagian monolith backend — repo/deploy sendiri, fase 4). Awal fase 2-3: cukup endpoint + HTTP client internal (curl/Postman collection) — jangan blokir progres karena UI.
- Setiap endpoint admin memanggil `XxxApi` modul domain — TIDAK ada SQL di modul admin.

## Data Model (schema `admin`)
```sql
admin_actions(id ULID, admin_id, action_type, target_type, target_id,
              payload JSONB, reason TEXT, second_approver, at)  -- append-only
approval_requests(id, type, payload JSONB, requested_by, status,
                  approver, decided_at, expires_at)
```

## Edge Cases
- Approver kedua = pengaju → DITOLAK sistem (4-eyes tidak bisa self-approve).
- Aksi admin race dengan aksi user (admin freeze saat user withdraw in-flight) → state machine domain yang menang; admin action antri konsisten.
- Admin offboarding: satu tombol revoke semua akses (session, role) — wajib < 1 menit.

## Testing
- Permission matrix test: setiap endpoint × setiap role → allow/deny sesuai tabel.
- 4-eyes flow: self-approve ditolak, expiry approval bekerja.
- Audit completeness: setiap endpoint mutasi menghasilkan admin_action record (test otomatis iterasi seluruh route).
