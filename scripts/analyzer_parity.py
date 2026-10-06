"""Compare the Python agent analyzer over the ablation set and emit JVM goldens.

Run from the repository root with its requirements installed:
    python scripts/analyzer_parity.py --output signalchain-android/app/src/test/resources/analyzer_parity.json
"""
from __future__ import annotations

import argparse
import json
import sys
import wave
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

# dsp_pipeline imports scipy.signal for other stages. The analyzer only needs
# the first-order pre-emphasis filter, so keep the parity utility runnable in
# lightweight Python environments that have NumPy but not SciPy.
try:
    import scipy.signal  # noqa: F401
except ImportError:
    import types

    scipy = types.ModuleType("scipy")
    scipy.signal = types.ModuleType("scipy.signal")

    def lfilter(_b: list[float], _a: list[float], values: np.ndarray) -> np.ndarray:
        result = np.empty_like(values, dtype=float)
        if len(values):
            result[0] = values[0]
            result[1:] = values[1:] + _b[1] * values[:-1]
        return result

    scipy.signal.lfilter = lfilter
    sys.modules["scipy"] = scipy
    sys.modules["scipy.signal"] = scipy.signal

from agent_analyzer import analyze_audio
from dsp_pipeline import estimate_snr, frame_params, pre_emphasis, voice_activity_detection


def read_mono(path: Path) -> tuple[int, np.ndarray]:
    with wave.open(str(path), "rb") as wav:
        channels, width, rate, frames = wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()
        raw = wav.readframes(frames)
    if width == 2:
        samples = np.frombuffer(raw, dtype="<i2").astype(np.float64) / 32768.0
    elif width == 1:
        samples = (np.frombuffer(raw, dtype=np.uint8).astype(np.float64) - 128) / 128.0
    else:
        raise ValueError(f"Unsupported PCM width {width} in {path}")
    if channels > 1:
        samples = samples.reshape(-1, channels).mean(axis=1)
    return rate, samples


def synthetic_signals() -> dict[str, np.ndarray]:
    rate = 16_000
    n = np.arange(rate * 2, dtype=np.float64)
    gate = ((n >= 3_200) & (n < 24_000)).astype(np.float64)
    speech = 0.16 * np.sin(2 * np.pi * 223 * n / rate) * gate
    steady = 0.018 * np.sin(2 * np.pi * 73 * n / rate)
    variable = 0.025 * np.sin(2 * np.pi * 997 * n / rate) * ((n.astype(int) // 800) % 2)
    broadband = 0.035 * (np.sin(2 * np.pi * 2_113 * n / rate) + np.sin(2 * np.pi * 3_719 * n / rate))
    return {
        "steady_tone_speech": (steady + speech).astype(np.float32),
        "gated_broadband_speech": (broadband + variable + speech).astype(np.float32),
        "quiet_tonal_noise": (0.01 * np.sin(2 * np.pi * 611 * n / rate)).astype(np.float32),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--testset", type=Path, default=ROOT / "ablation_testset")
    parser.add_argument("--output", type=Path, help="Write full test-set results and deterministic Kotlin goldens as JSON")
    args = parser.parse_args()

    testset = []
    for path in sorted((args.testset / "noisy").glob("*.wav")):
        rate, samples = read_mono(path)
        result = analyze_audio(samples, rate)
        fp = frame_params(len(samples), rate)
        speech, _, _ = voice_activity_detection(pre_emphasis(samples), fp)
        segmental_snr = round(estimate_snr(samples, fp, speech), 2)
        testset.append({"file": path.name, **result, "segmental_snr_db": segmental_snr})
    if not testset:
        raise SystemExit(f"No WAV files found under {args.testset / 'noisy'}")

    goldens = {
        name: analyze_audio(samples.astype(np.float64), 16_000)
        for name, samples in synthetic_signals().items()
    }
    report = {"testset": testset, "synthetic_goldens": goldens}
    rendered = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")

    snrs = [row["snr_db"] for row in testset]
    cvs = [row["noise_stationarity_cv"] for row in testset]
    snr_deltas = [abs(row["snr_db"] - row["segmental_snr_db"]) for row in testset]
    print(json.dumps({
        "files": len(testset),
        "snr_db_min_median_max": [min(snrs), float(np.median(snrs)), max(snrs)],
        "cv_min_median_max": [min(cvs), float(np.median(cvs)), max(cvs)],
        "global_vs_segmental_snr_abs_delta_median_max": [float(np.median(snr_deltas)), max(snr_deltas)],
        "stationary_count": sum(row["stationary_noise"] for row in testset),
        "full_adaptive_count": sum(row["snr_db"] < 5 or (row["snr_db"] <= 15 and not row["stationary_noise"]) for row in testset),
        "classical_dsp_count": sum(5 <= row["snr_db"] <= 15 and row["stationary_noise"] for row in testset),
        "light_touch_count": sum(row["snr_db"] > 15 for row in testset),
    }))


if __name__ == "__main__":
    main()
