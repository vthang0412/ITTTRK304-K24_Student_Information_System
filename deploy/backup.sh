#!/usr/bin/env bash
# Create a compressed MySQL dump before deploying.
# Safe to run when the stack is not running: it exits without an error.
#
# Secrets are read inside the MySQL container from its environment; the
# password is never printed to stdout/stderr or written to the backup file.
set -euo pipefail
umask 077

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

# docker compose validates the whole file even for "exec", so provide the
# image variables that docker-compose.prod.yml requires.
IMAGE_TAG_IN="${IMAGE_TAG:-}"
BACKEND_IMAGE_IN="${BACKEND_IMAGE:-}"
FRONTEND_IMAGE_IN="${FRONTEND_IMAGE:-}"
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
if [[ -n "$BACKEND_IMAGE_IN" ]]; then
  export BACKEND_IMAGE="$BACKEND_IMAGE_IN"
fi
if [[ -n "$FRONTEND_IMAGE_IN" ]]; then
  export FRONTEND_IMAGE="$FRONTEND_IMAGE_IN"
fi

BACKUP_DIR="${BACKUP_DIR:-${DEPLOY_DIR}/backups}"
RETENTION="${BACKUP_RETENTION:-10}"
[[ "$RETENTION" =~ ^[0-9]+$ ]] || RETENTION=10
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

log() { printf '==> %s\n' "$*"; }

if ! docker compose -f "$COMPOSE_FILE" ps --status running --services 2>/dev/null | grep -qx 'mysql'; then
  log "MySQL is not running; skipping database backup (first deploy or stopped stack)."
  exit 0
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${BACKUP_DIR}/sis_${timestamp}.sql.gz"
tmp_file="${backup_file}.tmp"

cleanup() { rm -f "$tmp_file"; }
trap cleanup EXIT

log "Backing up MySQL database to ${backup_file}"
if ! docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers "$MYSQL_DATABASE"' \
  | gzip -c > "$tmp_file"; then
  log "Database backup failed; aborting deployment."
  exit 1
fi

# A valid gzip dump must not be empty and must pass gzip integrity checks.
if [[ ! -s "$tmp_file" ]]; then
  log "Database backup is empty; aborting deployment."
  exit 1
fi
if ! gzip -t "$tmp_file"; then
  log "Database backup is not a valid gzip file; aborting deployment."
  exit 1
fi

chmod 600 "$tmp_file"
mv "$tmp_file" "$backup_file"
chmod 600 "$backup_file"
log "Backup written: ${backup_file} ($(du -h "$backup_file" | cut -f1))"

# Keep the most recent backups. This only prunes old dump files, never volumes.
mapfile -t old_backups < <(
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'sis_*.sql.gz' -printf '%T@ %p\n' \
    | sort -rn | cut -d' ' -f2-
)
for ((i = RETENTION; i < ${#old_backups[@]}; i++)); do
  rm -f "${old_backups[$i]}"
done
