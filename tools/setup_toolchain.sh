#!/usr/bin/env bash
# 一键重建 Android 构建工具链（免 Gradle）
# 下载到 /root/atc 与 /root/dl；沙箱 /root 会被周期性重置，重置后重跑本脚本即可。
set -e
mkdir -p /root/dl /root/atc/sdk/bt

echo "===[1/4] build-tools r34 ==="
if [ ! -f /root/atc/sdk/bt/android-14/aapt2 ]; then
  curl -fL --max-time 300 -o /root/dl/build-tools_r34-linux.zip \
    'https://mirrors.cloud.tencent.com/AndroidSDK/build-tools_r34-linux.zip'
  cd /root/atc/sdk/bt && unzip -q -o /root/dl/build-tools_r34-linux.zip
fi

echo "===[2/4] platform-35 ==="
if [ ! -f /root/atc/sdk/android-35/android.jar ]; then
  curl -fL --max-time 300 -o /root/dl/platform-35_r01.zip \
    'https://dl.google.com/android/repository/platform-35_r01.zip'
  cd /root/atc/sdk && unzip -q -o /root/dl/platform-35_r01.zip
fi

echo "===[3/4] r8 / apksig / guava ==="
NX='https://mirrors.cloud.tencent.com/nexus/repository/maven-public'
[ -f /root/dl/r8.jar ] || curl -fL --max-time 180 -o /root/dl/r8.jar "$NX/com/android/tools/r8/8.3.37/r8-8.3.37.jar"
[ -f /root/dl/apksig.jar ] || curl -fL --max-time 180 -o /root/dl/apksig.jar "$NX/com/android/tools/build/apksig/8.2.2/apksig-8.2.2.jar"
[ -f /root/dl/guava.jar ] || curl -fL --max-time 180 -o /root/dl/guava.jar "$NX/com/google/guava/guava/31.1-android/guava-31.1-android.jar"

echo "===[4/4] 校验 ==="
for f in /root/atc/sdk/bt/android-14/aapt2 /root/atc/sdk/bt/android-14/zipalign \
         /root/atc/sdk/android-35/android.jar /root/dl/r8.jar /root/dl/apksig.jar /root/dl/guava.jar; do
  [ -f "$f" ] && echo "OK $f" || { echo "FAIL $f"; exit 1; }
done
echo "工具链就绪"
