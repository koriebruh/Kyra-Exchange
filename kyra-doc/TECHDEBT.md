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
- **Kerjakan saat:** penyimpanan+API SUDAH jadi (setAntiPhishingCode/antiPhishingCode). Sisa: render frasa di email login-alert/withdraw (butuh producer email itu dibangun).

### Passkeys / WebAuthn
- **Apa:** passkey sebagai 2FA; hardware key wajib untuk admin.
- **Ditunda karena:** TOTP sudah cukup untuk MVP; WebAuthn butuh frontend
  ceremony registrasi. Struktur login (`LoginResult`) sudah siap menerimanya.
- **Ref spec:** [modules/01-identity.md](modules/01-identity.md) F2c.
- **Kerjakan saat:** frontend + admin backoffice dibangun.

### Kirim email lewat provider ASLI (SMTP/SES/Postmark)
- **Apa:** delivery email nyata ke inbox user. Framework notification + template +
  verifikasi-email-on-register SUDAH jalan. Dua backend `EmailSender`:
  `RecordingEmailSender` (mock, default test) + `SmtpEmailSender` (quarkus-mailer,
  `kyra.email.provider=smtp`). Dev sudah kirim ke Mailpit (UI :8025).
- **Ditunda karena:** butuh akun/kredensial relay email PROD + setup SPF/DKIM/DMARC
  di domain. Real impl = set `QUARKUS_MAILER_*` env, tanpa ubah kode.
- **Ref spec:** [modules/13-notification.md](modules/13-notification.md) F2 +
  [DEV-INFRA.md](DEV-INFRA.md).
- **Kerjakan saat:** ada akun relay email prod + domain terverifikasi.

### Integrasi custody Fystack (ASLI) — app BELUM connect
- **Apa:** implementasi asli `CustodyProvider` → `HttpCustodyProvider` lawan Fystack
  **Apex REST API** (`http://localhost:8150` kalau self-host). Sekarang cuma
  `MockCustodyProvider`; **nol kode Fystack**. deposit address, submit withdrawal
  (idempotent by withdrawId), custody balance (reconciliation).
- **Fakta:** Fystack **open-core, BISA self-host** (Docker, `./fystack-ignite.sh`,
  ~14 service: Apex API :8150, UI :8015, 3× MPCIUM :8080–8082, Postgres :5433,
  Redis :6380, Mongo :27018, NATS :4223, Consul :8501). Butuh **CoinMarketCap API
  key** + ~4 vCPU/4 GB RAM. Repo: github.com/fystack/fystack-selfhost-scripts.
- **Ditunda karena:** (1) stack berat + butuh CMC key + config di-generate ignite →
  bukan dijejalkan ke docker-compose.dev.yml, dijalankan opt-in; (2) belum ambil
  **kontrak Apex API pasti** (endpoint wallet/address/transfer/balance + auth) —
  wajib verifikasi dari docs.fystack.io sebelum koding (uang real, gak nebak);
  (3) port MPCIUM 8080 bentrok app Kyra → app pindah port. Blocker rilis produksi.
- **Ref spec:** [modules/08-wallet.md](modules/08-wallet.md) + [DEV-INFRA.md](DEV-INFRA.md).
- **Kerjakan saat:** kontrak Apex API diverifikasi + Fystack stack jalan (dev/sandbox).

### Integrasi vendor lain: KYC, AML screening, price feed
- **Apa:** impl asli `MockKycProvider`, `MockAddressScreener`, `Mock*PriceProvider`.
- **Ditunda karena:** vendor belum dipilih; ini API hosted, butuh kredensial +
  verifikasi kontrak. Real impl = satu bean per provider dipilih config, pola email.
- **Ref spec:** [modules/10-compliance.md](modules/10-compliance.md),
  [modules/09-risk.md](modules/09-risk.md) + [DEV-INFRA.md](DEV-INFRA.md).
- **Kerjakan saat:** vendor dipilih + kredensial tersedia.

### Wiring notification ke producer lain (wallet, login-baru, password-change)
- **Apa:** deposit/withdraw/login-baru/password-change kirim notifikasi. Register
  sudah wired. Sisanya perlu resolve email dari userId (wallet punya userId, bukan
  email) — butuh lookup identity→email.
- **Ditunda karena:** task kecil, bukan blocked. Perlu API resolve email per userId
  (atau caller bawa email).
- **Ref spec:** [modules/13-notification.md](modules/13-notification.md) Trigger table.
- **Kerjakan saat:** increment berikutnya.

### Pajak PPh/PPN per trade (modul 15)
- **Apa:** withholding PPh (penjual) + PPN (pembeli) per trade ke akun
  `kyra:tax:*`, setor & lapor DJP, konversi nilai ke IDR.
- **Ditunda karena:** mekanisme (siapa bayar apa, basis perhitungan, status PPN
  terkini pasca-reklasifikasi ke aset keuangan) + rate WAJIB dikonfirmasi
  konsultan pajak — spec modul 15 sendiri menandai ini TODO. Konversi IDR butuh
  sumber kurs pajak (KMK). Membangun mekanisme tebakan = risiko salah hukum.
  Struktur withholding-nya identik fee (sudah ada polanya), jadi implementasi
  cepat begitu rate+basis dikonfirmasi.
- **Ref spec:** [modules/15-tax.md](modules/15-tax.md).
- **Kerjakan saat:** konsultan pajak konfirmasi rate/basis/mekanisme + ada sumber
  kurs IDR.

### Fase 6 — Derivatives: refinement lanjutan
- **Apa:** SUDAH JADI (modules/derivatives): open/close/liquidation + mark-price
  PnL + insurance backstop + funding rate + position averaging + partial close.
  Sisa: ADL (auto-deleverage), mode margin cross vs isolated, feed mark/index
  price + funding-premium ASLI (sekarang mock `MarkPriceProvider`).
- **Ditunda karena:** feed harga eksternal butuh integrasi vendor; ADL & mode
  margin = refinement di atas fondasi yang sudah ada & teruji.
- **Ref spec:** [modules/09-risk.md](modules/09-risk.md) Bagian B + README fase 6.
- **Kerjakan saat:** ada feed harga + kebutuhan produk derivatives penuh.

---

*Saat sebuah fitur dikerjakan: hapus dari sini. Saat menemukan fitur baru yang
ditunda karena nunggu piece lain: tambah entri dengan format sama.*
