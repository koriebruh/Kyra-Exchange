# Deferred Features

Fitur yang **belum diimplementasi karena butuh setup/piece lain dulu** (frontend,
modul yang belum dibangun, dsb). Ditunda sadar, bukan dilupakan. Dikerjakan
nanti saat blocker-nya siap.

Format: **Apa** · **Ditunda karena** · **Ref spec** · **Kerjakan saat**.

---

### Captcha di endpoint auth
- **Apa:** Cloudflare Turnstile / hCaptcha di register, login, reset password,
  resend verification.
- **Ditunda karena:** butuh **frontend** untuk render widget + akun provider
  eksternal. Belum ada FE, jadi token captcha tak bisa diverifikasi nyata.
- **Ref spec:** [modules/01-identity.md](modules/01-identity.md) F1.
- **Kerjakan saat:** frontend auth dibangun.

### Anti-phishing code
- **Apa:** frasa pribadi user di semua email resmi (email tanpa frasa = phishing).
- **Ditunda karena:** butuh **modul notification** (email templating) yang belum
  dibangun.
- **Ref spec:** [modules/01-identity.md](modules/01-identity.md) F2b +
  [modules/13-notification.md](modules/13-notification.md).
- **Kerjakan saat:** modul notification dibangun (Fase 4).

### Passkeys / WebAuthn
- **Apa:** passkey sebagai 2FA; hardware key wajib untuk admin.
- **Ditunda karena:** TOTP sudah cukup untuk MVP; WebAuthn butuh frontend
  ceremony registrasi. Struktur login (`LoginResult`) sudah siap menerimanya.
- **Ref spec:** [modules/01-identity.md](modules/01-identity.md) F2c.
- **Kerjakan saat:** frontend + admin backoffice dibangun.

### Email verifikasi & notifikasi (kirim email asli)
- **Apa:** benar-benar kirim email verifikasi / alert login-baru / withdraw, dsb.
  Sekarang token verifikasi hanya dikembalikan API (dev), belum dikirim.
- **Ditunda karena:** butuh **modul notification** + provider email (SES/Postmark).
- **Ref spec:** [modules/13-notification.md](modules/13-notification.md).
- **Kerjakan saat:** modul notification dibangun (Fase 4).

---

*Saat sebuah fitur dikerjakan: hapus dari sini. Saat menemukan fitur baru yang
ditunda karena nunggu piece lain: tambah entri dengan format sama.*
