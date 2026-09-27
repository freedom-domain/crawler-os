#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
max_parallel="${BUILD_JOBS:-2}"

usage() {
  printf 'Usage: %s [-j|--jobs <count>]\n' "$0"
  printf 'Build all local Docker images (default parallel jobs: 2).\n'
}

while (($# > 0)); do
  case "$1" in
    -j|--jobs)
      if (($# < 2)); then
        printf 'Error: %s requires a job count.\n' "$1" >&2
        usage >&2
        exit 2
      fi
      max_parallel="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      printf 'Error: unknown argument: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! "$max_parallel" =~ ^[1-9][0-9]*$ ]]; then
  printf 'Error: job count must be a positive integer; got "%s".\n' "$max_parallel" >&2
  exit 2
fi

build_scripts=(
  crawler-gateway
  crawler-user-service
  crawler-spider-service
  crawler-search-service
  crawler-file-service
  crawler-worker
  crawler-admin-web
)

printf 'Building %d Docker images with up to %s parallel jobs.\n' \
  "${#build_scripts[@]}" "$max_parallel"

failed=0
for ((start = 0; start < ${#build_scripts[@]}; start += max_parallel)); do
  batch_pids=()
  batch_names=()

  for ((index = start; index < start + max_parallel && index < ${#build_scripts[@]}; index++)); do
    name="${build_scripts[$index]}"
    printf 'Starting %s\n' "$name"
    "$script_dir/$name.sh" &
    batch_pids+=("$!")
    batch_names+=("$name")
  done

  for index in "${!batch_pids[@]}"; do
    if wait "${batch_pids[$index]}"; then
      printf 'Finished %s\n' "${batch_names[$index]}"
    else
      printf 'Failed %s\n' "${batch_names[$index]}" >&2
      failed=1
    fi
  done
done

if ((failed != 0)); then
  printf 'One or more Docker image builds failed.\n' >&2
  exit 1
fi

printf 'All Docker image builds completed successfully.\n'
