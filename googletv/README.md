# googletv/: a Chromecast with Google TV for a TV you can't modify

**Device type.** This folder is for a **Chromecast with Google TV (4K, codename "sabrina", Android 14)** plugged
into a TV that can't be changed itself. It was built for a **TCL Roku TV** (32S327-CA). The scripts in the
repo's root are for a TV that *is* an Android TV (a TCL 50S434-CA, Android TV 9). Pick by what your TV runs:

| | TV with Android TV built in (repo root) | TV you can't modify + a Chromecast with Google TV (this folder) |
|---|---|---|
| Example | TCL 50S434-CA, Android TV 9 | TCL 32S327-CA (Roku OS), plus the box on HDMI 2 |
| What gets changed | the TV | the box; the TV only gets a few menu settings |
| Scripts | `snapshot.sh` `disable.sh` `restore.sh` `check.sh` | the same four, here |
| Home screen | TV Home **is** the launcher; the stock one is disabled | TV Home opens **on top of** Google's home (the "home guard"); the stock launcher stays on |
| Casting | the TV's own Chromecast built-in | the box's Chromecast built-in (the Roku keeps AirPlay) |
| Android | 9 | 14 |

## What you get

- The TV powers up straight onto the box, and the box's remote turns the TV off too (HDMI-CEC).
- Real Chromecast casting under a name you choose (`BOX_NAME`, default "Kitchen TV").
- The box slimmed down the same reversible way as the TCL: apps turned off (`debloat-list.txt`), faster animations,
  location and background Wi-Fi scanning off, a snapshot before anything changes and a `restore.sh` after.
- Google TV's own **Apps only mode** (no recommendations rows), and **TV Home** (the same app as on the TCL) on top
  of Google's home.

## Setting it up

1. **The box.** Set it up with the Google Home app. Then Settings → System → About → tap *Build* 7×, Developer
   options → **Wireless debugging**, and pair: `adb pair <ip>:<pairing port> <code>`. The pairing popup has its own
   port and code, different from the main Wireless debugging screen. If `adb pair` says "protocol fault", run
   `adb kill-server` and try again. Wireless debugging switches itself off whenever the box restarts or its Wi-Fi
   reconnects; turn it back on and `adb mdns services` shows the new port. The box needs its own wall adapter (the
   TV's USB port can't power it) and has a single USB-C port, so a keyboard needs a hub that passes power through.
2. **The TV** (a Roku TV here; the names differ on other brands):
   - Settings → System → Power → **Power on** = the box's HDMI port.
   - Settings → System → **Control other devices (CEC)**: **1-Touch Play** on, and **System standby** on. Without
     System standby the TV *acknowledges* the box's CEC standby command and ignores it, so the box's remote only
     turns the box off.
   - Settings → Privacy → Smart TV experience → turn off *Use info from TV inputs* (Roku's content recognition of
     what's on its HDMI inputs), if the entry exists on your model.
   - Leave "Auto power savings" off: the box already sleeps the TV after it idles (20 min by default).
3. **The scripts.**
   ```
   googletv/snapshot.sh                 # once, before anything else: saves backup/cottage-googletv/ (git-ignored)
   CONF_DIR=<folder with wifi.conf, optional cameras.conf> OUT=backup/cottage-googletv/tvhome-cottage.apk tvhome/build.sh
   googletv/disable.sh                  # apps off, settings, Triple C, TV Home + the home guard
   googletv/check.sh                    # apps, settings, the guard, and a real test cast
   googletv/restore.sh                  # back to the snapshot
   ```
   `TV=<adb target>` picks the box (default: the first device `adb devices` lists), `HOME_GUARD=0` skips TV Home.
4. **Apps only mode** is Google's own setting: profile picture → Settings → Accounts & Profiles → your account →
   *Apps only mode*. It hides recommendations and turns off search and voice. Plex etc. come from the Play Store.

## The home guard

TV Home ships a small accessibility service, `HomeGuardService`. Android only tells it when the **stock Google TV home
window** appears (`res/xml/home_guard_config.xml` limits it to that one package; it reads no screen content), and when
TV Home isn't already showing it opens TV Home on top. Nothing is disabled, so Wi-Fi, the keyboard and the setup
wizard are untouched. Android 14 lets an accessibility service start the activity from the background. Google's home
may show for an instant before TV Home covers it. If the service ever launches more than 8 times in a minute it
stands down for a minute. `disable.sh` turns it on with
`settings put secure enabled_accessibility_services local.tvhome/local.tvhome.HomeGuardService` plus
`accessibility_enabled 1`; **undo any time** with `adb shell settings put secure enabled_accessibility_services ""`
(or `restore.sh`).

## How we got here

This started as "do for the kitchen TV what we did for the living-room TV". The living-room set is an Android TV, so
ADB, `pm disable-user` and a replacement launcher were all available. The kitchen TV was a TCL Roku TV, and the
order of events was:

1. **Roku TVs have no ADB.** The only network control is Roku's External Control Protocol on port 8060. With "Control
   by mobile apps" on *Limited* (the default here) even the channel list and key presses are refused. On *Enabled* you
   can read the channel list, send key presses and launch channels, but it can't install or remove a channel
   (`/install` answers 401), change a setting, or show what is on screen.
2. **Flashing or rooting it isn't possible.** The firmware is signed, there's no way to load another image, and the
   one public root, RootMyRoku, only worked on Roku OS 9.4.0 (build 4200) and earlier; its author says v10 removed the
   pieces it needs. This TV was on 15.3.4 (it updated itself to 16.0.4 along the way).
3. **Roku's Developer Mode is the one official way in**, and it only sideloads your own channel. It is switched on
   with a key sequence on the *physical* remote (Home ×3, Up ×2, Right, Left, Right, Left, Right); sending the same
   keys over the remote-control protocol does nothing. A channel can't replace the Roku home screen or start on
   power-up (the "Power on" setting only picks an input). The installer it opens is reachable by anything on the
   network, so turn Developer Mode off when you're done.
4. **So the Roku became a screen** and a Chromecast with Google TV took over: power on to its HDMI port, CEC on, and the
   box does everything else.
5. **Copying the TCL recipe broke the box.** On the TCL, TV Home becomes the home screen by disabling the stock
   launcher. Here TV Home launched fine when started directly, but with both Google launchers disabled every Home
   press went to a blank screen and the system restarted (which also turns Wireless debugging off). The cause: the
   setup wizard (`com.google.android.tungsten.setupwraith`) registers a fallback HOME activity with priority 1, above
   TV Home's 0, Android won't let a normal app claim a higher priority, and the shell isn't allowed to disable just
   that component. Making TV Home the default home (`cmd package set-home-activity`) with the launchers still on is
   ignored: the Home key keeps opening Google's launcher.
6. **Disabling the whole setup wizard was worse.** The box dropped off Wi-Fi, came back without its saved Wi-Fi
   password and with no on-screen keyboard. It needed a factory reset (hold the button on the back until the light
   is solid white) and a fresh setup from the Google Home app. We never proved which piece caused it.
7. **What worked:** leave every system piece alone, turn on Google's **Apps only mode**, disable the preinstalled streaming
   apps, and add the **home guard** so TV Home still gets its place on top. Along the way: the box's CEC standby
   needed the Roku's *System standby* setting, `adb pair` failures were cured by restarting the adb server, and a
   name with a space has to be quoted for `adb shell`.

## Rules for this box

- Never disable the home launchers (`com.google.android.apps.tv.launcherx`, `com.google.android.tvlauncher`), the setup
  wizard (`com.google.android.tungsten.setupwraith`), the Assistant (`com.google.android.katniss`) or the keyboard
  (`com.google.android.inputmethod.latin`). `check.sh` fails if any of them is off.
- Never press Home (or send `KEYCODE_HOME`) while no real launcher is enabled.
- Take the snapshot first, change one thing at a time, and check the box is still reachable after each step.
- A router DHCP reservation for the box stops its address changing after a restart.

## Files

- `snapshot.sh` / `disable.sh` / `restore.sh` / `check.sh`: as above.
- `debloat-list.txt`: what gets turned off, and what must never be.
- The home guard itself lives in `../tvhome/` (`HomeGuardService.java`, `res/xml/home_guard_config.xml`).
