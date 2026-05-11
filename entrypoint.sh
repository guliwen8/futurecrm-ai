#!/bin/sh
set -e

echo "=== FutureCRM AI Starting ==="
echo "Database path: ${FUTURECRM_DB:-/app/data/futurecrm-ai.db}"

# Ensure the data directory exists
DATA_DIR=$(dirname "${FUTURECRM_DB:-/app/data/futurecrm-ai.db}")
mkdir -p "$DATA_DIR"

# Start the application
exec java -jar /app/app.jar
