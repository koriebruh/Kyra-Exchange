# 18 — Data Protection (Anti Kehilangan & Anti Kebocoran)

> Hasil audit lintas-modul sebelum koding. Dua sumbu: data tidak boleh HILANG (durability) dan tidak boleh BOCOR (confidentiality). Aturan di sini mengikat semua modul — kalau konflik dengan dokumen modul, dokumen ini menang.

## A. Matriks Durability (di mana data hidup, apa yang boleh hilang)

| Data | Tempat | Boleh hilang? | Mekanisme proteksi |
|---|---|---|---|
| Ledger (journals/entries) | Postgres | **TIDAK PERNAH** | append-only, `synchronous_commit=on`, WAL archiving, backup+drill, replica |
| Matching event log | Postgres | **TIDAK PERNAH** | write-ahead sebelum ACK; arsip permanen (lihat §A2) |
| Order/trade/deposit/withdraw records | Postgres | tidak | transaksi + backup |
| Order book state | RAM | ya (rebuild) | snapshot + replay event log |
| Candle/ticker berjalan | RAM | ya (rebuild) | rebuild dari tabel trades |
| Trigger (stop) orders | RAM | tidak | event log + rebuild (pola matching) |
| Outbox events | Postgres | tidak sebelum terpublish | purge HANYA setelah published + umur > 30 hari |
| Session/refresh token | Postgres (hash) | tidak | DB biasa |
| Rate-limit counter, cache | Valkey | ya | — |
| WS delivery ke klien | network | ya | klien resync via seq/snapshot |

### A1. ATURAN VALKEY (baru, mengikat)
**Valkey = cache & counter DISPOSABLE only.** Dilarang menyimpan data yang tidak bisa direkonstruksi dari Postgres. Restart Valkey kosong = sistem tetap benar (lebih lambat sesaat). Ditegakkan di code review; pelanggaran = block merge.

### A2. Retensi snapshot & event log matching
- Simpan **3 snapshot terakhir** per pair (bukan 1) — snapshot korup → fallback ke sebelumnya + replay lebih panjang. Verifikasi checksum snapshot saat tulis & baca.
- `matching_events` tidak pernah DELETE. Partisi bulanan; partisi > 6 bulan di-detach → arsip object storage (terenkripsi) — tetap bisa direstore untuk audit/dispute.

### A3. Postgres durability (wajib, non-negotiable)
- `synchronous_commit = on` (default — JANGAN dimatikan demi benchmark), `fsync = on`.
- Failover ke replica async = ada window kehilangan → prosedur failover WAJIB: (1) cek `last_settled_seq` vs matching event log, (2) rekonsiliasi ledger vs custody on-chain SEBELUM buka trading/withdraw. Trading buka terakhir, withdraw paling terakhir.

### A4. Jaring pengaman webhook (deposit — lubang kritis yang ditemukan audit)
Webhook = jalur cepat, **BUKAN satu-satunya jalur**:
- **Polling deteksi** tiap 5 menit: tarik daftar transaksi dari node/indexer, diff dengan tabel deposits/withdrawals → transaksi yang tidak dikenal = diproses (idempotent by txid, jadi aman dobel).
- Withdraw BROADCASTING juga di-poll statusnya (bukan cuma nunggu webhook).
- Metric: `kyra_wallet_sweep_found_missed_total` — nilai > 0 sesekali = normal (webhook loss terjadi); melonjak = investigasi.
- Pola sama untuk webhook KYC provider: polling status submission yang menggantung > 1 jam.

## B. Aturan Anti-Kebocoran (mengikat semua modul)

### B1. Otorisasi objek (anti-IDOR)
- SETIAP endpoint yang menerima ID resource (order, deposit, withdraw, session, api-key, case) WAJIB verifikasi kepemilikan `resource.user_id == caller` di layer domain (bukan cuma di query filter).
- **Test wajib per endpoint:** akses resource user lain → 404 (bukan 403 — jangan konfirmasi keberadaan resource).
- Admin endpoints: permission check + audit view (siapa lihat data siapa).

### B2. Error hygiene
- Response error API = kode stabil + pesan generik. DILARANG: stack trace, SQL, class name, versi library, path internal. Exception mapper global menegakkan (bukan disiplin per-endpoint).
- Detail teknis → log dengan trace_id; user dapat `error_id` (= trace_id) untuk dilaporkan ke support.
- 404 untuk resource orang lain (lihat B1); 401 vs 403 konsisten & terdokumentasi.

### B3. Token & credential di transit
- **WS auth: token dikirim di message pertama setelah connect, DILARANG di URL query string** (query nyangkut di access log proxy/CDN).
- Semua header `Authorization` & body endpoint auth di-exclude dari access log Caddy/CDN.
- HSTS aktif; TLS ≥ 1.2; cookie (kalau dipakai UI) `Secure + HttpOnly + SameSite=Strict`.
- CORS: allowlist origin eksplisit (domain UI kita saja) di endpoint browser; API-key endpoints tidak butuh CORS longgar.

### B4. Data publik = anonim total
- Trade feed / depth / candle publik: DILARANG memuat `user_id`, `order_id`, atau apapun yang bisa di-link ke akun. Depth = agregat per price level saja.
- Kontrak ini dites: schema test respons publik menolak field terlarang.

### B5. PII lifecycle
- PII (KYC) terenkripsi at-rest (AES-GCM), key di secrets manager, **envelope encryption** + rotasi key terjadwal (re-encrypt lazy).
- Disk VPS: **full-disk encryption (LUKS)** minimal di volume DB & backup — PII tidak boleh telanjang di disk provider.
- Backup = berisi segalanya → akses backup dibatasi sama ketatnya dengan akses DB prod + tercatat.
- Export CSV (statement user, report admin): **sanitasi formula injection** — cell diawali `= + - @` di-prefix `'`.
- Log/trace: larangan PII (sudah di 17) + redaction processor di Alloy sebagai lapis kedua.

### B6. Anti-enumeration (konsisten di semua fitur)
- Register/reset password: respons uniform (sudah di 01).
- **Internal transfer**: lookup penerima by UID/email → respons TIDAK membedakan "tidak terdaftar" vs "tidak bisa menerima"; rate limit ketat; konfirmasi nama parsial hanya SETELAH rate-limit & captcha.
- Deposit address lookup, API key id, dsb: pola sama — jangan jadi oracle keberadaan akun.

### B7. Webhook keluar (user webhook) — anti-SSRF
- URL user divalidasi: HTTPS only, resolve DNS → tolak IP private/link-local/loopback (10.x, 172.16-31.x, 192.168.x, 169.254.x, 127.x, ::1, fd00::/8) **saat validasi DAN saat kirim** (anti DNS-rebinding: resolve sekali, connect ke IP hasil resolve).
- Egress webhook lewat proxy/network namespace terpisah tanpa akses ke jaringan internal.
- Response webhook tidak pernah di-log body-nya penuh (bisa berisi apapun).

### B8. Callback/poll masuk (node/indexer custody, KYC, email provider)
- SEMUA webhook masuk: verifikasi signature + timestamp tolerance (anti-replay) + idempotency. Tidak ada pengecualian. Endpoint webhook = path rahasia + IP allowlist provider (defense berlapis, bukan pengganti signature).

## C. Checklist Penegakan (masuk CI / definition-of-done per modul)
- [ ] Test IDOR per endpoint resource (template test, wajib semua modul)
- [ ] Schema test respons publik (tolak field user_id/order_id)
- [ ] Log scanner anti-secret/PII (sudah di 17) + redaction Alloy
- [ ] Exception mapper global + test "error tidak bocorkan internal"
- [ ] Chaos test: restart Valkey kosong → sistem benar
- [ ] Crash-recovery test matching + settlement (sudah di 05/06) + snapshot korup → fallback
- [ ] Sweep test: matikan webhook mock → deposit tetap terdeteksi via polling
- [ ] SSRF test: webhook URL internal ditolak (daftar payload standar)
- [ ] Failover drill: ikuti prosedur A3, verifikasi urutan buka layanan
