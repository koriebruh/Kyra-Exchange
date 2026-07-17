# ADR 0002 — Modular Monolith on Quarkus (Java 21)

Status: accepted · Date: 2026-07-17

## Context
Exchange butuh banyak subsistem (ledger, matching, wallet, compliance, …) tapi tim kecil. Microservices dari awal = pajak operasional & konsistensi distribusi yang belum perlu; monolith tanpa disiplin = bola lumpur.

## Decision
Satu aplikasi Quarkus, Maven multi-module: `kyra-common` + 14 modul domain (jar) + `kyra-app` (wiring). Batas modul: hanya package `api` yang boleh direferensikan modul lain — ditegakkan ArchUnit di CI. Satu schema Postgres per modul, cross-schema FK dilarang. Komunikasi antar modul: interface API (sinkron) + domain event (asinkron).

Alternatif ditolak: microservices (overhead prematur), monolith satu module (batas tidak ditegakkan compiler/test).

## Consequences
Deploy tunggal & transaksi lintas modul mudah di fase awal; pemisahan ke service terpisah nanti = angkat modul, bukan bedah kode. Harga: disiplin boundary dijaga test, bukan proses terpisah.
