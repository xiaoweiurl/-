"""主体裁剪不依赖 GPU。检测器用假的框。"""

from __future__ import annotations

import io
import unittest

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
        self.assertIn(body["crop_mode"], ("off", "stub", "owlv2", "unavailable"))
        if not body["crop_enabled"]:
            self.assertEqual(body["crop_mode"], "off")
            self.assertFalse(body["crop_detector_ready"])

    def test_health_reports_detector_missing(self):
        previous = (main.CROP_ENABLED, main.STUB, main._detector)
        main.CROP_ENABLED = True
        main.STUB = False
        main._detector = None
        try:
            body = main.health()
        finally:
            main.CROP_ENABLED, main.STUB, main._detector = previous
        self.assertTrue(body["crop_enabled"])
        self.assertEqual(body["crop_mode"], "unavailable")
        self.assertFalse(body["crop_detector_ready"])

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
