#!/usr/bin/env bash
# Roll back to a previously deployed image tag.
#
# Usage:
#   ./rollback.sh              # use the tag recorded in .previous_tag
#   ./rollback.sh <image-tag>  # use an explicit commit SHA
#
# Rollback is allowed only to the last known previous tag and only when the
# current schema version matches the version that tag last ran against.
# A fresh database backup is taken before switching images. No database
# downgrade is ever performed by this script.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

if [[ $# -gt 0 ]]; then
  TARGET_TAG="$1"
elif [[ -f .previous_tag ]]; then
  TARGET_TAG="$(tr -d '\r\n' < .previous_tag)"
else
  echo "ERROR: No previous tag recorded in .previous_tag." >&2
  echo "Usage: ./rollback.sh <image-tag>" >&2
  echo "Tags currently present on this host:" >&2
  docker images --format '{{.Repository}}:{{.Tag}}' | grep -E 'sis|student' || true
  exit 1
fi

if [[ -z "$TARGET_TAG" ]]; then
  echo "ERROR: Target tag is empty." >&2
  exit 1
fi
if [[ ! "$TARGET_TAG" =~ ^[0-9a-f]{40}$ ]]; then
  echo "ERROR: Rollback target must be a full lowercase commit SHA." >&2
  exit 1
fi

CURRENT_TAG="$(tr -d '\r\n' < .current_tag 2>/dev/null || true)"
if [[ "$TARGET_TAG" == "$CURRENT_TAG" ]]; then
  echo "Tag ${TARGET_TAG} is already the current deployment."
  exit 0
fi

if [[ ! -f .previous_tag || "$(tr -d '\r\n' < .previous_tag)" != "$TARGET_TAG" ]]; then
  echo "ERROR: Only the last recorded previous tag has a compatibility record." >&2
  exit 1
fi
if [[ ! -f .previous_schema_version ]]; then
  echo "ERROR: No schema compatibility record exists for this rollback target." >&2
  echo "Inspect Flyway history and perform a reviewed recovery manually." >&2
  exit 1
fi

echo "==> Requesting rollback to tag: ${TARGET_TAG}"
echo "The deploy script will require matching recorded schema compatibility and a fresh verified backup."
echo "No database downgrade or volume removal is performed."
SIS_ROLLBACK=1 AUTO_ROLLBACK=0 IMAGE_TAG="$TARGET_TAG" bash ./deploy.sh
