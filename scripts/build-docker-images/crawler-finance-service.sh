#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$script_dir/_build-image.sh" \
  crawler-finance-service \
  docker/Dockerfile-app-local \
  . \
  SERVICE=crawler-finance-service
