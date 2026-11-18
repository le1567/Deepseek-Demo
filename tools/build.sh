#!/usr/bin/env bash
# DeepSeek App 一键构建脚本（免 Gradle）
# 用法：bash tools/build.sh
# 工具链路径可用环境变量覆盖：BT / AJ / R8 / APKSIG / GUAVA
# 签名密码建议用环境变量设置：KS_PASS（默认 deepseek123，仅用于本地演示）
set -e

# 工程根目录 = 本脚本所在目录的上级（可移植，不依赖固定路径）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ="$(cd "$SCRIPT_DIR/.." && pwd)"

# 工具链：默认沙箱路径，可通过环境变量覆盖为你本地的 Android SDK
BT="${BT:-${ANDROID_HOME:-/root/atc/sdk}/bt/android-14}"
AJ="${AJ:-${ANDROID_HOME:-/root/atc/sdk}/android-35/android.jar}"
R8="${R8:-/root/dl/r8.jar}"
APKSIG="${APKSIG:-/root/dl/apksig.jar}"
GUAVA="${GUAVA:-/root/dl/guava.jar}"

BUILD="${BUILD:-/tmp/dsbuild}"
KS="${KS:-$PROJ/tools/deepseek.keystore}"
KS_PASS="${KS_PASS:-deepseek123}"
KS_ALIAS="${KS_ALIAS:-deepseek}"

echo "工程目录: $PROJ"
echo "===[0/7] 工具链检查 ==="
for f in "$BT/aapt2" "$BT/zipalign" "$AJ" "$R8" "$APKSIG" "$GUAVA"; do
  if [ ! -f "$f" ]; then
    echo "缺少 $f"
    echo "请准备: build-tools r34(aapt2/zipalign), platform-35(android.jar), r8 8.3.37, apksig 8.2.2, guava 31.1"
    echo "并设置环境变量 BT / AJ / R8 / APKSIG / GUAVA 指向这些文件"
    exit 1
  fi
done

if [ ! -d "$PROJ/assets/site" ] || [ ! -f "$PROJ/assets/site/index.html" ]; then
  echo "!! 未找到 assets/site 静态站点，请先运行: bash tools/build-web.sh"
  exit 1
fi

rm -rf "$BUILD" && mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dexout" "$BUILD/res"
cp -r "$PROJ/res/." "$BUILD/res/"

echo "===[1/7] 签名 keystore ==="
if [ ! -f "$KS" ]; then
  if [ "$KS_PASS" = "deepseek123" ]; then
    echo "警告: 使用默认演示密码构建。正式分发请设置 KS_PASS 环境变量并妥善保管 keystore。"
  fi
  keytool -genkeypair -v -keystore "$KS" -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=DeepSeek App, OU=Community, O=OpenSource, L=Beijing, ST=Beijing, C=CN" >/dev/null 2>&1
  echo "keystore 已生成: $KS （请妥善备份，丢失后无法覆盖安装）"
else
  echo "复用已有 keystore: $KS"
fi

echo "===[2/7] aapt2 compile ==="
"$BT/aapt2" compile --dir "$BUILD/res" -o "$BUILD/res.zip"

echo "===[3/7] aapt2 link（含 assets/site 静态站点）==="
"$BT/aapt2" link -o "$BUILD/base.apk" -I "$AJ" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -A "$PROJ/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 1 --version-name 1.0.0 \
  "$BUILD/res.zip"

echo "===[4/7] javac 编译 Java 源码 ==="
find "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
find "$PROJ/src" -name '*.java' >> "$BUILD/sources.txt"
set +e
javac -source 8 -target 8 -encoding UTF-8 \
  -classpath "$AJ" \
  -d "$BUILD/classes" @"$BUILD/sources.txt" > "$BUILD/javac.log" 2>&1
JAVAC_RC=$?
set -e
if [ "$JAVAC_RC" -ne 0 ]; then echo "!! javac 编译失败，中止构建"; cat "$BUILD/javac.log"; exit 1; fi
grep -v 'bootstrap class path' "$BUILD/javac.log" | grep -v '源值 8' | grep -v '目标值 8' | grep -v 'source value 8' || true

echo "===[5/7] D8 生成 classes.dex ==="
find "$BUILD/classes" -name '*.class' > "$BUILD/classlist.txt"
java -cp "$R8" com.android.tools.r8.D8 \
  --release --lib "$AJ" --min-api 24 \
  --output "$BUILD/dexout" @"$BUILD/classlist.txt"

echo "===[6/7] 追加 classes.dex 并 zipalign ==="
python3 - "$BUILD/base.apk" "$BUILD/dexout/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
print('classes.dex 已追加')
PY
"$BT/zipalign" -f -p 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

echo "===[7/7] 签名 ==="
OUT="${OUT:-$PROJ/dist}"
mkdir -p "$OUT" /tmp/dsapk-sign
javac -cp "$APKSIG" -d "$BUILD" "$PROJ/tools/SignApk.java"
java -cp "$APKSIG:$BUILD" SignApk "$KS" "$KS_PASS" "$KS_ALIAS" "$KS_PASS" \
  "$BUILD/aligned.apk" "/tmp/dsapk-sign/DeepSeek.apk"
cp /tmp/dsapk-sign/DeepSeek.apk "$OUT/DeepSeek-v1.0.0.apk"

echo "" && echo "========== 构建完成 =========="
ls -lh "$OUT/DeepSeek-v1.0.0.apk"
"$BT/apksigner" verify --print-certs "$OUT/DeepSeek-v1.0.0.apk" 2>&1 | grep -E 'Signer|Verified|v2|v3' | head -6 || true
