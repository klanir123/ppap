#!/bin/sh
# Creates one database (and owner role) per component. Names come from POSTGRES_MULTIPLE_DATABASES
# as "db:user:password" triples separated by commas. Runs once on first container start.
set -eu
IFS=','
for entry in ${POSTGRES_MULTIPLE_DATABASES:-}; do
  db=$(echo "$entry" | cut -d: -f1); user=$(echo "$entry" | cut -d: -f2); pass=$(echo "$entry" | cut -d: -f3)
  echo "creating database '$db' owned by '$user'"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
CREATE ROLE "$user" LOGIN PASSWORD '$pass';
CREATE DATABASE "$db" OWNER "$user";
SQL
done
