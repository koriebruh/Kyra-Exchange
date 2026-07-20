# Go-live readiness — honest gap list

**Verdict: NOT ready for production.** The backend **core engine** (matching,
ledger, order, settlement, custody-web3j, identity, derivatives) is built and
tested (see [STATUS.md](STATUS.md), 201 tests green). But a real, regulated
Indonesian crypto exchange handling real money needs far more. This is the full
list of what is still missing, by severity.

Severity: 🔴 **BLOCKER** (cannot legally/technically launch) · 🟠 **CRITICAL**
(unsafe to launch without) · 🟡 **IMPORTANT** (needed soon after / for scale).

Rough estimate: what exists ≈ the trading-engine core. Remaining go-live work
(legal, real vendors, fiat, frontend, prod hardening) is the **majority** of the
total effort.

---

## 1. Legal / Regulatory (Indonesia) — mostly non-technical, hard blockers
- 🔴 **PFAK / OJK licence** (Pedagang Fisik Aset Kripto). Operating without it is illegal. Transition Bappebti→OJK.
- 🔴 **PT entity + paid-up capital** — regulator sets a large minimum (puluhan miliar IDR) + escrow.
- 🔴 **Registered local custodian** (kustodian terdaftar) + clearing (KKI/ICH) membership — self-custody alone may not satisfy the licence.
- 🔴 **Tax (module 15)**: PPh final 0.1% + PPN 0.11% per transaction — WITHHOLD per trade + report/setor to DJP. IDR conversion (kurs pajak). Legal-blocked, not built.
- 🔴 **AML/CFT program + PPATK** reporting (LTKM/LTKT), Travel Rule for VASP withdrawals.
- 🟠 **UU PDP** (data protection) compliance; ToS, privacy policy, risk disclosure, user agreements (legal-reviewed).

## 2. Custody / Wallet — money-safety, technical blockers
- 🔴 **ERC-20 tokens (USDT)** — the exchange is USDT-quoted but custody does **native coin only**. Cannot accept one real USDT deposit. `transfer()`/`balanceOf` per token contract. (TECHDEBT)
- 🔴 **Multi-chain** — real users deposit USDT on TRON/BSC/ETH, plus BTC etc. One EVM chain is not enough.
- 🔴 **Deposit detection** — poller/indexer of incoming tx per address. Not built (mock).
- 🔴 **Production key security** — OpenBao **prod mode** (not dev in-memory), auto-unseal (HSM/cloud KMS), seed backup + recovery ceremony, key rotation. Currently dev root token.
- 🟠 **Hot/cold split + cold storage** (multisig/MPC or hardware), sweep deposits→hot, gas/fueling for token withdrawals + low-balance alarms.
- 🟠 **Withdrawal address whitelist + 24h delay** (spec'd, not built), broadcast/confirmation monitoring.
- 🟠 **Automated scheduled reconciliation** ledger↔on-chain + alarms (logic exists; not scheduled/wired).

## 3. Compliance vendors — real integrations
- 🔴 **Real KYC** (e-KTP/Dukcapil + liveness): Privy / Verihubs / VIDA. Mock now.
- 🔴 **Real AML / sanctions / on-chain screening**: Chainalysis / Elliptic / TRM. Mock now.
- 🟠 **Transaction monitoring** + SAR/STR, PEP screening, sanctions-list refresh.

## 4. Fiat rails (IDR) — critical for an Indonesian exchange
- 🔴 **IDR on/off-ramp** — bank transfer / Virtual Account / QRIS deposit & withdraw. **Not built at all.** Users fund in Rupiah.
- 🟠 Payment-gateway integration + settlement/recon with banks.

## 5. Product / Trading completeness
- 🟠 **MARKET orders** — only LIMIT built (engine needs quote-budget for market-buy).
- 🟠 **STOP / STOP-LIMIT / OCO** trigger orders; post-only, reduce-only.
- 🟠 **Real price / index feed** — mock now; needed for derivatives mark price, liquidation, funding premium.
- 🟠 **Derivatives**: ADL, cross/isolated margin, position/leverage limits, risk tiers.
- 🟡 **Fee tiers / VIP / maker rebates** (flat frozen rate now); listing/delisting workflow.

## 6. Frontend / Client — none exists (backend-only)
- 🔴 **Web frontend** — trading UI, charts, order book, wallet, KYC onboarding. Nothing built.
- 🟠 **Admin backoffice UI** (REST exists, no UI); mobile apps.
- 🟠 **Private WebSocket streams** (user order/balance/position updates) — only public trade stream built.
- 🟡 Captcha widget (Turnstile), anti-phishing rendering in emails, passkeys.

## 7. Notifications / Comms
- 🟠 **Real email relay** (SES/Postmark) + SPF/DKIM/DMARC on domain. Mailpit is dev-only.
- 🟠 **Notification producers** — only register-verification wired. Missing: deposit, withdrawal, login-alert, password/2FA change, liquidation.
- 🟡 SMS / push for 2FA + alerts; status page.

## 8. Security hardening (pre-launch)
- 🔴 **External penetration test + security audit** before launch. Not done.
- 🟠 WAF + DDoS (Cloudflare), bot/fraud detection, withdrawal-2FA enforcement, captcha.
- 🟠 Velocity limits (Valkey), suspicious-login/device detection, session hardening.
- 🟡 Bug bounty program; incident-response drills (playbooks spec'd).

## 9. Operations / Infra (production)
- 🔴 **Prod deployment + HA** — currently single-node dev. Multi-node, failover, TLS/domain/DNS.
- 🔴 **DB backups (PITR) + tested restore + replication + failover procedure**. Not set up.
- 🟠 **Prod observability** — Grafana/Alloy stack, alerting, on-call/PagerDuty, SLOs (dev lgtm only now).
- 🟠 **CI/CD prod pipeline** — canary/blue-green, rollback, migration safety.
- 🟠 **Load / performance / stress testing** at prod scale (engine throughput, DB).
- 🟠 **DR site**, RPO/RTO targets, offsite backups, DR drills.

## 10. Pre-launch validation (mandatory)
- 🔴 **Full testnet dry-run** — real deposit/withdraw on testnet + small mainnet amounts (spec'd as mandatory, kyra-doc/modules/08 Testing).
- 🟠 End-to-end QA of every money path under failure injection.
- 🟠 Financial reconciliation + accounting sign-off; Proof-of-Reserves publication cadence.

---

## Bottom line
- ✅ **Done + tested:** trading engine, ledger, orders, settlement, market data, identity/2FA/API-keys, compliance gates, admin 4-eyes, derivatives, self-custody web3j (native coin), REST + rate-limit + WS-public.
- 🔴 **Hard blockers before ANY real launch:** OJK/PFAK licence + tax, **ERC-20/multi-chain custody**, deposit detection, prod key security, real KYC/AML, **IDR fiat rails**, **a frontend**, external security audit, prod HA/backups/DR, testnet dry-run.
- The engine is real and solid. Going live is a **program** (legal + vendors + fiat + frontend + ops), not a few features. Do NOT launch on the current build.
