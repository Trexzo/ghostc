#!/usr/bin/env python3
"""Deterministic offline fixture tests for Slate screenshot comparison."""
import hashlib
from pathlib import Path
import random
import struct
import tempfile
import unittest
import zlib

from slate_visual_review import make_review, validate_png


def chunk(kind, payload):
    return (struct.pack(">I", len(payload)) + kind + payload +
            struct.pack(">I", zlib.crc32(kind + payload) & 0xffffffff))


def png(width, height, rgba=False):
    color = 6 if rgba else 2
    pixel = b"\x00" * (4 if rgba else 3)
    rows = (b"\x00" + pixel * width) * height
    ihdr = struct.pack(">IIBBBBB", width, height, 8, color, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) +
            chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


class VisualReviewTests(unittest.TestCase):
    def test_dimensions_and_safe_embedded_comparison(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            baseline = root / "baseline<script>.png"
            experimental = root / "experimental.png"
            output = root / "review.html"
            original = png(12, 8)
            baseline.write_bytes(original)
            experimental.write_bytes(png(12, 8, rgba=True))
            self.assertEqual(make_review(baseline, experimental, output),
                             (12, 8))
            page = output.read_text(encoding="utf-8")
            self.assertIn('id="baseline"', page)
            self.assertIn('id="experiment"', page)
            self.assertIn('type="range"', page)
            self.assertIn("12 × 8", page)
            self.assertIn("&lt;script&gt;", page)
            self.assertNotIn("baseline<script>.png", page)
            self.assertIn(hashlib.sha256(original).hexdigest(), page)
            self.assertIn("data:image/png;base64,", page)
            self.assertIn("no internet connection required", page)
            with self.assertRaisesRegex(ValueError, "must not replace"):
                make_review(baseline, experimental, baseline)

    def test_incompatible_and_corrupt_inputs(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            a = root / "a.png"
            b = root / "b.png"
            out = root / "review.html"
            a.write_bytes(png(4, 4))
            b.write_bytes(png(5, 4))
            with self.assertRaisesRegex(ValueError, "different pixel"):
                make_review(a, b, out)
            self.assertFalse(out.exists())
            for content in (b"not a PNG", png(4, 4)[:-2],
                            png(4, 4) + b"trailing",
                            png(4, 4)[:-12]):
                with self.assertRaises(ValueError):
                    validate_png(content)
            tampered = bytearray(png(4, 4))
            tampered[24] = 3
            with self.assertRaisesRegex(ValueError, "CRC"):
                validate_png(bytes(tampered))
            with self.assertRaisesRegex(ValueError, "8-bit RGB"):
                validate_png(png_indexed())

    def test_random_dimensions(self):
        randomizer = random.Random(20261104)
        for _ in range(500):
            width = randomizer.randrange(1, 65)
            height = randomizer.randrange(1, 65)
            self.assertEqual(validate_png(
                png(width, height, rgba=bool(randomizer.getrandbits(1)))),
                (width, height))


def png_indexed():
    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 3, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) +
            chunk(b"IDAT", zlib.compress(b"\x00\x00")) +
            chunk(b"IEND", b""))


if __name__ == "__main__":
    unittest.main(verbosity=2)
    print("SLATE_VISUAL_REVIEW_TEST_PASS")
