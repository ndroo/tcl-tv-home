#!/usr/bin/env bash
# Put the TV into its slimmed-down state:
#   1. install our own home screen (tvhome/), 2. turn off the apps in debloat-list.txt,
#   3. restrict Netflix/YouTube in the background, 4. speed + privacy settings,
#   5. fully compile the apps that matter.
# Nothing system-level is uninstalled. ./restore.sh undoes all of it (so does a factory reset).
# Safe to re-run any time, e.g. after a TCL system update switches things back on.
set -u
TV="${TV:-192.168.2.50:5555}"
DIR="$(cd "$(dirname "$0")" && pwd)"
tv() { adb -s "$TV" shell "$@" </dev/null; }

adb connect "$TV" >/dev/null 2>&1
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "TV not connected (or not approved on the TV)."; exit 1; }

echo "== Installing TV Home (must come before the stock home screen is turned off) =="
adb -s "$TV" install -r "$DIR/tvhome/tvhome.apk" 2>&1 | tail -1
tv pm list packages -e | grep -q 'local.tvhome$' || { echo "TV Home did not install; stopping so the TV keeps a home screen."; exit 1; }
echo "== Installing the Triple C app (first on TV Home) =="
adb -s "$TV" install -r "$DIR/triplec-tv/triplec-tv.apk" 2>&1 | tail -1

echo "== Turning off apps =="
grep -vE '^[[:space:]]*(#|$)' "$DIR/debloat-list.txt" | awk '{print $1}' | while read -r pkg; do
  printf '%-40s %s\n' "$pkg" "$(tv pm disable-user --user 0 "$pkg" 2>&1 | tr -d '\r')"
done
tv cmd package set-home-activity local.tvhome/.HomeActivity >/dev/null

echo "== Background restrictions (they still open normally when you use or cast to them) =="
for p in com.google.android.youtube.tv; do
  tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore
  echo "$p: $(tv cmd appops get "$p" RUN_ANY_IN_BACKGROUND | tr -d '\r')"
done
# Netflix is deliberately left alone. Blocking its boot-time service (appops + taking it off
# TCL's power whitelist) saved ~95 MB but made Netflix show "device is not optimized for
# Netflix (-14)" on cold start (2026-11-26). Undo any earlier run of the old version:
tv cmd appops reset com.netflix.ninja
echo "netflix power whitelist: $(tv cmd deviceidle sys-whitelist +com.netflix.ninja 2>&1 | tr -d '\r')"

echo "== Speed settings =="
tv settings put global window_animation_scale 0.5
tv settings put global transition_animation_scale 0.5
tv settings put global animator_duration_scale 0.5
echo "animations 0.5x"
# The screensaver starts N minutes after the last remote press and covers casts (both the
# Google one and the basic one do). 2 hours instead of 5 minutes keeps casts visible.
tv settings put system screen_off_timeout 7200000
echo "screensaver starts after 2 h idle (was 5 min)"

echo "== Privacy settings =="
tv settings put secure location_providers_allowed -gps
tv settings put secure location_providers_allowed -network
tv settings put global wifi_scan_always_enabled 0
echo "location: '$(tv settings get secure location_providers_allowed | tr -d '\r')' (empty = off), wifi background scanning off"

echo "== Compiling key apps fully (Chromecast's cast module was only interpreted) =="
for p in com.google.android.apps.mediashell local.tvhome com.tcl.tvinput com.tcl.settings; do
  printf '%-40s %s\n' "$p" "$(tv cmd package compile -m speed -f "$p" 2>&1 | tr -d '\r' | tail -1)"
done

tv input keyevent KEYCODE_HOME
echo "== Chromecast check =="
tv pm list packages -e | grep -q 'com.google.android.apps.mediashell$' && echo "Chromecast app: enabled" || echo "WARNING: Chromecast app not enabled"
curl -s -m 5 "http://${TV%%:*}:8008/setup/eureka_info?params=name" | grep -q '"name"' && echo "Cast service: answering" || echo "WARNING: cast service not answering"
echo "Run ./check.sh for the full test."
