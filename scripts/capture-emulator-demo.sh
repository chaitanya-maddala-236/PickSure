#!/usr/bin/env bash
set -euo pipefail

mkdir -p ../screenshots
adb devices -l
timeout 180s adb wait-for-device

# Let the system finish first-boot work before launching the Compose app.
for attempt in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
    break
  fi
  sleep 2
done
sleep 30

timeout 180s adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
timeout 180s adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c

set +e
timeout 240s adb shell am instrument -w -r com.picksure.android.test/androidx.test.runner.AndroidJUnitRunner > ../screenshots/instrumentation.txt 2>&1
instrumentation_status=$?
set -e

cat ../screenshots/instrumentation.txt
adb logcat -d -v time > ../screenshots/logcat.txt || true

if [ "$instrumentation_status" -ne 0 ] || grep -q "shortMsg=Process crashed" ../screenshots/instrumentation.txt; then
  exit 1
fi
grep -q "INSTRUMENTATION_CODE: 0" ../screenshots/instrumentation.txt

adb pull /sdcard/Android/data/com.picksure.android/files/picksure-demo ../screenshots/captured
mv ../screenshots/captured/*.png ../screenshots/
rmdir ../screenshots/captured
