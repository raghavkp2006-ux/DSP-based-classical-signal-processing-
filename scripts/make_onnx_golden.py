"""Generate a tiny fixed-seed inference vector for the Android JVM ONNX test."""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import onnxruntime as ort


ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "signalchain-android/app/src/main/assets/models/spectral_mask_denoiser.onnx"
OUTPUT = ROOT / "signalchain-android/app/src/test/resources/onnx_mask_golden.json"
SEED = 2026


def main() -> None:
    features = np.random.default_rng(SEED).uniform(-0.5, 0.5, (1, 1, 257, 1)).astype(np.float32)
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    mask = session.run([session.get_outputs()[0].name], {session.get_inputs()[0].name: features})[0]
    report = {
        "seed": SEED,
        "shape": [257, 1],
        "input": features.reshape(-1).tolist(),
        "output": mask.reshape(-1).tolist(),
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {features.size} inputs, {mask.size} outputs")


if __name__ == "__main__":
    main()
