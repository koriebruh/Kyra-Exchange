# 08 — Wallet (Custody via Fystack)

> Titik kontak satu-satunya dengan blockchain, lewat Fystack (MPC wallet-as-a-service). Fase 4 — sejak modul ini live, bug = kehilangan uang asli.

## Tujuan
Mengelola deposit & withdraw crypto user melalui API Fystack, menjaga ledger internal selalu sinkron dengan saldo custody, tanpa pernah memegang private key.

## Prinsip
1. Ledger internal = kebenaran saldo user; Fystack = kebenaran aset on-chain. Keduanya WAJIB rekonsiliasi harian.
2. Semua callback/webhook dari Fystack diverifikasi signature-nya. Tidak ada trust tanpa verifikasi.
3. Withdraw = jalur paling diserang di exchange manapun → defense berlapis (2FA, whitelist, limit, approval, delay).
4. Integrasi Fystack di balik interface `CustodyProvider` — ganti/tambah provider = implementasi baru, bukan refactor. **Sebelum implementasi: baca docs API Fystack terbaru & konfirmasi fitur (deposit webhook, withdraw API, address generation, sweep policy).**

## Fitur Detail

### F1. Deposit address
- Per user per aset: generate address via Fystack API saat pertama diminta; simpan mapping `(user, asset, chain) → address`.
- Address ditampilkan dengan QR + peringatan chain (kirim BTC ke address ETH = hilang).
- Address reuse: satu address per user permanen (standar CEX) — memudahkan atribusi deposit.

### F2. Alur Deposit
```
1. Webhook Fystack: incoming tx {txid, address, asset, amount, confirmations}
2. Verifikasi signature webhook + idempotency by (txid, output_index)
3. Insert deposits status DETECTED (0 conf) → tampil "pending" di UI
4. Tiap update confirmations → update; capai min_confirmations (per aset, dari market)
   → status CONFIRMED → journal DEPOSIT (external → user:main) → BalanceChanged
5. compliance hook: screening address sumber (AML) SEBELUM credit;
   flagged → status HELD + case ke admin, dana tidak masuk saldo user
```
- Deposit di bawah minimum (dust) → tercatat, tidak dikredit (kebijakan: akumulasi sampai lewat minimum, atau abaikan — putuskan & dokumentasikan).
- Reorg chain: confirmations turun / tx hilang → deposit CONFIRMED tidak pernah di-rollback otomatis (min_confirmations dipilih agar praktis mustahil); kejadian nyata = insiden manual.
- **Polling sweep (WAJIB — webhook bukan satu-satunya jalur):** tiap 5 menit tarik daftar transaksi dari Fystack API, diff dengan tabel lokal → transaksi tak dikenal diproses seolah webhook datang (idempotent by txid → aman dobel). Withdraw BROADCASTING juga di-poll statusnya. Webhook hilang TIDAK boleh = deposit hilang. Detail di 18-data-protection §A4.

### F3. Alur Withdraw (paling kritis)
```
1. Request: user pilih aset, address tujuan, amount. Wajib: 2FA TOTP + (jika aktif) address whitelist
2. Validasi: saldo cukup, amount ≥ min_withdraw, format address valid per chain
3. RiskApi.checkWithdraw: limit harian per user/level KYC, velocity, skor anomali
4. AccountApi.hold(user, asset, amount + fee_withdraw)  → status PENDING_REVIEW
5. Keputusan:
   - amount < auto_threshold & risk lolos → auto-approve
   - selain itu → antrian approval admin (4-eyes untuk nominal besar)
6. APPROVED → panggil Fystack withdraw API (idempotency key = withdraw_id)
   → status BROADCASTING → simpan txid
7. Webhook konfirmasi on-chain → status COMPLETED
   → journal WITHDRAW (user:hold → external) final
8. REJECTED/FAILED → release hold + notifikasi + alasan
```
- Delay keamanan: withdraw ke address BARU (bukan whitelist) → tahan 24 jam (dengan opsi user membatalkan) — mitigasi akun ke-hack.
- Perubahan keamanan akun (reset password/2FA/email) → freeze withdraw 24 jam (ditegakkan di sini, dipicu event identity).
- Fee withdraw: per aset (menutup network fee + margin), di-review berkala vs fee jaringan aktual; fase lanjut: estimasi dinamis dari fee jaringan real-time (update otomatis dengan batas atas-bawah).
- **Gas/fueling:** withdraw token (ERC-20 dsb) butuh native coin (ETH) untuk gas — konfirmasi ke Fystack siapa yang menyediakan & top-up-nya. Kalau tanggung jawab kita: monitoring saldo gas per chain + alarm menipis (withdraw macet gara-gara kehabisan gas = insiden memalukan yang umum).

### F4. Rekonsiliasi (harian, otomatis)
```
Untuk tiap aset:
  ledger_total  = Σ user balances + kyra fee accounts (internal)
  custody_total = Fystack API balance
  pending       = withdraw BROADCASTING + deposit DETECTED belum credit
  assert |custody_total − ledger_total − pending_adjustment| == 0
```
- Selisih ≠ 0 → alarm CRITICAL + laporan otomatis + freeze withdraw aset tsb sampai dijelaskan. Auto-fix DILARANG.
- Hasil rekonsiliasi disimpan (bukti audit).

### F5. Treasury visibility (admin)
- Dashboard: saldo per aset di Fystack, pending in/out, fee terkumpul, riwayat rekonsiliasi, saldo gas per chain.

### F6. Proof of Reserves (fase 5 — detail di 16-ops-security)
- Snapshot ledger → Merkle tree liabilities + bukti saldo custody ≥ liabilities, publikasi bulanan.

## ⚠️ Catatan Regulasi Custody (verifikasi dengan konsultan hukum)
Rezim PFAK/OJK kemungkinan MEWAJIBKAN penyimpanan aset di **kustodian terdaftar Indonesia** (ekosistem CFX: kustodian ICC, kliring KKI). Fystack saja mungkin tidak memenuhi syarat lisensi — skenario realistis: mayoritas aset di kustodian lokal wajib + Fystack sebagai hot wallet operasional. Interface `CustodyProvider` mendukung multi-provider justru karena ini. **Klarifikasi SEBELUM menandatangani kontrak Fystack.**

## Data Model (schema `wallet`)

```sql
deposit_addresses(user_id, asset, chain, address, fystack_ref, created_at,
                  UNIQUE(user_id, asset, chain), UNIQUE(address, chain))
deposits(id ULID, user_id, asset, chain, txid, output_index, amount,
         confirmations, status ENUM(DETECTED,CONFIRMED,CREDITED,HELD,IGNORED_DUST),
         credited_journal_ref, detected_at, credited_at,
         UNIQUE(txid, output_index))
withdrawals(id ULID, user_id, asset, chain, to_address, amount, fee,
            status ENUM(PENDING_REVIEW,APPROVED,REJECTED,BROADCASTING,
                        COMPLETED,FAILED),
            hold_journal_ref, txid, reviewed_by, requested_at, completed_at)
address_whitelist(user_id, asset, chain, address, label, added_at, activated_at)
            -- aktif 24 jam setelah ditambah (anti-hack)
reconciliations(id, asset, at, ledger_total, custody_total, pending, delta, status)
```

## Domain Events
- `DepositDetected/DepositCredited {user, asset, amount}` → notification, WS
- `WithdrawRequested/Approved/Completed/Failed` → notification, audit
- `ReconciliationFailed {asset, delta}` → alarm, admin

## Edge Cases
- Deposit token yang tidak didukung ke address kami → tercatat di Fystack, tidak dikredit; kebijakan recovery manual (fee ditanggung user).
- Fystack API down saat withdraw APPROVED → retry dengan backoff + idempotency key; stuck > T → alarm, JANGAN kirim ulang tanpa idempotency (double-spend risk).
- Webhook datang dua kali / out-of-order → idempotent by (txid, output_index) & state machine hanya maju.
- User request withdraw lalu saldo dipakai trading (race) → hold di langkah 4 atomik; gagal hold = reject.
- Memo/tag chains (XRP, dsb) → field memo wajib divalidasi; salah memo = dana user hilang → double-confirm di UI.

## Testing
- Simulasi provider (mock Fystack): semua alur happy + setiap failure mode (timeout, webhook duplikat, out-of-order, partial).
- State machine test: transisi ilegal ditolak.
- Rekonsiliasi test: inject selisih → alarm terpicu + withdraw freeze.
- E2E testnet: sebelum go-live WAJIB uji deposit/withdraw asli di testnet chain + amount kecil di mainnet.
