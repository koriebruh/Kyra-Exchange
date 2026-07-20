# Feature status — verified checklist

✅ = implemented **and** covered by passing tests (test class named) **and** confirmed to run.
Anything not ticked is deferred/follow-up (see [TECHDEBT.md](TECHDEBT.md)) — not done.

> **Not production-ready.** This lists what is *built + tested*, not what go-live
> needs. For the full go-live gap list (legal, fiat, ERC-20/multi-chain custody,
> KYC/AML vendors, frontend, prod ops), see **[GO-LIVE.md](GO-LIVE.md)**.

Baseline: full reactor `./mvnw verify` green — **201 tests, 0 fail/error**. Live
end-to-end proofs (beyond unit tests) are listed at the bottom.

## Core / kernel
- [x] Money = BigDecimal, no float/double (enforced) — `MoneyTest`, `ModuleBoundaryTest`
- [x] PairSymbol / AssetId value types — `PairSymbolTest`
- [x] Module boundary: only `.api` crosses modules (ArchUnit) — `ModuleBoundaryTest`
- [x] App boots + health + metrics + Flyway migrate + OpenAPI — `AppSmokeTest`, `OpenApiTest`

## 01 Identity — `identity` (43 tests)
- [x] Register + email verify (single-use token) — `IdentityServiceTest`
- [x] Argon2id password hashing (salt, malformed reject) — `PasswordHasherTest`
- [x] Login + JWT (RS256); suspended/unverified blocked — `IdentityServiceTest`
- [x] Refresh rotation + reuse-detection revokes session family — `IdentityServiceTest`
- [x] Sessions list/revoke; logout — `IdentityServiceTest`
- [x] 2FA TOTP enroll/confirm/disable + recovery codes (RFC-6238 vector) — `TotpServiceTest`, `TwoFactorFlowTest`
- [x] API keys HMAC (scopes, timestamp window, IP whitelist, revoke) — `ApiKeyServiceTest`
- [x] AES-256-GCM at-rest secret encryption (tamper/wrong-key fail) — `CryptoServiceTest`
- [x] Anti-phishing code storage — `IdentityServiceTest`

## 02 Account ledger — `account` (14 tests)
- [x] Double-entry, balanced-journal-only, no negative user balance — `LedgerServiceTest`, `JournalBalancePropertiesTest`
- [x] Hold/release, idempotent post — `LedgerServiceTest`
- [x] Multi-asset atomic settlement journal — `LedgerServiceTest`
- [x] Race-safe concurrent holds (no lost update/overdraw) — `LedgerConcurrencyTest`
- [x] Invariants hold under randomized ops — `LedgerInvariantTest`
- [x] Proof of Reserves (Merkle liabilities + inclusion) — `ProofOfReservesTest`

## 03 Market — `market` (11 tests)
- [x] Asset/pair registry; order-grid validate (tick/step/min-notional/qty) — `MarketServiceTest`
- [x] Status lifecycle + freeze cascade; rules change only while halted — `MarketServiceTest`

## 05 Matching — `matching` (16 tests)
- [x] Deterministic order book: price-time priority — `OrderBookTest`
- [x] IOC/FOK, market order, self-trade prevention, never-crossed — `OrderBookTest`
- [x] Per-pair serialized engine; concurrent submits conserve qty — `MatchingEngineTest`
- [x] Determinism (same commands → same events) — `MatchingDeterminismTest`
- [x] Recovery: restore resting orders with priority — `MatchingEngineTest`

## 06 Settlement — `settlement` (4 tests)
- [x] Trade → one balanced journal, idempotent, value-conserving — `SettlementServiceTest`
- [x] Fee deducted from received, credited to exchange — `SettlementServiceTest`

## 04 Order — `order` (11 tests)
- [x] LIMIT GTC/IOC/FOK: validate → hold → submit → settle → release — `OrderFlowTest`
- [x] Two accounts trade end-to-end; price-improvement release; partial fill — `OrderFlowTest`
- [x] Cancel; frozen-account gate; risk price-band; dup client-order-id idempotent — `OrderFlowTest`
- [x] Book recovery from open orders on startup — `OrderRecoveryTest`

## 07 Market data — `marketdata` (6 tests)
- [x] 1m OHLCV candles + higher-interval aggregation; ticker; depth — `MarketdataServiceTest`

## 09 Risk — `risk` (4 tests)
- [x] checkOrder: max notional + price band — `RiskServiceTest`

## 11 Fee — `fee` (4 tests)
- [x] Maker/taker rate on received; round-up to scale; cap; zero — `FeesTest`

## 08 Wallet + custody — `wallet` (17 tests)
- [x] Deposit address; creditDeposit idempotent by txid — `WalletServiceTest`
- [x] Withdrawal: freeze + KYC-L1 + AML-screening gates; hold+fee — `WalletServiceTest`
- [x] approve/reject/complete/fail; 4-eyes; reconciliation breach alarm — `WalletServiceTest`
- [x] HD wallet derivation (secp256k1, BIP44) vs Anvil accounts — `HdWalletTest`
- [x] **web3j self-custody**: per-user deposit address, on-chain balance, sign+broadcast withdrawal (mines), idempotency — `Web3jCustodyLiveTest` (live Anvil)
- [x] **OpenBao seed store** round-trip — `Web3jCustodyLiveTest` (live OpenBao)
- [x] Runtime provider select (mock | web3j), no rebuild — `CustodyProviderProducer`

## 10 Compliance — `compliance` (5 tests)
- [x] KYC levels (raise-only, never lowered); AML address screening — `ComplianceServiceTest`
- [x] Account freeze/unfreeze — `ComplianceServiceTest`

## 12 Admin — `admin` (5 tests)
- [x] Withdrawal approve/reject with immutable audit; 4-eyes distinct approvers — `AdminServiceTest`
- [x] Freeze/unfreeze user (audited); append-only audit enforced — `AdminServiceTest`

## 13 Notification — `notification` (4 tests)
- [x] Templated email, idempotent by dedupKey, missing-var fails safe — `NotificationServiceTest`
- [x] SMTP delivery (SmtpEmailSender) — verified live to Mailpit (see below)

## 14 Liquidity — `liquidity` (3 tests)
- [x] MM two-sided quotes around reference price; min-notional skip — `LiquidityServiceTest`

## Derivatives (perpetual) — `derivatives` (12 tests)
- [x] open (lock margin), close (mark-price PnL), short — `PerpetualServiceTest`
- [x] Liquidation covered by insurance (user never negative) — `PerpetualServiceTest`
- [x] Funding (long↔short, idempotent by round) — `PerpetualServiceTest`
- [x] Position averaging; partial close (proportional PnL); reduce — `PerpetualServiceTest`
- [x] Ledger conserves value across lifecycle — `PerpetualServiceTest`

## Public API / app (kyra-app, 35 tests)
- [x] REST: /v1/auth, /v1/market, /v1/orders, /v1/admin (ADMIN role), **/v1/wallet** — resource tests
- [x] Withdrawal rejection → 422 (not 500) — `WalletResourceTest`
- [x] Valkey per-IP rate limiting (429 + headers) — `RateLimiterTest`
- [x] Public WebSocket trade stream — `WsStreamTest`
- [x] Audit log append-only (mutation rejected) — `AuditLogTest`
- [x] Uniform ApiError shape; auth guards (401) — resource tests
- [x] App with custody=web3j derives real HD address end-to-end — `WalletWeb3jIntegrationTest`

## Verified LIVE (runs, not only unit-tested)
- [x] **Native executable** — GraalVM native-image ELF (~110 MB), boots ~0.4s, `AppSmokeIT` runs against the binary
- [x] **Email → Mailpit** — register → verification email (with token) lands in inbox
- [x] **web3j custody → Anvil + OpenBao** — deposit address, withdrawal mines on-chain, balance, seed round-trip
- [x] **Full app on custody=web3j** — REST `GET /v1/wallet/address` returns a real `0x…` HD address

---

## NOT done (deferred — see TECHDEBT.md), do not assume working
- [ ] Custody ERC-20 tokens (USDT) — native coin only so far
- [ ] Deposit detector (poll node/indexer)
- [ ] Real vendors: KYC, AML, price feed, SMTP relay (mocks/Mailpit used)
- [ ] Tax (module 15) — legal-blocked
- [ ] Derivatives ADL, cross/isolated margin, real mark-price feed
- [ ] Captcha, passkeys, velocity limits, more notification producers
- [ ] Production key security (hot/cold split, OpenBao prod unseal/HSM), PFAK/OJK licence
