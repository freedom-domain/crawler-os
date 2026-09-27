#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"

if ! command -v mvn >/dev/null 2>&1; then
  printf 'Error: Maven (mvn) is not installed or is not on PATH.\n' >&2
  exit 127
fi

maven_info="$(mvn -version 2>&1)" || {
  printf 'Error: failed to inspect the Maven runtime.\n%s\n' "$maven_info" >&2
  exit 1
}

if [[ "$maven_info" =~ Java[[:space:]]version:[[:space:]]([0-9]+)([.]([0-9]+))? ]]; then
  java_major="${BASH_REMATCH[1]}"
  if [[ "$java_major" == "1" && -n "${BASH_REMATCH[3]:-}" ]]; then
    java_major="${BASH_REMATCH[3]}"
  fi
else
  printf 'Error: could not determine the Java version used by Maven.\n%s\n' "$maven_info" >&2
  exit 1
fi

if ((java_major < 21)); then
  printf 'Error: Maven is running with Java %s; this project requires JDK 21 or newer.\n' \
    "$java_major" >&2
  printf 'Set JAVA_HOME to a JDK 21+ installation and ensure "$JAVA_HOME/bin" is on PATH.\n' >&2
  printf 'Maven runtime details:\n%s\n' "$maven_info" >&2
  exit 1
fi

printf 'Building with local Maven (Java %s).\n' "$java_major"

cd "$repo_root"
exec mvn "$@" -DskipTests package
