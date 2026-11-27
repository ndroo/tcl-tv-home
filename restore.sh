#!/usr/bin/env bash
# Put the TV back the way it was on 2026-11-26, using the snapshot in backup/.
# Re-enables every app that was on in the snapshot and restores the changed settings.
set -u
TV="${TV:-192.168.2.50:5555}"
DIR="$(cd "$(dirname "$0")" && pwd)"
tv() { adb -s "$TV" shell "$@" </dev/null; }

adb connect "$TV" >/dev/null 2>&1
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "TV not connected (or not approved on the TV)."; exit 1; }

was_on=$(sed 's/^package://' "$DIR/backup/packages-enabled.txt" | tr -d '\r')
now_off=$(tv pm list packages -d | sed 's/^package://' | tr -d '\r')
now_all=$(tv pm list packages | sed 's/^package://' | tr -d '\r')

echo "== Re-enabling apps =="
n=0
for pkg in $was_on; do
  if ! grep -qx "$pkg" <<<"$now_all"; then            # removed for this user -> bring back
    printf '%-40s %s\n' "$pkg" "$(tv cmd package install-existing "$pkg" 2>&1 | tr -d '\r')"; n=$((n+1))
  elif grep -qx "$pkg" <<<"$now_off"; then            # disabled -> enable
    printf '%-40s %s\n' "$pkg" "$(tv pm enable "$pkg" 2>&1 | tr -d '\r')"; n=$((n+1))
  fi
done
echo "$n app(s) restored"

echo "== Removing background restrictions and TV Home =="
for p in com.netflix.ninja com.google.android.youtube.tv com.google.android.tts; do tv cmd appops reset "$p"; echo "$p: restrictions cleared"; done
echo "power whitelist: $(tv cmd deviceidle sys-whitelist reset | tr -d '\r')"  # nothing was removed before we started
# The stock home screen is back on now (above), so it is safe to remove ours.
tv pm list packages -e | grep -q 'com.google.android.tvlauncher$' \
  && echo "local.tvhome: $(tv pm uninstall local.tvhome 2>&1 | tr -d '\r')" \
  || echo "stock home screen not back on; leaving TV Home installed so the TV keeps a home screen"
echo "fm.triplec.tv: $(tv pm uninstall fm.triplec.tv 2>&1 | tr -d '\r')"

echo "== Restoring settings =="
restore_setting() {  # namespace key
  local val
  val=$(grep -E "^$2=" "$DIR/backup/settings-$1.txt" | head -1 | cut -d= -f2-)
  if [ -n "$val" ]; then tv settings put "$1" "$2" "$val"; echo "$1 $2 = $val"
  else tv settings delete "$1" "$2" >/dev/null; echo "$1 $2 = (default)"; fi
}
restore_setting global window_animation_scale
restore_setting global transition_animation_scale
restore_setting global animator_duration_scale
restore_setting secure screensaver_components
restore_setting global wifi_scan_always_enabled
restore_setting system screen_off_timeout
# Location providers only accept +name / -name, so turn each original one back on.
for p in $(grep -E "^location_providers_allowed=" "$DIR/backup/settings-secure.txt" | cut -d= -f2- | tr ',' ' '); do
  tv settings put secure location_providers_allowed "+$p"
done
echo "secure location_providers_allowed = $(tv settings get secure location_providers_allowed | tr -d '\r')"
echo "Done. Restart the TV (unplug 10 s) to be sure everything comes back cleanly."
