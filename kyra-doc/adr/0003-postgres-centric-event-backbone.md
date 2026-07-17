# ADR 0003 — Postgres-centric Event Backbone (no broker yet)

Status: accepted · Date: 2026-07-17

## Context
Domain events perlu durable & terurut (audit, settlement, marketdata). Pilihan: Kafka penuh, Redpanda, atau Postgres outbox tanpa broker.

## Decision
Fase awal: outbox table di Postgres, dalam transaksi yang sama dengan mutasi data; konsumsi in-process (CDI events / poller). Interface `EventPublisher` diabstraksi dari hari 1 — pindah ke Redpanda/Kafka nanti = implementasi baru, bukan refactor. Envelope event membawa `trace_context` (W3C) untuk kesinambungan tracing.

Alternatif ditolak: Kafka/Redpanda sekarang (satu sistem lagi untuk diops, monolith belum membutuhkannya).

## Consequences
Ops minimal, konsistensi transaksional gratis. Batas: throughput event = throughput Postgres — cukup jauh untuk fase awal; revisit saat pecah service atau > ~5k event/s sustained.
