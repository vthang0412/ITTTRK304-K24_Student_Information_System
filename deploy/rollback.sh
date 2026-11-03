#!/usr/bin/env bash
# Roll back to a previously deployed image tag.
#
# Usage:
#   ./rollback.sh              # use the tag recorded in .previous_tag
#   ./rollback.sh <image-tag>  # use an explicit commit SHA
#   SKIP_BACKUP=1 ./rollback.sh <image-tag>  # emergency only, if MySQL cannot be dumped
#
# A fresh database backup is taken by deploy.sh before the switch.
# The MySQL volume is never removed.
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

CURRENT_TAG="$(tr -d '\r\n' < .current_tag 2>/dev/null || true)"
if [[ "$TARGET_TAG" == "$CURRENT_TAG" ]]; then
  echo "Tag ${TARGET_TAG} is already the current deployment."
  exit 0
fi

echo "==> Rolling back to tag: ${TARGET_TAG}"
echo "A fresh database backup is taken before switching images."
echo "The MySQL volume is never removed. Automatic rollback is disabled to avoid loops."
SIS_ROLLBACK=1 AUTO_ROLLBACK=0 IMAGE_TAG="$TARGET_TAG" bash ./deploy.sh
