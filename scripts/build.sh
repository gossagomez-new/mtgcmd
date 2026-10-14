#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf '%s\n' 'Usage: build.sh [--skip-tests]' '       build.sh --help' \
    '' 'Build React, then clean and verify the Java package.' \
    'PMD findings are advisory and recorded in backend/target/pmd.txt.' \
    '--skip-tests packages without running the Java tests.'
}

if (( $# > 1 )); then
  usage >&2
  exit 2
fi

skip_tests=false
case "${1-}" in
  '')
    if (( $# != 0 )); then
      usage >&2
      exit 2
    fi
    ;;
  --help) usage; exit 0 ;;
  --skip-tests) skip_tests=true ;;
  *) usage >&2; exit 2 ;;
esac

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"

printf '%s\n' 'Building frontend…'
npm --prefix "$repo_root/frontend" run build
if [[ ! -f "$repo_root/frontend/dist/index.html" ]]; then
  printf '%s\n' 'Frontend build did not produce frontend/dist/index.html; packaging stopped.' >&2
  exit 1
fi

if [[ "$skip_tests" == true ]]; then
  printf '%s\n' 'Packaging Java (Java tests skipped)…'
  mvn -f "$repo_root/backend/pom.xml" clean package -DskipTests
else
  printf '%s\n' 'Building and verifying Java…'
  mvn -f "$repo_root/backend/pom.xml" clean verify
fi

artifacts=()
for artifact in "$repo_root"/backend/target/*.jar; do
  if [[ -f "$artifact" ]]; then
    artifacts+=("$artifact")
  fi
done
if (( ${#artifacts[@]} != 1 )); then
  printf '%s\n' 'Expected exactly one regular .jar in backend/target; cannot report a unique artifact.' >&2
  exit 1
fi

pmd_log="$repo_root/backend/target/pmd.txt"
if [[ ! -f "$pmd_log" ]]; then
  printf '%s\n' 'PMD did not produce backend/target/pmd.txt; cannot report a complete build.' >&2
  exit 1
fi

if [[ "$skip_tests" == true ]]; then
  printf '%s\n' 'Build complete (Java tests skipped).'
else
  printf '%s\n' 'Build complete (Java verification completed).'
fi
printf 'Artifact: %s\n' "${artifacts[0]}"
printf 'PMD findings log: %s\n' "$pmd_log"
