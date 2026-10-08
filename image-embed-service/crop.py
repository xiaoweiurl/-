"""
主体裁剪。检索前把衣服或人物从背景里框出来，再交给 Chinese-CLIP。

检测器：google/owlv2-base-patch16-ensemble（OWLv2，Apache-2.0）。
选它是因为 transformers 4.57 自带 Owlv2Processor / Owlv2ForObjectDetection，
不用另装检测库，RTX 5090 + torch 2.14 cu130 可以 fp16 跑。
提示词是 clothing / garment / person。检不出、或图太小，就用整张图。

权重从 HF_ENDPOINT 下载。国内默认 https://hf-mirror.com。
"""

from __future__ import annotations

import logging
import math
from typing import Optional, Protocol, Sequence

from PIL import Image

logger = logging.getLogger("image-embed.crop")

PROMPTS = ("clothing", "garment", "person")
DEFAULT_MODEL = "google/owlv2-base-patch16-ensemble"
DEFAULT_MIN_SIDE = 48
DEFAULT_PADDING = 0.12
DEFAULT_THRESHOLD = 0.20
# 框住了几乎整张图时，裁切没有意义，当没检出。
MAX_COVER = 0.96


class Detector(Protocol):
    def detect(self, image: Image.Image) -> Sequence[dict]:
        """返回 {"score": float, "box": [x0, y0, x1, y1]}，坐标是像素。"""


def image_too_small(image: Image.Image, min_side: int = DEFAULT_MIN_SIDE) -> bool:
    width, height = image.size
    return min(width, height) < min_side


def select_box(detections: Sequence[dict], threshold: float) -> Optional[list]:
    best_score = threshold
    best_box = None
    found = False
    for item in detections or []:
        try:
            score = float(item.get("score", 0.0))
            box = item.get("box")
        except (TypeError, AttributeError):
            continue
        if box is None or len(box) != 4:
            continue
        if not found or score > best_score:
            # 第一条必须达到阈值；之后取得分更高的。
            if not found and score < threshold:
                continue
            best_score = score
            best_box = [float(value) for value in box]
            found = True
    return best_box


def pad_box(box: Sequence[float], width: int, height: int, padding_ratio: float):
    x0, y0, x1, y1 = box
    if x1 < x0:
        x0, x1 = x1, x0
    if y1 < y0:
        y0, y1 = y1, y0
    box_w = max(1.0, x1 - x0)
    box_h = max(1.0, y1 - y0)
    pad_x = box_w * max(0.0, padding_ratio)
    pad_y = box_h * max(0.0, padding_ratio)
    left = max(0, int(math.floor(x0 - pad_x)))
    top = max(0, int(math.floor(y0 - pad_y)))
    right = min(width, int(math.ceil(x1 + pad_x)))
    bottom = min(height, int(math.ceil(y1 + pad_y)))
    if right - left < 2 or bottom - top < 2:
        return None
    covered = ((right - left) * (bottom - top)) / float(max(1, width * height))
    if covered >= MAX_COVER:
        return None
    return left, top, right, bottom


def crop_to_box(image: Image.Image, box: Sequence[float], padding_ratio: float) -> Optional[Image.Image]:
    padded = pad_box(box, image.width, image.height, padding_ratio)
    if padded is None:
        return None
    return image.crop(padded)


def preprocess(image: Image.Image, detector: Optional[Detector], enabled: bool,
               min_side: int = DEFAULT_MIN_SIDE, padding_ratio: float = DEFAULT_PADDING,
               threshold: float = DEFAULT_THRESHOLD) -> tuple[Image.Image, str]:
    """返回 (送给 CLIP 的图, "crop" 或 "full")。"""
    if not enabled or detector is None or image_too_small(image, min_side):
        return image, "full"
    try:
        detections = detector.detect(image)
    except Exception:
        logger.exception("主体检测失败，改用整图")
        return image, "full"
    box = select_box(detections, threshold)
    if box is None:
        return image, "full"
    cropped = crop_to_box(image, box, padding_ratio)
    if cropped is None:
        return image, "full"
    return cropped, "crop"


class OwlV2GarmentDetector:
    """OWLv2 开放词汇检测。只在 IMAGE_EMBED_CROP=1 且不是 stub 时加载。"""

    def __init__(self, model_id: str, device: str):
        import torch
        from transformers import Owlv2ForObjectDetection, Owlv2Processor

        logger.info("加载主体检测 %s，设备 %s", model_id, device)
        self.processor = Owlv2Processor.from_pretrained(model_id)
        model = Owlv2ForObjectDetection.from_pretrained(model_id)
        model.eval()
        if device == "cuda":
            model = model.to("cuda").half()
        else:
            model = model.to("cpu").float()
        self.model = model
        self.device = device
        self._torch = torch

    def detect(self, image: Image.Image) -> list[dict]:
        texts = [list(PROMPTS)]
        inputs = self.processor(text=texts, images=image, return_tensors="pt")
        moved = {key: value.to(self.device) for key, value in inputs.items()}
        if self.device == "cuda" and "pixel_values" in moved:
            moved["pixel_values"] = moved["pixel_values"].half()
        with self._torch.no_grad():
            outputs = self.model(**moved)
        target = self._torch.tensor([[image.height, image.width]], device=self.device)
        results = self.processor.post_process_object_detection(
            outputs=outputs, target_sizes=target, threshold=0.01
        )
        if not results:
            return []
        result = results[0]
        detections = []
        boxes = result.get("boxes")
        scores = result.get("scores")
        if boxes is None or scores is None:
            return []
        for box, score in zip(boxes, scores):
            coords = box.detach().float().cpu().tolist()
            detections.append({"score": float(score), "box": [float(value) for value in coords]})
        return detections
