# 17 — Observability (Logging, Metrics, Tracing)

> Tiga pilar + standar yang mengikat semuanya. Tanpa correlation, debugging insiden prod = menebak. Ini spesifikasi wajib sejak fase 0 — retrofit observability ke sistem jalan itu 10x lebih mahal.

## Arsitektur (LGTM stack, self-hosted, koheren satu vendor Grafana)

```
Quarkus app ──► OpenTelemetry SDK (built-in Quarkus) — standar & protokol, vendor-neutral
   │ logs (JSON, stdout)
   │ metrics (Micrometer /q/metrics)
   │ traces (OTLP gRPC)
   ▼
Grafana Alloy (SATU agent per VPS — distribusi OTel Collector milik Grafana):
   kumpul log container → Loki          (menggantikan Promtail)
   scrape metrics app + exporters → remote_write Prometheus
   terima OTLP traces + tail sampling → Tempo
   ▼
Grafana (satu UI: log ↔ metric ↔ trace saling loncat via trace_id & exemplars)
Alertmanager → Telegram/call routing (page vs ticket, lihat 16)
```

Kenapa LGTM + Alloy: satu ekosistem, korelasi antar pilar native (klik log → trace → metric), satu agent binary per VPS untuk tiga pilar, ringan, gratis. Catatan penting: **OTel ≠ alternatif Alloy** — OTel = standar/SDK di sisi app (tetap dipakai apapun collectornya), Alloy = collector distribution. App bicara OTLP → ganti backend/collector kapanpun tanpa sentuh kode. Alternatif SaaS (Datadog dsb) mahal & data keluar — tidak untuk fase awal.

## 1. Distributed Tracing (OpenTelemetry)

### Standar
- **W3C Trace Context** (`traceparent` header) — standar propagasi resmi, default OTel.
- Instrumentasi: `quarkus-opentelemetry` (auto-instrument REST server/client, JDBC, scheduler). Vert.x/CDI event antar modul → propagasi context manual via event envelope (field `trace_context`) — WAJIB, kalau tidak trace putus di batas modul.
- Semantic conventions: **OTel semconv** untuk nama span & attribute (`http.request.method`, `db.system`, dst) + namespace custom `kyra.*` (`kyra.order.id`, `kyra.pair`, `kyra.user.id`).

### Alur yang wajib ter-trace end-to-end
```
REST intake → validasi market → risk check → hold ledger (DB span)
  → enqueue matching [SPAN LINK, bukan parent-child]
  → matching process (span per command)
  → settlement batch [span link ke N trade] → journal DB → outbox
  → WS publish
```
- **Matching hot path:** JANGAN full-trace tiap order di dalam engine loop (overhead). Pola: span per command dengan sampling; event log matching sudah jadi audit trail lengkapnya. Batch settlement = satu span dengan `span links` ke trace order asal (fan-in pattern OTel).
- Async boundary (queue, outbox, poll deposit detection) = **span link**, bukan parent-child — supaya durasi span tidak bohong.

### Sampling
- Fase awal: 100% (traffic kecil, data emas buat debugging).
- Naik volume: **tail-based sampling** di collector (simpan: semua error, semua latency > p95, semua yang menyentuh withdraw/settlement; sample 10% sisanya). Jalur DANA (withdraw, settlement, rekonsiliasi) SELALU 100%.

### Ekspor
- OTLP gRPC → Alloy (buffer, retry, tail-sampling via komponen `otelcol.*`) → Tempo. Alloy = satu titik kontrol pipeline (redaction attribute PII juga di sini).

## 2. Logging (standar wajib, ditegakkan dari fase 0)

### Format: JSON structured, satu baris per event, stdout (container-friendly)
Field wajib setiap baris:

```json
{
  "ts": "2026-07-17T10:15:30.123Z",        // ISO-8601 / RFC 3339, UTC, ms
  "level": "INFO|WARN|ERROR",
  "service": "kyra-exchange",
  "module": "order",                        // modul asal
  "logger": "class",
  "trace_id": "...", "span_id": "...",     // korelasi ke Tempo — WAJIB terisi di request path
  "event": "order.placed",                  // machine-readable event name (snake, namespaced)
  "message": "human readable",
  "user_id": "01H...",                      // ULID internal — BUKAN email/nama
  ...context fields...
}
```

- Implementasi: `quarkus-logging-json` + MDC (trace_id/span_id auto dari OTel, user_id di-set filter auth).
- Naming attribute ikut **OTel semantic conventions** + `kyra.*` — SAMA dengan tracing (satu kosakata di log & trace).

### Aturan isi (ditegakkan code review + test)
1. **DILARANG di log:** password, token, secret, private key, TOTP, PII mentah (email, nama, KTP, address crypto tujuan lengkap — boleh prefix 6 char + "…"). CI test: regex scanner atas output log test suite.
2. `user_id` internal boleh (ULID, bukan PII langsung); email TIDAK.
3. Level policy: `ERROR` = butuh tindakan/investigasi (alert-able); `WARN` = anomali survivable; `INFO` = event bisnis penting (order placed, withdraw approved); `DEBUG` = off di prod (bisa dinyalakan per-logger runtime via config).
4. Log = observasi. **Audit log = record hukum** — tabel DB immutable (sudah didesain per modul), BUKAN dari Loki. Jangan campur.

### Retensi
- Loki: hot 30 hari, archive object storage 13 bulan.
- Audit log DB: ikut regulasi (≥5 tahun) — bagian backup DB, bukan Loki.

## 3. Metrics (Micrometer → Prometheus)

### Konvensi
- Prometheus naming: `kyra_<modul>_<nama>_<unit>` (`kyra_matching_orders_total`, `kyra_settlement_lag_seconds`).
- Label cardinality DIJAGA: `pair`, `side`, `status` boleh; `user_id`, `order_id` DILARANG sebagai label (cardinality explosion — itu urusan log/trace).
- **Exemplars** aktif: metric latency membawa contoh trace_id → klik histogram di Grafana loncat ke trace.

### Katalog metric minimum

**Teknis (RED per endpoint + USE per resource):**
- HTTP: rate, error rate, duration histogram per route (auto Micrometer)
- `kyra_matching_queue_depth{pair}`, `kyra_matching_command_duration_seconds`
- `kyra_settlement_lag_seconds{pair}` ← alert page
- `kyra_outbox_unpublished_count`, `kyra_ws_connections`, `kyra_ws_dropped_slow_consumers_total`
- JVM (heap, GC pause), DB pool (Agroal), Valkey latency

**Bisnis (dashboard + anomali):**
- `kyra_orders_placed_total{pair,type}`, `kyra_trades_total{pair}`, `kyra_trade_volume_quote{pair}`
- `kyra_deposits_credited_total{asset}`, `kyra_withdrawals_total{asset,status}`
- `kyra_reconciliation_delta{asset}` ← page bila ≠ 0
- `kyra_balance_drift_detected_total` ← page
- `kyra_mm_spread{pair}`, `kyra_mm_inventory{asset}` (fase 5)

## 4. Infra Monitoring (exporter lengkap — yang tadinya bolong)

| Target | Exporter |
|---|---|
| Host VPS (CPU/disk/net/mem) | node_exporter |
| Container | cAdvisor |
| PostgreSQL (replication lag!, connections, bloat) | postgres_exporter |
| Valkey | redis_exporter |
| Uptime dari LUAR network (blackbox HTTP/TCP probe API+WS) | blackbox_exporter + probe eksternal pihak ketiga (UptimeRobot dsb — kalau VPS mati semua, tetap ada yang teriak) |
| TLS cert expiry | blackbox (sudah di alert 16) |
| Backup job sukses/gagal | pushgateway / textfile collector |
| Custody node/RPC health (latency, error rate dari sisi kita) | metric klien custom |

Prometheus + Alertmanager + Grafana + Loki + Tempo di VPS-3 (docker compose); Alloy = agent di TIAP VPS (kumpul lokal → kirim ke VPS-3). Grafana di belakang VPN/IP allowlist — dashboard exchange = intel buat penyerang.

## 5. SLI / SLO (dasar alert & error budget)

| SLI | SLO awal |
|---|---|
| API availability (blackbox eksternal) | 99.9% / 30 hari |
| Order ACK latency | p99 < 100ms |
| WS event delivery | p99 < 500ms |
| Settlement lag | p99 < 5s |
| Deposit credit setelah konfirmasi ke-N | p95 < 60s |
| Withdraw processing (auto-approved) | p95 < 10 menit |

Error budget habis → freeze fitur, fokus stabilitas (aturan tim, tulis di 16).

## 6. Standar & Sertifikasi (jawaban "perlu ISO apa?")

**Dipakai sebagai spesifikasi teknis (gratis, langsung):**
- **ISO 8601 / RFC 3339** — semua timestamp log/dokumen (API publik pakai epoch ms, lihat README §8)
- **W3C Trace Context** — propagasi tracing
- **OTel Semantic Conventions** — penamaan attribute log/trace/metric
- **RFC 5424 severity** — semantik level log
- **OWASP ASVS L2** — checklist verifikasi keamanan aplikasi (dipakai pentest fase 4); target L3 untuk komponen withdraw/auth
- **CIS Benchmarks** — hardening OS/Docker/Postgres di VPS

**Sertifikasi organisasi (roadmap, bukan sekarang):**
- **ISO/IEC 27001** (ISMS) — target SETELAH launch stabil (fase 5+). Regulator/partner/bank sering minta. Desain kita (audit log, access control, DR drill, offboarding) sudah searah kontrolnya — dokumentasikan sejak sekarang biar audit nanti murah.
- **SOC 2 Type II** — alternatif/komplemen, relevan kalau target partner internasional.
- **PCI-DSS: TIDAK berlaku** (tidak proses kartu). **ISO 20022: TIDAK relevan** (messaging pembayaran antar bank).

## 7. Checklist Fase 0 (revisi — menggantikan baris "Observability baseline" lama)
- [ ] quarkus-opentelemetry + Alloy + Tempo jalan; trace REST→DB terlihat di Grafana
- [ ] quarkus-logging-json + field wajib (trace_id, module, event) + MDC
- [ ] Propagasi trace context di event envelope antar modul (test: trace nyambung lintas modul)
- [ ] Prometheus scrape app + node/postgres/redis/blackbox/cAdvisor exporter
- [ ] Grafana: datasource Loki+Tempo+Prometheus terhubung, korelasi log↔trace jalan (klik trace_id di log → buka trace)
- [ ] Alertmanager routing page vs ticket (katalog di 16)
- [ ] CI: log scanner anti-secret/PII di output test

## Testing
- Trace continuity test: satu request place-order → assert satu trace_id muncul di span REST, DB, matching command, settlement (integration test baca dari collector in-memory exporter).
- Log schema test: semua log entry test suite valid terhadap JSON schema field wajib.
- Alert rule test: `promtool test rules` untuk semua alert (unit test alert = alert beneran nyala saat kondisinya).
