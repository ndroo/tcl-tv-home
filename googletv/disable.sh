#!/usr/bin/env bash
# Slim down a Chromecast with Google TV (Android 12+) the safe way:
#   1. turn off the apps in googletv/debloat-list.txt, 2. install the Triple C app, 3. restrict YouTube in the
#   background, 4. speed + privacy settings, 5. name the box.
# It deliberately does NOT disable any home screen: on this box, disabling the launchers hands every Home press
# to the setup wizard's fallback screen, which crashed the system twice (see the README). Instead TV Home is put
# on top of Google's home by a small accessibility service (the "home guard"); HOME_GUARD=0 skips that.
# Nothing system-level is uninstalled. googletv/restore.sh undoes all of it (so does a factory reset).
# Safe to re-run any time.
#
# TV         the adb target (default: the first device `adb devices` lists; see snapshot.sh for pairing)
# BOX_NAME   the name shown when casting (default "Kitchen TV")
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(dirname "$DIR")"
TV="${TV:-$(adb devices | awk -F'\t' 'NR>1 && $2=="device"{print $1; exit}')}"
TRIPLEC_APK="${TRIPLEC_APK:-$ROOT/triplec-tv/triplec-tv.apk}"
TVHOME_APK="${TVHOME_APK:-$ROOT/backup/cottage-googletv/tvhome-cottage.apk}"   # CONF_DIR=<wifi.conf, cameras.conf> OUT=<this> tvhome/build.sh
BOX_NAME="${BOX_NAME:-Kitchen TV}"
tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }

[ -n "$TV" ] && [ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Box not connected (pair it first, see snapshot.sh)."; exit 1; }
[ -d "$ROOT/backup/cottage-googletv" ] || { echo "No snapshot yet: run googletv/snapshot.sh first."; exit 1; }

echo "== Turning off apps =="
grep -vE '^[[:space:]]*(#|$)' "$DIR/debloat-list.txt" | awk '{print $1}' | while read -r pkg; do
  printf '%-44s %s\n' "$pkg" "$(tv pm disable-user --user 0 "$pkg" 2>&1)"
done

echo "== Installing the Triple C app =="
adb -s "$TV" install -r "$TRIPLEC_APK" 2>&1 | tail -1

if [ "${HOME_GUARD:-1}" = 1 ] && [ -f "$TVHOME_APK" ]; then
  echo "== TV Home + the home guard (TV Home opens on top of Google's home; nothing is disabled) =="
  adb -s "$TV" install -r "$TVHOME_APK" 2>&1 | tail -1
  svc=local.tvhome/local.tvhome.HomeGuardService
  cur=$(tv settings get secure enabled_accessibility_services)
  case "$cur" in
    *"$svc"*) ;;
    ""|null) tv settings put secure enabled_accessibility_services "$svc" ;;
    *) tv settings put secure enabled_accessibility_services "$cur:$svc" ;;
  esac
  tv settings put secure accessibility_enabled 1
  echo "accessibility services: $(tv settings get secure enabled_accessibility_services)"
  echo "undo any time: adb shell settings put secure enabled_accessibility_services '' (or run restore.sh)"
fi

echo "== Background restrictions (they still open normally when you use or cast to them) =="
for p in com.google.android.youtube.tv; do
  tv cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore
  echo "$p: $(tv cmd appops get "$p" RUN_ANY_IN_BACKGROUND)"
done
# Netflix is deliberately left alone, as on the TCL TV (restricting it caused "not optimized for Netflix (-14)").

echo "== Speed settings =="
tv settings put global window_animation_scale 0.5
tv settings put global transition_animation_scale 0.5
tv settings put global animator_duration_scale 0.5
echo "animations 0.5x"
# Ambient-mode screensaver after 2 h idle instead of 10 min (the box still goes to sleep after sleep_timeout).
tv settings put system screen_off_timeout 7200000
echo "screensaver starts after 2 h idle (was 10 min)"

echo "== Privacy settings =="
tv cmd location set-location-enabled false >/dev/null 2>&1
tv settings put global wifi_scan_always_enabled 0
echo "location enabled: $(tv cmd location is-location-enabled 2>&1 | head -1), wifi background scanning off"

echo "== Name =="
tv "settings put global device_name '$BOX_NAME'"   # one string with its own quotes: adb would split a name with a space
echo "device name: $(tv settings get global device_name)"
