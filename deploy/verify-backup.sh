#!/usr/bin/env bash
# Verify a database + uploads backup set without changing Docker or application data.
set -euo pipefail

fail() { printf 'ERROR: %s\n' "$*" >&2; }

if [[ $# -ne 1 ]]; then
  fail "Provide the database archive from one backup set."
  echo "Usage: ./verify-backup.sh backups/sis_<UTC timestamp>.sql.gz[.gpg]" >&2
  exit 1
fi

DB_ARCHIVE="$1"
[[ -f "$DB_ARCHIVE" ]] || { fail "Database archive not found."; exit 1; }
case "$DB_ARCHIVE" in
  *.sql.gz.gpg) stem="${DB_ARCHIVE%.sql.gz.gpg}" ;;
  *.sql.gz) stem="${DB_ARCHIVE%.sql.gz}" ;;
  *) fail "Database archive must end with .sql.gz or .sql.gz.gpg."; exit 1 ;;
esac
UPLOADS_ARCHIVE="${stem}.uploads.tar.gz"
[[ "$DB_ARCHIVE" == *.gpg ]] && UPLOADS_ARCHIVE="${UPLOADS_ARCHIVE}.gpg"
MANIFEST="${stem}.sha256"
[[ -f "$UPLOADS_ARCHIVE" ]] || { fail "Matching uploads archive not found."; exit 1; }
[[ -f "$MANIFEST" ]] || { fail "Checksum manifest not found."; exit 1; }

if [[ "$DB_ARCHIVE" == */* ]]; then
  backup_dir="${DB_ARCHIVE%/*}"
else
  backup_dir="."
fi
(
  cd "$backup_dir"
  sha256sum --check "$(basename "$MANIFEST")"
)

stream_archive() {
  case "$1" in
    *.gpg) gpg --batch --decrypt "$1" ;;
    *) cat -- "$1" ;;
  esac
}

stream_archive "$DB_ARCHIVE" | gzip -t
if ! stream_archive "$DB_ARCHIVE" | gzip -dc | tail -n 5 | grep -q '^-- Dump completed'; then
  fail "Database dump is incomplete."
  exit 1
fi
stream_archive "$UPLOADS_ARCHIVE" | tar -tzf - >/dev/null
printf 'Backup set is valid: database, uploads, and checksums passed.\n'
