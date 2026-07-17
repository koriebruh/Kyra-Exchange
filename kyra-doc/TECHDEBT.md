# Tech Debt & Deferred Work

Daftar hal yang **sengaja ditunda**, alasannya, dan referensi spec + file kode
terkait. Bukan bug — keputusan sadar agar tidak membangun stub palsu sebelum
dependensinya ada. Tiap item punya "kapan dikerjakan" supaya tidak terlupa.

Konvensi status: `DEFERRED` (belum, ada blocker) · `PARTIAL` (sebagian jalan) ·
`PLANNED` (dijadwalkan fase tertentu).

---

## Fase 1 — Identity & Ledger

### TD-1. Captcha di endpoint auth — DEFERRED
- **Apa:** Cloudflare Turnstile / hCaptcha di register, login, reset password,
  resend verification (anti bot-farm & credential stuffing).
- **Kenapa ditunda:** butuh frontend untuk render widget + akun/secret provider
  eksternal. Tanpa frontend, verifikasi token captcha tidak bisa diuji nyata.
- **Spec:** [modules/01-identity.md](modules/01-identity.md) F1 (Registrasi).
- **Kode saat ini:** `modules/identity/.../domain/IdentityService.java` —
  `register()` belum ada langkah verifikasi captcha.
- **Kapan:** saat frontend auth dibangun (paralel Fase 5 / saat UI mulai), atau
  lebih awal jika bot abuse muncul di staging.

### TD-2. Anti-phishing code — DEFERRED
- **Apa:** frasa pribadi user yang dicantumkan di semua email resmi; email tanpa
  frasa = phishing.
- **Kenapa ditunda:** butuh modul **notification** (email templating) yang belum
  dibangun. Menyimpan frasa tanpa yang me-render-nya = fitur mati.
- **Spec:** [modules/01-identity.md](modules/01-identity.md) F2b +
  [modules/13-notification.md](modules/13-notification.md) F1.
- **Kode saat ini:** belum ada; kolom frasa belum di skema `identity`.
- **Kapan:** bersamaan implementasi modul notification (Fase 4 minimal).

### TD-3. Passkeys / WebAuthn — PLANNED
- **Apa:** passkey sebagai 2FA (anti-phishing by design); hardware key wajib
  untuk admin.
- **Kenapa ditunda:** TOTP sudah memenuhi kebutuhan 2FA MVP; WebAuthn menambah
  ceremony registrasi + dependency. Desain sekarang sudah tidak hardcode
  "2FA = TOTP" (login pakai `LoginResult` sealed), jadi penambahan tidak
  membongkar alur.
- **Spec:** [modules/01-identity.md](modules/01-identity.md) F2c.
- **Kode saat ini:** `LoginResult` (api), `TwoFactorService` (domain) — struktur
  sudah siap menerima faktor tambahan.
- **Kapan:** Fase lanjut, sebelum/di sekitar admin backoffice (Fase 4).

### TD-4. Audit hook untuk login & register — PARTIAL
- **Apa:** catat `LOGIN_SUCCESS`, `LOGIN_FAILED`, `USER_REGISTERED` ke audit log
  dengan actor + IP.
- **Kenapa ditunda:** aksi **authenticated** paling sensitif (buat/cabut API key,
  enable/disable 2FA) sudah di-audit. Login/register perlu threading actor/email
  ke titik yang tepat (login sukses tidak bawa userId ke REST; gagal login butuh
  email tanpa membocorkan enumerasi). Bukan blocker keamanan, tinggal kelengkapan.
- **Spec:** [modules/01-identity.md](modules/01-identity.md) (Security Notes) +
  [modules/16-ops-security.md](modules/16-ops-security.md) §6.
- **Kode saat ini:** `kyra-app/.../audit/AuditLog.java` +
  `kyra-app/.../auth/AuthResource.java` (baru audit API-key & 2FA).
- **Kapan:** saat menyentuh AuthResource berikutnya; kecil.

### TD-5. API key: masa berlaku (expiry) — PARTIAL
- **Apa:** parameter `expiresAt` saat pembuatan API key.
- **Kenapa ditunda:** kolom `expires_at` ada di skema & dicek saat autentikasi,
  tapi `ApiKeyApi.create(...)` belum menerima parameter expiry — jadi field
  selalu null (key tanpa kedaluwarsa). Fungsional untuk MVP; expiry = pengetatan.
- **Spec:** [modules/01-identity.md](modules/01-identity.md) F4.
- **Kode saat ini:** `modules/identity/.../api/ApiKeyApi.java` (signature
  `create`), `.../domain/ApiKeyService.java` (`entity.expiresAt` tak pernah diset).
- **Kapan:** saat API key management UI dibangun, atau saat ada permintaan.

### TD-6. JWT: RS256 sekarang, target Ed25519 — PLANNED
- **Apa:** tanda tangan access token pakai RS256; spec menyebut Ed25519 (EdDSA).
- **Kenapa:** RS256 first-class di SmallRye JWT & langsung jalan; EdDSA butuh
  verifikasi konfigurasi sign+verify lintas komponen. Peralihan = ganti kunci +
  properti, **tanpa perubahan kode** (algoritma dari konfigurasi).
- **Spec:** [modules/01-identity.md](modules/01-identity.md) (Security Notes:
  "JWT ditandatangani asymmetric (Ed25519)").
- **Kode saat ini:** `modules/identity/.../domain/TokenService.java`
  (`Jwt...sign()`), kunci di `resources/jwt/*.pem`.
- **Kapan:** sebelum produksi (Fase 4 hardening) bareng rotasi kunci via secret.

### TD-7. API key secret disimpan encrypted, bukan hash — RESOLVED-BY-DESIGN
- **Catatan:** spec F4 menulis "secret disimpan hash", tapi HMAC verification
  butuh secret asli untuk dihitung ulang → **wajib** reversible. Disimpan
  AES-256-GCM (`CryptoService`). Ini penyimpangan sadar dari teks spec, bukan
  utang — dicatat agar reviewer paham. Perbaiki teks spec saat revisi.
- **Spec:** [modules/01-identity.md](modules/01-identity.md) F4.
- **Kode:** `modules/identity/.../domain/ApiKeyService.java`.

### TD-8. Uji kedaluwarsa berbasis waktu (JWT/refresh/challenge) — DEFERRED
- **Apa:** test untuk access token / refresh token / 2FA challenge yang benar-
  benar lewat masa berlaku.
- **Kenapa ditunda:** butuh kontrol waktu (clock injection) agar tidak flaky /
  lambat. Jalur revoked & rotated sudah tercakup; expiry logic ada tapi diuji
  lewat waktu nyata itu rapuh.
- **Kode:** perlu abstraksi `Clock` yang diinject ke `TokenService`,
  `TwoFactorService`, `IdentityService` (kini pakai `Instant.now()` langsung).
- **Kapan:** saat menambah faktor waktu lain / sebelum audit keamanan Fase 4.

---

## Lintas-modul / infra (belum jatuh tempo, dicatat agar tak lupa)

### TD-9. Grant DB append-only (entries, audit_log) — PLANNED
- **Apa:** cabut UPDATE/DELETE di level GRANT untuk `account.entries` &
  `audit.audit_log`; kini hanya dijaga trigger.
- **Kenapa ditunda:** butuh user DB aplikasi terpisah dari owner (setup prod).
  Trigger sudah memberi proteksi di dev.
- **Spec:** [modules/02-account-ledger.md](modules/02-account-ledger.md) +
  [modules/16-ops-security.md](modules/16-ops-security.md).
- **Kapan:** setup infra prod (Fase 4).

### TD-10. Outbox / EventPublisher belum terpakai — PLANNED
- **Apa:** `EventEnvelope` (kyra-common) sudah ada, tapi belum ada tabel outbox
  atau `EventPublisher`. Event domain (`BalanceChanged`) kini hanya CDI event
  in-process.
- **Kenapa ditunda:** konsumen event (marketdata WS, notification) belum
  dibangun. Abstraksi dibuat saat konsumen pertama muncul (Fase 2/3).
- **Spec:** [README.md](README.md) §7 + [adr/0003-postgres-centric-event-backbone.md](adr/0003-postgres-centric-event-backbone.md).
- **Kapan:** Fase 2 (settlement → marketdata).

---

## Cara pakai dokumen ini
- Saat sebuah item dikerjakan: pindahkan ke commit terkait, hapus dari sini
  (atau ubah status jadi RESOLVED dengan tanggal).
- Saat menemukan utang baru: tambah entri `TD-N` dengan format sama
  (Apa / Kenapa ditunda / Spec / Kode / Kapan).
- Review daftar ini di awal tiap fase baru.
