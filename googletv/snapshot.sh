#!/usr/bin/env bash
# Save a Chromecast with Google TV's apps and settings to backup/cottage-googletv/ - run once, BEFORE disable.sh.
# googletv/restore.sh puts the box back to this state. backup/ holds device identifiers, so it's kept out of git.
#
# TV = the adb target: the serial `adb devices` shows after pairing (default: the first device listed).
# Pair first (Settings > System > Developer options > Wireless debugging > Pair with code):
#   adb pair <ip>:<pairing port> <code>      (if that fails with "protocol fault": adb kill-server; adb start-server)
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TV="${TV:-$(adb devices | awk -F'\t' 'NR>1 && $2=="device"{print $1; exit}')}"
tv() { adb -s "$TV" shell "$@" </dev/null; }

[ -n "$TV" ] && [ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Box not connected (pair it first, see the top of this file)."; exit 1; }
D="$ROOT/backup/cottage-googletv"
[ -e "$D" ] && { echo "$D already exists; move it aside first if you really want a new 'before' snapshot."; exit 1; }
mkdir -p "$D" && cd "$D"
tv getprop > getprop.txt
tv pm list packages -f > packages-all-with-paths.txt
tv pm list packages -s | sort > packages-system.txt
tv pm list packages -3 | sort > packages-user-installed.txt
tv pm list packages -d | sort > packages-already-disabled.txt
tv pm list packages -e | sort > packages-enabled.txt
tv cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN > home-launcher.txt
tv settings list global > settings-global.txt
tv settings list secure > settings-secure.txt
tv settings list system > settings-system.txt
tv dumpsys meminfo > meminfo-before.txt
echo "Saved to $D: $(wc -l < packages-enabled.txt | tr -d ' ') enabled apps, home = $(tail -1 home-launcher.txt | tr -d '\r')"
