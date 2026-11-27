# TV Home: a fast, ad-free, reversible TCL Android TV

A slimmed-down setup for a **TCL 50S434-CA** (Android TV 9, Realtek RTD2851, 2 GB RAM): switch
inputs, work like a monitor, keep **Chromecast built-in**, lose the ads and bloat. Everything is
done over ADB with stock Android commands (apps are *disabled*, never uninstalled from the system),
and `restore.sh` puts the TV back exactly as it was.

It includes **TV Home**, a ~40 KB replacement home screen written for this TV: your HDMI inputs,
your TV apps (show/hide in its Settings), a guest Wi-Fi QR code, a full-screen camera wall, and the
TV's own settings. No ads, no recommendation rows, about 9 MB of RAM instead of 60–110 MB.

Built with the plain Android SDK tools (no Gradle, no Android Studio) on macOS.

> Tested on one TCL model and firmware. Package names and behaviour differ between models; read
> `debloat-list.txt` before running anything on another TV, and keep the snapshot.

## What it changes

| Change | Why | Undo |
|---|---|---|
| 19 apps disabled (`debloat-list.txt`) | voice search, TCL assistant / Alexa / telemetry / app store / cloud login, TCL's remote app switcher, recommendations, demo-mode apps, the stock home screen | `restore.sh` |
| **TV Home** (`tvhome/`) is the home screen | inputs · a row of TV apps (order in `Apps.java`, hide any in Settings) · Wi-Fi · Cameras · Settings · clock | `restore.sh` re-enables the stock one and uninstalls this |
| YouTube restricted in background | it started itself at every boot; it still opens when used or cast to | `restore.sh` |
| Animations 0.5× | snappier menus | `restore.sh` |
| Screensaver after 2 h instead of 5 min | it covered casts 5 min after the last remote press | `restore.sh` |
| Location off, background Wi-Fi scanning off | a TV doesn't need them | `restore.sh` |
| Chromecast, TV Home, TCL input + settings apps fully compiled (`speed`) | Chromecast's cast module was only interpreted | harmless; Play Store updates reset it |

Kept on purpose: Chromecast (`mediashell`), Play Services, Play Store (keeps Chromecast and
YouTube updated), HDMI/input services, the remote's services, system updates, and
YouTube/Netflix/Prime (on Android TV they are what plays some casts). Free RAM went from ~470 MB to
~650–700 MB.

## Setting it up

You need `adb`, a JDK (17), the Android SDK build-tools and a platform jar (`build.sh` looks in
`/opt/homebrew/share/android-commandlinetools`; set `ANDROID_SDK` otherwise), and `swift` for the
Wi-Fi QR code.

1. **Turn on debugging on the TV**: Settings → Device Preferences → About → press *Build* 7×,
   then Device Preferences → Developer options → *USB debugging* on. Approve this computer when
   the TV asks.
2. **Point the scripts at the TV**: they default to `192.168.2.50:5555`; use `TV=<ip>:5555 ./disable.sh` etc.
3. **Snapshot first**: `./snapshot.sh` saves every app and setting to `backup/` (kept out of git:
   it holds device identifiers). `restore.sh` returns the TV to this state.
4. **Configure** (optional; both files are ignored by git and baked into the app at build time):
   - `cp tvhome/wifi.conf.example tvhome/wifi.conf` — network name and password for the
     *Get the Wi-Fi* page (QR code + details for guests).
   - `cp tvhome/cameras.conf.example tvhome/cameras.conf` — the *Cameras* tile (see below).
5. **Build**: `tvhome/build.sh` (and `triplec-tv/build.sh` if you want that app). Each makes its
   own signing key on first run: keep `*.keystore` somewhere safe, since updates need the same key.
6. **Apply**: `./disable.sh` (installs TV Home, disables the list, applies the settings). Re-run it
   after a TCL system update switches things back on.
7. **Check**: `./check.sh` (apps, settings, home screen, a real test cast, inputs).
8. **Lock it down**: `adb shell settings put global adb_enabled 0`, or turn *USB debugging* off.
   Anything on your Wi-Fi can talk to an open ADB port.

Undo everything: turn debugging back on, then `./restore.sh`.

## Files

- `snapshot.sh` / `disable.sh` / `restore.sh` / `check.sh` — see above.
- `debloat-list.txt` — what gets disabled and why, plus what must never be.
- `castcheck.py` — a tiny Chromecast client (standard library only, no ADB needed):
  `python3 castcheck.py status|play|launch <appId>|stop`.
- `tvhome/` — the home screen (`HomeActivity`), its Settings (TV settings + show/hide apps),
  the Wi-Fi page, the Cameras screen, line icons drawn in code; `make_wifi_qr.swift` makes the QR
  with macOS CoreImage and the build refuses to continue unless it decodes back exactly.
- `triplec-tv/` — an app for the radio station [triplec.fm](https://triplec.fm): it loads the
  station's own Chromecast receiver page unchanged and swaps Google's cast framework script for
  `assets/caf_shim.js`, which plays the stream in an `<audio>` element and raises the same player
  events, so the TV shows exactly the cast UI with no phone. A pattern that works for any custom
  Cast receiver.

## The Cameras tile

It opens a camera site's full-screen multi-view for one property, signed in with a **device
key** sent as `Authorization: Bearer <key>` to `/device/login` (never in a URL). The column on the
left switches property; OK on a camera fills the screen with it in HD; Back returns to the grid,
and Back on the grid leaves. The site side lives in [ndroo/cameras](https://github.com/ndroo/cameras)
(device keys, `/wall?house=<slug>&tv=1`, `/stream/<id>/wall.mjpeg`).

Why the wall shows **pictures, not video**: this TV's chip decodes only **two H.264 videos at
once** (`/vendor/etc/media_codecs_realtek_video.xml`: `concurrent-instances max="2"`; a third
fails with `InsufficientResources` and the player retries forever), and its WebView reports no
software H.264 decoder. So the server turns each camera's sub-stream into MJPEG (640 wide, 5 a
second) and every tile moves; OK opens one camera in HD (one real decoder) with a sound toggle.

## Things learned

- `com.tcl.appstatecontroller` downloads a YES/NO app list from TCL at every boot and switches apps
  on or off to match (it re-enabled Alexa). `disable.sh` disables it first.
- Any screensaver starts N minutes after the last remote press, even over a cast from a
  web-receiver app.
- The stock launcher is a system app with HOME priority 2, so "set default home" is ignored until
  it's disabled.
- Netflix and Prime Video are on TCL's system power whitelist, which makes Android's background
  restriction a no-op for them. Taking Netflix off it saves ~95 MB but makes Netflix show
  "device is not optimized for Netflix (-14)" on cold start, so it's left as shipped.
- Android clears background restrictions on updated *system* apps at every boot (Google TTS).
- Android WebView draws a huge grey play icon over unstarted videos; return a 1×1 bitmap from
  `WebChromeClient.getDefaultVideoPoster()`.
- Clearing a `<video>`'s `src` doesn't free its hardware decoder straight away;
  `removeAttribute('src')` + `load()` does.
- go2rtc's `video-rtc.js` player takes its settings as JS properties, not HTML attributes: a
  `mode="mse"` attribute is ignored, so it also tries WebRTC, which takes over whenever it connects.
- Tapo's "VGA" relay stream is really 1280×720 (1280×960 on a doorbell).

## License

[MIT](LICENSE). Use at your own risk: it changes how your TV runs, and models differ.
