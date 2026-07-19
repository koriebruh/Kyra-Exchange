# 16 — Ops & Security Program

> Bukan modul kode — disiplin operasional. Exchange jatuh bukan cuma karena bug, tapi karena ops jelek: backup tak pernah dites, alert tak ada yang baca, akses eks-karyawan tak dicabut. Dokumen ini = kontrak operasional minimum.

## 1. Environments

| Env | Tujuan | Data |
|---|---|---|
| `dev` | lokal developer, docker compose | dummy |
| `staging` | mirror prod (spec lebih kecil), uji release + custody di testnet chain | dummy + testnet chain |
| `prod` | live | real |
| `sandbox` (fase 5) | publik untuk developer bot: API identik, dana palsu | simulasi |

- Rilis WAJIB lewat staging dulu (smoke test otomatis: place order, cancel, deposit testnet).
- Config per env via env vars + SOPS; TIDAK ada secret di image/repo plaintext.

## 2. Deploy & Release
- Pipeline: PR → CI (build, test, ArchUnit, dependency scan) → merge → image → staging auto-deploy → smoke test → prod deploy manual-approve.
- Prod deploy: rolling dengan graceful shutdown — **matching engine drain protocol**: stop intake → flush event → snapshot → baru stop proses. Startup kebalikan (recovery dulu, intake terakhir).
- Rollback: image sebelumnya selalu siap; DB migration wajib backward-compatible satu versi (expand-contract pattern) supaya rollback aman.
- Maintenance window besar: umumkan H-24 (banner + email), mode cancel-only dulu sebelum full stop.

## 3. Backup & Disaster Recovery
- Postgres: WAL archiving kontinu + full backup harian → object storage terenkripsi (restic), retensi 30 hari + 12 bulanan.
- **Restore drill BULANAN**: restore ke server kosong, jalankan app, verifikasi rekonsiliasi ledger. Backup yang tidak pernah di-restore = tidak ada backup. Hasil drill dicatat.
- Target awal: RPO ≤ 5 menit (WAL streaming), RTO ≤ 4 jam (terdokumentasi & dilatih).
- Replica streaming di VPS terpisah (beda datacenter kalau bisa) — failover manual dulu, terdokumentasi langkah-demi-langkah.
- Snapshot matching engine & event log ikut backup DB (satu sumber, konsisten).

## 4. Monitoring & Alerting (katalog minimum)

**Page (bangunin orang):**
- Rekonsiliasi gagal / selisih ledger-custody ≠ 0
- Settlement lag > 30 detik
- Balance drift terdeteksi (balances ≠ Σ entries)
- Withdraw stuck BROADCASTING > 30 menit
- App down / health check merah / DB replication broken
- Login admin dari IP di luar allowlist (indikasi breach)
- Lonjakan withdraw global (circuit breaker hampir kena)

**Ticket (jam kerja):**
- Deposit HELD menumpuk, KYC queue > SLA, WS disconnect rate naik, disk > 80%, cert expiry < 14 hari, backup gagal, sandbox down

- Stack, exporter lengkap, standar logging/tracing (OTel), SLI/SLO detail → **modul 17-observability** (spesifikasi teknis penuh). Setiap alert page WAJIB punya runbook link.
- Alert rules di-version-control + `promtool test rules` di CI (alert juga kode).

## 5. Incident Management
- Severity: **SEV1** (dana berisiko / trading mati total / breach) — respon < 15 menit, freeze dulu pikir kemudian; **SEV2** (degradasi fitur besar); **SEV3** (minor).
- SEV1 playbook pertama: **kill-switch withdraw → snapshot bukti (log, DB) → baru investigasi.** Uang keluar tidak bisa di-undo; downtime bisa dimaafkan, dana hilang tidak.
- Postmortem blameless wajib SEV1/SEV2 (template: timeline, akar masalah, action items ber-deadline).
- Komunikasi publik: status page + template pengumuman disiapkan SEBELUM insiden pertama.

## 6. Security Program
- **Perimeter:** Cloudflare (atau setara) di depan semua endpoint — DDoS mitigation, WAF, bot management. Origin IP dirahasiakan (firewall hanya terima dari CDN). Exchange PASTI kena DDoS & credential stuffing — bukan "kalau", tapi "kapan".
- **Akses internal:** SSH key-only + MFA, akses prod via bastion, per-orang (tidak ada akun bersama), least privilege DB. **Offboarding checklist < 1 jam**: revoke SSH, VPN, admin role, secrets rotate yang dia tahu.
- **Secrets:** OpenBao/Vault untuk seed custody + secret runtime; SOPS+age untuk config. Rotasi terjadwal: DB password, JWT signing key (dukung dua key aktif untuk rotasi mulus). Seed HD custody: backup + prosedur unseal OpenBao.
- **Dependency & image scanning:** CI wajib (osv-scanner/Trivy), patch SLA: critical 48 jam.
- **Pentest:** eksternal sebelum fase 4 go-live, lalu tahunan + setiap perubahan besar permukaan serangan.
- **Bug bounty** (fase 5): mulai private program; scope, reward tiers, safe harbor jelas.
- **NTP:** wajib akurat di semua node (TOTP, HMAC timestamp, urutan event bergantung waktu).
- **Audit log review:** review berkala akses admin & anomali (bukan cuma dikumpulkan).

## 7. Proof of Reserves (fase 5 — kepercayaan publik pasca-FTX)
- Publikasi berkala: Merkle tree dari saldo user (user bisa verifikasi saldo miliknya ada di tree) + bukti kepemilikan address custody (on-chain) ≥ total liabilities.
- Otomatiskan generasinya dari snapshot ledger; frekuensi bulanan.
- Nilai jual kompetitif nyata di pasar Indonesia — bedakan dari exchange abal-abal.

## 8. Dokumen Ops yang Harus Ada (living docs, folder `ops/` repo)
- [ ] Runbook per alert page (langkah diagnosis + mitigasi)
- [ ] Prosedur failover DB
- [ ] Prosedur restore backup (dites bulanan)
- [ ] Playbook SEV1 + kontak darurat (penyedia node/RPC, penyedia server, konsultan hukum)
- [ ] Onboarding/offboarding checklist
- [ ] Register akses: siapa punya akses apa (review kuartalan)

## Testing / Verifikasi Berkala
- Chaos drill kuartalan di staging: matikan DB primary / putus koneksi node RPC / kill app saat load → sistem berperilaku sesuai desain (fail-closed withdraw, recovery matching benar).
- Tabletop exercise insiden SEV1 dua kali setahun (latihan tim, bukan cuma sistem).
