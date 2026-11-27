#!/usr/bin/env bash
# Build triplec-tv.apk (fm.triplec.tv) with the plain Android SDK tools (no Gradle needed).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK:-/opt/homebrew/share/android-commandlinetools}"
BT="$SDK/build-tools/36.0.0"
JAR="$SDK/platforms/android-30/android.jar"   # compiled against 30; code only uses API <= 28

rm -rf build && mkdir -p build/classes build/dex
javac --release 8 -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --release --min-api 28 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml -A assets build/res.zip
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -p -f 4 build/unsigned.apk build/aligned.apk

# Local signing key for this sideloaded app (keep it: updates must use the same key).
KS_PASS="${KEYSTORE_PASS:-triplec-tv-local}"   # protects only the local keystore (kept out of git)
if [ ! -f triplec-tv.keystore ]; then
  keytool -genkeypair -keystore triplec-tv.keystore -alias triplec-tv -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" -dname "CN=Triple C TV" >/dev/null
fi
"$BT/apksigner" sign --ks triplec-tv.keystore --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out triplec-tv.apk build/aligned.apk
"$BT/apksigner" verify triplec-tv.apk && echo "built $(pwd)/triplec-tv.apk ($(du -h triplec-tv.apk | cut -f1))"
