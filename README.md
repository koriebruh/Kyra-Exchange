# Kyra Exchange

Production crypto exchange — modular monolith on Quarkus (Java 21).

**Start here:** [`kyra-doc/README.md`](kyra-doc/README.md) — architecture, module index, build phases.
Architecture decisions: [`kyra-doc/adr/`](kyra-doc/adr/).

## Layout

```
kyra-common/     shared kernel: Money, AssetId, PairSymbol, EventEnvelope, Result
modules/<name>/  14 domain modules (api / domain / infra) — boundaries enforced by ArchUnit
kyra-app/        Quarkus application wiring all modules; config, migrations
kyra-doc/        design docs (18 module specs) + ADRs
```

## Dev

```bash
./mvnw verify                                   # build + tests (needs JDK 21, Docker for dev services)
./mvnw -DskipTests install                      # first time only: put modules in local repo
./mvnw -f kyra-app/pom.xml quarkus:dev          # dev mode (auto Postgres+Redis via Dev Services)
docker compose -f docker-compose.dev.yml up     # stable local infra (Postgres + Valkey)
docker compose -f docker-compose.dev.yml --profile obs up   # + Grafana/OTLP at :3000
./mvnw -DskipTests package && docker compose -f docker-compose.dev.yml --profile app up --build   # packaged app
```

Health: `/q/health` · Metrics: `/q/metrics`

## License

Proprietary — see [LICENSE](LICENSE). All rights reserved.
