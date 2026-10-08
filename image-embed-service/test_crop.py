"""主体裁剪不依赖 GPU。检测器用假的框。"""

from __future__ import annotations

import importlib
import io
import logging
import unittest
from unittest import mock

from PIL import Image

import crop
import main


class _Boxes:
    def __init__(self, detections):
        self._detections = detections

    def detect(self, image):
        return self._detections


class CropFallbackTest(unittest.TestCase):
    def test_tiny_image_skips_detector(self):
        seen = {"calls": 0}

        class Detector:
            def detect(self, image):
                seen["calls"] += 1
                return [{"score": 0.9, "box": [0, 0, 10, 10]}]

        image = Image.new("RGB", (16, 16), "red")
        prepared, mode = crop.preprocess(image, Detector(), enabled=True, min_side=48)
        self.assertEqual(mode, "full")
        self.assertEqual(prepared.size, (16, 16))
        self.assertEqual(seen["calls"], 0)

    def test_no_confident_box_uses_whole_image(self):
        image = Image.new("RGB", (200, 200), "white")
        prepared, mode = crop.preprocess(
            image, _Boxes([{"score": 0.05, "box": [10, 10, 80, 80]}]), enabled=True, threshold=0.2
        )
        self.assertEqual(mode, "full")
        self.assertEqual(prepared.size, image.size)

    def test_empty_detections_use_whole_image(self):
        image = Image.new("RGB", (200, 200), "white")
        prepared, mode = crop.preprocess(image, _Boxes([]), enabled=True)
        self.assertEqual(mode, "full")
        self.assertEqual(prepared.size, image.size)

    def test_detector_error_uses_whole_image(self):
        class Broken:
            def detect(self, image):
                raise RuntimeError("boom")

        image = Image.new("RGB", (200, 200), "white")
        prepared, mode = crop.preprocess(image, Broken(), enabled=True)
        self.assertEqual(mode, "full")
        self.assertEqual(prepared.size, image.size)

    def test_confident_box_crops_with_padding_inside_frame(self):
        image = Image.new("RGB", (200, 160), "blue")
        prepared, mode = crop.preprocess(
            image,
            _Boxes([{"score": 0.4, "box": [40, 30, 120, 110]}]),
            enabled=True,
            threshold=0.2,
            padding_ratio=0.1,
        )
        self.assertEqual(mode, "crop")
        self.assertLess(prepared.width, image.width)
        self.assertLess(prepared.height, image.height)
        self.assertGreater(prepared.width, 80)

    def test_disabled_does_not_call_detector(self):
        seen = {"calls": 0}

        class Detector:
            def detect(self, image):
                seen["calls"] += 1
                return [{"score": 0.99, "box": [10, 10, 40, 40]}]

        image = Image.new("RGB", (200, 200), "white")
        prepared, mode = crop.preprocess(image, Detector(), enabled=False)
        self.assertEqual(mode, "full")
        self.assertEqual(seen["calls"], 0)
        self.assertEqual(prepared.size, image.size)

    def test_health_reports_crop_switch(self):
        body = main.health()
        self.assertIn("crop_enabled", body)
        self.assertIn("crop_mode", body)
        self.assertIn("crop_detector_ready", body)
        self.assertIn("crop_detector_error", body)
        self.assertIn(body["crop_mode"], ("off", "stub", "owlv2", "unavailable"))
        if not body["crop_enabled"]:
            self.assertEqual(body["crop_mode"], "off")
            self.assertFalse(body["crop_detector_ready"])
            self.assertIsNone(body["crop_detector_error"])

    def test_health_reports_detector_missing(self):
        previous = (main.CROP_ENABLED, main.STUB, main._detector, main._crop_detector_error)
        main.CROP_ENABLED = True
        main.STUB = False
        main._detector = None
        main._crop_detector_error = "ImportError: 主体裁剪需要 scipy"
        try:
            body = main.health()
        finally:
            main.CROP_ENABLED, main.STUB, main._detector, main._crop_detector_error = previous
        self.assertTrue(body["crop_enabled"])
        self.assertEqual(body["crop_mode"], "unavailable")
        self.assertFalse(body["crop_detector_ready"])
        self.assertIn("scipy", body["crop_detector_error"])

    def test_missing_scipy_error_names_the_dependency(self):
        real = importlib.import_module

        def fake(name, package=None):
            if name == "scipy" or name.startswith("scipy."):
                raise ImportError("No module named 'scipy'")
            return real(name, package)

        with mock.patch("importlib.import_module", fake):
            with self.assertRaises(ImportError) as caught:
                crop.require_crop_imports()
        self.assertIn("scipy", str(caught.exception))
        self.assertIn("Owlv2ImageProcessor.resize", str(caught.exception))

    def test_startup_records_detector_import_failure_once(self):
        previous = (main.CROP_ENABLED, main.STUB, main._detector, main._crop_detector_error)
        main.CROP_ENABLED = True
        main.STUB = False
        main._detector = object()
        main._crop_detector_error = None

        class Broken:
            def __init__(self, model_id, device):
                raise ImportError(
                    "主体裁剪需要 scipy：transformers 的 OWLv2 后处理会导入 scipy.ndimage"
                )

        original = crop.OwlV2GarmentDetector
        crop.OwlV2GarmentDetector = Broken
        try:
            with self.assertLogs("image-embed", level="ERROR") as logs:
                main._load_detector()
            body = main.health()
        finally:
            crop.OwlV2GarmentDetector = original
            main.CROP_ENABLED, main.STUB, main._detector, main._crop_detector_error = previous
        self.assertFalse(body["crop_detector_ready"])
        self.assertEqual(body["crop_mode"], "unavailable")
        self.assertIn("scipy", body["crop_detector_error"])
        errors = [record for record in logs.records if record.levelno >= logging.ERROR]
        self.assertEqual(len(errors), 1)

    def test_request_without_crop_flag_does_not_crop(self):
        raw = io.BytesIO()
        Image.new("RGB", (80, 80), (10, 20, 30)).save(raw, format="PNG")
        image = main.read_image(raw.getvalue())
        called = {"n": 0}

        class Detector:
            def detect(self, current):
                called["n"] += 1
                return [{"score": 0.9, "box": [5, 5, 40, 40]}]

        previous_detector = main._detector
        previous_stub = main.STUB
        main._detector = Detector()
        main.STUB = True
        try:
            _vector, mode = main.embed_image_prepared(image, crop_requested=False)
        finally:
            main._detector = previous_detector
            main.STUB = previous_stub
        self.assertEqual(mode, "full")
        self.assertEqual(called["n"], 0)


if __name__ == "__main__":
    unittest.main()
