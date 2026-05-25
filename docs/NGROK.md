# ngrok 代理配置指南

本文档介绍如何使用 ngrok 将本地图片管理系统暴露到公网，方便外部访问和测试。

## 什么是 ngrok？

ngrok 是一个反向代理工具，可以将本地服务暴露到公网。它提供了一个公网 URL，让外部用户可以访问你本地运行的服务。

## 安装 ngrok

### macOS
```bash
brew install ngrok
```

### Windows
```bash
choco install ngrok
```

### Linux
```bash
# 使用 apt
curl -s https://ngrok-agent.s3.amazonaws.com/ngrok.asc | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc >/dev/null
echo "deb https://ngrok-agent.s3.amazonaws.com buster main" | sudo tee /etc/apt/sources.list.d/ngrok.list
sudo apt update && sudo apt install ngrok

# 或直接下载
# https://ngrok.com/download
```

## 配置 ngrok

### 1. 注册账号

访问 [ngrok.com](https://ngrok.com) 注册免费账号。

### 2. 获取 Auth Token

1. 登录 ngrok Dashboard
2. 访问 [Your Authtoken](https://dashboard.ngrok.com/get-started/your-authtoken)
3. 复制你的 authtoken

### 3. 配置 Token

```bash
ngrok config add-authtoken <your-token>
```

## 启动代理

### 方法一：使用启动脚本

```bash
./scripts/start-ngrok.sh
```

### 方法二：手动启动

```bash
# 1. 确保项目已启动
# 前端（端口 5000）
pnpm run dev

# 后端（端口 8080）
cd backend && ./mvnw spring-boot:run

# 2. 启动 ngrok
ngrok http 5000
```

## 配置前端

启动 ngrok 后，你会看到类似以下的输出：

```
Session Status                online
Account                       your-email@example.com (Plan: Free)
Version                       3.x.x
Region                        United States (us)
Latency                       -
Web Interface                 http://127.0.0.1:4040
Forwarding                    https://xxxx-xxxx.ngrok-free.app -> http://localhost:5000
```

复制 `Forwarding` 中的 https 地址（如 `https://xxxx-xxxx.ngrok-free.app`）。

### 更新环境变量

编辑 `.env.local` 文件：

```env
# 将 ngrok 地址配置为后端 API 地址
NEXT_PUBLIC_BACKEND_API_URL=https://xxxx-xxxx.ngrok-free.app/api
```

### 重启前端

```bash
# 重启前端以加载新的环境变量
pnpm run dev
```

## 免费版限制

- 每次启动 ngrok，域名会变化
- 有请求速率限制
- 页面会显示 ngrok 警告页面
- 无法绑定自定义域名

## 付费版优势

- 固定域名
- 更高的请求限制
- 无警告页面
- 自定义域名绑定

### 固定域名配置

付费版可以使用固定域名：

```bash
ngrok http 5000 --domain=your-domain.ngrok-free.app
```

或在配置文件 `~/.ngrok2/ngrok.yml` 中：

```yaml
version: "2"
authtoken: <your-token>
tunnels:
  imagemanager:
    proto: http
    addr: 5000
    hostname: your-domain.ngrok-free.app
```

然后启动：

```bash
ngrok start imagemanager
```

## 常见问题

### 1. ngrok 显示 "Tunnel session failed"

可能是 token 配置错误，重新运行：

```bash
ngrok config add-authtoken <your-token>
```

### 2. 页面显示 ngrok 警告

这是免费版的正常行为，点击 "Visit Site" 继续访问。

### 3. 请求超时

免费版有速率限制，如果请求过多可能会被限制。等待一段时间后再试。

### 4. 后端 API 跨域问题

后端 CORS 已配置允许所有域名，不应该有跨域问题。如果仍然出现，检查：

1. 前端 `.env.local` 中的 `NEXT_PUBLIC_BACKEND_API_URL` 是否正确
2. 后端是否正在运行
3. ngrok 是否正在代理

### 5. 分享链接访问不了

分享链接的域名来自前端环境变量 `COZE_PROJECT_DOMAIN_DEFAULT`。如果使用 ngrok，需要确保：

1. 前端通过 ngrok 地址访问
2. 分享链接会自动使用当前域名

## 安全注意事项

1. **不要暴露敏感服务**：ngrok 会将服务暴露到公网，确保不要暴露含有敏感数据的服务。

2. **使用密码保护**：分享链接支持密码保护，建议启用。

3. **定期检查访问日志**：通过 ngrok Web Interface (http://127.0.0.1:4040) 查看请求日志。

4. **生产环境**：生产环境不建议使用 ngrok，应该使用正式的服务器部署。

## 相关链接

- [ngrok 官网](https://ngrok.com)
- [ngrok 文档](https://ngrok.com/docs)
- [ngrok Dashboard](https://dashboard.ngrok.com)
