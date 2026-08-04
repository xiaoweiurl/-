# Reranker Service

基于 [BGE-Reranker-v2-m3](https://huggingface.co/BAAI/bge-reranker-v2-m3) 的文档重排序服务。

## 特性

- 🚀 基于 FlagEmbedding 的高性能重排序
- 🌍 支持多语言（中文、英文等）
- 📊 分数归一化到 0-1 范围
- 🔥 FastAPI 高性能异步框架
- 🐳 Docker 支持

## 快速开始

### 本地运行

```bash
# 安装依赖
pip install -r requirements.txt

# 启动服务
python main.py
```

### Docker 运行

```bash
# 构建镜像
docker build -t reranker-service .

# 运行容器
docker run -p 8001:8001 reranker-service
```

## API 文档

启动后访问 http://localhost:8001/docs 查看交互式 API 文档。

### 健康检查

```bash
curl http://localhost:8001/health
```

### 重排序接口

```bash
curl -X POST http://localhost:8001/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "什么是人工智能",
    "documents": [
      "人工智能是计算机科学的一个分支",
      "今天天气很好",
      "AI 技术正在改变世界"
    ],
    "top_k": 2,
    "normalize": true
  }'
```

响应示例：

```json
{
  "results": [
    {
      "index": 0,
      "document": "人工智能是计算机科学的一个分支",
      "score": 0.95
    },
    {
      "index": 2,
      "document": "AI 技术正在改变世界",
      "score": 0.82
    }
  ],
  "query": "什么是人工智能"
}
```

## 配置

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `MODEL_NAME` | 模型名称 | `BAAI/bge-reranker-v2-m3` |
| `PORT` | 服务端口 | `8001` |

## 模型说明

- **模型**: `BAAI/bge-reranker-v2-m3`
- **参数量**: 568M
- **上下文长度**: 8K
- **语言**: 多语言（中/英/日/韩等）
- **首次启动**: 会自动下载模型（约 2.2GB）

## 性能参考

| 文档数量 | 耗时 |
|---------|------|
| 10 | ~50ms |
| 50 | ~200ms |
| 100 | ~400ms |

*测试环境: NVIDIA T4 GPU*

## 与 Java 后端集成

在 Java 后端配置 Reranker 服务地址：

```yaml
app:
  reranker:
    base-url: http://localhost:8001
```
