"""Create a deterministic, held-out LibriSpeech ablation test set.

The final ``holdout_fraction`` of lexically sorted FLAC files is reserved for
evaluation.  Re-train with the first remaining files only for a strict
train/test split; the included pre-existing checkpoint may have seen dev-clean
files and should be re-trained before reporting final academic results.
"""
from __future__ import annotations

import argparse
import csv
import random
from math import gcd
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

from train_denoiser import LibriSyntheticNoise

NOISE_NAMES = ("white", "brown_hum", "tonal", "bursty")
SNRS = (-5, 0, 5, 10, 15)


def read_clean(path: Path, sample_rate: int, seconds: float) -> np.ndarray:
    audio, sr = sf.read(path, dtype="float32", always_2d=True)
    audio = audio.mean(axis=1)
    if sr != sample_rate:
        factor = gcd(sr, sample_rate)
        audio = resample_poly(audio, sample_rate // factor, sr // factor).astype(np.float32)
    samples = int(sample_rate * seconds)
    if len(audio) < samples:
        audio = np.pad(audio, (0, samples - len(audio)))
    return audio[:samples] / (np.max(np.abs(audio[:samples])) + 1e-8) * 0.7


def deterministic_noise(kind: int, samples: int, seed: int) -> np.ndarray:
    """Reuse LibriSyntheticNoise._noise with isolated seeded RNG state."""
    state_py, state_np = random.getstate(), np.random.get_state()
    try:
        random.seed(seed)
        np.random.seed(seed)
        original = random.randrange
        random.randrange = lambda *args, **kwargs: kind
        return LibriSyntheticNoise._noise(samples).astype(np.float32)
    finally:
        random.randrange = original
        random.setstate(state_py)
        np.random.set_state(state_np)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", required=True, help="LibriSpeech folder containing .flac files")
    parser.add_argument("--output", default="ablation_testset")
    parser.add_argument("--seed", type=int, default=3011448)
    parser.add_argument("--num-clean", type=int, default=2,
                        help="Two files × four noises × five SNRs = 40 conditions")
    parser.add_argument("--holdout-fraction", type=float, default=0.10)
    parser.add_argument("--seconds", type=float, default=3.0)
    parser.add_argument("--sample-rate", type=int, default=16000)
    args = parser.parse_args()

    files = sorted(Path(args.data).rglob("*.flac"))
    if not files:
        raise SystemExit("No .flac files found under --data")
    split = max(1, int(len(files) * (1 - args.holdout_fraction)))
    held_out = files[split:]
    if len(held_out) < args.num_clean:
        raise SystemExit("Held-out split does not contain enough files")
    selector = random.Random(args.seed)
    selected = selector.sample(held_out, args.num_clean)

    output = Path(args.output)
    clean_dir, noisy_dir = output / "clean", output / "noisy"
    clean_dir.mkdir(parents=True, exist_ok=True)
    noisy_dir.mkdir(parents=True, exist_ok=True)
    rows = []
    for source_index, source in enumerate(selected):
        file_id = f"holdout_{source_index:02d}_{source.stem}"
        clean = read_clean(source, args.sample_rate, args.seconds)
        clean_path = clean_dir / f"{file_id}.wav"
        sf.write(clean_path, clean, args.sample_rate)
        for noise_index, noise_name in enumerate(NOISE_NAMES):
            noise = deterministic_noise(noise_index, len(clean), args.seed + source_index * 100 + noise_index)
            for snr in SNRS:
                scaled = noise * np.sqrt(np.mean(clean**2) / (np.mean(noise**2) + 1e-12)) / (10 ** (snr / 20))
                noisy = np.clip(clean + scaled, -1.0, 1.0).astype(np.float32)
                noisy_path = noisy_dir / f"{file_id}_{noise_name}_{snr:+03d}dB.wav"
                sf.write(noisy_path, noisy, args.sample_rate)
                rows.append({"file_id": file_id, "source_clean_file": str(source.resolve()),
                             "noise_type": noise_name, "snr_db": snr,
                             "clean_path": str(clean_path.resolve()),
                             "noisy_path": str(noisy_path.resolve())})
    with (output / "metadata.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=("file_id", "source_clean_file", "noise_type", "snr_db", "clean_path", "noisy_path"))
        writer.writeheader(); writer.writerows(rows)
    with (output / "SPLIT.md").open("w", encoding="utf-8") as handle:
        handle.write(f"Held-out split: lexical indices {split}..{len(files)-1} of {len(files)} LibriSpeech files.\n")
        handle.write("Use only indices 0..{split_minus_one} when re-training to avoid train/test leakage.\n".format(split_minus_one=split - 1))
        handle.write(f"Seed: {args.seed}; conditions: {len(rows)}; fixed SNRs: {SNRS}.\n")
    print(f"Created {len(rows)} deterministic conditions from {args.num_clean} held-out clean clips.")
    print(output / "metadata.csv")


if __name__ == "__main__":
    main()
