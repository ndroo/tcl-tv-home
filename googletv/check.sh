#!/usr/bin/env bash
# Health check: is the box still the way googletv/disable.sh left it, and does casting work?
# Needs wireless debugging on (and paired). TV = the adb target (default: the first device listed).
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(dirname "$DIR")"
TV="${TV:-$(adb devices | awk -F'\t' 'NR>1 && $2=="device"{print $1; exit}')}"
tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
ok=0; bad=0
pass() { echo "  ok    $*"; ok=$((ok+1)); }
fail() { echo "  FAIL  $*"; bad=$((bad+1)); }

[ -n "$TV" ] && [ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Box not connected (is wireless debugging on and paired?)"; exit 1; }
echo "box up for $(tv cat /proc/uptime | awk '{print int($1/60)}') min"

echo "== Apps that should be off =="
off=$(tv pm list packages -d | sed 's/^package://')
for p in $(grep -vE '^[[:space:]]*(#|$)' "$DIR/debloat-list.txt" | awk '{print $1}'); do
  grep -qx "$p" <<<"$off" && pass "$p off" || fail "$p is ON again"
done

echo "== Things that must stay on (the home screen, setup wizard and keyboard especially) =="
on=$(tv pm list packages -e | sed 's/^package://')
for p in com.google.android.apps.mediashell com.google.android.chromecast.chromecastservice com.google.android.gms \
         com.android.vending com.google.android.tv.remote.service com.android.tv.settings fm.triplec.tv \
         com.google.android.youtube.tv com.netflix.ninja com.amazon.amazonvideo.livingroom \
         com.google.android.apps.tv.launcherx com.google.android.tvlauncher com.google.android.tungsten.setupwraith \
         com.google.android.katniss com.google.android.inputmethod.latin; do
  grep -qx "$p" <<<"$on" && pass "$p on" || fail "$p is OFF"
done

echo "== Settings =="
[ "$(tv settings get system screen_off_timeout)" = 7200000 ] && pass "screensaver after 2 h" || fail "screensaver timeout reset"
[ "$(tv settings get global window_animation_scale)" = 0.5 ] && pass "animations 0.5x" || fail "animations reset"
tv cmd location is-location-enabled 2>&1 | grep -qi false && pass "location off" || fail "location back on"
tv cmd appops get com.google.android.youtube.tv RUN_ANY_IN_BACKGROUND | grep -q ignore && pass "YouTube restricted in background" || fail "YouTube background restriction lost"
tv cmd appops get com.netflix.ninja RUN_ANY_IN_BACKGROUND | grep -q ignore && fail "Netflix is restricted (can cause the -14 warning)" || pass "Netflix unrestricted"
if tv pm list packages | grep -q '^package:local.tvhome$'; then
  tv settings get secure enabled_accessibility_services | grep -q 'local.tvhome/local.tvhome.HomeGuardService' && pass "home guard on (TV Home covers Google's home)" || fail "TV Home is installed but the home guard is OFF"
fi
name=$(tv settings get global device_name)
[ "$name" = "${BOX_NAME:-Kitchen TV}" ] && pass "name is $name" || fail "name is '$name'"

echo "== Casting =="
ip=$(tv ip -o -4 addr show wlan0 | awk '{print $4}' | cut -d/ -f1)
if [ -n "$ip" ]; then
  TV="$ip" python3 -I "$ROOT/castcheck.py" play >/tmp/castcheck.$$ 2>&1 && pass "cast test video on $ip: $(tail -1 /tmp/castcheck.$$ | cut -c1-40)" || fail "cast: $(tail -1 /tmp/castcheck.$$)"
  TV="$ip" python3 -I "$ROOT/castcheck.py" stop >/dev/null 2>&1; rm -f /tmp/castcheck.$$
else
  fail "could not read the box's Wi-Fi address"
fi

echo "== Memory =="
tv cat /proc/meminfo | awk '/MemAvailable/{printf "  available: %d MB\n", $2/1024}'
echo "$ok ok, $bad failed"
[ "$bad" -eq 0 ]
