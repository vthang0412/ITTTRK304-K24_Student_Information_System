#!/usr/bin/env bash
# Back up the Production MySQL database and uploaded files as one timestamped set.
set -euo pipefail
umask 077

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
UPLOADS_VOLUME="${UPLOADS_VOLUME:-sis_backend_uploads}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-${DEPLOY_DIR}/backups}"
cd "$DEPLOY_DIR"

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
BACKUP_GPG_RECIPIENT_IN="${BACKUP_GPG_RECIPIENT:-}"
RCLONE_DEST_IN="${RCLONE_DEST:-}"
if [[ -f .backup.env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.backup.env
  set +a
fi
[[ -n "$BACKUP_GPG_RECIPIENT_IN" ]] && BACKUP_GPG_RECIPIENT="$BACKUP_GPG_RECIPIENT_IN"
[[ -n "$RCLONE_DEST_IN" ]] && RCLONE_DEST="$RCLONE_DEST_IN"
export IMAGE_TAG="${IMAGE_TAG_IN:-${IMAGE_TAG:-latest}}"
[[ -n "$BACKEND_IMAGE_IN" ]] && export BACKEND_IMAGE="$BACKEND_IMAGE_IN"
[[ -n "$FRONTEND_IMAGE_IN" ]] && export FRONTEND_IMAGE="$FRONTEND_IMAGE_IN"

log() { printf '==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; }

[[ "$UPLOADS_VOLUME" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]] || {
  fail "Invalid uploads volume name."
  exit 1
}

if ! docker compose -f "$COMPOSE_FILE" ps --status running --services 2>/dev/null | grep -qx 'mysql'; then
  log "MySQL is not running; no backup was created."
  exit 1
fi

if ! docker volume inspect "$UPLOADS_VOLUME" >/dev/null 2>&1; then
  fail "Uploads volume '${UPLOADS_VOLUME}' does not exist; backup aborted."
  exit 1
fi

if [[ -n "${BACKUP_GPG_RECIPIENT:-}" ]] && ! command -v gpg >/dev/null 2>&1; then
  fail "BACKUP_GPG_RECIPIENT is set but gpg is not installed."
  exit 1
fi
if [[ -n "${RCLONE_DEST:-}" ]] && ! command -v rclone >/dev/null 2>&1; then
  fail "RCLONE_DEST is set but rclone is not installed."
  exit 1
fi

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
stage_dir="$(mktemp -d "${BACKUP_DIR}/.sis_${timestamp}.XXXXXX")"
cleanup() { rm -rf -- "$stage_dir"; }
trap cleanup EXIT

db_plain="${stage_dir}/sis_${timestamp}.sql.gz"
uploads_plain="${stage_dir}/sis_${timestamp}.uploads.tar.gz"

log "Dumping MySQL database (including routines, triggers, and events)"
docker compose -f "$COMPOSE_FILE" exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --default-character-set=utf8mb4 --single-transaction --quick --hex-blob --no-tablespaces --routines --triggers --events "$MYSQL_DATABASE"' \
  | gzip -c > "$db_plain"

log "Archiving uploads volume '${UPLOADS_VOLUME}'"
docker run --rm --network none \
  --mount "type=volume,source=${UPLOADS_VOLUME},target=/data,readonly" \
  alpine:3.20 tar -C /data -cf - . \
  | gzip -c > "$uploads_plain"

[[ -s "$db_plain" ]] || { fail "Database archive is empty."; exit 1; }
[[ -s "$uploads_plain" ]] || { fail "Uploads archive is empty."; exit 1; }
gzip -t "$db_plain"
gzip -t "$uploads_plain"
if ! gzip -dc "$db_plain" | tail -n 5 | grep -q '^-- Dump completed'; then
  fail "Database dump is incomplete (missing 'Dump completed' marker)."
  exit 1
fi
tar -tzf "$uploads_plain" >/dev/null

db_file="$db_plain"
uploads_file="$uploads_plain"
if [[ -n "${BACKUP_GPG_RECIPIENT:-}" ]]; then
  log "Encrypting archives for GPG recipient"
  gpg --batch --yes --trust-model always --output "${db_plain}.gpg" \
    --encrypt --recipient "$BACKUP_GPG_RECIPIENT" "$db_plain"
  gpg --batch --yes --trust-model always --output "${uploads_plain}.gpg" \
    --encrypt --recipient "$BACKUP_GPG_RECIPIENT" "$uploads_plain"
  [[ -s "${db_plain}.gpg" && -s "${uploads_plain}.gpg" ]]
  gpg --batch --list-packets "${db_plain}.gpg" >/dev/null
  gpg --batch --list-packets "${uploads_plain}.gpg" >/dev/null
  db_file="${db_plain}.gpg"
  uploads_file="${uploads_plain}.gpg"
  rm -f -- "$db_plain" "$uploads_plain"
fi

manifest="${stage_dir}/sis_${timestamp}.sha256"
(
  cd "$stage_dir"
  sha256sum "$(basename "$db_file")" "$(basename "$uploads_file")" > "$(basename "$manifest")"
  sha256sum --check "$(basename "$manifest")"
)

db_final="${BACKUP_DIR}/$(basename "$db_file")"
uploads_final="${BACKUP_DIR}/$(basename "$uploads_file")"
manifest_final="${BACKUP_DIR}/sis_${timestamp}.sha256"
for path in "$db_final" "$uploads_final" "$manifest_final"; do
  [[ ! -e "$path" ]] || { fail "Backup target already exists: $path"; exit 1; }
done

chmod 600 "$db_file" "$uploads_file" "$manifest"
mv -- "$db_file" "$db_final"
mv -- "$uploads_file" "$uploads_final"
mv -- "$manifest" "$manifest_final"
chmod 600 "$db_final" "$uploads_final" "$manifest_final"

if [[ -n "${RCLONE_DEST:-}" ]]; then
  log "Copying and verifying this backup set at configured offsite destination"
  rclone copy "$BACKUP_DIR" "$RCLONE_DEST" \
    --include "/$(basename "$db_final")" \
    --include "/$(basename "$uploads_final")" \
    --include "/$(basename "$manifest_final")" \
    --checksum
  rclone check "$BACKUP_DIR" "$RCLONE_DEST" \
    --include "/$(basename "$db_final")" \
    --include "/$(basename "$uploads_final")" \
    --include "/$(basename "$manifest_final")" \
    --download
fi

log "Backup set validated: ${db_final}, ${uploads_final}, ${manifest_final}"
log "No previous backups were removed."
