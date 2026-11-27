#!/usr/bin/env bash
# Put the Chromecast with Google TV back the way it was, using the snapshot in backup/cottage-googletv/.
# Re-enables every app that was on in the snapshot and restores the changed settings.
# TV = the adb target (default: the first device `adb devices` lists).
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(dirname "$DIR")"
SNAP="$ROOT/backup/cottage-googletv"
TV="${TV:-$(adb devices | awk -F'\t' 'NR>1 && $2=="device"{print $1; exit}')}"
tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }

[ -n "$TV" ] && [ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Box not connected (is wireless debugging on and paired?)."; exit 1; }
[ -f "$SNAP/packages-enabled.txt" ] || { echo "No snapshot in $SNAP."; exit 1; }

was_on=$(sed 's/^package://' "$SNAP/packages-enabled.txt" | tr -d '\r')
now_off=$(tv pm list packages -d | sed 's/^package://')
now_all=$(tv pm list packages | sed 's/^package://')

echo "== Re-enabling apps =="
n=0
for pkg in $was_on; do
  if ! grep -qx "$pkg" <<<"$now_all"; then            # removed for this user -> bring back
    printf '%-44s %s\n' "$pkg" "$(tv cmd package install-existing "$pkg" 2>&1)"; n=$((n+1))
  elif grep -qx "$pkg" <<<"$now_off"; then            # disabled -> enable
    printf '%-44s %s\n' "$pkg" "$(tv pm enable "$pkg" 2>&1)"; n=$((n+1))
  fi
done
echo "$n app(s) restored"

echo "== Removing background restrictions and the Triple C app =="
for p in com.google.android.youtube.tv; do tv cmd appops reset "$p"; echo "$p: restrictions cleared"; done
echo "fm.triplec.tv: $(tv pm uninstall fm.triplec.tv 2>&1)"

echo "== Restoring settings =="
restore_setting() {  # namespace key
  local val
  val=$(grep -E "^$2=" "$SNAP/settings-$1.txt" | head -1 | cut -d= -f2-)
  if [ -n "$val" ]; then tv settings put "$1" "$2" "$val"; echo "$1 $2 = $val"
  else tv settings delete "$1" "$2" >/dev/null; echo "$1 $2 = (default)"; fi
}
restore_setting global window_animation_scale
restore_setting global transition_animation_scale
restore_setting global animator_duration_scale
restore_setting global wifi_scan_always_enabled
restore_setting system screen_off_timeout
restore_setting global device_name
# the home guard: switch it off first (the snapshot had no accessibility services), then remove TV Home
restore_setting secure enabled_accessibility_services
restore_setting secure accessibility_enabled
echo "local.tvhome: $(tv pm uninstall local.tvhome 2>&1)"
was_loc=$(grep -E "^location_mode=" "$SNAP/settings-secure.txt" | head -1 | cut -d= -f2-)
if [ -n "$was_loc" ] && [ "$was_loc" != 0 ]; then tv cmd location set-location-enabled true >/dev/null 2>&1; fi
echo "location enabled: $(tv cmd location is-location-enabled 2>&1 | head -1)"
echo "Done. Restart the box (unplug 10 s) to be sure everything comes back cleanly."
