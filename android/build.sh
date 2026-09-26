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
# bundletool Maven Central'da yok; Google'ın resmî GitHub sürümünden alınır
[ -f "$ARAC/bundletool-all-1.18.1.jar" ] || curl -sSfL --retry 4 -o "$ARAC/bundletool-all-1.18.1.jar" \
  https://github.com/google/bundletool/releases/download/1.18.1/bundletool-all-1.18.1.jar
ANDROID_JAR="$ARAC/android-all-14-robolectric-10818077.jar"

rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/tools" "$OUT/apk/root/assets" "$OUT/apk/resources/package_1"

echo "1/8 Java derleniyor"
javac -nowarn --release 8 -cp "$ANDROID_JAR" -encoding UTF-8 \
  -d "$OUT/classes" $(find "$AND/src" -name '*.java')

echo "2/8 DEX oluşturuluyor"
java -cp "$ARAC/dalvik-dx-16.0.1.jar" com.android.dx.command.Main --dex --min-sdk-version=24 \
  --output="$OUT/apk/root/classes.dex" "$OUT/classes"

echo "3/8 Dosyalar yerleştiriliyor"
cp "$AND/AndroidManifest.xml" "$OUT/apk/"
cp -r "$AND/res" "$OUT/apk/resources/package_1/"
printf '{"package_id":127,"package_name":"com.mmurat.kelimeatolyesi"}' > "$OUT/apk/resources/package_1/package.json"
python3 "$KOK/yokdil/build.py"
{ printf '<!doctype html><html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>\n'
  cat "$KOK/kelime-atolyesi.html"; printf '\n</body></html>\n'; } > "$OUT/index.tmp.html"
# Uygulama internetsiz çalışır: Google Fonts bağlantıları yerine gömülü yazı tipleri
python3 - "$OUT/index.tmp.html" "$OUT/apk/root/assets/index.html" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
s, n = re.subn(r'<link rel="(?:preconnect|stylesheet)" href="https://fonts\.(?:googleapis|gstatic)\.com[^>]*>\n?', "", s)
assert n == 3, n
s = s.replace("<title>", '<link rel="stylesheet" href="fonts/fonts.css">\n<title>', 1)
assert "googleapis" not in s and "gstatic" not in s
open(sys.argv[2], "w", encoding="utf-8").write(s)
PY
mkdir -p "$OUT/apk/root/assets/fonts" && cp "$AND"/fonts/* "$OUT/apk/root/assets/fonts/"

echo "4/8 Manifest ve kaynaklar kodlanıyor"
javac -nowarn -encoding UTF-8 -cp "$ARAC/ARSCLib-1.4.0.jar:$ARAC/apksig-2.3.0.jar:$ARAC/bundletool-all-1.18.1.jar" -d "$OUT/tools" "$AND"/build-tools/*.java
java -cp "$ARAC/ARSCLib-1.4.0.jar:$OUT/tools" Encode "$OUT/apk" "$OUT/unsigned.apk"

echo "5/8 Hizalanıyor"
python3 "$AND/build-tools/align.py" "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "6/8 APK imzalanıyor"
KS="$AND/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass android -keypass android \
    -alias kelime -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Kelime Atolyesi, O=mmurat234, C=TR"
fi
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED --add-exports java.base/sun.security.util=ALL-UNNAMED -cp "$ARAC/apksig-2.3.0.jar:$OUT/tools" Sign "$OUT/aligned.apk" "$KOK/dist/KelimeAtolyesi.apk" "$KS" android kelime

ls -la "$KOK/dist/KelimeAtolyesi.apk"

echo "7/8 Google Play paketi (AAB) oluşturuluyor"
java -cp "$ARAC/ARSCLib-1.4.0.jar:$ARAC/bundletool-all-1.18.1.jar:$OUT/tools" ProtoYaz \
  "$OUT/unsigned.apk" "$AND/AndroidManifest.xml" "$AND/res" com.mmurat.kelimeatolyesi "$OUT/base.zip"
rm -f "$OUT/app.aab"
java -jar "$ARAC/bundletool-all-1.18.1.jar" build-bundle --modules="$OUT/base.zip" --output="$OUT/app.aab"
java -jar "$ARAC/bundletool-all-1.18.1.jar" validate --bundle="$OUT/app.aab" > /dev/null

echo "8/8 AAB yükleme anahtarıyla imzalanıyor"
# Yükleme anahtarı depoda tutulmaz (.gitignore). Yoksa bu adım atlanır.
if [ -f "$AND/upload.keystore" ] && [ -f "$AND/upload.parola" ]; then
  P="$(cat "$AND/upload.parola")"
  jarsigner -keystore "$AND/upload.keystore" -storetype PKCS12 -storepass "$P" -keypass "$P" \
    -sigalg SHA256withRSA -digestalg SHA-256 "$OUT/app.aab" yukleme > /dev/null
  jarsigner -verify "$OUT/app.aab" | grep -q "jar verified"
  cp "$OUT/app.aab" "$KOK/dist/YokdilKelime.aab"
  ls -la "$KOK/dist/YokdilKelime.aab"
else
  echo "Yükleme anahtarı bulunamadı; imzasız AAB: $OUT/app.aab"
fi
