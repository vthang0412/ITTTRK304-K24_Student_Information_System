#!/usr/bin/env bash
# Restore a matched MySQL + uploads backup set created by backup.sh.
set -euo pipefail
umask 077

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
UPLOADS_VOLUME="${UPLOADS_VOLUME:-sis_backend_uploads}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

log()  { printf '==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; }

[[ "$UPLOADS_VOLUME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || {
  fail "Invalid uploads volume name."
  exit 1
}

if [[ $# -ne 1 ]]; then
  fail "Provide the database archive from one backup set."
  echo "Usage: ./restore.sh backups/sis_<UTC timestamp>.sql.gz[.gpg]" >&2
  exit 1
fi

DB_ARCHIVE="$1"
[[ -f "$DB_ARCHIVE" ]] || { fail "Database archive not found: $DB_ARCHIVE"; exit 1; }
[[ -f .env ]] || { fail ".env is missing in ${DEPLOY_DIR}."; exit 1; }
[[ -f "$COMPOSE_FILE" ]] || { fail "${COMPOSE_FILE} is missing."; exit 1; }

# Compose validates image variables even for exec/stop/start.
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

case "$DB_ARCHIVE" in
  *.sql.gz.gpg) stem="${DB_ARCHIVE%.sql.gz.gpg}"; encrypted=1 ;;
  *.sql.gz) stem="${DB_ARCHIVE%.sql.gz}"; encrypted=0 ;;
  *) fail "Database archive must end with .sql.gz or .sql.gz.gpg."; exit 1 ;;
esac
UPLOADS_ARCHIVE="${stem}.uploads.tar.gz"
[[ "$encrypted" == "1" ]] && UPLOADS_ARCHIVE="${UPLOADS_ARCHIVE}.gpg"
MANIFEST="${stem}.sha256"
[[ -f "$UPLOADS_ARCHIVE" ]] || { fail "Matching uploads archive not found: $UPLOADS_ARCHIVE"; exit 1; }
[[ -f "$MANIFEST" ]] || { fail "Backup checksum manifest not found: $MANIFEST"; exit 1; }

stream_archive() {
  case "$1" in
    *.gpg) gpg --batch --decrypt "$1" ;;
    *) cat -- "$1" ;;
  esac
}

log "Verifying checksums and both archives"
bash ./verify-backup.sh "$DB_ARCHIVE"

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

if ! docker compose -f "$COMPOSE_FILE" --env-file .env ps --status running --services 2>/dev/null | grep -qx 'mysql'; then
  fail "MySQL container is not running. Restore aborted."
  exit 1
fi
if ! docker volume inspect "$UPLOADS_VOLUME" >/dev/null 2>&1; then
  fail "Uploads volume '${UPLOADS_VOLUME}' does not exist. Restore aborted."
  exit 1
fi
ACTUAL_DB="$(docker compose -f "$COMPOSE_FILE" --env-file .env exec -T mysql printenv MYSQL_DATABASE | tr -d '\r\n')"
if [[ "$ACTUAL_DB" != "$DB_NAME" ]]; then
  fail "DB_NAME does not match the running MySQL container. Restore aborted."
  exit 1
fi

cat >&2 <<EOF

WARNING: this will replace database '${DB_NAME}' and all files in volume '${UPLOADS_VOLUME}'.
         Both archives and checksums were validated before any data is changed.
         A pre-restore backup set will be created before stopping the application.

EOF
if [[ "${FORCE:-0}" != "1" ]]; then
  read -r -p "Type the database name (${DB_NAME}) to continue: " answer
  [[ "$answer" == "$DB_NAME" ]] || { fail "Confirmation did not match. Restore aborted."; exit 1; }
fi

log "Creating a pre-restore backup of database and uploads"
COMPOSE_FILE="$COMPOSE_FILE" UPLOADS_VOLUME="$UPLOADS_VOLUME" bash ./backup.sh || {
  fail "Pre-restore backup failed; nothing has been changed."
  exit 1
}

log "Stopping frontend and backend"
docker compose -f "$COMPOSE_FILE" --env-file .env stop frontend backend

log "Restoring database"
if ! stream_archive "$DB_ARCHIVE" | gzip -dc | docker compose -f "$COMPOSE_FILE" --env-file .env exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE"'; then
  fail "Database restore failed. Application containers remain stopped; inspect the database and pre-restore backup."
  exit 1
fi

log "Replacing uploaded files from the matched archive"
if ! stream_archive "$UPLOADS_ARCHIVE" | docker run --rm -i --network none \
  --mount "type=volume,source=${UPLOADS_VOLUME},target=/data" \
  alpine:3.20 sh -c \
  'set -eu; find /data -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +; tar -xzf - -C /data'; then
  fail "Uploads restore failed. Application containers remain stopped; recover from the pre-restore backup set."
  exit 1
fi

log "Starting backend and frontend"
docker compose -f "$COMPOSE_FILE" --env-file .env start backend frontend
docker compose -f "$COMPOSE_FILE" --env-file .env ps
log "Database and uploads restored from the same backup set."
