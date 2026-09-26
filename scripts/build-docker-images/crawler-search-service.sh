#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$script_dir/_build-image.sh" \
  crawler-search-service \
  docker/Dockerfile-app \
  . \
  SERVICE=crawler-search-service
