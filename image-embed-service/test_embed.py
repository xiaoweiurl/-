"""无 GPU 的桩测试：文本池化、极小图、以及 python main.py 的 h11 默认。"""

from __future__ import annotations

import ast
import io
import os
import sys
import unittest
from pathlib import Path

os.environ.setdefault("IMAGE_EMBED_STUB", "1")

import main
from PIL import Image

ROOT = Path(__file__).resolve().parent
RERANKER_MAIN = ROOT.parent / "reranker-service" / "main.py"


class _NoGrad:
    def __enter__(self):
        return None

    def __exit__(self, exc_type, exc, tb):
        return False


class _Torch:
    @staticmethod
    def no_grad():
        return _NoGrad()


class _Pixel:
    def to(self, device):
        return self


class _Vector:
    def __init__(self, values):
        self._values = values

    def __getitem__(self, index):
        return self

    def detach(self):
        return self

    def float(self):
        return self

    def cpu(self):
        return self

    def tolist(self):
        return list(self._values)


def _invoke_main(path: Path) -> dict:
    """执行 `if __name__` 里的 uvicorn.run，但不真正监听端口。"""
    import runpy
    import uvicorn

    captured = {}
    original = uvicorn.run

    def fake_run(*args, **kwargs):
        captured["args"] = args
        captured["kwargs"] = kwargs

    uvicorn.run = fake_run
    try:
        runpy.run_path(str(path), run_name="__main__")
    finally:
        uvicorn.run = original
    if "kwargs" not in captured:
        raise AssertionError(f"{path} 没有调用 uvicorn.run")
    return captured["kwargs"]


def _image_processor_formats(path: Path):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    formats = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        keywords = {item.arg: item.value for item in node.keywords}
        if "images" not in keywords:
            continue
        fmt = keywords.get("input_data_format")
        if not isinstance(fmt, ast.Constant):
            raise AssertionError(f"{path}:{node.lineno} 的图片 processor 调用缺少 input_data_format")
        formats.append(fmt.value)
    return formats


class EmbedFixesTest(unittest.TestCase):
    def setUp(self):
        self._stub = main.STUB
        self._processor = main._processor
        self._device = main._device
        self._torch = sys.modules.get("torch")

    def tearDown(self):
        main.STUB = self._stub
        main._processor = self._processor
        main._device = self._device
        if self._torch is None:
            sys.modules.pop("torch", None)
        else:
            sys.modules["torch"] = self._torch
        os.environ.pop("IMAGE_EMBED_HTTP", None)
        os.environ.pop("RERANKER_HTTP", None)

    def test_text_path_uses_cls_token(self):
        seen = {}

        class Hidden:
            def __getitem__(self, key):
                seen["slice"] = key
                return "cls"

        class Outputs:
            def __getitem__(self, index):
                if index == 1:
                    raise IndexError("Chinese-CLIP text model has no pooler")
                if index != 0:
                    raise IndexError(index)
                return Hidden()

        class Features:
            def norm(self, p=2, dim=-1, keepdim=True):
                return 2.0

            def __truediv__(self, other):
                return "unit"

        class Model:
            def text_model(self, input_ids=None, attention_mask=None):
                return Outputs()

            def text_projection(self, pooled):
                self.pooled = pooled
                return Features()

        model = Model()
        self.assertEqual(main._text_features(model, input_ids="ids", attention_mask="mask"), "unit")
        self.assertEqual(model.pooled, "cls")
        self.assertEqual(seen["slice"], (slice(None), 0, slice(None)))

    def test_one_pixel_gif_produces_vector(self):
        raw = io.BytesIO()
        Image.new("RGB", (1, 1), (220, 30, 30)).save(raw, format="GIF")
        image = main.read_image(raw.getvalue())
        self.assertEqual(image.size, (1, 1))
        self.assertEqual(image.mode, "RGB")

        calls = {}

        def processor(images, return_tensors="pt", input_data_format=None):
            calls["input_data_format"] = input_data_format
            calls["size"] = images.size
            if input_data_format != "channels_last":
                raise ValueError("mean must have 1 elements if it is a single channel image, got 3")
            return {"pixel_values": _Pixel()}

        vector = [0.0] * 768
        vector[0] = 1.0
        main.STUB = False
        main._processor = processor
        main._device = "cpu"
        sys.modules["torch"] = _Torch
        original = main._image_features
        main._image_features = lambda model, pixel_values: _Vector(vector)
        try:
            got = main.embed_image(image)
        finally:
            main._image_features = original

        self.assertEqual(calls["input_data_format"], "channels_last")
        self.assertEqual(calls["size"], (1, 1))
        self.assertEqual(len(got), 768)
        self.assertTrue(all(isinstance(value, float) for value in got))
        self.assertEqual(got[0], 1.0)

    def test_image_processor_calls_are_channels_last(self):
        formats = _image_processor_formats(ROOT / "main.py")
        self.assertGreaterEqual(len(formats), 2)
        self.assertTrue(all(fmt == "channels_last" for fmt in formats))

    def test_default_http_mode_is_h11(self):
        os.environ.pop("IMAGE_EMBED_HTTP", None)
        embed = _invoke_main(ROOT / "main.py")
        self.assertEqual(embed["http"], "h11")
        self.assertEqual(embed["workers"], 1)

        os.environ["IMAGE_EMBED_HTTP"] = "httptools"
        self.assertEqual(_invoke_main(ROOT / "main.py")["http"], "httptools")

        os.environ.pop("RERANKER_HTTP", None)
        self.assertEqual(_invoke_main(RERANKER_MAIN)["http"], "h11")


if __name__ == "__main__":
    unittest.main()
