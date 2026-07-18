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
- **Ditunda karena:** modul notification SUDAH dibangun (templating jalan). Sisa:
  identity simpan frasa per user + kirim ke notification sebagai param template.
  Task kecil, tidak lagi blocked — buildable kapan saja.
- **Ref spec:** [modules/01-identity.md](modules/01-identity.md) F2b +
  [modules/13-notification.md](modules/13-notification.md).
- **Kerjakan saat:** increment berikutnya (tidak blocked).

### Passkeys / WebAuthn
- **Apa:** passkey sebagai 2FA; hardware key wajib untuk admin.
- **Ditunda karena:** TOTP sudah cukup untuk MVP; WebAuthn butuh frontend
  ceremony registrasi. Struktur login (`LoginResult`) sudah siap menerimanya.
- **Ref spec:** [modules/01-identity.md](modules/01-identity.md) F2c.
- **Kerjakan saat:** frontend + admin backoffice dibangun.

### Kirim email lewat provider ASLI (SMTP/SES/Postmark)
- **Apa:** delivery email nyata ke inbox user. Framework notification + template +
  verifikasi-email-on-register SUDAH jalan lewat `EmailSender` (mock:
  RecordingEmailSender yang record+log).
- **Ditunda karena:** butuh akun/kredensial provider email + setup SPF/DKIM/DMARC
  di domain. Real impl = satu bean `EmailSender` baru dipilih config, tanpa ubah
  logic notification.
- **Ref spec:** [modules/13-notification.md](modules/13-notification.md) F2.
- **Kerjakan saat:** ada akun provider email + domain terverifikasi.

### Wiring notification ke producer lain (wallet, login-baru, password-change)
- **Apa:** deposit/withdraw/login-baru/password-change kirim notifikasi. Register
  sudah wired. Sisanya perlu resolve email dari userId (wallet punya userId, bukan
  email) — butuh lookup identity→email.
- **Ditunda karena:** task kecil, bukan blocked. Perlu API resolve email per userId
  (atau caller bawa email).
- **Ref spec:** [modules/13-notification.md](modules/13-notification.md) Trigger table.
- **Kerjakan saat:** increment berikutnya.

---

*Saat sebuah fitur dikerjakan: hapus dari sini. Saat menemukan fitur baru yang
ditunda karena nunggu piece lain: tambah entri dengan format sama.*
