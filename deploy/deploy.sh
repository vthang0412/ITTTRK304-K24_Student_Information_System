#!/usr/bin/env bash
# Deploy a new SIS image tag on the VPS.
#
# Usage:
#   IMAGE_TAG=<commit-sha> \
#   BACKEND_IMAGE=ghcr.io/<owner>/<repo>-backend \
#   FRONTEND_IMAGE=ghcr.io/<owner>/<repo>-frontend \
#   ./deploy.sh
#
# The script:
#   1. backs up MySQL while the old version is still running,
#   2. pulls the new images,
#   3. recreates backend/frontend without touching volumes,
#   4. waits for healthchecks and runs smoke tests,
#   5. rolls back to the previous healthy tag if the new one fails,
#   6. records the tag only after success (so later rollbacks stay safe).
#
# Environment:
#   AUTO_ROLLBACK=1|0  enable/disable automatic rollback (default: 1)
#   SKIP_BACKUP=1      emergency only: skip the pre-deploy database backup
#   SIS_ROLLBACK=1     internal flag used by rollback.sh
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

AUTO_ROLLBACK="${AUTO_ROLLBACK:-1}"
SIS_ROLLBACK="${SIS_ROLLBACK:-0}"

log()  { printf '==> %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; }

read_env_value() {
  local key="$1"
  local value
  value="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" .env 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '\r' || true)"
  value="${value#\"}"; value="${value%\"}"
  value="${value#\'}"; value="${value%\'}"
  printf '%s' "$value"
}

on_error() {
  local code=$?
  fail "Deployment of tag '${IMAGE_TAG:-unknown}' failed (exit ${code})."
  fail "The running version may be unchanged or already rolled back."
  fail "Inspect the stack with:"
  fail "  docker compose -f ${COMPOSE_FILE} ps"
  fail "  docker compose -f ${COMPOSE_FILE} logs --tail=200 backend frontend mysql"
  fail "Roll back manually with:"
  fail "  cd ${DEPLOY_DIR} && ./rollback.sh"
  fail "If the pull failed with 'unauthorized', make the GHCR packages public or set GHCR_PULL_TOKEN."
  exit "$code"
}
trap on_error ERR

[[ -f .env ]] || { fail ".env is missing in ${DEPLOY_DIR}. Create it from env.production.example."; exit 1; }
[[ -f "$COMPOSE_FILE" ]] || { fail "${COMPOSE_FILE} is missing in ${DEPLOY_DIR}."; exit 1; }

# Keep values passed by the caller/rollback script above values from files.
IMAGE_TAG_IN="${IMAGE_TAG:-}"
BACKEND_IMAGE_IN="${BACKEND_IMAGE:-}"
FRONTEND_IMAGE_IN="${FRONTEND_IMAGE:-}"

if [[ -f .images.env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.images.env
  set +a
fi

IMAGE_TAG="${IMAGE_TAG_IN:-${IMAGE_TAG:-}}"
BACKEND_IMAGE="${BACKEND_IMAGE_IN:-${BACKEND_IMAGE:-$(read_env_value BACKEND_IMAGE)}}"
FRONTEND_IMAGE="${FRONTEND_IMAGE_IN:-${FRONTEND_IMAGE:-$(read_env_value FRONTEND_IMAGE)}}"

[[ -n "$IMAGE_TAG" ]] || { fail "IMAGE_TAG is required (commit SHA)."; exit 1; }
[[ -n "$BACKEND_IMAGE" ]] || { fail "BACKEND_IMAGE is required."; exit 1; }
[[ -n "$FRONTEND_IMAGE" ]] || { fail "FRONTEND_IMAGE is required."; exit 1; }
export IMAGE_TAG BACKEND_IMAGE FRONTEND_IMAGE

TARGET_TAG="$IMAGE_TAG"

# Tag currently running before this deployment starts.
CURRENT_TAG=""
if [[ -f .current_tag ]]; then
  CURRENT_TAG="$(tr -d '\r\n' < .current_tag)"
fi

log "Deploying image tag: ${TARGET_TAG}"
log "Backend image:  ${BACKEND_IMAGE}"
log "Frontend image: ${FRONTEND_IMAGE}"
log "Currently running tag: ${CURRENT_TAG:-<none>}"

wait_for_health() {
  local service="$1" container="$2" timeout="$3"
  local deadline=$((SECONDS + timeout))
  log "Waiting for ${service} to become healthy (timeout ${timeout}s)"
  while (( SECONDS < deadline )); do
    local status
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    case "$status" in
      healthy)
        log "${service} is healthy"
        return 0
        ;;
      unhealthy|exited|dead)
        fail "${service} is ${status}."
        return 1
        ;;
      *)
        sleep 5
        ;;
    esac
  done
  fail "Timed out waiting for ${service} to become healthy."
  return 1
}

run_stack() {
  local tag="$1"
  local pull_mode="$2"

  export IMAGE_TAG="$tag"

  log "Pulling images for tag ${tag}"
  if ! docker compose -f "$COMPOSE_FILE" --env-file .env pull backend frontend; then
    if [[ "$pull_mode" == "strict" ]]; then
      fail "Could not pull images for tag ${tag}."
      return 1
    fi
    log "WARNING: could not pull tag ${tag}; trying the local images (needed for rollback)."
  fi

  # Recreate only backend/frontend. Never run "down" and never use "-v".
  log "Applying tag ${tag}"
  docker compose -f "$COMPOSE_FILE" --env-file .env up -d --remove-orphans || return 1

  wait_for_health "MySQL" "sis-mysql" 180 || return 1
  wait_for_health "Backend" "sis-backend" 300 || return 1
  wait_for_health "Frontend" "sis-frontend" 120 || return 1

  # Smoke test the frontend and the Nginx -> backend proxy from inside the
  # frontend container. Nginx listens on 8080 (nginx-unprivileged image).
  # /actuator/health returns a real application status.
  log "Running smoke tests"
  docker compose -f "$COMPOSE_FILE" --env-file .env exec -T frontend \
    wget -q -O /dev/null http://127.0.0.1:8080/ || return 1
  docker compose -f "$COMPOSE_FILE" --env-file .env exec -T frontend \
    wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || return 1
}

record_success() {
  local deployed_tag="$1"
  printf 'BACKEND_IMAGE=%s\nFRONTEND_IMAGE=%s\n' "$BACKEND_IMAGE" "$FRONTEND_IMAGE" > .images.env

  if [[ "$SIS_ROLLBACK" == "1" ]]; then
    printf '%s\n' "$deployed_tag" > .current_tag
    # Never keep the failed tag as the next rollback target (L-03).
    if [[ -f .previous_tag && "$(tr -d '\r\n' < .previous_tag)" == "$deployed_tag" ]]; then
      rm -f .previous_tag
    fi
    return 0
  fi

  if [[ -n "$CURRENT_TAG" && "$CURRENT_TAG" != "$deployed_tag" ]]; then
    printf '%s\n' "$CURRENT_TAG" > .previous_tag
  fi
  printf '%s\n' "$deployed_tag" > .current_tag
}

# 1. Ensure MySQL is running, then back up the database before touching the
#    application containers. Starting only the MySQL service does not recreate
#    the backend/frontend and never removes a volume, so the backup always sees
#    the current data even if the stack was stopped before this deploy.
if [[ "${SKIP_BACKUP:-0}" == "1" ]]; then
  log "SKIP_BACKUP=1; skipping database backup (emergency use only)."
else
  log "Ensuring MySQL is running before the pre-deploy backup"
  docker compose -f "$COMPOSE_FILE" --env-file .env up -d mysql || { fail "Could not start MySQL."; exit 1; }
  wait_for_health "MySQL" "sis-mysql" 180 || { fail "MySQL is not healthy; aborting deployment."; exit 1; }
  COMPOSE_FILE="$COMPOSE_FILE" bash ./backup.sh || { fail "Database backup failed; aborting deployment."; exit 1; }
fi

# 2. Deploy the new tag.
if run_stack "$TARGET_TAG" strict; then
  record_success "$TARGET_TAG"
  log "Deployment of tag ${TARGET_TAG} completed successfully."
  if [[ -f .previous_tag ]]; then
    log "Previous tag available for rollback: $(tr -d '\r\n' < .previous_tag)"
  fi
  exit 0
fi

fail "Tag ${TARGET_TAG} did not become healthy."

# 3. Automatic rollback to the tag that was running before this deployment.
if [[ "$AUTO_ROLLBACK" == "1" && -n "$CURRENT_TAG" && "$CURRENT_TAG" != "$TARGET_TAG" ]]; then
  log "AUTO_ROLLBACK=1: rolling back to ${CURRENT_TAG}"
  SIS_ROLLBACK=1
  export SIS_ROLLBACK
  if run_stack "$CURRENT_TAG" lenient; then
    record_success "$CURRENT_TAG"
    fail "Deployment of ${TARGET_TAG} failed and was rolled back to ${CURRENT_TAG}."
    fail "Database volumes were not modified. Investigate tag ${TARGET_TAG} before retrying."
    exit 1
  fi
  fail "ROLLBACK TO ${CURRENT_TAG} FAILED. Manual intervention required."
  fail "The database volume is still intact. Inspect with:"
  fail "  docker compose -f ${COMPOSE_FILE} ps"
  fail "  docker compose -f ${COMPOSE_FILE} logs --tail=200 backend frontend mysql"
  exit 1
fi

fail "No automatic rollback was performed (AUTO_ROLLBACK=${AUTO_ROLLBACK}, previous tag='${CURRENT_TAG:-<none>}')."
fail "The database volume was not modified. Inspect the stack manually."
exit 1
