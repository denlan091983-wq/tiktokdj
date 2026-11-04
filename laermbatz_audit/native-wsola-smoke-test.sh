#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CXX="${CXX:-c++}"
if ! command -v "$CXX" >/dev/null 2>&1; then
  echo "C++-Compiler nicht gefunden: $CXX" >&2
  exit 2
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
"$CXX" -std=c++17 -O2 -Wall -Wextra -Werror \
  "$PROJECT_DIR/app/src/test/cpp/master_recording_ring_test.cpp" \
  -o "$TMP_DIR/master-recording-ring-test"
"$TMP_DIR/master-recording-ring-test"
"$CXX" -std=c++17 -O2 -Wall -Wextra -Werror \
  "$PROJECT_DIR/app/src/test/cpp/wsola_processor_test.cpp" \
  -o "$TMP_DIR/wsola-processor-test"
"$TMP_DIR/wsola-processor-test"
