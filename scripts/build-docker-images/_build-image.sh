#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  printf 'Usage: %s <image> <dockerfile> <context> <build-arg>\n' "$0" >&2
  exit 2
fi

image="$1"
dockerfile="$2"
context="$3"
build_arg="$4"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
image_tag="${IMAGE_TAG:-local}"

if ! command -v docker >/dev/null 2>&1; then
  printf 'Error: docker is not installed or is not on PATH.\n' >&2
  exit 127
fi

if [[ -n "$build_arg" ]]; then
  docker build \
    --tag "$image:$image_tag" \
    --file "$repo_root/$dockerfile" \
    --build-arg "$build_arg" \
    "$repo_root/$context"
else
  docker build \
    --tag "$image:$image_tag" \
    --file "$repo_root/$dockerfile" \
    "$repo_root/$context"
fi
