# Optional: Debezium CDC instead of the polling relay

> Not part of the default setup and not covered by the automated tests. This is the week 3 day 7
> stretch exercise from `specs/implementation-plan.md`.

The services publish their outbox with a polling relay (`OutboxRelay`, `SELECT ... FOR UPDATE SKIP LOCKED`).
Debezium can do the same job by reading PostgreSQL's WAL, with lower latency and no polling queries.

Steps:

1. Start PostgreSQL with logical decoding: add `command: ["postgres", "-c", "wal_level=logical"]` to the
   `postgres` service in `infra/docker-compose.yml`, and grant replication to the service user:
   `ALTER USER transfer_user WITH REPLICATION;`
2. Add a Kafka Connect container with the Debezium PostgreSQL connector (image `quay.io/debezium/connect`).
3. Turn the polling relay off in the service, otherwise every event is published twice:
   `PAYNEST_OUTBOX_RELAY_ENABLED=false`.
4. Register the connector: `curl -X POST -H 'Content-Type: application/json' --data @transfer-outbox-connector.json http://localhost:8083/connectors`
   (use the Connect REST port you mapped).

The outbox table already has Debezium's default column names (`id`, `aggregatetype`, `aggregateid`, `type`,
`payload`). The extra `topic` column is used for routing (`route.by.field=topic`).
Because Debezium does not set `published_at`, delete old outbox rows with a scheduled job.

Docs: https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
