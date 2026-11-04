#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/android-sdk}}"

if [[ ! -f "$SDK_ROOT/platforms/android-34/android.jar" ]]; then
  echo "Android SDK Platform 34 nicht gefunden: $SDK_ROOT" >&2
  echo "Setze ANDROID_HOME oder ANDROID_SDK_ROOT auf den installierten SDK-Pfad." >&2
  exit 2
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$SDK_ROOT/platform-tools:$SDK_ROOT/cmdline-tools/latest/bin:$PATH"

cd "$PROJECT_DIR"
bash ./native-wsola-smoke-test.sh
bash ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug "$@"
