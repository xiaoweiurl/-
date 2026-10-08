"""
图片向量服务。

默认模型：OFA-Sys/chinese-clip-vit-base-patch16（Chinese-CLIP ViT-B/16，512 维）。
图和中文文本在同一向量空间，余弦相似度可直接比。

RTX 5090（sm_120）这台机器改用 ViT-L/14，并与 Java 一起换成新集合，不要改默认值：
  IMAGE_EMBED_MODEL=OFA-Sys/chinese-clip-vit-large-patch14
  IMAGE_EMBED_DIMENSION=768
  对应 IMAGE_SEARCH_DIMENSION=768、IMAGE_SEARCH_COLLECTION=image_vectors_vitl
PyTorch 需要 cu128 或更新的轮子（本机 cu130）。cu124/cu126 没有 sm_120。

环境变量：
  IMAGE_EMBED_DEVICE=auto|cpu|cuda   默认 auto：有 CUDA 用 GPU，否则 CPU
  IMAGE_EMBED_MODEL                   默认 OFA-Sys/chinese-clip-vit-base-patch16
  IMAGE_EMBED_DIMENSION               默认 512，必须和 Java image-search.dimension 一致
  IMAGE_EMBED_PORT                    默认 8002
  IMAGE_EMBED_HOST                    默认 127.0.0.1
  IMAGE_EMBED_STUB=1                  不加载模型，用确定性颜色向量（只给流水线冒烟，不能用于正式检索）
  IMAGE_EMBED_HTTP                    默认 h11。Java HttpClient 会发 Upgrade: h2c，httptools 会拒绝。一般不要改
  IMAGE_EMBED_CROP=1                  加载 OWLv2，按请求裁到衣服/人物。默认关。整图请求不受影响
  IMAGE_EMBED_CROP_MODEL              默认 google/owlv2-base-patch16-ensemble（Apache-2.0）
  IMAGE_EMBED_CROP_THRESHOLD          默认 0.20。低于这个分数就用整图
  IMAGE_EMBED_CROP_PADDING            默认 0.12，框外再留一圈
  IMAGE_EMBED_CROP_MIN_SIDE           默认 48。更小的图（例如小 GIF）不检测
  HF_ENDPOINT                         默认 https://hf-mirror.com
"""

from __future__ import annotations

import io
import logging
import math
import os
import threading
from typing import List, Optional

# 国内镜像。用户已设置 HF_ENDPOINT 时不覆盖。
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from pydantic import BaseModel, Field
import uvicorn

import crop as crop_mod

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
logger = logging.getLogger("image-embed")

STUB = os.environ.get("IMAGE_EMBED_STUB", "").strip().lower() in ("1", "true", "yes")
MODEL_ID = os.environ.get("IMAGE_EMBED_MODEL", "OFA-Sys/chinese-clip-vit-base-patch16")
EXPECTED_DIM = int(os.environ.get("IMAGE_EMBED_DIMENSION", "512"))
HOST = os.environ.get("IMAGE_EMBED_HOST", "127.0.0.1")
PORT = int(os.environ.get("IMAGE_EMBED_PORT", "8002"))
MAX_BYTES = int(os.environ.get("IMAGE_EMBED_MAX_BYTES", str(20 * 1024 * 1024)))
CROP_ENABLED = os.environ.get("IMAGE_EMBED_CROP", "").strip().lower() in ("1", "true", "yes", "on")
CROP_MODEL = os.environ.get("IMAGE_EMBED_CROP_MODEL", crop_mod.DEFAULT_MODEL)
CROP_THRESHOLD = float(os.environ.get("IMAGE_EMBED_CROP_THRESHOLD", str(crop_mod.DEFAULT_THRESHOLD)))
CROP_PADDING = float(os.environ.get("IMAGE_EMBED_CROP_PADDING", str(crop_mod.DEFAULT_PADDING)))
CROP_MIN_SIDE = int(os.environ.get("IMAGE_EMBED_CROP_MIN_SIDE", str(crop_mod.DEFAULT_MIN_SIDE)))

_lock = threading.Lock()
_model = None
_processor = None
_detector = None
_device = "cpu"
_cuda_available = False
_gpu_name: Optional[str] = None
_dimension = EXPECTED_DIM


def detect_device() -> str:
    requested = os.environ.get("IMAGE_EMBED_DEVICE", "auto").strip().lower()
    if requested not in ("auto", "cpu", "cuda"):
        raise RuntimeError("IMAGE_EMBED_DEVICE 只能是 auto、cpu 或 cuda")
    cuda = False
    name = None
    if not STUB:
        import torch
        cuda = bool(torch.cuda.is_available())
        if cuda:
            name = torch.cuda.get_device_name(0)
    global _cuda_available, _gpu_name
    _cuda_available = cuda
    _gpu_name = name
    if requested == "cpu":
        return "cpu"
    if requested == "cuda":
        if not cuda:
            raise RuntimeError("IMAGE_EMBED_DEVICE=cuda，但 torch.cuda.is_available() 为 false")
        return "cuda"
    return "cuda" if cuda else "cpu"


def _l2(vec: List[float]) -> List[float]:
    norm = math.sqrt(sum(v * v for v in vec))
    if norm <= 0:
        return vec
    return [v / norm for v in vec]


def stub_image_embedding(image: Image.Image, dimension: int) -> List[float]:
    """把图片缩成 8x8 色块再展开。纯色图的缩放/裁剪会得到几乎相同的向量。"""
    img = image.convert("RGB").resize((32, 32), Image.Resampling.BILINEAR)
    pixels = list(img.getdata())
    cell = 4
    feats: List[float] = []
    for gy in range(8):
        for gx in range(8):
            rs = gs = bs = 0.0
            for y in range(gy * cell, (gy + 1) * cell):
                for x in range(gx * cell, (gx + 1) * cell):
                    r, g, b = pixels[y * 32 + x]
                    rs += r
                    gs += g
                    bs += b
            feats.extend([rs / (cell * cell * 255.0), gs / (cell * cell * 255.0), bs / (cell * cell * 255.0)])
    out = [0.0] * dimension
    for i, value in enumerate(feats):
        out[i % dimension] += value
        out[(i * 7 + 3) % dimension] += value * 0.5
    return _l2(out)


def stub_text_embedding(text: str, dimension: int) -> List[float]:
    out = [0.0] * dimension
    for i, ch in enumerate(text):
        out[(ord(ch) + i * 13) % dimension] += 1.0
    if not any(out):
        out[0] = 1.0
    return _l2(out)


def load_model() -> None:
    global _model, _processor, _device, _dimension
    _device = detect_device()
    if STUB:
        _dimension = EXPECTED_DIM
        logger.info("IMAGE_EMBED_STUB=1，使用确定性颜色向量，维度 %s，设备标记 %s", _dimension, _device)
        return

    import torch
    from transformers import ChineseCLIPModel, ChineseCLIPProcessor

    logger.info("加载图片向量模型 %s，设备 %s", MODEL_ID, _device)
    processor = ChineseCLIPProcessor.from_pretrained(MODEL_ID)
    model = ChineseCLIPModel.from_pretrained(MODEL_ID)
    model.eval()
    if _device == "cuda":
        model = model.to("cuda").half()
    else:
        model = model.to("cpu").float()

    with torch.no_grad():
        # 1 像素边长会被推断成 channels-first。read_image 已转成 RGB，这里必须声明 channels_last。
        probe = processor(
            images=Image.new("RGB", (32, 32), "white"),
            return_tensors="pt",
            input_data_format="channels_last",
        )
        pixel_values = probe["pixel_values"].to(_device)
        if _device == "cuda":
            pixel_values = pixel_values.half()
        features = _image_features(model, pixel_values)
        actual = int(features.shape[-1])
    if actual != EXPECTED_DIM:
        raise RuntimeError(
            f"模型输出维度 {actual} 与 IMAGE_EMBED_DIMENSION={EXPECTED_DIM} 不一致。"
            "请同时修改 Java 的 image-search.dimension，并换一个新的 image_ 集合名。"
        )
    _processor = processor
    _model = model
    _dimension = actual
    logger.info("模型就绪：%s device=%s cuda=%s gpu=%s dim=%s", MODEL_ID, _device, _cuda_available, _gpu_name, _dimension)
    _load_detector()


def _load_detector() -> None:
    global _detector
    if not CROP_ENABLED or STUB:
        _detector = None
        if CROP_ENABLED and STUB:
            logger.info("IMAGE_EMBED_CROP=1 但当前是 stub，不加载 OWLv2")
        return
    try:
        _detector = crop_mod.OwlV2GarmentDetector(CROP_MODEL, _device)
        logger.info("主体检测就绪：%s", CROP_MODEL)
    except Exception:
        _detector = None
        logger.exception("主体检测没加载起来。裁剪请求会退回整图，整图向量不受影响")


def _image_features(model, pixel_values):
    vision_outputs = model.vision_model(pixel_values=pixel_values)
    pooled = vision_outputs[1]
    features = model.visual_projection(pooled)
    return features / features.norm(p=2, dim=-1, keepdim=True)


def _text_features(model, input_ids, attention_mask):
    text_outputs = model.text_model(input_ids=input_ids, attention_mask=attention_mask)
    # Chinese-CLIP 文本塔没有 pooler，text_outputs[1] 会 IndexError。
    # 与 transformers ChineseCLIPModel.get_text_features 一致，取 CLS（第 0 位）。
    pooled = text_outputs[0][:, 0, :]
    features = model.text_projection(pooled)
    return features / features.norm(p=2, dim=-1, keepdim=True)


def read_image(data: bytes) -> Image.Image:
    if not data:
        raise HTTPException(status_code=400, detail="空文件")
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=400, detail="图片超过大小上限")
    try:
        image = Image.open(io.BytesIO(data))
        image = image.convert("RGB")
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"无法解析图片: {exc}") from exc
    image.thumbnail((1024, 1024))
    return image


def embed_image(image: Image.Image) -> List[float]:
    vector, _mode = embed_image_prepared(image, crop_requested=False)
    return vector


def embed_image_prepared(image: Image.Image, crop_requested: bool) -> tuple[List[float], str]:
    prepared = image
    mode = "full"
    if crop_requested:
        prepared, mode = crop_mod.preprocess(
            image,
            _detector,
            enabled=CROP_ENABLED and _detector is not None,
            min_side=CROP_MIN_SIDE,
            padding_ratio=CROP_PADDING,
            threshold=CROP_THRESHOLD,
        )
    return _embed_pixels(prepared), mode


def _embed_pixels(image: Image.Image) -> List[float]:
    if STUB:
        return stub_image_embedding(image, _dimension)
    import torch
    # 1x1、3x1、1x3 会被当成 channels-first，报 mean must have 1 elements, got 3。
    inputs = _processor(images=image, return_tensors="pt", input_data_format="channels_last")
    pixel_values = inputs["pixel_values"].to(_device)
    if _device == "cuda":
        pixel_values = pixel_values.half()
    with _lock, torch.no_grad():
        features = _image_features(_model, pixel_values)
    return [float(v) for v in features[0].detach().float().cpu().tolist()]


def embed_text(text: str) -> List[float]:
    text = (text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="文本为空")
    if len(text) > 200:
        text = text[:200]
    if STUB:
        return stub_text_embedding(text, _dimension)
    import torch
    inputs = _processor(text=[text], padding=True, truncation=True, max_length=52, return_tensors="pt")
    input_ids = inputs["input_ids"].to(_device)
    attention_mask = inputs["attention_mask"].to(_device)
    with _lock, torch.no_grad():
        features = _text_features(_model, input_ids, attention_mask)
    return [float(v) for v in features[0].detach().float().cpu().tolist()]


class TextRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=200)


class EmbedResponse(BaseModel):
    embedding: List[float]
    dimension: int
    model: str
    device: str
    stub: bool
    preprocess: str = "full"


app = FastAPI(title="Image Embed Service", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def _startup() -> None:
    load_model()


def _payload(embedding: List[float], preprocess: str = "full") -> EmbedResponse:
    return EmbedResponse(
        embedding=embedding,
        dimension=len(embedding),
        model="stub" if STUB else MODEL_ID,
        device=_device,
        stub=STUB,
        preprocess=preprocess,
    )


def _crop_mode() -> str:
    if not CROP_ENABLED:
        return "off"
    if STUB:
        return "stub"
    if _detector is None:
        return "unavailable"
    return "owlv2"


@app.get("/health")
def health():
    ready = STUB or _model is not None
    return {
        "status": "ok" if ready else "loading",
        "model": "stub" if STUB else MODEL_ID,
        "device": _device,
        "cuda_available": _cuda_available,
        "gpu_name": _gpu_name,
        "dimension": _dimension,
        "stub": STUB,
        "crop_enabled": CROP_ENABLED,
        "crop_mode": _crop_mode(),
        "crop_model": CROP_MODEL if CROP_ENABLED and not STUB else None,
        "crop_detector_ready": _detector is not None,
        "crop_prompts": list(crop_mod.PROMPTS),
    }


def _crop_requested(value: Optional[str]) -> bool:
    return str(value or "").strip().lower() in ("1", "true", "yes", "on")


@app.post("/embed/image", response_model=EmbedResponse)
async def embed_image_api(
    file: UploadFile = File(...),
    crop: Optional[str] = Form(default=None),
):
    data = await file.read()
    image = read_image(data)
    try:
        vector, mode = embed_image_prepared(image, crop_requested=_crop_requested(crop))
        return _payload(vector, mode)
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("图片向量失败")
        raise HTTPException(status_code=500, detail=f"图片向量失败: {exc}") from exc


@app.post("/embed/text", response_model=EmbedResponse)
def embed_text_api(body: TextRequest):
    try:
        return _payload(embed_text(body.text))
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("文本向量失败")
        raise HTTPException(status_code=500, detail=f"文本向量失败: {exc}") from exc


if __name__ == "__main__":
    # httptools 会拒绝 Java HttpClient 的 Upgrade: h2c。plain python main.py 固定 h11。
    uvicorn.run(app, host=HOST, port=PORT, workers=1, http=os.environ.get("IMAGE_EMBED_HTTP", "h11"))
