#!/usr/bin/env bash
# Save the TV's current apps and settings to backup/ - run this once, BEFORE disable.sh.
# restore.sh puts the TV back to exactly this state. backup/ holds device identifiers, so it's
# kept out of git.
set -eu
TV="${TV:-192.168.2.50:5555}"
DIR="$(cd "$(dirname "$0")" && pwd)"
tv() { adb -s "$TV" shell "$@" </dev/null; }

adb connect "$TV" >/dev/null 2>&1
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "TV not connected (or not approved on the TV)."; exit 1; }
if [ -d "$DIR/backup" ] && [ -n "$(ls -A "$DIR/backup" 2>/dev/null)" ]; then
  echo "backup/ already exists; move it aside first if you really want a new 'before' snapshot."; exit 1
fi
mkdir -p "$DIR/backup" && cd "$DIR/backup"
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
echo "Saved to backup/: $(wc -l < packages-enabled.txt | tr -d ' ') enabled apps, home = $(tail -1 home-launcher.txt)"
