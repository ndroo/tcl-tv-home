#!/usr/bin/env bash
# Health check: is the TV still the way disable.sh left it, and do casting + inputs work?
# Needs network debugging on (Settings > Device Preferences > Developer options > USB debugging).
set -u
TV="${TV:-192.168.2.50:5555}"
DIR="$(cd "$(dirname "$0")" && pwd)"
tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
ok=0; bad=0
pass() { echo "  ok    $*"; ok=$((ok+1)); }
fail() { echo "  FAIL  $*"; bad=$((bad+1)); }

adb connect "$TV" >/dev/null 2>&1
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "TV not connected (is USB debugging on?)"; exit 1; }
echo "TV up for $(tv cat /proc/uptime | awk '{print int($1/60)}') min"

echo "== Apps that should be off =="
off=$(tv pm list packages -d | sed 's/^package://')
for p in $(grep -vE '^[[:space:]]*(#|$)' "$DIR/debloat-list.txt" | awk '{print $1}'); do
  grep -qx "$p" <<<"$off" && pass "$p off" || fail "$p is ON again"
done

echo "== Things that must stay on =="
on=$(tv pm list packages -e | sed 's/^package://')
for p in com.google.android.apps.mediashell com.google.android.gms com.tcl.tv com.tcl.tvinput \
         com.android.providers.tv com.tcl.tcl_bt_rcu_service com.tcl.keycustomfunctionservice local.tvhome \
         fm.triplec.tv; do
  grep -qx "$p" <<<"$on" && pass "$p on" || fail "$p is OFF"
done

echo "== Settings =="
[ "$(tv settings get system screen_off_timeout)" = 7200000 ] && pass "screensaver after 2 h" || fail "screensaver timeout reset"
[ "$(tv settings get global window_animation_scale)" = 0.5 ] && pass "animations 0.5x" || fail "animations reset"
[ -z "$(tv settings get secure location_providers_allowed)" ] && pass "location off" || fail "location back on"
for p in com.google.android.youtube.tv; do
  tv cmd appops get $p RUN_ANY_IN_BACKGROUND | grep -q ignore && pass "$p restricted in background" || fail "$p background restriction lost"
done
# Netflix must be left as TCL shipped it, or it warns "not optimized for Netflix (-14)".
tv cmd appops get com.netflix.ninja RUN_ANY_IN_BACKGROUND | grep -q ignore && fail "Netflix is restricted (causes -14 warning)" || pass "Netflix unrestricted"
tv dumpsys deviceidle whitelist | grep -q "^system,com.netflix.ninja" && pass "Netflix on its power whitelist" || fail "Netflix off its power whitelist (causes -14 warning)"
home=$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
[ "$home" = "local.tvhome/.HomeActivity" ] && pass "home screen is TV Home" || fail "home screen is $home"

echo "== Casting =="
python3 -I "$DIR/castcheck.py" play >/tmp/castcheck.$$ 2>&1 && pass "cast test video: $(tail -1 /tmp/castcheck.$$ | cut -c1-40)" || fail "cast: $(tail -1 /tmp/castcheck.$$)"
python3 -I "$DIR/castcheck.py" stop >/dev/null 2>&1; rm -f /tmp/castcheck.$$

echo "== Inputs =="
n=$(tv dumpsys tv_input | grep -oE "TvInputInfo\{id=[^,]*passthroughinput[^,]*" | sort -u | wc -l | tr -d ' ')
[ "$n" -ge 3 ] && pass "$n passthrough inputs registered" || fail "only $n passthrough inputs"

echo "== Memory =="
tv cat /proc/meminfo | awk '/MemAvailable/{printf "  available: %d MB\n", $2/1024}'
echo "$ok ok, $bad failed"
[ "$bad" -eq 0 ]
