#!/bin/bash
# 从官方 chat-demo 上游源码重建静态站点到 assets/site/
# 需要 Node.js 22.12+ 与 pnpm（本沙箱 Node 20 也能构建成功）
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ="$(cd "$SCRIPT_DIR/.." && pwd)"
UP=$PROJ/upstream/chat-demo

echo "===[web/0] 安装前端依赖 ==="
cd "$UP"
if [ ! -d node_modules ]; then
  command -v pnpm >/dev/null 2>&1 || npm install -g pnpm@10.8.1
  pnpm install --frozen-lockfile
else
  echo "依赖已存在，跳过安装"
fi

echo "===[web/1] 构建静态站点 ==="
npm run build

echo "===[web/2] 同步到 assets/site ==="
rm -rf "$PROJ/assets/site"
mkdir -p "$PROJ/assets/site"
cp -r "$UP/dist/." "$PROJ/assets/site/"
echo "站点文件数: $(find "$PROJ/assets/site" -type f | wc -l)"
