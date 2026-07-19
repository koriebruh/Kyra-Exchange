# 08 — Wallet (Custody: web3j self-custody + OpenBao)

> Titik kontak satu-satunya dengan blockchain, lewat **web3j** ke node EVM (JSON-RPC). Kunci di-derive dari satu HD seed yang disimpan di **OpenBao** (fork open-source Vault). Fase 4 — sejak modul ini live, bug = kehilangan uang asli.

## Tujuan
Mengelola deposit & withdraw crypto user lewat web3j + node RPC, menjaga ledger internal selalu sinkron dengan saldo custody on-chain. **Self-custody: kita memegang private key** (HD wallet), seed terenkripsi di OpenBao — bukan MPC/vendor. Interface `CustodyProvider` memungkinkan swap ke MPC vendor (Fireblocks/BitGo) kelak tanpa ubah logic.

## Prinsip
1. Ledger internal = kebenaran saldo user; on-chain = kebenaran aset. Keduanya WAJIB rekonsiliasi harian.
2. Deposit dikredit HANYA setelah `min_confirmations` dari node. Tidak ada webhook vendor untuk dipercaya — kita **pantau chain sendiri** (poll node/indexer), idempotent by txid.
3. Withdraw = jalur paling diserang di exchange manapun → defense berlapis (2FA, whitelist, limit, approval, delay).
4. Di balik interface `CustodyProvider` — ganti/tambah provider (mis. MPC vendor) = implementasi baru, bukan refactor. **Keamanan kunci (hot/cold split, unseal OpenBao prod, backup seed) adalah tanggung jawab operator** — konsekuensi self-custody.

## Fitur Detail

### F1. Deposit address
- Per user: HD address dari seed pada path BIP44 `m/44'/60'/0'/0/index`, index stabil per user (`wallet.hd_index`) → address reproducible & atribusi deposit jelas.
- Address ditampilkan dengan QR + peringatan chain (kirim BTC ke address ETH = hilang).
- Address reuse: satu address per user permanen (standar CEX).

### F2. Alur Deposit
```
1. Poller/indexer: pantau incoming tx ke address custody {txid, address, asset, amount, confirmations}
2. Idempotency by (txid, output_index) — aman diproses berulang
3. Insert deposits status DETECTED (0 conf) → tampil "pending" di UI
4. Tiap update confirmations → update; capai min_confirmations (per aset, dari market)
   → status CONFIRMED → journal DEPOSIT (external → user:main) → BalanceChanged
5. compliance hook: screening address sumber (AML) SEBELUM credit;
   flagged → status HELD + case ke admin, dana tidak masuk saldo user
```
- Deposit di bawah minimum (dust) → tercatat, tidak dikredit (kebijakan: akumulasi sampai lewat minimum, atau abaikan — putuskan & dokumentasikan).
- Reorg chain: confirmations turun / tx hilang → deposit CONFIRMED tidak pernah di-rollback otomatis (min_confirmations dipilih agar praktis mustahil); kejadian nyata = insiden manual.
- **Deteksi = polling node/indexer (WAJIB):** karena self-custody tidak punya webhook vendor, kita tarik saldo/transaksi tiap interval, diff dengan tabel lokal → tx tak dikenal diproses (idempotent by txid → aman dobel). Withdraw BROADCASTING juga di-poll statusnya (receipt). Detail di 18-data-protection §A4.

### F3. Alur Withdraw (paling kritis)
```
1. Request: user pilih aset, address tujuan, amount. Wajib: 2FA TOTP + (jika aktif) address whitelist
2. Validasi: saldo cukup, amount ≥ min_withdraw, format address valid per chain
3. RiskApi.checkWithdraw: limit harian per user/level KYC, velocity, skor anomali
4. AccountApi.hold(user, asset, amount + fee_withdraw)  → status PENDING_REVIEW
5. Keputusan:
   - amount < auto_threshold & risk lolos → auto-approve
   - selain itu → antrian approval admin (4-eyes untuk nominal besar)
6. APPROVED → web3j: build + sign (secp256k1, seed dari OpenBao) + broadcast dari hot wallet;
   idempotent by withdraw_id (txHash direkam di wallet.web3j_withdrawal → retry aman)
   → status BROADCASTING → simpan txid
7. Poll receipt on-chain → status COMPLETED
   → journal WITHDRAW (user:hold → external) final
8. REJECTED/FAILED → release hold + notifikasi + alasan
```
- Delay keamanan: withdraw ke address BARU (bukan whitelist) → tahan 24 jam (dengan opsi user membatalkan) — mitigasi akun ke-hack.
- Perubahan keamanan akun (reset password/2FA/email) → freeze withdraw 24 jam (ditegakkan di sini, dipicu event identity).
- Fee withdraw: per aset (menutup network fee + margin), di-review berkala vs fee jaringan aktual; fase lanjut: estimasi dinamis dari fee jaringan real-time.
- **Gas/fueling:** withdraw token (ERC-20 dsb) butuh native coin (ETH) untuk gas. Self-custody → tanggung jawab kita: monitoring saldo native hot wallet per chain + alarm menipis (withdraw macet gara-gara kehabisan gas = insiden memalukan yang umum).

### F4. Rekonsiliasi (harian, otomatis)
```
Untuk tiap aset:
  ledger_total  = Σ user balances + kyra fee accounts (internal)
  custody_total = Σ saldo on-chain (address user + hot wallet) via web3j
  pending       = withdraw BROADCASTING + deposit DETECTED belum credit
  assert |custody_total − ledger_total − pending_adjustment| == 0
```
- Selisih ≠ 0 → alarm CRITICAL + laporan otomatis + freeze withdraw aset tsb sampai dijelaskan. Auto-fix DILARANG.
- Hasil rekonsiliasi disimpan (bukti audit).

### F5. Treasury visibility (admin)
- Dashboard: saldo per aset on-chain (hot + agregat deposit address), pending in/out, fee terkumpul, riwayat rekonsiliasi, saldo native (gas) per chain.

### F6. Proof of Reserves (fase 5 — detail di 16-ops-security)
- Snapshot ledger → Merkle tree liabilities + bukti saldo custody ≥ liabilities, publikasi bulanan.

## ⚠️ Catatan Regulasi Custody (verifikasi dengan konsultan hukum)
Rezim PFAK/OJK kemungkinan MEWAJIBKAN penyimpanan aset di **kustodian terdaftar Indonesia** (ekosistem CFX: kustodian ICC, kliring KKI). Self-custody sendiri mungkin tidak memenuhi syarat lisensi — skenario realistis: mayoritas aset di kustodian lokal wajib (cold) + self-custody web3j sebagai hot wallet operasional. Interface `CustodyProvider` mendukung multi-provider justru karena ini. **Klarifikasi SEBELUM go-live.**

## Data Model (schema `wallet`)

```sql
deposit_addresses(user_id, asset, chain, address, custody_ref, created_at,
                  UNIQUE(user_id, asset, chain), UNIQUE(address, chain))
hd_index(user_id PK, idx BIGINT UNIQUE, created_at)   -- index HD stabil per user (V802)
web3j_withdrawal(withdraw_id PK, tx_hash, created_at) -- idempotency broadcast (V802)
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
- Deposit token yang tidak didukung ke address kami → tercatat on-chain, tidak dikredit; kebijakan recovery manual (fee ditanggung user).
- Node/RPC down saat withdraw APPROVED → retry dengan backoff + idempotency (withdraw_id → txHash direkam); stuck > T → alarm, JANGAN broadcast ulang tanpa cek record (double-spend risk).
- Tx deposit terdeteksi dua kali / out-of-order → idempotent by (txid, output_index) & state machine hanya maju.
- User request withdraw lalu saldo dipakai trading (race) → hold di langkah 4 atomik; gagal hold = reject.
- Memo/tag chains (XRP, dsb) → field memo wajib divalidasi; salah memo = dana user hilang → double-confirm di UI.

## Testing
- Mock custody (`MockCustodyProvider`): semua alur happy + setiap failure mode.
- web3j provider (`Web3jVaultCustodyProvider`): diuji end-to-end lawan Anvil + OpenBao asli (`Web3jCustodyLiveTest`) — HD address, balance, sign+broadcast+mine, idempotency, seed round-trip.
- State machine test: transisi ilegal ditolak.
- Rekonsiliasi test: inject selisih → alarm terpicu + withdraw freeze.
- E2E testnet: sebelum go-live WAJIB uji deposit/withdraw asli di testnet chain + amount kecil di mainnet.

## Status implementasi
- Custody terpilih: **web3j self-custody + OpenBao** (native coin teruji). Sisa buat prod: ERC-20 (USDT), deteksi deposit (poller), atomicity broadcast↔record, keamanan kunci prod (hot/cold, unseal), sweep+rekonsiliasi — lihat TECHDEBT.
