#!/bin/bash
# FutureCRM AI Database Backup Script
# Usage: ./backup.sh
# Create a timestamped backup of the SQLite database file.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DB_FILE="${FUTURECRM_DB:-${SCRIPT_DIR}/futurecrm-ai.db}"
BACKUP_DIR="${SCRIPT_DIR}/backups"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/futurecrm-ai-${TIMESTAMP}.db"

if [ ! -f "$DB_FILE" ]; then
    echo "Error: Database file not found at ${DB_FILE}"
    exit 1
fi

mkdir -p "$BACKUP_DIR"

echo "Backing up ${DB_FILE} ..."
cp "$DB_FILE" "$BACKUP_FILE"
echo "Backup created: ${BACKUP_FILE}"

# Keep only the most recent 30 backups
BACKUP_COUNT=$(ls -1 "$BACKUP_DIR"/futurecrm-ai-*.db 2>/dev/null | wc -l)
if [ "$BACKUP_COUNT" -gt 30 ]; then
    echo "Pruning old backups (keeping latest 30)..."
    ls -1t "$BACKUP_DIR"/futurecrm-ai-*.db | tail -n +31 | xargs rm -f
fi

echo "Done. Total backups: $(ls -1 "$BACKUP_DIR"/futurecrm-ai-*.db 2>/dev/null | wc -l)"
echo ""
echo "To restore: cp ${BACKUP_FILE} ${DB_FILE}"
