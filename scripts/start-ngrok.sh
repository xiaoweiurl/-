#!/bin/bash

# ==========================================
# ngrok 启动脚本
# ==========================================

echo "=========================================="
echo "  图片管理系统 - ngrok 代理启动"
echo "=========================================="
echo ""

# 检查 ngrok 是否安装
if ! command -v ngrok &> /dev/null; then
    echo "❌ ngrok 未安装"
    echo ""
    echo "请先安装 ngrok:"
    echo "  macOS:   brew install ngrok"
    echo "  Windows: choco install ngrok"
    echo "  Linux:   curl -s https://ngrok-agent.s3.amazonaws.com/ngrok.asc | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null"
    echo "           echo 'deb https://ngrok-agent.s3.amazonaws.com buster main' | sudo tee /etc/apt/sources.list.d/ngrok.list"
    echo "           sudo apt update && sudo apt install ngrok"
    echo ""
    echo "或直接下载: https://ngrok.com/download"
    exit 1
fi

echo "✅ ngrok 已安装"
echo ""

# 检查是否已配置 authtoken
if ! ngrok config check &> /dev/null; then
    echo "⚠️  ngrok authtoken 未配置"
    echo ""
    echo "请先配置 authtoken:"
    echo "  1. 访问 https://dashboard.ngrok.com/get-started/your-authtoken 获取 token"
    echo "  2. 运行: ngrok config add-authtoken <your-token>"
    exit 1
fi

echo "✅ ngrok authtoken 已配置"
echo ""

# 检查端口
PORT=5000
if lsof -i :$PORT &> /dev/null; then
    echo "✅ 检测到服务运行在端口 $PORT"
else
    echo "⚠️  端口 $PORT 没有服务运行"
    echo ""
    echo "请先启动项目:"
    echo "  前端: pnpm run dev"
    echo "  后端: cd backend && ./mvnw spring-boot:run"
    echo ""
    read -p "是否继续启动 ngrok? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo ""
echo "=========================================="
echo "  启动 ngrok 代理..."
echo "=========================================="
echo ""
echo "📝 启动后请复制 ngrok 提供的 https 地址"
echo "   并更新 .env.local 中的 NEXT_PUBLIC_BACKEND_API_URL"
echo ""
echo "   示例: NEXT_PUBLIC_BACKEND_API_URL=https://xxxx.ngrok-free.app/api"
echo ""
echo "=========================================="
echo ""

# 启动 ngrok
ngrok http $PORT
