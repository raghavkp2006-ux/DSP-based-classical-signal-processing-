"""Export the trained spectral-mask denoiser and validate ONNX numerics."""
from pathlib import Path
import numpy as np
import torch
import onnxruntime as ort

from trained_ml_denoiser import load_model


ROOT = Path(__file__).resolve().parent
CHECKPOINT = ROOT / "models" / "spectral_mask_denoiser.pt"
OUTPUT = ROOT / "models" / "spectral_mask_denoiser.onnx"


def main() -> None:
    model, payload, device = load_model(CHECKPOINT, device="cpu")
    channels = int(payload.get("channels", 24))
    n_fft = int(payload.get("n_fft", 512))
    freq_bins = n_fft // 2 + 1
    example = torch.randn(1, 1, freq_bins, 16, dtype=torch.float32)

    with torch.no_grad():
        torch_output = model(example).cpu().numpy()

    torch.onnx.export(
        model,
        example,
        OUTPUT,
        input_names=["features"],
        output_names=["mask"],
        dynamic_axes={"features": {0: "batch", 3: "frames"}, "mask": {0: "batch", 3: "frames"}},
        opset_version=17,
        do_constant_folding=True,
    )

    session = ort.InferenceSession(str(OUTPUT), providers=["CPUExecutionProvider"])
    onnx_output = session.run(["mask"], {"features": example.numpy()})[0]
    diff = float(np.max(np.abs(torch_output - onnx_output)))
    print(f"channels={channels} input_shape={tuple(example.shape)}")
    print(f"diff={diff:.9g}")
    print("PASS" if diff <= 1e-5 else "FAIL")


if __name__ == "__main__":
    main()
