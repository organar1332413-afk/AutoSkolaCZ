#!/usr/bin/env bash
set +e

LOG_FILE="/tmp/room-test.log"

run_tests() {
  gradle :core:data:connectedDebugAndroidTest --stacktrace >"$LOG_FILE" 2>&1
  status=$?
  cat "$LOG_FILE"
  return "$status"
}

run_tests
status=$?

if [ "$status" -eq 0 ]; then
  exit 0
fi

if grep -Eq 'Failed to install|InstallException|INSTALL_FAILED|ShellCommandUnresponsiveException|No compatible devices connected|Unknown API Level|device offline|device not found' "$LOG_FILE"; then
  echo "Transient Android emulator/ADB failure detected; recovering and retrying Room device tests once."

  adb kill-server >/dev/null 2>&1 || true
  adb start-server >/dev/null 2>&1 || true
  adb wait-for-device || true

  ready=0
  for _ in $(seq 1 45); do
    boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if [ "$boot_completed" = "1" ] && adb shell pm list packages >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 2
  done

  if [ "$ready" -ne 1 ]; then
    echo "Android emulator did not recover in time."
    exit "$status"
  fi

  sleep 10
  run_tests
  exit $?
fi

exit "$status"
