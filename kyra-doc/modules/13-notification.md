# 13 — Notification

> Saluran komunikasi ke user. Sederhana, tapi wajib andal — email withdraw yang tidak terkirim = panik + tiket support.

## Tujuan
Mengirim notifikasi transaksional (email, webhook, nanti push/telegram) berdasarkan domain events, dengan template terkelola dan delivery yang bisa diaudit.

## Prinsip
1. Konsumen event murni — modul lain TIDAK memanggil "kirim email" langsung; mereka publish domain event, notification yang memutuskan kirim apa.
2. At-least-once + idempotent: event sama dua kali → satu email (dedup by event_id).
3. Non-blocking: kegagalan kirim TIDAK pernah menggagalkan transaksi bisnis (async via outbox).

## Trigger (fase 4 minimal)

| Event | Channel | Catatan |
|---|---|---|
| UserRegistered | email | verifikasi email (via identity, prioritas tinggi) |
| UserLoggedIn (IP/device baru) | email | info + tombol "bukan saya" → freeze |
| TwoFactorEnabled/Disabled | email | |
| DepositDetected / DepositCredited | email + WS | |
| WithdrawRequested | email | dengan link CANCEL (window 24 jam utk address baru) |
| WithdrawApproved/Completed/Failed | email | |
| PasswordChanged / reset | email | + peringatan withdraw hold 24 jam |
| OrderFilled (opsional, per preferensi) | email/webhook | default OFF utk trader aktif |
| PairHalted / maintenance | broadcast | banner + email opsional |

## Fitur Detail

### F1. Template engine
- Template per (tipe, channel, bahasa — ID & EN dari awal), versioned di repo (bukan DB) → review via PR.
- Variabel typed per tipe notifikasi; render gagal = alarm, jangan kirim email kosong.
- **Anti-phishing code** (frasa pribadi user, dari modul identity) WAJIB dirender di semua email. Email tanpa frasa = user tahu itu phishing.

### F2. Email delivery
- Provider SMTP/API (SES/Postmark/Resend — pilih dari deliverability & harga) di balik interface `EmailSender`.
- Wajib: SPF, DKIM, DMARC di domain — email exchange = target phishing #1; DMARC reject policy.
- Kategori: TRANSACTIONAL (selalu kirim: security & dana) vs INFORMATIONAL (bisa unsubscribe).
- Retry dengan backoff; bounce/complaint webhook → tandai email bermasalah → tampilkan warning di UI user.

### F3. User webhook (untuk bot/institusi, fase 5)
- User daftarkan URL + secret; kirim event order/balance dengan HMAC signature; retry terbatas; auto-disable webhook yang gagal terus.
- **Anti-SSRF (wajib):** HTTPS only; tolak IP private/link-local/loopback saat validasi DAN saat kirim (resolve sekali, connect ke IP hasil resolve — anti DNS-rebinding); egress lewat jalur tanpa akses jaringan internal. Detail di 18-data-protection §B7.

### F4. Preferences
- Per user: channel per kategori. Security & dana TIDAK bisa dimatikan.

## Data Model (schema `notification`)
```sql
notifications(id ULID, user_id, type, channel, status ENUM(PENDING,SENT,
              FAILED,SUPPRESSED), event_id UNIQUE-per-type, payload JSONB,
              sent_at, error TEXT, attempts INT)
user_preferences(user_id, category, channel, enabled)
user_webhooks(id, user_id, url, secret_hash, events TEXT[], status, fail_count)
```

## Edge Cases
- Burst event (settlement batch besar) → rate-limit per user per tipe (max 1 email "order filled" per menit, digabung).
- Email provider down → antrian menumpuk → alarm; security email (login baru, withdraw) prioritas antrian tertinggi.
- User ganti email → notifikasi ke email LAMA dan baru (anti account takeover senyap).

## Testing
- Dedup: event duplikat → satu kirim.
- Template: render semua tipe × bahasa dengan fixture (CI menolak variabel yang hilang).
- Prioritas antrian: security email mendahului broadcast saat backlog.
