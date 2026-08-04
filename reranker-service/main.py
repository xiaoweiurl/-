"""
Reranker Service - 基于 Sentence-Transformers 的文档重排序服务
使用 bge-reranker-v2-m3 模型，支持多语言（中英文）
"""

import os
import logging
from typing import List, Optional
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import uvicorn

# 设置 HuggingFace 国内镜像（解决下载超时问题）
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"
os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 全局模型变量
reranker = None
MODEL_NAME = "BAAI/bge-reranker-v2-m3"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理：启动时加载模型"""
    global reranker
    logger.info(f"正在加载 Reranker 模型：{MODEL_NAME}")
    try:
        from sentence_transformers import CrossEncoder
        reranker = CrossEncoder(MODEL_NAME)
        logger.info("Reranker 模型加载成功")
    except ImportError:
        logger.error("请安装 sentence-transformers: pip install sentence-transformers")
        raise
    except Exception as e:
        logger.error(f"模型加载失败：{e}")
        raise
    yield
    logger.info("Reranker 服务关闭")


app = FastAPI(
    title="Reranker Service",
    description="基于 bge-reranker-v2-m3 的文档重排序服务",
    version="1.0.0",
    lifespan=lifespan,
)

# CORS 配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class RerankRequest(BaseModel):
    """重排序请求"""
    query: str = Field(..., description="查询文本", min_length=1)
    documents: List[str] = Field(..., description="待排序文档列表", min_items=1)
    top_k: Optional[int] = Field(None, description="返回前 K 个结果", ge=1)


class RerankResult(BaseModel):
    """单个重排序结果"""
    index: int = Field(..., description="原始索引")
    text: str = Field(..., description="文档内容")
    score: float = Field(..., description="相关性分数（0-1）")


class RerankResponse(BaseModel):
    """重排序响应"""
    query: str = Field(..., description="查询文本")
    results: List[RerankResult] = Field(..., description="排序结果")


@app.post("/rerank", response_model=RerankResponse)
async def rerank_documents(request: RerankRequest):
    """
    对文档列表进行重排序
    
    使用 bge-reranker-v2-m3 模型对查询和文档的相关性进行打分，
    返回按相关性降序排列的结果。
    """
    if reranker is None:
        raise HTTPException(status_code=503, detail="模型未加载")
    
    try:
        # 构建 (query, document) 对
        pairs = [(request.query, doc) for doc in request.documents]
        
        # 计算相关性分数
        scores = reranker.predict(pairs)
        
        # 归一化分数到 0-1 范围（使用 sigmoid）
        import numpy as np
        normalized_scores = 1 / (1 + np.exp(-scores))
        
        # 组合结果
        results = []
        for idx, (doc, score) in enumerate(zip(request.documents, normalized_scores)):
            results.append({
                "index": idx,
                "text": doc,
                "score": float(score)
            })
        
        # 按分数降序排序
        results.sort(key=lambda x: x["score"], reverse=True)
        
        # 截取 top_k
        if request.top_k:
            results = results[:request.top_k]
        
        logger.info(f"重排序完成：{len(results)} 个文档")
        
        return RerankResponse(
            query=request.query,
            results=[RerankResult(**r) for r in results]
        )
        
    except Exception as e:
        logger.error(f"重排序失败：{e}")
        raise HTTPException(status_code=500, detail=f"重排序失败：{str(e)}")


@app.post("/rerank/batch")
async def rerank_batch(requests: List[RerankRequest]):
    """批量重排序"""
    results = []
    for req in requests:
        try:
            result = await rerank_documents(req)
            results.append(result)
        except Exception as e:
            results.append({"error": str(e)})
    return results


@app.get("/health")
async def health_check():
    """健康检查"""
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "loaded": reranker is not None
    }


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)
