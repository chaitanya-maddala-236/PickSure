#!/usr/bin/env bash
set -euo pipefail

mkdir -p ../screenshots
adb devices -l
timeout 180s adb wait-for-device
adb logcat -c
capture_logcat() {
  adb logcat -d -v time > ../screenshots/logcat.txt || true
}
trap capture_logcat EXIT

# Wait until the package manager and activity service have started, not only
# until Android sets sys.boot_completed during first boot.
system_ready=false
for attempt in $(seq 1 90); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
    packages="$(adb shell cmd package list packages 2>&1 || true)"
    current_user="$(adb shell am get-current-user 2>&1 | tr -d '\r' || true)"
    if printf '%s\n' "$packages" | grep -q '^package:' && [ "$current_user" = "0" ]; then
      system_ready=true
      break
    fi
  fi
  sleep 2
done
if [ "$system_ready" != true ]; then
  exit 1
fi
sleep 30

timeout 180s adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
timeout 180s adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

set +e
timeout 240s adb shell am instrument -w -r com.picksure.android.test/androidx.test.runner.AndroidJUnitRunner > ../screenshots/instrumentation.txt 2>&1
instrumentation_status=$?
set -e

cat ../screenshots/instrumentation.txt

if [ "$instrumentation_status" -ne 0 ] || grep -q "shortMsg=Process crashed" ../screenshots/instrumentation.txt; then
  exit 1
fi
grep -Eq '^OK \([0-9]+ tests?\)$' ../screenshots/instrumentation.txt

adb pull /sdcard/Android/data/com.picksure.android/files/picksure-demo ../screenshots/captured
mv ../screenshots/captured/*.png ../screenshots/
rmdir ../screenshots/captured
