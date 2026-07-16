# 01 — Identity (Auth)

> Modul paling pertama dibangun. Semua modul lain bergantung ke sini untuk "siapa user ini dan boleh apa".

## Tujuan
Mengelola identitas user: pendaftaran, login, sesi, 2FA, API key untuk bot trading, dan otorisasi role. Menjadi satu-satunya penerbit token/credential di sistem.

## Tanggung Jawab
- Register & verifikasi email
- Login (password + 2FA) dan session management
- Penerbitan & validasi JWT access token + refresh token
- API key HMAC untuk programmatic trading
- Role & permission (USER, ADMIN, super-admin ops)
- Device/session tracking & revocation
- Rate limit percobaan login (anti brute force)

**Bukan tanggung jawab modul ini:** KYC (itu compliance), limit trading (itu risk), saldo (itu account).

## Fitur Detail

### F1. Registrasi
- Input: email + password. Password policy: min 10 char, cek terhadap daftar password bocor (k-anonymity API HaveIBeenPwned atau daftar lokal).
- Hash: **Argon2id** (memory 64MB, iterations 3, parallelism 2 — tuning per hardware).
- Email verification: token sekali pakai, expire 24 jam. Akun belum verified = tidak bisa login.
- Anti-enumeration: respons register selalu sama walau email sudah terdaftar ("cek email Anda").
- **Captcha wajib** (Cloudflare Turnstile / hCaptcha) di register, login, reset password, resend verification — tanpa ini bot farm bikin ribuan akun (persiapan wash trading / bonus abuse).

### F2. Login & Session
- Flow: email+password → (jika 2FA aktif) tantang TOTP → terbit access token (JWT, umur 15 menit) + refresh token (opaque, 30 hari, disimpan hash-nya di DB).
- Refresh token rotation: tiap refresh, token lama di-invalidate. Reuse token lama terdeteksi = seluruh keluarga token di-revoke (indikasi pencurian).
- Session record: device fingerprint, IP, user-agent, last_active. User bisa lihat & revoke session dari UI.
- Login dari IP/negara baru → email notifikasi.
- Lockout progresif: 5 gagal = delay 1 menit, 10 gagal = 15 menit + notifikasi email. Berbasis akun DAN IP (Valkey counter).

### F2b. Anti-phishing code
- User set frasa pribadi → dicantumkan di SEMUA email resmi. Email tanpa frasa = phishing. Fitur standar CEX, murah, efektif. Disimpan terenkripsi, dirender modul notification.

### F2c. Passkeys / WebAuthn (fase lanjut)
- Passkey sebagai 2FA user (lebih kuat dari TOTP, anti-phishing by design); hardware key wajib untuk admin. Desain sekarang: tabel credential terpisah — jangan hardcode asumsi "2FA = TOTP" di flow login.

### F3. Two-Factor (TOTP)
- TOTP RFC 6238, secret 160-bit, window ±1 step.
- Recovery codes: 10 kode sekali pakai, ditampilkan sekali, disimpan hash.
- **2FA wajib untuk:** withdraw, ubah password, buat API key, disable 2FA (butuh recovery code + email confirm + cooldown 24 jam penahanan withdraw).
- Anti-replay: satu kode TOTP hanya valid sekali (catat last used timestep).

### F4. API Key (bot trading)
- Key pair: `api_key` (public id) + `api_secret` (ditampilkan sekali, disimpan hash).
- Autentikasi request: HMAC-SHA256 atas `(timestamp + method + path + body)`, timestamp tolerance ±30 detik (anti-replay).
- Scope per key: `read`, `trade`, `withdraw` (withdraw default OFF, aktifkan butuh 2FA + whitelist address).
- IP whitelist opsional per key. Expiry opsional. Revoke instan.

### F5. Role & Permission
- Role: `USER`, `SUPPORT` (read-only user data), `OPS` (approval withdraw), `ADMIN` (config), `SUPERADMIN`.
- Admin login terpisah: wajib 2FA hardware-key-ready (WebAuthn, fase lanjut), IP allowlist kantor/VPN.
- Semua permission check lewat satu titik (`IdentityApi.authorize(userId, permission)`).

## Data Model (schema `identity`)

```sql
users(id ULID PK, email UNIQUE, password_hash, status ENUM(PENDING,ACTIVE,SUSPENDED,CLOSED),
      email_verified_at, created_at)
totp_secrets(user_id PK FK, secret_encrypted, enabled_at, last_used_timestep)
recovery_codes(id, user_id, code_hash, used_at)
sessions(id, user_id, refresh_token_hash, family_id, device_info JSONB, ip,
         created_at, last_active_at, revoked_at)
api_keys(id, user_id, key_id UNIQUE, secret_hash, scopes TEXT[], ip_whitelist INET[],
         expires_at, revoked_at, created_at)
roles(user_id, role, granted_by, granted_at)
login_attempts(id, email, ip, success BOOL, at)   -- untuk lockout & forensik
```

Secret TOTP dienkripsi at-rest (AES-GCM, key dari secrets manager), bukan plaintext.

## Domain Events (dipublish ke outbox)
- `UserRegistered {user_id, email}`
- `UserLoggedIn {user_id, ip, device}` / `LoginFailed {email, ip}`
- `TwoFactorEnabled/Disabled {user_id}`
- `ApiKeyCreated/Revoked {user_id, key_id}`
- `SessionRevoked {user_id, session_id}`
- `UserSuspended {user_id, reason, by}`

Konsumen: notification (email alert), compliance (deteksi anomali), audit log.

## API (prefix `/v1/auth`)
```
POST /register            POST /login              POST /login/2fa
POST /token/refresh       POST /logout             GET  /sessions
DELETE /sessions/{id}     POST /2fa/enable         POST /2fa/confirm
POST /2fa/disable         POST /password/change    POST /password/reset-request
POST /password/reset      GET  /api-keys           POST /api-keys
DELETE /api-keys/{id}
```

## Security Notes
- JWT ditandatangani asymmetric (Ed25519) — modul lain verifikasi pakai public key, tidak bisa menerbitkan.
- Klaim JWT minimal: `sub` (user_id), `roles`, `exp`, `jti`. Tidak ada PII di token.
- Password reset: token sekali pakai 30 menit; reset = revoke semua session + tahan withdraw 24 jam.
- Ubah email: konfirmasi dua sisi (email lama + baru) + tahan withdraw 24 jam.
- Semua aksi di atas masuk audit log immutable.

## Edge Cases
- Register dengan email yang pernah CLOSED → buat akun baru, akun lama tetap tersimpan (kebutuhan audit/regulasi).
- Refresh token dipakai bersamaan dari 2 device (race) → yang kedua terdeteksi reuse → revoke family, paksa login ulang.
- Jam server drift → TOTP window ±1 step, NTP wajib di VPS.
- User kehilangan 2FA + recovery codes → proses manual via support + KYC ulang (identity hanya sediakan API `adminReset2fa`, keputusan di admin).

## Dependencies
- **Keluar:** notification (kirim email), audit log.
- **Masuk (dipakai modul lain):** `IdentityApi.verifyToken()`, `authorize()`, `verifyApiKeySignature()` — dipakai layer REST/WS di kyra-app.

## Testing
- Unit: password policy, TOTP window, HMAC verification, lockout state machine.
- Integration (Testcontainers): full flow register→verify→login→2FA→refresh→revoke.
- Security test: token reuse detection, replay HMAC dengan timestamp kadaluarsa, brute force lockout.
