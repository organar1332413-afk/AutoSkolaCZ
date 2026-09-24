#!/usr/bin/env bash
set +e

LOG_FILE="/tmp/room-test.log"

gradle :core:data:connectedDebugAndroidTest --stacktrace >"$LOG_FILE" 2>&1
status=$?
cat "$LOG_FILE"

if [ "$status" -eq 0 ]; then
  exit 0
fi

if grep -Eq 'Failed to install|InstallException|INSTALL_FAILED' "$LOG_FILE"; then
  echo "Transient emulator install failure detected; retrying Room device tests once."
  adb wait-for-device

  ready=0
  for _ in $(seq 1 30); do
    if adb shell pm list packages >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 2
  done

  if [ "$ready" -ne 1 ]; then
    echo "Android package manager did not become ready."
    exit "$status"
  fi

  sleep 10
  gradle :core:data:connectedDebugAndroidTest --stacktrace
  exit $?
fi

exit "$status"
