#!/usr/bin/env bash
# LiteBrawl 测试服务器启动脚本（Linux / macOS）
set -e
cd "$(dirname "$0")"

echo "=============================================="
echo "  LiteBrawl 测试服务器"
echo "=============================================="

if ! command -v java >/dev/null 2>&1; then
    echo "[错误] 找不到 java，请先安装 Java 21：https://adoptium.net"
    exit 1
fi

if ! grep -qi "eula=true" eula.txt 2>/dev/null; then
    echo "[步骤 1] 首次启动需要先同意 Minecraft EULA"
    echo "         打开 eula.txt，把 eula=false 改成 eula=true"
    echo "         EULA：https://aka.ms/MinecraftEULA"
    exit 1
fi

if [ ! -f paper.jar ]; then
    echo "[错误] 找不到 paper.jar"
    exit 1
fi

echo "正在启动，首次启动会生成世界，约需 30-60 秒…"
echo "启动完成后控制台会打印 LiteBrawl 网页控制台地址。"
echo
exec java -Xms1G -Xmx2G -jar paper.jar nogui
