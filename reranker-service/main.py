"""
Reranker Service - 基于 FlagEmbedding 的文档重排序服务
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
    logger.info(f"正在加载 Reranker 模型: {MODEL_NAME}")
    try:
        from FlagEmbedding import FlagReranker
        reranker = FlagReranker(MODEL_NAME, use_fp16=True)
        logger.info("Reranker 模型加载成功")
    except ImportError:
        logger.error("请安装 FlagEmbedding: pip install FlagEmbedding")
        raise
    except Exception as e:
        logger.error(f"模型加载失败: {e}")
        raise
    yield
    logger.info("Reranker 服务关闭")


app = FastAPI(
    title="Reranker Service",
    description="基于 BGE-Reranker 的文档重排序服务",
    version="1.0.0",
    lifespan=lifespan
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
    query: str = Field(..., description="查询文本")
    documents: List[str] = Field(..., description="待排序的文档列表")
    top_k: Optional[int] = Field(None, description="返回前 K 个结果", ge=1)
    normalize: bool = Field(True, description="是否将分数归一化到 0-1 范围")


class RerankResult(BaseModel):
    """单个重排序结果"""
    index: int = Field(..., description="原始索引")
    document: str = Field(..., description="文档内容")
    score: float = Field(..., description="相关性分数")


class RerankResponse(BaseModel):
    """重排序响应"""
    results: List[RerankResult] = Field(..., description="排序后的结果列表")
    query: str = Field(..., description="原始查询")


class HealthResponse(BaseModel):
    """健康检查响应"""
    status: str
    model: str
    loaded: bool


@app.get("/health", response_model=HealthResponse, tags="系统")
async def health_check():
    """健康检查接口"""
    return HealthResponse(
        status="healthy" if reranker is not None else "unhealthy",
        model=MODEL_NAME,
        loaded=reranker is not None
    )


@app.post("/rerank", response_model=RerankResponse, tags="重排序")
async def rerank(request: RerankRequest):
    """
    文档重排序接口
    
    接收查询文本和文档列表，返回按相关性排序的结果。
    
    - **query**: 查询文本
    - **documents**: 待排序的文档列表
    - **top_k**: 返回前 K 个结果（可选，默认返回全部）
    - **normalize**: 是否将分数归一化到 0-1 范围（默认 true）
    """
    if reranker is None:
        raise HTTPException(status_code=503, detail="Reranker 模型未加载")
    
    if not request.documents:
        return RerankResponse(results=[], query=request.query)
    
    try:
        # 构建 query-document 对
        pairs = [[request.query, doc] for doc in request.documents]
        
        # 计算相关性分数
        scores = reranker.compute_score(pairs, normalize=request.normalize)
        
        # 如果只有一个文档，scores 是单个值而不是列表
        if isinstance(scores, (int, float)):
            scores = [scores]
        
        # 构建结果
        results = [
            RerankResult(
                index=i,
                document=doc,
                score=float(score)
            )
            for i, (doc, score) in enumerate(zip(request.documents, scores))
        ]
        
        # 按分数降序排序
        results.sort(key=lambda x: x.score, reverse=True)
        
        # 如果指定了 top_k，截取前 K 个
        if request.top_k is not None:
            results = results[:request.top_k]
        
        logger.info(f"重排序完成: query='{request.query[:50]}...', docs={len(request.documents)}, top_k={request.top_k}")
        
        return RerankResponse(results=results, query=request.query)
        
    except Exception as e:
        logger.error(f"重排序失败: {e}")
        raise HTTPException(status_code=500, detail=f"重排序失败: {str(e)}")


@app.post("/rerank/batch", tags="重排序")
async def rerank_batch(requests: List[RerankRequest]):
    """
    批量重排序接口
    
    同时处理多个重排序请求，提高效率。
    """
    if reranker is None:
        raise HTTPException(status_code=503, detail="Reranker 模型未加载")
    
    results = []
    for req in requests:
        try:
            response = await rerank(req)
            results.append(response)
        except Exception as e:
            logger.error(f"批量重排序中单个请求失败: {e}")
            results.append(RerankResponse(results=[], query=req.query))
    
    return results


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8001,
        reload=False,
        log_level="info"
    )
