#!/usr/bin/env python3
"""
Train a YOLOv5n screen-reflection detector and export to TFLite.

Trains on the Roboflow dataset at scripts/test.v1i.yolov5pytorch/ with
PAD-relevant classes. Before training the script:
  - Merges dead class "b" (1 instance) into "reflection"
  - Remaps to 5 clean classes: artifact, device, face, finger, reflection
  - Combines val + test splits for a larger validation set (108 images)
  - Applies custom hyperparameters tuned for small, imbalanced data

Usage:
    python scripts/train_screen_detector.py              # full pipeline
    python scripts/train_screen_detector.py --export-only # re-export existing weights
    python scripts/train_screen_detector.py --epochs 300  # custom epoch count

Requirements:
    pip install torch torchvision pyyaml tensorflow
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
DATASET_DIR = SCRIPT_DIR / "test.v1i.yolov5pytorch"
MODELS_DIR = PROJECT_ROOT / "pad-core" / "src" / "main" / "assets" / "models"
OUTPUT_NAME = "screen_reflection"
PREPARED_DIR = SCRIPT_DIR / "prepared_dataset"

IMG_SIZE = 384
BATCH_SIZE = 16
DEFAULT_EPOCHS = 300
PATIENCE = 50

# Original classes:  0=artifact, 1=b(bezel), 2=device, 3=face, 4=finger, 5=reflection
# Class 1 ("b") has ~1 training instance — merge into reflection.
# Remap to 5 classes: 0=artifact, 1=device, 2=face, 3=finger, 4=reflection
CLASS_REMAP = {
    0: 0,  # artifact  → 0
    1: 4,  # b (bezel) → reflection (4)
    2: 1,  # device    → 1
    3: 2,  # face      → 2
    4: 3,  # finger    → 3
    5: 4,  # reflection→ 4
}

NEW_CLASS_NAMES = ["artifact", "device", "face", "finger", "reflection"]
NEW_NC = len(NEW_CLASS_NAMES)


def check_prerequisites():
    try:
        import torch  # noqa: F401
    except ImportError:
        print("ERROR: PyTorch not installed. Run: pip install torch torchvision")
        sys.exit(1)

    if not DATASET_DIR.is_dir():
        print(f"ERROR: Dataset not found at {DATASET_DIR}")
        sys.exit(1)

    data_yaml = DATASET_DIR / "data.yaml"
    if not data_yaml.exists():
        print(f"ERROR: data.yaml not found at {data_yaml}")
        sys.exit(1)


def remap_labels(src_label_dir: Path, dst_label_dir: Path):
    """Remap class indices in YOLO label files and write to dst."""
    dst_label_dir.mkdir(parents=True, exist_ok=True)
    count = 0
    for txt in sorted(src_label_dir.glob("*.txt")):
        lines = []
        for line in txt.read_text().strip().splitlines():
            parts = line.strip().split()
            if not parts:
                continue
            old_cls = int(parts[0])
            new_cls = CLASS_REMAP.get(old_cls)
            if new_cls is not None:
                lines.append(f"{new_cls} {' '.join(parts[1:])}")
        (dst_label_dir / txt.name).write_text("\n".join(lines) + "\n" if lines else "")
        count += 1
    return count


def symlink_images(src_img_dir: Path, dst_img_dir: Path):
    """Symlink images from src to dst to avoid duplication."""
    dst_img_dir.mkdir(parents=True, exist_ok=True)
    for img in src_img_dir.iterdir():
        if img.suffix.lower() in (".jpg", ".jpeg", ".png", ".bmp", ".webp"):
            dst = dst_img_dir / img.name
            if not dst.exists():
                dst.symlink_to(img.resolve())


def prepare_dataset() -> Path:
    """
    Prepare a clean dataset:
    1. Remap class indices (merge 'b' into 'reflection', compact to 5 classes)
    2. Merge val + test into a single larger validation split
    3. Write a new data.yaml with absolute paths
    """
    print("\n  Preparing dataset ...")

    train_src = DATASET_DIR / "train"
    val_src = DATASET_DIR / "valid"
    test_src = DATASET_DIR / "test"

    train_dst = PREPARED_DIR / "train"
    val_dst = PREPARED_DIR / "val"

    if PREPARED_DIR.exists():
        shutil.rmtree(PREPARED_DIR)

    # --- Train split ---
    symlink_images(train_src / "images", train_dst / "images")
    n_train = remap_labels(train_src / "labels", train_dst / "labels")
    print(f"    Train: {n_train} images")

    # --- Val split = original val + test merged ---
    symlink_images(val_src / "images", val_dst / "images")
    symlink_images(test_src / "images", val_dst / "images")
    n_val = remap_labels(val_src / "labels", val_dst / "labels")
    n_test = remap_labels(test_src / "labels", val_dst / "labels")
    print(f"    Val:   {n_val + n_test} images (val:{n_val} + test:{n_test})")

    # --- Verify new distribution ---
    print(f"    Classes: {NEW_CLASS_NAMES}")
    for split_name, label_dir in [("train", train_dst / "labels"), ("val", val_dst / "labels")]:
        counts = {i: 0 for i in range(NEW_NC)}
        for txt in label_dir.glob("*.txt"):
            for line in txt.read_text().strip().splitlines():
                parts = line.strip().split()
                if parts:
                    cls = int(parts[0])
                    counts[cls] = counts.get(cls, 0) + 1
        dist = ", ".join(f"{NEW_CLASS_NAMES[i]}={counts[i]}" for i in range(NEW_NC))
        print(f"    {split_name} distribution: {dist}")

    # --- Write data.yaml ---
    import yaml
    data = {
        "train": str(train_dst / "images"),
        "val": str(val_dst / "images"),
        "nc": NEW_NC,
        "names": NEW_CLASS_NAMES,
    }
    data_yaml = PREPARED_DIR / "data.yaml"
    with open(data_yaml, "w") as f:
        yaml.dump(data, f, default_flow_style=False)

    print(f"    data.yaml → {data_yaml}")
    return data_yaml


def clone_yolov5(dest: Path):
    """Clone YOLOv5 repository if not already present."""
    if (dest / "train.py").exists():
        print(f"  YOLOv5 already present at {dest}")
        return
    print("  Cloning ultralytics/yolov5 ...")
    subprocess.check_call(
        ["git", "clone", "--depth", "1", "https://github.com/ultralytics/yolov5.git", str(dest)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.check_call(
        [sys.executable, "-m", "pip", "install", "-r", str(dest / "requirements.txt"), "-q"],
    )


def train(yolov5_dir: Path, data_yaml: Path, epochs: int) -> Path:
    """Run YOLOv5 training with default YOLOv5 hyperparameters."""
    run_dir = SCRIPT_DIR / "runs"
    cmd = [
        sys.executable, str(yolov5_dir / "train.py"),
        "--img", str(IMG_SIZE),
        "--batch", str(BATCH_SIZE),
        "--epochs", str(epochs),
        "--patience", str(PATIENCE),
        "--data", str(data_yaml),
        "--weights", "yolov5n.pt",
        "--project", str(run_dir),
        "--name", OUTPUT_NAME,
        "--exist-ok",
        "--cache",
    ]
    print(f"\n  Training YOLOv5n for {epochs} epochs (default hyperparams) ...")
    print(f"    Patience:     {PATIENCE}")
    print(f"  Command: {' '.join(cmd)}\n")
    subprocess.check_call(cmd)

    best = run_dir / OUTPUT_NAME / "weights" / "best.pt"
    if not best.exists():
        best = run_dir / OUTPUT_NAME / "weights" / "last.pt"
    if not best.exists():
        print("ERROR: No trained weights found")
        sys.exit(1)

    print(f"\n  Best weights: {best}")
    return best


def export_tflite(yolov5_dir: Path, weights: Path) -> Path:
    """Export trained model to TFLite (float16)."""
    cmd = [
        sys.executable, str(yolov5_dir / "export.py"),
        "--weights", str(weights),
        "--include", "tflite",
        "--img-size", str(IMG_SIZE),
    ]
    print("\n  Exporting to TFLite (float16) ...")
    subprocess.check_call(cmd)

    candidates = list(weights.parent.parent.rglob("*.tflite"))
    if not candidates:
        candidates = list(weights.parent.rglob("*.tflite"))
    if not candidates:
        print("ERROR: No .tflite file found after export")
        sys.exit(1)

    tflite = max(candidates, key=lambda p: p.stat().st_mtime)
    print(f"  Exported TFLite: {tflite} ({tflite.stat().st_size / 1024 / 1024:.1f} MB)")
    return tflite


def install_model(tflite_path: Path):
    """Copy TFLite to assets and pack to .pad."""
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    dest = MODELS_DIR / f"{OUTPUT_NAME}.tflite"
    shutil.copy2(tflite_path, dest)
    print(f"\n  Copied to {dest}")

    pack_script = SCRIPT_DIR / "pack_models.py"
    if pack_script.exists():
        print("  Packing to .pad format ...")
        subprocess.check_call([sys.executable, str(pack_script)])
        print("  Model packed successfully")
    else:
        print(f"  WARNING: {pack_script} not found, .tflite left as-is")


def main():
    parser = argparse.ArgumentParser(description="Train YOLOv5n screen-reflection detector")
    parser.add_argument("--epochs", type=int, default=DEFAULT_EPOCHS)
    parser.add_argument("--export-only", action="store_true",
                        help="Skip training, re-export existing weights")
    args = parser.parse_args()

    print("=" * 60)
    print("  Screen Reflection Detector — Training Pipeline v2")
    print("=" * 60)

    check_prerequisites()

    yolov5_dir = SCRIPT_DIR / "yolov5"
    clone_yolov5(yolov5_dir)

    data_yaml = prepare_dataset()

    if args.export_only:
        best = SCRIPT_DIR / "runs" / OUTPUT_NAME / "weights" / "best.pt"
        if not best.exists():
            print(f"ERROR: No existing weights at {best}")
            sys.exit(1)
    else:
        best = train(yolov5_dir, data_yaml, args.epochs)

    tflite = export_tflite(yolov5_dir, best)
    install_model(tflite)

    print("\n" + "=" * 60)
    print("  Done! Model installed to pad-core assets.")
    print("  Classes: " + ", ".join(f"{i}={n}" for i, n in enumerate(NEW_CLASS_NAMES)))
    print("=" * 60)


if __name__ == "__main__":
    main()
