#!/usr/bin/env bash
# Restore a MySQL backup created by backup.sh.
#
# Usage:
#   ./restore.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz
#   FORCE=1 ./restore.sh <file>   # non-interactive (CI/emergency only)
#
# This script is never called automatically by deploy.sh or the CD workflow.
# It overwrites the current database contents. A confirmation is required
# unless FORCE=1. Database volumes are never deleted.
set -euo pipefail
umask 077

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

log()  { printf '==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; }

if [[ $# -lt 1 ]]; then
  fail "Missing backup file."
  echo "Usage: ./restore.sh backups/sis_YYYYMMDDTHHMMSSZ.sql.gz" >&2
  echo "Available backups:" >&2
  find "${BACKUP_DIR:-$DEPLOY_DIR/backups}" -maxdepth 1 -type f -name 'sis_*.sql.gz' -printf '  %p\n' 2>/dev/null | sort || true
  exit 1
fi

BACKUP_FILE="$1"
[[ -f "$BACKUP_FILE" ]] || { fail "Backup file not found: $BACKUP_FILE"; exit 1; }
[[ -f .env ]] || { fail ".env is missing in ${DEPLOY_DIR}."; exit 1; }
[[ -f "$COMPOSE_FILE" ]] || { fail "${COMPOSE_FILE} is missing in ${DEPLOY_DIR}."; exit 1; }

# docker compose validates the whole file even for "exec", so provide the
# image variables that docker-compose.prod.yml requires.
IMAGE_TAG_IN="${IMAGE_TAG:-}"
if [[ -z "$IMAGE_TAG_IN" && -f .current_tag ]]; then
  IMAGE_TAG_IN="$(tr -d '\r\n' < .current_tag)"
fi
if [[ -f .images.env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.images.env
  set +a
fi
export IMAGE_TAG="${IMAGE_TAG_IN:-${IMAGE_TAG:-latest}}"

read_env_value() {
  local key="$1"
  local value
  value="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '\r' || true)"
  value="${value#\"}"; value="${value%\"}"
  value="${value#\'}"; value="${value%\'}"
  printf '%s' "$value"
}
DB_NAME="${DB_NAME:-$(read_env_value DB_NAME)}"
DB_NAME="${DB_NAME:-sis_db}"

log "Checking backup integrity: $BACKUP_FILE"
gzip -t "$BACKUP_FILE" || { fail "Invalid gzip backup file: $BACKUP_FILE"; exit 1; }

if ! docker compose -f "$COMPOSE_FILE" ps --status running --services 2>/dev/null | grep -qx 'mysql'; then
  fail "MySQL container is not running. Start it before restoring."
  exit 1
fi

cat >&2 <<EOF

WARNING: this will overwrite the contents of database '${DB_NAME}'.
         Current data will be replaced by ${BACKUP_FILE}.
         The application containers will be stopped during the restore.
         The MySQL volume itself is not deleted.

EOF

if [[ "${FORCE:-0}" != "1" ]]; then
  read -r -p "Type the database name (${DB_NAME}) to continue: " answer
  [[ "$answer" == "$DB_NAME" ]] || { fail "Confirmation did not match. Restore aborted."; exit 1; }
fi

log "Stopping backend and frontend"
docker compose -f "$COMPOSE_FILE" --env-file .env stop frontend backend

log "Restoring into database ${DB_NAME}"
if ! gunzip -c "$BACKUP_FILE" | docker compose -f "$COMPOSE_FILE" --env-file .env exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 "$MYSQL_DATABASE"'; then
  fail "Restore failed. The MySQL container is still running; inspect it before restarting the app."
  exit 1
fi

log "Starting backend and frontend"
docker compose -f "$COMPOSE_FILE" --env-file .env start backend frontend
docker compose -f "$COMPOSE_FILE" --env-file .env ps

log "Restore completed. Verify the application and data before continuing."
