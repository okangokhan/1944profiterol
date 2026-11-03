#!/usr/bin/env bash
# Release APK'yı derler ve anahtar döndürme zinciriyle (eski debug anahtarı → yeni Ajanda anahtarı) imzalar.
# Böylece eski anahtarla kurulmuş uygulamalar da (Android 9+) güncelleme alır.
# Gerekenler keystore/ içinde: keystore.properties, ajanda-release.jks, eski-ajanda.keystore, ajanda.lineage
set -euo pipefail
cd "$(dirname "$0")"
source <(grep -E '^(storePassword|keyAlias)=' keystore/keystore.properties)
BT="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" | sort -V | tail -1)"
./gradlew :app:assembleRelease
VERSION=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
OUT="../apk/Ajanda-v$VERSION.apk"
mkdir -p ../apk
"$BT/apksigner" sign --out "$OUT" \
  --ks keystore/eski-ajanda.keystore --ks-pass pass:android --ks-key-alias androiddebugkey \
  --next-signer --ks keystore/ajanda-release.jks --ks-pass "pass:$storePassword" --ks-key-alias "$keyAlias" \
  --lineage keystore/ajanda.lineage --rotation-min-sdk-version 28 --v4-signing-enabled false \
  app/build/outputs/apk/release/app-release.apk
"$BT/apksigner" verify --verbose --print-certs "$OUT" | grep -E "Verified using|Signer|DN"
echo "Hazır: $OUT"
