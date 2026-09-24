#!/usr/bin/env bash
# Kelime Atölyesi APK'sını Android SDK olmadan, Maven Central'daki araçlarla derler.
# Kullanım: android/build.sh   → çıktı: dist/KelimeAtolyesi.apk
set -euo pipefail

KOK="$(cd "$(dirname "$0")/.." && pwd)"
AND="$KOK/android"
ARAC="$AND/.tools"
OUT="$AND/.build"
C=https://repo.maven.apache.org/maven2

mkdir -p "$ARAC" "$KOK/dist"
indir() { [ -f "$ARAC/$2" ] || curl -sSfL --retry 4 --retry-delay 3 -o "$ARAC/$2" "$C/$1/$2"; }
indir io/github/reandroid/ARSCLib/1.4.0 ARSCLib-1.4.0.jar
indir com/android/tools/build/apksig/2.3.0 apksig-2.3.0.jar
indir com/jakewharton/android/repackaged/dalvik-dx/16.0.1 dalvik-dx-16.0.1.jar
indir org/robolectric/android-all/14-robolectric-10818077 android-all-14-robolectric-10818077.jar
ANDROID_JAR="$ARAC/android-all-14-robolectric-10818077.jar"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/tools" "$OUT/apk/root/assets" "$OUT/apk/resources/package_1"

echo "1/6 Java derleniyor"
javac -nowarn --release 8 -cp "$ANDROID_JAR" -encoding UTF-8 \
  -d "$OUT/classes" $(find "$AND/src" -name '*.java')

echo "2/6 DEX oluşturuluyor"
java -cp "$ARAC/dalvik-dx-16.0.1.jar" com.android.dx.command.Main --dex --min-sdk-version=24 \
  --output="$OUT/apk/root/classes.dex" "$OUT/classes"

echo "3/6 Dosyalar yerleştiriliyor"
cp "$AND/AndroidManifest.xml" "$OUT/apk/"
cp -r "$AND/res" "$OUT/apk/resources/package_1/"
printf '{"package_id":127,"package_name":"com.mmurat.kelimeatolyesi"}' > "$OUT/apk/resources/package_1/package.json"
cp "$KOK/kelime-atolyesi.html" "$OUT/apk/root/assets/index.html"

echo "4/6 Manifest ve kaynaklar kodlanıyor"
javac -nowarn -cp "$ARAC/ARSCLib-1.4.0.jar:$ARAC/apksig-2.3.0.jar" -d "$OUT/tools" "$AND"/build-tools/*.java
java -cp "$ARAC/ARSCLib-1.4.0.jar:$OUT/tools" Encode "$OUT/apk" "$OUT/unsigned.apk"

echo "5/6 Hizalanıyor"
python3 "$AND/build-tools/align.py" "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "6/6 İmzalanıyor"
KS="$AND/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass android -keypass android \
    -alias kelime -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Kelime Atolyesi, O=mmurat234, C=TR"
fi
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED -cp "$ARAC/apksig-2.3.0.jar:$OUT/tools" Sign "$OUT/aligned.apk" "$KOK/dist/KelimeAtolyesi.apk" "$KS" android kelime

ls -la "$KOK/dist/KelimeAtolyesi.apk"
