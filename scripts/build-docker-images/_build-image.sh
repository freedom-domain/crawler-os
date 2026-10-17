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
image_tag="${IMAGE_TAG:-latest}"

if ! command -v docker >/dev/null 2>&1; then
  printf 'Error: docker is not installed or is not on PATH.\n' >&2
  exit 127
fi

if [[ -n "$build_arg" ]]; then
  service="${build_arg#SERVICE=}"
  if [[ "$service" == "$build_arg" ]]; then
    printf 'Error: unsupported build argument: %s\n' "$build_arg" >&2
    exit 2
  fi

  if [[ "${LOCAL_MAVEN_PREBUILT:-}" != "1" ]]; then
    "$repo_root/scripts/build-local.sh" -pl "$service" -am
  fi

  build_context="$(mktemp -d)"
  trap 'rm -rf "$build_context"' EXIT
  mkdir -p "$build_context/$service/target"
  cp "$repo_root/$service"/target/*.jar "$build_context/$service/target/"
  cp "$repo_root/$dockerfile" "$build_context/Dockerfile"

  docker build \
    --tag "$image:$image_tag" \
    --file "$build_context/Dockerfile" \
    --build-arg "$build_arg" \
    "$build_context"
else
  docker build \
    --tag "$image:$image_tag" \
    --file "$repo_root/$dockerfile" \
    "$repo_root/$context"
fi
