import json
import os
import sys
import numpy as np

# Add repo root to sys.path so dsp_pipeline can be imported
repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, repo_root)

import dsp_pipeline

def make_eq_golden():
    np.random.seed(42)
    fs = 16000
    n = 2000
    x = (np.random.randn(n) * 0.1).astype(np.float32)
    y = dsp_pipeline.apply_eq(x, fs, 1.0)

    out_dir = os.path.join(repo_root, "signalchain-android", "app", "src", "test", "resources")
    os.makedirs(out_dir, exist_ok=True)
    out_file = os.path.join(out_dir, "eq_golden.json")

    data = {
        "fs": fs,
        "gain": 1.0,
        "input": x.tolist(),
        "output": y.tolist()
    }

    with open(out_file, "w") as f:
        json.dump(data, f)
    print(f"Generated {out_file} with {n} samples.")

if __name__ == "__main__":
    make_eq_golden()
