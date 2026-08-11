#!/usr/bin/env bash
# 一键：构建 Debug APK → 复制到仓库根目录
set -e
cd "$(dirname "$0")"

echo "==> 1/2 构建 Debug APK"
if [[ -z "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
./gradlew :app:assembleDebug

echo "==> 2/2 复制 APK"
APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
  echo "未找到 APK: $APK" >&2
  exit 1
fi
cp "$APK" "./MalodyBSC-android-debug.apk"
echo ""
echo "✅ 完成！APK 位置: $(pwd)/MalodyBSC-android-debug.apk"
