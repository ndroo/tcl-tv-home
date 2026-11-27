#!/usr/bin/env bash
# Build tvhome.apk with the plain Android SDK tools (no Gradle needed).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-30/android.jar"   # compiled against 30; code only uses API <= 28

rm -rf build && mkdir -p build/classes build/dex build/assets
# "Get the Wi-Fi" screen: network details + a QR code made by macOS (self-checked by decoding).
if [ -f wifi.conf ]; then
  cp wifi.conf build/assets/wifi.conf
  swift make_wifi_qr.swift wifi.conf build/assets/wifi_qr.png
fi
# Cameras tile: the camera site, the house it opens and this TV's device key
[ -f cameras.conf ] && cp cameras.conf build/assets/cameras.conf
# Our own names for the input tiles (HDMI 2 = PlayStation, ...)
[ -f inputs.conf ] && cp inputs.conf build/assets/inputs.conf
# java.* from the JDK's Java 8 API (lambdas need it), android.* from android.jar; d8 desugars.
javac --release 8 -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --release --min-api 28 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml -A build/assets
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk

# Local signing key for this one sideloaded app (keep it: updates must use the same key).
KS_PASS="${KEYSTORE_PASS:-tvhome-local}"   # protects only the local keystore (kept out of git)
if [ ! -f tvhome.keystore ]; then
  keytool -genkeypair -keystore tvhome.keystore -alias tvhome -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=TV Home" >/dev/null
fi
"$BT/apksigner" sign --ks tvhome.keystore --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out tvhome.apk build/aligned.apk
"$BT/apksigner" verify tvhome.apk && echo "built $(pwd)/tvhome.apk ($(du -h tvhome.apk | cut -f1))"
