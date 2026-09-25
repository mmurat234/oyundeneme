#!/usr/bin/env bash
# Pamuk'u Yakala! — Android Studio/Gradle gerektirmeyen APK derleme betiği.
# Gerekenler: JDK 11+, curl, python3 (pip). Araçlar ilk çalıştırmada buildtools/cache içine indirilir.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(pwd)
CACHE=$ROOT/buildtools/cache
OUT=$ROOT/build
MAVEN=https://repo1.maven.org/maven2
mkdir -p "$CACHE"

fetch() { # url hedef
  [ -s "$2" ] || curl -fsSL --retry 4 -o "$2" "$1"
}

# 1) Araçlar
fetch https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar "$CACHE/android.jar"
fetch $MAVEN/com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar "$CACHE/dx.jar"
fetch $MAVEN/com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar "$CACHE/apksig.jar"
if [ ! -x "$CACHE/aapt2" ]; then
  python3 -m pip download aapt2==0.2.1 --no-deps -q -d "$CACHE/whl"
  (cd "$CACHE/whl" && python3 -m zipfile -e aapt2-0.2.1-py3-none-any.whl x)
  cp "$CACHE/whl/x/aapt2/bin/Linux/aapt2" "$CACHE/aapt2" && chmod +x "$CACHE/aapt2"
fi
AAPT2=$CACHE/aapt2

rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex,tools}

# 2) İkon
javac -d "$OUT/tools" buildtools/IconGen.java
java -Djava.awt.headless=true -cp "$OUT/tools" IconGen "$OUT/res"
cp -r res/* "$OUT/res/"

# 3) Kaynaklar
"$AAPT2" compile --dir "$OUT/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/base.apk" -I "$CACHE/android.jar" --manifest AndroidManifest.xml \
  --java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 33 --version-code 1 --version-name 1.0 \
  "$OUT/res.zip"

# 4) Java -> class -> dex
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath "$CACHE/android.jar" \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v "bootstrap\|source value 8\|target value 8\|To suppress\|^warning: \[options\]\|^[0-9] warning" || true
java -cp "$CACHE/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=24 --output="$OUT/dex/classes.dex" "$OUT/classes"

# 5) dex'i APK'ya ekle
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && python3 - "$OUT/unsigned.apk" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('classes.dex', 'classes.dex')
PY
)

# 6) İmza anahtarı (yoksa oluştur) ve imzalama
KS=$ROOT/buildtools/pamuk.p12
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass pamuk123 -keypass pamuk123 \
    -alias pamuk -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pamuk Oyunu, O=Mihrimah ve Murat, C=TR"
fi
javac -cp "$CACHE/apksig.jar" -d "$OUT/tools" buildtools/Signer.java
java --add-exports java.base/sun.security.x509=ALL-UNNAMED -cp "$CACHE/apksig.jar:$OUT/tools" Signer "$OUT/unsigned.apk" "$ROOT/PamukuYakala.apk" "$KS" pamuk123 pamuk

echo "Hazır: $ROOT/PamukuYakala.apk"
