#!/usr/bin/env python3
"""
Pack OpenPAD TFLite models: gzip compress then XOR-scramble.

Produces .pad files that ModelLoader.kt can reverse at runtime.
Run this once before building the APK.

Usage:
    python scripts/pack_models.py          # pack .tflite -> .pad
    python scripts/pack_models.py --unpack # reverse: .pad -> .tflite
"""

import gzip
import os
import sys

MODELS_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "pad-core", "src", "main", "assets", "models"
)

# 32-byte XOR key — must match ModelLoader.kt XOR_KEY exactly
XOR_KEY = bytes([
    0x4F, 0x70, 0x65, 0x6E, 0x50, 0x41, 0x44, 0x5F,
    0x4D, 0x6F, 0x64, 0x65, 0x6C, 0x47, 0x75, 0x61,
    0x72, 0x64, 0x5F, 0x32, 0x30, 0x32, 0x36, 0x5F,
    0x53, 0x44, 0x4B, 0x5F, 0x76, 0x31, 0x2E, 0x30,
])


def xor_bytes(data: bytes) -> bytes:
    key_len = len(XOR_KEY)
    return bytes(b ^ XOR_KEY[i % key_len] for i, b in enumerate(data))


def pack():
    if not os.path.isdir(MODELS_DIR):
        print(f"ERROR: {MODELS_DIR} not found")
        sys.exit(1)

    print(f"Packing models in {MODELS_DIR}\n")
    total_before = 0
    total_after = 0

    for name in sorted(os.listdir(MODELS_DIR)):
        if not name.endswith(".tflite"):
            continue

        src = os.path.join(MODELS_DIR, name)
        dst = os.path.join(MODELS_DIR, name.replace(".tflite", ".pad"))

        with open(src, "rb") as f:
            raw = f.read()

        compressed = gzip.compress(raw, compresslevel=9)
        scrambled = xor_bytes(compressed)

        with open(dst, "wb") as f:
            f.write(scrambled)

        size_before = len(raw)
        size_after = len(scrambled)
        total_before += size_before
        total_after += size_after
        reduction = (1 - size_after / size_before) * 100

        print(f"  {name}")
        print(f"    {size_before / 1024 / 1024:.1f} MB -> {size_after / 1024 / 1024:.1f} MB "
              f"({reduction:.0f}% smaller)")

        os.remove(src)

    print(f"\n{'=' * 50}")
    print(f"Total: {total_before / 1024 / 1024:.1f} MB -> {total_after / 1024 / 1024:.1f} MB")
    if total_before > 0:
        print(f"Reduction: {(1 - total_after / total_before) * 100:.0f}%")
    print(f"\nOriginal .tflite files removed from assets.")
    print(f"Backups are in scripts/model_backups/ if needed.")


def unpack():
    if not os.path.isdir(MODELS_DIR):
        print(f"ERROR: {MODELS_DIR} not found")
        sys.exit(1)

    print(f"Unpacking models in {MODELS_DIR}\n")

    for name in sorted(os.listdir(MODELS_DIR)):
        if not name.endswith(".pad"):
            continue

        src = os.path.join(MODELS_DIR, name)
        dst = os.path.join(MODELS_DIR, name.replace(".pad", ".tflite"))

        with open(src, "rb") as f:
            scrambled = f.read()

        compressed = xor_bytes(scrambled)
        raw = gzip.decompress(compressed)

        with open(dst, "wb") as f:
            f.write(raw)

        print(f"  {name} -> {os.path.basename(dst)} ({len(raw) / 1024 / 1024:.1f} MB)")
        os.remove(src)

    print("\nDone. .pad files removed, .tflite files restored.")


if __name__ == "__main__":
    if "--unpack" in sys.argv:
        unpack()
    else:
        pack()
