#!/bin/bash
# Runs once, when the Postgres data volume is empty.
# Database per service: each service gets its own database and user, and cannot connect to the others.
set -euo pipefail

create_database() {
  local db=$1 user=$2 password=$3
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
	CREATE USER ${user} WITH PASSWORD '${password}';
	CREATE DATABASE ${db} OWNER ${user};
	REVOKE ALL ON DATABASE ${db} FROM PUBLIC;
	SQL
}

create_database wallet_db   wallet_user   "${WALLET_DB_PASSWORD}"
create_database transfer_db transfer_user "${TRANSFER_DB_PASSWORD}"
create_database ledger_db   ledger_user   "${LEDGER_DB_PASSWORD}"
